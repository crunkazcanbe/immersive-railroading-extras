package com.dogpound.railmap.road;

import com.dogpound.railmap.ohle.Reg;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/**
 * A three-aspect road traffic signal head on its pole (batch 5 #3). Place it at the stop line facing the drivers it
 * controls (the lenses face you as you place it). The GROUP selector on the back puts it in phase group A-D of the
 * nearest Traffic Signal Controller (48 blocks), which lights it over the signal cable: red, red + amber, green, amber,
 * flashing amber, or dark when the cabinet is off / unpowered. Road vehicles driving toward its lenses stop at its stop
 * line on red, on red + amber, and on amber when they can; a queue at red calls its group (vehicle actuation), vehicles
 * still arriving on green hold the green on.
 */
public class TileTrafficSignal extends RoadTile {
    static final Reg<TileTrafficSignal> REG = new Reg<>();
    private long ctlPos = Long.MIN_VALUE;
    private int aspect;

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    public int group() { return Math.floorMod(ctl.get("s_grp"), 4); }
    /** Phasing aspect code shown now (server) */
    public int aspect() { return aspect; }
    public EnumFacing facing() { return controlFacing(); }

    /** stop for this (red, red + amber, or amber when able) */
    public boolean stopAspect() { return aspect == TileTrafficController.Phasing.RED || aspect == TileTrafficController.Phasing.RED_AMBER || aspect == TileTrafficController.Phasing.AMBER; }

    public TileTrafficController controller() {
        if (ctlPos != Long.MIN_VALUE && world.isBlockLoaded(net.minecraft.util.math.BlockPos.fromLong(ctlPos))
                && world.getTileEntity(net.minecraft.util.math.BlockPos.fromLong(ctlPos)) instanceof TileTrafficController c && !c.isInvalid()) return c;
        return null;
    }

    @Override
    protected void serverTick() {
        if (age % 100 == 1 || ctlPos == Long.MIN_VALUE && age % 20 == 1) {
            TileTrafficController c = TileTrafficController.nearest(world, pos);
            ctlPos = c == null ? Long.MIN_VALUE : c.getPos().toLong();
        }
        if (age % 2 != 0) return;
        TileTrafficController c = controller();
        aspect = c == null ? TileTrafficController.Phasing.DARK : c.aspect(group());
        boolean flash = (world.getTotalWorldTime() / 10) % 2 == 0;
        put("r", aspect == TileTrafficController.Phasing.RED || aspect == TileTrafficController.Phasing.RED_AMBER);
        put("a", aspect == TileTrafficController.Phasing.AMBER || aspect == TileTrafficController.Phasing.RED_AMBER || aspect == TileTrafficController.Phasing.FLASH_AMBER && flash);
        put("g", aspect == TileTrafficController.Phasing.GREEN);
    }

    @Override
    public String controlPress(String id, net.minecraft.entity.player.EntityPlayer who) {
        if (id.equals("s_grp")) {
            if (who == null) return "§7The group is set at the head itself";
            ctl.set("s_grp", (group() + 1) % 4);
            ctlPos = Long.MIN_VALUE;
            TileTrafficController c = TileTrafficController.nearest(world, pos);
            return "§eSignal head: phase group " + "ABCD".charAt(group()) + (c == null ? " §c- no Traffic Signal Controller within " + TileTrafficController.RANGE + " blocks" : " §7of the controller at " + c.getPos().getX() + "," + c.getPos().getZ());
        }
        return "§7Signal head group " + "ABCD".charAt(group()) + " - " + (controller() == null ? "no controller in range" : controller().ph.stageName());
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("asp", aspect); }
    @Override protected void load(NBTTagCompound t) { aspect = t.getInteger("asp"); }
}
