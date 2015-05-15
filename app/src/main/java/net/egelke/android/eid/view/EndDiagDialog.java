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
package net.egelke.android.eid.view;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;

import net.egelke.android.eid.EidSuiteApp;
import net.egelke.android.eid.R;

public class EndDiagDialog extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.diagEndMsg)
                .setTitle(R.string.diagTitle)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.send, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Intent i = new Intent(Intent.ACTION_SENDTO);
                        i.setType("text/plain");
                        i.setData(Uri.parse("mailto:info@egelke.net"));
                        i.putExtra(Intent.EXTRA_SUBJECT, "eID Suite: Diagnostics");
                        i.putExtra(Intent.EXTRA_TEXT, getArguments().getString("Result"));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(i);
                        } catch (ActivityNotFoundException ex) {
                            Toast.makeText(getActivity(), R.string.toastNoMailClient, Toast.LENGTH_SHORT).show();
                            Tracker tracker = ((EidSuiteApp) EndDiagDialog.this.getActivity().getApplication()).getTracker();
                            tracker.send(new HitBuilders.ExceptionBuilder()
                                    .setDescription(new StandardExceptionParser(EndDiagDialog.this.getActivity(), null).getDescription(Thread.currentThread().getName(), ex))
                                    .setFatal(false).build());
                        }
                    }
                });
        return builder.create();
    }

}
