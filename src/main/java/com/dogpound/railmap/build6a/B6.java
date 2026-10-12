package com.dogpound.railmap.build6a;

import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.wire.Terminal;
import com.dogpound.railmap.wire.WireNet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Batch 6a shared plumbing: store counting / taking, a machine that drives itself forward (track layer, TBM), and
 * the supply checks for machines that need more than 120-480 V (the TBM's trailing 4.16-13.8 kV cable).
 */
public final class B6 {
    private B6() {}

    /** the stack carries this ore-dictionary name (irRail, irTie ...) */
    public static boolean ore(ItemStack s, String name) {
        if (s.isEmpty()) return false;
        if (!OreDictionary.doesOreNameExist(name)) return false;
        int id = OreDictionary.getOreID(name);
        for (int o : OreDictionary.getOreIDs(s)) if (o == id) return true;
        return false;
    }

    public static int count(IInventory inv, Predicate<ItemStack> ok) {
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) { ItemStack s = inv.getStackInSlot(i); if (!s.isEmpty() && ok.test(s)) n += s.getCount(); }
        return n;
    }

    /** takes up to n matching items; returns how many it took */
    public static int take(IInventory inv, Predicate<ItemStack> ok, int n) {
        int got = 0;
        for (int i = 0; i < inv.getSizeInventory() && got < n; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || !ok.test(s)) continue;
            int k = Math.min(n - got, s.getCount());
            s.shrink(k);
            got += k;
            if (s.isEmpty()) inv.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        if (got > 0) inv.markDirty();
        return got;
    }

    /** puts a stack into the store; returns what did not fit */
    public static ItemStack put(IInventory inv, ItemStack s) {
        s = s.copy();
        for (int i = 0; i < inv.getSizeInventory() && !s.isEmpty(); i++) {
            ItemStack h = inv.getStackInSlot(i);
            if (!h.isEmpty() && ItemStack.areItemsEqual(h, s) && ItemStack.areItemStackTagsEqual(h, s) && h.getCount() < h.getMaxStackSize()) {
                int k = Math.min(s.getCount(), h.getMaxStackSize() - h.getCount());
                h.grow(k); s.shrink(k);
            }
        }
        for (int i = 0; i < inv.getSizeInventory() && !s.isEmpty(); i++)
            if (inv.getStackInSlot(i).isEmpty()) { inv.setInventorySlotContents(i, s.copy()); s = ItemStack.EMPTY; }
        inv.markDirty();
        return s;
    }

    public static int free(IInventory inv) { int n = 0; for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i).isEmpty()) n++; return n; }

    /** IR yaw (0 = +z south, 90 = +x east) -> the nearest horizontal facing */
    public static EnumFacing facingOf(float irYaw) {
        int q = Math.floorMod(Math.round(irYaw / 90f), 4);
        return new EnumFacing[]{EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.WEST}[q];
    }

    /** facing -> IR yaw */
    public static float yawOf(EnumFacing f) {
        switch (f) { case EAST: return 90; case NORTH: return 180; case WEST: return 270; default: return 0; }
    }

    /**
     * The machine drives itself to `to` (the railhead / the new face): its tile is lifted off (nothing drops), the old
     * block goes, the same block is set at `to` facing `face`, and the tile comes back with everything it had. False
     * (nothing changed) when `to` is not free.
     */
    public static boolean move(World w, BlockPos from, BlockPos to, EnumFacing face) {
        TileEntity t = w.getTileEntity(from);
        if (t == null || !w.isBlockLoaded(to)) return false;
        IBlockState here = w.getBlockState(from);
        if (!w.getBlockState(to).getBlock().isReplaceable(w, to) && !w.isAirBlock(to)) return false;
        NBTTagCompound tag = t.writeToNBT(new NBTTagCompound());
        w.removeTileEntity(from);                                     // breakBlock finds no tile: nothing drops
        w.setBlockToAir(from);
        IBlockState s = here.getPropertyKeys().contains(com.dogpound.railmap.block.BlockStationDevice.FACING) ? here.withProperty(com.dogpound.railmap.block.BlockStationDevice.FACING, face) : here;
        w.setBlockState(to, s, 3);
        TileEntity n = w.getTileEntity(to);
        if (n != null) {
            tag.setInteger("x", to.getX()); tag.setInteger("y", to.getY()); tag.setInteger("z", to.getZ());
            n.readFromNBT(tag);
            n.markDirty();
        }
        return true;
    }

    /** lift the machine off its block for a moment (so IR can build through it); put it back with `restore` */
    public static NBTTagCompound lift(World w, BlockPos p) {
        TileEntity t = w.getTileEntity(p);
        if (t == null) return null;
        NBTTagCompound tag = t.writeToNBT(new NBTTagCompound());
        tag.setInteger("_state", net.minecraft.block.Block.getStateId(w.getBlockState(p)));
        w.removeTileEntity(p);
        w.setBlockToAir(p);
        return tag;
    }

    public static void restore(World w, BlockPos p, NBTTagCompound tag) {
        w.setBlockState(p, net.minecraft.block.Block.getStateById(tag.getInteger("_state")), 3);
        TileEntity n = w.getTileEntity(p);
        if (n != null) { n.readFromNBT(tag); n.markDirty(); }
    }

    // ---- supply ----------------------------------------------------------------------------------------------------

    /**
     * A machine's power supply from the IR Extras grid, checked once a second at `at` (the machine itself, or the
     * launch shaft its trailing cable runs back to): an AC level between minV and maxV is right, a level over burnV
     * burns its electronics (the 12 V board; screwdriver fixes it), anything else is the wrong supply. load FE is drawn
     * from the network that feeds it.
     */
    public static final class Supply {
        public int state;
        public String what = "no supply";
        public long fe;
        public double volts;

        public boolean tick(World w, BlockPos at, Terminal term, String name, long load, double minV, double maxV, double burnV, String need) {
            int was = state;
            state = 0; fe = 0; volts = 0;
            what = "no supply - lay a grid cable to it (" + need + ")";
            if (!B6Config.supplyRequired) { state = 1; what = "supply not required (config)"; volts = maxV; return was != state; }
            GridData d = GridData.get(w);
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos nb = at.offset(f);
                if (d.node(nb) == null) continue;
                GridData.Net n = d.netAt(nb, f.getOpposite());
                if (n == null) continue;
                if (!n.problem.isEmpty()) { what = "supply dead: " + n.problem; continue; }
                Elec.Level L = n.level;
                if (L == null) continue;
                if (L.volts > burnV) {
                    if (term != null && !term.burnt) { term.burnt = true; WireNet.burnOut(w, at, name, L.label); }
                    what = "BURNT OUT - fed " + L.label;
                    return true;
                }
                if (!L.ac || L.volts < minV || L.volts > maxV) { what = "wrong supply: " + L.label + " (needs " + need + ")"; continue; }
                if (load > 0) {
                    long got = d.draw(n, load);
                    if (got < load) { what = "supply can't carry the load (" + got + "/" + load + " FE)"; continue; }
                    fe = got;
                }
                state = 1; volts = L.volts; what = L.label;
                break;
            }
            return was != state;
        }

        public boolean ok() { return state == 1; }
        public void write(NBTTagCompound t, String k) { t.setByte(k, (byte) state); t.setString(k + "W", what); }
        public void read(NBTTagCompound t, String k) { state = t.getByte(k); what = t.getString(k + "W"); }
    }

    public static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }
}
