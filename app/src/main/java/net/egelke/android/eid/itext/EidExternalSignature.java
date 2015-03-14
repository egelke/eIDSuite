/*
    This file is part of eID Suite.
    Copyright (C) 2014 Egelke BVBA

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
package net.egelke.android.eid.itext;

import com.itextpdf.text.pdf.security.ExternalSignature;

import net.egelke.android.eid.UserCancelException;
import net.egelke.android.eid.reader.EidCardReader;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;


public class EidExternalSignature implements ExternalSignature {
    private static final String HASH_ALG = "SHA256";
    private static final String ENC_ALG = "RSA";


    private EidCardReader reader;

    public EidExternalSignature(EidCardReader reader) {
        this.reader = reader;
    }

    @Override
    public String getHashAlgorithm() {
        return HASH_ALG;
    }

    @Override
    public String getEncryptionAlgorithm() {
        return ENC_ALG;
    }

    @Override
    public byte[] sign(byte[] bytes) throws GeneralSecurityException {
        MessageDigest messageDigest = MessageDigest.getInstance(getHashAlgorithm());
        byte hash[] = messageDigest.digest(bytes);

        try {
            return reader.signPkcs1(hash, EidCardReader.DigestAlg.SHA256, EidCardReader.Key.NON_REPUDIATION);
        } catch (IOException ioe) {
            throw new GeneralSecurityException(ioe);
        }catch (UserCancelException ioe) {
            throw new GeneralSecurityException(ioe);
        }
    }
}
