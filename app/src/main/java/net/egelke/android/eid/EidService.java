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
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import net.egelke.android.eid.belpic.FileId;
import net.egelke.android.eid.reader.EidCardCallback;
import net.egelke.android.eid.reader.EidCardReader;
import net.egelke.android.eid.usb.CCID;
import net.egelke.android.eid.view.R;

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
            if (msg.what == QUIT) {
                Looper.myLooper().quit();
                return;
            }

            try {
                obtainUsbDevice();
                obtainUsbPermission();
                obtainEidReader();
                try {
                    obtainEidCard();
                    switch (msg.what) {
                        case READ_DATA:
                            for(FileId fileId : FileId.values()) {
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
    private int notifyId = 10;

    //temporally properties
    private UsbDevice ccidDevice;
    private EidCardReader eidCardReader;

    @Override
    public void onCreate() {
        super.onCreate();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        notifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        (new BroadcastThread()).start();
        (new MessageThread()).start();
    }

    @Override
    public void onDestroy() {
        try {
            messenger.send(Message.obtain(null, QUIT));
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to send the quit message", e);
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        int count = 0;
        while (count < 5 && (messenger == null || broadcast == null)) {
            SystemClock.sleep(++count * 10);
        }
        return messenger.getBinder();
    }

    private void obtainUsbDevice() {
        final Object wait = new Object();
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
            BroadcastReceiver attachReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (CCID.isCCIDCompliant(device)) {
                        ccidDevice = device;
                        synchronized (wait) {
                            wait.notify();
                        }
                    } else {
                        String product = getProductName(device);
                        NotificationCompat.Builder builder = new NotificationCompat.Builder(EidService.this)
                                .setSmallIcon(R.drawable.ic_stat_gen)
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
                    .setSmallIcon(R.drawable.ic_stat_gen)
                    .setContentTitle("Connect your eID reader")
                    .setContentText("Please connect your eID reader to the tablet/phone")
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(NotificationCompat.PRIORITY_MAX);
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle("Connect eID reader");
            inboxStyle.setSummaryText("Please connect your eID reader, " +
                    "see details for detected but unsupported devices");
            for (String device : unsupportedDevices) {
                inboxStyle.addLine(device);
            }
            builder.setStyle(inboxStyle);
            notifyMgr.notify(1, builder.build());

            synchronized (wait) {
                try {
                    wait.wait(/* 10 min */ 10 * 60 * 1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for USB insert", e);
                }
            }

            notifyMgr.cancel(1);
            unregisterReceiver(attachReceiver);

            if (ccidDevice == null) throw new IllegalStateException("No reader connected");
        }

    }

    public void obtainUsbPermission() {
        if (!usbManager.hasPermission(ccidDevice)) {
            final Object wait = new Object();
            BroadcastReceiver grantReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    synchronized (wait) {
                        wait.notify();
                    }
                }
            };

            registerReceiver(grantReceiver, new IntentFilter(ACTION_USB_PERMISSION), null, broadcast);
            usbManager.requestPermission(ccidDevice, PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0));
            synchronized (wait) {
                try {
                    wait.wait(/* 1 minute */60 * 1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for USB grant permission", e);
                }
            }
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
            final Object wait = new Object();
            eidCardReader.setEidCardCallback(new EidCardCallback() {
                @Override
                public void inserted() {
                    synchronized (wait) {
                        wait.notify();
                    }
                }

                @Override
                public void removed() {

                }
            });

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_stat_gen)
                    .setContentTitle("Insert your eID")
                    .setContentText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            ? "Please insert your eID in the " + getProductNameFromOs(ccidDevice) + " reader"
                            : "Please insert your eID in the reader")
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(NotificationCompat.PRIORITY_MAX);
            notifyMgr.notify(2, builder.build());

            synchronized (wait) {
                try {
                    wait.wait(/* 10 minute */ 10 * 60 * 1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for eID", e);
                }
            }
            notifyMgr.cancel(2);
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
