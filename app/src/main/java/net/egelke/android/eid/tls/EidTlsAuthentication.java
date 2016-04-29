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
package net.egelke.android.eid.tls;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import net.egelke.android.eid.belpic.FileId;
import net.egelke.android.eid.service.EidService;

import org.spongycastle.crypto.tls.Certificate;
import org.spongycastle.crypto.tls.CertificateRequest;
import org.spongycastle.crypto.tls.TlsAuthentication;
import org.spongycastle.crypto.tls.TlsCredentials;

import java.io.IOException;


public class EidTlsAuthentication implements TlsAuthentication {

    private static final String TAG = "net.egelke.android.eid";

    private EidTlsClient client;
    private org.spongycastle.asn1.x509.Certificate[] certs;

    public EidTlsAuthentication(EidTlsClient client) {
        this.client = client;
    }

    @Override
    public void notifyServerCertificate(Certificate serverCertificate) throws IOException {
        //Nothing to do
    }

    public org.spongycastle.asn1.x509.Certificate[] getCertificates() {
        return certs;
    }

    @Override
    public TlsCredentials getClientCredentials(final CertificateRequest certificateRequest) throws IOException {

        try {
            certs = new org.spongycastle.asn1.x509.Certificate[2];
            Message msg = Message.obtain(null, EidService.READ_DATA, 0, 0);
            msg.getData().putBoolean(FileId.AUTH_CERT.name(), true);
            msg.getData().putBoolean(FileId.INTCA_CERT.name(), true);

            HandlerThread sslThread = new HandlerThread("SslFactoryMsgThread", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            sslThread.start();

            msg.replyTo = new Messenger(new Handler(sslThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case EidService.DATA_RSP:
                            FileId file = FileId.fromId(msg.arg1);
                            org.spongycastle.asn1.x509.Certificate cert = org.spongycastle.asn1.x509.Certificate.getInstance(msg.getData().getByteArray(file.name()));
                            switch (file) {
                                case AUTH_CERT:
                                    //TODO:check if cert is requested? Maybe, lets the server decide for now
                                    certs[0] = cert;
                                    break;
                                case INTCA_CERT:
                                    certs[1] = cert;
                                    break;
                                default:
                                    return;
                            }
                            break;
                        case EidService.END:
                            Looper.myLooper().quit();
                            break;
                        default:
                            return;
                    }
                }
            });

            client.sendToEid(msg);
            sslThread.join(10 * 60 * 1000);
            if (certs[0] == null || certs[1] == null) {
                Log.e(TAG, "Failed to obtain the TLS client certificates in a timely manner");
                return null;
            }
            return new EidTlsSignerCredentials(client, new Certificate(certs));
        } catch (RuntimeException re) {
            Log.e(TAG, "Failed to obtain the TLS client certificates", re);
            throw re;
        } catch (Exception e) {
            Log.e(TAG, "Failed to obtain the TLS client certificates", e);
            throw new IOException(e);
        }


    }
}
