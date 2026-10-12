package com.dogpound.railmap.ops6;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.TileArrivalsBoard;
import com.dogpound.railmap.station.TilePisBoard;
import com.dogpound.railmap.station.TileStationPiece;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The master clock system (batch 6 E #24). Every station clock, arrivals board and PIS sign is a SLAVE clock with its
 * own quartz movement: left alone it drifts (each one at its own rate, up to Ops6Config.clockDriftMinPerDay). A powered
 * Master Clock in range (Ops6Config.clockRange) with OUTPUT on pulls every slave onto its own time at each impulse
 * (every second, 30 s or minute - its PULSE selector), and the master itself is held to GPS or radio time, or runs on
 * its crystal (holdover) when it loses them. Old worlds: no slave drifts until a master clock has been placed once.
 * Offsets are in game ticks (a game minute is 16.7 ticks); the client gets the ones near the player every 2 s.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class MasterClocks {
    private MasterClocks() {}

    public static final class Slave {
        public float drift;          // ticks the slave is off railway time (+ = fast)
        public long syncAt, seenAt;
        public long master;          // the master that last pulsed it (pos)
        public boolean fault;        // dark: no 12 V on a powered slave
        public String kind = "";
    }

    public static final class Data extends WorldSavedData {
        static final String NAME = "irextras_masterclock";
        public boolean everMaster;
        public final Map<Long, Slave> slaves = new HashMap<>();
        public Data() { super(NAME); }
        public Data(String n) { super(n); }

        @Override public void readFromNBT(NBTTagCompound t) {
            everMaster = t.getBoolean("ever");
            NBTTagCompound s = t.getCompoundTag("s");
            for (String k : s.getKeySet()) {
                NBTTagCompound c = s.getCompoundTag(k);
                Slave x = new Slave();
                x.drift = c.getFloat("d"); x.syncAt = c.getLong("sy"); x.master = c.getLong("m"); x.kind = c.getString("k");
                try { slaves.put(Long.parseLong(k), x); } catch (NumberFormatException ignored) { }
            }
        }

        @Override public NBTTagCompound writeToNBT(NBTTagCompound t) {
            t.setBoolean("ever", everMaster);
            NBTTagCompound s = new NBTTagCompound();
            for (Map.Entry<Long, Slave> e : slaves.entrySet()) {
                NBTTagCompound c = new NBTTagCompound();
                c.setFloat("d", e.getValue().drift); c.setLong("sy", e.getValue().syncAt); c.setLong("m", e.getValue().master); c.setString("k", e.getValue().kind);
                s.setTag(String.valueOf(e.getKey()), c);
            }
            t.setTag("s", s);
            return t;
        }
    }

    public static Data data(World w) {
        MapStorage s = w.getPerWorldStorage();
        Data d = (Data) s.getOrLoadData(Data.class, Data.NAME);
        if (d == null) { d = new Data(); s.setData(Data.NAME, d); }
        return d;
    }

    /** ticks per tick a free slave at this position drifts: its own rate, -1..+1 of the configured worst */
    public static double rate(long pos) {
        long h = pos * 0x9E3779B97F4A7C15L;
        double f = ((h >>> 11) % 2001) / 1000.0 - 1.0;                          // -1 .. +1
        if (Math.abs(f) < 0.2) f = Math.copySign(0.2, f == 0 ? 1 : f);           // no movement keeps perfect time
        return f * Ops6Config.clockDriftMinPerDay * (1000.0 / 60) / 24000.0;
    }

    /** is this tile a slave clock? its kind, or null */
    public static String slaveKind(TileEntity t) {
        if (t instanceof TileStationPiece p && "clock".equals(p.kind())) return "STATION CLOCK";
        if (t instanceof TileArrivalsBoard) return "ARRIVALS BOARD";
        if (t instanceof TilePisBoard) return "PIS SIGN";
        return null;
    }

    static boolean slavePowered(TileEntity t) {
        if (t instanceof TileArrivalsBoard a) return a.term().powered();
        if (t instanceof TilePisBoard p) return p.powered();
        return true;                                                             // a station clock is a mains movement
    }

    /** server: the offset (ticks) of the slave clock at pos */
    public static int offset(World w, BlockPos pos) {
        Slave s = data(w).slaves.get(pos.toLong());
        return s == null ? 0 : Math.round(s.drift);
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.getTotalWorldTime() % 20 != 11) return;
        try { second(e.world); } catch (RuntimeException | LinkageError ex) { RailMap.LOG.warn("[IR Extras] master clock tick failed: {}", ex.toString()); }
    }

    static void second(World w) {
        List<TileMasterClock> masters = new ArrayList<>();
        List<TileEntity> slaves = new ArrayList<>();
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (t.isInvalid()) continue;
            if (t instanceof TileMasterClock m) masters.add(m);
            else if (slaveKind(t) != null) slaves.add(t);
        }
        Data d = data(w);
        if (!masters.isEmpty() && !d.everMaster) { d.everMaster = true; d.markDirty(); }
        if (slaves.isEmpty() && masters.isEmpty()) return;
        long now = w.getTotalWorldTime();
        double r2 = (double) Ops6Config.clockRange * Ops6Config.clockRange;
        for (TileMasterClock m : masters) m.count[0] = m.count[1] = m.count[2] = 0;
        for (TileEntity t : slaves) {
            long key = t.getPos().toLong();
            Slave s = d.slaves.computeIfAbsent(key, k -> new Slave());
            s.kind = slaveKind(t);
            s.seenAt = now;
            s.fault = !slavePowered(t);
            TileMasterClock best = null;
            double bd = r2;
            for (TileMasterClock m : masters) {
                double dd = m.getPos().distanceSq(t.getPos());
                if (dd <= r2) m.count[0]++;
                if (dd <= bd && m.outputting()) { bd = dd; best = m; }
            }
            if (best != null && !s.fault) {
                if (now - s.syncAt >= best.pulseTicks()) { s.drift = best.offset; s.syncAt = now; s.master = best.getPos().toLong(); }
                else s.drift += rate(key) * 20;
                if (Math.abs(s.drift - best.offset) < 17) best.count[1]++;        // within a game minute of the master
            } else if (Ops6Config.clockDrift && d.everMaster) s.drift += rate(key) * 20;
            if (s.fault) for (TileMasterClock m : masters) if (m.getPos().distanceSq(t.getPos()) <= r2) m.count[2]++;
        }
        d.slaves.values().removeIf(s -> now - s.seenAt > 24000L * 7 && now - s.syncAt > 24000L * 7);   // gone for a week: forget it
        d.markDirty();
        if (now % 40 == 11) for (EntityPlayer p : w.playerEntities) if (p instanceof EntityPlayerMP mp) send(w, d, mp);
    }

    static void send(World w, Data d, EntityPlayerMP p) {
        NBTTagCompound o = new NBTTagCompound();
        int n = 0;
        for (Map.Entry<Long, Slave> e : d.slaves.entrySet()) {
            BlockPos b = BlockPos.fromLong(e.getKey());
            if (p.getDistanceSq(b) > 160 * 160) continue;
            int off = Math.round(e.getValue().drift);
            if (off != 0) { o.setInteger(String.valueOf(e.getKey()), off); n++; }
            if (n > 400) break;
        }
        Ops6Net.NET.sendTo(PacketOps6.of("clocks").data(o), p);
    }

    // ---- client ---------------------------------------------------------------------------------------------------------
    private static final Map<Long, Integer> CLIENT = new ConcurrentHashMap<>();

    public static void clientSync(NBTTagCompound o) {
        Map<Long, Integer> m = new HashMap<>();
        for (String k : o.getKeySet()) try { m.put(Long.parseLong(k), o.getInteger(k)); } catch (NumberFormatException ignored) { }
        CLIENT.clear();
        CLIENT.putAll(m);
    }

    /** client: ticks to add to the world time for the slave clock at pos */
    public static int clientOffset(BlockPos pos) { Integer v = CLIENT.get(pos.toLong()); return v == null ? 0 : v; }
}
