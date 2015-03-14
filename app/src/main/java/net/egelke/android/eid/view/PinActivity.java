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

import android.app.PendingIntent;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import net.egelke.android.eid.R;


public class PinActivity extends ActionBarActivity {

    private static final String TAG = "net.egelke.android.eid";

    public static final String EXTRA_RETRIES = "RETRIES";
    public static final String EXTRA_BROADCAST = "BROADCAST";
    public static final String EXTRA_PIN = "PIN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        final int retries = getIntent().getIntExtra(EXTRA_RETRIES, -1);
        final PendingIntent pi = getIntent().getParcelableExtra(EXTRA_BROADCAST);

        final TextView retriesLeft = (TextView) findViewById(R.id.retries);
        if (retries >= 0) {
            retriesLeft.setText("Remaining retries left: " + retries);
        } else {
            retriesLeft.setText("");
        }

        final EditText pin = (EditText) findViewById(R.id.pin);

        final Button cancel = (Button) findViewById(R.id.button_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                try {
                    pi.send(PinActivity.this, 1, intent);
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Failed to broadcast PIN cancel click", e);
                }
                finish();
            }
        });

        final Button ok = (Button) findViewById(R.id.button_ok);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.putExtra(EXTRA_PIN, pin.getText().toString());
                try {
                    pi.send(PinActivity.this, 0, intent);
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Failed to broadcast PIN cancel click", e);
                }
                finish();
            }
        });
    }
}
