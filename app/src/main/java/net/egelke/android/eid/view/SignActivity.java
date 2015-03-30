/*
    This file is part of eID Suite.
    Copyright (C) 2014 Egelke BVBA

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
import android.database.Cursor;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
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
import android.provider.OpenableColumns;
import android.text.TextUtils;
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
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfReader;

import net.egelke.android.eid.EidService;
import net.egelke.android.eid.EidSuiteApp;
import net.egelke.android.eid.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class SignActivity extends Activity {

    private static final String TAG = "net.egelke.android.eid";
    private static final String ACTION_LOCATED = "net.egelke.android.eid.LOCATED";
    private static final int INPUT_REQUEST_CODE = 1;
    private static final int WRITE_REQUEST_CODE = 2;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    //TODO:change into viewmodel
    private static class SignatureField {
        String name;
        List<String> pages;
        String label;

        SignatureField(String name, List<String> pages, String label) {
            this.name = name;
            this.pages = pages;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private class CopyFile extends AsyncTask<Uri, Void, Void> {

        @Override
        protected Void doInBackground(Uri... params) {
            try {
                InputStream is = new FileInputStream(tmp);
                OutputStream os = getContentResolver().openOutputStream(params[0], "w");

                try {
                    int count;
                    byte[] buffer = new byte[1024];
                    if (this.isCancelled()) return null;
                    while ((count = is.read(buffer)) >= 0) {
                        os.write(buffer, 0, count);
                        if (this.isCancelled()) return null;
                    }
                } finally {
                    is.close();
                    os.close();
                    tmp.delete();
                    tmp = null;
                }
            } catch (IOException ioe) {
                Log.e(TAG, "Failed to copy file", ioe);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Toast.makeText(SignActivity.this, SignActivity.this.getString(R.string.toastSignReady), Toast.LENGTH_SHORT).show();
            setProgressBarIndeterminateVisibility(false);
        }
    }

    private class Locate extends AsyncTask<Location, Void, String> {

        private boolean endProgress;

        public Locate(boolean endProgress) {
            this.endProgress = endProgress;
        }

        @Override
        protected String doInBackground(Location... params) {
            try {
                Geocoder gcd = new Geocoder(SignActivity.this);
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
            location.setText(s != null ? s : "");
            if (endProgress) setProgressBarIndeterminateVisibility(false);
        }
    }

    private class Parse extends AsyncTask<Void, Void, List<SignatureField>> {

        @Override
        protected List<SignatureField> doInBackground(Void... params) {
            try {
                PdfReader reader = new PdfReader(getContentResolver().openInputStream(src));
                try {
                    AcroFields acroFields = reader.getAcroFields();
                    List<String> names = acroFields.getBlankSignatureNames();

                    List<SignatureField> fields = new LinkedList<SignatureField>();
                    for(String name : names) {
                        List<String> pages = new LinkedList<String>();
                        for(AcroFields.FieldPosition fp : acroFields.getFieldPositions(name)) {
                            pages.add(Integer.toString(fp.page));
                        }
                        fields.add(new SignatureField(name, pages,
                                String.format(getString(R.string.signField), name, TextUtils.join(", ", pages))));
                    }

                    return fields;
                } finally {
                    reader.close();
                }
            } catch (IOException ioe) {
                Log.e(TAG, "Failed to parse pdf");
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<SignatureField> values) {
            signInv.setChecked(values.isEmpty());
            signInv.setEnabled(!values.isEmpty());
            ArrayAdapter<SignatureField> adapter = new ArrayAdapter<SignatureField>(SignActivity.this,
                    android.R.layout.simple_list_item_single_choice, values);
            signNames.setAdapter(adapter);
            signNames.setEnabled(!values.isEmpty());
            if (!values.isEmpty())
                signNames.setItemChecked(0, true);
            setProgressBarIndeterminateVisibility(false);
        }
    }

    //instance
    private Messenger mEidService = null;
    private BroadcastReceiver bcReceiver;
    private LocationManager locationManager;
    private String locationProvider;
    private String locationProviderDetailed;

    //controls
    private TextView file;
    private CheckBox signInv;
    private ListView signNames;
    private Spinner reason;
    private EditText location;
    private Button sign;
    private Button view;

    //Session
    private Uri src;
    private File tmp;
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
                case EidService.SIGN_RSP:
                    tmp = new File(msg.getData().getString("output"));
                    String filename = new File(tmp.getPath()).getName();
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        File dstFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename);
                        (new CopyFile()).execute(Uri.fromFile(dstFile));
                    } else {
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/pdf");
                        intent.putExtra(Intent.EXTRA_TITLE, file.getText().toString());
                        try {
                            startActivityForResult(intent, WRITE_REQUEST_CODE);
                        } catch (ActivityNotFoundException ex) {
                                Toast.makeText(SignActivity.this, R.string.toastNoDocMngr, Toast.LENGTH_SHORT).show();
                            }
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

        setContentView(R.layout.activity_sign);

        bcReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_LOCATED.equals(intent.getAction())) {
                    (new Locate(true)).execute((Location) intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED));
                    if (locationCancel != null) {
                        locationCancel.cancel(false);
                        locationCancel = null;
                    }
                }
            }
        };
        registerReceiver(bcReceiver, new IntentFilter(ACTION_LOCATED));

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_LOW);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(false);
        criteria.setAltitudeRequired(false);
        criteria.setPowerRequirement(Criteria.NO_REQUIREMENT);
        locationProvider =  locationManager.getBestProvider(criteria, true);
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_MEDIUM);
        locationProviderDetailed = locationManager.getBestProvider(criteria, false);

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
                setProgressBarIndeterminateVisibility(true);

                Intent intent;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                } else {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                }
                intent.setType("application/pdf");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(intent, INPUT_REQUEST_CODE);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(SignActivity.this, R.string.toastNoDocMngr, Toast.LENGTH_SHORT).show();
                }
            }
        });

        view = (Button) findViewById(R.id.view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(src);
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
                    msg.getData().putParcelable("input", src);
                    msg.getData().putString("reason", (String)reason.getSelectedItem());
                    msg.getData().putString("location", location.getText().toString());
                    if (!signInv.isChecked())
                        msg.getData().putString("sign",
                                ((SignatureField) signNames.getItemAtPosition(signNames.getCheckedItemPosition())).name);
                    msg.replyTo = mEidServiceResponse;
                    mEidService.send(msg);
                } catch (Exception e) {
                    Log.e(TAG, "Failed calling eID Service", e);
                }
            }
        });
        bindService(new Intent(this, EidService.class), mConnection, Context.BIND_AUTO_CREATE);

        location = (EditText) findViewById(R.id.location);

        if (locationProvider != null) (new Locate(false)).execute(locationManager.getLastKnownLocation(locationProvider));
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case INPUT_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    src = data.getData();
                    if ("content".equals(src.getScheme())) {
                        Cursor cursor = getContentResolver().query(src, null, null, null, null);
                        try {
                            if (cursor != null && cursor.moveToFirst()) {
                                file.setText(cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)));
                            }
                        } finally {
                            cursor.close();
                        }
                    } else {
                        file.setText(src.getPath());
                    }
                    view.setEnabled(true);
                    sign.setEnabled(true);
                    (new Parse()).execute();
                }
                break;
            case WRITE_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    (new CopyFile()).execute(data.getData());
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        //tear down service
        if (mEidService != null) {
            unbindService(mConnection);
        }
        unregisterReceiver(bcReceiver);
        super.onDestroy();
    }
}
