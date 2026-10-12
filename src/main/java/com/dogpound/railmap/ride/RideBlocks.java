package com.dogpound.railmap.ride;

import com.dogpound.railmap.block.BlockStationDevice;
import com.dogpound.railmap.block.BlockWideDesk;
import com.dogpound.railmap.safety.SafetyBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The ropeway blocks: stations, machine room, desk, tension weight, pylon sections and funicular track. */
public final class RideBlocks {
    private RideBlocks() {}

    /** removing a part a line can't do without takes the rope down (cars gone, riders set down) */
    static void dropLine(World w, RideData.Line l, String why) {
        if (l == null || w.isRemote) return;
        RideData d = RideData.get(w);
        d.lines.remove(l.id);
        d.changed();
        RideTicker.removed(w, l);
        for (int k = 0; k < RideData.F_NAMES.length; k++) com.dogpound.railmap.alarm.AlarmManager.get(w).clear("ride:" + l.id + ":" + k);
        com.dogpound.railmap.alarm.Recorder.event(w, l.label() + " rope taken down: " + why);
        for (EntityPlayer p : w.playerEntities) if (p.getDistanceSq(l.station(0)) < 96 * 96 || p.getDistanceSq(l.station(1)) < 96 * 96)
            p.sendStatusMessage(new TextComponentString("§6" + l.label() + ": rope taken down - " + why), false);
    }

    /** lines whose rope rests on something at p (a support point just above it, or a track point in it) */
    static List<RideData.Line> resting(World w, BlockPos p) {
        List<RideData.Line> o = new ArrayList<>();
        for (RideData.Line l : RideData.get(w).lines.values()) {
            for (double[] s : l.supports) {
                if (Math.floor(s[0]) == p.getX() && Math.floor(s[2]) == p.getZ() && s[1] >= p.getY() - 0.5 && s[1] <= p.getY() + 3.5) { o.add(l); break; }
            }
            if (l.pylons.contains(p.toLong()) && !o.contains(l)) o.add(l);
        }
        return o;
    }

    // ---- stations + tension weight (SafetyBlock: facing, tile, wrench) ---------------------------------------------
    public static class Part extends SafetyBlock {
        public Part(String id, Supplier<TileEntity> tile, double[] box, String... tips) { super(id, tile, box, tips); }

        @Override
        public void breakBlock(World w, BlockPos pos, IBlockState state) {
            if (!w.isRemote) {
                TileEntity t = w.getTileEntity(pos);
                RideData d = RideData.get(w);
                if (t instanceof TileRideStation) dropLine(w, d.byStation(pos), "a station was removed");
                else if (t instanceof TileRideTension te) { RideData.Line l = d.line(te.lineId); if (l != null && l.tension == pos.toLong()) { l.tension = 0; d.changed(); } }
            }
            super.breakBlock(w, pos, state);
        }
    }

    // ---- machine room + control desk (three wide) --------------------------------------------------------------------
    public static class Wide extends BlockWideDesk {
        public Wide(String id, Supplier<TileEntity> tile, String... tips) { super(id, Material.IRON, tile, tips); setHardness(3f); }

        @Override
        public void breakBlock(World w, BlockPos pos, IBlockState state) {
            if (!w.isRemote && w.getTileEntity(pos) instanceof RideTile t) {
                RideData d = RideData.get(w);
                RideData.Line l = d.line(t.lineId);
                if (l != null) { if (l.machine == pos.toLong()) l.machine = 0; if (l.desk == pos.toLong()) l.desk = 0; d.changed(); }
            }
            super.breakBlock(w, pos, state);
        }
    }

    // ---- pylon (lattice mast section; the top one carries the head, drawn turned to the line by RideRender) ----------
    public static class Pylon extends BlockStationDevice {
        static final AxisAlignedBB BOX = new AxisAlignedBB(3 / 16.0, 0, 3 / 16.0, 13 / 16.0, 1, 13 / 16.0);
        public Pylon() {
            super("ropeway_pylon", Material.IRON,
                    "Ropeway pylon: galvanised lattice mast section - stack them as high as the line needs",
                    "The top section carries the sheave trains: click it with the Haul Rope Spool while stringing a rope");
            setHardness(4f);
        }

        @Override public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return BOX; }

        @Override
        public void breakBlock(World w, BlockPos pos, IBlockState state) {
            if (!w.isRemote) for (RideData.Line l : resting(w, pos.up(Math.max(0, top(w, pos) - pos.getY())))) dropLine(w, l, "a pylon was cut down");
            super.breakBlock(w, pos, state);
        }

        /** the y of the top section of the stack p is in */
        public static int top(World w, BlockPos p) {
            BlockPos q = p;
            while (w.getBlockState(q.up()).getBlock() instanceof Pylon && q.getY() < 255) q = q.up();
            return q.getY();
        }
    }

    // ---- funicular track: flat, or 45 degrees where the next piece is a block higher ------------------------------------
    public static class Track extends BlockStationDevice {
        public static final PropertyBool STEEP = PropertyBool.create("steep");
        static final AxisAlignedBB FLAT = new AxisAlignedBB(0, 0, 0, 1, 4 / 16.0, 1);

        public Track() {
            super("funicular_track", Material.IRON,
                    "Funicular track: sleepers, rails and rope rollers - lay it from the bottom station to the top station",
                    "Up a slope it turns into a 45° ramp by itself (one block up per block along)");
            setHardness(2f);
            setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(STEEP, false));
        }

        @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, STEEP); }

        @Override
        public IBlockState getActualState(IBlockState s, IBlockAccess w, BlockPos p) {
            for (EnumFacing f : EnumFacing.HORIZONTALS) if (w.getBlockState(p.offset(f).up()).getBlock() == this) return s.withProperty(FACING, f).withProperty(STEEP, true);
            for (EnumFacing f : EnumFacing.HORIZONTALS) {
                IBlockState n = w.getBlockState(p.offset(f).down());
                if (n.getBlock() == this || w.getBlockState(p.offset(f)).getBlock() == this) return s.withProperty(FACING, f.getAxis() == EnumFacing.Axis.Z ? EnumFacing.NORTH : EnumFacing.EAST).withProperty(STEEP, false);
            }
            return s.withProperty(STEEP, false);
        }

        @Override
        public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return getActualState(s, w, p).getValue(STEEP) ? FULL_BLOCK_AABB : FLAT; }

        @Override
        public void breakBlock(World w, BlockPos pos, IBlockState state) {
            if (!w.isRemote) for (RideData.Line l : resting(w, pos)) if (l.kind == RideGeom.FUNICULAR) dropLine(w, l, "a piece of track was lifted");
            super.breakBlock(w, pos, state);
        }

        /** the car's running height on a piece of track at p */
        public static double railY(World w, BlockPos p) {
            return w.getBlockState(p).getActualState(w, p).getValue(STEEP) ? p.getY() + 0.5 + 0.2 : p.getY() + 0.25;
        }
    }
}
