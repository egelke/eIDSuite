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
package net.egelke.android.eid.usb.diagnostic;


import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import java.util.Collections;
import java.util.List;

public class Device {

    private UsbDevice d;
    private List<CCID> cs;

    public Device(UsbDevice device, UsbDeviceConnection connection) {
        this.d = device;
        try {
            this.cs = CCID.Parse(connection.getRawDescriptors());
        } catch (Exception e) {
            this.cs = Collections.emptyList();
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(
            "Device: Name=%s, VendorId=%X, ProductId=%X, Class=%X, Subclass=%X, Protocol=%X",
            d.getDeviceName(), d.getVendorId(), d.getProductId(),
            d.getDeviceClass(), d.getDeviceSubclass(), d.getDeviceProtocol())
        );

        for (int i = 0; i < d.getInterfaceCount(); i++) {
            builder.append("\r\n");
            builder.append(new Interface(d.getInterface(i)).toString());
        }

        for (CCID c : cs) {
            builder.append("\r\n");
            builder.append(c.toString());
        }

        return builder.toString();
    }
}
