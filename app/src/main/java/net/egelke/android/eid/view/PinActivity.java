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
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import net.egelke.android.eid.R;


public class PinActivity extends Activity {

    private static final String TAG = "net.egelke.android.eid";

    public static final String EXTRA_RETRIES = "RETRIES";
    public static final String EXTRA_BROADCAST = "BROADCAST";
    public static final String EXTRA_PIN = "PIN";

    private EditText pin;
    private PendingIntent pi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);
        setFinishOnTouchOutside(false);

        int retries = getIntent().getIntExtra(EXTRA_RETRIES, -1);
        pi = getIntent().getParcelableExtra(EXTRA_BROADCAST);

        TextView retriesLeft = (TextView) findViewById(R.id.retries);
        if (retries >= 0) {
            retriesLeft.setText(String.format(this.getString(R.string.pinRetries), retries));
        } else {
            retriesLeft.setText("");
        }

        pin = (EditText) findViewById(R.id.pin);
        pin.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                        || (actionId == EditorInfo.IME_ACTION_DONE)) {
                    success();
                    return true;
                }
                return false;
            }
        });

        Button cancel = (Button) findViewById(R.id.button_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                failure();
            }
        });

        Button ok = (Button) findViewById(R.id.button_ok);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                success();
            }
        });


    }

    @Override
    public void onBackPressed() {
        failure();
        super.onBackPressed();
    }

    private void success() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_PIN, pin.getText().toString());
        try {
            pi.send(PinActivity.this, 0, intent);
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Failed to broadcast PIN cancel click", e);
        }
        finish();
    }

    private void failure() {
        Intent intent = new Intent();
        try {
            pi.send(PinActivity.this, 1, intent);
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Failed to broadcast PIN cancel click", e);
        }
        finish();
    }
}
