package com.dogpound.railmap.wire;

import com.dogpound.railmap.grid.BlockInstrument;
import com.dogpound.railmap.grid.Reading;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/**
 * A Gauge Panel: four holes in its front (2 x 2). Each hole has a wire colour run into it from behind and an
 * instrument mounted over it (any of the panel instruments: meters, the readout screen, the lamp). Looks like any
 * block you right-click it with.
 */
public class TileGaugePanel extends TileEntity implements ITickable {
    public final int[] inst = {-1, -1, -1, -1};      // BlockInstrument.Inst ordinal, -1 = empty hole
    public final int[] wire = {-1, -1, -1, -1};      // wire colour run into the hole, -1 = none
    public final Reading[] rd = {new Reading(), new Reading(), new Reading(), new Reading()};
    /** Control Buttons in the holes: which of the machine's panel controls each copies, when it was last pressed */
    public final String[] ctl = {"", "", "", ""};
    public final long[] pressedAt = new long[4];
    public String camo = "";
    /** one 12 V DC feed powers all four holes; the wrong voltage burns the lot */
    public boolean burnt;
    public int pw = 1;

    public String repair() {
        if (!burnt) return null;
        burnt = false; sync();
        return "§aRepaired - new electronics behind the Gauge Panel";
    }
    public int camoMeta;

    public boolean uses(int c) { for (int w : wire) if (w == c) return true; return false; }

    public IBlockState camoState() {
        if (camo.isEmpty()) return null;
        Block b = Block.REGISTRY.getObject(new ResourceLocation(camo));
        if (b == null || b == net.minecraft.init.Blocks.AIR) return null;
        try { return b.getStateFromMeta(camoMeta); } catch (Exception e) { return b.getDefaultState(); }
    }

    @Override
    public void update() {
        if (world.isRemote || world.getTotalWorldTime() % 20 != (pos.hashCode() & 15)) return;
        StringBuilder before = new StringBuilder(), after = new StringBuilder();
        for (Reading r : rd) before.append(r.key());
        int pwBefore = pw;
        WireNet.Supply sup = burnt ? null : WireNet.power(world, pos);
        if (sup != null && sup.state == 2) { burnt = true; WireNet.burnOut(world, pos, "Gauge Panel", sup.what); }
        pw = burnt ? 2 : sup.state == 0 ? 0 : 1;
        for (int i = 0; i < 4; i++) {
            if (inst[i] >= 0 && pw != 1) { rd[i].unpowered(pw, pw == 2 ? "fix with a screwdriver" : sup.what); continue; }
            rd[i].pw = 1;
            if (inst[i] < 0) continue;
            BlockInstrument.Inst k = BlockInstrument.Inst.values()[inst[i]];
            if (wire[i] < 0) { rd[i].notLinked("no wire in", "this hole"); continue; }
            BlockPos src = WireNet.source(world, pos, wire[i]);
            if (src == null) rd[i].notLinked("nothing on the", WireNet.NAMES[wire[i]] + " wire");
            else if (k == BlockInstrument.Inst.BUTTON) ctl[i] = rd[i].readButton(world, src, ctl[i]);
            else rd[i].read(world, src, k);
        }
        for (Reading r : rd) after.append(r.key());
        if (!before.toString().equals(after.toString()) || pw != pwBefore) sync();
    }

    /** the button in hole i: press the machine's own control */
    public String press(int i, String who) {
        if (pw == 2) return "§cBurnt out - fix it with a screwdriver (and check its supply is 12 V DC)";
        if (pw == 0) return "§cNo power - run 12 V DC Power Wire to the Gauge Panel from a 12 V DC supply";
        BlockPos s = wire[i] < 0 ? null : WireNet.source(world, pos, wire[i]);
        if (!(s != null && world.getTileEntity(s) instanceof IWiredMachine m)) return "§cNothing on that wire - land it on a machine's control terminal (right-click the machine with it)";
        ctl[i] = rd[i].readButton(world, s, ctl[i]);
        String r = m.wiredPress(ctl[i], who);
        rd[i].readButton(world, s, ctl[i]); sync();
        return r == null || r.isEmpty() ? "§a" + rd[i].line1 + " pressed" : r;
    }

    public String cycle(int i, int dir) {
        BlockPos s = wire[i] < 0 ? null : WireNet.source(world, pos, wire[i]);
        if (s == null) return "§cWire that hole to a machine first";
        ctl[i] = Reading.nextButton(world, s, ctl[i], dir);
        rd[i].readButton(world, s, ctl[i]); sync();
        return "§aThat button now does: §f" + rd[i].line1 + " §7(" + rd[i].line2 + ")";
    }

    public void sync() {
        markDirty();
        if (world != null) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setIntArray("inst", inst); t.setIntArray("wire", wire);
        t.setString("camo", camo); t.setInteger("camoMeta", camoMeta); t.setBoolean("burnt", burnt); t.setInteger("pw", pw);
        for (int i = 0; i < 4; i++) { rd[i].write(t, "r" + i); t.setString("ctl" + i, ctl[i]); }
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        int[] a = t.getIntArray("inst"), b = t.getIntArray("wire");
        for (int i = 0; i < 4; i++) { inst[i] = a.length == 4 ? a[i] : -1; wire[i] = b.length == 4 ? b[i] : -1; }
        camo = t.getString("camo"); camoMeta = t.getInteger("camoMeta"); burnt = t.getBoolean("burnt"); pw = t.hasKey("pw") ? t.getInteger("pw") : 1;
        for (int i = 0; i < 4; i++) { rd[i].read(t, "r" + i); ctl[i] = t.getString("ctl" + i); }
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(0.3); }
}
