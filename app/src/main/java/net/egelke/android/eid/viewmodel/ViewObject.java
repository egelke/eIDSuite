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
package net.egelke.android.eid.viewmodel;


import android.content.Context;

import java.util.LinkedList;
import java.util.List;

public abstract class ViewObject {

    protected Context ctx;
    private boolean updating = false;
    private List<UpdateListener> listeners = new LinkedList<UpdateListener>();

    protected ViewObject(Context ctx) {
        this.ctx = ctx;
    }

    public boolean isUpdating() {
        return updating;
    }

    public void addListener(UpdateListener listener) {
        listeners.add(listener);
        onUpdate();
    }

    public void removeListener(UpdateListener listener) {
        listeners.remove(listener);
    }

    public void startUpdate() {
        if (!updating) {
            updating = true;
            onUpdate();
        }
    }

    public void endUpdate() {
        if (updating) {
            updating = false;
            onUpdate();
        }
    }

    public void onUpdate() {
        for(UpdateListener listener : listeners) {
            listener.onUpdate(this);
        }
    }
}
