package com.dogpound.railmap.light;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.WireNet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lighting contactor panel (LCP): four contactors K1-K4, one per lighting circuit, each with its HAND / OFF / AUTO
 * selector and a pulled-in lamp. On AUTO a contactor follows the photocell controller on the panel's control wire
 * (circuits 3 and 4 drop out in its night-cut window); with no controller it uses its own photocell. Every lamp landed on
 * the same control wire colour is on the circuit its CIRCUIT selector names - so one panel switches a whole station.
 * The panel counts its lamps (lit / failed) per circuit, and raises a LIGHTING alarm when lamps fail. 12 V coils: with no
 * 12 V the contactors drop out and the lamps fall back to their own photocells.
 */
public class TileLightingContactor extends DepotTile {
    private static final String[] K = {"k1", "k2", "k3", "k4"};
    private final boolean[] closed = new boolean[4];
    private boolean darkLatch, alarmed;
    private Scan scan;
    private int failed;

    @Override public String wiredName() { return "Lighting contactor panel " + Math.floorMod(pos.getX() * 3 + pos.getZ() * 7 + pos.getY(), 1000); }
    @Override public String gearState() {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < 4; k++) b.append("K").append(k + 1).append(closed[k] ? " IN " : " out ");
        return b.append(failed > 0 ? "· " + failed + " LAMP(S) FAILED" : "").toString().trim();
    }
    @Override public int wiredLamp() { return term.burnt || !term.powered() ? 3 : failed > 0 ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) {
        for (int k = 0; k < 4; k++) { m.put("K" + (k + 1), closed[k]); m.put("lamps" + (k + 1), scan == null ? 0 : scan.circuits[k].size()); }
        m.put("failed", failed); m.put("controller", scan != null && scan.controller != null);
    }
    public boolean closed(int k) { return closed[k]; }

    // ---- the wire run ---------------------------------------------------------------------------------------------------
    public static final class Scan {
        @SuppressWarnings("unchecked") public final List<BlockPos>[] circuits = new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
        public BlockPos controller, panel;
        public int total() { return circuits[0].size() + circuits[1].size() + circuits[2].size() + circuits[3].size(); }
    }

    /** every lamp, controller and panel whose control terminal is on `wire` along the runs reaching `me` */
    public static Scan scan(World w, BlockPos me, int wire) {
        Scan s = new Scan();
        if (wire < 0 || wire > 15) return s;
        for (BlockPos p : WireNet.bus(w, me)) {
            if (p.equals(me)) continue;
            TileEntity t = w.getTileEntity(p);
            if (!(t instanceof IWiredGear g) || g.ctlWire() != wire) continue;
            if (t instanceof TileLightController) { if (s.controller == null) s.controller = p; }
            else if (t instanceof TileLightingContactor) { if (s.panel == null) s.panel = p; }
            else if (t instanceof ILamp l && l.lampCircuit() >= 1 && l.lampCircuit() <= 4) s.circuits[l.lampCircuit() - 1].add(p);
        }
        return s;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "scan": scan = scan(world, pos, ctlWire()); show(); return "§7Found " + scan.total() + " lamp(s)" + (scan.controller != null ? " and a photocell controller" : " - no controller, own photocell") + " on the " + WireNet.name(ctlWire()) + " wire";
            case "ack": AlarmManager.get(world).clear(alarmKey()); alarmed = false; return "§7Lighting alarm acknowledged";
            default: return null;
        }
    }

    private String alarmKey() { return "lighting:" + pos.toLong(); }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        if (scan == null || age % 100 == 0) scan = scan(world, pos, ctlWire());
        TileLightController c = scan.controller != null && world.getTileEntity(scan.controller) instanceof TileLightController lc && lc.powered() ? lc : null;
        double lx = Lux.at(world, pos.up());
        if (darkLatch ? lx > 70 : lx < 35) darkLatch = !darkLatch;
        boolean live = powered();
        int failedNow = 0;
        for (int k = 0; k < 4; k++) {
            int mode = Math.floorMod(ctl.get("s_" + K[k]), 3);                  // AUTO / HAND / OFF
            boolean auto = c != null ? c.output() && !(k >= 2 && c.nightCut()) : darkLatch;
            boolean want = live && (mode == 1 || mode == 0 && auto);
            if (want != closed[k]) {
                closed[k] = want;
                world.playSound(null, pos, want ? com.dogpound.railmap.sound.PackSounds.ev("grid.contactor_close") : com.dogpound.railmap.sound.PackSounds.ev("grid.contactor_open"), net.minecraft.util.SoundCategory.BLOCKS, 0.6f, 1.0f);   // the contactor clunk
                if (k == 0) Recorder.event(world, "LIGHTING: " + wiredName() + " circuits " + (want ? "switched ON" : "switched OFF"));
                controlsChanged();
            }
            int lit = 0, bad = 0;
            for (BlockPos p : scan.circuits[k]) {
                if (live) LightingBus.set(world, p, closed[k], pos);              // no 12 V: coils dropped, the lamps fall back to their photocells
                if (world.getTileEntity(p) instanceof ILamp l) { if (l.lampOn()) lit++; if (l.lampFailed()) bad++; }
            }
            failedNow += bad;
            put(K[k], closed[k]);
            text("n" + (k + 1), String.format(Locale.ROOT, "%d/%d", lit, scan.circuits[k].size()));
        }
        failed = failedNow;
        put("ctrl", c != null); put("flt", failed > 0); put("pwr", live); put("cell", darkLatch);
        if (failed > 0 && !alarmed) {
            AlarmManager.get(world).raise(world, alarmKey(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR,
                    "LIGHTING: " + failed + " lamp(s) failed on " + wiredName() + " - repair them (asset register)", pos, true);
            alarmed = true;
        } else if (failed == 0 && alarmed) { AlarmManager.get(world).clear(alarmKey()); alarmed = false; }
        show();
    }

    private void show() {
        text("wire", ctlWire() < 0 ? "NO WIRE" : WireNet.name(ctlWire()).toUpperCase(Locale.ROOT));
        text("tot", scan == null ? "" : scan.total() + " LAMPS");
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        for (int k = 0; k < 4; k++) t.setBoolean("K" + k, closed[k]);
        t.setBoolean("dark", darkLatch); t.setBoolean("alarmed", alarmed); t.setInteger("failed", failed);
    }

    @Override protected void loadExtra(NBTTagCompound t) {
        for (int k = 0; k < 4; k++) closed[k] = t.getBoolean("K" + k);
        darkLatch = t.getBoolean("dark"); alarmed = t.getBoolean("alarmed"); failed = t.getInteger("failed");
    }
}
