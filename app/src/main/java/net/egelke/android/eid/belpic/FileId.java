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
package net.egelke.android.eid.belpic;


import java.io.IOException;
import java.security.cert.CertificateException;

public enum FileId {

    IDENTITY(1, IdentityFactory.class),
    ADDRESS(2, AddressFactory.class),
    PHOTO(3, null),
    AUTH_CERT(4, CertificateFactory.class),
    SIGN_CERT(5, CertificateFactory.class),
    INTCA_CERT(6, CertificateFactory.class),
    ROOTCA_CERT(7, CertificateFactory.class),
    RRN_CERT(8, CertificateFactory.class),
    IDENTITY_SIGN(9, null),
    ADDRESS_SIGN(10, null);

    private int id;
    private Class factoryClass;

    private FileId(int id, Class factoryClass) {
        this.id = id;
        this.factoryClass = factoryClass;
    }

    public int getId() {
        return id;
    }
    public Class getFactoryClass() { return factoryClass; }

    public Object parse(byte[] bytes) throws IOException, CertificateException {
        if (factoryClass == null) return bytes;

        Factory factory;
        try {
            factory = (Factory) factoryClass.newInstance();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return factory.create(bytes);
    }

    public static FileId fromId(int id) {
        for (FileId file : FileId.values()) {
            if (file.id == id) return file;
        }
        return null;
    }

}
