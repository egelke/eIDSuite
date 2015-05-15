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
package net.egelke.android.eid.view;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Criteria;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import net.egelke.android.eid.service.EidService;
import net.egelke.android.eid.EidSuiteApp;
import net.egelke.android.eid.R;
import net.egelke.android.eid.viewmodel.Location;
import net.egelke.android.eid.viewmodel.PdfFile;
import net.egelke.android.eid.viewmodel.UpdateListener;
import net.egelke.android.eid.viewmodel.ViewObject;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class SignActivity extends Activity implements UpdateListener {

    private static final String TAG = "net.egelke.android.eid";
    private static final String ACTION_LOCATED = "net.egelke.android.eid.LOCATED";
    private static final int OPEN_REQUEST_CODE = 1;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    //instance
    private Messenger mEidService = null;
    private BroadcastReceiver bcReceiver;
    private LocationManager locationManager;
    private String locationProvider;

    //controls
    private TextView file;
    private CheckBox signInv;
    private ListView signNames;
    private Spinner reason;
    private EditText location;
    private Button sign;
    private Button view;

    //Session
    private Location l;
    private PdfFile p;
    private File tmp;
    private ArrayAdapter<PdfFile.Signature> s;
    private ScheduledFuture locationCancel;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mEidService = new Messenger(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            mEidService = null;
        }
    };

    private Messenger mEidServiceResponse = new Messenger(new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case EidService.END:
                    setProgressBarIndeterminateVisibility(false);
                    return true;
                case EidService.SIGN_RSP:
                    tmp = new File(msg.getData().getString("output"));

                    p.startUpdate();
                    p.setFile(null);

                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(tmp));
                    sendIntent.setType("application/pdf");
                    try {
                        startActivity(sendIntent);
                    } catch (ActivityNotFoundException ex) {
                        Toast.makeText(SignActivity.this, R.string.toastNoActivityFound, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        Tracker t = ((EidSuiteApp) this.getApplication()).getTracker();
        t.setScreenName("eID Sign");
        t.send(new HitBuilders.ScreenViewBuilder().build());

        bindService(new Intent(this, EidService.class), mConnection, Context.BIND_AUTO_CREATE);

        setContentView(R.layout.activity_sign);

        file = (TextView) findViewById(R.id.signFile);

        signInv  = (CheckBox) findViewById(R.id.signInv);
        signInv.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                signNames.setEnabled(!isChecked);
                if (isChecked) {
                    signNames.clearChoices();
                    signNames.requestLayout();
                } else {
                    signNames.setItemChecked(0, true);
                }
            }
        });

        signNames = (ListView) findViewById(R.id.signFields);

        reason = (Spinner) findViewById(R.id.reason);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.sign_reasons, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reason.setAdapter(adapter);

        Button select = (Button) findViewById(R.id.select);
        select.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("application/pdf");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(intent, OPEN_REQUEST_CODE);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(SignActivity.this, R.string.toastNoActivityFound, Toast.LENGTH_SHORT).show();
                }
            }
        });

        view = (Button) findViewById(R.id.view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(p.getFile());
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(SignActivity.this, R.string.toastNoPdfViewer, Toast.LENGTH_SHORT).show();
                }
            }
        });

        sign = (Button) findViewById(R.id.sign);
        sign.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    setProgressBarIndeterminateVisibility(true);
                    final Message msg = Message.obtain(null, EidService.SIGN, 0, 0);
                    msg.getData().putParcelable("input", p.getFile());
                    msg.getData().putString("name", p.getTitle());
                    msg.getData().putString("reason", (String) reason.getSelectedItem());
                    msg.getData().putString("location", location.getText().toString());
                    if (!signInv.isChecked()) msg.getData().putString("sign",
                                ((PdfFile.Signature) signNames.getItemAtPosition(signNames.getCheckedItemPosition())).getName());
                    msg.replyTo = mEidServiceResponse;
                    mEidService.send(msg);
                } catch (Exception e) {
                    Log.e(TAG, "Failed calling eID Service", e);
                }
            }
        });

        location = (EditText) findViewById(R.id.location);

        p = ((EidSuiteApp) getApplication()).getViewObject(PdfFile.class);
        s = new ArrayAdapter<PdfFile.Signature>(this,
                android.R.layout.simple_list_item_single_choice, p.getSignatures());
        signNames.setAdapter(s);
        p.addListener(this);

        l = ((EidSuiteApp) getApplication()).getViewObject(Location.class);
        l.addListener(this);

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_LOW);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(false);
        criteria.setAltitudeRequired(false);
        criteria.setPowerRequirement(Criteria.NO_REQUIREMENT);
        locationProvider =  locationManager.getBestProvider(criteria, true);

        if (locationProvider != null) {
            bcReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ACTION_LOCATED.equals(intent.getAction())) {
                        setProgressBarIndeterminateVisibility(false);
                        if (locationCancel != null) {
                            locationCancel.cancel(false);
                            locationCancel = null;
                        }

                        l.startUpdate();
                        l.setLocation((android.location.Location) intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED));
                    }
                }
            };
            registerReceiver(bcReceiver, new IntentFilter(ACTION_LOCATED));

            l.startUpdate();
            l.setLocation(locationManager.getLastKnownLocation(locationProvider));
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sign, menu);
        menu.findItem(R.id.action_locate).setEnabled(locationProvider != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_downloads:
                Intent i = new Intent();
                i.setAction(DownloadManager.ACTION_VIEW_DOWNLOADS);
                startActivity(i);
                return true;
            case R.id.action_locate:
                l.startUpdate();
                setProgressBarIndeterminateVisibility(true);
                final PendingIntent pi = PendingIntent.getBroadcast(SignActivity.this, 0, new Intent(ACTION_LOCATED), 0);
                locationManager.requestSingleUpdate(locationProvider, pi);
                locationCancel = scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        locationManager.removeUpdates(pi);
                        locationCancel = null;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setProgressBarIndeterminateVisibility(false);
                                Toast.makeText(SignActivity.this, R.string.toastFailedLocate, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }, 20, TimeUnit.SECONDS);
                return true;
            case R.id.action_settings:
                Intent intent = new Intent();
                intent.setClass(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case OPEN_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    p.startUpdate();
                    p.setFile(data.getData());
                } else {
                    Toast.makeText(this, R.string.toastFileCanceled, Toast.LENGTH_LONG).show();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    public void onUpdate(ViewObject value) {
        if (view == null || sign == null || signInv == null || location == null)
            return;

        if (value == p) {
            if (value.isUpdating()) {
                view.setEnabled(true);
                sign.setEnabled(true);
            } else {
                view.setEnabled(p.hasFile());
                sign.setEnabled(p.hasFile());
                signInv.setChecked(p.hasFile() && !p.hasSignatures());
                signInv.setEnabled(p.hasSignatures());
                s.notifyDataSetChanged();
                if (p.hasSignatures()) signNames.setItemChecked(0, true);
            }
        } else if (value == l) {
            if (!value.isUpdating()) {
                if (locationCancel != null) {
                    locationCancel.cancel(false);
                    locationCancel = null;
                }
                location.setText(l.getCity());
                setProgressBarIndeterminateVisibility(false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        //tear down service
        if (mEidService != null) unbindService(mConnection);
        if (bcReceiver != null) unregisterReceiver(bcReceiver);
        super.onDestroy();
    }
}
