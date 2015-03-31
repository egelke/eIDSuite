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
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.x500.X500Principal;

public class Certificate {
    private static final String TAG = "net.egelke.android.eid";
    private static final Pattern snExtract = Pattern.compile(".*CN=([^,]*).*");

    private X509Certificate cert;
    private Context ctx;

    public Certificate() {

    }

    public Certificate(Context ctx, X509Certificate cert) {
        this.ctx = ctx;
        this.cert = cert;
    }

    @Override
    public String toString() {
        String dn = cert.getSubjectX500Principal().getName();
        Matcher matcher = snExtract.matcher(dn);
        String cn;
        if (matcher.matches())
            cn = matcher.group(1);
        else
            cn = dn;
        return cn;
    }

    public String getSubject() {
        if (cert != null) {
            String subjectValue = cert.getSubjectX500Principal().getName(X500Principal.RFC2253);
            subjectValue = subjectValue.replace(",", "\r\n"); // write line by line
            StringBuffer subjectWriter = new StringBuffer();
            try {
                // We need to convert some RFC2253 stuff
                String subjectLine;
                BufferedReader subjectReader = new BufferedReader(new StringReader(subjectValue));
                while ((subjectLine = subjectReader.readLine()) != null) {
                    String[] lineParts = subjectLine.split("=");
                    if ("2.5.4.5".equals(lineParts[0])) {
                        lineParts[0] = "SERIALNUMBER";
                    } else if ("2.5.4.4".equals(lineParts[0])) {
                        lineParts[0] = "SURNAME";
                    } else if ("2.5.4.42".equals(lineParts[0])) {
                        lineParts[0] = "GIVENNAME";
                    }
                    subjectWriter.append(lineParts[0]);
                    subjectWriter.append('=');

                    if (lineParts[1].startsWith("#13")) { // we should decode...
                        int i = 5;
                        while ((i + 2) <= lineParts[1].length()) {
                            subjectWriter.append(new String(new byte[]{(byte) Integer
                                            .parseInt(lineParts[1].substring(i,i + 2), 16)}));
                            i += 2;
                        }
                    } else {
                        subjectWriter.append(lineParts[1]);
                    }
                    subjectWriter.append("\r\n");
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to convert  the subject name", e);
            }
            return subjectWriter.toString();
        } else {
            return "";
        }
    }

    public String getFrom() {
        if (cert != null) {
            java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
            return dateFormat.format(cert.getNotBefore());
        } else {
            return "";
        }
    }

    public String getTo() {
        if (cert != null) {
            java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
            return dateFormat.format(cert.getNotAfter());
        } else {
            return "";
        }
    }

    public boolean getDigitalSignatureUsage() {
        return cert != null && cert.getKeyUsage()[0];
    }

    public boolean getNonRepudiationUsage() {
        return cert != null && cert.getKeyUsage()[1];
    }

    public boolean getKeyEnciphermentUsage() {
        return cert != null && cert.getKeyUsage()[2];
    }

    public boolean getDataEnciphermentUsage() {
        return cert != null && cert.getKeyUsage()[3];
    }

    public boolean getKeyAgreementUsage() {
        return cert != null && cert.getKeyUsage()[4];
    }

    public boolean getKeyCertSignUsage() {
        return cert != null && cert.getKeyUsage()[5];
    }

    public boolean getCRLSignUsage() {
        return cert != null && cert.getKeyUsage()[6];
    }

    public boolean getEncipherOnlyUsage() {
        return cert != null && cert.getKeyUsage()[7];
    }

    public boolean getDecipherOnlyUsage() {
        return cert != null && cert.getKeyUsage()[8];
    }
}
