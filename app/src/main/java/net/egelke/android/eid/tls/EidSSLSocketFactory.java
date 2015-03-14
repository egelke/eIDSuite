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
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import net.egelke.android.eid.EidService;
import net.egelke.android.eid.belpic.FileId;

import org.apache.http.MethodNotSupportedException;
import org.spongycastle.crypto.tls.AlertDescription;
import org.spongycastle.crypto.tls.Certificate;
import org.spongycastle.crypto.tls.CertificateRequest;
import org.spongycastle.crypto.tls.DefaultTlsClient;
import org.spongycastle.crypto.tls.DefaultTlsSignerCredentials;
import org.spongycastle.crypto.tls.HashAlgorithm;
import org.spongycastle.crypto.tls.SignatureAlgorithm;
import org.spongycastle.crypto.tls.SignatureAndHashAlgorithm;
import org.spongycastle.crypto.tls.TlsAuthentication;
import org.spongycastle.crypto.tls.TlsClient;
import org.spongycastle.crypto.tls.TlsClientProtocol;
import org.spongycastle.crypto.tls.TlsCredentials;
import org.spongycastle.crypto.tls.TlsFatalAlert;
import org.spongycastle.crypto.tls.TlsSignerCredentials;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.util.encoders.Hex;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.SSLSocketFactory;


public class EidSSLSocketFactory extends SSLSocketFactory {

    private static final String TAG = "net.egelke.android.eid";
    private static final int TLS_RSA_WITH_AES_256_CBC_SHA = 0x0035;
    private static final int TLS_DHE_RSA_WITH_AES_256_CBC_SHA = 0x0039;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
            Security.addProvider(new BouncyCastleProvider());
    }

    //fixed fields
    private SecureRandom sr = new SecureRandom();

    //member fields
    Messenger eidService;

    /*
    //variable fields
    OutputStream os;
    DataInputStream is;

    int cypherSuite;
    byte[] clientRandom = new byte[28];
    byte[] serverRandom = new byte[28];
    byte[] preMasterSecret = new byte[48];
    List<X509Certificate> serverChain = new LinkedList<X509Certificate>();
    List<X509Certificate> clientChain = new LinkedList<X509Certificate>();
    */

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
        tlsClientProtocol.connect(new DefaultTlsClient() {
            @Override
            public TlsAuthentication getAuthentication() throws IOException {
                return new TlsAuthentication() {
                    @Override
                    public void notifyServerCertificate(Certificate serverCertificate) throws IOException {
                        //TODO
                    }

                    @Override
                    public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) throws IOException {
                        return new TlsSignerCredentials() {
                            @Override
                            public byte[] generateCertificateSignature(byte[] hash) throws IOException {
                                Log.w(TAG, "Request unsupported method: " + Hex.toHexString(hash));
                                throw new TlsFatalAlert(AlertDescription.internal_error);
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
        //DefaultTlsSignerCredentials
        return new Socket() {

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
        };

            /*
            //Send the client hello
            writeHandshake(createClientHello(clientRandom, TLS_RSA_WITH_AES_256_CBC_SHA));

            //receive the server responses (Hello, Certificate, Certificate Request)
            byte[] remaining = null;
            remaining = parseServerHello(readHandshake(remaining));
            remaining = parseCertificate(readHandshake(remaining));
            remaining = parseCertificateRequest(readHandshake(remaining));
            parseServerDone(readHandshake(remaining));

            //TODO:verify server certificates
            readCertificates();
            //TODO:verify if client certificate matches

            //send the client responses (Certificate, Client key exchange, Certificate Verify)
            writeHandshake(createCertificate(), createClientKeyExchange());
            */
    }

    /*
    private void writeHandshake(byte[]... messages) throws IOException {
        ByteArrayOutputStream mem = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(mem);

        out.writeByte(0x16); //handshake id
        out.writeShort(0x0301); //TLS v1.0

        //Length
        int length = 0;
        for(byte[] msg : messages) {
            length += msg.length;
        }
        out.writeShort(length);

        //messages
        for(byte[] msg : messages) {
            out.write(msg);
        }

        os.write(mem.toByteArray());
        os.flush();
    }

    private byte[] readHandshake(byte[] remaining) throws IOException {
        if (remaining != null) return remaining;

        int id = is.readUnsignedByte();
        if (id != 0x16) throw new IOException("Not a handshake message: " + id);
        int version = is.readUnsignedShort();
        if (version != 0x0301) throw new IOException("Unsupported TLS version: " + version);

        int length = is.readShort();
        if (length <= 0) throw new IOException("Empty message");
        byte[] buffer = new byte[length];
        readAll(is, buffer);
        return buffer;
    }

    private byte[] createClientHello(byte[] clientRandom, int... cyphers) throws IOException {
        ByteArrayOutputStream mem = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(mem);

        //init
        sr.nextBytes(clientRandom);

        //Header
        out.writeByte(0x01); //client hello id
        out.writeByte(0x00); //first byte of the hello client Length
        out.writeShort(39+(cyphers.length*2)); //client Hello Length

        //Data
        out.writeShort(0x0301); //TLS v1.0
        out.writeInt((int) (System.currentTimeMillis() / 1000L)); //Unix time
        out.write(clientRandom); //secure random
        out.writeByte(0x00); //session ID length
        out.writeShort(cyphers.length*2); //cypher suites length
        for(int cypher : cyphers) {
            out.writeShort(cypher);
        }
        out.writeByte(0x01); //compression method lengths
        out.writeByte(0x00); //Compression method: null

        return mem.toByteArray();
    }

    private void readCertificates() throws IOException, RemoteException, InterruptedException {
        final X509Certificate[] certs = new X509Certificate[2];
        final Message msg = Message.obtain(null, EidService.READ_DATA, 0, 0);
        msg.getData().putBoolean(FileId.AUTH_CERT.name(), true);
        msg.getData().putBoolean(FileId.INTCA_CERT.name(), true);
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
        msgThread.join(10*60*1000);
        if (certs[0] == null || certs[1] == null) throw new IOException("Failed to obtain eID certificates");
        clientChain.addAll(Arrays.asList(certs));
    }

    private byte[] createCertificate() throws IOException, CertificateEncodingException {
        ByteArrayOutputStream mem = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(mem);

        //Header
        out.writeByte(0x0B); //certificate id
        out.writeByte(0x00); //first byte of the Length
        int certLength = 0;
        for (X509Certificate cert : clientChain) {
            certLength += 3 + cert.getEncoded().length;
        }
        out.writeShort(3 + certLength); //rest of the Length bytes

        //Data
        out.writeByte(0x00); //first byte of the Certs Length
        out.writeShort(certLength); //rest of the Certs Length bytes
        for (X509Certificate cert : clientChain) {
            out.writeByte(0x00); //first byte of the Cert Length
            out.writeShort(cert.getEncoded().length); //rest of the Cert Length bytes
            out.write(cert.getEncoded());
        }

        return mem.toByteArray();

    }

    private byte[] createClientKeyExchange() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, NoSuchProviderException {
        //generate pre master secret, replace first bytes with protocol version
        sr.nextBytes(preMasterSecret);
        preMasterSecret[0] = 0x03;
        preMasterSecret[1] = 0x01;

        //generate the encrypted pre master secret
        Cipher cipher = Cipher.getInstance("RSA/None/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, serverChain.get(0).getPublicKey(), sr);
        byte[] encryptedPreMasterSecret = cipher.doFinal(preMasterSecret);

        ByteArrayOutputStream mem = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(mem);

        //Header
        out.writeByte(0x10); //certificate id
        out.writeByte(0x00); //first byte of the Length
        out.writeShort(encryptedPreMasterSecret.length+2);

        //Data
        out.writeShort(encryptedPreMasterSecret.length);
        out.write(encryptedPreMasterSecret);

        return mem.toByteArray();
    }

    private byte[] parseServerHello(byte[] msgs) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(msgs));

        int length = parseMsgHeader(in, 0x02);

        if (in.readUnsignedShort() != 0x0301) throw new IOException("Unsupported TLS version");
        int time = in.readInt(); //maybe do something with it later, but not now
        readAll(in, serverRandom);
        int sessionLen = in.readByte();
        if (sessionLen > 0) {
            byte[] sessionId = new byte[sessionLen];
            readAll(in, sessionId); //maybe do something with it later, but not now
        }
        cypherSuite = in.readShort();
        in.readByte(); //read the compression method

        return parseMsgFooter(in, msgs.length, length, 38 + sessionLen);
    }

    private byte[] parseCertificate(byte[] msgs) throws IOException, CertificateException, NoSuchProviderException {
        serverChain.clear();
        CertificateFactory factory = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(msgs));

        int length = parseMsgHeader(in, 0x0B);
        int certListBytes = 0;
        if (in.readUnsignedByte() != 0x00)
            throw new IOException("Cert list to long, not supported");
        int certListLength = in.readUnsignedShort();
        while (certListBytes < certListLength) {
            if (in.readUnsignedByte() != 0x00)
                throw new IOException("Cert to long, not supported");
            int certLength = in.readUnsignedShort();
            Log.v(TAG, "Reading cert of length " + certLength);
            byte[] certBytes = new byte[certLength];
            readAll(in, certBytes);
            X509Certificate cert =(X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
            Log.v(TAG, cert.getSubjectX500Principal().toString());
            serverChain.add(cert);
            certListBytes += 3 + certLength;
        }

        return parseMsgFooter(in, msgs.length, length, 3 + certListLength);
    }

    private byte[] parseCertificateRequest(byte[] msgs) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(msgs));

        int length = parseMsgHeader(in, 0x0D);

        //TODO: parse to verify if eID is in the list of certificates

        return parseMsgFooter(in, msgs.length, length, 0);
    }

    private void parseServerDone(byte[] msgs) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(msgs));
        int length = parseMsgHeader(in, 0x0E);

        if (parseMsgFooter(in, msgs.length, length, 2) != null)
            throw new IOException("More data after server done");
    }

    private static int parseMsgHeader(DataInputStream msgs, int type) throws IOException {
        if (msgs.readUnsignedByte() != type) throw new IOException("Not the expected msg message");
        if (msgs.readUnsignedByte() != 0x00) throw new IOException("Msg to long, not supported");
        return msgs.readShort();
    }

    private static byte[] parseMsgFooter(DataInputStream msgs, int msgLength,  int length, int parsed) throws IOException {
        msgs.skipBytes(length - parsed); //skip any remaining footers, we aren't interested

        if (length < (msgLength - 4)) {
            byte[] suffix = new byte[msgLength - 4 - length];
            readAll(msgs, suffix);
            return suffix;
        } else {
            return null;
        }
    }

    private static void readAll(InputStream in, byte[] buffer) throws IOException {
        int read = 0;
        while (read < buffer.length) {
            int count = in.read(buffer, read, buffer.length - read);
            Log.v(TAG, "Read " + count + " number of bytes: " + Hex.toHexString(buffer));
            read += count;
        }
    }
    */

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
