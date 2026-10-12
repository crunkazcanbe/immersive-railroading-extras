package com.dogpound.railmap.factory.machine;

import com.dogpound.railmap.factory.block.BlockCasing;
import com.dogpound.railmap.factory.block.BlockPort;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * A machine's pattern laid into the world around its controller. The panel faces `front`; standing in front of it the
 * pattern's x runs to your right (front.rotateYCCW()), z runs away from you into the machine (front.getOpposite()).
 */
public final class Structure {
    private Structure() {}

    public static BlockPos at(MachineType t, BlockPos controller, EnumFacing front, int x, int y, int z) {
        int[] c = t.controller();
        EnumFacing right = front.rotateYCCW(), back = front.getOpposite();
        return controller.offset(right, x - c[0]).up(y - c[1]).offset(back, z - c[2]);
    }

    /** "" if the structure is complete, else what's wrong (and where) */
    public static String check(World w, MachineType t, BlockPos controller, EnumFacing front) {
        for (int y = 0; y < t.height(); y++)
            for (int z = 0; z < t.depth(); z++)
                for (int x = 0; x < t.width(); x++) {
                    char ch = t.pattern[y][z].charAt(x);
                    if (ch == ' ' || ch == 'C') continue;
                    BlockPos p = at(t, controller, front, x, y, z);
                    IBlockState s = w.getBlockState(p);
                    Block b = s.getBlock();
                    String need = null;
                    switch (ch) {
                        case '#': if (!(b instanceof BlockCasing)) need = "Machine Casing"; break;
                        case 'I': if (!(b instanceof BlockPort bp) || bp.kind != BlockPort.Kind.ITEM_IN) need = "Item Input Hatch"; break;
                        case 'O': if (!(b instanceof BlockPort bp) || bp.kind != BlockPort.Kind.ITEM_OUT) need = "Item Output Hatch"; break;
                        case 'W': if (!(b instanceof BlockPort bp) || bp.kind != BlockPort.Kind.FLUID_IN) need = "Water Inlet"; break;
                        case 'F': if (!(b instanceof BlockPort bp) || bp.kind != BlockPort.Kind.FLUID_OUT) need = "Fluid Output Hatch"; break;
                        case 'D': if (!(b instanceof BlockPort bp) || bp.kind != BlockPort.Kind.DRIVE) need = "Drive Coupling"; break;
                        case 'P': if (!(b instanceof BlockPort bp) || bp.kind != BlockPort.Kind.POWER) need = "Power Inlet"; break;
                        case 'T':
                            if (b instanceof BlockCasing || b instanceof BlockPort) need = "open space (the track runs through here)";
                            break;
                        default: break;
                    }
                    if (need != null)
                        return need + " needed at " + p.getX() + " " + p.getY() + " " + p.getZ() + " (found " + b.getLocalizedName() + ")";
                    if (b instanceof BlockCasing || b instanceof BlockPort) {
                        BlockPos owner = BlockCasing.ownerOf(w, p);
                        if (owner != null && !owner.equals(controller)) return "The block at " + p.getX() + " " + p.getY() + " " + p.getZ() + " already belongs to another machine";
                    }
                }
        return "";
    }

    /** every casing and port of the machine (not the controller, not open space) */
    public static List<BlockPos> members(MachineType t, BlockPos controller, EnumFacing front) {
        List<BlockPos> l = new ArrayList<>();
        for (int y = 0; y < t.height(); y++)
            for (int z = 0; z < t.depth(); z++)
                for (int x = 0; x < t.width(); x++) {
                    char ch = t.pattern[y][z].charAt(x);
                    if (ch == ' ' || ch == 'C' || ch == 'T') continue;
                    l.add(at(t, controller, front, x, y, z));
                }
        return l;
    }

    /** the open 'T' positions (where trains run) */
    public static List<BlockPos> clearance(MachineType t, BlockPos controller, EnumFacing front) {
        List<BlockPos> l = new ArrayList<>();
        for (int y = 0; y < t.height(); y++)
            for (int z = 0; z < t.depth(); z++)
                for (int x = 0; x < t.width(); x++)
                    if (t.pattern[y][z].charAt(x) == 'T') l.add(at(t, controller, front, x, y, z));
        return l;
    }
}
