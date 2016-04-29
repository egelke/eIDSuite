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

import android.os.Messenger;
import android.util.Log;

import org.spongycastle.crypto.tls.TlsClientProtocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;


public class EidSSLSocket extends SSLSocket {


    private static final String TAG = "net.egelke.android.eid";
    public static final String[] CIPHER_SUITES = new String[] {
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
    };
    private static final String[] PROTOCOLS = new String[] {
            "SSLv3",
            "TLSv1",
            "TLSv1.1",
            "TLSv1.2"
    };
    private static final String[] ENABLED_PROTOCOLS = new String[] {
            "TLSv1",
            "TLSv1.1",
            "TLSv1.2"
    };

    //Init
    private String host;
    private int port;
    private Messenger eidService;
    private TlsClientProtocol tlsClientProtocol;
    private List<HandshakeCompletedListener> listeners = new LinkedList<>();

    //After handshake
    private EidSSLSession session;

    public EidSSLSocket(Messenger eidService, String host, int port, TlsClientProtocol tlsClientProtocol) {
        this.host = host;
        this.port = port;
        this.eidService = eidService;
        this.tlsClientProtocol = tlsClientProtocol;
    }

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
        return CIPHER_SUITES;
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return CIPHER_SUITES;
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {
        if (!Arrays.equals(suites, CIPHER_SUITES))
            throw new IllegalArgumentException();
    }

    @Override
    public String[] getSupportedProtocols() {
        return PROTOCOLS;
    }

    @Override
    public String[] getEnabledProtocols() {
        return ENABLED_PROTOCOLS;
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
        if (!Arrays.equals(protocols, ENABLED_PROTOCOLS))
            throw new IllegalArgumentException();
    }

    @Override
    public SSLSession getSession() {
        Log.d(TAG, "SSLSocket.getSession");
        return session;
    }

    @Override
    public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void startHandshake() throws IOException {
        Log.d(TAG, "SSLSocket.startHandshake");

        EidTlsClient client = new EidTlsClient(eidService, host, port);
        tlsClientProtocol.connect(client);

        this.session = new EidSSLSession(client);

        for (HandshakeCompletedListener listener : listeners) {
            listener.handshakeCompleted(new HandshakeCompletedEvent(this, this.session));
        }
    }

    @Override
    public void setUseClientMode(boolean use) {
        if (!use)
            throw new IllegalArgumentException();
    }

    @Override
    public boolean getUseClientMode() {
        return true;
    }


    @Override
    public void setNeedClientAuth(boolean need) {
        if (need) throw new IllegalArgumentException();

    }

    @Override
    public boolean getNeedClientAuth() {
        return false;
    }


    @Override
    public void setWantClientAuth(boolean want) {
        if (!want) throw new IllegalArgumentException();
    }

    @Override
    public boolean getWantClientAuth() {
        return true;
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {
        if (!flag)
            throw new IllegalArgumentException();
    }

    @Override
    public boolean getEnableSessionCreation() {
        return true;
    }
}
