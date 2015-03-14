/*
    This file is part of eID Suite.
    Copyright (C) 2014 Egelke BVBA

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
package net.egelke.android.eid.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CCID implements Closeable {

    private static final String TAG = "net.egelke.android.eid";

    public static enum SlotStatus {
        Active,
        Inactive,
        Missing
    }

    public static boolean isCCIDCompliant(UsbDevice usbDevice) {
        if (usbDevice.getDeviceClass() == UsbConstants.USB_CLASS_CSCID) {
            return true;
        } else if (usbDevice.getDeviceClass() == UsbConstants.USB_CLASS_PER_INTERFACE) {
            for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
                if (usbDevice.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_CSCID) {
                    return true;
                }
            }
        }
        return false;
    }

    //Input property
    private UsbDevice usbDevice;
    private UsbManager usbManager;

    //connection
    private UsbInterface usbInterface;
    private UsbDeviceConnection usbConnection;

    //USB streams
    private UsbEndpoint usbOut;
    private UsbEndpoint usbIn;
    private UsbEndpoint usbInterrupt;

    //State properties
    private int sequence;
    private ScheduledFuture stateRequester;

    //callback
    private CardCallback callback;

    public CardCallback getCallback()
    {
        return callback;
    }

    public void setCallback(CardCallback value)
    {
        callback = value;
    }

    public synchronized boolean isOpen() {
        return usbConnection != null;
    }

    public CCID(UsbManager manager, final UsbDevice device)
    {
        usbManager = manager;
        usbDevice = device;
    }

    public synchronized void open() throws IOException {
        if (usbDevice == null) throw new IllegalArgumentException("Device can't be null");

        for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
            UsbInterface usbIf = usbDevice.getInterface(i);
            if (usbIf.getInterfaceClass() == UsbConstants.USB_CLASS_CSCID) {
                usbInterface = usbIf;
            }
        }
        if (usbInterface == null)
            throw new IllegalStateException("The device hasn't a smart card reader");

        usbConnection = usbManager.openDevice(usbDevice);
        usbConnection.claimInterface(usbInterface, true);
        sequence = 0;

        byte[] rawDescriptor = usbConnection.getRawDescriptors();

        //Get the interfaces
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint usbEp = usbInterface.getEndpoint(i);
            if (usbEp.getDirection() == UsbConstants.USB_DIR_IN && usbEp.getType() == UsbConstants.USB_ENDPOINT_XFER_INT && usbEp.getAttributes() == 0x03) {
                usbInterrupt = usbEp;
            }
            if (usbEp.getDirection() == UsbConstants.USB_DIR_OUT && usbEp.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && usbEp.getAttributes() == 0x02) {
                usbOut = usbEp;
            }
            if (usbEp.getDirection() == UsbConstants.USB_DIR_IN && usbEp.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && usbEp.getAttributes() == 0x02) {
                usbIn = usbEp;
            }
        }

        //Listen for state changes
        if (usbInterrupt != null && callback != null) {
            ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
            stateRequester = ses.scheduleWithFixedDelay(new StateRequesterTask(), 100, 1000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (stateRequester != null) {
            stateRequester.cancel(false);
            stateRequester = null;
        }
        if (usbInterface != null) {
            usbConnection.releaseInterface(usbInterface);
            usbConnection.close();
            usbConnection = null;
            usbInterface = null;
        }
    }

    private class StateRequesterTask implements Runnable {

        @Override
        public void run() {
            byte[] buffer = new byte[10];
            int count = usbConnection.bulkTransfer(usbInterrupt, buffer, buffer.length, 100);

            if (count < 2) {
                Log.v(TAG, "CCID interrupt read returned an invalid length: " + count);
                return;
            }

            if (buffer[0] == (byte) 0x51) {
                Log.v(TAG, String.format("CCID interrupt read returned an error : %x", buffer[3]));
            }

            if (buffer[0] != (byte) 0x50) {
                Log.v(TAG, String.format("CCID interrupt read returned an invalid type: %x", buffer[0]));
                return;
            }

            Log.d(TAG, String.format("CCID interrupt read returned the following status: %x", buffer[1]));
            if ((buffer[1] & (byte) 0x03) == (byte) 0x02)
                callback.removed();
            else if ((buffer[1] & (byte) 0x03) == (byte) 0x03)
                callback.inserted();
        }
    }

    public synchronized byte[] powerOn() throws IOException {
        byte[] rsp = transmit((byte) 0x62/*IccPowerOn*/, null);
        return parseDataBlock(rsp);
    }

    public synchronized void powerOff() throws IOException {
        byte[] rsp = transmit((byte)0x63/*IccPowerOff*/, null);
        parseSlotStatus(rsp);
    }

    public synchronized byte[] transmitApdu(byte[] apdu) throws IOException {
        byte[] rsp = transmit((byte)0x6F /*XfrBlock*/, apdu);
        return parseDataBlock(rsp);
    }

    private byte[] transmit(byte cmd, byte[] data) throws IOException {
        sequence = (sequence + 1) % 0xFF;
        byte[] req = new byte[(data == null ? 0 : data.length) + 10];
        req[0] = cmd;
        req[1] = (byte) (req.length - 10); //(data) length
        req[2] = 0x00; //length, continued (we don't support long lenghts)
        req[3] = 0x00; //length, continued (we don't support long lenghts)
        req[4] = 0x00; //length, continued (we don't support long lenghts)
        req[5] = (byte) 0; //slot
        req[6] = (byte) sequence;
        req[7] = 0x00; //Bot used (Xfr: Block Waiting Timeout)
        req[8] = 0x00; //Not used (Xfr: Param (short APDU))
        req[9] = 0x00; //Not used (Xfr: Param, continued (short APDU))
        if (data != null)
            System.arraycopy(data, 0, req, 10, data.length);

        int count;

        count = usbConnection.bulkTransfer(usbOut, req, req.length, 5000);
        if (count < 0) {
            throw new IOException("Failed to send data to the CCID reader");
        }

        byte[] rsp = new byte[268];
        count = usbConnection.bulkTransfer(usbIn, rsp, rsp.length, 10000);
        if (count < 0) {
            throw new IOException("Failed to read data to the CCID reader");
        }

        byte[] newRsp = new byte[count];
        System.arraycopy(rsp, 0, newRsp, 0, count);

        return newRsp;
    }

    private SlotStatus parseSlotStatus(byte[] rsp) throws IOException {
        validateResponse(rsp, (byte)0x81, true);

        switch(rsp[7] & (byte) 0xC0) {
            case 0x80:
                return SlotStatus.Missing;
            case 0x40:
                return SlotStatus.Inactive;
            case 0x00:
                return SlotStatus.Active;
            default:
                throw new IOException("Invalid slot status received from teh CCID reader");
        }
    }

    private byte[] parseDataBlock(byte[] rsp) throws IOException {
        validateResponse(rsp, (byte)0x80, false);

        byte[] newRsp = new byte[rsp.length - 10];
        System.arraycopy(rsp, 10, newRsp, 0, newRsp.length);
        return newRsp;
    }

    private void validateResponse(byte[] rsp, byte type, boolean ignoreSlotStatus) throws IOException {
        if (rsp.length < 10) {
            throw new IOException("The response is to short");
        }

        if (rsp[0] != type) {
            throw new IOException("Illegal CCID reader response (wrong type)");
        }
        if (rsp[6] != (byte)sequence) {
            throw new IOException("Illegal CCID reader response (wrong sequence)");
        }
        if ((rsp[7] & (byte)0x03) == 0x01 && rsp[8] == 0x00) {
            throw new UnsupportedOperationException("command not supported by the reader");
        }
        if ((rsp[7] & (byte)0x03) != 0x00) {
            throw new IOException(String.format("Command Error returned by the CCID reader: %x", rsp[8]));
        }
        if (!ignoreSlotStatus) {
            if ((rsp[7] & (byte)0xC0) != 0x00) {
                throw new IOException(String.format("Slot Error returned by the CCID reader: %x", rsp[7] & 0xC0));
            }
        }
    }
}
