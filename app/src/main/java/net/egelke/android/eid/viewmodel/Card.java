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

import net.egelke.android.eid.model.Identity;

public class Card extends ViewObject {

    private Identity id;

    public Card(Context ctx) {
        super(ctx);
    }

    public void setIdentity(Identity id) {
        this.id = id;
    }

    public String getCardNr() {
        return id != null ? id.cardNumber : "";
    }

    public String getIssuePlace() {
        return id != null ? id.cardDeliveryMunicipality : "";
    }

    public String getChipNr() {
        return id != null ? id.chipNumber : "";
    }

    public String getValidFrom() {
        if (id != null && id.cardValidity != null) {
            java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
            return dateFormat.format(id.cardValidity.begin.getTime());
        } else {
            return "";
        }
    }

    public String getValidTo() {
        if (id != null && id.cardValidity != null) {
            java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
            return dateFormat.format(id.cardValidity.end.getTime());
        } else {
            return "";
        }
    }

}
