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
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Messenger;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import net.egelke.android.eid.EidService;
import net.egelke.android.eid.EidSuiteApp;
import net.egelke.android.eid.R;
import net.egelke.android.eid.tls.EidSSLSocketFactory;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public class AuthActivity extends Activity {

    private static final String TAG = "net.egelke.android.eid";
    private static final String HOME= "http://www.taxonweb.be/";
    private static final Pattern CD_FILE_PATTERN = Pattern.compile(".*filename=\"?([^\";]*)\"?.*");

    private Messenger mEidService = null;
    private WebView webview;
    private String url;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mEidService = new Messenger(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            mEidService = null;
        }
    };

    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1));

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                Cursor c = dm.query(query);
                if (c.moveToFirst()) {
                    int sIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(sIndex)) {
                        Toast.makeText(AuthActivity.this, R.string.toastDownlComplete, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(AuthActivity.this, R.string.toastDownlFailed, Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        Tracker t = ((EidSuiteApp) this.getApplication()).getTracker();
        t.setScreenName("eID Auth");
        t.send(new HitBuilders.ScreenViewBuilder().build());

        webview = new WebView(this);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setAppCachePath(getCacheDir().getAbsolutePath());
        webview.getSettings().setAppCacheEnabled(true);
        webview.getSettings().setBuiltInZoomControls(true);
        webview.setWebChromeClient(new MyWebChromeClient());
        webview.setWebViewClient(new MyWebViewClient());
        webview.setDownloadListener(new MyDownloadListener());
        setContentView(webview);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        Intent intent = getIntent();
        if (intent.getAction() == Intent.ACTION_VIEW) {
            url = intent.getData().toString();
            webview.loadUrl(url);
        } else {
            webview.loadUrl(HOME);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        //setup service
        bindService(new Intent(this, EidService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    private class MyWebChromeClient extends WebChromeClient {



        public void onProgressChanged(WebView view, int progress) {
            AuthActivity.this.setProgress(progress * 1000);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            AuthActivity.this.setTitle(getString(R.string.title_activity_auth) + ": " + title);
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            Bitmap base = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_a);
            base = base.copy(base.getConfig(), true);

            Canvas canvas = new Canvas(base);
            Bitmap large= Bitmap.createScaledBitmap(icon, icon.getWidth()*2, icon.getHeight()*2, false);
            canvas.drawBitmap(large, base.getWidth() - large.getWidth(), base.getHeight() - large.getHeight(), null);
            AuthActivity.this.getActionBar().setIcon(new BitmapDrawable(getResources(), base));
        }
    }

    private class MyWebViewClient extends WebViewClient {

        String cookies;

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            setProgressBarVisibility(true);
            setProgressBarIndeterminateVisibility(true);
            getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 0);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            setProgressBarVisibility(false);
            setProgressBarIndeterminateVisibility(false);
            getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 10000);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(final WebView view, final String url) {
            if (!url.startsWith("https://certif.iamfas.belgium.be/fas/")) {
                Log.d(TAG, String.format("Getting %s in the default way", url));
                //get the cookie at the each run, lets hope it is on time.
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        cookies = CookieManager.getInstance().getCookie("https://certif.iamfas.belgium.be/fas/");
                    }
                }).start();
                return null;
            }
            try {
                EidSSLSocketFactory factory = new EidSSLSocketFactory(mEidService);

                URL path = new URL(url);
                HttpsURLConnection con = (HttpsURLConnection) path.openConnection();
                con.setInstanceFollowRedirects(false);
                con.setRequestProperty("Cookie", cookies);
                con.setRequestProperty("Referer", AuthActivity.this.url);
                con.setRequestProperty("Connection", "Keep-Alive");
                con.setSSLSocketFactory(factory);

                Log.d(TAG, String.format("Getting %s [Cookie=%s, Referer=%s]", url, cookies, AuthActivity.this.url));
                con.connect();
                if (con.getResponseCode() == 200) {
                    Log.d(TAG, String.format("Got 200, returning data (%d)", con.getContentLength()));
                    String[] contentTypeParts = con.getContentType().split(";[ ]*");
                    return new WebResourceResponse(contentTypeParts[0], contentTypeParts[1], con.getInputStream());
                }if (con.getResponseCode() == 302) {
                    Log.d(TAG, "Got 302, Setting cookies and following");
                    //Update the cookies before we do the redirect
                    String iamfasPR=null;
                    List<String> setCookieValues = con.getHeaderFields().get("Set-Cookie");
                    for(String setCookieValue : setCookieValues) {
                        CookieManager.getInstance().setCookie(url, setCookieValue);
                        if (setCookieValue.startsWith("iamfasPR=")) {
                            iamfasPR = setCookieValue.split(";[ ]*")[0];
                        }
                    }
                    String redirect = con.getHeaderField("Location");
                    while (con.getInputStream().read() >= 0) { }
                    con.disconnect();

                    path = new URL(redirect);
                    con = (HttpsURLConnection) path.openConnection();
                    con.setInstanceFollowRedirects(false);
                    con.setSSLSocketFactory(factory);
                    String cookie[] = cookies.split(";[ ]*");
                    List<String> newCookies = new LinkedList<String>();
                    for(String newCookie : cookie) {
                        if (newCookie.startsWith("FASNODE")
                                || newCookie.startsWith("STORKNODE")
                                || newCookie.startsWith("iamfaslbPR")
                                || newCookie.startsWith("IAM3-FAS-JSESSIONID-PR"))
                            newCookies.add(newCookie);
                    }
                    newCookies.add(iamfasPR);
                    String newCookie = TextUtils.join("; ", newCookies);
                    con.setRequestProperty("Cookie", newCookie);
                    con.setRequestProperty("Referer", AuthActivity.this.url);
                    con.setRequestProperty("Connection", "Keep-Alive");

                    Log.d(TAG, String.format("Getting %s  [Cookie=%s, Referer=%s]", path.toString(), newCookie, AuthActivity.this.url));
                    con.connect();

                    String[] contentTypeParts = con.getContentType().split(";[ ]*");
                    Log.d(TAG, String.format("Got %d %s (%d)", con.getResponseCode(), con.getResponseMessage(), con.getContentLength()));
                    return new WebResourceResponse(contentTypeParts[0], contentTypeParts[1], con.getInputStream());
                } else {
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed HTTP intercept", e);
                return null;
            }
        }

        @Override
        public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
            Log.w(TAG, String.format("Receive SSL Error: %s", error));
            AlertDialog.Builder builder = new AlertDialog.Builder(AuthActivity.this);
            builder.setMessage("There are problems with the security certificate for this site")
                    .setIcon(R.drawable.ic_action_warning)
                    .setTitle("Security Warning");
            builder.setNegativeButton(R.string.goback, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    handler.cancel();
                }
            });
            builder.setPositiveButton(R.string.cont, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    handler.proceed();
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private class MyDownloadListener implements DownloadListener {



        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
            Log.d(TAG, String.format("Download: %s [mimeType=%s, contentDisposition=%s]", url, mimetype, contentDisposition));

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse((url)));
            request.setMimeType(mimetype);

            request.addRequestHeader("User-Agent", userAgent);
            request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));

            if (contentDisposition != null) {
                Matcher m = CD_FILE_PATTERN.matcher(contentDisposition);
                if (m.matches())
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, m.group(1));
            }

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
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
        switch (item.getItemId()) {
            case android.R.id.home:
                webview.loadUrl(HOME);
                return true;
            case R.id.action_refresh:
                webview.reload();
                return true;
            case R.id.action_downloads:
                Intent i = new Intent();
                i.setAction(DownloadManager.ACTION_VIEW_DOWNLOADS);
                startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webview.canGoBack()) {
            webview.goBack();
            return true;
        }
        // If it wasn't the Back key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();

        //tear down service
        if (mEidService != null) {
            unbindService(mConnection);
        }
    }

    @Override
    protected void onDestroy() {
        CookieManager.getInstance().removeAllCookie();
        unregisterReceiver(receiver);
        super.onDestroy();
    }
}
