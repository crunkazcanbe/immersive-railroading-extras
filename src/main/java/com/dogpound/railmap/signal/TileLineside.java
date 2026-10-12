package com.dogpound.railmap.signal;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Lineside scenery (whistle post, milepost, crossbuck, switch stand, derail, bumper) -- and the
 * speed sign, which extends it. Holds only the size, so the piece can be scaled to sit right next
 * to full-size Immersive Railroading stock; it is drawn by {@code LinesideRenderer}.
 */
public class TileLineside extends TileEntity implements IScalable, com.dogpound.railmap.settings.ISettingsHolder, com.dogpound.railmap.controls.IControlled {
    private float scale = 1f;

    // ---- working controls (requested feature): the switch stand's hand lever really throws the points beside it.
    // Mechanical, like the real thing: no power needed. Other pieces have no layout, so no controls.
    private final com.dogpound.railmap.controls.ControlState ctl = new com.dogpound.railmap.controls.ControlState();
    @Override public com.dogpound.railmap.controls.ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); if (world != null && !world.isRemote) { IBlockState st = world.getBlockState(pos); world.notifyBlockUpdate(pos, st, st, 3); } }
    /** resized / moved: the renderer draws the piece elsewhere, so the controls would float - none then */
    @Override public com.dogpound.railmap.controls.Layout layout() { return scale != 1f || moved() ? com.dogpound.railmap.controls.Layout.NONE : com.dogpound.railmap.controls.IControlled.super.layout(); }

    @Override
    public String controlPress(String id, net.minecraft.entity.player.EntityPlayer who) {
        if (!id.equals("lever")) return null;
        boolean reverse = ctl.get("lever") == 0;
        ctl.set("lever", reverse ? 1 : 0);
        BlockPos sw = throwSwitch(reverse);
        return sw == null ? "§eLever " + (reverse ? "REVERSE" : "NORMAL") + " §7- no switch within 4 blocks to throw (stand it beside the points)"
                : "§eSwitch thrown " + (reverse ? "REVERSE (turnout)" : "NORMAL (straight)") + " §7at " + sw.getX() + ", " + sw.getY() + ", " + sw.getZ();
    }

    /** the nearest IR switch within 4 blocks, forced to the lever's side; null = none */
    private BlockPos throwSwitch(boolean reverse) {
        java.util.List<BlockPos> near = new java.util.ArrayList<>();
        for (BlockPos p : BlockPos.getAllInBox(pos.add(-4, -1, -4), pos.add(4, 1, 4))) near.add(p.toImmutable());
        near.sort(java.util.Comparator.comparingDouble(p -> p.distanceSq(pos)));
        for (BlockPos p : near)
            if (world.isBlockLoaded(p) && com.dogpound.railmap.scan.SwitchControl.isRail(world, p) && com.dogpound.railmap.scan.SwitchControl.set(world, p, reverse)) return p;
        return null;
    }

    // ---- Settings Console (sneak-right-click with the Signal Wrench): fine placement ----
    protected final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);

    @Override public String settingsTitle() { return "Lineside Piece"; }
    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }

    @Override
    public java.util.List<com.dogpound.railmap.settings.Setting> settingDefs() {
        java.util.List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        l.add(com.dogpound.railmap.settings.Setting.num("Placement", "offX", "Slide left / right", "Move it off the block grid to sit right against the rail", 0, -8, 8, 1, "px"));
        l.add(com.dogpound.railmap.settings.Setting.num("Placement", "offZ", "Slide forward / back", "", 0, -8, 8, 1, "px"));
        l.add(com.dogpound.railmap.settings.Setting.num("Placement", "lift", "Raise / lower", "Sink it into a slab or lift it onto a ledge", 0, -8, 16, 1, "px"));
        l.add(com.dogpound.railmap.settings.Setting.num("Placement", "turn", "Angle", "Turn it to face curved track", 0, -45, 45, 5, "°"));
        return l;
    }

    @Override
    public void onSettingsChanged(String key) {
        markDirty();
        if (world != null && !world.isRemote) { IBlockState st = world.getBlockState(pos); world.notifyBlockUpdate(pos, st, st, 3); }
    }

    /** moved off its block by the console: the chunk model hides and the renderer draws it */
    public boolean moved() { return cfg.num("offX") != 0 || cfg.num("offZ") != 0 || cfg.num("lift") != 0 || cfg.num("turn") != 0; }
    public double offX() { return cfg.num("offX") / 16.0; }
    public double offZ() { return cfg.num("offZ") / 16.0; }
    public double lift() { return cfg.num("lift") / 16.0; }
    public float turn() { return cfg.num("turn"); }

    @Override
    public float scale() {
        return scale;
    }

    @Override
    public void setScale(float s) {
        scale = IScalable.clamp(s);
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getPropertyKeys().contains(BlockLineside.FACING) ? s.getValue(BlockLineside.FACING) : EnumFacing.NORTH;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        if (scale != 1f) t.setFloat("scale", scale);
        cfg.write(t);
        ctl.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        scale = t.hasKey("scale") ? IScalable.clamp(t.getFloat("scale")) : 1f;
        cfg.read(t);
        ctl.read(t);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);   // chunk model on/off follows moved()/scale
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        double g = Math.max(0, scale - 1) + (moved() ? 1 : 0);
        return new AxisAlignedBB(pos).grow(g, 0, g).expand(0, g, 0);
    }
}
