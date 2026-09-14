package com.dogpound.railmap.signal;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/** The instrument case beside the track: where redstone meets the signalling. */
public class BlockRelayCase extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private static final AxisAlignedBB BOX = new AxisAlignedBB(1.5 / 16.0, 0, 2.5 / 16.0, 14.5 / 16.0, 14.5 / 16.0, 13.5 / 16.0);

    public BlockRelayCase() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "relay_case");
        setTranslationKey(RailMap.MODID + ".relay_case");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(2f);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRelayCase relay) {
            // The wrench does the linking; a bare hand cycles what the redstone means.
            if (player.getHeldItem(hand).isEmpty() || player.isSneaking()) {
                relay.cycleMode();
            }
            player.sendStatusMessage(new TextComponentString(relay.statusLine()), true);
        }
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        tip.add("§7Wire redstone here to control a whole junction of signals.");
        tip.add("§8Link signals with the Signal Wrench (up to " + TileRelayCase.LINK_RANGE + " blocks).");
        tip.add("§8Right-click bare-handed: Hold / Drive / Report");
        tip.add("§8Comparator reads the worst aspect on the box.");
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRelayCase();
    }

    // ---- redstone -----------------------------------------------------------------------

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRelayCase relay)) return 0;
        // Only the Report mode drives a wire; the other two are inputs, and a box that both
        // listened and shouted on the same wire would feed itself.
        return relay.mode() == TileRelayCase.Mode.REPORT ? relay.redstoneOutput() : 0;
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileRelayCase relay ? relay.redstoneOutput() : 0;
    }

    // ---- placement / shape ---------------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
                                            float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOX;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }
}
