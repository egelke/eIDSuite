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
package net.egelke.android.eid.diagnostic;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

public class CCIDDescriptor {
    public static enum Voltage {
        _5_0V,
        _3_0V,
        _1_8V
    }

    public static enum Protocol {
        T0,
        T1
    }

    public static enum Mechanical {
        Accept,
        Ejection,
        Capture,
        LockUnlock
    }

    public static enum Feature {
        AutoParamConfigViaATR,
        AutoActivationOnInsert,
        AutoVoltageSelection,
        AutoClockChange,
        AutoDataRateChange,
        AutoParamNego,
        AutoPPS,
        CanStopClock,
        NADAccepted,
        AutoIFSDExchange,
        TPDU,
        ShortAPDU,
        ShortAndExtendedAPDU,
        WakeOnCardAction
    }

    public static enum PINSupport {
        Verification,
        Modification
    }

    public static class ScreenSize {
        int lines;
        int charsPerLine;

        public ScreenSize(int lines, int charsPerLine) {
            this.lines = lines;
            this.charsPerLine = charsPerLine;
        }
    }

    public static List<CCIDDescriptor> Parse(byte[] data) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb = bb.order(ByteOrder.LITTLE_ENDIAN);

        List<CCIDDescriptor> ccids = new LinkedList<CCIDDescriptor>();
        while (bb.hasRemaining()) {
            int len = bb.get();
            int type = bb.get();
            if (type == 0x21) {
                if (len != 0x36)
                    throw new IllegalArgumentException("Invalid Smart Card Device descriptor (wrong length)");

                CCIDDescriptor ccid = new CCIDDescriptor();
                ccid.ccidVersion = bb.getShort();
                ccid.maxSlotIndex = bb.get();

                int vsData = bb.get();
                List<Voltage> vsList = new LinkedList<Voltage>();
                if ((vsData & 0x01) == 0x01) vsList.add(Voltage._5_0V);
                if ((vsData & 0x02) == 0x02) vsList.add(Voltage._3_0V);
                if ((vsData & 0x04) == 0x04) vsList.add(Voltage._1_8V);
                ccid.voltages = vsList.isEmpty() ? EnumSet.noneOf(Voltage.class) : EnumSet.copyOf(vsList);

                int pData = bb.getInt();
                List<Protocol> pList = new LinkedList<Protocol>();
                if ((pData & 0x00000001) == 0x00000001) pList.add(Protocol.T0);
                if ((pData & 0x00000002) == 0x00000002) pList.add(Protocol.T1);
                ccid.protocols = pList.isEmpty() ? EnumSet.noneOf(Protocol.class) : EnumSet.copyOf(pList);

                ccid.defaultClock = bb.getInt();
                ccid.maxClock = bb.getInt();
                ccid.numClockSupported = bb.get();
                ccid.defaultDataRate = bb.getInt();
                ccid.maxDataRate = bb.getInt();
                ccid.numDataRatesSupported = bb.get();
                ccid.maxIFSD = bb.getInt();
                bb.getInt(); //We ignore the synchProtocols (not relevant for USB)

                int mData = bb.getInt();
                List<Mechanical> mList = new LinkedList<Mechanical>();
                if ((mData & 0x00000001) == 0x00000001) mList.add(Mechanical.Accept);
                if ((mData & 0x00000002) == 0x00000002) mList.add(Mechanical.Ejection);
                if ((mData & 0x00000004) == 0x00000004) mList.add(Mechanical.Capture);
                if ((mData & 0x00000008) == 0x00000008) mList.add(Mechanical.LockUnlock);
                ccid.mechanicals = mList.isEmpty() ? EnumSet.noneOf(Mechanical.class) : EnumSet.copyOf(mList);

                int fData = bb.getInt();
                List<Feature> fList = new LinkedList<Feature>();
                if ((fData & 0x00000002) == 0x00000002)
                    fList.add(Feature.AutoParamConfigViaATR);
                if ((fData & 0x00000004) == 0x00000004)
                    fList.add(Feature.AutoActivationOnInsert);
                if ((fData & 0x00000008) == 0x00000008) fList.add(Feature.AutoVoltageSelection);
                if ((fData & 0x00000010) == 0x00000010) fList.add(Feature.AutoClockChange);
                if ((fData & 0x00000020) == 0x00000020) fList.add(Feature.AutoDataRateChange);
                if ((fData & 0x00000040) == 0x00000040) fList.add(Feature.AutoParamNego);
                if ((fData & 0x00000080) == 0x00000080) fList.add(Feature.AutoPPS);
                if ((fData & 0x00000100) == 0x00000100) fList.add(Feature.CanStopClock);
                if ((fData & 0x00000200) == 0x00000200) fList.add(Feature.NADAccepted);
                if ((fData & 0x00000400) == 0x00000400) fList.add(Feature.AutoIFSDExchange);
                if ((fData & 0x00010000) == 0x00010000) fList.add(Feature.TPDU);
                if ((fData & 0x00020000) == 0x00020000) fList.add(Feature.ShortAPDU);
                if ((fData & 0x00040000) == 0x00040000) fList.add(Feature.ShortAndExtendedAPDU);
                if ((fData & 0x00100000) == 0x00100000) fList.add(Feature.WakeOnCardAction);
                ccid.features = fList.isEmpty() ? EnumSet.noneOf(Feature.class) : EnumSet.copyOf(fList);

                ccid.maxCCIDMessageLength = bb.getInt();
                ccid.classGetResponse = bb.get();
                ccid.classEnvelope = bb.get();

                int x = bb.get();
                int y = bb.get();
                ccid.lcdLayout = new ScreenSize(x, y);

                int psData = bb.get();
                List<PINSupport> psList = new LinkedList<PINSupport>();
                if ((psData & 0x01) == 0x01) psList.add(PINSupport.Verification);
                if ((psData & 0x02) == 0x02) psList.add(PINSupport.Modification);
                ccid.pinSupports = psList.isEmpty() ? EnumSet.noneOf(PINSupport.class) : EnumSet.copyOf(psList);

                ccid.maxCCIDBusySlots = bb.get();

                ccids.add(ccid);
            } else {
                bb.position(bb.position() + len - 2);
            }
        }
        return ccids;
    }


    private int ccidVersion;
    private int maxSlotIndex;
    private EnumSet<Voltage> voltages;
    private EnumSet<Protocol> protocols;
    private int defaultClock;
    private int maxClock;
    private int numClockSupported;
    private int defaultDataRate;
    private int maxDataRate;
    private int numDataRatesSupported;
    private int maxIFSD;
    private EnumSet<Mechanical> mechanicals;
    private EnumSet<Feature> features;
    private int maxCCIDMessageLength;
    private int classGetResponse;
    private int classEnvelope;
    private ScreenSize lcdLayout;
    private EnumSet<PINSupport> pinSupports;
    private int maxCCIDBusySlots;


    @Override
    public String toString() {
        return String.format("CCID: Version=%X, NumSlot=%d, Voltages=%s, Protocols=%s, " +
                        "DefaultClock=%dKHz, MaxClock=%dKHz, NumClockSupported=%d, " +
                        "DefaultDataRate=%dbps, MaxDataRate=%dpbs, NumDataRatesSupported=%d, " +
                        "MaxIFSD=%d, Mechanicals=%s, Features=%s, MaxCCIDMessageLength=%d, " +
                        "ClassGetResponse=%X, classEnvelope=%X, lcdLayout=%dx%d, " +
                        "PIN Support=%s, MaxCCIDBusySlots=%d",
                ccidVersion, maxSlotIndex +1, voltages, protocols, defaultClock, maxClock,
                numClockSupported, defaultDataRate, maxDataRate, numDataRatesSupported,
                maxIFSD, mechanicals, features, maxCCIDMessageLength, classGetResponse,
                classEnvelope, lcdLayout.charsPerLine, lcdLayout.lines, pinSupports,
                maxCCIDBusySlots);
    }

    public EnumSet<PINSupport> getPinSupports() {
        return pinSupports;
    }
}
