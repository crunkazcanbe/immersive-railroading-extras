package com.dogpound.railmap.grid;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * What one instrument shows: a needle value against its full scale, a little screen's three lines, a status lamp.
 * Read from a point of the grid (a machine or a cable). Shared by the wall instruments, the Gauge Panel's four holes
 * and the radio, so a reading looks the same wherever it is shown.
 */
public final class Reading {
    public double value, full = 1;
    public String unit = "", status = "", line1 = "", line2 = "", line3 = "";
    public int lamp;                      // 0 off, 1 live (green), 2 dead (red), 3 tripped (amber, flashing)
    /** switchboard extras (wall instruments): alarm setpoints and peak hold as fractions of full scale (0 / -1 = off) */
    public double hiFrac, loFrac, peakFrac = -1;
    public boolean alarm;
    /** Control Button: the cap colour of the panel control it copies */
    public int col = 0x606060;
    /** its 12 V DC supply: 0 none (dark), 1 powered, 2 burnt out by the wrong voltage */
    public int pw = 1;
    /** needle speed: 1 fast, 0.5 normal, 0.2 slow */
    public double damp = 1;
    /** client: where the needle is drawn now (eases toward value) */
    public float needle;
    public long needleAt;

    public void notLinked(String l2, String l3) {
        value = 0; full = 1; unit = ""; status = "NOT LINKED"; line1 = "NOT LINKED"; line2 = l2; line3 = l3; lamp = 0;
    }

    /** no 12 V (or burnt out): the needle drops to its stop, screens and lamps go dark */
    public void unpowered(int state, String why) {
        value = 0; full = Math.max(1e-9, full); lamp = 0; alarm = false; peakFrac = -1;
        line1 = line2 = line3 = ""; status = state == 2 ? "BURNT OUT" : "NO 12 V";
        if (why != null) line2 = why;
        pw = state;
    }

    /** read a point of the grid in real units, for this kind of instrument */
    public void read(World world, BlockPos target, BlockInstrument.Inst k) {
        if (target == null) { notLinked("Signal Wrench:", "machine -> me"); return; }
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(target);
        if (te instanceof com.dogpound.railmap.wire.IWiredMachine m && !(te instanceof TileGrid)) { m.wiredRead(this, k); return; }   // a Pride Industry plant...
        GridData d = GridData.get(world);
        GridData.Node n = d.node(target);
        GridData.Net net = d.netOf(target);
        if (n == null || net == null) { value = 0; status = "NO SIGNAL"; line1 = "NO SIGNAL"; line2 = line3 = ""; lamp = 2; return; }
        boolean live = net.problem.isEmpty();
        Elec.Level lv = net.level;
        double nominal = lv != null ? lv.volts : (net.traction ? 25_000 : 11_000);
        double pu = lv != null ? (n.pu > 0 ? n.pu : net.puSource) : net.volt;
        double volts = live ? nominal * pu : 0;
        double rating = 0;
        for (long c : net.ins) { GridData.Node cv = d.nodes().get(c); if (cv != null) rating += cv.kva * 1000.0; }
        if (rating <= 0) rating = (double) GridData.RATING * Elec.W_PER_FE_T * Math.max(1, net.transformers);
        double amps = n.kind == GridKind.CABLE || n.sided() ? n.amps : net.amps;
        double ampsFull = n.kind == GridKind.CABLE ? n.cable().ampacity() : (lv != null ? lv.amps(rating) : 500);
        lamp = n.kind == GridKind.BREAKER && n.breaker == 2 ? 3 : live ? 1 : 2;
        status = live ? "LIVE" : net.problem;
        switch (k) {
            case VOLT: value = volts; full = nominal * 1.25; unit = nominal >= 1000 ? "kV" : "V"; break;
            case AMP: value = amps; full = Math.max(1, ampsFull * 1.25); unit = "A"; break;
            case WATT: value = net.pW; full = Math.max(1, rating * 1.25); unit = rating >= 1e6 ? "MW" : "kW"; break;
            case VAR: value = net.qVar; full = Math.max(1, rating * 0.75); unit = rating >= 1e6 ? "Mvar" : "kvar"; break;
            case PF: value = live ? net.pf : 0; full = 1; unit = "PF"; break;
            case FREQ: {
                double hz = lv == null ? 50 : lv.hz;
                value = live && hz > 0 ? (net.hzPu != 1 ? hz * net.hzPu : hz * (1 - 0.002 * Math.min(1.5, net.pW / Math.max(1, rating)))) : 0;
                full = hz <= 0 ? 60 : hz * 1.1; unit = "Hz"; break;
            }
            case TEMP: value = n.kind == GridKind.CABLE ? n.temp : 25 + n.heat; full = 120; unit = "°C"; break;
            default: value = 0; full = 1; unit = ""; break;
        }
        line1 = (lv != null ? lv.label : net.legacy ? "legacy grid" : "no supply") + (live ? "" : " - DEAD");
        line2 = String.format(Locale.ROOT, "%s  %s", Elec.si(volts, "V"), Elec.si(amps, "A"));
        line3 = String.format(Locale.ROOT, "%s  PF %.2f", Elec.si(net.pW, "W"), net.pf);
    }

    /** the controls on a machine's panel a Control Button can copy (buttons, selectors, keys, levers; not the cabinet door) */
    public static java.util.List<Panel.C> buttons(Panel p) {
        java.util.List<Panel.C> l = new java.util.ArrayList<>();
        if (p != null) for (Panel.C c : p.cs) if (c.clickable() && c.id != null && !c.id.isEmpty() && !c.id.equals("door")) l.add(c);
        return l;
    }

    /**
     * A Control Button copying control `ctl` of the machine at `target` (empty / unknown = its first one): its label, cap
     * colour, where a selector stands, and the machine's run lamp. Returns the control id it settled on.
     */
    public String readButton(World world, BlockPos target, String ctl) {
        net.minecraft.tileentity.TileEntity te = target == null ? null : world.getTileEntity(target);
        if (!(te instanceof com.dogpound.railmap.wire.IWiredMachine m)) { notLinked(target == null ? "no machine on" : "not a machine", "this wire"); col = 0x606060; return ctl; }
        java.util.List<Panel.C> cs = buttons(m.wiredPanel());
        if (cs.isEmpty()) { notLinked(m.wiredName(), "has no buttons"); col = 0x606060; return ctl; }
        Panel.C c = cs.get(0);
        for (Panel.C x : cs) if (x.id.equals(ctl)) c = x;
        int st = m.wiredState(c.id);
        boolean multi = c.positions != null && c.positions.length > 0;
        line1 = c.label == null || c.label.isEmpty() ? c.id.toUpperCase(Locale.ROOT) : c.label;
        line2 = m.wiredName();
        line3 = multi ? c.positions[Math.max(0, Math.min(c.positions.length - 1, st))] : "";
        lamp = m.wiredLamp();
        status = lamp == 1 ? "RUNNING" : lamp == 3 ? "FAULT" : "STOPPED";
        col = c.t == Panel.T.SELECTOR || c.t == Panel.T.KEY ? 0x202020 : c.t == Panel.T.MUSHROOM ? 0xD02020 : c.colour;
        value = st; full = multi ? Math.max(1, c.positions.length - 1) : 1; unit = "";
        return c.id;
    }

    /** the next (or previous) control a Control Button can copy, after `ctl` */
    public static String nextButton(World world, BlockPos target, String ctl, int dir) {
        net.minecraft.tileentity.TileEntity te = target == null ? null : world.getTileEntity(target);
        if (!(te instanceof com.dogpound.railmap.wire.IWiredMachine m)) return ctl;
        java.util.List<Panel.C> cs = buttons(m.wiredPanel());
        if (cs.isEmpty()) return ctl;
        int i = 0;
        for (int j = 0; j < cs.size(); j++) if (cs.get(j).id.equals(ctl)) i = j;
        return cs.get(Math.floorMod(i + dir, cs.size())).id;
    }

    /** a fingerprint of what is shown, to sync only on change */
    public String key() { return value + "|" + full + unit + status + line1 + line2 + line3 + lamp + "|" + hiFrac + loFrac + peakFrac + alarm + damp + "|" + col + "|" + pw; }

    public void write(NBTTagCompound t, String p) {
        t.setDouble(p + "value", value); t.setDouble(p + "full", full); t.setString(p + "unit", unit); t.setString(p + "status", status);
        t.setString(p + "l1", line1); t.setString(p + "l2", line2); t.setString(p + "l3", line3); t.setInteger(p + "lamp", lamp);
        t.setDouble(p + "hi", hiFrac); t.setDouble(p + "lo", loFrac); t.setDouble(p + "pk", peakFrac); t.setBoolean(p + "al", alarm); t.setDouble(p + "dmp", damp); t.setInteger(p + "col", col); t.setInteger(p + "pw", pw);
    }

    public void read(NBTTagCompound t, String p) {
        value = t.getDouble(p + "value"); full = Math.max(1e-9, t.getDouble(p + "full")); unit = t.getString(p + "unit"); status = t.getString(p + "status");
        line1 = t.getString(p + "l1"); line2 = t.getString(p + "l2"); line3 = t.getString(p + "l3"); lamp = t.getInteger(p + "lamp");
        hiFrac = t.getDouble(p + "hi"); loFrac = t.getDouble(p + "lo"); peakFrac = t.hasKey(p + "pk") ? t.getDouble(p + "pk") : -1; alarm = t.getBoolean(p + "al");
        damp = t.hasKey(p + "dmp") ? t.getDouble(p + "dmp") : 1;
        col = t.hasKey(p + "col") ? t.getInteger(p + "col") : 0x606060;
        pw = t.hasKey(p + "pw") ? t.getInteger(p + "pw") : 1;
    }
}
