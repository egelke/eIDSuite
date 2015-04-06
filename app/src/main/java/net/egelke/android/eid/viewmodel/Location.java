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
import android.location.*;
import android.location.Address;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.util.List;

public class Location extends ViewObject {

    private static final String TAG = "net.egelke.android.eid";

    private class Process extends AsyncTask<android.location.Location, Void, String> {

        @Override
        protected String doInBackground(android.location.Location... params) {
            try {
                Geocoder gcd = new Geocoder(ctx);
                List<Address> address = gcd.getFromLocation(params[0].getLatitude(), params[0].getLongitude(), 1);
                if (address.size() > 0) {
                    return address.get(0).getLocality();
                } else {
                    return null;
                }
            } catch (IOException ioe) {
                Log.e(TAG, "Failed to obtain location");
                return null;
            }
        }

        @Override
        protected void onPostExecute(String s) {
            city = s;
            endUpdate();
        }
    }

    private String city;

    public Location(Context ctx) {
        super(ctx);
    }

    public void setLocation(android.location.Location location) {
        if (location != null) {
            (new Process()).execute(location);
        }
    }

    public String getCity() {
        return city == null ? "" : city;
    }
}
