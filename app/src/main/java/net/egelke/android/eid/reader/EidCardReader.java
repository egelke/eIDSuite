/*
    This file is part of eID Suite.
    Copyright (C) 2014-2015 Egelke BVBA

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
package net.egelke.android.eid.reader;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import net.egelke.android.eid.CardBlockedException;
import net.egelke.android.eid.UserCancelException;
import net.egelke.android.eid.belpic.FileId;
import net.egelke.android.eid.model.Address;
import net.egelke.android.eid.model.Identity;
import net.egelke.android.eid.usb.CCID;
import net.egelke.android.eid.usb.CardCallback;
import net.egelke.android.eid.usb.diagnostic.Device;

import org.spongycastle.asn1.ASN1Encoding;
import org.spongycastle.asn1.DERNull;
import org.spongycastle.asn1.nist.NISTObjectIdentifiers;
import org.spongycastle.asn1.oiw.OIWObjectIdentifiers;
import org.spongycastle.asn1.x509.AlgorithmIdentifier;
import org.spongycastle.asn1.x509.DigestInfo;
import org.spongycastle.asn1.x509.X509ObjectIdentifiers;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;

public class EidCardReader implements Closeable {

    private static final String TAG = "net.egelke.android.eid";

    public static enum Key {
        AUTHENTICATION,
        NON_REPUDIATION
    }

    public static enum DigestAlg {
        RAW,
        SHA1,
        SHA256
    }

    //https://www.eftlab.com.au/index.php/site-map/knowledge-base/118-apdu-response-list
    private static enum ApduSwCode {
        OK,
        OkGetRsp,
        VerifyFail,
        SecConNotSatisfied,
        AuthBlocked,
        WrongParamP1P2,
        BadLengthLeCorrectIsXX
    }

    private static final Map<FileId, byte[]> FILES;
	private static final byte[] READ_BINARY = {0x00, (byte) 0xB0, 0x00, 0x00, (byte)0x00};
    private static final byte[] GET_RESPONSE = {0x00, (byte) 0xC0, 0x00, 0x00, (byte)0x00};
    private static final byte[] INIT_SIGN_AUTH_RAWPKCS1 = {0x00, 0x22, 0x41, (byte)0xB6, 0x05, //ISO/IEC 7816 header, data below
            0x04, (byte)0x80, 0x01 /*RSA-SSA with prepared EMSA*/, (byte) 0x84, (byte) 0x82 /*AUTHENTICATION KEY*/};
    private static final byte[] INIT_SIGN_NONREP_RAWPKCS1 = {0x00, 0x22, 0x41, (byte)0xB6, 0x05, //ISO/IEC 7816 header, data below
            0x04, (byte)0x80, 0x01 /*RSA-SSA with prepared EMSA*/, (byte) 0x84, (byte) 0x83 /*NON REPUDIATION KEY*/};
    private static final byte[] DO_SIGN = {0x00, 0x2A, (byte) 0x9E, (byte) 0x9A, 0x00};
    //private static final byte[] DO_SIGN_EMSA_SHA1 = {0x00, 0x2A, (byte) 0x9E, (byte) 0x9A, 0x00, //ISO/IEC 7816 header, data below
    //        0x30, 0x00, 0x30, 0x07, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x04, 0x00};
    //private static final byte[] DO_SIGN_EMSA_SHA256 = {0x00, 0x2A, (byte) 0x9E, (byte) 0x9A, 0x00, //ISO/IEC 7816 header, data below
    //        0x30, 0x00, 0x30, 0x0b, 0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x04, 0x00};
    private static final byte[] VERIFY_PIN = {0x00, 0x20, 0x00, 0x01, 0x08, //ISO/IEC 7816 header, data below
            0x20, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    private static final byte[] ATR_PATTERN = { (byte) 0x3b, (byte) 0x98, 0x00, (byte) 0x40, 0x00, (byte) 0x00, 0x00, 0x00, (byte) 0x01, (byte) 0x01, (byte) 0xad, (byte) 0x13, (byte) 0x10 };
    private static final byte[] ATR_MASK    = { (byte) 0xff, (byte) 0xff, 0x00, (byte) 0xff, 0x00, (byte) 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xf0 };

    static {
        FILES = new Hashtable<FileId, byte[]>();
        FILES.put(FileId.IDENTITY, new byte[]{0x00, (byte) 0xA4, 0x08, 0x0C, 0x06, 0x3F, 0x00, (byte) 0xDF, 0x01, 0x40, 0x31});
        FILES.put(FileId.ADDRESS, new byte[]{0x00, (byte) 0xA4, 0x08, 0x0C, 0x06, 0x3F, 0x00, (byte) 0xDF, 0x01, 0x40, 0x33});
        FILES.put(FileId.PHOTO, new byte[]{0x00, (byte) 0xA4, 0x08, 0x0C, 0x06, 0x3F, 0x00, (byte) 0xDF, 0x01, 0x40, 0x35});
        FILES.put(FileId.AUTH_CERT, new byte[]{0x00, (byte) 0xA4, 0x08, 0x0C, 0x06, 0x3F, 0x00, (byte) 0xDF, 0x00, 0x50, 0x38});
        FILES.put(FileId.SIGN_CERT, new byte[]{0x00, (byte) 0xA4, 0x08, 0x0C, 0x06, 0x3F, 0x00, (byte) 0xDF, 0x00, 0x50, 0x39});
        FILES.put(FileId.INTCA_CERT, new byte[]{0x00, (byte) 0xA4, 0x08, 0x0C, 0x06, 0x3F, 0x00, (byte) 0xDF, 0x00, 0x50, 0x3A});
        FILES.put(FileId.ROOTCA_CERT, new byte[]{0x00, (byte) 0xA4, 0x08, 0x0C, 0x06, 0x3F, 0x00, (byte) 0xDF, 0x00, 0x50, 0x3B});
        FILES.put(FileId.RRN_CERT, new byte[]{0x00, (byte) 0xA4, 0x08, 0x0C, 0x06, 0x3F, 0x00, (byte) 0xDF, 0x00, 0x50, 0x3C});
        FILES.put(FileId.IDENTITY_SIGN, new byte[]{0x00, (byte) 0xA4, 0x08, 0x0C, 0x06, 0x3F, 0x00, (byte) 0xDF, 0x01, 0x40, 0x32});
        FILES.put(FileId.ADDRESS_SIGN, new byte[]{0x00, (byte) 0xA4, 0x08, 0x0C, 0x06, 0x3F, 0x00, (byte) 0xDF, 0x01, 0x40, 0x34});
    }

    public static boolean isEid(byte[] atr) {
        if (atr == null || atr.length != ATR_PATTERN.length) return false;

        int i = 0;
        while (i < atr.length && (atr[i] & ATR_MASK[i]) == ATR_PATTERN[i])
            i++;
        return i >= atr.length;

    }

    private CCID cardReader;
    private EidCardCallback eidCardCallback;
    private PinCallback pinCallback;
    private boolean cardPresent;

    public EidCardCallback getEidCardCallback() {
        return eidCardCallback;
    }

    public void setEidCardCallback(EidCardCallback value) {
        this.eidCardCallback = value;
    }

    public PinCallback getPinCallback() { return pinCallback; }

    public void setPinCallback(PinCallback value) { this.pinCallback = value; }

    public boolean isOpen() {
        return cardReader.isOpen();
    }

    public boolean isCardPresent() {
        return cardPresent;
    }
	
	public EidCardReader(UsbManager manager, final UsbDevice device) {
        cardPresent = false;
        this.cardReader = new CCID(manager, device);
        this.cardReader.setCallback(new CardReaderCallback());
	}

    public Diagnose diagnose() {
        if (cardReader.isOpen()) throw new IllegalStateException("Can't open an open eid reader");


        byte[] atr = null;
        Device dDiag = cardReader.diagnose();
        if (cardReader.isCCIDCompliant()) {
            try {
                cardReader.open();
                try {
                    atr = cardReader.powerOn();
                } catch(Exception e) {
                    //we are diagnosing, we that is ok
                }
                cardReader.close();
            } catch (Exception e) {
                //we are diagnosing, so we don't care much
            }
        }
        return new Diagnose(dDiag, atr);
    }

	public synchronized void open() throws IOException {
        if (cardReader.isOpen()) {
            Log.d(TAG, "EidCardReader is already open");
            return;
        }

        cardPresent = false;
        cardReader.open();
        try {
            cardReader.powerOff();
        } catch (Exception e) {
            Log.d(TAG, "Failed power off after open", e);
        }
        try {
            processAtr(cardReader.powerOn());
        } catch (IOException io) {
            Log.d(TAG, "Failed power on after open");
        }
	}

    private class CardReaderCallback implements CardCallback
    {

        @Override
        public void inserted() {
            try {
                processAtr(cardReader.powerOn());
                if (cardPresent && eidCardCallback != null) eidCardCallback.inserted();
            } catch (IOException io) {
                Log.d(TAG, "Failed to handle card insert", io);
            }
        }

        @Override
        public void removed() {
            if (cardPresent) {
                cardPresent = false;
                if (eidCardCallback != null) eidCardCallback.removed();
            }
        }
    }

    private void processAtr(byte[] atr) {
        cardPresent = isEid(atr);
    }
	
	@Override
	public synchronized void close() throws IOException {
        if (!cardReader.isOpen()) {
            Log.d(TAG, "EidCardReader is already closed");
            return;
        }
        if (cardPresent) {
            try {
                cardReader.powerOff();
            } catch (UnsupportedOperationException uoe) {
                Log.d(TAG, "EidCardReader can't power down the card");
            }
            cardPresent = false;
        }
        cardReader.close();
	}

	public synchronized byte[] readFileRaw(Enum fileOrCertificate) throws IOException {
        if (!cardPresent) {
            throw new IOException("No card present");
        }

        Log.d(TAG, "Reading file: " + fileOrCertificate.name());
        selectFile(FILES.get(fileOrCertificate));
        return readSelectedFile();
	}


    public X509Certificate readCertificate(FileId certificate) throws IOException, CertificateException {
        if (certificate.getFactoryClass() != net.egelke.android.eid.belpic.CertificateFactory.class)
            throw new IllegalArgumentException();

        return (X509Certificate) certificate.parse(readFileRaw(certificate));
    }


	public Identity readIdentity() throws IOException {
        try {
            return (Identity) FileId.IDENTITY.parse(readFileRaw(FileId.IDENTITY));
        } catch (CertificateException ce) {
            throw new RuntimeException(ce);
        }
	}


	public Address readAddress() throws IOException {
        try {
            return (Address) FileId.ADDRESS.parse(readFileRaw(FileId.ADDRESS));
        } catch (CertificateException ce) {
            throw new RuntimeException(ce);
        }
	}

    public byte[] readPhoto() throws IOException {
		return readFileRaw(FileId.PHOTO);
	}

    public synchronized byte[] signPkcs1(byte[] hash, DigestAlg alg, Key key) throws IOException, UserCancelException {
        //Prepare the card for signing
        byte[] rsp;
        switch (key) {
            case NON_REPUDIATION:
                rsp = cardReader.transmitApdu(INIT_SIGN_NONREP_RAWPKCS1);
                break;
            case AUTHENTICATION:
                rsp = cardReader.transmitApdu(INIT_SIGN_AUTH_RAWPKCS1);
                break;
            default:
                throw new IllegalArgumentException("Unknown key type");
        }
        if (validateResponse(rsp) != ApduSwCode.OK) {
            throw new IOException(String.format("The card returned an error: SW=%X %X", rsp[0], rsp[1]));
        }

        //Unlock the card with the pin if needed
        if (key == Key.NON_REPUDIATION) verifyPin();

        //Calculate the signature
        byte [] digestInfo;
        switch (alg) {
            case RAW:
                digestInfo = hash;
                break;
            case SHA1:
                digestInfo = new DigestInfo(new AlgorithmIdentifier(X509ObjectIdentifiers.id_SHA1,
                        DERNull.INSTANCE), hash).getEncoded(ASN1Encoding.DER);
                break;
            case SHA256:
                digestInfo = new DigestInfo(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256,
                        DERNull.INSTANCE), hash).getEncoded(ASN1Encoding.DER);
                break;
            default:
                throw new IllegalArgumentException("Unknown digest algorithm");
        }
        byte [] cmd = new byte[DO_SIGN.length + digestInfo.length];
        System.arraycopy(DO_SIGN, 0, cmd, 0, DO_SIGN.length);
        cmd[4] = (byte) digestInfo.length;
        System.arraycopy(digestInfo, 0, cmd, DO_SIGN.length, digestInfo.length);

        byte[] data;
        rsp = cardReader.transmitApdu(cmd);
        switch (validateResponse(rsp)) {
            case OK:
                data = new byte[rsp.length-2];
                System.arraycopy(rsp, 0, data, 0, data.length);
                break;
            case OkGetRsp:
                data = getResponse(rsp[1]);
                break;
            case SecConNotSatisfied:
                verifyPin();
                rsp = cardReader.transmitApdu(cmd);
                switch (validateResponse(rsp)) {
                    case OK:
                        data = new byte[rsp.length-2];
                        System.arraycopy(rsp, 0, data, 0, data.length);
                        break;
                    case OkGetRsp:
                        data = getResponse(rsp[1]);
                        break;
                    default:
                        throw new IOException(String.format("The card returned an error: SW=%X %X", rsp[0], rsp[1]));
                }
                break;
            default:
                throw new IOException(String.format("The card returned an error: SW=%X %X", rsp[0], rsp[1]));
        }

        return data;
    }

    public void verifyPin() throws IOException, UserCancelException {
        int retries = -1;
        while (true) {
            char[] pin = pinCallback.getPin(retries);
            byte[] cmd = Arrays.copyOf(VERIFY_PIN, VERIFY_PIN.length);
            cmd[5] = (byte) (cmd[5] | pin.length);
            for (int idx = 0; idx < pin.length; idx += 2) {
                byte digit1 = (byte) ((pin[idx] - '0') << 4);
                byte digit2 = idx + 1 < pin.length ? (byte) (pin[idx + 1] - '0') : 0x0F;
                cmd[idx / 2 + 6] = (byte) (digit1 | digit2);
            }

            //Erase pin from memory
            Arrays.fill(pin, (char) 0);
            try {
                byte[] rsp = cardReader.transmitApdu(cmd);
                switch (validateResponse(rsp)) {
                    case OK:
                        return;
                    case VerifyFail:
                        retries = rsp[1] & 0x0F;
                        break;
                    case AuthBlocked:
                        throw new CardBlockedException("The key on the card is blocked (to many tries)");
                    default:
                        throw new IOException(String.format("The card returned an error: SW=%X %X", rsp[0], rsp[1]));
                }
            } finally {
                //Erase pin from memory
                Arrays.fill(cmd, (byte) 0);
            }
        }
    }
	
	private void selectFile(byte[] cmd) throws IOException {
		byte[] rsp = cardReader.transmitApdu(cmd);
        if (validateResponse(rsp) != ApduSwCode.OK) {
            throw new IOException(String.format("The card returned an error: SW=%X %X", rsp[0], rsp[1]));
        }
	}
	
	private byte[] readSelectedFile() throws IOException {
        byte[] rsp;
		int offset = 0;
        ApduSwCode status;
		byte[] cmd = Arrays.copyOf(READ_BINARY, READ_BINARY.length);
		ByteArrayOutputStream idFileOut = new ByteArrayOutputStream();
		do {
			cmd[2] = (byte) (offset >> 8);
			cmd[3] = (byte) (offset & 0xFF);
			rsp = cardReader.transmitApdu(cmd);
            status = validateResponse(rsp);
            switch (status) {
                case OK:
                    idFileOut.write(rsp, 0, rsp.length - 2);
                    offset += rsp.length - 2;
                    break;
                case WrongParamP1P2:
                    //Finished, there where no more bytes (we passed the end of the file)
                    break;
                case BadLengthLeCorrectIsXX:
                    cmd[4] = rsp[1];
                    break;
                default:
                    throw new IOException(String.format("The card returned an error: SW=%X %X", rsp[0], rsp[1]));
            }
		} while (status == ApduSwCode.BadLengthLeCorrectIsXX
                || (status == ApduSwCode.OK && rsp.length == 258));

		Log.d(TAG, String.format("File read (len %d)", idFileOut.toByteArray().length));
		return idFileOut.toByteArray();
	}

    private byte[] getResponse(int len) throws IOException {
        byte[] cmd = Arrays.copyOf(GET_RESPONSE, GET_RESPONSE.length);
        cmd[4] = (byte) len;

        byte[] rsp = cardReader.transmitApdu(cmd);
        switch (validateResponse(rsp)) {
            case OK:
                byte[] data = new byte[rsp.length -2];
                System.arraycopy(rsp, 0, data, 0, data.length);
                return data;
            default:
                //TODO: BadLengthLeCorrectIsXX
                throw new IOException(String.format("The card returned an error: SW=%X %X", rsp[0], rsp[1]));
        }
    }

    private ApduSwCode validateResponse(byte[] rsp) throws IOException {
        if (rsp.length < 2) {
            Log.e(TAG, "APDU command did not return 2 bytes: " + rsp.length);
            throw new IOException("The card returned an invalid response");
        } else if (rsp[rsp.length - 2] == ((byte) 0x90) && rsp[rsp.length - 1] == 0x00) {
            return ApduSwCode.OK;
        } else if (rsp[rsp.length - 2] == (byte) 0x61) {
            Log.d(TAG, String.format("APDU OK, still %X bytes available", rsp[rsp.length-1]));
            return ApduSwCode.OkGetRsp;
        } else if (rsp[rsp.length - 2] ==((byte)0x63) && (rsp[rsp.length - 1] & 0xF0) == 0xC0) {
            Log.i(TAG, String.format("Verify fail, %X tries left.", rsp[1] & 0x0F));
            return ApduSwCode.VerifyFail;
        } else if (rsp[rsp.length - 2] == ((byte) 0x69) && rsp[rsp.length - 1] == ((byte)0x82)) {
            Log.w(TAG, "Security condition not satisfied");
            return ApduSwCode.SecConNotSatisfied;
        } else if (rsp[rsp.length - 2] == ((byte) 0x69) && rsp[rsp.length - 1] == ((byte)0x83)) {
            Log.w(TAG, "Authentication method blocked");
            return ApduSwCode.AuthBlocked;
        } else if (rsp[rsp.length - 2] == ((byte) 0x6B) && rsp[rsp.length - 1] == 0x0) {
            Log.w(TAG, "APDU wrong parameter(s) P1-P2");
            return ApduSwCode.WrongParamP1P2;
        } else if (rsp[rsp.length - 2] == ((byte) 0x6C)) {
            Log.d(TAG, String.format("APDU Bad length value in Le; 'xx' is the correct exact Le", rsp[rsp.length-1]));
            return ApduSwCode.BadLengthLeCorrectIsXX;
        } else {
            Log.w(TAG, String.format("APDU command failed: %X %X", rsp[rsp.length - 2], rsp[rsp.length - 1]));
            throw new IOException("The card returned an error: " + rsp[rsp.length - 2]);
        }
    }

}
