package com.dogpound.railmap.ohle;

import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * OHLE Feeding Diagram (her ChatGPT list #31: "feeding diagrams"): the electrical control room mimic for the overhead
 * line. Six sections (nearest first) with LIVE / EARTHED / FAULT lamps and their state, four feeder breakers with
 * CLOSED / TRIPPED / LOCKOUT lamps and CLOSE / OPEN pushes, six isolators with their position, earth and an OPERATE push
 * (works isolators in REMOTE or AUTO). Every lamp and button is real; with no 12 V the mimic is dark and dead.
 */
public class TileFeedingDiagram extends SafetyTile {
    static final int NS = 6, NF = 4, NI = 6;
    private final long[] feeders = new long[NF];
    private final long[] isolators = new long[NI];

    @Override public String wiredName() { return "OHLE feeding diagram " + Ohle.xz(pos); }
    @Override public String gearState() { return powered() ? "mimic live" : "dark (no 12 V)"; }

    @Override
    protected void serverTick() {
        if (age % 20 != 9) return;
        OhleData d = OhleData.get(world);
        List<Ohle.Section> secs = new ArrayList<>(Ohle.known(world));
        secs.sort(Comparator.comparingDouble(s -> BlockPos.fromLong(s.anchor).distanceSq(pos)));
        for (int i = 0; i < NS; i++) {
            Ohle.Section s = i < secs.size() ? secs.get(i) : null;
            text("sn" + i, s == null ? "" : s.name());
            text("ss" + i, s == null ? "" : s.state() + (s.feeders.isEmpty() ? "" : " F" + s.feeders.size()));
            put("sl" + i, s != null && s.energised);
            put("se" + i, s != null && s.earthed);
            put("sf" + i, s != null && s.faulted);
        }
        List<Long> fl = new ArrayList<>();
        for (Map.Entry<Long, GridData.Node> en : GridData.get(world).nodes().entrySet())
            if (en.getValue().kind == GridKind.FEEDER && (en.getValue().feeds.equals("Wire") || en.getValue().feeds.equals("Auto"))) fl.add(en.getKey());
        fl.sort(Comparator.comparingDouble(f -> BlockPos.fromLong(f).distanceSq(pos)));
        for (int j = 0; j < NF; j++) {
            feeders[j] = j < fl.size() ? fl.get(j) : 0;
            OhleData.Cb cb = feeders[j] == 0 ? null : d.cbs.get(feeders[j]);
            int st = cb == null ? 0 : cb.state;
            text("fn" + j, feeders[j] == 0 ? "" : "F " + Ohle.xz(BlockPos.fromLong(feeders[j])));
            put("fc" + j, feeders[j] != 0 && st == 0);
            put("ft" + j, st == 1);
            put("fl" + j, st == 2);
        }
        List<TileIsolator> il = TileIsolator.sorted(world, pos);
        for (int k = 0; k < NI; k++) {
            TileIsolator t = k < il.size() ? il.get(k) : null;
            isolators[k] = t == null ? 0 : t.getPos().toLong();
            text("in" + k, t == null ? "" : "I " + Ohle.xz(t.getPos()) + " " + TileIsolator.MODES[t.mode()].charAt(0));
            put("ic" + k, t != null && t.closed());
            put("io" + k, t != null && !t.closed());
            put("ie" + k, t != null && t.earthed());
        }
        text("ev", d.log.isEmpty() ? "NO EVENTS" : d.log.getFirst());
        put("pwr", powered());
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        if (!powered()) return "§cThe mimic is dead - no 12 V";
        String w = who == null ? "control room" : who.getName() + " (diagram)";
        if (id.length() == 3 && (id.startsWith("fC") || id.startsWith("fO"))) {
            int j = id.charAt(2) - '0';
            if (j < 0 || j >= NF || feeders[j] == 0) return "§7No feeder on this row";
            return Ohle.feederOp(world, feeders[j], id.charAt(1) == 'C', w);
        }
        if (id.startsWith("iop")) {
            int k = id.charAt(3) - '0';
            if (k < 0 || k >= NI || isolators[k] == 0) return "§7No isolator on this row";
            if (!(world.getTileEntity(BlockPos.fromLong(isolators[k])) instanceof TileIsolator t)) return "§7Isolator not loaded";
            return t.remote(null, w);
        }
        return null;
    }

    @Override protected void save(NBTTagCompound t) {}
    @Override protected void load(NBTTagCompound t) {}
}
