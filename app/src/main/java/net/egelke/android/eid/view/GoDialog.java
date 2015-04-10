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
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import net.egelke.android.eid.R;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

public class GoDialog extends DialogFragment {

    public interface Listener {
        void onGo(String url);
    }

    private TextView url;
    private Listener listener;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.dialog_go, null);
        url = (TextView) v.findViewById(R.id.url);

        url.setText(getArguments().getString("url"));

        builder.setView(v).setTitle(R.string.action_go)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.cont, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        navigate();
                    }
                });

        url.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                navigate();
                dismiss();
                return true;
            }
        });

        // Create the AlertDialog object and return it
        return builder.create();
    }

    private void navigate() {
        try {
            String urlValue  = url.getText().toString();
            URL parsed = new URL(urlValue.contains("://") ? urlValue : "http://" + urlValue);
            listener.onGo(parsed.toString());
        } catch (MalformedURLException e) {
            Toast.makeText(GoDialog.this.getActivity(), R.string.toastInvalidUrl, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
    }
}
