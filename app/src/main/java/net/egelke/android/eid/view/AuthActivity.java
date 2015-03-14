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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.os.Parcel;
import android.os.SystemClock;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import net.egelke.android.eid.EidService;
import net.egelke.android.eid.R;
import net.egelke.android.eid.jca.BeIDKeyStoreStream;
import net.egelke.android.eid.jca.BeIDManagerFactoryParameters;
import net.egelke.android.eid.jca.BeIDProvider;
import net.egelke.android.eid.tls.EidSSLSocketFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.CharBuffer;
import java.security.KeyStore;
import java.security.Security;

import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class AuthActivity extends ActionBarActivity {

    private static final String TAG = "net.egelke.android.eid";

    static {
        //Security.addProvider(new BeIDProvider());
    }

    private Messenger mEidService = null;

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
        setContentView(R.layout.activity_auth);
    }

    @Override
    protected void onStart() {
        super.onStart();

        //setup service
        bindService(new Intent(this, EidService.class), mConnection, Context.BIND_AUTO_CREATE);

        Intent intent = getIntent();
        if (intent.getAction() == Intent.ACTION_VIEW) {
            final Uri url = intent.getData();
            ((TextView) findViewById(R.id.text)).setText(url.toString());

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int count = 0;
                        while (count < 10 && (mEidService == null)) {
                            SystemClock.sleep(++count * 10);
                        }
                        if (mEidService == null) throw new IllegalStateException("No eID service initialized in time");

/*
                        KeyStore serverRootCert = KeyStore.getInstance("AndroidCAStore");
                        serverRootCert.load(null, null);
                        KeyStore clientCert = KeyStore.getInstance("BeID");
                        clientCert.load(new BeIDKeyStoreStream(mEidService), null);

                        HttpClient httpClient;
                        HttpParams httpParams = new BasicHttpParams();
                        SSLSocketFactory sslSocketFactory = new SSLSocketFactory(clientCert, null, serverRootCert);
                        SchemeRegistry registry = new SchemeRegistry();
                        registry.register(new Scheme("https", sslSocketFactory, 443));
                        httpClient = new DefaultHttpClient(new ThreadSafeClientConnManager(httpParams, registry), httpParams);

                        HttpGet request = new HttpGet(url.toString());
                        final HttpResponse response = httpClient.execute(request);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ((TextView) findViewById(R.id.text)).setText(response.getStatusLine().toString());
                            }
                        });
*/

/*
                        SSLContext sslContext = SSLContext.getInstance("TLS");
                        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("BeID");

                        keyManagerFactory.init(new BeIDManagerFactoryParameters(mEidService));
                        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
                        javax.net.ssl.SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                        URL path = new URL(url.toString());
                        HttpsURLConnection con = (HttpsURLConnection) path.openConnection();
                        try {
                            final CharBuffer buffer = CharBuffer.allocate(1000);
                            con.setSSLSocketFactory(sslSocketFactory);
                            Reader reader = new InputStreamReader(con.getInputStream());
                            reader.read(buffer);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ((TextView) findViewById(R.id.text)).setText(buffer.toString());
                                }
                            });
                        } finally {
                            con.disconnect();
                        }
*/

                        URL path = new URL(url.toString());
                        HttpsURLConnection con = (HttpsURLConnection) path.openConnection();
                        try {
                            final CharBuffer buffer = CharBuffer.allocate(1000);
                            con.setSSLSocketFactory(new EidSSLSocketFactory(mEidService));
                            Reader reader = new InputStreamReader(con.getInputStream());
                            reader.read(buffer);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ((TextView) findViewById(R.id.text)).setText(buffer.toString());
                                }
                            });
                        } finally {
                            con.disconnect();
                        }
                    } catch (final Exception e) {
                        Log.e(TAG, "Failed to authenticate with server", e);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ((TextView) findViewById(R.id.text)).setText(e.toString());
                            }
                        });
                    }
                }
            }).start();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_auth, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
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
