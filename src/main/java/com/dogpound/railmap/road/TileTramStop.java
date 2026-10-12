package com.dogpound.railmap.road;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.ohle.Reg;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.sound.PackSounds;
import com.dogpound.railmap.stn.StnPanel;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Tram stop (batch 5 #4): a three-bay glass shelter with bench, stop flag, timetable case, help point and the
 * real-time passenger information display (PIS). Placed beside the tram track it is a station (named "Tram Stop x,z"
 * until you rename it), so lines can call at it like any station; tram-mode trams also stop at it on their way when it
 * is ALL STOP, or - with the STOP key at REQUEST - when someone waits at it or has just validated a ticket here.
 * <p>
 * The PIS shows the next three trams (line, destination, minutes - DUE / NOW), and a message line; the TRAM DUE lamp,
 * the lit platform edge markers and a chime warn when a tram is about to arrive. INFO reads the next trams out to people
 * at the stop. 12 V + mains (the display and lighting); the stop itself works without power, the display doesn't.
 */
public class TileTramStop extends StnPanel {
    static final Reg<TileTramStop> REG = new Reg<>();
    private int arrivals, validations;
    private long validatedAt = -1_000_000L, apprSince = -1;
    private boolean appr;
    private final Set<UUID> here = new HashSet<>();

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    public static Collection<TileTramStop> all(World w) { return REG.all(w); }

    public static TileTramStop nearest(World w, BlockPos p, double reach) {
        TileTramStop best = null;
        double bd = reach * reach;
        for (TileTramStop s : REG.all(w)) { double d = s.getPos().distanceSq(p); if (d < bd) { bd = d; best = s; } }
        return best;
    }

    @Override public String wiredName() { return "Tram stop " + stopName(); }
    @Override public String gearState() { return !live() ? "NO SUPPLY - display dark (trams still stop)" : (request() ? "REQUEST stop" : "ALL STOP") + " · " + arrivals + " trams · " + validations + " validations"; }
    @Override protected long load() { return 5; }

    public String stopName() { String n = StationData.get(world).nameAt(pos); return n == null ? "Tram Stop " + pos.getX() + "," + pos.getZ() : n; }
    public boolean request() { return ctl.get("k_req") == 1; }
    public boolean approaching() { return appr; }

    /** should a passing tram stop here? (works without power - it is the stop flag) */
    public boolean wantsTram() {
        if (!request()) return true;
        if (world.getTotalWorldTime() - validatedAt < 1200) return true;
        for (EntityPlayer p : world.playerEntities) if (!p.isRiding() && p.getDistanceSq(pos) < 7 * 7) return true;
        return false;
    }

    void arrived(UUID tram) { if (here.add(tram)) { arrivals++; markDirty(); world.playSound(null, pos, PackSounds.ev("cab.bell"), SoundCategory.BLOCKS, 0.8f, 1.2f); } }
    void departed(UUID tram) { here.remove(tram); }
    void validated() { validations++; validatedAt = world.getTotalWorldTime(); markDirty(); }

    @Override
    protected String act(String id, EntityPlayer who) {
        if (!id.equals("info")) return null;
        List<String> l = next(3);
        String msg = "§d[" + stopName() + "] §f" + (l.isEmpty() ? "No trams are running to this stop" : String.join(" · ", l).replaceAll(" +", " "));
        for (EntityPlayer p : world.playerEntities) if (p.getDistanceSq(pos) < 10 * 10) p.sendMessage(new TextComponentString(msg));
        world.playSound(null, pos, PackSounds.ev("station.chime_two_tone"), SoundCategory.BLOCKS, 0.7f, 1.0f);
        return "";
    }

    @Override
    protected void work() {
        if (age % 200 == 7 && StationData.get(world).nameAt(pos) == null)                  // a stop is a station: lines can call at it
            StationData.get(world).setName(pos, "Tram Stop " + pos.getX() + "," + pos.getZ());
        if (age % 20 != 5) return;
        List<String> l = next(3);
        boolean was = appr;
        appr = soon();
        if (appr && !was) { apprSince = world.getTotalWorldTime(); if (live()) world.playSound(null, pos, PackSounds.ev("station.chime_dingdong"), SoundCategory.BLOCKS, 0.9f, 1.0f); }
        put("appr", appr);
        text("name", StnUtil.up(StnUtil.cut(stopName(), 22)) + "  " + com.dogpound.railmap.biz.BizData.hhmm(world.getWorldTime()));
        for (int i = 0; i < 3; i++) text("p" + (i + 1), i < l.size() ? l.get(i) : "");
        text("pmsg", appr ? "TRAM APPROACHING - STAND BACK" : l.isEmpty() ? "NO TRAMS RUNNING" : request() ? "REQUEST STOP - WAIT HERE" : "");
    }

    /** a tram due within ~25 s, or standing here */
    private boolean soon() {
        long now = world.getTotalWorldTime();
        for (Object[] t : trams()) if ((double) t[1] < 25) return true;
        for (UUID u : here) if (TramMode.dwellingAt(u, pos, now)) return true;
        return false;
    }

    /** the next trams as PIS lines "LINE DEST MIN" */
    List<String> next(int n) {
        List<Object[]> l = trams();
        l.sort(Comparator.comparingDouble(t -> (double) t[1]));
        List<String> out = new ArrayList<>();
        for (Object[] t : l) {
            if (out.size() >= n) break;
            double s = (double) t[1];
            out.add(String.format(Locale.ROOT, "%-4s %-13s %s", StnUtil.cut((String) t[0], 4), StnUtil.cut((String) t[2], 13), s < 0 ? "NOW" : s < 30 ? "DUE" : (int) Math.ceil(s / 60) + " MIN"));
        }
        return out;
    }

    /** {line, seconds to here (-1 = standing here), destination} for every tram heading this way */
    private List<Object[]> trams() {
        List<Object[]> out = new ArrayList<>();
        String me = stopName();
        RailwayData rd = RailwayData.get(world);
        StationData sd = StationData.get(world);
        long now = world.getTotalWorldTime();
        try {
            for (Locomotive l : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) {
                if (!TramMode.isTram(world, l)) continue;
                cam72cam.mod.math.Vec3d p = l.getPosition();
                double dist = Math.sqrt(pos.distanceSq(p.x, p.y, p.z));
                if (dist > 2000) continue;
                RailwayData.AutoTrain a = rd.train(l.getUUID());
                Autopilot.Run r = Autopilot.run(l.getUUID());
                String line = a == null || a.line.isEmpty() ? "-" : a.line;
                RailwayData.Line ln = a == null ? null : rd.line(a.line);
                String dest = ln == null || ln.stations.isEmpty() ? "-" : Optional.ofNullable(sd.names().get(ln.stations.get(a.direction < 0 ? 0 : ln.stations.size() - 1))).orElse("-");
                double ms = Math.max(4, l.getCurrentSpeed() == null ? 0 : Math.abs(l.getCurrentSpeed().metersPerSecond()));
                if (TramMode.dwellingAt(l.getUUID(), pos, now) || r != null && r.dwelling && r.dwellStation == pos.toLong()) out.add(new Object[]{line, -1.0, dest});
                else if (r != null && me.equals(r.nextStopName) && r.metresToStop >= 0) out.add(new Object[]{line, r.metresToStop / ms, dest});
                else if (dist < 600 && (r == null || r.nextStopName.isEmpty() || dist < 120)) out.add(new Object[]{line, dist * 1.4 / ms, dest});
            }
        } catch (RuntimeException | LinkageError e) {
            return out;
        }
        return out;
    }

    public String[] row() {
        List<String> l = next(1);
        return new String[]{stopName(), !live() ? "!NO SUPPLY" : request() ? "~REQUEST" : "+ALL STOP", l.isEmpty() ? "-" : l.get(0).replaceAll(" +", " "), appr ? "!DUE" : ".", String.valueOf(arrivals), String.valueOf(validations)};
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setInteger("arr", arrivals); t.setInteger("val", validations); }
    @Override protected void loadExtra(NBTTagCompound t) { arrivals = t.getInteger("arr"); validations = t.getInteger("val"); }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 2, 2); }
}
