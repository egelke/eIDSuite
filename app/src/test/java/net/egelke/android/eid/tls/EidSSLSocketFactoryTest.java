package net.egelke.android.eid.tls;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;

import net.egelke.android.eid.EidService;
import net.egelke.android.eid.belpic.FileId;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import be.fedict.commons.eid.jca.BeIDProvider;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Message.class, Looper.class})
public class EidSSLSocketFactoryTest {

    public static KeyStore eidStore;

    @Mock private Bundle input;
    @Mock private Message inputMsg;

    @Mock private Bundle authCert;
    @Mock private Message authCertMsg;

    @Mock private Bundle caCert;
    @Mock private Message caCertMsg;

    @Mock private Messenger msger;

    @BeforeClass
    public static void init() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        Security.addProvider(new BeIDProvider());
        Security.addProvider(new BouncyCastleProvider());
        eidStore = KeyStore.getInstance("BeID");
        eidStore.load(null);

    }

    @Test
    public void testCreateSocket() throws Exception {
        inputMsg.what = EidService.READ_DATA;
        Mockito.when(inputMsg.getData()).thenReturn(input);
        Mockito.doNothing().when(input).putBoolean(null, false);

        PowerMockito.mockStatic(Message.class);
        Mockito.when(Message.obtain(null, EidService.READ_DATA, 0, 0)).thenReturn(inputMsg);

        PowerMockito.mockStatic(Looper.class);
        PowerMockito.doNothing().when(Looper.class, "prepare");
        PowerMockito.doNothing().when(Looper.class, "loop");

        Mockito.doNothing().when(msger).send(null);

        EidSSLSocketFactory ssl = new EidSSLSocketFactory(msger);

        /*
        EidSSLSocketFactory ssl = new EidSSLSocketFactory(new Messenger(new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                try {
                    switch (msg.what) {
                        case EidService.READ_DATA:
                            if (msg.getData().getBoolean(FileId.AUTH_CERT.name(), false)) {
                                authCertMsg.what = EidService.DATA_RSP;
                                authCertMsg.arg1 = FileId.AUTH_CERT.getId();
                                Mockito.when(authCertMsg.getData()).thenReturn(authCert);
                                Mockito.when(authCert.getSerializable(FileId.AUTH_CERT.name())).thenReturn(eidStore.getCertificate("Authentication"));
                                msg.replyTo.send(authCertMsg);
                            }
                            if (msg.getData().getBoolean(FileId.INTCA_CERT.name(), false)) {
                                caCertMsg.what = EidService.DATA_RSP;
                                caCertMsg.arg1 = FileId.INTCA_CERT.getId();
                                Mockito.when(caCertMsg.getData()).thenReturn(caCert);
                                Mockito.when(caCert.getSerializable(FileId.INTCA_CERT.name())).thenReturn(eidStore.getCertificate("CA"));
                                msg.replyTo.send(caCertMsg);
                            }
                            return true;
                        default:
                            return false;
                    }
                } catch (Exception e) {
                    return false;
                }
            }
        })));
        */

        Socket s = new Socket();
        s.connect(new InetSocketAddress("test.eid.belgium.be", 443));
        Socket wrapped = ssl.createSocket(s, "test.eid.belgium.be", 443, false);

        assertNotNull(wrapped);
    }
}