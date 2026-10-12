package com.dogpound.railmap.newrail6;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.Supply;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Rack section supervision panel (batch 6 #16): a lineside pedestal for the rack railway within 128 blocks. ENABLE key,
 * RACK SPEED limit (10-30 km/h), ENTRY speed (5-15 km/h), OVERSPEED action (ALARM / BRAKE), the rack-rail LUBRICATOR
 * (OFF / AUTO - an oil pump and heated nozzles at the entry, 120 / 240 V AC), TAG COG / UNTAG (fits a rack pinion to the
 * locomotive nearest the panel), RENEW PINION (a stopped cog loco by the panel), ACK, LAMP TEST, E-STOP (every train on
 * the rack braked), lamps, three gauges and a four-page screen (OVERVIEW / LOCOS / SECTION / EVENTS).
 */
public class TileRackPanel extends NR6Tile {
    public static final int[] VMAX = {10, 15, 20, 25, 30};
    public static final int[] ENTRY = {5, 8, 10, 15};
    static final String[] PAGES = {"OVERVIEW", "LOCOS", "SECTION", "EVENTS"};

    // this scan's readings (transient)
    private final List<String> locos = new ArrayList<>(), scanning = new ArrayList<>();
    private int onRack, nonCog, scanOn, scanNon;
    private double fastest, steepest, scanFast, scanSteep;
    private boolean overspeed, scanOvs, entryFault, wearWarn, scanWear;
    private int rackBlocks, entryBlocks, countRack, countEntry, sliceX = -999;

    public TileRackPanel() { super(new Supply(Elec.Level.AC120, Elec.Level.AC240)); }

    @Override public String wiredName() { return "Rack panel " + pos.getX() + "," + pos.getZ(); }

    boolean enabled() { return powered() && ctl.get("k_en") == 1; }
    int vmax() { return VMAX[Math.max(0, Math.min(VMAX.length - 1, ctl.get("s_vmax")))]; }
    int entryLimit() { return ENTRY[Math.max(0, Math.min(ENTRY.length - 1, ctl.get("s_entry")))]; }
    boolean braking() { return ctl.get("s_act") == 1; }
    boolean lubricating() { return ctl.get("s_lube") == 1 && live(); }

    // ---- Rack.tick feeds the panel ------------------------------------------------------------------------------------
    void scanStart() { scanning.clear(); scanOn = scanNon = 0; scanFast = scanSteep = 0; scanOvs = scanWear = false; }

    void scanEnd() {
        locos.clear(); locos.addAll(scanning);
        onRack = scanOn; nonCog = scanNon; fastest = scanFast; steepest = scanSteep; overspeed = scanOvs; wearWarn = scanWear;
        if (!scanOvs) clearAlarm("rack:" + pos.toLong() + ":ovs");                       // standing alarms go with their condition
        if (scanNon == 0) clearAlarm("rack:" + pos.toLong() + ":non");
    }

    void report(Locomotive l, String name, boolean cog, double kmh, double grade, float wear) {
        scanOn++;
        scanFast = Math.max(scanFast, kmh);
        scanSteep = Math.max(scanSteep, grade);
        if (cog && wear >= 80) scanWear = true;
        scanning.add(String.format(Locale.ROOT, "%-10s %3.0fKM/H %2.0f%% %s", name.toUpperCase(Locale.ROOT), kmh, grade, cog ? "W" + Math.round(wear) + "%" : "NO COG"));
        if (!enabled()) return;
        String key = "rack:" + pos.toLong();
        if (!cog) {
            scanNon++;
            alarm(key + ":non", AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MINOR, "Non-rack locomotive " + name + " on the rack section by " + wiredName() + " - it has only its adhesion");
        }
        boolean e = ctl.get("m_estop") == 1;
        if (kmh > vmax() + 1) {
            scanOvs = true;
            alarm(key + ":ovs", AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR, String.format(Locale.ROOT, "RACK OVERSPEED %s %.0f km/h (limit %d)%s", name, kmh, vmax(), braking() ? " - brakes applied" : ""));
            if (braking()) e = true;
        }
        if (e) { l.setThrottle(0); l.setTrainBrake(1); }
    }

    void entered(Locomotive l, String name, int verdict, double kmh) {
        if (verdict == 0) { event(name + " ON RACK " + f0(kmh) + " KM/H"); return; }
        String what = verdict == 1 ? String.format(Locale.ROOT, "HARD ENTRY %s %.0f KM/H (ENTRY %d)", name, kmh, entryLimit()) : name + " RAN ON WITHOUT ENTRY SECTION";
        event(what);
        if (!enabled()) return;
        entryFault = true;
        alarm("rack:" + pos.toLong() + ":entry", AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, what + " - pinion struck the tooth tips");
    }

    @Override
    public String gearState() {
        return (powered() ? enabled() ? "supervising" : "key OFF" : "dark - no 12 V") + " · " + onRack + " on rack · lube " + (lubricating() ? "on" : mains.what);
    }

    @Override public int wiredLamp() { return !powered() ? 3 : overspeed || entryFault ? 2 : 1; }

    @Override
    protected void serverTick() {
        if (age % 20 == 7) mainsTick(ctl.get("s_lube") == 1 ? 350 : 40);
        if (age % 20 == 13) countSlice();
        if (age % 5 != 0) return;
        boolean on = powered();
        NR6Data d = NR6Data.get(world);
        put("l_pwr", on);
        put("l_lube", on && lubricating());
        put("l_on", on && onRack > 0);
        put("l_ovs", on && overspeed);
        put("l_non", on && nonCog > 0);
        put("l_entry", on && entryFault);
        put("l_wear", on && wearWarn);
        put("l_estop", on && ctl.get("m_estop") == 1);
        put("g_spd", (int) Math.min(100, fastest / vmax() * 100));
        put("g_grade", (int) Math.min(100, steepest / 25 * 100));
        put("g_lube", lubricating() ? 70 + (int) (world.getTotalWorldTime() / 5 % 7) : 0);
        int tagged = d.cog.size();
        switch (page(PAGES.length)) {
            case 0 -> screen("RACK SUPERVISION " + (enabled() ? "ON" : "OFF"),
                    "ON RACK " + onRack + "  NON-RACK " + nonCog,
                    "LIMIT " + vmax() + " KM/H  ENTRY " + entryLimit(),
                    "OVERSPEED: " + (braking() ? "BRAKE" : "ALARM") + (overspeed ? "  ACTIVE" : ""),
                    String.format(Locale.ROOT, "FASTEST %.0f  STEEPEST %.0f%%", fastest, steepest),
                    "LUBE " + (lubricating() ? "PUMPING" : ctl.get("s_lube") == 1 ? "NO MAINS" : "OFF") + "  COG LOCOS " + tagged);
            case 1 -> {
                String[] o = new String[6];
                o[0] = "LOCOS ON RACK " + locos.size();
                for (int i = 1; i < 6; i++) o[i] = i - 1 < locos.size() ? locos.get(i - 1) : (i == 1 ? "NONE" : "");
                screen(o);
            }
            case 2 -> screen("SECTION WITHIN 48 BLOCKS",
                    "RACK LENGTHS " + rackBlocks + " M",
                    "ENTRY SECTIONS " + entryBlocks,
                    entryBlocks == 0 && rackBlocks > 0 ? "NO ENTRY SECTION LAID" : "ENTRY OK",
                    "LUBRICATOR " + (lubricating() ? "PUMPING" : "STOPPED"),
                    "MAINS " + mains.what);
            default -> screen(logLines("EVENTS", 6));
        }
    }

    /** counts rack lengths around the panel a few rows a second (97 x 17 x 97 blocks a full pass) */
    private void countSlice() {
        if (sliceX == -999 || sliceX > 48) { if (sliceX != -999) { rackBlocks = countRack; entryBlocks = countEntry; } countRack = countEntry = 0; sliceX = -48; }
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int n = 0; n < 6 && sliceX <= 48; n++, sliceX++)
            for (int dz = -48; dz <= 48; dz++)
                for (int dy = -8; dy <= 8; dy++) {
                    q.setPos(pos.getX() + sliceX, pos.getY() + dy, pos.getZ() + dz);
                    if (!world.isBlockLoaded(q)) continue;
                    Block b = world.getBlockState(q).getBlock();
                    if (b == NR6Registry.rackRail) countRack++;
                    else if (b == NR6Registry.rackEntry) { countRack++; countEntry++; }
                }
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        if (id.equals("test")) return lampTest();
        if (id.startsWith("s_") || id.startsWith("k_") || id.startsWith("m_")) return null;
        if (!powered()) return "§cPanel dark - no 12 V";
        NR6Data d = NR6Data.get(world);
        switch (id) {
            case "tag", "untag", "renew" -> {
                Locomotive l = Rack.nearestLoco(world, pos, 16);
                if (l == null) return "§7No locomotive within 16 blocks of the panel";
                String n = Rack.name(l);
                if (id.equals("tag")) {
                    if (!d.cog.add(l.getUUID())) return "§7" + n + " already has a rack pinion";
                    d.changed(); event(n + " FITTED WITH RACK PINION"); return "§a" + n + " fitted with a rack pinion (cog locomotive)";
                }
                if (id.equals("untag")) {
                    if (!d.cog.remove(l.getUUID())) return "§7" + n + " has no rack pinion" + (Rack.cog(world, l) ? " of ours (its pack builds it as a cog loco)" : "");
                    d.changed(); event(n + " PINION REMOVED"); return "§e" + n + " rack pinion removed";
                }
                if (!Rack.cog(world, l)) return "§7" + n + " has no rack pinion to renew";
                if (Math.abs(l.getRealSpeed().metric()) > 1) return "§cStop " + n + " first";
                d.wear.put(l.getUUID(), 0f); d.changed();
                AlarmManager.get(world).clear("rack:pinion:" + l.getUUID());
                event(n + " PINION RENEWED");
                return "§a" + n + ": new rack pinion fitted (wear 0 %)";
            }
            case "ack" -> {
                entryFault = false;
                String k = "rack:" + pos.toLong();
                for (String s : new String[]{":non", ":ovs", ":entry"}) clearAlarm(k + s);
                return "§7Rack alarms acknowledged";
            }
            default -> { return null; }
        }
    }
}
