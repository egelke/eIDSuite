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
package net.egelke.android.eid.reader;

import net.egelke.android.eid.usb.diagnostic.Device;

import org.spongycastle.util.encoders.Hex;

public class Diagnose {

    private Device device;
    private byte[] atr;

    public Diagnose(Device device, byte[] atr) {
        this.device = device;
        this.atr = atr;
    }

    public boolean hasCard() { return atr != null; }
    public boolean hasEid() {
        return EidCardReader.isEid(atr);
    }

    @Override
    public String toString() {
        if (atr == null) {
            return device.toString();
        } else {
            return String.format("%s\r\nATR: %s", device.toString(), Hex.toHexString(atr));
        }
    }
}
