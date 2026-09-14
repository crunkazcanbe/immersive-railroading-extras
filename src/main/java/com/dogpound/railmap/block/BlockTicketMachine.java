package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.item.ItemTicket;
import com.dogpound.railmap.network.PacketTicketMenu;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * The ticket machine: two blocks tall, like the real kiosks on a platform. The lower half holds
 * the tile; the upper half is its screen and just forwards clicks down.
 */
public class BlockTicketMachine extends BlockStationDevice {
    public static final PropertyBool UPPER = PropertyBool.create("upper");

    public BlockTicketMachine() {
        super("ticket_machine", Material.IRON,
                "Sells tickets to every named station.",
                "Right-click: buy · right-click holding a ticket: call a train",
                "Link platform signals with the Signal Wrench");
        setDefaultState(getDefaultState().withProperty(UPPER, false));
        setLightLevel(0.3f);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, UPPER);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return super.getStateFromMeta(meta & 3).withProperty(UPPER, (meta & 4) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(UPPER) ? 4 : 0);
    }

    // ---- two halves ----------------------------------------------------------------------

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && world.getBlockState(pos.up()).getBlock().isReplaceable(world, pos.up());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        world.setBlockState(pos.up(), state.withProperty(UPPER, true), 3);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        boolean upper = state.getValue(UPPER);
        BlockPos other = upper ? pos.down() : pos.up();
        if (world.getBlockState(other).getBlock() != this) world.setBlockToAir(pos);
    }

    @Override
    public net.minecraft.item.Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return state.getValue(UPPER) ? net.minecraft.init.Items.AIR : super.getItemDropped(state, rand, fortune);
    }

    @Override
    public void onBlockHarvested(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        if (state.getValue(UPPER) && world.getBlockState(pos.down()).getBlock() == this) {
            if (player.capabilities.isCreativeMode) world.setBlockState(pos.down(), Blocks.AIR.getDefaultState(), 3);
            else world.destroyBlock(pos.down(), true);
        }
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return !state.getValue(UPPER);
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return state.getValue(UPPER) ? null : new TileTicketMachine();
    }

    // ---- use -----------------------------------------------------------------------------

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        BlockPos base = state.getValue(UPPER) ? pos.down() : pos;
        if (world.isRemote) return true;
        if (!(world.getTileEntity(base) instanceof TileTicketMachine machine)) return true;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() instanceof com.dogpound.railmap.item.ItemSignalWrench) return false;
        if (held.getItem() instanceof ItemTicket) {
            player.sendMessage(new TextComponentString("[TICKET] " + machine.insert(player, held)));
            return true;
        }
        machine.resolveStation();
        if (player instanceof EntityPlayerMP mp) RailMap.NETWORK.sendTo(PacketTicketMenu.build(machine, mp), mp);
        return true;
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        if (state.getValue(UPPER)) return 0;
        return world.getTileEntity(pos) instanceof TileTicketMachine m ? m.redstoneOutput() : 0;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return state.getValue(UPPER)
                ? rotated(state.getValue(FACING), 1, 0, 3, 15, 14, 14)
                : rotated(state.getValue(FACING), 1, 0, 2, 15, 16, 14);
    }
}
