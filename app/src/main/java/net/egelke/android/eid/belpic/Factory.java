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

import android.os.Parcelable;
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

public abstract class Factory {
	private static final String[][] MONTHS = new String[][] { new String[] { "JAN" }, new String[] { "FEV", "FEB" }, new String[] { "MARS", "MAAR", "M??R" },
			new String[] { "AVR", "APR" }, new String[] { "MAI", "MEI" }, new String[] { "JUIN", "JUN" }, new String[] { "JUIL", "JUL" },
			new String[] { "AOUT", "AUG" }, new String[] { "SEPT", "SEP" }, new String[] { "OCT", "OKT" }, new String[] { "NOV" },
			new String[] { "DEC", "DEZ" } };

	private static String[] GENDER_MALE = { "M" };
	private static String[] GENDER_FEMALE = { "F", "V", "W" };

    public abstract Object create(byte[] bytes) throws IOException, CertificateException;

    protected Map<Integer, byte[]> parse(InputStream steam) throws IOException {
        int tag;
        Map<Integer, byte[]> values = new TreeMap<Integer, byte[]>();
        while ((tag = steam.read()) != -1) {
            int len = 0;
            int lenByte;
            do {
                lenByte = steam.read();
                len = (len << 7) + (lenByte & 0x7F);
            } while ((lenByte & 0x80) == 0x80);

            //In case the file is padded with nulls
            if (tag == 0 && len == 0) break;

            byte[] value = new byte[len];
            int read = 0;
            while (read < len) {
                read += steam.read(value, read, len - read);
            }
            Log.d("net.egelke.android.eid", String.format("Added tag %d (len %d)", tag, value.length));
            values.put(tag, value);
        }
        return values;
    }

	protected String toString(byte[] array) {
		if (array == null) return null;
		
		try {
			return new String(array, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			Log.e("net.egelke.android.eid", "Failed to convert to string", e);
			return null;
		}
	}

    protected String toHexString(byte[] array) {
		StringBuffer hexString = new StringBuffer();
		for (byte b : array) {
			int intVal = b & 0xff;
			if (intVal < 0x10)
				hexString.append("0");
			hexString.append(Integer.toHexString(intVal));
		}
		return hexString.toString();
	}

    protected Gender toGender(final byte[] value) {
		final String genderStr = toString(value);

		if (Arrays.binarySearch(GENDER_MALE, genderStr) >= 0) {
			return Gender.MALE;
		}
		if (Arrays.binarySearch(GENDER_FEMALE, genderStr) >= 0) {
			return Gender.FEMALE;
		}

		throw new RuntimeException("unknown gender: " + genderStr);
	}

    protected DocumentType toDocType(final byte[] value) {
		int docTypeCode = Integer.parseInt(toString(value), 10);
		switch (docTypeCode) {
		case 1:
			return DocumentType.BELGIAN_CITIZEN;
		case 6:
			return DocumentType.KIDS_CARD;
		case 7:
			return DocumentType.BOOTSTRAP_CARD;
		case 8:
			return DocumentType.HABILITATION_CARD;
		case 11:
			return DocumentType.FOREIGNER_A;
		case 12:
			return DocumentType.FOREIGNER_B;
		case 13:
			return DocumentType.FOREIGNER_C;
		case 14:
			return DocumentType.FOREIGNER_D;
		case 15:
			return DocumentType.FOREIGNER_E;
		case 16:
			return DocumentType.FOREIGNER_E_PLUS;
		case 17:
			return DocumentType.FOREIGNER_F;
		case 18:
			return DocumentType.FOREIGNER_F_PLUS;
		case 19:
			return DocumentType.EUROPEAN_BLUE_CARD_H;
		default:
			throw new RuntimeException("Unsupported card type: " + toString(value));
		}
	}

    protected EnumSet<SpecialStatus> toStatusSet(final byte[] value) {
		int statusCode = Integer.parseInt(toString(value), 10);
		switch(statusCode) {
		case 0:
			return EnumSet.noneOf(SpecialStatus.class);
		case 1:
			return EnumSet.of(SpecialStatus.WHITE_CANE);
		case 2:
			return EnumSet.of(SpecialStatus.EXTENDED_MINORITY);
		case 3:
			return EnumSet.of(SpecialStatus.WHITE_CANE, SpecialStatus.EXTENDED_MINORITY);
		case 4:
			return EnumSet.of(SpecialStatus.YELLOW_CANE);
		case 5:
			return EnumSet.of(SpecialStatus.YELLOW_CANE, SpecialStatus.EXTENDED_MINORITY);
		default:
			throw new RuntimeException("Unsupported special status: " + toString(value));
		}
	}

    protected SpecialOrganisation toSpecialOrganisation(final byte[] value) {
		if (value == null || value.length == 0) return null;
		
		int orgCode = Integer.parseInt(toString(value), 10);
		switch(orgCode) {
		case 1:
			return SpecialOrganisation.SHAPE;
		case 2:
			return SpecialOrganisation.NATO;
		case 4:
			return SpecialOrganisation.FORMER_BLUE_CARD_HOLDER;
		case 5:
			return SpecialOrganisation.RESEARCHER;
		default:
			throw new RuntimeException("Unsupported special organiation");
		}
	}

    protected Calendar toCalendar(final byte[] value) {
		final String dateStr = new String(value);
		Log.d("net.egelke.android.eid", String.format("Converting %s to calendar", dateStr));
		
		final int day = Integer.parseInt(dateStr.substring(0, 2));
		final int month = Integer.parseInt(dateStr.substring(3, 5));
		final int year = Integer.parseInt(dateStr.substring(6));
		return new GregorianCalendar(year, month -1, day);
	}

    protected Calendar toCalendar2(final byte[] value) {
		Calendar cal = new GregorianCalendar(1, 0, 1);
		String dateOfBirthStr;
		try {
			dateOfBirthStr = new String(value, "UTF-8").trim();
		} catch (final UnsupportedEncodingException uex) {
			return null;
		}
		int spaceIdx = dateOfBirthStr.indexOf('.');
		if (-1 == spaceIdx) {
			spaceIdx = dateOfBirthStr.indexOf(' ');
		}

		if (spaceIdx > 0) {
			final String dayStr = dateOfBirthStr.substring(0, spaceIdx);
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dayStr));
			String monthStr = dateOfBirthStr.substring(spaceIdx + 1, dateOfBirthStr.length() - 4 - 1);
			if (monthStr.endsWith(".")) {
				monthStr = monthStr.substring(0, monthStr.length() - 1);
			}
			final String yearStr = dateOfBirthStr.substring(dateOfBirthStr.length() - 4);
			cal.set(Calendar.YEAR, Integer.parseInt(yearStr));
			cal.set(Calendar.MONTH, toMonth(monthStr) - 1);

			return cal;
		}

		if (dateOfBirthStr.length() == 4) {
			cal.set(Calendar.YEAR, Integer.parseInt(dateOfBirthStr));
			return cal;
		}

		throw new RuntimeException("Unsupported Birth Date Format [" + dateOfBirthStr + "]");
	}

    protected int toMonth(String monthStr) {
		monthStr = monthStr.trim();
		for (int monthIdx = 0; monthIdx < MONTHS.length; monthIdx++) {
			final String[] monthNames = MONTHS[monthIdx];
			for (String monthName : monthNames) {
				if (monthName.equals(monthStr)) {
					return monthIdx + 1;
				}
			}
		}
		throw new RuntimeException("unknown month: " + monthStr);
	}
}
