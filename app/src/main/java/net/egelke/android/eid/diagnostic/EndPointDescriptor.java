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


import android.hardware.usb.UsbEndpoint;

public class EndPointDescriptor {

    private UsbEndpoint ep;

    public EndPointDescriptor(UsbEndpoint ep) {
        this.ep = ep;
    }

    @Override
    public String toString() {
        return String.format("EndPoint: Address=%X, Number=%d, Direction=%X, " +
                        "Attributes=%X, Type=%X, " +
                        "Interval=%X, MaxPacketSize=%X",
                ep.getAddress(), ep.getEndpointNumber(), ep.getDirection(),
                ep.getAttributes(), ep.getType(),
                ep.getInterval(), ep.getMaxPacketSize());
    }
}
