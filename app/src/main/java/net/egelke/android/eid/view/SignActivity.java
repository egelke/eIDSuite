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
import android.app.PendingIntent;
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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.OpenableColumns;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.method.CharacterPickerDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfAnnotation;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;

import net.egelke.android.eid.EidService;
import net.egelke.android.eid.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;


public class SignActivity extends ActionBarActivity {

    private static final String TAG = "net.egelke.android.eid";
    private static final String ACTION_LOCATED = "net.egelke.android.eid.LOCATED";
    private static final int INPUT_REQUEST_CODE = 1;
    private static final int WRITE_REQUEST_CODE = 2;

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
            Toast.makeText(SignActivity.this, "Signed document ready", Toast.LENGTH_SHORT).show();
        }
    }

    private class Locate extends AsyncTask<Location, Void, String> {

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
        }
    }

    private class Parse extends AsyncTask<Void, Void, List<String>> {

        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                PdfReader reader = new PdfReader(getContentResolver().openInputStream(src));
                try {
                    return reader.getAcroFields().getBlankSignatureNames();
                } finally {
                    reader.close();
                }
            } catch (IOException ioe) {
                Log.e(TAG, "Failed to parse pdf");
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<String> strings) {
            signInv.setChecked(strings.isEmpty());
            signInv.setEnabled(!strings.isEmpty());
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(SignActivity.this,
                    android.R.layout.simple_list_item_single_choice, strings);
            signNames.setAdapter(adapter);
            signNames.setEnabled(!strings.isEmpty());
            if (!strings.isEmpty())
                signNames.setItemChecked(0, true);
        }
    }

    //instance
    private Messenger mEidService = null;
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
                        intent.putExtra(Intent.EXTRA_TITLE, filename);
                        startActivityForResult(intent, WRITE_REQUEST_CODE);
                    }
                    return true;
                default:
                    return false;
            }
        }
    }));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign);

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_LOCATED.equals(intent.getAction())) {
                    (new Locate()).execute((Location)intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED));
                }
            }
        }, new IntentFilter(ACTION_LOCATED));

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
                Intent intent;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                } else {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                }
                intent.setType("application/pdf");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, INPUT_REQUEST_CODE);
            }
        });

        view = (Button) findViewById(R.id.view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(src);
                startActivity(intent);
            }
        });

        sign = (Button) findViewById(R.id.sign);
        sign.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    /*
                    tmp = new File(getCacheDir().getAbsolutePath() + File.separator + "tmp.pdf");
                    PdfReader reader = new PdfReader(getContentResolver().openInputStream(src));
                    PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(tmp));
                    PdfFormField field1 = PdfFormField.createSignature(stamper.getWriter());
                    field1.setFieldName("Aanbieder");
                    field1.setWidget(new Rectangle(1.5f*72, 2.0f*72, 3.5f*72, 2.7f*72), PdfAnnotation.HIGHLIGHT_OUTLINE);
                    field1.setFlags(PdfAnnotation.FLAGS_PRINT);
                    stamper.addAnnotation(field1, 1);

                    PdfFormField field2 = PdfFormField.createSignature(stamper.getWriter());
                    field2.setFieldName("Verantwoordelijke");
                    field2.setWidget(new Rectangle(5.0f*72, 2.0f*72, 7.0f*72, 2.7f*72), PdfAnnotation.HIGHLIGHT_OUTLINE);
                    field1.setFlags(PdfAnnotation.FLAGS_PRINT);
                    stamper.addAnnotation(field2, 1);

                    stamper.close();

                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/pdf");
                    intent.putExtra(Intent.EXTRA_TITLE, "file1.pdf");
                    startActivityForResult(intent, WRITE_REQUEST_CODE);
                    */

                    final Message msg = Message.obtain(null, EidService.SIGN, 0, 0);
                    msg.getData().putParcelable("input", src);
                    msg.getData().putString("reason", (String)reason.getSelectedItem());
                    msg.getData().putString("location", location.getText().toString());
                    if (!signInv.isChecked())
                        msg.getData().putString("sign", (String) signNames.getItemAtPosition(signNames.getCheckedItemPosition()));
                    msg.replyTo = mEidServiceResponse;
                    mEidService.send(msg);
                } catch (Exception e) {
                    Log.e(TAG, "Failed calling eID Service", e);
                }
            }
        });
        bindService(new Intent(this, EidService.class), mConnection, Context.BIND_AUTO_CREATE);

        location = (EditText) findViewById(R.id.location);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sign, menu);
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        (new Locate()).execute(locationManager.getLastKnownLocation(locationProvider));
        locationManager.requestSingleUpdate(locationProvider, PendingIntent.getBroadcast(SignActivity.this, 0, new Intent(ACTION_LOCATED), 0));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
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
        super.onDestroy();
    }
}
