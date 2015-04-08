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
package net.egelke.android.eid.view;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TextView;

import net.egelke.android.eid.EidSuiteApp;
import net.egelke.android.eid.R;
import net.egelke.android.eid.viewmodel.Address;
import net.egelke.android.eid.viewmodel.UpdateListener;

public class AddressFragment extends Fragment implements UpdateListener<Address> {

    private static final String TAG = "net.egelke.android.eid";

    private Address a;

    private ProgressBar loading;

    private TableLayout data;

    private TextView street;

    private TextView zip;

    private TextView municipality;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_address, container, false);

        loading = (ProgressBar) rootView.findViewById(R.id.loading_address);
        data = (TableLayout)rootView.findViewById(R.id.address_data);

        street = (TextView) rootView.findViewById(R.id.street);
        zip = (TextView) rootView.findViewById(R.id.zip);
        municipality = (TextView) rootView.findViewById(R.id.municipality);

        a = ((EidSuiteApp) getActivity().getApplication()).getViewObject(Address.class);
        onUpdate(a);
        a.addListener(this);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        a.removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onUpdate(Address a) {
        if (data == null || loading == null)
            return;


        if (this.a != a)
            Log.w(TAG, "Updated object isn't the the instance object");

        if (a.isUpdating()) {
            data.setVisibility(View.GONE);
            loading.setVisibility(View.VISIBLE);
        } else {
            street.setText(a.getStreet());
            zip.setText(a.getZip());
            municipality.setText(a.getMunicipality());

            loading.setVisibility(View.GONE);
            data.setVisibility(View.VISIBLE);
        }
    }
}
