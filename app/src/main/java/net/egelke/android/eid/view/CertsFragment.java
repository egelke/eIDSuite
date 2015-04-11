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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import net.egelke.android.eid.EidSuiteApp;
import net.egelke.android.eid.R;
import net.egelke.android.eid.viewmodel.Certificate;
import net.egelke.android.eid.viewmodel.Certificates;
import net.egelke.android.eid.viewmodel.UpdateListener;

public class CertsFragment extends Fragment implements UpdateListener<Certificates> {



    private Certificates c;
    private ArrayAdapter<Certificate> listItems;

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ListView rootView = (ListView) inflater.inflate(R.layout.fragment_certificates, container, false);

        c = ((EidSuiteApp) getActivity().getApplication()).getViewObject(Certificates.class);
        listItems = new ArrayAdapter<Certificate>(this.getActivity(), 0, c.getCertificates()) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view;
                if (convertView == null) {
                    view = inflater.inflate(R.layout.item_certificate, parent, false);
                } else {
                    view = convertView;
                }

                TextView title = (TextView) view.findViewById(R.id.certTitle);
                TextView subject = (TextView) view.findViewById(R.id.certSubject);
                TextView validity = (TextView) view.findViewById(R.id.certValidity);

                Certificate item = getItem(position);
                title.setText(item.getTitle());
                subject.setText(item.getSubject());
                validity.setText(String.format(getString(R.string.validFromTo), item.getFrom(), item.getTo()));

                return view;
            }
        };
        rootView.setAdapter(listItems);
        c.addListener(this);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        c.removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onUpdate(Certificates value) {
        if (listItems != null) listItems.notifyDataSetChanged();
    }
}
