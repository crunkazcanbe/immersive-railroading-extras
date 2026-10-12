package com.dogpound.railmap.crew;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Revenue Protection Office (batch 5 #9): the inspectors' desk. The CASE FILE screen lists every penalty fare and
 * warning (who, where, which inspector, state, CCTV camera bookmarked); the selected case is shown in full on the
 * terminal. WAIVE refunds a player's penalty fare (they must be online) and marks the case; UPHOLD marks it upheld.
 * The CCTV LINK lamp and count show the live cameras covering the station (cases bookmark the nearest camera's
 * recording). The stats board: checks / dodgers caught / warnings / penalty fares today and yesterday, and the fare
 * evasion the passenger model estimates around here. The PENALTY selector sets warn only / warn first / penalty fare
 * for the whole railway. DEPLOY gives the selected inspector a beat (a line). PRINT LEDGER prints the fines ledger as a
 * book. Real storage: case-file cabinet, evidence store and the ledger drawer; the shelf holds the ledger and whatever
 * else you put on it. 12 V control + 230 V mains.
 */
public class TileRevenueOffice extends StnFurniture {
    static double[][] SPOTS = {{-11.525, 22.0, 14.0}, {-8.575, 22.0, 14.0}, {-5.625, 22.0, 14.0}, {-2.675, 22.0, 14.0}};   // tools/gen_bb_crew.py
    private int ccur, icur, lcur;
    private String lastMsg = "";
    private long testUntil;
    private boolean unloading;

    public TileRevenueOffice() {
        super(new String[]{"cf", "ev", "ld"}, new String[]{"Case file cabinet", "Evidence store", "Ledger drawer"}, new int[]{27, 18, 9}, 4);
    }

    @Override public String wiredName() { return "Revenue protection office"; }
    @Override public String gearState() { CrewData c = CrewData.get(world); return !live() ? "NO SUPPLY" : c.today[Revenue.CAUGHT] + " caught today · " + Coins.fmt(c.today[Revenue.CENTS]); }
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override protected boolean mechanical(String id) { return id.startsWith("s_") || id.startsWith("k_"); }

    List<CrewData.Case> cases() { return new ArrayList<>(CrewData.get(world).cases); }
    CrewData.Case sel() { List<CrewData.Case> l = cases(); if (l.isEmpty()) return null; ccur = Math.floorMod(ccur, l.size()); return l.get(ccur); }
    List<CrewData.Staff> inspectors() { List<CrewData.Staff> l = new ArrayList<>(); for (CrewData.Staff s : CrewData.get(world).staff.values()) if (s.role == CrewData.INSPECTOR) l.add(s); return l; }
    CrewData.Staff insp() { List<CrewData.Staff> l = inspectors(); if (l.isEmpty()) return null; icur = Math.floorMod(icur, l.size()); return l.get(icur); }
    String line() { List<String> l = new ArrayList<>(RailwayData.get(world).lines().keySet()); Collections.sort(l); l.add(0, ""); lcur = Math.floorMod(lcur, l.size()); return l.get(lcur); }

    @Override public int controlValue(String id) { return term.powered() && ctl.get("lamptest") == 1 && !mechanical(id) ? Math.max(1, ctl.get(id)) : super.controlValue(id); }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        if (!id.equals("s_mode")) return null;
        CrewData c = CrewData.get(world);
        c.penaltyMode = Math.floorMod(c.mode() + 1, 3);
        c.markDirty();
        ctl.set(id, c.penaltyMode);
        String[] m = {"WARNINGS ONLY", "WARN FIRST, THEN PENALTY FARE", "PENALTY FARE"};
        Recorder.event(world, "Revenue protection policy: " + m[c.penaltyMode] + " (" + StnFurniture.who(who) + ")");
        return "§ePenalty policy: " + m[c.penaltyMode] + " (" + Coins.fmt(CrewConfig.penaltyCents) + ")";
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        CrewData c = CrewData.get(world);
        CrewData.Case k = sel();
        String r = switch (id) {
            case "test" -> { testUntil = world.getTotalWorldTime() + 60; ctl.set("lamptest", 1); yield "§7Lamp test"; }
            case "cprev" -> { ccur--; yield ""; }
            case "cnext" -> { ccur++; yield ""; }
            case "iprev" -> { icur--; yield ""; }
            case "inext" -> { icur++; yield ""; }
            case "lprev" -> { lcur--; yield ""; }
            case "lnext" -> { lcur++; yield ""; }
            case "waive" -> Revenue.waive(world, c, k, StnFurniture.who(who));
            case "uphold" -> {
                if (k == null) yield "§7No case selected";
                if (k.state.equals("WAIVED")) yield "§7That case was waived";
                k.state = k.state.startsWith("UPHELD") ? k.state : "UPHELD " + k.state;
                c.markDirty();
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.7f, 1.0f);
                yield "§aCase #" + k.id + " upheld";
            }
            case "deploy" -> {
                CrewData.Staff s = insp();
                if (s == null) yield "§7No inspectors - hire one at a Crew Office";
                s.line = line();
                c.markDirty();
                com.dogpound.railmap.depot.FleetData.get(world).crewNote(world, s.name + " (inspector) deployed to " + (s.line.isEmpty() ? "any line" : "the " + s.line));
                yield "§a" + s.name + " deployed to " + (s.line.isEmpty() ? "any line" : "the " + s.line);
            }
            case "print" -> {
                List<String> out = new ArrayList<>();
                out.add("FINES LEDGER");
                out.add("Today: " + c.today[Revenue.CHECKS] + " checked, " + c.today[Revenue.CAUGHT] + " caught, " + Coins.fmt(c.today[Revenue.CENTS]));
                out.add("All time: " + c.total[Revenue.CHECKS] + " checked, " + c.total[Revenue.CAUGHT] + " caught, " + Coins.fmt(c.total[Revenue.CENTS]));
                for (CrewData.Case x : c.cases)
                    out.add("#" + x.id + " " + Recorder.clock(world, x.tick) + " " + x.who + " - " + x.where + " - " + x.state + (x.cents > 0 ? " " + Coins.fmt(x.cents) : "") + " (" + x.inspector + (x.cam.isEmpty() ? "" : ", " + x.cam) + ")");
                StnUtil.print(world, pos, controlFacing(), "Fines ledger", "Revenue protection", out);
                yield "§aLedger printed";
            }
            default -> null;
        };
        if (r != null && !r.isEmpty()) lastMsg = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(r);
        return r;
    }

    @Override
    protected void work() {
        long now = world.getTotalWorldTime();
        if (testUntil > 0 && now > testUntil) { testUntil = 0; ctl.set("lamptest", 0); controlsChanged(); }
        if (age % 100 == 23) { CrewData c = CrewData.get(world); if (c.rpOffices.add(pos.toLong())) c.markDirty(); }
    }

    @Override
    protected boolean refresh() {
        CrewData c = CrewData.get(world);
        List<CrewData.Case> cs = cases();
        if (!cs.isEmpty()) ccur = Math.floorMod(ccur, cs.size());
        int top = cs.isEmpty() ? 0 : ccur / 6 * 6;
        boolean ch = StationControls.put(ctl, "s_mode", c.mode());
        for (int i = 0; i < 6; i++) {
            CrewData.Case k = top + i < cs.size() ? cs.get(top + i) : null;
            String row = k == null ? "" : String.format(Locale.ROOT, "#%-4d %-10s %-9s %-7s %s", k.id, cut(k.who.toUpperCase(Locale.ROOT), 10), cut(k.where.toUpperCase(Locale.ROOT), 9),
                    cut(k.state, 7), k.cents > 0 ? Coins.fmt(k.cents) : "");
            ch |= StationControls.text(ctl, "c" + (i + 1), row);
            ch |= StationControls.put(ctl, "c" + (i + 1) + "s", k != null && top + i == ccur);
        }
        CrewData.Case k = sel();
        String[] d = new String[4];
        Arrays.fill(d, "");
        if (k == null) d[0] = "NO CASES";
        else {
            d[0] = "CASE #" + k.id + " " + k.state + (k.cents > 0 ? " " + Coins.fmt(k.cents) : "");
            d[1] = (k.who + (k.player != null ? " (PLAYER)" : "")).toUpperCase(Locale.ROOT);
            d[2] = ("AT " + k.where + " " + Recorder.clock(world, k.tick)).toUpperCase(Locale.ROOT);
            d[3] = ("BY " + k.inspector + " - " + (k.cam.isEmpty() ? "NO CCTV" : k.cam + " BOOKMARKED")).toUpperCase(Locale.ROOT);
        }
        for (int i = 0; i < 4; i++) ch |= StationControls.text(ctl, "d" + (i + 1), cut(d[i], 40));
        ch |= StationControls.text(ctl, "st1", String.format(Locale.ROOT, "TODAY %d CHK %d CAUGHT %d WARN %s", c.today[Revenue.CHECKS], c.today[Revenue.CAUGHT], c.today[Revenue.WARNED], Coins.fmt(c.today[Revenue.CENTS])));
        ch |= StationControls.text(ctl, "st2", String.format(Locale.ROOT, "YDAY  %d CHK %d CAUGHT %s", c.yesterday[Revenue.CHECKS], c.yesterday[Revenue.CAUGHT], Coins.fmt(c.yesterday[Revenue.CENTS])));
        double ev = 0, b = 0;
        Map.Entry<Long, String> st = com.dogpound.railmap.stn.StnData.station(world, pos, 160);
        PassengerData.Stn ps = st == null ? null : PassengerData.get(world).stn(st.getKey());
        if (ps != null) { ev = ps.evadersDay; b = ps.boardedDay; }
        ch |= StationControls.text(ctl, "st3", ps == null ? "NO STATION NEARBY" : String.format(Locale.ROOT, "%s EVASION %.1f%% %s", cut(ps.name.toUpperCase(Locale.ROOT), 12), b > 0 ? ev * 100 / b : 0,
                Revenue.deterrent(world, ps.key) < 1 ? "PATROLLED" : "NOT PATROLLED"));
        int cams = Revenue.camerasNear(world, pos, 64);
        ch |= StationControls.put(ctl, "cctvl", cams > 0) | StationControls.text(ctl, "ncam", cams + " CAMS");
        CrewData.Staff s = insp();
        String l = line();
        ch |= StationControls.text(ctl, "isel", s == null ? "NO INSPECTORS" : cut((s.name + " " + (s.line.isEmpty() ? "ANY" : s.line)).toUpperCase(Locale.ROOT), 18));
        ch |= StationControls.text(ctl, "lsel", l.isEmpty() ? "ANY LINE" : cut(l.toUpperCase(Locale.ROOT), 12));
        int open = 0;
        for (CrewData.Case x : cs) if (x.state.equals("PAID") || x.state.equals("WARNED")) open++;
        ch |= StationControls.put(ctl, "openl", open > 0) | StationControls.put(ctl, "nopen", open) | StationControls.put(ctl, "pwr", term.powered());
        int onPatrol = 0;
        List<CrewData.Staff> ins = inspectors();
        for (CrewData.Staff x : ins) if (x.onDuty) onPatrol++;
        for (int i = 0; i < 4; i++) ch |= StationControls.put(ctl, "ch" + (i + 1), i < ins.size() && !ins.get(i).onDuty);   // an off-duty inspector's machine is on charge
        ch |= StationControls.put(ctl, "patl", onPatrol > 0) | StationControls.put(ctl, "npat", onPatrol);
        ch |= StationControls.text(ctl, "msg", cut(lastMsg, 40));
        return ch;
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("cc", ccur); t.setInteger("ic", icur); t.setInteger("lc", lcur); }
    @Override protected void load(NBTTagCompound t) { ccur = t.getInteger("cc"); icur = t.getInteger("ic"); lcur = t.getInteger("lc"); }

    @Override public void onChunkUnload() { unloading = true; super.onChunkUnload(); }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote && !unloading) { CrewData c = CrewData.get(world); if (c.rpOffices.remove(pos.toLong())) c.markDirty(); }
    }
}
