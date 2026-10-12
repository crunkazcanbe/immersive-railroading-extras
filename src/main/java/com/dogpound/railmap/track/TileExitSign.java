package com.dogpound.railmap.track;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Tunnel evacuation sign (her ChatGPT list #16): the green running-man box sign on the tunnel wall, lit, that tells you
 * how far it is to the nearest way out in each direction - a cross-passage door or a tunnel portal - worked out from
 * where those really are. In an emergency the arrows flash. Maintained light on 12 V, 3 h battery without.
 */
public class TileExitSign extends TrackGear implements TrackBlock.Lit {
    private static final int BATT_SECS = 3 * 3600;
    private int battery = BATT_SECS, level;
    private String left = "", right = "";

    @Override public String wiredName() { return "Exit sign " + Math.floorMod(pos.getX() * 3 + pos.getZ() * 7, 1000); }
    @Override public int light() { return level; }
    @Override public String gearState() { return (level == 0 ? "DARK" : powered() ? "lit" : "on battery") + " · " + left + " | " + right; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("left", left); m.put("right", right); m.put("batteryPct", 100 * battery / BATT_SECS); }

    @Override
    protected void tickGear() {
        if (age % 20 != 0) return;
        int want;
        if (powered()) { want = 9; battery = Math.min(BATT_SECS, battery + 3); }
        else if (battery > 0) { battery--; want = 6; }
        else want = 0;
        if (want != level) { level = want; world.checkLight(pos); controlsChanged(); }
        if (age % 100 == 0 || left.isEmpty()) directions();
        TileTunnelControl c = TileTunnelControl.of(world, pos);
        boolean em = c != null && c.emergency();
        put("man", level > 0 ? 1 : 0);
        put("arl", level > 0 && !left.startsWith("--") ? (em ? 2 : 1) : 0);
        put("arr", level > 0 && !right.startsWith("--") ? (em ? 2 : 1) : 0);
        text("left", level > 0 ? left : "");
        text("right", level > 0 ? right : "");
    }

    private void directions() {
        EnumFacing f = controlFacing();
        EnumFacing l = f.rotateY();
        double bl = Double.MAX_VALUE, br = Double.MAX_VALUE;
        String sl = "--", sr = "--";
        for (TileEntity t : exits()) {
            BlockPos p = t.getPos();
            double dx = p.getX() - pos.getX(), dz = p.getZ() - pos.getZ();
            if (Math.abs(p.getY() - pos.getY()) > 24) continue;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 2 || d > 2048) continue;
            String what = (t instanceof TileCrossPassage ? "EXIT " : "PORTAL ") + Math.round(d) + "M";
            if (dx * l.getXOffset() + dz * l.getZOffset() > 0) { if (d < bl) { bl = d; sl = what; } }
            else if (d < br) { br = d; sr = what; }
        }
        left = sl;
        right = sr;
    }

    private java.util.List<TileEntity> exits() {
        java.util.List<TileEntity> l = new java.util.ArrayList<>(TrackHooks.live(world, TileCrossPassage.class));
        l.addAll(TrackHooks.live(world, TileTunnelPortal.class));
        return l;
    }

    @Override public int controlValue(String id) { return ctl.get(id); }
    @Override protected boolean keepTextUnpowered() { return true; }
    @Override public String controlPress(String id, EntityPlayer who) { directions(); return "§a<- " + left + "   |   " + right + " ->"; }

    @Override protected void save(NBTTagCompound t) { t.setInteger("batt", battery); t.setInteger("lvl", level); }
    @Override protected void load(NBTTagCompound t) { battery = t.hasKey("batt") ? t.getInteger("batt") : BATT_SECS; int old = level; level = t.getInteger("lvl"); if (world != null && world.isRemote && old != level) world.checkLight(pos); }
}
