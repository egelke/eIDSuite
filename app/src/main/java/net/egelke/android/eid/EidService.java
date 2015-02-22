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
package net.egelke.android.eid;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import net.egelke.android.eid.belpic.FileId;
import net.egelke.android.eid.reader.EidCardCallback;
import net.egelke.android.eid.reader.EidCardReader;
import net.egelke.android.eid.usb.CCID;

import java.io.IOException;
import java.io.Serializable;
import java.security.cert.CertificateException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class EidService extends Service {

    private static final String TAG = "net.egelke.android.eid";
    private static final String ACTION_USB_PERMISSION = "net.egelke.android.eid.USB_PERMISSION";
    private static final long USB_TIMEOUT = 10 * 60 * 1000; //10 MIN
    private static final long EID_TIMEOUT = 10 * 60 * 1000; //10 MIN
    private static final long CONFIRM_TIMEOUT = 1 * 60 * 1000; //1MIN

    //Internal
    private static final int QUIT = 0;

    //Actions
    public static final int READ_DATA = 1;
    public static final int VERIFY_CARD = 3;
    public static final int SIGN_PDF = 11;

    //Action Response
    public static final int DATA_RSP = 101;

    private class IncomingHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            //Stop if requested
            if (destroyed) {
                Looper.myLooper().quit();
                return;
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(EidService.this)
                    .setSmallIcon(R.drawable.ic_stat_card)
                    .setContentTitle("eID Service: Reader initialization")
                    .setContentText("Your eID reader is being initialized")
                    .setCategory(Notification.CATEGORY_SERVICE);
            PowerManager.WakeLock wl = powerMgr.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);

            wl.acquire();
            startForeground(1, builder.build());
            try {
                obtainUsbDevice();
                obtainUsbPermission();
                obtainEidReader();
                try {
                    builder = new NotificationCompat.Builder(EidService.this)
                            .setSmallIcon(R.drawable.ic_stat_card)
                            .setContentTitle("eID Service: Card initialization")
                            .setContentText("Your eID card is being initialized")
                            .setCategory(Notification.CATEGORY_SERVICE);
                    notifyMgr.notify(1, builder.build());
                    obtainEidCard();
                    switch (msg.what) {
                        case READ_DATA:
                            builder = new NotificationCompat.Builder(EidService.this)
                                    .setSmallIcon(R.drawable.ic_stat_card)
                                    .setContentTitle("eID Service: Card read")
                                    .setContentText("The data (e.g. identity, address, photo) of your eID card is being read")
                                    .setCategory(Notification.CATEGORY_SERVICE);
                            notifyMgr.notify(1, builder.build());
                            for (FileId fileId : FileId.values()) {
                                replyIfNeeded(msg, fileId);
                            }
                        case SIGN_PDF:
                            //TODO
                            break;
                        default:
                            super.handleMessage(msg);
                    }
                } finally {
                    eidCardReader.close();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "Failed to process message due to illegal state", e);
            } catch (Exception e) {
                Log.e(TAG, "Failed to process message", e);
            } finally {
                wl.release();
                stopForeground(true);
            }
        }

        private void replyIfNeeded(Message msg, FileId file) throws RemoteException, IOException, CertificateException {
            if (msg.getData().getBoolean(file.name(), false)) {
                byte[] bytes = eidCardReader.readFileRaw(file);
                Object data = file.parse(bytes);

                Message rsp = Message.obtain(null, DATA_RSP, file.getId(), 0);
                if (data instanceof Parcelable)
                    rsp.getData().putParcelable(file.name(), (Parcelable)data);
                else if (data instanceof byte[])
                    rsp.getData().putByteArray(file.name(), (byte[])data);
                else if (data instanceof Serializable)
                    rsp.getData().putSerializable(file.name(), (Serializable)data);

                msg.replyTo.send(rsp);
            }
        }
    }

    private class BroadcastThread extends Thread {

        public BroadcastThread() {
            setDaemon(true);
            setName("EidServiceBCThread");
        }

        public void run() {
            Looper.prepare();
            broadcast = new Handler();
            Looper.loop();
        }
    }
    private class MessageThread extends Thread {

        public MessageThread() {
            setName("EidServiceMsgThread");
        }

        public void run() {
            Looper.prepare();
            messenger = new Messenger(new IncomingHandler());
            Looper.loop();
        }
    }

    //permanent properties
    private Handler broadcast = null;
    private Messenger messenger = null;
    private UsbManager usbManager;
    private NotificationManager notifyMgr;
    private PowerManager powerMgr;
    private int notifyId = 10;
    private boolean destroyed;
    private Thread messageThread;

    //temporally properties
    private UsbDevice ccidDevice;
    private EidCardReader eidCardReader;
    private Object wait;

    @Override
    public void onCreate() {
        Log.d(TAG, "EidService onCreate " + this);
        super.onCreate();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        notifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        powerMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);

        (new BroadcastThread()).start();
        messageThread = new MessageThread();
        messageThread.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "EidService onBind " + this);
        int count = 0;
        while (count < 5 && (messenger == null || broadcast == null)) {
            SystemClock.sleep(++count * 10);
        }
        return messenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "EidService onUnbind " + this);
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "EidService onRebind " + this);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "EidService onDestroy " + this);

        destroyed = true;

        //make sure there is another message
        try {
            messenger.send(Message.obtain(null, QUIT));
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to send the quit message", e);
        }
        //remove any lock
        if (wait != null) {
            synchronized (wait) {
                wait.notify();
            }
        }

        //wait for the message thread to stop (give up after 1s)
        try {
            messageThread.join(1000);
        } catch (Exception e) {
            Log.w(TAG, "Failed wait for the message thread to stop", e);
        }


        super.onDestroy();
    }

    private void obtainUsbDevice() {
        ccidDevice = null;
        List<String> unsupportedDevices = new LinkedList<>();
        Map<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (ccidDevice == null && deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if (CCID.isCCIDCompliant(device)) {
                ccidDevice = device;
            } else {
                unsupportedDevices.add(getProductName(device));
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
                        String product = getProductName(device);
                        NotificationCompat.Builder builder = new NotificationCompat.Builder(EidService.this)
                                .setSmallIcon(R.drawable.ic_stat_card)
                                .setContentTitle(product + " is an unknown device")
                                .setContentText("The " + product + " you connected isn't a supported (CCID) reader")
                                .setCategory(Notification.CATEGORY_SERVICE)
                                .setPriority(NotificationCompat.PRIORITY_HIGH);
                        notifyMgr.notify(notifyId++, builder.build());
                    }
                }
            };
            registerReceiver(attachReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED), null, broadcast);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_stat_card)
                    .setContentTitle("eID Service: Connect your reader")
                    .setContentText("Please connect your eID reader to the tablet/phone")
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS);
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle("eID Service: Connect your eID reader");
            inboxStyle.setSummaryText("Please connect your eID reader to the tablet/phone");
            if (unsupportedDevices.size() == 0) {
                inboxStyle.addLine("No USB devices detected");
            } else {
                inboxStyle.addLine("Incompatible USB devices detected:");
            }
            for (String device : unsupportedDevices) {
                inboxStyle.addLine("\t" + device);
            }
            builder.setStyle(inboxStyle);
            notifyMgr.notify(1, builder.build());

            wait = new Object();
            synchronized (wait) {
                try {
                    wait.wait(USB_TIMEOUT);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for USB insert", e);
                }
            }
            wait = null;

            unregisterReceiver(attachReceiver);
            if (ccidDevice == null) throw new IllegalStateException("No reader connected");
        }
    }

    public void obtainUsbPermission() {
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

            registerReceiver(grantReceiver, new IntentFilter(ACTION_USB_PERMISSION), null, broadcast);
            usbManager.requestPermission(ccidDevice, PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0));
            wait = new Object();
            synchronized (wait) {
                try {
                    wait.wait(CONFIRM_TIMEOUT);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for USB grant permission", e);
                }
            }
            wait = null;
            unregisterReceiver(grantReceiver);
            if (!usbManager.hasPermission(ccidDevice)) throw new IllegalStateException("No USB permission granted");
        }
    }

    public void obtainEidReader() throws IOException {
        eidCardReader = new EidCardReader(usbManager, ccidDevice);
        eidCardReader.open();
    }

    public void obtainEidCard() {
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

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_stat_card)
                    .setContentTitle("eID Service: Insert your eID")
                    .setContentText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            ? "Please insert your eID in your " + getProductNameFromOs(ccidDevice) + " eID reader"
                            : "Please insert your eID in your eID reader")
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS);
            notifyMgr.notify(1, builder.build());

            wait = new Object();
            synchronized (wait) {
                try {
                    wait.wait(EID_TIMEOUT);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for eID", e);
                }
            }
            wait = null;
            eidCardReader.setEidCardCallback(null);

            if (!eidCardReader.isCardPresent()) throw new IllegalStateException("No eID card present");
        }
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
}
