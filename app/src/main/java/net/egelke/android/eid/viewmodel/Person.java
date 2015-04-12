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

import net.egelke.android.eid.R;
import net.egelke.android.eid.model.Identity;
import net.egelke.android.eid.model.SpecialStatus;

public class Person extends ViewObject {

    private Identity id;

    public Person(Context ctx) {
        super(ctx);
    }

    public void setIdentity(Identity id) {
        this.id = id;
    }

    public Identity getIdentity() {
        return this.id;
    }

    public String getType() {
        if (id != null && id.documentType != null) {
            switch (id.documentType) {
                case BELGIAN_CITIZEN:
                    return ctx.getString(R.string.cardtype_citizen);
                case KIDS_CARD:
                    return ctx.getString(R.string.cardtype_kids);
                case FOREIGNER_A:
                    return ctx.getString(R.string.cardtype_a);
                case FOREIGNER_B:
                    return ctx.getString(R.string.cardtype_b);
                case FOREIGNER_C:
                    return ctx.getString(R.string.cardtype_c);
                case FOREIGNER_D:
                    return ctx.getString(R.string.cardtype_d);
                case FOREIGNER_E:
                    return ctx.getString(R.string.cardtype_e);
                case FOREIGNER_E_PLUS:
                    return ctx.getString(R.string.cardtype_eplus);
                case FOREIGNER_F:
                    return ctx.getString(R.string.cardtype_f);
                case FOREIGNER_F_PLUS:
                    return ctx.getString(R.string.cardtype_fplus);
                default:
                    return id.documentType.name().replace('_', ' ');
            }
        } else {
            return "";
        }
    }

    public String getFamilyName() {
        return id != null && id.familyName != null ? id.familyName : "";
    }

    public String getGivenNames() {
        if (id != null) {
            return String.format("%s %s",
                    id.firstName == null ? "" : id.firstName,
                    id.middleNames == null ? "" : id.middleNames);
        } else {
            return "";
        }
    }

    public String getBirthPlace() {
        return id != null && id.placeOfBirth != null ? id.placeOfBirth : "";
    }

    public String getBirthDate() {
        if (id != null && id.dateOfBirth != null) {
            java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
            return dateFormat.format(id.dateOfBirth.getTime());
        } else {
            return "";
        }
    }

    public String getSex() {
        if (id != null && id.gender != null) {
            switch (id.gender) {
                case MALE:
                    return ctx.getString(R.string.sex_male);
                case FEMALE:
                    return ctx.getString(R.string.sex_female);
                default:
                    return "";
            }
        } else {
            return "";
        }
    }

    public String getNationalNumber() {
        return id != null && id.nationalNumber != null ? id.nationalNumber : "";
    }

    public String getNationality() {
        return id != null && id.nationality != null ? id.nationality : "";
    }

    public String getNobleTitle() {
        return id != null && id.nobleTitle != null ? id.nobleTitle : "";
    }

    public boolean getWhiteCaneStatus() {
        return id != null && id.specialStatus != null && id.specialStatus.contains(SpecialStatus.WHITE_CANE);
    }

    public boolean getYellowCaneStatus() {
        return id != null && id.specialStatus != null && id.specialStatus.contains(SpecialStatus.YELLOW_CANE);
    }

    public boolean getExtMinorityStatus() {
        return id != null && id.specialStatus != null && id.specialStatus.contains(SpecialStatus.EXTENDED_MINORITY);
    }
}
