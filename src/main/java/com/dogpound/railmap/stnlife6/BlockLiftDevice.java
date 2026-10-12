package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Boarding lift / gap filler block (batch 6c #15): the moving part's position is a blockstate LEVEL (its own model per
 * step) and its collision follows the tile's live geometry, so you really stand on the lift deck and walk on the gap
 * filler plate. While a lift deck rises, whoever stands on it is carried up with it (both sides: the client moves the
 * player, the server moves mobs).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public class BlockLiftDevice extends DepotBlock {
    public static final PropertyInteger LEVEL = PropertyInteger.create("level", 0, 9);

    public BlockLiftDevice(String id, Supplier<TileEntity> tile, double[] box, String... tips) { super(id, tile, box, tips); }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, LEVEL); }

    @Override
    public IBlockState getActualState(IBlockState s, IBlockAccess w, BlockPos p) {
        TileEntity t = w instanceof net.minecraft.world.ChunkCache c ? c.getTileEntity(p, net.minecraft.world.chunk.Chunk.EnumCreateEntityType.CHECK) : w.getTileEntity(p);
        return t instanceof MovingTile m ? s.withProperty(LEVEL, Math.max(0, Math.min(9, m.level()))) : s;
    }

    @Override
    public void addCollisionBoxToList(IBlockState s, World w, BlockPos p, AxisAlignedBB box, List<AxisAlignedBB> out, Entity e, boolean actual) {
        if (!(w.getTileEntity(p) instanceof MovingTile m)) { super.addCollisionBoxToList(s, w, p, box, out, e, actual); return; }
        for (double[] b : m.boxes()) addCollisionBoxToList(p, box, out, rotated(s.getValue(FACING), b[0], b[1], b[2], b[3], b[4], b[5]));
    }

    /** a north-frame px box turned to the block's facing (block-local) */
    public static AxisAlignedBB rotatedBox(IBlockState s, double x0, double y0, double z0, double x1, double y1, double z1) {
        return rotated(s.getPropertyKeys().contains(FACING) ? s.getValue(FACING) : net.minecraft.util.EnumFacing.NORTH, x0, y0, z0, x1, y1, z1);
    }

    /** ride the lift deck up */
    @SubscribeEvent
    public static void carry(LivingEvent.LivingUpdateEvent ev) {
        EntityLivingBase e = ev.getEntityLiving();
        if (e.isRiding() || (e.world.isRemote && !(e instanceof net.minecraft.entity.player.EntityPlayer)) || (!e.world.isRemote && e instanceof net.minecraft.entity.player.EntityPlayer)) return;
        BlockPos p = new BlockPos(e.posX, e.posY + 0.05, e.posZ);
        if (!(e.world.getTileEntity(p) instanceof TileBoardingLift l)) return;
        double top = p.getY() + l.top() / 16.0;
        if (e.posY < top && e.posY > top - 0.45) {
            e.setPosition(e.posX, top, e.posZ);
            if (e.motionY < 0) e.motionY = 0;
            e.onGround = true;
            e.fallDistance = 0;
        }
    }
}
