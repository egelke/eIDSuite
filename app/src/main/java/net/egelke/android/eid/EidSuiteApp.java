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
package net.egelke.android.eid;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.view.View;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import net.egelke.android.eid.viewmodel.ViewObject;

import java.lang.reflect.Constructor;
import java.util.Hashtable;

public class EidSuiteApp extends Application {

    private static final String TAG = "net.egelke.android.eid";

    private Tracker mTracker;
    private Hashtable<String, ViewObject> data = new Hashtable<String, ViewObject>();

    public synchronized Tracker getTracker() {
       if (mTracker == null) {
           GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
           mTracker = analytics.newTracker(R.xml.app_tracker);
       }
       return mTracker;
    }

    public synchronized <T extends ViewObject> T getViewObject(Class<T> clazz) {
        if (!data.containsKey(clazz.getName())) {
            try {
                Constructor<T> constructor = clazz.getConstructor(Context.class);
                data.put(clazz.getName(), constructor.newInstance(getApplicationContext()));
            } catch (Exception e) {
                Log.w(TAG, "failed to empty viewstate", e);
            }
        }
        return (T) data.get(clazz.getName());
    }

}
