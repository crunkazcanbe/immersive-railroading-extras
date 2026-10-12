package com.dogpound.railmap.track;

import com.dogpound.railmap.safety.SafetyBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.function.Supplier;

/**
 * Every track-infrastructure block with a tile: the safety gear's block (faces the placer, Signal Wrench links picked
 * rails / switches / crossings onto a control) plus real light for lamps that emit it (tunnel lights, exit signs) and
 * dropping its row from the console's asset register when broken.
 */
public class TrackBlock extends SafetyBlock {
    /** a tile that lights the block: 0..15 */
    public interface Lit { int light(); }
    /** a tile that takes a name from a renamed Name Tag */
    public interface Named { String setName(String name); }

    private final boolean dark;

    public TrackBlock(String id, Supplier<TileEntity> tile, double[] box, String... tips) { this(id, tile, box, false, tips); }

    /** dark = blocks skylight (lining-like solid wall gear) */
    public TrackBlock(String id, Supplier<TileEntity> tile, double[] box, boolean dark, String... tips) {
        super(id, tile, box, tips);
        this.dark = dark;
        if (dark) setLightOpacity(255);
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity t = world.getTileEntity(pos);
        return t instanceof Lit l ? Math.max(0, Math.min(15, l.light())) : 0;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, net.minecraft.entity.player.EntityPlayer player, net.minecraft.util.EnumHand hand, net.minecraft.util.EnumFacing side, float hx, float hy, float hz) {
        net.minecraft.item.ItemStack h = player.getHeldItem(hand);
        if (h.getItem() == net.minecraft.init.Items.NAME_TAG && h.hasDisplayName() && world.getTileEntity(pos) instanceof Named n) {
            if (!world.isRemote) player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(n.setName(h.getDisplayName())), true);
            return true;
        }
        return super.onBlockActivated(world, pos, state, player, hand, side, hx, hy, hz);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) TrackData.get(world).unasset(pos);
        super.breakBlock(world, pos, state);
    }
}
