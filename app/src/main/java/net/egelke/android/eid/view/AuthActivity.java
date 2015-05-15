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

import android.annotation.TargetApi;
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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
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

import net.egelke.android.eid.service.EidService;
import net.egelke.android.eid.EidSuiteApp;
import net.egelke.android.eid.R;
import net.egelke.android.eid.tls.EidSSLSocketFactory;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public class AuthActivity extends Activity implements GoDialog.Listener {

    private static final String TAG = "net.egelke.android.eid";
    private static final String HOME = "http://www.taxonweb.be/";
    private static final String SEARCH = "http://www.google.be";

    private static final Pattern CD_FILE_PATTERN = Pattern.compile(".*filename=\"?([^\";]*)\"?.*");

    private MyWebViewClient wvc;
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
        webview.getSettings().setAppCacheEnabled(true); //TODO:make configurable
        webview.getSettings().setBuiltInZoomControls(true);
        webview.setWebChromeClient(new MyWebChromeClient());
        webview.setDownloadListener(new MyDownloadListener());
        setContentView(webview);

        CookieManager.setAcceptFileSchemeCookies(true);
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webview, true);
        }

        getActionBar().setDisplayHomeAsUpEnabled(true);
        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        Intent intent = getIntent();
        if (intent.getAction() == Intent.ACTION_VIEW) {
            url = intent.getData().toString();
            webview.loadUrl(url);
        } else {
            if (savedInstanceState != null && savedInstanceState.containsKey("page")) {
                webview.loadUrl(savedInstanceState.getString("page"));
            } else {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                webview.loadUrl(sharedPref.getString("pref_auth_home", HOME));
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        //setup service
        bindService(new Intent(this, EidService.class), mConnection, Context.BIND_AUTO_CREATE);

        //new client
        wvc = new MyWebViewClient();
        wvc.start();
        webview.setWebViewClient(wvc);

        webview.freeMemory();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("page", webview.getUrl());

        super.onSaveInstanceState(outState);
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
            Bitmap large= Bitmap.createScaledBitmap(icon, base.getWidth()/4, base.getHeight()/4, false);
            canvas.drawBitmap(large, base.getWidth() - large.getWidth(), base.getHeight() - large.getHeight(), null);
            AuthActivity.this.getActionBar().setIcon(new BitmapDrawable(getResources(), base));
        }
    }

    private class MyWebViewClient extends WebViewClient {

        private final String[] iamUrls;
        private final String[] iamCookies;

        private HandlerThread cookieThread;
        private Messenger cookieMsngr;
        private EidSSLSocketFactory factory;

        public MyWebViewClient() {
            iamUrls = new String[]{
                    "https://mijndossier.rrn.fgov.be/", //TODO:Fix (does everything in mutual ssl)
                    "https://certif.iamfas.belgium.be/fas/",
                    "https://www.ehealth.fgov.be/authenticate/eid/SSL2ways" //TODO:Fix (drops the connection)
            };
            iamCookies = new String[iamUrls.length];
        }

        public void start() {
            cookieThread = new HandlerThread("AuthActivityMsgThread", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            cookieThread.start();

            cookieMsngr = new Messenger(new Handler(cookieThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    CookieManager.getInstance().removeExpiredCookie();

                    if (msg.arg1 >= 0) {
                        iamCookies[msg.arg1] = CookieManager.getInstance().getCookie(iamUrls[msg.arg1]);
                    } else {
                        for (int i = 0; i < iamCookies.length; i++) {
                            iamCookies[i] = CookieManager.getInstance().getCookie(iamUrls[i]);
                        }
                    }
                }
            });
        }

        public void stop() throws RemoteException {
            cookieThread.quit();
        }

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
            Log.v(TAG, String.format("should Intercept: %s", url));
            int iam = -1;
            for (int i = 0; i < iamUrls.length; i++) {
                if (url.startsWith(iamUrls[i])) {
                    iam = i;
                }
            }

            try {
                if (iam != -1) {
                    if (factory == null) {
                        int count = 0;
                        while (count++ < 10 && mEidService == null) {
                            SystemClock.sleep(100 * count);
                        }
                        factory = new EidSSLSocketFactory(mEidService);
                    }

                    URL path = new URL(url);
                    HttpsURLConnection con = (HttpsURLConnection) path.openConnection();
                    con.setInstanceFollowRedirects(false);
                    con.setRequestProperty("Cookie", iamCookies[iam]);
                    con.setSSLSocketFactory(factory);

                    con.connect();
                    if (con.getHeaderFields() != null && con.getHeaderFields().get("Set-Cookie") != null) {
                        List<String> setCookieValues = con.getHeaderFields().get("Set-Cookie");
                        for (String setCookieValue : setCookieValues) {
                            CookieManager.getInstance().setCookie(url, setCookieValue);
                        }
                        cookieMsngr.send(Message.obtain(null, 1, iam, 0));
                    }

                    //translate a redirect if needed
                    if (con.getResponseCode() == 301 || con.getResponseCode() == 302 ) {
                        String redirect = con.getHeaderField("Location");
                        String html = String.format("<html><body onload=\"timer=setTimeout(function(){ window.location='%s';}, 300)\">" +
                                "you will be redirected soon" +
                                "</body></html>", redirect);
                        return new WebResourceResponse("text/html", Charset.defaultCharset().name(), new ByteArrayInputStream(html.getBytes()));
                    } else {
                        String[] contentTypeParts = con.getContentType().split(";[ ]*");
                        String encoding = contentTypeParts.length > 1 && contentTypeParts[1].startsWith("charset=") ? contentTypeParts[1].replaceFirst("charset=", "") : Charset.defaultCharset().name();
                        return new WebResourceResponse(contentTypeParts[0], encoding, con.getInputStream());
                    }
                } else {
                    cookieMsngr.send(Message.obtain(null, 1, -1, 0));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed HTTP intercept", e);
            }
            return null;
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
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        switch (item.getItemId()) {
            case android.R.id.home:
                webview.freeMemory();
                webview.loadUrl(sharedPref.getString("pref_auth_home", HOME));
                return true;
            case R.id.action_search:
                webview.freeMemory();
                webview.loadUrl(sharedPref.getString("pref_auth_search", SEARCH));
                return true;
            case R.id.action_go:
                GoDialog go = new GoDialog();
                Bundle args = new Bundle();
                args.putString("url", webview.getUrl());
                go.setArguments(args);
                go.show(getFragmentManager(), "Go");
                return true;
            case R.id.action_refresh:
                webview.freeMemory();
                webview.reload();
                return true;
            case R.id.action_clean:
                CookieManager.getInstance().removeAllCookie();
                webview.clearCache(true);
                return true;
            case R.id.action_downloads:
                Intent i = new Intent();
                i.setAction(DownloadManager.ACTION_VIEW_DOWNLOADS);
                startActivity(i);
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webview != null && webview.canGoBack()) {
            webview.goBack();
            return true;
        }
        // If it wasn't the Back key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onGo(String url) {
        if (webview != null) {
            webview.freeMemory();
            webview.loadUrl(url);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        //tear down service
        if (mEidService != null) unbindService(mConnection);
        if (wvc != null) {
            try {
                wvc.stop();
            } catch (Exception e) {

            } finally {
                wvc = null;
            }
        }
        if (webview != null) webview.setWebViewClient(null);
    }

    @Override
    protected void onDestroy() {
        //CookieManager.getInstance().removeAllCookie();
        unregisterReceiver(receiver);
        super.onDestroy();
    }
}
