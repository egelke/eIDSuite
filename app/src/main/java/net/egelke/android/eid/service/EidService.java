/*
    This file is part of eID Suite.
    Copyright (C) 2014-2015 Egelke BVBA

    eID Suite is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    eID Suite is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with eID Suite.  If not, see <http://www.gnu.org/licenses/>.
*/
package net.egelke.android.eid.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.security.BouncyCastleDigest;
import com.itextpdf.text.pdf.security.CrlClient;
import com.itextpdf.text.pdf.security.CrlClientOnline;
import com.itextpdf.text.pdf.security.ExternalDigest;
import com.itextpdf.text.pdf.security.ExternalSignature;
import com.itextpdf.text.pdf.security.MakeSignature;
import com.itextpdf.text.pdf.security.OcspClient;
import com.itextpdf.text.pdf.security.OcspClientBouncyCastle;
import com.itextpdf.text.pdf.security.TSAClient;
import com.itextpdf.text.pdf.security.TSAClientBouncyCastle;

import net.egelke.android.eid.AbortException;
import net.egelke.android.eid.CardBlockedException;
import net.egelke.android.eid.EidSuiteApp;
import net.egelke.android.eid.R;
import net.egelke.android.eid.UserCancelException;
import net.egelke.android.eid.belpic.FileId;
import net.egelke.android.eid.diagnostic.DeviceDescriptor;
import net.egelke.android.eid.reader.APDUException;
import net.egelke.android.eid.reader.EidCardCallback;
import net.egelke.android.eid.reader.EidCardReader;
import net.egelke.android.eid.reader.PinCallback;
import net.egelke.android.eid.usb.CCID;
import net.egelke.android.eid.usb.CCIDException;
import net.egelke.android.eid.view.PinActivity;
import net.egelke.android.eid.view.PinPadActivity;
import net.egelke.android.eid.view.SettingsActivity;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class EidService extends Service {

    private static final String TAG = "net.egelke.android.eid";
    private static final String ACTION_USB_PERMISSION = "net.egelke.android.eid.USB_PERMISSION";
    private static final String ACTION_PIN_PROVIDED = "net.egelke.android.eid.PIN_PROVIDED";
    private static final long USB_TIMEOUT = 1 * 60 * 1000; //1 MIN
    private static final long EID_TIMEOUT = 1 * 60 * 1000; //1 MIN
    private static final long CONFIRM_TIMEOUT = 1 * 60 * 1000; //1MIN
    private static final long PIN_TIMEOUT = 1 * 60 * 1000; //1MIN

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
            Security.addProvider(new BouncyCastleProvider());
    }

    //Actions
    public static final int READ_DATA = 1;
    public static final int VERIFY_CARD = 3;
    public static final int VERIFY_PIN = 5;
    public static final int AUTH = 10;
    public static final int SIGN = 11;
    public static final int DIAG = 99;

    //Action Response
    public static final int DATA_RSP = 101;
    public static final int AUTH_RSP = 110;
    public static final int SIGN_RSP = 111;
    public static final int DIAG_RSP = 199;

    //Action End
    public static final int END = 900;

    private class IncomingHandler extends Handler {

        public IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(EidService.this)
                    .setSmallIcon(R.drawable.ic_stat_card)
                    .setContentTitle(EidService.this.getText(R.string.notiRInit))
                    .setContentText(EidService.this.getText(R.string.notiRInitMsg))
                    .setCategory(Notification.CATEGORY_SERVICE);
            PowerManager.WakeLock wl = powerMgr.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);

            wl.acquire();
            startForeground(1, builder.build());
            try {
                if (msg.what == DIAG) {
                    diagnose(msg);
                    return;
                }

                boolean online = isOnline(msg);

                obtainUsbDevice();
                obtainUsbPermission();
                obtainEidReader();
                try {
                    obtainEidCard();
                    switch (msg.what) {
                        case READ_DATA:
                            readData(msg);
                            break;
                        case VERIFY_PIN:
                            verifyPin(msg);
                            break;
                        case AUTH:
                            authenticate(msg);
                            break;
                        case SIGN:
                            sign(msg, online);
                            break;
                        default:
                            super.handleMessage(msg);
                    }
                } finally {
                    eidCardReader.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to process message", e);
                processError(e);
            } finally {
                try {
                    end(msg);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to end the message", e);
                }

                wl.release();
                stopForeground(true);
            }
        }
    }

    //permanent properties
    private Handler uiHandler = null;
    private Handler broadcast = null;
    private Messenger messenger = null;
    private UsbManager usbManager;
    private NotificationManager notifyMgr;
    private PowerManager powerMgr;
    private HandlerThread bcThread;
    private HandlerThread messageThread;

    //temporally properties
    private BroadcastReceiver detachReceiver;
    private UsbDevice ccidDevice;
    private EidCardReader eidCardReader;
    private Object wait;
    private String pin;

    @Override
    public void onCreate() {
        Log.d(TAG, "EidService onCreate " + this);
        super.onCreate();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        notifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        powerMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);

        bcThread = new HandlerThread("EidServiceBCThread", Process.THREAD_PRIORITY_BACKGROUND);
        bcThread.start();

        messageThread = new HandlerThread("EidServiceMsgThread", Process.THREAD_PRIORITY_FOREGROUND);
        messageThread.start();

        uiHandler = new Handler(Looper.getMainLooper());
        broadcast = new Handler(bcThread.getLooper());
        messenger = new Messenger(new IncomingHandler(messageThread.getLooper()));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "EidService onBind " + this);
        return messenger.getBinder();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "EidService onDestroy " + this);

        //end the threads
        if (!bcThread.quit()) {
            Log.w(TAG, "Failed to quit broadcast thread loop");
        }
        if (!messageThread.quit()) {
            Log.w(TAG, "Failed to quit main thread loop");
        }

        //remove any lock to allow the loopers to quit
        if (wait != null) {
            synchronized (wait) {
                wait.notify();
            }
        }

        super.onDestroy();
    }

    private boolean isOnline(Message msg) throws AbortException {
        boolean online = true;
        if (msg.what == SIGN) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(EidService.this);
            String netReq = sharedPref.getString(SettingsActivity.KEY_PREF_SIGN_NETWORK, getString(R.string.pref_sign_network_default));

            if ("never".equals(netReq)) {
                online = false;
            } else {
                ConnectivityManager conMngr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = conMngr.getActiveNetworkInfo();
                if ("required".equals(netReq)) {
                    if (netInfo == null || !netInfo.isConnected() || !netInfo.isAvailable()) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(EidService.this, R.string.toastNoNetwork, Toast.LENGTH_LONG).show();
                            }
                        });
                        throw new AbortException("Network required");
                    }
                } else { //online by default
                    online = netInfo != null && netInfo.isConnected() && netInfo.isAvailable();
                }
            }
        }
        return online;
    }

    private void obtainUsbDevice() throws AbortException {
        ccidDevice = null;
        detachReceiver = null;

        Map<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (ccidDevice == null && deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if (CCID.isCCIDCompliant(device)) {
                ccidDevice = device;
            }
        }

        if (ccidDevice == null) {
            final BroadcastReceiver attachReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (CCID.isCCIDCompliant(device)) {
                        ccidDevice = device;
                        if (wait == null) {
                            Log.w(TAG, "Obtained USB device without wait handler");
                            return;
                        }
                        synchronized (wait) {
                            wait.notify();
                        }
                    } else {
                        final String product = getProductName(device);
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(EidService.this, String.format(EidService.this.getString(R.string.toastUnknownDevice), product), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            };
            registerReceiver(attachReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED), null, broadcast);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_stat_card)
                    .setContentTitle(EidService.this.getString(R.string.notiConnect))
                    .setContentText(EidService.this.getString(R.string.notiConnectMsg))
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS);
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(EidService.this, R.string.notiConnectMsg, Toast.LENGTH_LONG).show();
                }
            });

            wait = new Object();
            notifyMgr.notify(1, builder.build());
            synchronized (wait) {
                try {
                    wait.wait(USB_TIMEOUT);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for USB insert", e);
                }
            }
            wait = null;

            try {
                unregisterReceiver(attachReceiver);
            } catch (IllegalArgumentException iae) {
                Log.i(TAG, "Android claims the receiver isn't registered", iae);
            }
            if (ccidDevice == null) throw new AbortException("No reader connected");
        }

        //Listen to detach
        detachReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device.getDeviceName().equals(ccidDevice.getDeviceName())) {
                    //end the ongoing wait
                    if (wait != null) {
                        synchronized (wait) {
                            wait.notify();
                        }
                    }
                }
            }
        };
        registerReceiver(detachReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED), null, broadcast);
    }

    public void obtainUsbPermission() throws AbortException {
        if (!usbManager.hasPermission(ccidDevice)) {
            BroadcastReceiver grantReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (wait == null) {
                        Log.w(TAG, "Obtained USB permission without wait handler");
                        return;
                    }
                    synchronized (wait) {
                        wait.notify();
                    }
                }
            };

            wait = new Object();
            registerReceiver(grantReceiver, new IntentFilter(ACTION_USB_PERMISSION), null, broadcast);
            usbManager.requestPermission(ccidDevice, PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0));
            synchronized (wait) {
                try {
                    wait.wait(CONFIRM_TIMEOUT);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for USB grant permission", e);
                }
            }
            wait = null;
            try {
                unregisterReceiver(grantReceiver);
            } catch (IllegalArgumentException iae) {
                Log.i(TAG, "Android claims the receiver isn't registered", iae);
            }
            if (!usbManager.hasPermission(ccidDevice))
                throw new AbortException("No USB permission granted");
        }
    }

    public void obtainEidReader() throws IOException {
        eidCardReader = new EidCardReader(usbManager, ccidDevice);
        eidCardReader.open();
        eidCardReader.setPinCallback(new PinCallback() {
            @Override
            public char[] getPin(int retries) throws UserCancelException {
                BroadcastReceiver pinReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        pin = intent.getStringExtra(PinActivity.EXTRA_PIN);
                        if (wait == null) {
                            Log.w(TAG, "Obtained PIN without wait handler");
                            return;
                        }
                        synchronized (wait) {
                            wait.notify();
                        }
                    }
                };

                wait = new Object();
                registerReceiver(pinReceiver, new IntentFilter(ACTION_PIN_PROVIDED), null, broadcast);
                Intent dialogIntent = new Intent(getBaseContext(), PinActivity.class);
                dialogIntent.putExtra(PinActivity.EXTRA_RETRIES, retries);
                dialogIntent.putExtra(PinActivity.EXTRA_BROADCAST,
                        PendingIntent.getBroadcast(EidService.this, 0, new Intent(ACTION_PIN_PROVIDED), 0));
                dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplication().startActivity(dialogIntent);

                synchronized (wait) {
                    try {
                        wait.wait(PIN_TIMEOUT);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Interrupted while waiting for PIN", e);
                    }
                }
                wait = null;
                try {
                    unregisterReceiver(pinReceiver);
                } catch (IllegalArgumentException iae) {
                    Log.i(TAG, "Android claims the receiver isn't registered", iae);
                }
                if (pin == null) throw new UserCancelException("No PIN provided");
                try {
                    return pin.toCharArray();
                } finally {
                    pin = null;
                }
            }

            @Override
            public void pinPadStart(int retries) {
                Intent dialogIntent = new Intent(getBaseContext(), PinPadActivity.class);
                dialogIntent.putExtra(PinPadActivity.EXTRA_RETRIES, retries);
                dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplication().startActivity(dialogIntent);
            }

            @Override
            public void pinPadEnd() {
                sendBroadcast(new Intent(PinPadActivity.ACTION_PINPAD_END));
            }
        });
    }

    public void obtainEidCard() throws AbortException {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(EidService.this)
                .setSmallIcon(R.drawable.ic_stat_card)
                .setContentTitle(EidService.this.getString(R.string.notiCInit))
                .setContentText(EidService.this.getString(R.string.notiCInitMsg))
                .setCategory(Notification.CATEGORY_SERVICE);
        notifyMgr.notify(1, builder.build());
        if (!eidCardReader.isCardPresent()) {
            eidCardReader.setEidCardCallback(new EidCardCallback() {
                @Override
                public void inserted() {
                    if (wait == null) {
                        Log.w(TAG, "Obtained eID device without wait handler");
                        return;
                    }
                    synchronized (wait) {
                        wait.notify();
                    }
                }

                @Override
                public void removed() {

                }
            });

            final String msg = String.format(EidService.this.getString(R.string.notiInsertMsg),
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ? "" :
                            getProductNameFromOs(ccidDevice));
            builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_stat_card)
                    .setContentTitle(EidService.this.getString(R.string.notiInsert))
                    .setContentText(msg)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS);
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(EidService.this, msg, Toast.LENGTH_LONG).show();
                }
            });

            wait = new Object();
            notifyMgr.notify(1, builder.build());
            synchronized (wait) {
                try {
                    wait.wait(EID_TIMEOUT);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for eID", e);
                }
            }
            wait = null;
            eidCardReader.setEidCardCallback(null);

            if (!eidCardReader.isCardPresent()) throw new AbortException("No eID card present");
        }
    }

    private void diagnose(Message msg) throws RemoteException {
        boolean foundDevice = false;
        boolean foundCCID = false;
        boolean foundCard = false;
        boolean foundEid = false;
        StringBuilder builder = new StringBuilder();
        builder.append("Diagnose results: ");

        Map<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            ccidDevice = deviceIterator.next();

            Tracker t = ((EidSuiteApp) this.getApplication()).getTracker();
            t.send(new HitBuilders.EventBuilder("Reader Action", "Diagnose")
                    .setCustomDimension(1, getVendor())
                    .setCustomDimension(2, getProduct()).build());
            try {
                obtainUsbPermission();
                DeviceDescriptor dd = new DeviceDescriptor(usbManager, ccidDevice);
                builder.append("\r\n\r\n");
                builder.append(dd.toString());

                foundDevice = true;
                if (dd.hasCCID()) foundCCID = true;
                if (dd.hasCard()) foundCard = true;
                if (dd.hasEid()) foundEid = true;
            } catch (Exception e) {
                Log.w(TAG, "Failed to diagnose device", e);
                Tracker tracker = ((EidSuiteApp) this.getApplication()).getTracker();
                tracker.send(new HitBuilders.ExceptionBuilder()
                        .setDescription(new StandardExceptionParser(this, null).getDescription(Thread.currentThread().getName(), e))
                        .setFatal(false).build());
            } finally {
                ccidDevice = null;
            }
        }

        Message rsp = Message.obtain(null, DIAG_RSP, foundDevice ? 0 : 1, foundEid ? 0 : (foundCard ? 1 : (foundCCID ? 2 : 3)));
        rsp.getData().putString("Result", builder.toString());
        msg.replyTo.send(rsp);
    }

    private void readData(Message msg) throws RemoteException, IOException, CertificateException {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(EidService.this)
                .setSmallIcon(R.drawable.ic_stat_card)
                .setContentTitle(EidService.this.getString(R.string.notiRead))
                .setContentText(EidService.this.getString(R.string.notiReadMsg))
                .setCategory(Notification.CATEGORY_SERVICE);
        notifyMgr.notify(1, builder.build());
        Tracker t = ((EidSuiteApp) this.getApplication()).getTracker();
        t.send(new HitBuilders.EventBuilder("eID Action", "Read Data")
                .setCustomDimension(1, getVendor())
                .setCustomDimension(2, getProduct()).build());

        for (FileId fileId : FileId.values()) {
            if (msg.getData().size() == 0 || msg.getData().getBoolean(fileId.name(), false)) {
                byte[] bytes = eidCardReader.readFileRaw(fileId);
                Object data = fileId.parse(bytes);

                Message rsp = Message.obtain(null, DATA_RSP, fileId.getId(), 0);
                if (data instanceof Parcelable)
                    rsp.getData().putParcelable(fileId.name(), (Parcelable) data);
                else if (data instanceof byte[])
                    rsp.getData().putByteArray(fileId.name(), (byte[]) data);
                else if (data instanceof X509Certificate)
                    rsp.getData().putByteArray(fileId.name(), ((X509Certificate) data).getEncoded());

                msg.replyTo.send(rsp);
            }
        }
    }

    public void verifyPin(Message msg) throws IOException, UserCancelException {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(EidService.this)
                .setSmallIcon(R.drawable.ic_stat_card)
                .setContentTitle(EidService.this.getString(R.string.notiVerify))
                .setContentText(EidService.this.getString(R.string.notiVerifyMsg))
                .setCategory(Notification.CATEGORY_SERVICE);
        notifyMgr.notify(1, builder.build());
        Tracker t = ((EidSuiteApp) this.getApplication()).getTracker();
        t.send(new HitBuilders.EventBuilder("eID Action", "Verify PIN")
                .setCustomDimension(1, getVendor())
                .setCustomDimension(2, getProduct()).build());

        eidCardReader.verifyPin();
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(EidService.this, R.string.toastPinValid, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void authenticate(Message msg) throws IOException, RemoteException, UserCancelException {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(EidService.this)
                .setSmallIcon(R.drawable.ic_stat_card)
                .setContentTitle(EidService.this.getString(R.string.notiAuth))
                .setContentText(EidService.this.getString(R.string.notiAuthyMsg))
                .setCategory(Notification.CATEGORY_SERVICE);
        notifyMgr.notify(1, builder.build());
        Tracker t = ((EidSuiteApp) this.getApplication()).getTracker();
        t.send(new HitBuilders.EventBuilder("eID Action", "Authenticate")
                .setCustomDimension(1, getVendor())
                .setCustomDimension(2, getProduct()).build());

        byte[] hash = msg.getData().getByteArray("Hash");
        EidCardReader.DigestAlg digestAlg = EidCardReader.DigestAlg.valueOf(msg.getData().getString("DigestAlg", "SHA1"));
        byte[] signature = eidCardReader.signPkcs1(hash, digestAlg, EidCardReader.Key.AUTHENTICATION);

        Message rsp = Message.obtain(null, AUTH_RSP, 0, 0);
        rsp.getData().putByteArray("Signature", signature);
        msg.replyTo.send(rsp);
    }

    public void sign(Message msg, boolean online) throws IOException, DocumentException, GeneralSecurityException, RemoteException {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(EidService.this)
                .setSmallIcon(R.drawable.ic_stat_card)
                .setContentTitle(EidService.this.getString(R.string.notiSign))
                .setContentText(EidService.this.getString(R.string.notiSignMsg))
                .setCategory(Notification.CATEGORY_SERVICE);
        notifyMgr.notify(1, builder.build());
        Tracker t = ((EidSuiteApp) this.getApplication()).getTracker();
        t.send(new HitBuilders.EventBuilder("eID Action", "Sign")
                .setCustomDimension(1, getVendor())
                .setCustomDimension(2, getProduct()).build());

        Uri uri = msg.getData().getParcelable("input");
        String name = msg.getData().getString("name");
        String reason = msg.getData().getString("reason");
        String location = msg.getData().getString("location");
        String sign = msg.getData().getString("sign");

        String mimeType = getContentResolver().getType(uri);
        if ("application/pdf".equals(mimeType)) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(EidService.this);
            String prefix = sharedPref.getString(SettingsActivity.KEY_PREF_SIGN_PREFIX, getString(R.string.pref_sign_prefix_default));
            final boolean share = sharedPref.getBoolean(SettingsActivity.KEY_PREF_SIGN_SHARE, true);

            File tmp = File.createTempFile("eid", ".pdf", getCacheDir());
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), prefix + name);
            FileOutputStream fos = new FileOutputStream(file);

            //Prepare sign
            PdfReader reader = new PdfReader(getContentResolver().openInputStream(uri));
            PdfStamper stamper = PdfStamper.createSignature(reader, fos, '\0', tmp, true);
            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
            if (reason != null && reason.length() > 0) appearance.setReason(reason);
            if (location != null && location.length() > 0) appearance.setLocation(location);
            if (sign != null) appearance.setVisibleSignature(sign);

            ExternalSignature pks = new ExternalSignature() {
                @Override
                public String getHashAlgorithm() {
                    return "SHA256";
                }

                @Override
                public String getEncryptionAlgorithm() {
                    return "RSA";
                }

                @Override
                public byte[] sign(byte[] bytes) throws GeneralSecurityException {
                    MessageDigest messageDigest = MessageDigest.getInstance(getHashAlgorithm());
                    byte hash[] = messageDigest.digest(bytes);

                    try {
                        return eidCardReader.signPkcs1(hash, EidCardReader.DigestAlg.SHA256, EidCardReader.Key.NON_REPUDIATION);
                    } catch (Exception e) {
                        throw new GeneralSecurityException(e);
                    }
                }
            };
            ExternalDigest digest = new BouncyCastleDigest();
            Certificate[] chain = new Certificate[3];
            chain[0] = eidCardReader.readCertificate(FileId.SIGN_CERT);
            chain[1] = eidCardReader.readCertificate(FileId.INTCA_CERT);
            chain[2] = eidCardReader.readCertificate(FileId.ROOTCA_CERT);
            List<CrlClient> crlList = new LinkedList<CrlClient>();
            crlList.add(new CrlClientOnline(new Certificate[]{chain[1]})); //only request CRL of INT-CA
            OcspClient ocspClient = new OcspClientBouncyCastle();
            TSAClient tsaClient = new TSAClientBouncyCastle("http://tsa.belgium.be/connect");

            //Sign
            if (online) {
                MakeSignature.signDetached(appearance, digest, pks, chain, crlList, ocspClient, tsaClient, 0, MakeSignature.CryptoStandard.CADES);
            } else {
                MakeSignature.signDetached(appearance, digest, pks, chain, null, null, null, 0, MakeSignature.CryptoStandard.CADES);
            }

            //finish
            reader.close();
            stamper.close();
            fos.close();

            //make the file available
            MediaScannerConnection.scanFile(this, new String[]{file.toString()}, null, new MediaScannerConnection.OnScanCompletedListener() {
                @Override
                public void onScanCompleted(String path, Uri uri) {
                    if (uri != null && share) {
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
                        sendIntent.setType("application/pdf");
                        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(sendIntent);
                        } catch (ActivityNotFoundException ex) {
                            Toast.makeText(EidService.this, R.string.toastNoActivityFound, Toast.LENGTH_SHORT).show();
                            Tracker tracker = ((EidSuiteApp) EidService.this.getApplication()).getTracker();
                            tracker.send(new HitBuilders.ExceptionBuilder()
                                    .setDescription(new StandardExceptionParser(EidService.this, null).getDescription(Thread.currentThread().getName(), ex))
                                    .setFatal(false).build());
                        }
                    }
                }
            });

            //send the result
            Message rsp = Message.obtain(null, SIGN_RSP, 0, 0);
            rsp.getData().putString("output", file.getAbsolutePath());
            msg.replyTo.send(rsp);
        }
    }

    public void end(Message msg) throws RemoteException {
        if (detachReceiver != null) {
            try {
                unregisterReceiver(detachReceiver);
            } catch (IllegalArgumentException iae) {
                Log.i(TAG, "Android claims the receiver isn't registered", iae);
            }
            detachReceiver = null;
        }

        if (msg.replyTo != null) {
            Message end = Message.obtain(null, END, 0, 0);
            msg.replyTo.send(end);
        }
    }

    private String getVendor() {
        if (ccidDevice == null) return "unknown";

        return String.format("%X", ccidDevice.getVendorId());
    }

    private String getProduct() {
        if (ccidDevice == null) return "unknown";

        return String.format("%X", ccidDevice.getProductId());
    }

    private String getProductName(UsbDevice device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return getProductNameFromOs(device);
        } else {
            int deviceClass = device.getDeviceClass();
            if (deviceClass != UsbConstants.USB_CLASS_PER_INTERFACE) {
                return getClassName(deviceClass);
            } else {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    deviceClass = device.getInterface(i).getInterfaceClass();
                    if (builder.length() > 0) {
                        builder.append('/');
                    }
                    builder.append(getClassName(deviceClass));
                }
                return builder.toString();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getProductNameFromOs(UsbDevice device) {
        return device.getProductName();
    }

    private String getClassName(int deviceClass) {
        switch (deviceClass) {
            case UsbConstants.USB_CLASS_AUDIO:
                return this.getString(R.string.class_audio);
            case UsbConstants.USB_CLASS_CDC_DATA:
                return this.getString(R.string.class_cdc_data);
            case UsbConstants.USB_CLASS_COMM:
                return this.getString(R.string.class_comm);
            case UsbConstants.USB_CLASS_CONTENT_SEC:
                return this.getString(R.string.class_content_sec);
            case UsbConstants.USB_CLASS_HID:
                return this.getString(R.string.class_hid);
            case UsbConstants.USB_CLASS_HUB:
                return this.getString(R.string.class_hub);
            case UsbConstants.USB_CLASS_MASS_STORAGE:
                return this.getString(R.string.class_mass_storage);
            case UsbConstants.USB_CLASS_MISC:
                return this.getString(R.string.class_misc);
            case UsbConstants.USB_CLASS_PHYSICA:
                return this.getString(R.string.class_physica);
            case UsbConstants.USB_CLASS_PRINTER:
                return this.getString(R.string.class_printer);
            case UsbConstants.USB_CLASS_STILL_IMAGE:
                return this.getString(R.string.class_still_image);
            case UsbConstants.USB_CLASS_VIDEO:
                return this.getString(R.string.class_video);
            case UsbConstants.USB_CLASS_WIRELESS_CONTROLLER:
                return this.getString(R.string.class_wireless_controller);
            default:
                return this.getString(R.string.class_unknown);
        }
    }

    private void processError(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        if (root instanceof UserCancelException) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(EidService.this, R.string.toastEidCanceled, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (root instanceof AbortException) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(EidService.this, R.string.toastEidAborted, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (root instanceof CardBlockedException) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(EidService.this, R.string.toastEidBlocked, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(EidService.this, R.string.toastEidFailed, Toast.LENGTH_LONG).show();
                }
            });
        }

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(EidService.this);
        boolean fail = sharedPref.getBoolean(SettingsActivity.KEY_PREF_FAIL, false);
        if (fail) {
            SharedPreferences.Editor edit = sharedPref.edit();
            edit.putBoolean(SettingsActivity.KEY_PREF_FAIL, false);
            edit.commit();
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            else
                throw new RuntimeException(e);
        } else {
            StandardExceptionParser ep = new StandardExceptionParser(null, Collections.singleton(this.getClass().getPackage().getName())) {
                @Override
                protected String getDescription(Throwable cause, StackTraceElement element, String threadName) {
                    String msg = super.getDescription(cause, element, threadName);
                    if (cause instanceof CCIDException) {
                        CCIDException ccidError = (CCIDException) cause;
                        msg += String.format(" [CCID: %x %x]", ccidError.getStatus(), ccidError.getError());
                    } else if (cause instanceof APDUException) {
                        APDUException apduException = (APDUException) cause;
                        msg += String.format(" [APDU: %x %x]", apduException.getSW1(), apduException.getSW2());
                    }
                    return msg;
                }
            };

            Tracker t = ((EidSuiteApp) this.getApplication()).getTracker();
            t.send(new HitBuilders.ExceptionBuilder()
                    .setDescription(ep.getDescription(Thread.currentThread().getName(), e))
                    .setFatal(false)
                    .setCustomDimension(1, getVendor())
                    .setCustomDimension(2, getProduct()).build());
        }
    }
}
