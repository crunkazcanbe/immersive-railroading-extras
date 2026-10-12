package com.dogpound.railmap.road;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * A field unit wired back to its controller cabinet, as on a real street: traffic signal heads, the pedestrian push
 * button unit, tram signals, detection loops and the platform edge lights. It has no supply of its own - the cabinet
 * (TileTrafficController / TileTramStop, 12 V + mains) feeds its lamps over the signal cable, so with the cabinet dead
 * it is dark. Its lamps and selectors are live controls (IControlled); the server sets them, the client draws them.
 */
public abstract class RoadTile extends TileEntity implements ITickable, IControlled {
    protected final ControlState ctl = new ControlState();
    protected int age;
    private boolean dirty;

    @Override public ControlState controls() { return ctl; }

    @Override
    public void controlsChanged() {
        markDirty();
        if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); }
    }

    protected void put(String id, int v) { dirty |= StationControls.put(ctl, id, v); }
    protected void put(String id, boolean v) { dirty |= StationControls.put(ctl, id, v); }
    protected void text(String id, String t) { dirty |= StationControls.text(ctl, id, t == null ? "" : t); }

    @Override
    public final void update() {
        if (world.isRemote) return;
        age++;
        serverTick();
        if (dirty) { dirty = false; controlsChanged(); }
    }

    protected abstract void serverTick();

    /** the phase-group / mode selectors are set by hand at the unit (null = they just turn) */
    @Override
    public String controlPress(String id, net.minecraft.entity.player.EntityPlayer who) {
        if (who == null && id.startsWith("s_")) return "§7That is set at the unit itself";
        return null;
    }

    protected void save(NBTTagCompound t) {}
    protected void load(NBTTagCompound t) {}

    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { super.writeToNBT(t); ctl.write(t); save(t); return t; }
    @Override public void readFromNBT(NBTTagCompound t) { super.readFromNBT(t); ctl.read(t); load(t); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(1, 2, 1); }
}
