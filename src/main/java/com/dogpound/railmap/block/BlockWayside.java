package com.dogpound.railmap.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/** The weighbridge and the AEI reader: the two devices that measure a train instead of directing it. */
public class BlockWayside extends BlockStationDevice {

    private static final AxisAlignedBB DECK = new AxisAlignedBB(0, 0, 0, 1, 6 / 16.0, 1);

    private final TileWayside.Kind kind;

    public BlockWayside(TileWayside.Kind kind) {
        super(kind == TileWayside.Kind.SCALE ? "track_scale" : "aei_reader", Material.IRON,
                kind == TileWayside.Kind.SCALE
                        ? "Weighs every vehicle that rolls over it."
                        : "Reads and logs every vehicle that passes.",
                "Place in the rail · it measures the whole train, then reports",
                "Right-click for the last reading · comparator reads the vehicle count");
        this.kind = kind;
    }

    public static final net.minecraft.block.properties.PropertyBool SCALED =
            net.minecraft.block.properties.PropertyBool.create("scaled");

    @Override
    protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new net.minecraft.block.state.BlockStateContainer(this, FACING, SCALED);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return state.withProperty(SCALED, te instanceof TileWayside w && w.scale() != 1f);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return kind == TileWayside.Kind.SCALE ? new TileWayside.Scale() : new TileWayside.Aei();
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return DECK;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote || !(world.getTileEntity(pos) instanceof TileWayside w)) {
            return true;
        }
        player.sendStatusMessage(new TextComponentString(w.statusLine()), true);
        // The reader also keeps the consist list; print it where it can actually be read.
        List<String> manifest = w.manifest();
        if (!manifest.isEmpty()) {
            player.sendMessage(new TextComponentString(TextFormatting.AQUA + "— last consist —"));
            for (String line : manifest) {
                player.sendMessage(new TextComponentString(TextFormatting.GRAY + "  " + line));
            }
        }
        return true;
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof TileWayside w ? w.redstoneOutput() : 0;
    }
}
