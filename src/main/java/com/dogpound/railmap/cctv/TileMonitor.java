package com.dogpound.railmap.cctv;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/**
 * A 1x1 CCTV wall monitor (or the small ceiling-hung platform monitor). Monitors of the same kind placed side by side
 * facing the same way merge into a video wall (up to 4 x 4): the bottom-left one (as you face it) is the wall's
 * controller and the whole wall shows its picture, split 1 / 4 / 9 ways; any screen's CH+ / CH- / SPLIT / MENU works the
 * wall. Needs 12 V on its terminal and mains (a grid cable on any screen of the wall feeds the wall's power strip).
 * Playback: a monitor can play back an NVR recording (timeline, speed, pause) - the GUI drives it.
 */
public class TileMonitor extends CctvTile {
    public static final int[] SPEEDS = {-16, -8, -4, -2, -1, 1, 2, 4, 8, 16};
    public int no;
    public int split = 1, sel;
    public final int[] ch = new int[9];
    public boolean overlay = true;
    /** fisheye cameras: show the dewarped (flat) picture instead of the round lens image */
    public boolean dewarp;
    public int mode;                 // 0 live, 1 playback
    public long pbT;
    public int pbSpeed = 5;          // index into SPEEDS (5 = 1x)
    public boolean pbPause;
    private boolean supplied;

    public boolean ceiling() { return world != null && world.getBlockState(pos).getBlock() instanceof CctvBlock b && b.kind == CctvBlock.Kind.CEILING_MONITOR; }

    @Override protected int watts() { return ceiling() ? 25 : 45; }
    @Override public String wiredName() { return (ceiling() ? "Platform monitor" : "CCTV monitor") + (no > 0 ? " MON " + no : ""); }
    @Override public boolean running() { return term.powered() && supplied; }
    @Override public String gearState() { return !term.powered() ? "dark - no 12 V" : !supplied ? "dark - " + mains.what : mode == 1 ? "playback CAM " + ch[sel] : "CAM " + ch[0] + (split > 1 ? " split " + split : ""); }

    // ---- video wall -------------------------------------------------------------------------------------------------
    /** {masterX, masterY, masterZ, cols, rows, myCol, myRow} - a lone monitor is its own 1 x 1 wall */
    public static int[] wall(IBlockAccess w, BlockPos p) {
        TileEntity me = w.getTileEntity(p);
        if (!(me instanceof TileMonitor m)) return new int[]{p.getX(), p.getY(), p.getZ(), 1, 1, 0, 0};
        EnumFacing f = m.facing();
        EnumFacing right = f.rotateYCCW();
        int left = 0, down = 0;
        while (left < 3 && same(w, p.offset(right.getOpposite(), left + 1), m, f)) left++;
        while (down < 3 && same(w, p.offset(right.getOpposite(), left).down(down + 1), m, f)) down++;
        BlockPos o = p.offset(right.getOpposite(), left).down(down);
        int cols = 1, rows = 1;
        while (cols < 4 && same(w, o.offset(right, cols), m, f)) cols++;
        while (rows < 4 && same(w, o.up(rows), m, f)) rows++;
        for (int c = 0; c < cols; c++) for (int r = 0; r < rows; r++) if (!same(w, o.offset(right, c).up(r), m, f)) return new int[]{p.getX(), p.getY(), p.getZ(), 1, 1, 0, 0};
        if (left >= cols || down >= rows) return new int[]{p.getX(), p.getY(), p.getZ(), 1, 1, 0, 0};
        return new int[]{o.getX(), o.getY(), o.getZ(), cols, rows, left, down};
    }

    private static boolean same(IBlockAccess w, BlockPos q, TileMonitor m, EnumFacing f) {
        TileEntity t = w.getTileEntity(q);
        return t instanceof TileMonitor o && o.facing() == f && o.ceiling() == m.ceiling();
    }

    public TileMonitor master() {
        int[] wl = wall(world, pos);
        TileEntity t = world.getTileEntity(new BlockPos(wl[0], wl[1], wl[2]));
        return t instanceof TileMonitor m ? m : this;
    }

    public boolean isMaster() { return master() == this; }

    /** cameras this monitor can call up: on its network (or wireless train cameras), in its number order */
    public List<CctvData.Cam> visible() { return Cctv.visible(CctvData.get(world), net()); }

    // ---- server ------------------------------------------------------------------------------------------------------
    @Override
    protected void serverTick() {
        CctvData d = CctvData.get(world);
        if (no <= 0) { no = d.allocMon(); controlsChanged(); }
        boolean own = !CctvConfig.needSupply || mainsOk();
        if (age % 20 != 11) return;
        boolean was = supplied;
        supplied = own;
        if (!own) {                                             // the wall's power strip: any screen of the wall with mains feeds it
            int[] wl = wall(world, pos);
            EnumFacing right = facing().rotateYCCW();
            BlockPos o = new BlockPos(wl[0], wl[1], wl[2]);
            for (int c = 0; c < wl[3] && !supplied; c++) for (int r = 0; r < wl[4]; r++)
                if (world.getTileEntity(o.offset(right, c).up(r)) instanceof TileMonitor m && m != this && m.mains.ok()) { supplied = true; break; }
        }
        if (isMaster() && mode == 1 && !pbPause && running()) { pbT += SPEEDS[pbSpeed] * 20L; long now = world.getTotalWorldTime(); if (pbT > now) { pbT = now; mode = 0; } controlsChanged(); }
        TileMonitor m = master();
        int cam = m.ch[Math.min(m.sel, 8)];
        CctvData.Cam c = d.cam(cam);
        put("led_pwr", term.powered() && supplied);
        put("led_sig", c != null && c.streams() && Cctv.canSee(c, m.net()));
        put("led_rec", c != null && c.rec);
        text("ch", !running() ? "" : (m.mode == 1 ? "PB " : "CH ") + cam);
        if (was != supplied) controlsChanged();
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        TileMonitor m = master();
        if (m != this) return m.controlPress(id, who);
        if (!running()) return "§7The monitor is dark - " + (term.powered() ? mains.what : "no 12 V");
        switch (id) {
            case "chup": case "chdn": {
                List<CctvData.Cam> v = visible();
                if (v.isEmpty()) return "§cNo cameras on this monitor's network";
                int at = -1;
                for (int i = 0; i < v.size(); i++) if (v.get(i).no == ch[sel]) at = i;
                at = Math.floorMod(at + (id.equals("chup") ? 1 : -1), v.size());
                ch[sel] = v.get(at).no;
                mode = 0;
                controlsChanged();
                return "§aMON " + no + (split > 1 ? " tile " + (sel + 1) : "") + ": CAM " + ch[sel] + " " + v.get(at).name;
            }
            case "split": split = split == 1 ? 4 : split == 4 ? 9 : 1; sel = Math.min(sel, split - 1); controlsChanged(); return "§aMON " + no + ": split " + split;
            case "menu": sel = (sel + 1) % split; controlsChanged(); return "§7MON " + no + ": tile " + (sel + 1) + " selected (CH+/CH- change it) - right-click the screen for the menu";
            default: return null;
        }
    }

    /** GUI / desk operations, already on the master */
    public String op(String what, String arg) {
        long now = world.getTotalWorldTime();
        try {
            switch (what) {
                case "cam": { int t = Integer.parseInt(arg.split(",")[0]); int c = Integer.parseInt(arg.split(",")[1]); if (t < 0 || t > 8) return null; ch[t] = c; sel = t; mode = 0; break; }
                case "split": { int s = Integer.parseInt(arg); split = s == 4 || s == 9 ? s : 1; sel = Math.min(sel, split - 1); break; }
                case "sel": sel = Math.max(0, Math.min(split - 1, Integer.parseInt(arg))); break;
                case "overlay": overlay = !overlay; break;
                case "dewarp": dewarp = !dewarp; break;
                case "live": mode = 0; break;
                case "play": { if (mode == 0) { mode = 1; long[] r = Store.range(world, ch[sel]); pbT = r == null ? now : Math.max(r[0], now - 1200); } pbPause = false; break; }
                case "pause": pbPause = !pbPause; break;
                case "speed": pbSpeed = Math.max(0, Math.min(SPEEDS.length - 1, Integer.parseInt(arg))); break;
                case "seek": { mode = 1; pbT = Math.min(now, Long.parseLong(arg)); break; }
                case "step": { mode = 1; pbPause = true; pbT += Long.parseLong(arg) * 20L * CctvConfig.recordSeconds; break; }
                default: return null;
            }
        } catch (RuntimeException e) { return "§cBad value"; }
        controlsChanged();
        return "";
    }

    @Override
    protected void saveMore(NBTTagCompound t) {
        t.setInteger("no", no); t.setInteger("split", split); t.setInteger("sel", sel); t.setIntArray("ch", ch); t.setBoolean("ovl", overlay); t.setBoolean("dewarp", dewarp);
        t.setInteger("mode", mode); t.setLong("pbT", pbT); t.setInteger("pbSp", pbSpeed); t.setBoolean("pbP", pbPause); t.setBoolean("sup", supplied);
    }

    @Override
    protected void loadMore(NBTTagCompound t) {
        no = t.getInteger("no"); split = Math.max(1, t.getInteger("split")); sel = t.getInteger("sel");
        int[] c = t.getIntArray("ch");
        java.util.Arrays.fill(ch, 0);
        System.arraycopy(c, 0, ch, 0, Math.min(9, c.length));
        overlay = !t.hasKey("ovl") || t.getBoolean("ovl");
        dewarp = t.getBoolean("dewarp");
        mode = t.getInteger("mode"); pbT = t.getLong("pbT"); pbSpeed = t.hasKey("pbSp") ? t.getInteger("pbSp") : 5; pbPause = t.getBoolean("pbP"); supplied = t.getBoolean("sup");
    }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(4); }
}
