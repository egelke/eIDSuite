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
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import net.egelke.android.eid.EidService;
import net.egelke.android.eid.belpic.FileId;

import org.spongycastle.crypto.tls.AlertDescription;
import org.spongycastle.crypto.tls.Certificate;
import org.spongycastle.crypto.tls.CertificateRequest;
import org.spongycastle.crypto.tls.DefaultTlsClient;
import org.spongycastle.crypto.tls.HashAlgorithm;
import org.spongycastle.crypto.tls.SignatureAlgorithm;
import org.spongycastle.crypto.tls.SignatureAndHashAlgorithm;
import org.spongycastle.crypto.tls.TlsAuthentication;
import org.spongycastle.crypto.tls.TlsClientProtocol;
import org.spongycastle.crypto.tls.TlsCredentials;
import org.spongycastle.crypto.tls.TlsFatalAlert;
import org.spongycastle.crypto.tls.TlsSignerCredentials;
import org.spongycastle.crypto.tls.TlsUtils;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;


public class EidSSLSocketFactory extends SSLSocketFactory {

    private static final String TAG = "net.egelke.android.eid";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
            Security.addProvider(new BouncyCastleProvider());
    }

    //fixed fields
    private SecureRandom sr = new SecureRandom();

    //member fields
    Messenger eidService;


    public EidSSLSocketFactory(Messenger eidService) {
        this.eidService = eidService;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        Log.w(TAG, "Request unsupported method");
        return new String[0];
    }

    @Override
    public String[] getSupportedCipherSuites() {
        Log.w(TAG, "Request unsupported method");
        return new String[0];
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        if (s == null) {
            s = new Socket();
        }
        if (!s.isConnected()) {
            s.connect(new InetSocketAddress(host, port));
        }

        final TlsClientProtocol tlsClientProtocol = new TlsClientProtocol(s.getInputStream(), s.getOutputStream(), sr);

        //DefaultTlsSignerCredentials
        return new SSLSocket() {

            private java.security.cert.Certificate[] peertCerts;

            @Override
            public InputStream getInputStream() throws IOException {
                return tlsClientProtocol.getInputStream();
            }

            @Override
            public OutputStream getOutputStream() throws IOException {
                return tlsClientProtocol.getOutputStream();
            }

            @Override
            public synchronized void close() throws IOException {
                tlsClientProtocol.close();
            }

            @Override
            public String[] getSupportedCipherSuites() {
                Log.d(TAG, "SSLSocket.getSupportedCipherSuites");
                return new String[0];
            }

            @Override
            public String[] getEnabledCipherSuites() {
                Log.w(TAG, "SSLSocket.getEnabledCipherSuites");
                throw new UnsupportedOperationException();
            }

            @Override
            public void setEnabledCipherSuites(String[] suites) {
                Log.w(TAG, "SSLSocket.setEnabledCipherSuites");
                throw new UnsupportedOperationException();
            }

            @Override
            public String[] getSupportedProtocols() {
                Log.w(TAG, "SSLSocket.getSupportedProtocols");
                throw new UnsupportedOperationException();
            }

            @Override
            public String[] getEnabledProtocols() {
                Log.w(TAG, "SSLSocket.getEnabledProtocols");
                throw new UnsupportedOperationException();
            }

            @Override
            public void setEnabledProtocols(String[] protocols) {
                Log.d(TAG, "SSLSocket.setEnabledProtocols");

            }

            @Override
            public SSLSession getSession() {
                Log.d(TAG, "SSLSocket.getSession");
                return new SSLSession() {

                    @Override
                    public int getApplicationBufferSize() {
                        Log.w(TAG, "SSLSession.getApplicationBufferSize");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String getCipherSuite() {
                        Log.w(TAG, "SSLSession.getCipherSuite");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public long getCreationTime() {
                        Log.w(TAG, "SSLSession.getCreationTime");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public byte[] getId() {
                        Log.w(TAG, "SSLSession.getId");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public long getLastAccessedTime() {
                        Log.w(TAG, "SSLSession.getLastAccessedTime");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public java.security.cert.Certificate[] getLocalCertificates() {
                        Log.w(TAG, "SSLSession.getLocalCertificates");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Principal getLocalPrincipal() {
                        Log.w(TAG, "SSLSession.getLocalPrincipal");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int getPacketBufferSize() {
                        Log.w(TAG, "SSLSession.getPacketBufferSize");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public javax.security.cert.X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
                        Log.w(TAG, "SSLSession.getPeerCertificateChain");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public java.security.cert.Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
                        Log.d(TAG, "SSLSession.getPeerCertificates: " + peertCerts.length);
                        return peertCerts;
                    }

                    @Override
                    public String getPeerHost() {
                        Log.w(TAG, "SSLSession.getPeerHost");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int getPeerPort() {
                        Log.w(TAG, "SSLSession.getPeerPort");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
                        Log.w(TAG, "SSLSession.getPeerPrincipal");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String getProtocol() {
                        Log.w(TAG, "SSLSession.getProtocol");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public SSLSessionContext getSessionContext() {
                        Log.w(TAG, "SSLSession.getSessionContext");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Object getValue(String name) {
                        Log.w(TAG, "SSLSession.getValue");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String[] getValueNames() {
                        Log.w(TAG, "SSLSession.getValueNames");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void invalidate() {
                        Log.w(TAG, "SSLSession.invalidate");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean isValid() {
                        Log.w(TAG, "SSLSession.isValid");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void putValue(String name, Object value) {
                        Log.w(TAG, "SSLSession.putValue");
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void removeValue(String name) {
                        Log.w(TAG, "SSLSession.removeValue");
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
                Log.w(TAG, "SSLSocket.addHandshakeCompletedListener");
                throw new UnsupportedOperationException();
            }

            @Override
            public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
                Log.w(TAG, "SSLSocket.removeHandshakeCompletedListener");
                throw new UnsupportedOperationException();
            }

            @Override
            public void startHandshake() throws IOException {
                Log.d(TAG, "SSLSocket.startHandshake");
                tlsClientProtocol.connect(new DefaultTlsClient() {
                    @Override
                    public TlsAuthentication getAuthentication() throws IOException {
                        return new TlsAuthentication() {

                            private byte[] signature;

                            @Override
                            public void notifyServerCertificate(Certificate serverCertificate) throws IOException {
                                try {
                                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                                    List<java.security.cert.Certificate> certs = new LinkedList<java.security.cert.Certificate>();
                                    for(org.spongycastle.asn1.x509.Certificate cert : serverCertificate.getCertificateList()) {
                                        certs.add(cf.generateCertificate(new ByteArrayInputStream(cert.getEncoded())));
                                    }
                                    peertCerts = certs.toArray(new java.security.cert.Certificate[0]);
                                } catch (CertificateException e) {
                                    Log.w(TAG, "Failed to cache server certs", e);
                                    throw new IOException(e);
                                }
                            }

                            @Override
                            public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) throws IOException {
                                return new TlsSignerCredentials() {
                                    @Override
                                    public byte[] generateCertificateSignature(byte[] hash) throws IOException {
                                        Log.v(TAG, "Request unsupported method: " + Hex.toHexString(hash));

                                        try {
                                            signature = null;
                                            final Message msg = Message.obtain(null, EidService.AUTH, 0, 0);
                                            msg.getData().putByteArray("Hash", hash);
                                            if (!TlsUtils.isTLSv12(context)) {
                                                msg.getData().putString("DigestAlg", "RAW");
                                            }
                                            Thread msgThread = new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Looper.prepare();
                                                    msg.replyTo = new Messenger(new Handler(new Handler.Callback() {
                                                        @Override
                                                        public boolean handleMessage(Message msg) {
                                                            switch (msg.what) {
                                                                case EidService.AUTH_RSP:
                                                                    switch (msg.arg1) {
                                                                        case 0:
                                                                            signature = msg.getData().getByteArray("Signature");
                                                                            break;
                                                                        default:
                                                                            break;
                                                                    }
                                                                    break;
                                                                default:
                                                                    break;
                                                            }
                                                            Looper.myLooper().quit();
                                                            return true;
                                                        }
                                                    }));
                                                    Looper.loop();
                                                }
                                            });
                                            msgThread.start();
                                            int count = 0;
                                            while (count < 15 && (msg.replyTo == null)) {
                                                Thread.sleep(++count * 10, 0);
                                            }

                                            eidService.send(msg);
                                            msgThread.join(1 * 60 * 1000);
                                            if (signature == null) throw new TlsFatalAlert(AlertDescription.internal_error);
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
                                        try {
                                            final org.spongycastle.asn1.x509.Certificate[] certs = new org.spongycastle.asn1.x509.Certificate[2];
                                            final Message msg = Message.obtain(null, EidService.READ_DATA, 0, 0);
                                            msg.getData().putBoolean(FileId.AUTH_CERT.name(), true);
                                            msg.getData().putBoolean(FileId.INTCA_CERT.name(), true);
                                            //msg.getData().putBoolean(FileId.ROOTCA_CERT.name(), true);
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
                                                                    org.spongycastle.asn1.x509.Certificate cert = org.spongycastle.asn1.x509.Certificate.getInstance(msg.getData().getByteArray(file.name()));
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
                                                            if (certs[0] != null && certs[1] != null) {
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
                                            while (count < 15 && (msg.replyTo == null)) {
                                                Thread.sleep(++count * 10, 0);
                                            }

                                            eidService.send(msg);
                                            msgThread.join(10 * 60 * 1000);
                                            if (certs[0] == null || certs[1] == null) {
                                                Log.e(TAG, "Failed to obtain the TLS client certificates in a timely manner");
                                                return null;
                                            }
                                            return new Certificate(certs);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Failed to obtain the TLS client certificates", e);
                                            return null;
                                        }
                                    }
                                };
                            }
                        };
                    }
                });
            }

            @Override
            public void setUseClientMode(boolean mode) {
                Log.w(TAG, "SSLSocket.setUseClientMode");
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean getUseClientMode() {
                Log.w(TAG, "SSLSocket.getUseClientMode");
                throw new UnsupportedOperationException();
            }

            @Override
            public void setNeedClientAuth(boolean need) {
                Log.w(TAG, "SSLSocket.setNeedClientAuth");
                throw new UnsupportedOperationException();
            }

            @Override
            public void setWantClientAuth(boolean want) {
                Log.w(TAG, "SSLSocket.setWantClientAuth");
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean getNeedClientAuth() {
                Log.w(TAG, "SSLSocket.getNeedClientAuth");
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean getWantClientAuth() {
                Log.w(TAG, "SSLSocket.getWantClientAuth");
                throw new UnsupportedOperationException();
            }

            @Override
            public void setEnableSessionCreation(boolean flag) {
                Log.w(TAG, "SSLSocket.setEnableSessionCreation");
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean getEnableSessionCreation() {
                Log.w(TAG, "SSLSocket.getEnableSessionCreation");
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        Log.w(TAG, "Request unsupported method");
        return null;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        Log.w(TAG, "Request unsupported method");
        return null;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        Log.w(TAG, "Request unsupported method");
        return null;
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        Log.w(TAG, "Request unsupported method");
        return null;
    }
}
