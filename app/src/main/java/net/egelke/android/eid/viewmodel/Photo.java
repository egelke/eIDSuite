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
import android.graphics.drawable.Drawable;

import java.io.ByteArrayInputStream;

public class Photo {

    private byte[] data;

    public Photo() {

    }

    public Photo(Context ctx, byte[] data) {
        this.data = data;
    }

    public Drawable getDrawable() {
        return data != null ? Drawable.createFromStream(new ByteArrayInputStream(data), "idPic") : null;
    }
}
