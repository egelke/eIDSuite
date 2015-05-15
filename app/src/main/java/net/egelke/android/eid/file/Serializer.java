/*
    This file is part of eID Suite.
    Copyright (C) 2015 Egelke BVBA

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
package net.egelke.android.eid.file;


import android.util.Base64;
import android.util.Xml;

import net.egelke.android.eid.model.Address;
import net.egelke.android.eid.model.Identity;
import net.egelke.android.eid.model.SpecialStatus;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class Serializer {

    //xml
    private OutputStream stream;
    private XmlSerializer push = Xml.newSerializer();

    //cache
    Identity id;
    Address address;
    byte[] photo;
    byte[] root;
    byte[] intca;
    byte[] auth;
    byte[] sign;
    byte[] rrn;

    public Serializer(OutputStream stream) {
        this.stream = stream;
    }


    public void write() throws IOException {

        DateFormat df =  new SimpleDateFormat("yyyyMMdd");

        push = Xml.newSerializer();
        push.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        push.setOutput(stream, null);
        push.startDocument("UTF-8", true);
        push.startTag(null, "eid");

        //Identity
        if (id != null) {
            push.startTag(null, "identity");
            if (id.nationalNumber != null) push.attribute(null, "nationalnumber", id.nationalNumber);
            if (id.dateOfBirth != null) push.attribute(null, "dateofbirth", df.format(id.dateOfBirth.getTime()));
            if (id.gender != null) push.attribute(null, "gender", id.gender.name().toLowerCase());
            if (id.nobleTitle != null && !id.nobleTitle.isEmpty()) push.attribute(null, "noblecondition", id.nobleTitle);
            if (id.specialStatus != null || id.specialStatus.isEmpty()) {
                push.attribute(null, "specialstatus", "NO_STATUS");
            } else if (id.specialStatus.size() == 1) {
                push.attribute(null, "specialstatus", id.specialStatus.toArray(new SpecialStatus[0])[0].name());
            } else if (id.specialStatus.contains(SpecialStatus.WHITE_CANE) && id.specialStatus.contains(SpecialStatus.EXTENDED_MINORITY)) {
                push.attribute(null, "specialstatus", "WHITE_CANE_EXTENDED_MINORITY");
            } else if (id.specialStatus.contains(SpecialStatus.YELLOW_CANE) && id.specialStatus.contains(SpecialStatus.EXTENDED_MINORITY)) {
                push.attribute(null, "specialstatus", "YELLOW_CANE_EXTENDED_MINORITY");
            }
            if (id.firstName != null) writeElement("name", id.familyName);
            if (id.firstName != null) writeElement("firstname", id.firstName);
            if (id.middleNames != null) writeElement("middlenames", id.middleNames);
            if (id.nationality != null) writeElement("nationality", id.nationality);
            if (id.placeOfBirth != null) writeElement("placeofbirth", id.placeOfBirth);

            if (photo != null) {
                String value = Base64.encodeToString(photo, Base64.DEFAULT);
                writeElement("photo", value);
            }
            push.endTag(null, "identity");

            push.startTag(null, "card");
            if (id.documentType != null) push.attribute(null, "documenttype", id.documentType.name().toLowerCase());
            if (id.cardNumber != null) push.attribute(null, "cardnumber", id.cardNumber);
            if (id.chipNumber != null) push.attribute(null, "chipnumber", id.chipNumber);
            if (id.cardValidity != null && id.cardValidity.begin != null)
                push.attribute(null, "validitydatebegin", df.format(id.cardValidity.begin.getTime()));
            if (id.cardValidity != null && id.cardValidity.end != null)
                push.attribute(null, "validitydateend", df.format(id.cardValidity.end.getTime()));

            if (id.cardDeliveryMunicipality != null) writeElement("deliverymunicipality", id.cardDeliveryMunicipality);

            push.endTag(null, "card");
        } else if (photo != null) {
            push.startTag(null, "identity");
            String value = Base64.encodeToString(photo, Base64.DEFAULT);
            writeElement("photo", value);
            push.endTag(null, "identity");
        }

        //Address
        if (address != null) {
            push.startTag(null, "address");
            if (address.streetAndNumber != null) writeElement("streetandnumber", address.streetAndNumber);
            if (address.zip != null) writeElement("zip", address.zip);
            if (address.municipality != null) writeElement("municipality", address.municipality);
            push.endTag(null, "address");
        }

        //certificates
        if (root != null || intca != null || auth != null || sign != null || rrn != null) {
            push.startTag(null, "certificates");
            if (root != null) writeElement("root", Base64.encodeToString(root, Base64.DEFAULT));
            if (intca != null) writeElement("citizenca", Base64.encodeToString(intca, Base64.DEFAULT));
            if (auth != null) writeElement("authentication", Base64.encodeToString(auth, Base64.DEFAULT));
            if (sign != null) writeElement("signing", Base64.encodeToString(sign, Base64.DEFAULT));
            if (rrn != null) writeElement("rrn", Base64.encodeToString(rrn, Base64.DEFAULT));
            push.endTag(null, "certificates");
        }

        push.endTag(null, "eid");
        push.endDocument();
        push.flush();
    }

    public void setIdentity(Identity value) {
        this.id = value;
    }

    public void setAddress(Address value) {
        this.address = value;
    }

    public void setPhoto(byte[] photo) {
        this.photo = photo;
    }

    public void setRoot(byte[] root) {
        this.root = root;
    }

    public void setAuth(byte[] auth) {
        this.auth = auth;
    }

    public void setSign(byte[] sign) {
        this.sign = sign;
    }

    public void setIntca(byte[] intca) {
        this.intca = intca;
    }

    public void setRrn(byte[] rrn) {
        this.rrn = rrn;
    }

    private void writeElement(String name, String value) throws IOException {
        push.startTag(null, name);
        push.text(value);
        push.endTag(null, name);
    }
}
