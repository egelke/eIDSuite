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
package net.egelke.android.eid.view;

import android.app.Activity;
import android.os.Bundle;

public class SettingsActivity extends Activity {

    public static final String KEY_PREF_FAIL = "pref_fail";
    public static final String KEY_PREF_VIEW_SHARE = "pref_view_share";
    public static final String KEY_PREF_SIGN_NETWORK = "pref_sign_network";
    public static final String KEY_PREF_SIGN_PREFIX = "pref_sign_prefix";
    public static final String KEY_PREF_SIGN_SHARE = "pref_sign_share";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

}
