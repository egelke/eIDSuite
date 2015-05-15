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
package net.egelke.android.eid.viewmodel;


import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.security.PdfPKCS7;

import net.egelke.android.eid.R;

import org.spongycastle.asn1.x500.RDN;
import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x500.style.BCStyle;
import org.spongycastle.asn1.x500.style.IETFUtils;
import org.spongycastle.cert.jcajce.JcaX500NameUtil;
import org.spongycastle.jce.PrincipalUtil;
import org.spongycastle.jce.X509Principal;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

public class PdfFile extends ViewObject {

    private static final String TAG = "net.egelke.android.eid";

    public class Signature {
        boolean signed;
        String name;
        List<String> pages;
        String signedBy;
        String label;

        Signature(String name, List<String> pages) {
            this(name, pages, false, null);
        }

        Signature(String name, List<String> pages, boolean signed, String signedBy) {
            this.name = name;
            this.pages = pages;
            this.signed = signed;
            this.signedBy = signedBy;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            if (label == null) {
                    label = String.format(ctx.getString(R.string.signField), name,
                            pages != null ? TextUtils.join(", ", pages) : "?");
            }
            return label;
        }
    }

    private class Process extends AsyncTask<Uri, Signature, Void> {

        @Override
        protected void onPreExecute() {
            signatures.clear();
        }

        @Override
        protected Void doInBackground(Uri... params) {
            try {
                PdfReader reader = new PdfReader(ctx.getContentResolver().openInputStream(params[0]));
                try {
                    AcroFields acroFields = reader.getAcroFields();
                    List<String> names = acroFields.getBlankSignatureNames();
                    for(String name : names) {
                        List<String> pages = new LinkedList<String>();
                        for (AcroFields.FieldPosition fp : acroFields.getFieldPositions(name)) {
                            pages.add(Integer.toString(fp.page));
                        }
                        publishProgress(new Signature(name, pages));
                    }

                    /*
                    names = acroFields.getSignatureNames();
                    for(String name : names) {
                        List<String> pages = new LinkedList<String>();
                        for(AcroFields.FieldPosition fp : acroFields.getFieldPositions(name)) {
                            pages.add(Integer.toString(fp.page));
                        }

                        String signer = "";
                        PdfPKCS7 pkcs7 = acroFields.verifySignature(name);
                        X509Certificate cert = pkcs7.getSigningCertificate();
                        if (cert != null) {
                            X500Name subject = JcaX500NameUtil.getSubject(cert);
                            RDN[] subjectCNs = subject.getRDNs(BCStyle.CN);
                            if (subjectCNs.length > 0)
                                signer = IETFUtils.valueToString(subjectCNs[0].getFirst().getValue());
                        }

                        publishProgress(new Signature(name, pages, true, signer));
                    }
                    */
                    return null;
                } finally {
                    reader.close();
                }
            } catch (IOException ioe) {
                Log.e(TAG, "Failed to parse pdf");
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Signature... values) {
            signatures.add(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            endUpdate();
        }
    }

    private Uri file;
    private List<Signature> signatures;

    public PdfFile(Context ctx) {
        super(ctx);
        signatures = new LinkedList<Signature>();
    }

    public void setFile(Uri file) {
        this.file = file;
        if (file != null) {
            (new Process()).execute(file);
        } else {
            signatures.clear();
            endUpdate();
        }
    }

    public Uri getFile() {
        return this.file;
    }

    public boolean hasFile() {
        return file != null;
    }

    public String getTitle() {
        if (file == null) {
            return ctx.getString(R.string.selectFile);
        } else {
            if ("content".equals(file.getScheme())) {
                Cursor cursor = ctx.getContentResolver().query(file, null, null, null, null);
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    }
                    return "";
                } finally {
                    cursor.close();
                }
            } else {
                return file.getPath();
            }
        }
    }

    public List<Signature> getSignatures() {
        return signatures;
    }

    public boolean hasSignatures() {
        return !signatures.isEmpty();
    }



}
