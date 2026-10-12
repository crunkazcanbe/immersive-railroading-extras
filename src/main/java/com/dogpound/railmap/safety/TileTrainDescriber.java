package com.dogpound.railmap.safety;

import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Train Describer panel (her ChatGPT list #25): eight berths, each one a signal (Signal Wrench: pick the signal, click
 * the berth). A berth window shows the train standing at or approaching that signal - its headcode, or (selector) its
 * radio id or speed - stepping from berth to berth as the train moves, exactly like a signalbox describer. The berth
 * lamp lights while a train is in it. The headcodes come from the tracking records (one per train). With no 12 V the
 * berths go blank, or (fail-safe DESCRIBER_POWER = battery) keep the last headcodes.
 */
public class TileTrainDescriber extends SafetyTile {
    public static final int N = 8;
    private final long[] berth = new long[N];

    @Override public String wiredName() { return "Train describer"; }

    @Override public String gearState() {
        int linked = 0, occ = 0;
        for (int i = 0; i < N; i++) { if (berth[i] != 0) linked++; if (ctl.get("o" + (i + 1)) == 1) occ++; }
        return !powered() ? "no 12 V" : linked + " berth(s), " + occ + " occupied";
    }

    @Override public void gearInfo(Map<String, Object> m) { for (int i = 0; i < N; i++) if (berth[i] != 0) m.put("berth" + (i + 1), ctl.text("b" + (i + 1))); }

    @Override protected boolean keepTextUnpowered() { return !FailSafe.safe(world, FailSafe.DESCRIBER_POWER); }

    @Override
    protected void serverTick() {
        if (age % 10 != 5) return;
        put("pwr", powered());
        if (!powered()) return;                                                 // frozen (battery) or blanked by ptext
        SafetyData d = SafetyData.get(world);
        int show = ctl.get("s_show");
        long now = world.getTotalWorldTime();
        boolean stale = false;
        for (int i = 0; i < N; i++) {
            int n = i + 1;
            if (berth[i] == 0) { text("b" + n, ""); text("n" + n, "----"); put("o" + n, false); continue; }
            SafetyData.TrainRec first = null;
            int more = 0;
            for (SafetyData.TrainRec r : d.trains.values()) {
                if (r.gone || r.berthPos != berth[i]) continue;
                if (now - r.lastSeen > 100) stale = true;
                if (first == null) first = r; else more++;
            }
            String s = first == null ? "" : show == 1 ? "R" + first.radioId : show == 2 ? Math.round(Math.abs(first.speed)) + "KMH" : first.headcode;
            text("b" + n, s + (more > 0 ? "+" + more : ""));
            put("o" + n, first != null);
            BlockPos p = BlockPos.fromLong(berth[i]);
            String nm = world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileSignalMast m ? SignalSighting.name(m) : p.getX() + "," + p.getZ();
            text("n" + n, nm.length() > 9 ? nm.substring(0, 9) : nm);
        }
        put("stale", stale);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        if (id.equals("s_show")) { ctl.set("s_show", (ctl.get("s_show") + 1) % 3); return "§eShowing " + new String[]{"headcodes", "radio ids", "speeds"}[ctl.get("s_show")]; }
        if (id.startsWith("b") || id.startsWith("n")) {
            int i = Integer.parseInt(id.substring(1)) - 1;
            if (i < 0 || i >= N) return "";
            if (berth[i] == 0) return "§7Berth " + (i + 1) + " is free - Signal Wrench: pick a signal, then click this berth";
            for (SafetyData.TrainRec r : SafetyData.get(world).trains.values())
                if (!r.gone && r.berthPos == berth[i]) return "§e" + r.headcode + " RID " + r.radioId + " · " + r.names + " · " + r.cars + " veh / " + r.axles + " axles · " + Math.round(Math.abs(r.speed)) + " km/h" + (r.dest.isEmpty() ? "" : " · to " + r.dest);
            return "§7Berth " + (i + 1) + " empty";
        }
        return "";
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (!(control.startsWith("b") || control.startsWith("n"))) return "§7Click a berth window to set its signal";
        int i = Integer.parseInt(control.substring(1)) - 1;
        if (i < 0 || i >= N) return null;
        if (!(world.getTileEntity(pick) instanceof TileSignalMast)) return "§7A berth is a signal - pick a signal with the wrench";
        if (berth[i] == pick.toLong()) { berth[i] = 0; controlsChanged(); return "§7Berth " + (i + 1) + " cleared"; }
        berth[i] = pick.toLong();
        controlsChanged();
        return "§aBerth " + (i + 1) + " = the signal at " + pick.getX() + "," + pick.getZ() + " (trains on its approach show here)";
    }

    @Override protected void save(NBTTagCompound t) { t.setTag("berths", TileAxleEvaluator.longs(berth)); }
    @Override protected void load(NBTTagCompound t) { long[] b = TileAxleEvaluator.unlongs(t.getTagList("berths", 4)); for (int i = 0; i < N; i++) berth[i] = i < b.length ? b[i] : 0; }
}
