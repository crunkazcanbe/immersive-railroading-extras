package com.dogpound.railmap.infra;

import com.dogpound.railmap.ohle.Ohle;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OHLE Fault Locator panel (#39): the traction substation's distance-protection / fault-recorder cubicle. Its screens
 * show one fault record at a time (PREV / NEXT): the feeder that tripped, the loop impedance it measured (|Z|, R, X),
 * the fault current, the distance to the fault and its uncertainty by the chosen METHOD (AUTO = double-ended when both
 * ends recorded it, SINGLE = reactance method, DOUBLE), where along the wire that is, and the inspection crew's state.
 * DISPATCH sends a crew to the estimate; ACK silences the new-fault lamp. A 12 V substation-battery instrument.
 */
public class TileFaultLocator extends InfraTile {
    static final String[] METHODS = {"AUTO", "SINGLE", "DOUBLE"};
    private int sel;

    public TileFaultLocator() { super(null); }

    @Override public String wiredName() { return "OHLE fault locator " + pos.getX() + "," + pos.getZ(); }

    private InfraData.FaultRec cur() {
        List<InfraData.FaultRec> l = InfraData.get(world).faults;
        if (l.isEmpty()) return null;
        sel = Math.max(0, Math.min(l.size() - 1, sel));
        return l.get(sel);
    }

    @Override
    public String gearState() {
        InfraData.FaultRec r = cur();
        return term.status().replaceAll("§.", "") + (r == null ? " · no fault records" : String.format(Locale.ROOT, " · #%d %s %.0f m · crew %s", r.id, r.kind, FaultLocator.best(r, ctl.get("s_meth"))[0], FaultLocator.CREW[r.crew]));
    }

    @Override
    public int wiredLamp() {
        if (!powered()) return 3;
        for (InfraData.FaultRec r : InfraData.get(world).faults) if (!r.acked) return 2;
        return 1;
    }

    @Override
    public void gearInfo(Map<String, Object> m) {
        InfraData.FaultRec r = cur();
        m.put("records", InfraData.get(world).faults.size());
        if (r != null) { m.put("id", r.id); m.put("distanceM", Math.round(r.estD)); m.put("crew", FaultLocator.CREW[r.crew]); }
    }

    @Override
    protected void serverTick() {
        if (age % 10 != 3) return;
        List<InfraData.FaultRec> all = InfraData.get(world).faults;
        InfraData.FaultRec r = cur();
        int meth = ctl.get("s_meth");
        boolean unacked = false;
        for (InfraData.FaultRec x : all) unacked |= !x.acked;
        put("trip", unacked);
        put("pwr", powered());
        if (r == null) {
            text("rec", "NO RECORDS"); text("dist", "-"); text("z", ""); text("rx", ""); text("ika", ""); text("kind", "LINE HEALTHY"); text("loc", ""); text("crew", "");
            put("dbl", false); put("disp", false); put("site", false); put("iso", false); put("done", false); put("g_dist", 0);
            return;
        }
        double[] b = FaultLocator.best(r, meth);
        BlockPos e = BlockPos.fromLong(FaultLocator.bestPos(r, meth));
        text("rec", String.format(Locale.ROOT, "REC %d/%d #%d", sel + 1, all.size(), r.id));
        text("dist", b[0] < 0 ? "NO 2ND END" : String.format(Locale.ROOT, "%.0f M +-%.0f", b[0], b[1]));
        text("z", String.format(Locale.ROOT, "Z %.2f OHM", Math.hypot(r.r, r.x)));
        text("rx", String.format(Locale.ROOT, "R %.2f X %.3f", r.r, r.x));
        text("ika", String.format(Locale.ROOT, "I %.1f KA", r.ika));
        text("kind", r.kind + " F " + Ohle.xz(BlockPos.fromLong(r.feeder)));
        text("loc", "AT " + e.getX() + "," + e.getZ());
        text("crew", FaultLocator.CREW[r.crew]);
        put("dbl", r.estD2 >= 0);
        put("disp", r.crew == 1 || r.crew == 2);
        put("site", r.crew == 3 || r.crew == 5);
        put("iso", r.crew == 4);
        put("done", r.crew == 6);
        put("g_dist", (int) Math.round(Math.min(100, r.total <= 0 ? 0 : (b[0] < 0 ? 0 : b[0]) / r.total * 100)));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        List<InfraData.FaultRec> all = InfraData.get(world).faults;
        switch (id) {
            case "test": return lampTest();
            case "s_meth": return null;
            case "prev": sel = Math.max(0, sel - 1); controlsChanged(); return "§7Record " + (sel + 1) + "/" + all.size();
            case "next": sel = Math.min(Math.max(0, all.size() - 1), sel + 1); controlsChanged(); return "§7Record " + (sel + 1) + "/" + all.size();
            case "ack": {
                int n = 0;
                for (InfraData.FaultRec r : all) if (!r.acked) { r.acked = true; n++; com.dogpound.railmap.alarm.AlarmManager.get(world).clear("floc:" + r.id); }
                InfraData.get(world).markDirty();
                return n == 0 ? "§7Nothing to acknowledge" : "§a" + n + " fault record(s) acknowledged";
            }
            case "dispatch": {
                if (!powered()) return "§cThe locator is dead - no 12 V";
                InfraData.FaultRec r = cur();
                if (r == null) return "§7No fault to send a crew to";
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PHONE_RING, SoundCategory.BLOCKS, 0.6f, 1.2f);
                String s = FaultLocator.dispatch(world, r.id, pos, ctl.get("s_meth"), who(who));
                com.dogpound.railmap.api.QuestEvents.fire(world, who, pos, "ohle_crew_dispatched", "fault", r.id, "kind", r.kind);
                return s;
            }
            default: return "§7" + gearState();
        }
    }

    @Override protected void store(NBTTagCompound t) { t.setInteger("sel", sel); }
    @Override protected void restore(NBTTagCompound t) { sel = t.getInteger("sel"); }
}
