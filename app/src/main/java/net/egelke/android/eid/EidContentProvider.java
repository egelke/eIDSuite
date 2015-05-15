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
package net.egelke.android.eid;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;

import net.egelke.android.eid.service.EidService;


public class EidContentProvider extends ContentProvider {

    private static final String TAG = "net.egelke.android.eid";

    private static final int IDENTITY = 1;
    private static final int ADDRESS = 2;
    private static final int PHOTO = 3;
    private static final int CERTIFICATES = 4;
    private static final int CERTIFICATE = 5;
    private static final int DUMMY = 10;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI("net.egelke.android.eid.provider", "identity", IDENTITY);
        uriMatcher.addURI("net.egelke.android.eid.provider", "address", ADDRESS);
        uriMatcher.addURI("net.egelke.android.eid.provider", "photo", PHOTO);
        uriMatcher.addURI("net.egelke.android.eid.provider", "certificates", CERTIFICATES);
        uriMatcher.addURI("net.egelke.android.eid.provider", "certificates/#", CERTIFICATE);
        uriMatcher.addURI("net.egelke.android.eid.dummy", "identity", DUMMY+IDENTITY);
        uriMatcher.addURI("net.egelke.android.eid.dummy", "address", DUMMY+ADDRESS);
        uriMatcher.addURI("net.egelke.android.eid.dummy", "photo", DUMMY+PHOTO);
        uriMatcher.addURI("net.egelke.android.eid.dummy", "certificates", DUMMY+CERTIFICATES);
        uriMatcher.addURI("net.egelke.android.eid.dummy", "certificates/#", DUMMY+CERTIFICATE);
    }

    private Messenger eidService = null;

    @Override
    public boolean onCreate() {
        Context ctx = getContext();
        ctx.bindService(new Intent(ctx, EidService.class), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                eidService = new Messenger(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                eidService = null;
            }
        }, Context.BIND_AUTO_CREATE);

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (eidService == null) return null;

        switch (uriMatcher.match(uri)) {
            case IDENTITY:
                Message msg = Message.obtain(null, EidService.READ_DATA);
                msg.getData().putBoolean("identity", true);


        }

        return null;
    }

    @Override
    public String getType(Uri uri) {
        int match = uriMatcher.match(uri);
        switch (match)
        {
            case IDENTITY:
            case DUMMY+IDENTITY:
                return "vnd.android.cursor.item/vnd.net.egelke.android.eid.provider.identity";
            case ADDRESS:
            case DUMMY+ADDRESS:
                return "vnd.android.cursor.item/vnd.net.egelke.android.eid.provider.address";
            case PHOTO:
            case DUMMY+PHOTO:
                return "vnd.android.cursor.item/vnd.net.egelke.android.eid.provider.photo";
            case CERTIFICATES:
            case DUMMY+CERTIFICATES:
                return "vnd.android.cursor.dir/vnd.net.egelke.android.eid.provider.certificate";
            case CERTIFICATE:
            case DUMMY+CERTIFICATE:
                return "vnd.android.cursor.item/vnd.net.egelke.android.eid.provider.certificate";
            default:
                return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new SecurityException("You are not allowed to update an eID card");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new SecurityException("You are not allowed to update an eID card");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new SecurityException("You are not allowed to update an eID card");
    }
}
