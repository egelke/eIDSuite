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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TextView;

import net.egelke.android.eid.R;
import net.egelke.android.eid.viewmodel.Person;
import net.egelke.android.eid.viewmodel.UpdateListener;
import net.egelke.android.eid.viewmodel.ViewModel;

/**
 * A simple {@link Fragment} subclass.
 */
public class PersonFragment extends Fragment implements UpdateListener {


    public PersonFragment() {
        // Required empty public constructor

    }

    private ProgressBar loading;

    private TableLayout data;

    private TextView type;

    private TextView name;

    private TextView gNames;

    private TextView birthPlace;

    private TextView birthDate;

    private TextView sex;

    private TextView natNumber;

    private TextView nationality;

    private TextView title;

    private CheckBox status_whiteCane;

    private CheckBox status_yellowCane;

    private CheckBox status_extMinority;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_person, container, false);

        loading = (ProgressBar) rootView.findViewById(R.id.loading_person);
        data = (TableLayout)rootView.findViewById(R.id.person_data);
        type = (TextView) rootView.findViewById(R.id.idType);
        name = (TextView) rootView.findViewById(R.id.name);
        gNames = (TextView) rootView.findViewById(R.id.gNames);
        birthPlace = (TextView) rootView.findViewById(R.id.birthPlace);
        birthDate = (TextView) rootView.findViewById(R.id.birthDate);
        sex = (TextView) rootView.findViewById(R.id.sex);
        natNumber = (TextView) rootView.findViewById(R.id.natNumber);
        nationality = (TextView) rootView.findViewById(R.id.nationality);
        title = (TextView) rootView.findViewById(R.id.title);
        status_whiteCane = (CheckBox) rootView.findViewById(R.id.status_whiteCane);
        status_yellowCane = (CheckBox) rootView.findViewById(R.id.status_yellowCane);
        status_extMinority = (CheckBox) rootView.findViewById(R.id.status_extMinority);

        Person p = (Person) ViewModel.getData(Person.class.getName());
        if (p != null) set(p);
        ViewModel.addListener(this);

        return rootView;
    }


    @Override
    public void onDestroyView() {
        ViewModel.removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void startUpdate(String key) {
        if (Person.class.getName().equals(key)) {
            data.setVisibility(View.GONE);
            loading.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void updateFinished(String key, Object oldValue, Object newValue) {
        if (Person.class.getName().equals(key)) {
            set((Person) newValue);
            loading.setVisibility(View.GONE);
            data.setVisibility(View.VISIBLE);
        }
    }

    private void set(Person p) {
        type.setText(p.getType());
        name.setText(p.getFamilyName());
        gNames.setText(p.getGivenNames());
        birthPlace.setText(p.getBirthPlace());
        birthDate.setText(p.getBirthDate());
        sex.setText(p.getSex());
        natNumber.setText(p.getNationalNumber());
        nationality.setText(p.getNationality());
        title.setText(p.getNobleTitle());
        status_whiteCane.setChecked(p.getWhiteCaneStatus());
        status_yellowCane.setChecked(p.getYellowCaneStatus());
        status_extMinority.setChecked(p.getExtMinorityStatus());
    }
}
