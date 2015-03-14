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

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.HashMap;
import java.util.Map;

public class BeIDProvider extends Provider {

    public static final String NAME = "BeIDProvider";
    private static final String TAG = "net.egelke.android.eid";

    public BeIDProvider() {
        super(NAME, 1.0, "BeID Provider");

        putService(new BeIDService(this, "KeyStore", "BeID",
                BeIDKeyStore.class.getName()));

        final Map<String, String> signatureServiceAttributes = new HashMap<String, String>();
        signatureServiceAttributes.put("SupportedKeyClasses",
                BeIDPrivateKey.class.getName());
        putService(new BeIDService(this, "Signature", "SHA1withRSA",
                BeIDSignature.class.getName(), signatureServiceAttributes));
        putService(new BeIDService(this, "Signature", "SHA224withRSA",
                BeIDSignature.class.getName(), signatureServiceAttributes));
        putService(new BeIDService(this, "Signature", "SHA256withRSA",
                BeIDSignature.class.getName(), signatureServiceAttributes));
        putService(new BeIDService(this, "Signature", "SHA384withRSA",
                BeIDSignature.class.getName(), signatureServiceAttributes));
        putService(new BeIDService(this, "Signature", "SHA512withRSA",
                BeIDSignature.class.getName(), signatureServiceAttributes));
        putService(new BeIDService(this, "Signature", "NONEwithRSA",
                BeIDSignature.class.getName(), signatureServiceAttributes));
        putService(new BeIDService(this, "Signature", "RIPEMD128withRSA",
                BeIDSignature.class.getName(), signatureServiceAttributes));
        putService(new BeIDService(this, "Signature", "RIPEMD160withRSA",
                BeIDSignature.class.getName(), signatureServiceAttributes));
        putService(new BeIDService(this, "Signature", "RIPEMD256withRSA",
                BeIDSignature.class.getName(), signatureServiceAttributes));
        putService(new BeIDService(this, "Signature", "SHA1withRSAandMGF1",
                BeIDSignature.class.getName(), signatureServiceAttributes));
        putService(new BeIDService(this, "Signature", "SHA256withRSAandMGF1",
                BeIDSignature.class.getName(), signatureServiceAttributes));

        putService(new BeIDService(this, "KeyManagerFactory", "BeID",
                BeIDKeyManagerFactory.class.getName()));

        //putService(new BeIDService(this, "SecureRandom", "BeID",
        //        BeIDSecureRandom.class.getName()));
    }

    private static final class BeIDService extends Service {

        public BeIDService(final Provider provider, final String type,
                           final String algorithm, final String className) {
            super(provider, type, algorithm, className, null, null);
        }

        public BeIDService(final Provider provider, final String type,
                           final String algorithm, final String className,
                           final Map<String, String> attributes) {
            super(provider, type, algorithm, className, null, attributes);
        }

        @Override
        public Object newInstance(final Object constructorParameter)
                throws NoSuchAlgorithmException {
            Log.d(TAG, "newInstance: " + super.getType());
            if (super.getType().equals("Signature")) {
                return new BeIDSignature(this.getAlgorithm());
            }
            return super.newInstance(constructorParameter);
        }

        @Override
        public boolean supportsParameter(final Object parameter) {
            Log.d(TAG, "supportedParameter: " + parameter);
            return super.supportsParameter(parameter);
        }
    }
}
