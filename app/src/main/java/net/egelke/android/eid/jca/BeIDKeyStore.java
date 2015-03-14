/*
    This file is part of eID Suite.
    Copyright (C) 2015 Egelke BVBA
    Copyright (C) 2008-2013 FedICT (Commons eID Project)
    Copyright (C) 2014 e-Contract.be BVBA (Commons eID Project)

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
package net.egelke.android.eid.jca;


import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.util.Log;

import net.egelke.android.eid.EidService;
import net.egelke.android.eid.belpic.FileId;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;

public class BeIDKeyStore extends KeyStoreSpi {

    private static final String TAG = "net.egelke.android.eid";

    private Messenger mEidService;
    private X509Certificate mAuthentication = null;
    private X509Certificate mCA = null;
    private X509Certificate mRoot = null;

    @Override
    public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        Log.v(TAG, "BeIDKeyStore.engineGetKey");

        if ("Authentication".equals(alias)) {
            return new BeIDPrivateKey();
        }
        return null;
    }

    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
        Log.v(TAG, "BeIDKeyStore.engineGetCertificateChain");

        if (!"Authentication".equals(alias)) return null;
        try {
            final X509Certificate[] certs = new X509Certificate[] {
                    mAuthentication,
                    mCA,
                    mRoot
            };
            final Message msg = Message.obtain(null, EidService.READ_DATA, 0, 0);
            if (certs[0] == null) msg.getData().putBoolean(FileId.AUTH_CERT.name(), true);
            if (certs[1] == null) msg.getData().putBoolean(FileId.INTCA_CERT.name(), true);
            if (certs[2] == null) msg.getData().putBoolean(FileId.ROOTCA_CERT.name(), true);
            Thread msgThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Looper.prepare();
                    msg.replyTo = new Messenger(new Handler(new Handler.Callback() {
                        @Override
                        public boolean handleMessage(Message msg) {
                            switch (msg.what) {
                                case EidService.DATA_RSP:
                                    FileId file = FileId.fromId(msg.arg1);
                                    X509Certificate cert = (X509Certificate) msg.getData().getSerializable(file.name());
                                    switch (file) {
                                        case AUTH_CERT:
                                            mAuthentication = cert;
                                            certs[0] = cert;
                                            break;
                                        case INTCA_CERT:
                                            mCA = cert;
                                            certs[1] = cert;
                                            break;
                                        case ROOTCA_CERT:
                                            mRoot = cert;
                                            certs[2] = cert;
                                            break;
                                        default:
                                            return false;
                                    }
                                    break;
                                default:
                                    return false;
                            }
                            if (certs[0] != null && certs[1] != null && certs[2] != null) {
                                Looper.myLooper().quit();
                            }
                            return true;
                        }
                    }));
                    Looper.loop();
                }
            });
            msgThread.start();
            int count = 0;
            while (count < 10 && (msg.replyTo == null)) {
                SystemClock.sleep(++count * 10);
            }

            mEidService.send(msg);
            msgThread.join(10*60*1000);
            if (certs[0] != null && certs[1] != null && certs[2] != null) {
                return certs;
            } else {
                return null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to obtain certs from SSL", e);
            return null;
        }
    }

    @Override
    public Certificate engineGetCertificate(String alias) {
        Log.v(TAG, "BeIDKeyStore.engineGetCertificate");

        try {
            final X509Certificate[] cert = new X509Certificate[1];
            final Message msg = Message.obtain(null, EidService.READ_DATA, 0, 0);
            if ("Authentication".equals(alias)) {
                msg.getData().putBoolean(FileId.AUTH_CERT.name(), true);
            } else if ("CA".equals(alias)) {
                msg.getData().putBoolean(FileId.INTCA_CERT.name(), true);
            } else if ("Root".equals(alias)) {
                msg.getData().putBoolean(FileId.ROOTCA_CERT.name(), true);
            }
            Thread msgThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Looper.prepare();
                    msg.replyTo = new Messenger(new Handler(new Handler.Callback() {
                        @Override
                        public boolean handleMessage(Message msg) {
                            switch (msg.what) {
                                case EidService.DATA_RSP:
                                    FileId file = FileId.fromId(msg.arg1);
                                    cert[0] = (X509Certificate) msg.getData().getSerializable(file.name());
                                    switch (file) {
                                        case AUTH_CERT:
                                            mAuthentication = cert[0];
                                            break;
                                        case INTCA_CERT:
                                            mCA = cert[0];
                                            break;
                                        case ROOTCA_CERT:
                                            mRoot = cert[0];
                                            break;
                                        default:
                                            break;
                                    }
                                    Looper.myLooper().quit();
                                    return true;
                                default:
                                    return false;
                            }
                        }
                    }));
                    Looper.loop();
                }
            });
            msgThread.start();
            int count = 0;
            while (count < 10 && (msg.replyTo == null)) {
                SystemClock.sleep(++count * 10);
            }

            mEidService.send(msg);
            msgThread.join(10 * 60 * 1000);
            return cert[0];
        } catch (Exception e) {
            Log.w(TAG, "Failed to obtain certs from SSL", e);
            return null;
        }
    }

    @Override
    public Date engineGetCreationDate(String alias) {
        Log.w(TAG, "BeIDKeyStore.engineGetCreationDate");
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        Log.w(TAG, "BeIDKeyStore.engineSetKeyEntry");
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
        Log.w(TAG, "BeIDKeyStore.engineSetKeyEntry");
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        Log.w(TAG, "BeIDKeyStore.engineSetCertificateEntry");
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineDeleteEntry(String alias) throws KeyStoreException {
        Log.w(TAG, "BeIDKeyStore.engineDeleteEntry");
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<String> engineAliases() {
        Log.v(TAG, "BeIDKeyStore.engineAliases");
        final Vector<String> aliases = new Vector<String>();
        aliases.add("Authentication");
        aliases.add("CA");
        aliases.add("Root");
        return aliases.elements();
    }

    @Override
    public boolean engineContainsAlias(String alias) {
        Log.v(TAG, "BeIDKeyStore.engineContainsAlias");
        return "Authentication".equals(alias)
                || "CA".equals(alias)
                || "Root".equals(alias);
    }

    @Override
    public int engineSize() {
        Log.w(TAG, "BeIDKeyStore.engineSize");
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean engineIsKeyEntry(String alias) {
        Log.v(TAG, "BeIDKeyStore.engineIsKeyEntry");
        return "Authentication".equals(alias);
    }

    @Override
    public boolean engineIsCertificateEntry(String alias) {
        Log.v(TAG, "BeIDKeyStore.engineIsCertificateEntry");
        return "CA".equals(alias) || "Root".equals(alias);
    }

    @Override
    public String engineGetCertificateAlias(Certificate cert) {
        Log.w(TAG, "BeIDKeyStore.engineGetCertificateAlias");
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        Log.w(TAG, "BeIDKeyStore.engineStore");
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        Log.v(TAG, "BeIDKeyStore.engineLoad");

        mEidService = ((BeIDKeyStoreStream) stream).mEidService;
        mAuthentication = null;
        mCA = null;
        mRoot = null;
    }
}
