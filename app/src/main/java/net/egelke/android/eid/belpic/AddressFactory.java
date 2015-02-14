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
package net.egelke.android.eid.belpic;

import android.util.Log;

import net.egelke.android.eid.model.Address;
import net.egelke.android.eid.model.DocumentType;
import net.egelke.android.eid.model.Gender;
import net.egelke.android.eid.model.Identity;
import net.egelke.android.eid.model.Period;
import net.egelke.android.eid.model.SpecialOrganisation;
import net.egelke.android.eid.model.SpecialStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.TreeMap;

public class AddressFactory extends Factory {

    @Override
	public Address create(byte[] bytes) throws IOException, CertificateException {
        Map<Integer, byte[]> tvMap = parse(new ByteArrayInputStream(bytes));

		Address a = new Address();
		a.streetAndNumber = toString(tvMap.get(1));
		a.zip = toString(tvMap.get(2));
		a.municipality = toString(tvMap.get(3));
		
		return a;
	}

}
