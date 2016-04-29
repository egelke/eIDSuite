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

import android.os.*;
import android.util.Log;

import net.egelke.android.eid.service.EidService;
import net.egelke.android.eid.belpic.FileId;

import org.spongycastle.crypto.tls.AlertDescription;
import org.spongycastle.crypto.tls.Certificate;
import org.spongycastle.crypto.tls.CertificateRequest;
import org.spongycastle.crypto.tls.DefaultTlsClient;
import org.spongycastle.crypto.tls.ExtensionType;
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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
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
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.HandshakeCompletedEvent;
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
        return EidSSLSocket.CIPHER_SUITES;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return EidSSLSocket.CIPHER_SUITES;
    }

    @Override
    public Socket createSocket(Socket s, final String host, int port, boolean autoClose) throws IOException {
        if (s == null) {
            s = new Socket();
        }
        if (!s.isConnected()) {
            s.connect(new InetSocketAddress(host, port));
        }

        TlsClientProtocol tlsClientProtocol = new TlsClientProtocol(s.getInputStream(), s.getOutputStream(), sr);
        return new EidSSLSocket(eidService, host, port, tlsClientProtocol);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return createSocket((Socket)null, host, port, false);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        Log.w(TAG, "Request unsupported method");
        throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return createSocket((Socket)null, host.getHostName(), port, false);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        Log.w(TAG, "Request unsupported method");
        throw new UnsupportedOperationException();
    }
}
