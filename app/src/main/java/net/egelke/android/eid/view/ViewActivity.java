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

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import net.egelke.android.eid.EidService;
import net.egelke.android.eid.EidSuiteApp;
import net.egelke.android.eid.R;
import net.egelke.android.eid.belpic.FileId;
import net.egelke.android.eid.model.Identity;
import net.egelke.android.eid.viewmodel.Address;
import net.egelke.android.eid.viewmodel.Card;
import net.egelke.android.eid.viewmodel.Person;
import net.egelke.android.eid.viewmodel.ViewModel;

import java.io.ByteArrayInputStream;


public class ViewActivity extends ActionBarActivity implements StartDiagDialog.Listener {

    private static final String TAG = "net.egelke.android.eid";

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
                    return getString(R.string.fragment_person);
                case 1:
                    return getString(R.string.fragment_address);
                case 2:
                    return getString(R.string.fragment_photo);
                case 3:
                    return getString(R.string.fragment_card);
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
                    return getString(R.string.fragment_identity);
                case 1:
                    return getString(R.string.fragment_card);
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
            }
            return null;
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.fragment_idandcard);
            }
            return null;
        }
    }

    private boolean retry = false;

    private ViewPager mViewPager;

    private Messenger mEidService = null;

    private Messenger mEidServiceResponse = new Messenger(new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case EidService.DATA_RSP:
                    FileId file = FileId.fromId(msg.arg1);
                    switch (file) {
                        case IDENTITY:
                            Identity id = msg.getData().getParcelable(FileId.IDENTITY.name());
                            ViewModel.setData(Person.class.getName(), new Person(getApplicationContext(), id));
                            ViewModel.setData(Card.class.getName(), new Card(getApplicationContext(), id));
                            return true;
                        case ADDRESS:
                            net.egelke.android.eid.model.Address address = msg.getData().getParcelable(FileId.ADDRESS.name());
                            ViewModel.setData(Address.class.getName(), new Address(getApplicationContext(), address));
                            return true;
                        case PHOTO:
                            byte[] photo = msg.getData().getByteArray(FileId.PHOTO.name());
                            ViewModel.setData("Photo", Drawable.createFromStream(new ByteArrayInputStream(photo), "idPic"));
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


            switch(item.getItemId()) {
                case R.id.action_card:
                    ViewModel.start(Person.class.getName());
                    ViewModel.start(Card.class.getName());
                    ViewModel.start(Address.class.getName());
                    ViewModel.start("Photo");

                    msg = Message.obtain(null, EidService.READ_DATA, 0, 0);
                    msg.replyTo = mEidServiceResponse;
                    msg.getData().putBoolean(FileId.IDENTITY.name(), true);
                    msg.getData().putBoolean(FileId.ADDRESS.name(), true);
                    msg.getData().putBoolean(FileId.PHOTO.name(), true);
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
