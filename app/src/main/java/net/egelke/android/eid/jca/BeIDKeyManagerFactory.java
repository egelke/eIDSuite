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


import android.os.Messenger;
import android.util.Log;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;

public class BeIDKeyManagerFactory extends KeyManagerFactorySpi {

    private static final String TAG = "net.egelke.android.eid";

    private Messenger mEidService;

    @Override
    protected void engineInit(KeyStore ks, char[] password) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        Log.v(TAG, "BeIDKeyManagerFactory.engineInit(KeyStore, char[]");
    }

    @Override
    protected void engineInit(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException {
        Log.v(TAG, "BeIDKeyManagerFactory.engineInit(ManagerFactoryParameters)");
        mEidService = ((BeIDManagerFactoryParameters) spec).mEidService;
    }

    @Override
    protected KeyManager[] engineGetKeyManagers() {
        Log.v(TAG, "BeIDKeyManagerFactory.engineGetKeyManagers");
        return new KeyManager[] { new BeIDX509KeyManager(mEidService) };
    }
}
