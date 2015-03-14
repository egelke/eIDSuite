/*
    This file is part of eID Suite.
    Copyright (C) 2015 Egelke BVBA
    Copyright (C) 2008-2013 FedICT (Commons eID Project)

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

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509ExtendedKeyManager;

public class BeIDX509KeyManager extends X509ExtendedKeyManager {

    private static final String TAG = "net.egelke.android.eid";

    private Messenger mEidService;

    BeIDX509KeyManager(Messenger eidService) {
        mEidService = eidService;
    }

    @Override
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
        Log.v(TAG, "BeIDX509KeyManager.chooseClientAlias");
        for (String keyType : keyTypes) {
            Log.d(TAG, "key type: " + keyType);
            if ("RSA".equals(keyType)) {
                return "beid";
            }
        }
        return null;
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        Log.w(TAG, "BeIDX509KeyManager.chooseServerAlias");
        throw new UnsupportedOperationException();
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        Log.v(TAG, "BeIDX509KeyManager.getCertificateChain");

        try {
            final X509Certificate[] certs = new X509Certificate[3];
            final Message msg = Message.obtain(null, EidService.READ_DATA, 0, 0);
            msg.getData().putBoolean(FileId.AUTH_CERT.name(), true);
            msg.getData().putBoolean(FileId.INTCA_CERT.name(), true);
            msg.getData().putBoolean(FileId.ROOTCA_CERT.name(), true);
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
                                            certs[0] = cert;
                                            break;
                                        case INTCA_CERT:
                                            certs[1] = cert;
                                            break;
                                        case ROOTCA_CERT:
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
            Log.w(TAG, "Failed to obtain certs fro SSL", e);
            return null;
        }
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        Log.w(TAG, "BeIDX509KeyManager.getClientAliases");
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        Log.w(TAG, "BeIDX509KeyManager.getServerAliases");
        throw new UnsupportedOperationException();
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        Log.v(TAG, "BeIDX509KeyManager.getPrivateKey");
        return new BeIDPrivateKey();
    }
}
