package com.dogpound.railmap.road;

import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.ohle.Reg;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Inductive detection loop (batch 5 #3 / #4): a loop sawn into the road surface with its detector in the pit beside
 * it. LOOP selector: TRAM CALL = a tram passing over it asks the nearest Traffic Signal Controller for tram priority
 * (put it 50-100 m before the junction, or between the rails / beside the street track); TRAM CANCEL = the check-out
 * loop after the junction, ends the tram stage early once the tram is through; VEH A-D = a vehicle loop at a stop line:
 * a road vehicle on it calls that phase group (and holds its green on while vehicles keep coming). DET lights while
 * something is over it; the counter counts detections. Fed from the controller (dark without one).
 */
public class TileTramLoop extends RoadTile {
    static final Reg<TileTramLoop> REG = new Reg<>();
    static final String[] MODES = {"TRAM CALL", "TRAM CANCEL", "VEH A", "VEH B", "VEH C", "VEH D"};
    private long ctlPos = Long.MIN_VALUE;
    private boolean occ;
    private int count;

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    public int mode() { return Math.floorMod(ctl.get("s_mode"), MODES.length); }

    TileTrafficController controller() {
        return ctlPos != Long.MIN_VALUE && world.getTileEntity(net.minecraft.util.math.BlockPos.fromLong(ctlPos)) instanceof TileTrafficController c && !c.isInvalid() ? c : null;
    }

    @Override
    protected void serverTick() {
        if (age % 100 == 1 || ctlPos == Long.MIN_VALUE && age % 20 == 1) {
            TileTrafficController c = TileTrafficController.nearest(world, pos);
            ctlPos = c == null ? Long.MIN_VALUE : c.getPos().toLong();
        }
        if (age % 5 != 0) return;
        TileTrafficController c = controller();
        boolean live = c != null && c.live();
        int m = mode();
        boolean now = live && (m < 2 ? tramOver() : !world.getEntitiesWithinAABB(EntityRoadVehicle.class, new AxisAlignedBB(pos).grow(0.8, 1.5, 0.8)).isEmpty());
        if (now && !occ) {
            count++;
            markDirty();
            if (m == 0) c.tramCall();
            else if (m == 1) c.tramCancel();
            else c.vehCall(m - 2);
        }
        if (now && m >= 2) c.extend(m - 2);
        occ = now;
        put("det", now);
        put("pwr", live);
        text("cnt", String.valueOf(count % 10000));
    }

    private boolean tramOver() {
        for (TrainNode t : com.dogpound.railmap.server.TrainTracker.latest(world)) {
            if (Math.abs(t.y - pos.getY()) > 4) continue;
            double dx = t.x - pos.getX() - 0.5, dz = t.z - pos.getZ() - 0.5;
            double r = 3.5 + Math.abs(t.speedKmh) / 3.6;                  // positions come once a second: a fast tram must not jump over it
            if (dx * dx + dz * dz < r * r && TramMode.isTram(t.name, t.tag)) return true;
        }
        return false;
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (!id.equals("s_mode")) return null;
        if (who == null) return "§7The loop mode is set at its detector";
        ctl.set("s_mode", (mode() + 1) % MODES.length);
        return "§eLoop detector: " + MODES[mode()];
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("cnt", count); }
    @Override protected void load(NBTTagCompound t) { count = t.getInteger("cnt"); }
}
