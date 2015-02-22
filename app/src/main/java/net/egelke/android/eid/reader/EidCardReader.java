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
import net.egelke.android.eid.belpic.FileId;
import net.egelke.android.eid.model.Address;
import net.egelke.android.eid.model.Identity;
import net.egelke.android.eid.usb.CCID;
import net.egelke.android.eid.usb.CardCallback;

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

    private static final Map<FileId, byte[]> FILES;
	private static final byte[] READ_BINARY = {0x00, (byte) 0xB0, 0x00, 0x00, (byte)0x00};
    private static final byte[] INIT_SIGN_NONREP_RAWPKCS1 = {0x00, 0x22, 0x41, (byte)0xB6, 0x04, (byte)0x80, 0x10 /*RSA-SSA with prepared EMSA*/, (byte) 0x84, (byte) 0x83 /*NON REPUDIATION KEY*/};
    private static final byte[] DO_SIGN_EMSA_SHA256 = {0x00, 0x2A, (byte) 0x9E, (byte) 0x9A, 0x30, 0x2f, 0x30, 0x0b,0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x04, 0x20};
    private static final byte[] VERIFY_PIN = {0x00, 0x20, 0x00, 0x01, 0x20, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] ATR_PATTERN = { (byte) 0x3b, (byte) 0x98, 0x00, (byte) 0x40, 0x00, (byte) 0x00, 0x00, 0x00, (byte) 0x01, (byte) 0x01, (byte) 0xad, (byte) 0x13, (byte) 0x10 };
    private static final byte[] ATR_MASK    = { (byte) 0xff, (byte) 0xff, 0x00, (byte) 0xff, 0x00, (byte) 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xf0 };

    static {
        FILES = new Hashtable<>();
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
        if (atr.length == ATR_PATTERN.length) {
            int i = 0;
            while (i < atr.length && (atr[i] & ATR_MASK[i]) == ATR_PATTERN[i])
                i++;
            if (i >= atr.length) {
                cardPresent = true;
            }
        }
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

    public synchronized byte[] signSha256Pkcs1(byte[] hash) throws IOException {
        //Prepare the card for signing (fixed usage of Signature Key)
        byte[] rsp = cardReader.transmitApdu(INIT_SIGN_NONREP_RAWPKCS1);
        if (rsp.length != 2) {
            Log.e(TAG, "APDU init sign command did not return 2 bytes but: " + rsp.length);
            throw new IOException("The card returned an invalid response");
        }
        if (rsp[rsp.length - 2] != ((byte) 0x90) || rsp[rsp.length - 2] != 0x0) {
            Log.e(TAG, String.format("APDU init sign command failed: %X %X", rsp[0], rsp[1]));
            throw new IOException("The card returned an error: " + rsp[0]);
        }

        //Unlock the card with the pin
        verifyPin();

        //Calculate the signature
        byte [] cmd = new byte[DO_SIGN_EMSA_SHA256.length + hash.length];
        System.arraycopy(DO_SIGN_EMSA_SHA256, 0, cmd, 0, DO_SIGN_EMSA_SHA256.length);
        System.arraycopy(hash, 0, cmd, DO_SIGN_EMSA_SHA256.length, hash.length);
        rsp = cardReader.transmitApdu(cmd);
        if (rsp.length != 2) {
            Log.e(TAG, "APDU do sign command did not return 2 bytes but: " + rsp.length);
            throw new IOException("The card returned an invalid response");
        }
        if (rsp[rsp.length - 2] != ((byte) 0x90) || rsp[rsp.length - 2] != 0x0) {
            Log.e(TAG, String.format("APDU do sign command failed: %X %X", rsp[0], rsp[1]));
            throw new IOException("The card returned an error: " + rsp[0]);
        }

        byte[] rspData = new byte[rsp.length-2];
        System.arraycopy(rsp, 0, rspData, 0, rspData.length);
        return rspData;
    }

    public void verifyPin() throws IOException {
        int retries = -1;
        boolean verified = false;
        while (!verified) {
            char[] pin = pinCallback.getPin(retries);
            byte[] cmd = Arrays.copyOf(VERIFY_PIN, VERIFY_PIN.length);
            cmd[4] = (byte) (cmd[4] | pin.length);
            for (int idx = 0; idx < pin.length; idx += 2) {
                byte digit1 = (byte) (pin[idx] - '0' << 4);
                byte digit2 = idx + 1 < pin.length ? (byte) (pin[idx + 1] - '0') : 0x0F;
                cmd[idx / 2 + 5] = (byte) (digit1 | digit2);
            }
            //Erase pin from memory
            Arrays.fill(pin, (char) 0);
            try {
                byte[] rsp = cardReader.transmitApdu(cmd);
                if (rsp.length != 2) {
                    Log.e(TAG, "APDU verify pin command did not return 2 bytes but: " + rsp.length);
                    throw new IOException("The card returned an invalid response");
                } else if (rsp[0] == (byte) 0x69 && rsp[1] == (byte) 0x83) {
                    Log.w(TAG, String.format("APDU verify pin command indicated that the card was blocked: %X %X", rsp[0], rsp[1]));
                    throw new CardBlockedException("The key on the card is blocked (to many tries)");
                } else if (rsp[0] == (byte) 0x63) {
                    retries = rsp[1] & 0x0F;
                } else if (rsp[0] != ((byte) 0x90) || rsp[1] != 0x0) {
                    Log.e(TAG, String.format("APDU select file command failed: %X %X", rsp[0], rsp[1]));
                    throw new IOException("The card returned an error: " + rsp[0]);
                } else {
                    verified = true;
                }
            } finally {
                //Erase pin from memory
                Arrays.fill(cmd, (byte) 0);
            }
        }
    }
	
	private void selectFile(byte[] cmd) throws IOException {
		byte[] rsp = cardReader.transmitApdu(cmd);
		if (rsp.length != 2) {
			Log.e(TAG, "APDU select file command did not return 2 bytes but: " + rsp.length);
			throw new IOException("The card returned an invalid response");
		}
		if (rsp[0] != ((byte) 0x90) || rsp[1] != 0x0) {
			Log.e(TAG, String.format("APDU select file command failed: %X %X", rsp[0], rsp[1]));
			throw new IOException("The card returned an error: " + rsp[0]);
		}
	}
	
	private byte[] readSelectedFile() throws IOException {
        byte[] rsp;
		int offset = 0;
		byte[] cmd = Arrays.copyOf(READ_BINARY, READ_BINARY.length);
		ByteArrayOutputStream idFileOut = new ByteArrayOutputStream();
		do {
			cmd[2] = (byte) (offset >> 8);
			cmd[3] = (byte) (offset & 0xFF);
			rsp = cardReader.transmitApdu(cmd);
			if (rsp.length < 2) {
				Log.e(TAG, "APDU read identify file command did return less then 2 bytes: " + rsp.length);
				throw new IOException("The card return an invalid response");
			}
			Log.d(TAG, String.format("Result %X %X, data length: %d", rsp[rsp.length - 2], rsp[rsp.length - 1], rsp.length));
			if (rsp[rsp.length - 2] == ((byte) 0x6B) && rsp[rsp.length - 1] == 0x0) {
				// Finished, there where no more bytes
				break;
			}
			if (rsp[rsp.length - 2] == ((byte) 0x6C)) {
				// Almost finished, reading less
				cmd[4] = rsp[1];
				continue;
			}

			if ((rsp[rsp.length - 2] != ((byte) 0x90) || rsp[rsp.length - 1] != 0x0)) {
				throw new IOException("The card returned an error: " + rsp[0]);
			}
			idFileOut.write(rsp, 0, rsp.length - 2);
			offset += rsp.length - 2;
		} while (rsp.length == 258 || rsp[0] == ((byte) 0x6C));

		Log.d(TAG, String.format("File read (len %d)", idFileOut.toByteArray().length));
		return idFileOut.toByteArray();
	}

}
