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
package net.egelke.android.eid.viewmodel;

import android.content.Context;

import org.spongycastle.asn1.x500.RDN;
import org.spongycastle.asn1.x500.style.BCStyle;
import org.spongycastle.asn1.x500.style.IETFUtils;

public class Certificate extends ViewObject {
    private static final String TAG = "net.egelke.android.eid";

    private org.spongycastle.asn1.x509.Certificate cert;

    public Certificate(byte[] cert, Context ctx) {
        super(ctx);
        this.cert = org.spongycastle.asn1.x509.Certificate.getInstance(cert);
    }

    public String getTitle() {
        if (cert != null) {
            RDN[] subjectCNs = cert.getSubject().getRDNs(BCStyle.CN);

            if (subjectCNs.length > 0)
                return IETFUtils.valueToString(subjectCNs[0].getFirst().getValue());
            else
                return "?";
        } else {
            return "";
        }
    }

    public String getSubject() {
        if (cert != null) {
            return cert.getSubject().toString();
        } else {
            return "";
        }
    }

    public String getFrom() {
        if (cert != null) {
            java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
            return dateFormat.format(cert.getStartDate().getDate());
        } else {
            return "";
        }
    }

    public String getTo() {
        if (cert != null) {
            java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
            return dateFormat.format(cert.getEndDate().getDate());
        } else {
            return "";
        }
    }

}
