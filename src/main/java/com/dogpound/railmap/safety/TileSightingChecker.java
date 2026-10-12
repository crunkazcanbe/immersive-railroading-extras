package com.dogpound.railmap.safety;

import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.*;

/**
 * Signal Sighting Checker (her ChatGPT batch-3 #16): a survey camera on a tripod with a screen, the tool a sighting
 * committee walks the line with. Link a signal (Signal Wrench: pick it, then click the screen) - or step through every
 * signal that is short of its sighting with PREV / NEXT. SURVEY re-runs the signal's real survey (SignalSighting: the
 * approach walked back along the track, sight lines from a cab eye every 4 m, weather); SHOW draws the sight line,
 * the obstruction and the suggested banner repeater spot in the air for ~10 s for everyone near. The LINE SPEED
 * selector sets the speed this signal is approached at, which sets the sighting it needs (seconds x speed).
 */
public class TileSightingChecker extends SafetyTile {
    static final int[] KMH = {0, 40, 60, 80, 100, 125, 160};
    private long signal;
    private long showUntil;

    @Override public String wiredName() { return "Sighting checker"; }

    private TileSignalMast mast() {
        if (signal == 0) return null;
        BlockPos p = BlockPos.fromLong(signal);
        return world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileSignalMast m ? m : null;
    }

    @Override public String gearState() {
        TileSignalMast m = mast();
        if (m == null) return signal == 0 ? "no signal linked" : "signal not loaded";
        SignalSighting.Result r = SignalSighting.get(world, m.getPos());
        return r == null ? SignalSighting.name(m) + ": not surveyed yet" : SignalSighting.name(m) + ": " + Math.round(SignalSighting.sightNow(world, r)) + " of " + Math.round(SignalSighting.requiredAt(world, m.getPos())) + " m";
    }
    @Override public int wiredLamp() { TileSignalMast m = mast(); return m == null ? 1 : SignalSighting.deficit(world, m.getPos()) > 0 ? 2 : 1; }

    @Override
    protected void serverTick() {
        long now = world.getTotalWorldTime();
        if (showUntil > now && age % 8 == 0) show();
        if (age % 20 != 13) return;
        TileSignalMast m = mast();
        SignalSighting.Result r = m == null ? null : SignalSighting.get(world, m.getPos());
        double need = m == null ? SignalSighting.required(world) : SignalSighting.requiredAt(world, m.getPos());
        double now2 = r == null ? 0 : SignalSighting.sightNow(world, r), shortM = m == null ? 0 : SignalSighting.deficit(world, m.getPos());
        put("pwr", powered());
        put("ok", r != null && shortM <= 0);
        put("short", r != null && shortM > 0);
        put("showl", showUntil > now);
        put("vis", m == null ? 0 : (int) Math.round(SignalSighting.visibility(world, m.getPos()) * 100));
        put("s_kmh", kmhIndex(m));
        text("sig", m == null ? "NO SIGNAL" : SignalSighting.name(m));
        text("sight", r == null ? "--" : Math.round(now2) + "M");
        text("need", Math.round(need) + "M " + (m == null ? "" : SignalSighting.kmhAt(world, m.getPos()) + "KMH"));
        text("short", r == null ? "--" : shortM > 0 ? "SHORT " + Math.round(shortM) + "M" : "OK");
        text("obs", r == null ? "" : r.obstruction.toUpperCase(Locale.ROOT));
        text("ban", r == null ? "" : r.banner > 0 ? "BANNER " + Math.round(r.banner) + "M" : r.bannerAt != null ? "FIT BANNER " + Math.round(r.bannerAt[0]) + " " + Math.round(r.bannerAt[2]) : shortM > 0 ? "NO BANNER SPOT" : "");
        text("cnt", shortList().size() + " SHORT");
    }

    private int kmhIndex(TileSignalMast m) {
        if (m == null || !SafetyData.get(world).sightKmhAt.containsKey(m.getPos().toLong())) return 0;
        int k = SafetyData.get(world).sightKmhAt.get(m.getPos().toLong());
        for (int i = 1; i < KMH.length; i++) if (KMH[i] == k) return i;
        return 0;
    }

    /** signals short of their sighting, in a fixed order */
    List<Long> shortList() {
        List<Long> l = new ArrayList<>();
        for (SignalSighting.Result r : SignalSighting.all(world).values()) if (SignalSighting.deficit(world, BlockPos.fromLong(r.pos)) > 0) l.add(r.pos);
        Collections.sort(l);
        return l;
    }

    /** particles: the sight line from the furthest point the signal is seen, the obstruction, the banner spot */
    void show() {
        TileSignalMast m = mast();
        SignalSighting.Result r = m == null ? null : SignalSighting.get(world, m.getPos());
        if (r == null || !(world instanceof WorldServer ws)) return;
        double hx = m.getPos().getX() + 0.5, hy = m.getPos().getY() + 2.4 * Math.max(0.5, m.scale()), hz = m.getPos().getZ() + 0.5;
        double[] eye = null;
        for (double[] p : r.approach) { if (p[3] > r.sight + 0.5) break; eye = p; }
        if (eye != null) for (int i = 0; i <= 24; i++) {
            double f = i / 24.0;
            ws.spawnParticle(EnumParticleTypes.END_ROD, eye[0] + (hx - eye[0]) * f, eye[1] + 2.6 + (hy - eye[1] - 2.6) * f, eye[2] + (hz - eye[2]) * f, 1, 0, 0, 0, 0.0);
        }
        if (r.obstructionPos != 0) {
            BlockPos o = BlockPos.fromLong(r.obstructionPos);
            ws.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY, o.getX() + 0.5, o.getY() + 1.2, o.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
            ws.spawnParticle(EnumParticleTypes.REDSTONE, o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.0);
        }
        if (r.bannerAt != null) ws.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, r.bannerAt[0], r.bannerAt[1] + 1.6, r.bannerAt[2], 8, 0.2, 0.6, 0.2, 0.0);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        TileSignalMast m = mast();
        switch (id) {
            case "test": return lampTest();
            case "survey": {
                if (!powered()) return "§cNo 12 V - the camera is dead";
                if (m == null) return "§7Link a signal first (Signal Wrench: pick it, then click the screen) or use NEXT";
                try {
                    SignalSighting.Result r = SignalSighting.survey(world, m);
                    SignalSighting.store(world, r);
                    world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.5f, 1.0f);
                    return "§e" + r.name + ": " + Math.round(SignalSighting.sightNow(world, r)) + " m of " + Math.round(SignalSighting.requiredAt(world, m.getPos())) + " m - " + r.obstruction;
                } catch (LinkageError | RuntimeException e) { return "§cSurvey failed: " + e; }
            }
            case "show":
                if (!powered()) return "§cNo 12 V";
                if (m == null || SignalSighting.get(world, m.getPos()) == null) return "§7Survey a signal first";
                showUntil = world.getTotalWorldTime() + 200;
                return "§eShowing the sight line (white), the obstruction (red) and the banner spot (green) for 10 s";
            case "prev": case "next": {
                List<Long> l = shortList();
                if (l.isEmpty()) return "§aNo signal is short of its sighting";
                int i = l.indexOf(signal);
                i = i < 0 ? 0 : Math.floorMod(i + (id.equals("next") ? 1 : -1), l.size());
                signal = l.get(i);
                controlsChanged();
                return "§eSignal " + Geo.xz(signal) + " (" + (i + 1) + " of " + l.size() + " short)";
            }
            case "s_kmh": {
                if (m == null) return "§7Link a signal first";
                int n = (kmhIndex(m) + 1) % KMH.length;
                SafetyData d = SafetyData.get(world);
                if (n == 0) d.sightKmhAt.remove(m.getPos().toLong()); else d.sightKmhAt.put(m.getPos().toLong(), KMH[n]);
                d.markDirty();
                ctl.set("s_kmh", n);
                return "§eLine speed at " + SignalSighting.name(m) + ": " + (n == 0 ? "railway default " + d.sightKmh : KMH[n]) + " km/h - needs " + Math.round(SignalSighting.requiredAt(world, m.getPos())) + " m (re-survey)";
            }
            default: return "§7" + gearState();
        }
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (!(world.getTileEntity(pick) instanceof TileSignalMast)) return "§7Pick the signal to check";
        signal = pick.toLong();
        controlsChanged();
        return "§aChecker on the signal at " + pick.getX() + "," + pick.getZ() + " - press SURVEY";
    }

    @Override protected void save(NBTTagCompound t) { t.setLong("sig", signal); }
    @Override protected void load(NBTTagCompound t) { signal = t.getLong("sig"); }

}
