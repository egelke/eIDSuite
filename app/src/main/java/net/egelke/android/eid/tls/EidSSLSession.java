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

import android.util.Log;

import org.spongycastle.crypto.tls.CipherSuite;
import org.spongycastle.crypto.tls.ProtocolVersion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.security.auth.x500.X500Principal;


public class EidSSLSession implements SSLSession {

    private static final String TAG = "net.egelke.android.eid";

    private EidTlsClient client;
    private long created;

    public EidSSLSession(EidTlsClient client) {
        created = System.currentTimeMillis();
        this.client = client;
    }

    @Override
    public int getApplicationBufferSize() {
        Log.d(TAG, "SSLSession.getApplicationBufferSize");

        return 10240;
    }

    @Override
    public String getCipherSuite() {
        Log.d(TAG, "SSLSession.getCipherSuite");

        switch (client.getSelectedCipherSuite()) {
            case CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256:
                return "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
            case CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256:
                return "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256";
            case CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA:
                return "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA";
            case CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256:
                return "TLS_RSA_WITH_AES_128_GCM_SHA256";
            case CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256:
                return "TLS_RSA_WITH_AES_128_CBC_SHA256";
            case CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA:
                return "TLS_RSA_WITH_AES_128_CBC_SHA";
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public long getCreationTime() {
        Log.d(TAG, "SSLSession.getCreationTime");

        return created;
    }

    @Override
    public byte[] getId() {
        Log.w(TAG, "SSLSession.getId");

        return client.getSession().getSessionID();

    }

    @Override
    public long getLastAccessedTime() {
        Log.d(TAG, "SSLSession.getLastAccessedTime");

        return System.currentTimeMillis(); //TODO:improve
    }

    @Override
    public java.security.cert.Certificate[] getLocalCertificates() {
        Log.d(TAG, "SSLSession.getLocalCertificates");

        List<Certificate> localCerts = new LinkedList<>();
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for(org.spongycastle.asn1.x509.Certificate cert : client.getClientCertificates()) {
                localCerts.add(cf.generateCertificate(new ByteArrayInputStream(cert.getEncoded())));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to convert client certs", e);
            throw new RuntimeException("Failed to parse BC certs to JRE certs");
        }
        return localCerts.toArray(new Certificate[0]);
    }

    @Override
    public Principal getLocalPrincipal() {

        Log.d(TAG, "SSLSession.getLocalPrincipal");
        try {
            return new X500Principal(client.getClientCertificates()[0].getSubject().getEncoded());
        } catch (IOException e) {
            Log.w(TAG, "Failed to convert client cert to Principal", e);
            throw new RuntimeException("Failed to parse BC certs to JRE certs");
        }
    }

    @Override
    public int getPacketBufferSize() {
        Log.d(TAG, "SSLSession.getPacketBufferSize");

        return 10240;
    }

    @Override
    public javax.security.cert.X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        Log.d(TAG, "SSLSession.getPeerCertificateChain");
        throw new UnsupportedOperationException();
    }

    @Override
    public java.security.cert.Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        Log.d(TAG, "SSLSession.getPeerCertificates");

        List<Certificate> peerCerts = new LinkedList<>();
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for(org.spongycastle.asn1.x509.Certificate cert : client.getSession().exportSessionParameters().getPeerCertificate().getCertificateList()) {
                peerCerts.add(cf.generateCertificate(new ByteArrayInputStream(cert.getEncoded())));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to cache server certs", e);
            throw new SSLPeerUnverifiedException("Failed to parce BC certs to JRE certs");
        }
        return peerCerts.toArray(new Certificate[0]);
    }

    @Override
    public String getPeerHost() {
        Log.w(TAG, "SSLSession.getPeerHost");

        return client.getHost();
    }

    @Override
    public int getPeerPort() {
        Log.w(TAG, "SSLSession.getPeerPort");

        return client.getPort();
    }

    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        Log.w(TAG, "SSLSession.getPeerPrincipal");

        try {
            org.spongycastle.asn1.x509.Certificate cert =
                    client.getSession().exportSessionParameters().getPeerCertificate().getCertificateList()[0];
            return new X500Principal(cert.getSubject().getEncoded());
        } catch (IOException e) {
            Log.w(TAG, "Failed to convert client cert to Principal", e);
            throw new RuntimeException("Failed to parse BC certs to JRE certs");
        }
    }

    @Override
    public String getProtocol() {
        Log.w(TAG, "SSLSession.getProtocol");

        switch (client.getProtocol().getFullVersion()) {
            case 0x0301:
                return "TLSv1";
            case 0x0302:
                return "TLSv1.1";
            case 0x0303:
                return "TLSv1.2";
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public SSLSessionContext getSessionContext() {
        Log.w(TAG, "SSLSession.getSessionContext");
        throw new UnsupportedOperationException();
    }

    private Map<String, Object> values = new TreeMap<>();

    @Override
    public Object getValue(String name) {
        Log.d(TAG, "SSLSession.getValue");

        return values.get(name);
    }

    @Override
    public String[] getValueNames() {
        Log.d(TAG, "SSLSession.getValueNames");

        return values.keySet().toArray(new String[0]);
    }

    @Override
    public void invalidate() {
        Log.w(TAG, "SSLSession.invalidate");
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValid() {
        Log.w(TAG, "SSLSession.isValid");
        return true;
    }

    @Override
    public void putValue(String name, Object value) {
        Log.d(TAG, "SSLSession.putValue");

        values.put(name, value);
    }

    @Override
    public void removeValue(String name) {
        Log.d(TAG, "SSLSession.removeValue");

        values.remove(name);
    }
}
