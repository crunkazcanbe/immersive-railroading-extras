package com.dogpound.railmap.cine;

import com.dogpound.railmap.infra.InfraTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A trackside TV camera on a tripod (batch 5 #10): broadcast camera body with zoom lens, viewfinder, robotic pan / tilt
 * head that follows the nearest train, the CAM number thumbwheel (1-8), red tally lamps (ON AIR: a director desk has it
 * on program) and green preview lamp, battery / 12 V. Photo mode's TRACKSIDE cam and the director desk's AUTO cut to
 * the nearest working tripod as a train passes - like TV coverage. No 12 V = no picture (it is skipped).
 */
public class TileCamTripod extends InfraTile {
    public static final Set<TileCamTripod> ALL = Collections.newSetFromMap(new WeakHashMap<>());

    public TileCamTripod() { super(null); }

    public int number() { return 1 + Math.max(0, Math.min(7, ctl.get("s_no"))); }

    @Override public String wiredName() { return "Trackside TV camera " + number(); }
    @Override public String gearState() { return (powered() ? "CAM " + number() + " ready" : "no 12 V - no picture") + (ctl.get("tally") == 1 ? " · ON AIR" : ""); }

    /** the lens, where the picture is taken from */
    public Vec3d lens() { return new Vec3d(pos.getX() + 0.5, pos.getY() + 1.55, pos.getZ() + 0.5); }

    /** can the lens see (x, y, z) - no solid block in between */
    public boolean sees(double x, double y, double z) {
        Vec3d a = lens(), b = new Vec3d(x, y, z);
        RayTraceResult r = world.rayTraceBlocks(a.add(b.subtract(a).normalize().scale(0.8)), b, false, true, false);
        return r == null || r.typeOfHit != RayTraceResult.Type.BLOCK;
    }

    @Override
    protected void serverTick() {
        ALL.add(this);
        if (age % 10 != 0) return;
        boolean on = powered();
        put("l_pwr", on);
        // tally: on program / preview of any director desk
        boolean pgm = false, pvw = false;
        for (TileDirectorDesk d : TileDirectorDesk.ALL)
            if (d.getWorld() == world && !d.isInvalid() && d.live() && d.getPos().distanceSq(pos) < 512 * 512) {
                pgm |= d.resolved(d.program) == number();
                pvw |= d.resolved(d.preview) == number();
            }
        put("tally", on && pgm);
        put("tally_f", on && pgm);
        put("pvw", on && pvw && !pgm);
        text("d_no", on ? "CAM " + number() : "");
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        if (id.equals("test")) return lampTest();
        if (id.equals("s_no")) { ctl.set("s_no", (ctl.get("s_no") + 1) % 8); return "§eCamera number " + number(); }
        return null;
    }

    @Override protected void store(net.minecraft.nbt.NBTTagCompound t) {}
    @Override protected void restore(net.minecraft.nbt.NBTTagCompound t) {}
}
