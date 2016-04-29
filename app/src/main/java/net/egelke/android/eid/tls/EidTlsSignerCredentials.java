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

import net.egelke.android.eid.service.EidService;

import org.spongycastle.crypto.tls.AlertDescription;
import org.spongycastle.crypto.tls.Certificate;
import org.spongycastle.crypto.tls.HashAlgorithm;
import org.spongycastle.crypto.tls.SignatureAlgorithm;
import org.spongycastle.crypto.tls.SignatureAndHashAlgorithm;
import org.spongycastle.crypto.tls.TlsFatalAlert;
import org.spongycastle.crypto.tls.TlsSignerCredentials;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;

public class EidTlsSignerCredentials implements TlsSignerCredentials {

    private static final String TAG = "net.egelke.android.eid";


    private EidTlsClient client;
    private Certificate certificate;

    private byte[] signature;


    public EidTlsSignerCredentials(EidTlsClient client, Certificate certificate) {
        this.client = client;
        this.certificate = certificate;
    }

    @Override
    public byte[] generateCertificateSignature(byte[] hash) throws IOException {
        Log.v(TAG, "Request unsupported method: " + Hex.toHexString(hash));

        try {
            signature = null;
            final Message msg = Message.obtain(null, EidService.AUTH, 0, 0);
            msg.getData().putByteArray("Hash", hash);
            if (client.isTLSv12()) {
                msg.getData().putString("DigestAlg", "RAW");
            }
            final HandlerThread sslThread = new HandlerThread("SslFactoryMsgThread", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            sslThread.start();

            msg.replyTo = new Messenger(new Handler(sslThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case EidService.AUTH_RSP:
                            signature = msg.getData().getByteArray("Signature");
                            break;
                        case EidService.END:
                            Looper.myLooper().quit();
                            break;
                        default:
                            break;
                    }
                }
            });

            client.sendToEid(msg);
            sslThread.join(1 * 60 * 1000);
            if (signature == null)
                throw new TlsFatalAlert(AlertDescription.internal_error);
            return signature;
        } catch (TlsFatalAlert e) {
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Failed to obtain the TLS client certificates", e);
            throw new TlsFatalAlert(AlertDescription.internal_error);
        }
    }

    @Override
    public SignatureAndHashAlgorithm getSignatureAndHashAlgorithm() {
        return new SignatureAndHashAlgorithm(HashAlgorithm.sha1, SignatureAlgorithm.rsa);
    }

    @Override
    public Certificate getCertificate() {
        return certificate;
    }
}
