package com.dogpound.railmap.program;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileTrackCircuit;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * The signal box's memory for one world: 64 wireless channels (with names), the program (rules),
 * and the alarm log. Desks, redstone interfaces and the Signal Box screen all read and write here.
 */
public final class ProgramData extends WorldSavedData {
    public static final String NAME = "irextras_program";
    public static final int CHANNELS = 64;

    public final boolean[] channel = new boolean[CHANNELS + 1];
    public final String[] channelName = new String[CHANNELS + 1];
    public final List<Rule> rules = new ArrayList<>();
    public final List<String> alarms = new ArrayList<>();
    public int nextId = 1;
    /** bumped on every change so open screens know to refresh */
    public int version;

    public ProgramData() { this(NAME); }
    public ProgramData(String n) { super(n); java.util.Arrays.fill(channelName, ""); }

    public static ProgramData get(World world) {
        MapStorage st = world.getPerWorldStorage();
        ProgramData d = (ProgramData) st.getOrLoadData(ProgramData.class, NAME);
        if (d == null) { d = new ProgramData(); st.setData(NAME, d); }
        return d;
    }

    public boolean ch(int n) { return n >= 1 && n <= CHANNELS && channel[n]; }

    public void setChannel(int n, boolean v) {
        if (n < 1 || n > CHANNELS || channel[n] == v) return;
        channel[n] = v;
        changed();
    }

    public void changed() { version++; markDirty(); }

    public Rule rule(int id) {
        for (Rule r : rules) if (r.id == id) return r;
        return null;
    }

    public void alarm(World w, String text, BlockPos at) {
        long t = (w.getWorldTime() + 6000) % 24000;
        alarms.add(0, String.format("%02d:%02d  %s", t / 1000, (t % 1000) * 60 / 1000, text));
        while (alarms.size() > 50) alarms.remove(alarms.size() - 1);
        changed();
        for (EntityPlayer p : w.playerEntities)
            if (at == null || p.getDistanceSq(at) < 256 * 256) p.sendMessage(new TextComponentString("§c§l⚠ SIGNAL BOX ALARM §r§c" + text));
    }

    // ---- the engine: evaluated every half second by ProgramTicker ------------------------------------
    public void run(World w) {
        for (Rule r : rules) {
            if (!r.enabled) continue;
            boolean v;
            try {
                v = test(w, r.cond, r.condPos, r.condChannel, r.radius) && (r.cond2 == null || test(w, r.cond2, r.cond2Pos, r.cond2Channel, r.radius));
            } catch (RuntimeException | LinkageError e) {
                continue;
            }
            int now = v ? 1 : 0;
            boolean edge = now != r.last;
            r.last = now;
            Rule.Act a = v ? r.act : r.elseAct;
            long p = v ? r.actPos : r.elsePos, p2 = v ? r.actPos2 : r.elsePos2;
            int c = v ? r.actChannel : r.elseChannel;
            String text = v ? r.text : r.elseText;
            // a signal held by a WHEN with no ELSE goes back to the track when the WHEN stops being true
            if (!v && edge && a == Rule.Act.NOTHING && r.act.name().startsWith("SIGNAL")) { a = Rule.Act.SIGNAL_AUTO; p = r.actPos; }
            boolean level = a.name().startsWith("SIGNAL");
            if (edge || level) act(w, r, a, p, p2, c, text);
        }
    }

    static boolean test(World w, Rule.Cond c, long lp, int channel, int radius) {
        BlockPos p = BlockPos.fromLong(lp);
        switch (c) {
            case ALWAYS: return true;
            case CHANNEL_ON: return get(w).ch(channel);
            case CHANNEL_OFF: return !get(w).ch(channel);
            case DAYTIME: return w.isDaytime();
            case NIGHT: return !w.isDaytime();
            case REDSTONE: return lp != 0 && w.isBlockLoaded(p) && w.getRedstonePowerFromNeighbors(p) > 0;
            case OCCUPIED: case CLEAR: {
                boolean occ;
                TileEntity te = lp == 0 || !w.isBlockLoaded(p) ? null : w.getTileEntity(p);
                occ = te instanceof TileTrackCircuit tc ? tc.occupied() : trainNear(w, p, 3, false);
                return c == Rule.Cond.OCCUPIED ? occ : !occ;
            }
            case TRAIN_NEAR: return lp != 0 && trainNear(w, p, radius, false);
            case TRAIN_STOPPED: return lp != 0 && trainNear(w, p, radius, true);
            case SIGNAL_RED: case SIGNAL_PROCEED: {
                TileSignalMast m = mast(w, p);
                if (m == null) return false;
                boolean red = m.aspect() == Aspect.STOP || m.aspect() == Aspect.STOP_AND_PROCEED || m.aspect() == Aspect.DARK;
                return c == Rule.Cond.SIGNAL_RED ? red : !red;
            }
            case SWITCH_STRAIGHT: return lp != 0 && Interlocking.stateAt(w, p) == SwitchState.STRAIGHT;
            case SWITCH_TURN: return lp != 0 && Interlocking.stateAt(w, p) == SwitchState.TURN;
            case ROUTE_SET:
                for (Interlocking.Route r : Interlocking.routes(w)) if (r.start.equals(p)) return true;
                return false;
            default: return false;
        }
    }

    static boolean trainNear(World w, BlockPos p, int radius, boolean stopped) {
        cam72cam.mod.world.World u = cam72cam.mod.world.World.get(w);
        if (u == null) return false;
        for (cam72cam.immersiverailroading.entity.EntityRollingStock s : u.getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
            cam72cam.mod.math.Vec3d q = s.getPosition();
            double dx = q.x - p.getX() - 0.5, dy = q.y - p.getY(), dz = q.z - p.getZ() - 0.5;
            if (dx * dx + dz * dz > (double) radius * radius || Math.abs(dy) > 8) continue;
            if (stopped && s instanceof cam72cam.immersiverailroading.entity.EntityMoveableRollingStock m && m.getCurrentSpeed() != null
                    && Math.abs(m.getCurrentSpeed().metric()) > 2) continue;
            return true;
        }
        return false;
    }

    /** a signal by the position picked on the map: the mast itself, or the rail it watches */
    public static TileSignalMast mast(World w, BlockPos p) {
        if (p == null || !w.isBlockLoaded(p)) return null;
        TileEntity te = w.getTileEntity(p);
        if (te instanceof TileSignalMast m) return m;
        return SignalRegistry.mastAt(w.provider.getDimension(), new Vec3i(p.getX(), p.getY(), p.getZ()));
    }

    private void act(World w, Rule r, Rule.Act a, long lp, long lp2, int c, String text) {
        BlockPos p = BlockPos.fromLong(lp);
        String owner = "program#" + r.id;
        switch (a) {
            case NOTHING: return;
            case SIGNAL_STOP: case SIGNAL_RESTRICTING: case SIGNAL_APPROACH: case SIGNAL_CLEAR: case SIGNAL_AUTO: {
                TileSignalMast m = mast(w, p);
                if (m == null) return;
                switch (a) {
                    case SIGNAL_STOP -> m.setRelayControl(true, null);
                    case SIGNAL_RESTRICTING -> m.setRelayControl(false, Aspect.RESTRICTING);
                    case SIGNAL_APPROACH -> m.setRelayControl(false, Aspect.APPROACH);
                    case SIGNAL_CLEAR -> m.setRelayControl(false, Aspect.CLEAR);
                    default -> m.setRelayControl(false, null);
                }
                return;
            }
            case SWITCH_STRAIGHT: case SWITCH_TURN: {
                Vec3i v = new Vec3i(p.getX(), p.getY(), p.getZ());
                boolean ok = Interlocking.claim(w, v, a == Rule.Act.SWITCH_TURN ? SwitchState.TURN : SwitchState.STRAIGHT, owner);
                Interlocking.release(w, v, owner);
                if (!ok) alarm(w, "Rule '" + label(r) + "' could not throw the switch at " + Rule.where(lp) + " (occupied or locked)", p);
                return;
            }
            case SWITCH_AUTO: Interlocking.setAuto(w, p); return;
            case CHANNEL_ON: setChannel(c, true); return;
            case CHANNEL_OFF: setChannel(c, false); return;
            case ROUTE: {
                String res = Interlocking.setRoute(w, p, BlockPos.fromLong(lp2));
                if (res != null && !res.toLowerCase().contains("set")) alarm(w, "Route " + Rule.where(lp) + " -> " + Rule.where(lp2) + ": " + res, p);
                return;
            }
            case CANCEL_ROUTE: Interlocking.cancelRoute(w, p); return;
            case ANNOUNCE:
                for (EntityPlayer pl : w.playerEntities)
                    if (lp == 0 || pl.getDistanceSq(p) < 96 * 96) pl.sendMessage(new TextComponentString("§b📢 §f" + text));
                return;
            case ALARM: alarm(w, text.isEmpty() ? label(r) : text, lp == 0 ? null : p); return;
            default:
        }
    }

    private static String label(Rule r) { return r.name.isEmpty() ? "#" + r.id : r.name; }

    // ---- save ---------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        rules.clear();
        NBTTagList l = t.getTagList("rules", 10);
        for (int i = 0; i < l.tagCount(); i++) rules.add(Rule.read(l.getCompoundTagAt(i)));
        nextId = Math.max(1, t.getInteger("next"));
        int[] ch = t.getIntArray("ch");
        for (int i = 1; i <= CHANNELS; i++) channel[i] = i - 1 < ch.length && ch[i - 1] != 0;
        NBTTagList names = t.getTagList("names", 8);
        for (int i = 0; i < names.tagCount() && i + 1 <= CHANNELS; i++) channelName[i + 1] = names.getStringTagAt(i);
        alarms.clear();
        NBTTagList al = t.getTagList("alarms", 8);
        for (int i = 0; i < al.tagCount(); i++) alarms.add(al.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Rule r : rules) l.appendTag(r.write());
        t.setTag("rules", l);
        t.setInteger("next", nextId);
        int[] ch = new int[CHANNELS];
        for (int i = 1; i <= CHANNELS; i++) ch[i - 1] = channel[i] ? 1 : 0;
        t.setIntArray("ch", ch);
        NBTTagList names = new NBTTagList();
        for (int i = 1; i <= CHANNELS; i++) names.appendTag(new NBTTagString(channelName[i] == null ? "" : channelName[i]));
        t.setTag("names", names);
        NBTTagList al = new NBTTagList();
        for (String s : alarms) al.appendTag(new NBTTagString(s));
        t.setTag("alarms", al);
        return t;
    }
}
