/*
    This file is part of eID Suite.
    Copyright (C) 2015 Egelke BVBA
    Copyright (C) 2008-2013 FedICT (Commons eID Project)

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
package net.egelke.android.eid.jca;


import android.util.Log;

import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.SignatureSpi;

public class BeIDSignature extends SignatureSpi {

    private static final String TAG = "net.egelke.android.eid";

    BeIDSignature(final String signatureAlgorithm)
            throws NoSuchAlgorithmException {
        Log.d(TAG, "BeIDSignature.BeIDSignature");
    }

    @Override
    protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
        Log.d(TAG, "BeIDSignature.engineInitVerify");
    }

    @Override
    protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
        Log.d(TAG, "BeIDSignature.engineInitSign");

    }

    @Override
    protected void engineUpdate(byte b) throws SignatureException {
        Log.d(TAG, "BeIDSignature.engineUpdate");
    }

    @Override
    protected void engineUpdate(byte[] b, int off, int len) throws SignatureException {
        Log.d(TAG, "BeIDSignature.engineUpdate");
    }

    @Override
    protected byte[] engineSign() throws SignatureException {
        Log.d(TAG, "BeIDSignature.engineSign");
        return new byte[0];
    }

    @Override
    protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
        Log.d(TAG, "BeIDSignature.engineVerify");
        return false;
    }

    @Override
    protected void engineSetParameter(String param, Object value) throws InvalidParameterException {
        Log.d(TAG, "BeIDSignature.engineSetParameter");
    }

    @Override
    protected Object engineGetParameter(String param) throws InvalidParameterException {
        Log.d(TAG, "BeIDSignature.engineGetParameter");
        return null;
    }
}
