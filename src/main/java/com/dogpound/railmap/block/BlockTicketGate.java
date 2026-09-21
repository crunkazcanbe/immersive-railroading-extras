package com.dogpound.railmap.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** The fare gate on the concourse: shut until it is shown a ticket for this station. */
public class BlockTicketGate extends BlockStationDevice {

    public static final PropertyBool OPEN = PropertyBool.create("open");

    /** the pedestal is always there; the arms are what you walk into */
    private static final AxisAlignedBB SHUT =
            new AxisAlignedBB(0, 0, 0, 1, 14 / 16.0, 1);
    private static final AxisAlignedBB PASSABLE =
            new AxisAlignedBB(0, 0, 0, 1, 6 / 16.0, 1);

    public BlockTicketGate() {
        super("ticket_gate", Material.IRON,
                "Fare gate. Shows a ticket in, lets a passenger through.",
                "Place at the station entrance · right-click holding a ticket",
                "Passes redstone while open · a spent ticket is refused");
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, OPEN);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return state.withProperty(OPEN, te instanceof TileTicketGate g && g.isOpen());
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileTicketGate();
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileTicketGate g && g.isOpen() ? PASSABLE : SHUT;
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHUT;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        if (world.getTileEntity(pos) instanceof TileTicketGate gate) {
            ItemStack held = player.getHeldItem(hand);
            boolean wasOpen = gate.isOpen();
            String msg = gate.present(held);
            player.sendStatusMessage(new TextComponentString(msg), true);
            if (!wasOpen && gate.isOpen()) {
                world.playSound(null, pos, SoundEvents.BLOCK_NOTE_PLING, SoundCategory.BLOCKS, 0.6F, 1.6F);
            }
        }
        return true;
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return world.getTileEntity(pos) instanceof TileTicketGate g ? g.redstoneOutput() : 0;
    }
}
