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

import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import org.spongycastle.crypto.tls.DefaultTlsClient;
import org.spongycastle.crypto.tls.ExtensionType;
import org.spongycastle.crypto.tls.ProtocolVersion;
import org.spongycastle.crypto.tls.TlsAuthentication;
import org.spongycastle.crypto.tls.TlsSession;
import org.spongycastle.crypto.tls.TlsUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;

public class EidTlsClient extends DefaultTlsClient {

    private static final String TAG = "net.egelke.android.eid";

    private String host;
    private int port;
    private Messenger eidService;
    private EidTlsAuthentication tlsAuthentication;


    public EidTlsClient(Messenger eidService, String host, int port) {
        this.host = host;
        this.port = port;
        this.eidService = eidService;
        this.tlsAuthentication = new EidTlsAuthentication(this);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void sendToEid(Message msg) throws RemoteException {
        eidService.send(msg);
    }

    public boolean isTLSv12() {
        return TlsUtils.isTLSv12(context);
    }

    public ProtocolVersion getProtocol() {
        return context.getServerVersion();
    }

    public TlsSession getSession() {
        return context.getResumableSession();
    }

    public int getSelectedCipherSuite() {
        return selectedCipherSuite;
    }

    public org.spongycastle.asn1.x509.Certificate[] getClientCertificates() {
        return this.tlsAuthentication.getCertificates();
    }

    @Override
    public Hashtable getClientExtensions() throws IOException {
        Hashtable<Integer, byte[]> clientExtensions = super.getClientExtensions();
        if (clientExtensions == null) {
            clientExtensions = new Hashtable<>();
        }

        //Add host_name
        byte[] host_name = host.getBytes();

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(baos);
        dos.writeShort(host_name.length + 3); // entry size
        dos.writeByte(0); // name type = hostname
        dos.writeShort(host_name.length);
        dos.write(host_name);
        dos.close();

        clientExtensions.put(ExtensionType.server_name, baos.toByteArray());

        return clientExtensions;
    }

    @Override
    public TlsAuthentication getAuthentication() throws IOException {
        return tlsAuthentication;
    }
}
