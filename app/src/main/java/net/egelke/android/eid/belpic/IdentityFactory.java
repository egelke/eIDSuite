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

public class IdentityFactory extends Factory {

    @Override
	public Identity create(byte[] bytes) throws IOException, CertificateException {
        Map<Integer, byte[]> tvMap = parse(new ByteArrayInputStream(bytes));

		Identity id = new Identity();
		id.cardNumber = toString(tvMap.get(1));
		id.chipNumber = toHexString(tvMap.get(2));
		id.cardValidity = new Period();
		id.cardValidity.begin = toCalendar(tvMap.get(3));
		id.cardValidity.end = toCalendar(tvMap.get(4));
		id.cardDeliveryMunicipality = toString(tvMap.get(5));
		id.nationalNumber = toString(tvMap.get(6));
		id.familyName = toString(tvMap.get(7));
		id.firstName = toString(tvMap.get(8));
		id.middleNames = toString(tvMap.get(9));
		id.nationality = toString(tvMap.get(10));
		id.placeOfBirth = toString(tvMap.get(11));
		id.dateOfBirth = toCalendar2(tvMap.get(12));
		id.gender = toGender(tvMap.get(13));
		id.nobleTitle = toString(tvMap.get(14));
		id.documentType = toDocType(tvMap.get(15));
		id.specialStatus = toStatusSet(tvMap.get(16));
		//photo digest isn't important here, so we skip it for the time being
		//id.duplicate = toString(tvMap.get(18));
		//id.specialOrganisation = toSpecialOrganisation(tvMap.get(19));
		//waiting for documentation...
		//id.memberOfFamily = true;
		
		return id;
	}

}
