package com.dogpound.railmap.station;

import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/** an escalator section: a stair you can walk (two steps), which carries what stands on it while it runs */
public class BlockEscalator extends DepotBlock {
    public BlockEscalator() {
        super("escalator", TileEscalator::new, null, "Escalator section: moving steps that carry you up (or down) the flight",
                "Place sections rising one block each. START / STOP, direction key, E-STOP. Mains + 12 V");
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB box, List<AxisAlignedBB> out, Entity e, boolean actual) {
        EnumFacing f = state.getValue(FACING);
        // the flight rises toward the model's back (the far side from the player who placed it)
        addCollisionBoxToList(pos, box, out, rotated(f, 0, 0, 0, 16, 8, 16));
        addCollisionBoxToList(pos, box, out, rotated(f, 0, 8, 8, 16, 16, 16));
    }

    @Override
    public void onEntityWalk(World world, BlockPos pos, Entity e) { carry(world, pos, e); }

    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity e) { carry(world, pos, e); }

    private void carry(World world, BlockPos pos, Entity e) {
        if (!(world.getTileEntity(pos) instanceof TileEscalator t) || !t.running()) return;
        EnumFacing up = world.getBlockState(pos).getValue(FACING).getOpposite();     // toward the model's back
        EnumFacing dir = t.down() ? up.getOpposite() : up;
        double v = 0.12;
        e.motionX += (dir.getXOffset() * v - e.motionX) * 0.5;
        e.motionZ += (dir.getZOffset() * v - e.motionZ) * 0.5;
        if (!t.down() && e.onGround) e.motionY = Math.max(e.motionY, 0.05);
        e.velocityChanged = true;
    }
}
