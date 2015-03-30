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
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import net.egelke.android.eid.R;


public class PinPadActivity extends Activity {

    private static final String TAG = "net.egelke.android.eid";

    public static final String ACTION_PINPAD_END = "net.egelke.android.eid.PINPAD_END";
    public static final String EXTRA_RETRIES = "RETRIES";


    BroadcastReceiver bcReciever = new BroadcastReceiver() {

        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (ACTION_PINPAD_END.equals(intent.getAction())) {
                finish();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pinpad);
        setFinishOnTouchOutside(false);

        int retries = getIntent().getIntExtra(EXTRA_RETRIES, -1);

        TextView retriesLeft = (TextView) findViewById(R.id.retries);
        if (retries >= 0) {
            retriesLeft.setText(String.format(this.getString(R.string.pinRetries), retries));
        } else {
            retriesLeft.setText("");
        }
        registerReceiver(bcReciever, new IntentFilter(ACTION_PINPAD_END));
    }

    @Override
    public void onBackPressed() {
        //block back
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(bcReciever);
        super.onDestroy();
    }
}
