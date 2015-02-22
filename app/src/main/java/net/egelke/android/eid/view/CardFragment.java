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
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TextView;

import net.egelke.android.eid.R;
import net.egelke.android.eid.viewmodel.Card;
import net.egelke.android.eid.viewmodel.UpdateListener;
import net.egelke.android.eid.viewmodel.ViewModel;

public class CardFragment extends Fragment implements UpdateListener {

    private ProgressBar loading;

    private TableLayout data;

    private TextView cardNr;

    private TextView issuePlace;

    private TextView chipNr;

    private TextView validFrom;

    private TextView validTo;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_card, container, false);

        loading = (ProgressBar) rootView.findViewById(R.id.loading_card);
        data = (TableLayout)rootView.findViewById(R.id.card_data);

        cardNr = (TextView) rootView.findViewById(R.id.cardnr);
        issuePlace = (TextView) rootView.findViewById(R.id.issuePlace);
        chipNr = (TextView) rootView.findViewById(R.id.chipnr);
        validFrom = (TextView) rootView.findViewById(R.id.validFrom);
        validTo = (TextView) rootView.findViewById(R.id.validTo);

        Card c = (Card) ViewModel.getData(Card.class.getName());
        if (c != null) set(c);
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
        if (Card.class.getName().equals(key)) {
            data.setVisibility(View.GONE);
            loading.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void updateFinished(String key, Object oldValue, Object newValue) {
        if (Card.class.getName().equals(key)) {
            set((Card) newValue);

            loading.setVisibility(View.GONE);
            data.setVisibility(View.VISIBLE);
        }
    }

    private void set(Card c) {
        cardNr.setText(c.getCardNr());
        issuePlace.setText(c.getIssuePlace());
        chipNr.setText(c.getChipNr());
        validFrom.setText(c.getValidFrom());
        validTo.setText(c.getValidTo());
    }
}


