package com.dogpound.railmap.safety;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import java.util.HashMap;
import java.util.Map;

/**
 * Axle Counter Head (her ChatGPT list #19): a double magnetic wheel sensor clamped to the rail web. Each wheel flange
 * passes sensor A then B (or B then A), so the head knows the direction: the arrow on its casting points the way it
 * counts "up". Place it beside or under the track with the arrow along the track. It watches every real wheelset
 * (see AxleWatch) and counts the ones that cross its plane: up = moving the way the arrow points, down = against it.
 * Evaluators read the counts. Needs 12 V; unpowered, burnt or with a lab fault it reports FAULT and every section it
 * bounds goes DISTURBED. The LEDs on the junction box: power, sensor A, sensor B (flash as wheels go over), fault.
 */
public class TileAxleHead extends SafetyTile implements IPickable {
    /** running totals since placed (wrap-safe ints), what evaluators difference */
    public int up, down;
    private int flashA, flashB;
    private long lastAxleTick;
    private final Map<Long, Double> was = new HashMap<>();

    @Override public String pickName() { return "Axle counter head"; }
    @Override public String wiredName() { return "Axle counter head"; }

    public boolean healthy() { return powered() && !term.burnt && !FaultLab.has(world, FaultLab.Kind.AXLE_HEAD, pos); }

    @Override public String gearState() {
        return !powered() ? "FAULT - no 12 V" : !healthy() ? "FAULT - sensor failed" : "ok · " + up + " up / " + down + " down";
    }

    @Override public int wiredLamp() { return healthy() ? 1 : 3; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("up", up); m.put("down", down); m.put("healthy", healthy()); }

    public EnumFacing facing() {
        net.minecraft.block.state.IBlockState s = world.getBlockState(pos);
        return s.getBlock() instanceof BlockStationDevice ? s.getValue(BlockStationDevice.FACING) : EnumFacing.NORTH;
    }

    @Override
    protected void serverTick() {
        boolean ok = healthy();
        if (ok && age % 2 == 0) count();
        else if (!ok) was.clear();
        if (flashA > 0) flashA--;
        if (flashB > 0) flashB--;
        put("pwr", powered());
        put("fault", !ok);
        put("sa", flashA > 0);
        put("sb", flashB > 0);
    }

    /** every axle whose path since the last look crossed this head's plane, near the head, counts once */
    private void count() {
        EnumFacing f = facing();
        // the arrow on the casting points out of the model's front (the block's facing): "up" = moving that way
        double fx = f.getXOffset(), fz = f.getZOffset();
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5, cy = pos.getY();
        Map<Long, Double> next = new HashMap<>();
        for (AxleWatch.Axle a : AxleWatch.axles(world)) {
            double dx = a.x - cx, dz = a.z - cz;
            if (Math.abs(a.y - cy) > 3 || dx * dx + dz * dz > 20 * 20) continue;
            double along = dx * fx + dz * fz, across = Math.abs(dx * -fz + dz * fx);
            long k = a.key();
            Double before = was.get(k);
            next.put(k, along);
            if (before == null || across > 2.6) continue;
            if (before < 0 && along >= 0) { up++; flashA = 3; flashB = 5; lastAxleTick = world.getTotalWorldTime(); markDirty(); }
            else if (before >= 0 && along < 0) { down++; flashB = 3; flashA = 5; lastAxleTick = world.getTotalWorldTime(); markDirty(); }
        }
        was.clear();
        was.putAll(next);
    }

    public long lastAxle() { return lastAxleTick; }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        return "";
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("up", up); t.setInteger("down", down); }
    @Override protected void load(NBTTagCompound t) { up = t.getInteger("up"); down = t.getInteger("down"); }
}
