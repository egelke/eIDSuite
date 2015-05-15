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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import net.egelke.android.eid.service.EidService;
import net.egelke.android.eid.EidSuiteApp;
import net.egelke.android.eid.R;
import net.egelke.android.eid.belpic.FileId;
import net.egelke.android.eid.file.Serializer;
import net.egelke.android.eid.model.Identity;
import net.egelke.android.eid.viewmodel.Address;
import net.egelke.android.eid.viewmodel.Card;
import net.egelke.android.eid.viewmodel.Certificate;
import net.egelke.android.eid.viewmodel.Certificates;
import net.egelke.android.eid.viewmodel.Person;
import net.egelke.android.eid.viewmodel.Photo;

import java.io.OutputStream;


public class ViewActivity extends FragmentActivity implements StartDiagDialog.Listener {

    private static final String TAG = "net.egelke.android.eid";

    private static final int SAVE_REQUEST_CODE = 1;

    private class SaveTask extends AsyncTask<Uri, Void, Exception> {

        @Override
        protected Exception doInBackground(Uri... uris) {
            try {
                EidSuiteApp app = (EidSuiteApp) getApplication();
                Person personView = app.getViewObject(Person.class);
                Address addressView = app.getViewObject(Address.class);
                Photo photoView = app.getViewObject(Photo.class);
                Certificates certsView = app.getViewObject(Certificates.class);

                OutputStream outputStream = getContentResolver().openOutputStream(uris[0]);
                try {
                    Serializer writer = new Serializer(outputStream);
                    writer.setIdentity(personView.getIdentity());
                    writer.setPhoto(photoView.getData());
                    writer.setAddress(addressView.getAddress());
                    for(Certificate certView : certsView.getCertificates()) {
                        switch (certView.getId()) {
                            case AUTH_CERT:
                                writer.setAuth(certView.getValue());
                                break;
                            case SIGN_CERT:
                                writer.setSign(certView.getValue());
                                break;
                            case INTCA_CERT:
                                writer.setIntca(certView.getValue());
                                break;
                            case ROOTCA_CERT:
                                writer.setRoot(certView.getValue());
                                break;
                            case RRN_CERT:
                                writer.setRrn(certView.getValue());
                                break;
                        }
                    }
                    writer.write();
                    return null;
                } finally {
                    outputStream.close();
                }
            } catch (Exception e) {
                Log.e("net.egelke.android.eid", "Writing the eid files failed", e);
                return e;
            }
        }

        @Override
        protected void onPostExecute(Exception e) {
            if (e != null) {
                Toast.makeText(getApplicationContext(), "Failed to save the eID file", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getApplicationContext(), "Saved the eID file", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public class SmallPagerAdapter extends FragmentPagerAdapter {

        public SmallPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new PersonFragment();
                case 1:
                    return new AddressFragment();
                case 2:
                    return new PhotoFragment();
                case 3:
                    return new CardFragment();
                case 4:
                    return new CertsFragment();
            }
            return null;
        }

        @Override
        public int getCount() {
            return 5;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.fragment_person);
                case 1:
                    return getString(R.string.fragment_address);
                case 2:
                    return getString(R.string.fragment_photo);
                case 3:
                    return getString(R.string.fragment_card);
                case 4:
                    return getString(R.string.fragment_certs);
            }
            return null;
        }
    }

    public class LargePagerAdapter extends FragmentPagerAdapter {

        public LargePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new IdentityFragment();
                case 1:
                    return new CardFragment();
                case 2:
                    return new CertsFragment();
            }
            return null;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.fragment_identity);
                case 1:
                    return getString(R.string.fragment_card);
                case 2:
                    return getString(R.string.fragment_certs);
            }
            return null;
        }
    }

    public class XLargePagerAdapter extends FragmentPagerAdapter {

        public XLargePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new IdAndCardFragment();
                case 1:
                    return new CertsFragment();
            }
            return null;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.fragment_idandcard);
                case 1:
                    return getString(R.string.fragment_certs);
            }
            return null;
        }
    }

    private boolean retry = false;

    private ViewPager mViewPager;

    private MenuItem mSave;

    private Messenger mEidService = null;

    private Messenger mEidServiceResponse = new Messenger(new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            EidSuiteApp app = (EidSuiteApp) getApplication();
            switch (msg.what) {
                case EidService.END:
                    app.getViewObject(Person.class).endUpdate();
                    app.getViewObject(Card.class).endUpdate();
                    app.getViewObject(Address.class).endUpdate();
                    app.getViewObject(Photo.class).endUpdate();
                    app.getViewObject(Certificates.class).endUpdate();
                    if (mSave != null) mSave.setEnabled(app.getViewObject(Certificates.class).getCertificates().size() >= 5);
                    return true;
                case EidService.DATA_RSP:
                    FileId file = FileId.fromId(msg.arg1);
                    switch (file) {
                        case IDENTITY:
                            Person personView = app.getViewObject(Person.class);
                            Card cardView = app.getViewObject(Card.class);
                            Identity id = msg.getData().getParcelable(FileId.IDENTITY.name());
                            personView.setIdentity(id);
                            cardView.setIdentity(id);
                            personView.endUpdate();
                            cardView.endUpdate();
                            return true;
                        case ADDRESS:
                            Address addressView = app.getViewObject(Address.class);
                            net.egelke.android.eid.model.Address address = msg.getData().getParcelable(FileId.ADDRESS.name());
                            addressView.setAddress(address);
                            addressView.endUpdate();
                            return true;
                        case PHOTO:
                            Photo photoView = app.getViewObject(Photo.class);
                            byte[] photo = msg.getData().getByteArray(FileId.PHOTO.name());
                            photoView.setData(photo);
                            photoView.endUpdate();
                            return true;
                        case AUTH_CERT:
                        case SIGN_CERT:
                        case INTCA_CERT:
                        case ROOTCA_CERT:
                        case RRN_CERT:
                            Certificates certsView = app.getViewObject(Certificates.class);
                            for(String name : msg.getData().keySet()) {
                                byte[] cert = msg.getData().getByteArray(name);
                                certsView.getCertificates().add(new Certificate(file, cert, getApplicationContext()));
                            }
                            certsView.onUpdate();
                            return true;
                        default:
                            return false;
                    }
                case EidService.DIAG_RSP:
                    if (msg.arg1 > 0) {
                        Toast.makeText(ViewActivity.this, R.string.toastDiagNoDevices, Toast.LENGTH_LONG).show();
                    } else {
                        if (msg.arg2 == 0) {
                            Toast.makeText(ViewActivity.this, R.string.toastDiagSuccess, Toast.LENGTH_LONG).show();
                        } else if (msg.arg2 == 2 && !retry) {
                            retry = true;
                            Toast.makeText(ViewActivity.this, R.string.toastDiagNoCard, Toast.LENGTH_LONG).show();
                        } else {
                            retry = false;
                            DialogFragment dialog = new EndDiagDialog();
                            dialog.setArguments(msg.getData());
                            dialog.show(getSupportFragmentManager(), "EndDiagDialogFragment");
                        }
                    }
                    return true;
                default:
                    return false;
            }
        }
    }));

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mEidService = new Messenger(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            mEidService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Tracker t = ((EidSuiteApp) this.getApplication()).getTracker();
        t.setScreenName("eID View");
        t.send(new HitBuilders.ScreenViewBuilder().build());

        setContentView(R.layout.activity_view);

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        if (getResources().getConfiguration().smallestScreenWidthDp < 600)
            mViewPager.setAdapter(new SmallPagerAdapter(getSupportFragmentManager()));
        else if (getResources().getConfiguration().smallestScreenWidthDp < 720)
            mViewPager.setAdapter(new LargePagerAdapter(getSupportFragmentManager()));
        else
            mViewPager.setAdapter(new XLargePagerAdapter(getSupportFragmentManager()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_view, menu);

        mSave = menu.findItem(R.id.action_save);

        EidSuiteApp app = (EidSuiteApp) getApplication();
        if (mSave != null) mSave.setEnabled(app.getViewObject(Certificates.class).getCertificates().size() >= 5);

        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        //setup service
        bindService(new Intent(this, EidService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
            if (mEidService == null) {
                //TODO:toast!
                return false;
            }

            Message msg;
            EidSuiteApp app = (EidSuiteApp) getApplication();
            Person personView = app.getViewObject(Person.class);
            Card cardView = app.getViewObject(Card.class);
            Address addressView = app.getViewObject(Address.class);
            Photo photoView = app.getViewObject(Photo.class);
            Certificates certsView = app.getViewObject(Certificates.class);

            switch(item.getItemId()) {
                case R.id.action_card:
                    personView.startUpdate();
                    cardView.startUpdate();
                    addressView.startUpdate();
                    photoView.startUpdate();
                    certsView.startUpdate();

                    personView.setIdentity(null);
                    cardView.setIdentity(null);
                    addressView.setAddress(null);
                    photoView.setData(null);
                    certsView.getCertificates().clear();
                    if (mSave != null) mSave.setEnabled(false);

                    msg = Message.obtain(null, EidService.READ_DATA, 0, 0);
                    msg.replyTo = mEidServiceResponse;
                    msg.getData().putBoolean(FileId.IDENTITY.name(), true);
                    msg.getData().putBoolean(FileId.ADDRESS.name(), true);
                    msg.getData().putBoolean(FileId.PHOTO.name(), true);
                    msg.getData().putBoolean(FileId.AUTH_CERT.name(), true);
                    msg.getData().putBoolean(FileId.SIGN_CERT.name(), true);
                    msg.getData().putBoolean(FileId.INTCA_CERT.name(), true);
                    msg.getData().putBoolean(FileId.ROOTCA_CERT.name(), true);
                    msg.getData().putBoolean(FileId.RRN_CERT.name(), true);
                    mEidService.send(msg);
                    return true;
                case R.id.action_pin:
                    msg = Message.obtain(null, EidService.VERIFY_PIN, 0, 0);
                    mEidService.send(msg);
                    return true;
                case R.id.action_diag:
                    DialogFragment dialog = new StartDiagDialog();
                    dialog.show(getSupportFragmentManager(), "StartDiagDialogFragment");
                    return true;
                case R.id.action_save:
                    Identity id = personView.getIdentity();
                    if (id == null) return true;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        intent.putExtra(Intent.EXTRA_TITLE,String.format("%s.eid", id.nationalNumber));
                        startActivityForResult(intent, SAVE_REQUEST_CODE);
                    } else {
                        Toast.makeText(this, R.string.toastRequiresKitKat, Toast.LENGTH_LONG).show();
                    }
                    return true;
                case R.id.action_settings:
                    Intent intent = new Intent();
                    intent.setClass(this, SettingsActivity.class);
                    startActivity(intent);
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            //TODO: toast
            Log.e(TAG, "Failed to send message to eID Service", e);
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SAVE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                (new SaveTask()).execute(data.getData());
            }
        }
    }

    @Override
    public void onStartDiag() {
        try {
            Message msg = Message.obtain(null, EidService.DIAG, 0, 0);
            msg.replyTo = mEidServiceResponse;
            mEidService.send(msg);
        } catch (Exception e) {
            //TODO:toast.
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        //tear down service
        if (mEidService != null) {
            unbindService(mConnection);
        }
    }


}
