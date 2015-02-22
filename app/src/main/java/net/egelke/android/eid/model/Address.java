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

public class Address implements Parcelable {

    public Address()
    {

    }

    public Address(Parcel parcel)
    {
        streetAndNumber = parcel.readString();
        zip = parcel.readString();
        municipality = parcel.readString();
    }

    public String streetAndNumber;

    public String zip;

    public String municipality;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(streetAndNumber);
        parcel.writeString(zip);
        parcel.writeString(municipality);
    }

    public static final Parcelable.Creator<Address> CREATOR
            = new Parcelable.Creator<Address>() {

        @Override
        public Address createFromParcel(Parcel parcel) {
            return new Address(parcel);
        }

        @Override
        public Address[] newArray(int size) {
            return new Address[size];
        }
    };
}
