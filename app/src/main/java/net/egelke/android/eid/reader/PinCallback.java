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
package net.egelke.android.eid.reader;


public interface PinCallback {
    /**
     * Called to get the PIN from the user.
     * The method may throw an exception to cancel.
     * @param retries The number of retries left, -1 is unknown
     * @return The PIN as chars ('0', '1', ...)
     */
    char[] getPin(int retries);
}
