package com.dogpound.railmap.cctv;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.material.MapColor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * The CCTV server: the round-robin budget of ray-cast frames (motion, tamper, recording), train cameras, the directory
 * sync to clients, and every request a client makes (snapshots, playback frames, timelines, set-up, monitor menus,
 * export to a map).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Cctv {
    private Cctv() {}

    public static final Set<TileCamera> CAMS = Collections.newSetFromMap(new WeakHashMap<>());
    public static final Set<TileNvr> NVRS = Collections.newSetFromMap(new WeakHashMap<>());
    public static final Set<TileCctvDesk> DESKS = Collections.newSetFromMap(new WeakHashMap<>());
    /** latest server frame per camera number */
    public static final Map<Integer, Snap.Frame> LATEST = new HashMap<>();
    private static int sentVersion = -1;
    private static final Map<String, Long> RATE = new HashMap<>();

    // ---- lookups ------------------------------------------------------------------------------------------------------
    /** can a device on network `net` see this camera */
    public static boolean canSee(CctvData.Cam c, long net) {
        if (c.kind() == CamType.TRAIN || c.kind() == CamType.DRONE || c.kind() == CamType.CRANE || !CctvConfig.requireNetwork) return true;   // LTE
        return net != 0 && c.net == net;
    }

    public static List<CctvData.Cam> visible(CctvData d, long net) {
        List<CctvData.Cam> o = new ArrayList<>();
        for (CctvData.Cam c : d.cams.values()) if (canSee(c, net)) o.add(c);
        return o;
    }

    public static TileCamera camTile(World w, int no) {
        for (TileCamera t : CAMS) if (t.no == no && t.getWorld() == w && !t.isInvalid()) return t;
        return null;
    }

    public static TileMonitor monitor(World w, int no) {
        for (TileEntity t : w.loadedTileEntityList) if (t instanceof TileMonitor m && m.no == no && !t.isInvalid()) return m.master();
        return null;
    }

    public static List<TileNvr> nvrs(World w, long net) {
        List<TileNvr> o = new ArrayList<>();
        for (TileNvr n : NVRS) if (n.getWorld() == w && !n.isInvalid() && (net == 0 ? false : n.net() == net)) o.add(n);
        return o;
    }

    static void callUp(World w, long net, int cam, String why) {
        for (TileCctvDesk d : DESKS) if (d.getWorld() == w && !d.isInvalid() && (d.net() == net || !CctvConfig.requireNetwork)) d.callUp(cam, why);
    }

    /** a camera's frame: record it on its NVR if that NVR wants it now */
    static void record(TileCamera c, Snap.Frame f, long now) {
        CctvData.Cam e = CctvData.get(c.getWorld()).cam(c.no);
        boolean rec = false;
        if (c.nvr != null && c.getWorld().getTileEntity(c.nvr) instanceof TileNvr n && n.net() == c.net()) {
            boolean want = n.mode() == 1 || n.mode() == 2 && e != null && now - e.lastMotion < 200 || c.recUntil > now;
            if (want && now >= c.nextRec) {
                rec = n.store(c, f);
                c.nextRec = now + CctvConfig.recordSeconds * 20L;
            } else rec = e != null && e.rec && now < c.nextRec + 40;
        }
        if (e != null && e.rec != rec) { e.rec = rec; CctvData.get(c.getWorld()).changed(); }
    }

    // ---- ticking ------------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.world.isRemote) return;
        World w = ev.world;
        long now = w.getTotalWorldTime();
        // the frame budget: the cameras longest overdue first
        List<TileCamera> due = new ArrayList<>();
        for (TileCamera t : CAMS) if (t.getWorld() == w && !t.isInvalid() && t.nextFrame <= now) due.add(t);
        due.sort(Comparator.comparingLong(t -> t.nextFrame));
        for (int i = 0; i < Math.min(CctvConfig.framesPerTick, due.size()); i++) {
            try { due.get(i).capture(now); } catch (RuntimeException ex) { due.get(i).nextFrame = now + 200; RailMap.LOG.warn("[IR Extras] CCTV frame failed: {}", ex.toString()); }
        }
        if (now % 20 != 13) return;
        CctvData d = CctvData.get(w);
        int dim = w.provider.getDimension();
        for (CctvData.Cam c : d.cams.values()) {
            if (c.dim != dim) continue;
            int st = c.status;
            if (!c.train.isEmpty()) {
                double[] p = TrainCams.pose(w, c.train, 1f);
                if (p != null) { c.x = p[0]; c.y = p[1]; c.z = p[2]; c.yaw = (float) p[3]; c.pitch = (float) p[4]; c.fov = c.kind().fov; }
                c.status = p == null ? CctvData.UNLOADED : CctvData.ONLINE;
            } else if (!w.isBlockLoaded(BlockPos.fromLong(c.pos))) c.status = CctvData.UNLOADED;
            if (st != c.status) d.changed();
        }
        if (dim == 0 && d.version != sentVersion) {
            sentVersion = d.version;
            RailMap.NETWORK.sendToAll(new PacketCctvDir(d));
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP p) RailMap.NETWORK.sendTo(new PacketCctvDir(CctvData.get(p.world)), p);
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload e) {
        if (!e.getWorld().isRemote && e.getWorld().provider.getDimension() == 0) { Store.forget(); LATEST.clear(); sentVersion = -1; }
    }

    /** an alarm anywhere bookmarks the recordings of the cameras that can see the place (AlarmManager hook) */
    public static void onAlarm(World w, String key, String text, BlockPos at) {
        if (w == null || w.isRemote || at == null || key.startsWith("cctv:")) return;
        CctvData d = CctvData.get(w);
        long now = w.getTotalWorldTime();
        int dim = w.provider.getDimension();
        for (CctvData.Cam c : d.cams.values())
            if (c.dim == dim && c.streams() && at.distanceSq(c.x, c.y, c.z) < 48 * 48) d.mark(c.no, now, "ALARM " + text);
    }

    // ---- client requests ---------------------------------------------------------------------------------------------
    static void handle(EntityPlayerMP p, PacketCctvOp m) {
        World w = p.world;
        CctvData d = CctvData.get(w);
        switch (m.op) {
            case PacketCctvOp.SNAP -> {
                if (!rate(p, "s" + m.cam, 15)) return;
                CctvData.Cam c = d.cam(m.cam);
                if (c == null) return;
                Snap.Frame f = LATEST.get(m.cam);
                long now = w.getTotalWorldTime();
                World cw = DimensionManager.getWorld(c.dim);
                if ((f == null || now - f.t > 60) && cw != null && c.streams()) {
                    if (!c.train.isEmpty()) {
                        double[] q = TrainCams.pose(cw, c.train, 1f);
                        if (q != null) { f = Snap.cast(cw, q[0], q[1], q[2], (float) q[3], (float) q[4], c.fov, null, new int[0][]); LATEST.put(c.no, f); }
                    } else {
                        TileCamera t = camTile(cw, c.no);
                        if (t != null && t.running()) { t.capture(cw.getTotalWorldTime()); f = LATEST.get(c.no); }
                    }
                }
                send(p, 0, c.no, f, 0, 0, c.streams() ? "" : CctvData.STATUS[c.status]);
            }
            case PacketCctvOp.PB_FRAME -> {
                if (!rate(p, "p" + m.cam, 3)) return;
                send(p, 1, m.cam, Store.read(w, m.cam, m.t), 0, 0, "");
            }
            case PacketCctvOp.RANGE -> {
                long[] r = Store.range(w, m.cam);
                StringBuilder b = new StringBuilder();
                for (CctvData.Mark k : d.marks) if (k.cam == m.cam && b.length() < 3000) b.append(k.t).append(':').append(k.text.replace('|', ' ')).append('|');
                send(p, 2, m.cam, null, r == null ? 0 : r[0], r == null ? 0 : r[1], b.toString());
            }
            case PacketCctvOp.CONFIG -> {
                BlockPos at = BlockPos.fromLong(m.pos);
                if (p.getDistanceSq(at) > 24 * 24) return;
                if (w.getTileEntity(at) instanceof TileCamera c) p.sendStatusMessage(new net.minecraft.util.text.TextComponentString(c.configure(m.arg, p)), true);
            }
            case PacketCctvOp.MONITOR -> {
                BlockPos at = BlockPos.fromLong(m.pos);
                if (p.getDistanceSq(at) > 48 * 48 || !(w.getTileEntity(at) instanceof TileMonitor mon)) return;
                int k = m.arg.indexOf('|');
                String r = mon.master().op(k < 0 ? m.arg : m.arg.substring(0, k), k < 0 ? "" : m.arg.substring(k + 1));
                if (r != null && !r.isEmpty()) p.sendStatusMessage(new net.minecraft.util.text.TextComponentString(r), true);
            }
            case PacketCctvOp.EXPORT -> {
                BlockPos at = BlockPos.fromLong(m.pos);
                if (p.getDistanceSq(at) > 48 * 48 || !(w.getTileEntity(at) instanceof TileMonitor mon0)) return;
                TileMonitor mon = mon0.master();
                int cam = mon.ch[Math.min(mon.sel, 8)];
                Snap.Frame f = mon.mode == 1 ? Store.read(w, cam, mon.pbT) : LATEST.get(cam);
                p.sendStatusMessage(new net.minecraft.util.text.TextComponentString(export(p, f, cam)), true);
            }
            case PacketCctvOp.CONSOLE -> p.sendStatusMessage(new net.minecraft.util.text.TextComponentString(CctvConsole.op(w, m.arg, p)), true);
            default -> { }
        }
    }

    private static boolean rate(EntityPlayerMP p, String k, int ticks) {
        String key = p.getUniqueID() + k;
        long now = p.world.getTotalWorldTime();
        Long last = RATE.get(key);
        if (last != null && now - last < ticks) return false;
        RATE.put(key, now);
        if (RATE.size() > 4000) RATE.clear();
        return true;
    }

    static void send(EntityPlayerMP p, int kind, int cam, Snap.Frame f, long lo, long hi, String info) {
        RailMap.NETWORK.sendTo(new PacketCctvFrame(kind, cam, f, lo, hi, info), p);
    }

    /** the picture on a filled map (uses an empty map from the player's inventory) */
    static String export(EntityPlayer p, Snap.Frame f, int cam) {
        if (f == null) return "§cNothing to export - no frame for CAM " + cam;
        int slot = -1;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) if (p.inventory.getStackInSlot(i).getItem() == Items.MAP) { slot = i; break; }
        if (slot < 0 && !p.capabilities.isCreativeMode) return "§cYou need an empty map in your inventory";
        World w = p.world;
        ItemStack map = new ItemStack(Items.FILLED_MAP, 1, w.getUniqueDataId("map"));
        String id = "map_" + map.getMetadata();
        MapData md = new MapData(id);
        md.scale = 0;
        md.xCenter = 29_000_000; md.zCenter = 29_000_000;                     // far away: the map never redraws terrain over the still
        md.dimension = w.provider.getDimension();
        md.trackingPosition = false;
        int ox = 0, oy = (128 - 128 * f.h / f.w) / 2;
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                int sy = (y - oy) * f.w / 128;
                byte b = 0;
                if (sy >= 0 && sy < f.h) b = nearest(Snap.rgb(f.px[sy * f.w + Math.min(f.w - 1, (x - ox) * f.w / 128)]));
                md.colors[x + y * 128] = b;
            }
        md.markDirty();
        w.setData(id, md);
        if (slot >= 0) p.inventory.decrStackSize(slot, 1);
        if (!p.addItemStackToInventory(map)) p.dropItem(map, false);
        CctvData.get(w).mark(cam, w.getTotalWorldTime(), "EXPORTED TO MAP");
        return "§aCAM " + cam + " still exported to a map";
    }

    private static byte nearest(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, best = 0, bd = Integer.MAX_VALUE;
        for (int i = 1; i < MapColor.COLORS.length; i++) {
            MapColor m = MapColor.COLORS[i];
            if (m == null) continue;
            for (int s = 0; s < 4; s++) {
                int c = m.getMapColor(s), dr = ((c >> 16) & 255) - r, dg = ((c >> 8) & 255) - g, db = (c & 255) - b;
                int dd = dr * dr + dg * dg + db * db;
                if (dd < bd) { bd = dd; best = i * 4 + s; }
            }
        }
        return (byte) best;
    }

    public static WorldServer world0() { return DimensionManager.getWorld(0); }
}
