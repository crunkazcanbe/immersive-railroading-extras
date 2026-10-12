package com.dogpound.railmap.cctv;

import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.block.IFurniture;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.wire.WireNet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The CCTV head end: a 9-bay NVR with a 16-port PoE switch in a rack. Every 5 s it walks its wire bus (wires, bundled
 * cable, chases, radio links - WireNet.bus) and adopts the cameras, monitors, desks and other NVRs it reaches: that is
 * the video network. PoE powers cameras up to the switch budget. Hard drives in the bays (real storage) set how much
 * it records: each camera gets a ring of frames on disk. REC: STOP / CONT(inuous) / MOTION (motion + 10 s); LOCK keeps
 * footage (stops recording when full, DISK FULL alarm); FORMAT wipes. Drives changed = the array is rebuilt (wiped).
 */
public class TileNvr extends CctvTile implements IFurniture {
    public final InventoryBasic bays = new InventoryBasic("NVR drive bays", true, 9);
    private final List<BlockPos> cams = new ArrayList<>();
    private String driveKey = "";
    private int poeW, capacity, scanAge;
    private long lastWrite;
    private boolean full;

    public TileNvr() { bays.addInventoryChangeListener(inv -> markDirty()); }

    @Override protected int watts() { return 60 + poeW; }
    @Override public String wiredName() { return "CCTV NVR rack"; }
    @Override public boolean running() { return term.powered() && supplied; }
    private boolean supplied;
    @Override public String gearState() { return !running() ? "down - " + (term.powered() ? mains.what : "no 12 V") : cams.size() + " cameras, " + new String[]{"STOPPED", "RECORDING", "MOTION REC"}[ctl.get("s_rec")] + ", disk " + diskPct() + "%"; }

    public boolean poeOn() { return running() && ctl.get("s_poe") == 1; }
    public int mode() { return ctl.get("s_rec"); }
    public boolean locked() { return ctl.get("k_lock") == 1; }

    /** total recorded frames the drives hold */
    public int capacity() {
        int tb = 0;
        for (int i = 0; i < 9; i++) tb += ItemDrive.tb(bays.getStackInSlot(i));
        return tb * CctvConfig.framesPerTB;
    }

    public int perCamera() { return cams.isEmpty() ? 0 : Math.max(16, capacity / cams.size()); }

    public int diskPct() {
        long used = 0, tot = 0;
        CctvData d = CctvData.get(world);
        for (BlockPos p : cams) {
            if (!(world.getTileEntity(p) instanceof TileCamera c)) continue;
            used += Store.used(world, c.no); tot += Store.slots(world, c.no);
        }
        return tot == 0 ? 0 : (int) (used * 100 / tot);
    }

    @Override
    protected void serverTick() {
        Cctv.NVRS.add(this);
        boolean own = !CctvConfig.needSupply || mainsOk();
        if (supplied != own) { supplied = own; controlsChanged(); }
        if (++scanAge % 100 == (int) (pos.toLong() & 63) % 100) scan();
        if (age % 20 == 3) panel();
    }

    /** walk the bus: adopt everything on it into this network, power cameras over PoE */
    private void scan() {
        if (!running()) return;
        long now = world.getTotalWorldTime();
        List<BlockPos> bus = WireNet.bus(world, pos);
        long net = pos.toLong();
        List<TileEntity> devs = new ArrayList<>();
        for (BlockPos p : bus) {
            TileEntity t = world.getTileEntity(p);
            if (t instanceof TileNvr o && o != this && o.running()) net = Math.min(net, p.toLong());
            if (t instanceof CctvTile) devs.add(t);
        }
        joinNet(net);
        cams.clear();
        poeW = 0;
        String dk = driveKey();
        boolean rebuild = !dk.equals(driveKey) && !driveKey.isEmpty();
        boolean firstScan = driveKey.isEmpty();
        driveKey = dk;
        for (TileEntity t : devs) {
            ((CctvTile) t).joinNet(net);
            if (t instanceof TileCamera c) {
                c.nvr = pos;
                cams.add(t.getPos());
                if (poeOn() && poeW + c.type().watts <= CctvConfig.poeBudget && cams.size() <= 16) { poeW += c.type().watts; c.poeUntil = now + 300; }
            }
        }
        int want = perCamera();
        for (BlockPos p : cams) {
            if (!(world.getTileEntity(p) instanceof TileCamera c) || c.no <= 0) continue;
            int have = Store.slots(world, c.no);
            if (want == 0) continue;
            if (have == 0 || rebuild && !firstScan) Store.format(world, c.no, want);
        }
        if (rebuild && !firstScan) {
            com.dogpound.railmap.alarm.Recorder.event(world, "CCTV NVR: drives changed - array rebuilt, recordings cleared");
            world.playSound(null, pos, ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.6f, 1f);
        }
        markDirty();
    }

    private String driveKey() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 9; i++) b.append(ItemDrive.tb(bays.getStackInSlot(i))).append(',');
        return b.toString();
    }

    public List<BlockPos> cameras() { return cams; }

    /** a camera on this NVR wants to store a frame */
    boolean store(TileCamera c, Snap.Frame f) {
        if (!running() || capacity() == 0 || mode() == 0) return false;
        if (locked() && Store.full(world, c.no)) {
            if (!full) {
                full = true;
                AlarmManager.get(world).raise(world, "cctv:disk:" + pos.toLong(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MAJOR, "CCTV NVR DISK FULL - footage locked, recording stopped", pos, true);
            }
            return false;
        }
        if (Store.slots(world, c.no) == 0) Store.format(world, c.no, perCamera());
        boolean ok = Store.write(world, c.no, f);
        if (ok) lastWrite = world.getTotalWorldTime();
        return ok;
    }

    private void panel() {
        long now = world.getTotalWorldTime();
        boolean on = running();
        for (int i = 0; i < 16; i++) put("p" + (i + 1), on && poeOn() && i < cams.size());
        for (int i = 0; i < 9; i++) put("d" + (i + 1), on && !bays.getStackInSlot(i).isEmpty() && now - lastWrite < 60);
        put("poe", poeOn());
        put("led_pwr", on);
        put("led_rec", on && mode() > 0 && capacity() > 0);
        put("led_hdd", on && now - lastWrite < 40);
        put("led_alarm", on && full);
        int disk = on ? diskPct() : 0;
        put("g_disk", disk);
        put("g_poe", poeOn() ? poeW : 0);
        text("poew", poeOn() ? poeW + " W" : "OFF");
        text("disp1", capacity() == 0 ? "NO DRIVES - FIT HDD" : String.format(Locale.ROOT, "%d CAM  %d TB  %d%%", cams.size(), capacity() / CctvConfig.framesPerTB, disk));
        int secs = perCamera() * CctvConfig.recordSeconds;
        text("disp2", new String[]{"REC STOP", "REC CONT", "REC MOTION"}[mode()] + (locked() ? " LOCK" : "") + "  " + secs / 60 + " MIN/CAM");
        if (full && (!locked() || cams.stream().noneMatch(p -> world.getTileEntity(p) instanceof TileCamera c && Store.full(world, c.no)))) {
            full = false;
            AlarmManager.get(world).clear("cctv:disk:" + pos.toLong());
        }
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "bays":
                if (who == null) return "§7Drives are fitted by hand";
                who.openGui(RailMap.instance, RailMap.GUI_OFFICE_STORE, world, pos.getX(), pos.getY(), pos.getZ());
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.drawer_open"), SoundCategory.BLOCKS, 0.6f, 1.2f);
                return "";
            case "format":
                if (!running()) return "§cThe NVR is down";
                if (locked()) return "§cFootage is LOCKED - turn the lock key to OPEN first";
                for (BlockPos p : cams) if (world.getTileEntity(p) instanceof TileCamera c && c.no > 0) Store.format(world, c.no, perCamera());
                world.playSound(null, pos, ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 0.8f);
                return "§eNVR formatted: " + cams.size() + " camera rings, " + perCamera() + " frames each";
            case "ack":
                for (CctvData.Cam c : CctvData.get(world).cams.values()) if (c.net == net()) ackCam(c.no, who);
                int disk = idOf("cctv:disk:" + pos.toLong());
                if (disk > 0) AlarmManager.get(world).ack(disk, who == null ? "console" : who.getName());
                return "§eCCTV alarms acknowledged";
            case "test": return lampTest();
            default: return null;
        }
    }

    private int idOf(String key) {
        for (AlarmManager.Alarm a : AlarmManager.get(world).all()) if (a.key.equals(key)) return a.id;
        return Integer.MIN_VALUE;
    }

    private void ackCam(int no, EntityPlayer who) {
        AlarmManager am = AlarmManager.get(world);
        for (AlarmManager.Alarm a : am.all())
            if (a.key.startsWith("cctv:") && a.key.endsWith(":" + no) && !a.acked) am.ack(a.id, who == null ? "console" : who.getName());
    }

    // ---- storage ---------------------------------------------------------------------------------------------------
    @Override public IInventory storage(int k) { return k == 0 ? bays : null; }
    @Override public String storeName(int k) { return "NVR drive bays"; }
    @Override public int storeSize(int k) { return 9; }
    @Override public void dropAll() { InventoryHelper.dropInventoryItems(world, pos, bays); }

    @Override
    protected void saveMore(NBTTagCompound t) {
        t.setTag("bays", TileFurniture.save(bays));
        t.setString("drives", driveKey);
        t.setBoolean("sup", supplied);
        t.setBoolean("full", full);
    }

    @Override
    protected void loadMore(NBTTagCompound t) {
        TileFurniture.load(bays, t.getCompoundTag("bays"));
        driveKey = t.getString("drives");
        supplied = t.getBoolean("sup");
        full = t.getBoolean("full");
    }

    @Override public void invalidate() { super.invalidate(); Cctv.NVRS.remove(this); }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(1, 2, 1); }

    public static boolean isDrive(ItemStack s) { return ItemDrive.tb(s) > 0; }
}
