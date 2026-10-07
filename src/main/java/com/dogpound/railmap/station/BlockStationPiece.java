package com.dogpound.railmap.station;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * One piece of a station (requested feature), in one of four
 * architectural styles. Platforms are solid; the furniture has a fitted collision box. Name boards,
 * platform signs and clocks carry a tile so they can show live text / time.
 */
public class BlockStationPiece extends BlockStationDevice {
    public enum Kind {
        PLATFORM_EDGE("platform_edge", "The platform edge: coping stone, white line and yellow tactile strip", 0, 0, 0, 16, 16, 16),
        PLATFORM("platform", "Platform paving", 0, 0, 0, 16, 16, 16),
        CANOPY_COLUMN("canopy_column", "Holds up the canopy; stack Canopy Roof on top of a row of them", 6.5, 0, 6.5, 9.5, 16, 9.5),
        CANOPY_ROOF("canopy_roof", "Platform canopy roof; place it two blocks up on the columns", 0, 12, 0, 16, 16, 16),
        BENCH("bench", "Somewhere to wait", 1, 0, 5.5, 15, 9, 12),
        SHELTER("shelter", "Glass waiting shelter with a bench and a timetable poster", 0, 0, 0, 16, 16, 16),
        LAMP("lamp", "Platform lighting", 6.5, 0, 6.5, 9.5, 16, 9.5),
        BIN("bin", "Litter bin", 4, 0, 4, 12, 10, 12),
        CLOCK("clock", "Double-faced station clock: shows the time of day (settings: game or real time)", 7, 0, 7, 9, 16, 9),
        NAME_SIGN("name_sign", "Shows the station's name (from the nearest Station Master's Desk)", 0, 0, 7, 16, 16, 9),
        PLATFORM_SIGN("platform_sign", "Platform number; says when a train is standing at it", 3, 2, 7, 13, 16, 9),
        TIMETABLE("timetable", "Timetable poster board", 2, 0, 8.5, 14, 16, 10);

        public final String id, tip;
        final double[] box;

        Kind(String id, String tip, double... box) { this.id = id; this.tip = tip; this.box = box; }

        public boolean live() { return this == CLOCK || this == NAME_SIGN || this == PLATFORM_SIGN; }
        boolean solid() { return this == PLATFORM || this == PLATFORM_EDGE; }
        boolean glassy() { return this == SHELTER || this == CANOPY_ROOF; }
    }

    public static final String[] STYLES = {"victorian", "modern", "metro", "rural"};

    public final Kind kind;
    public final String style;

    public BlockStationPiece(String style, Kind kind) {
        super("station_" + style + "_" + kind.id, Material.ROCK, kind.tip);
        this.kind = kind;
        this.style = style;
        setHardness(kind.solid() ? 2.0f : 1.2f);
        this.fullBlock = kind.solid();
        this.lightOpacity = kind.solid() ? 255 : 0;
        if (kind == Kind.LAMP) setLightLevel(1.0f);
        if (kind == Kind.SHELTER) setLightOpacity(0);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        double[] b = kind.box;
        return rotated(state.getValue(FACING), b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    // Block's constructor asks these before our fields are set: answer "not solid" then fix up after
    @Override public boolean isOpaqueCube(IBlockState state) { return kind != null && kind.solid(); }
    @Override public boolean isFullCube(IBlockState state) { return kind != null && kind.solid(); }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return kind != null && kind.solid() ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return kind != null && kind.glassy() && !"metro".equals(style) && !"rural".equals(style) ? BlockRenderLayer.TRANSLUCENT : BlockRenderLayer.CUTOUT;
    }

    @Override public boolean hasTileEntity(IBlockState state) { return kind.live(); }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return kind.live() ? new TileStationPiece(kind.id) : null;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing f,
                                    float hx, float hy, float hz) {
        if (!kind.live() || world.isRemote) return kind.live();
        if (world.getTileEntity(pos) instanceof TileStationPiece t)
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(t.statusLine()), true);
        return true;
    }
}
