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
package net.egelke.android.eid.viewmodel;


import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

public final class ViewModel {

    private ViewModel() { }

    private static Hashtable<String, Object> data = new Hashtable<String, Object>();

    private static List<UpdateListener> listeners = new LinkedList<UpdateListener>();

    public static void start(String key) {
        for(UpdateListener listener : listeners) {
            listener.startUpdate(key);
        }
    }

    public static void setData(String key, Object value) {
        Object old = data.put(key, value);
        for(UpdateListener listener : listeners) {
            listener.updateFinished(key, old, value);
        }
    }

    public static Object getData(String key) {
        return data.get(key);
    }

    public static void addListener(UpdateListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(UpdateListener listener) {
        listeners.remove(listener);
    }
}
