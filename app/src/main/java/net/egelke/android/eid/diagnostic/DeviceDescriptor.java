/*
    This file is part of eID Suite.
    Copyright (C) 2015 Egelke BVBA

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
package net.egelke.android.eid.diagnostic;


import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import net.egelke.android.eid.reader.EidCardReader;
import net.egelke.android.eid.usb.CCID;

import org.spongycastle.util.encoders.Hex;

public class DeviceDescriptor {

    private UsbManager m;
    private UsbDevice d;

    private boolean hasEid;
    private boolean hasCard;
    private boolean hasCCID;

    private String msg;

    public boolean hasEid() {
        return hasEid;
    }

    public boolean hasCard() {
        return hasCard;
    }

    public boolean hasCCID() {
        return hasCCID;
    }

    public DeviceDescriptor(UsbManager usbManager, UsbDevice usbDevice) {
        this.m = usbManager;
        this.d = usbDevice;

        populate();
    }

    public void populate() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(
            "Device: Name=%s, VendorId=%X, ProductId=%X, Class=%X, Subclass=%X, Protocol=%X",
            d.getDeviceName(), d.getVendorId(), d.getProductId(),
            d.getDeviceClass(), d.getDeviceSubclass(), d.getDeviceProtocol())
        );

        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface in = d.getInterface(i);

            builder.append("\r\n");
            builder.append(new InterfaceDescriptor(in).toString());

            for (int j=0; j < in.getEndpointCount(); j++) {
                builder.append("\r\n");
                builder.append(new EndPointDescriptor(in.getEndpoint(j)).toString());
            }

            if (in.getInterfaceClass() == UsbConstants.USB_CLASS_CSCID) {
                hasCCID = true;
                builder.append("\r\n");
                CCID c = new CCID(m, d, in);
                try {
                    c.open();
                    try {
                        CCID.Response rsp = c.transmit((byte) 0x62/*IccPowerOn*/, null, (byte) 0x80/*DataBlock*/, false);
                        hasCard = rsp.data != null && rsp.data.length > 0;
                        hasEid = hasCard && EidCardReader.isEid(rsp.data);
                        builder.append(String.format("ICC PowerOn Rsp: Param=%X, Data=%s", rsp.param, Hex.toHexString(rsp.data)));
                    } catch (Exception e) {
                        builder.append(String.format("ICC PowerOn Failed: %s", e));
                    } finally {
                        c.close();
                    }
                } catch (Exception e) {
                    builder.append(String.format("ICC Open Failed: %s", e));
                }
            }
        }

        UsbDeviceConnection connection = m.openDevice(d);
        try {
            for (CCIDDescriptor c : CCIDDescriptor.Parse(connection.getRawDescriptors())) {
                builder.append("\r\n");
                builder.append(c.toString());
            }
        } catch (Exception e) {

        } finally {
            connection.close();
        }

        msg = builder.toString();
    }

    @Override
    public String toString() {
        return msg;
    }
}
