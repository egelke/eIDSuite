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


import java.io.IOException;

public class APDUException extends IOException {

    private byte SW1;
    private byte SW2;

    public APDUException(String msg, byte SW1, byte SW2) {
        super(msg);
        this.SW1 = SW1;
        this.SW2 = SW2;
    }

    public byte getSW1() {
        return SW1;
    }

    public byte getSW2() {
        return SW2;
    }
}
