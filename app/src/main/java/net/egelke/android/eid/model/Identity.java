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
package net.egelke.android.eid.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Calendar;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

public class Identity implements Parcelable {

    public Identity()
    {

    }

    public Identity(Parcel parcel)
    {
        cardNumber = parcel.readString();
        chipNumber = parcel.readString();
        cardValidity = new Period();
        long begin = parcel.readLong();
        if (begin > 0) {
            cardValidity.begin = new GregorianCalendar();
            cardValidity.begin.setTimeInMillis(begin);
        }
        long end = parcel.readLong();
        if (end > 0) {
            cardValidity.end = new GregorianCalendar();
            cardValidity.end.setTimeInMillis(end);
        }
        cardDeliveryMunicipality = parcel.readString();
        nationalNumber = parcel.readString();
        familyName = parcel.readString();
        firstName = parcel.readString();
        middleNames = parcel.readString();
        nationality = parcel.readString();
        placeOfBirth = parcel.readString();

        long dob = parcel.readLong();
        if (dob > 0) {
            dateOfBirth = new GregorianCalendar();
            dateOfBirth.setTimeInMillis(parcel.readLong());
        }
        String g = parcel.readString();
        if (g != null && !g.isEmpty()) {
            gender = Gender.valueOf(parcel.readString());
        }
        nobleTitle = parcel.readString();
        String dt = parcel.readString();
        if (dt != null && !dt.isEmpty()) {
            documentType = DocumentType.valueOf(parcel.readString());
        }
        int sl = parcel.readInt();
        specialStatus = EnumSet.noneOf(SpecialStatus.class);
        if (sl > 0) {
            String[] status = new String[sl];
            parcel.readStringArray(status);
            for (String item : status) {
                specialStatus.add(SpecialStatus.valueOf(item));
            }
        }
    }

    public String cardNumber;

    public String chipNumber;

    public Period cardValidity;

    public String cardDeliveryMunicipality;

    public String nationalNumber;

    public String familyName;

    public String firstName;

    public String middleNames;

    public String nationality;

    public String placeOfBirth;

    public Calendar dateOfBirth;

    public Gender gender;

    public String nobleTitle;

    public DocumentType documentType;

    public EnumSet<SpecialStatus> specialStatus;

    //public String duplicate;

    //public SpecialOrganisation specialOrganisation;

    //public boolean memberOfFamily;


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(cardNumber);
        parcel.writeString(chipNumber);
        parcel.writeLong(cardValidity == null || cardValidity.begin == null ? 0 :
                cardValidity.begin.getTimeInMillis());
        parcel.writeLong(cardValidity == null || cardValidity.end == null ? 0 :
                cardValidity.end.getTimeInMillis());
        parcel.writeString(cardDeliveryMunicipality);
        parcel.writeString(nationalNumber);
        parcel.writeString(familyName);
        parcel.writeString(firstName);
        parcel.writeString(middleNames);
        parcel.writeString(nationality);
        parcel.writeString(placeOfBirth);
        parcel.writeLong(dateOfBirth == null ? 0 : dateOfBirth.getTimeInMillis());
        parcel.writeString(gender == null ? "" : gender.name());
        parcel.writeString(nobleTitle);
        parcel.writeString(documentType == null ? "" : documentType.name());
        if (specialStatus == null) {
            parcel.writeInt(0);
        } else {
            List<String> status = new LinkedList<String>();
            for (SpecialStatus item : specialStatus) {
                status.add(item.name());
            }
            parcel.writeInt(status.size());
            parcel.writeStringArray(status.toArray(new String[0]));
        }
    }

    public static final Parcelable.Creator<Identity> CREATOR
            = new Parcelable.Creator<Identity>() {

        @Override
        public Identity createFromParcel(Parcel parcel) {
            return new Identity(parcel);
        }

        @Override
        public Identity[] newArray(int size) {
            return new Identity[size];
        }
    };
}
