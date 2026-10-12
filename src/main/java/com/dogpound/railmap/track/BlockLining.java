package com.dogpound.railmap.track;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Tunnel building blocks (requested feature): cheap lining
 * pieces in concrete (segmental, with bolt pockets and ring joints), engineering brick and dressed stone that join
 * into a tube - WALL (the side walls), HAUNCH (the curved shoulder), CROWN (the arched roof) and NICHE (a refuge recess
 * in the wall, white-painted). The inner face points the way the block faces (toward the player who places it, so
 * stand inside the tunnel and place them). Plus the CABLE TROUGH: concrete troughing with lids along the walkway.
 * They block daylight like rock, so the tunnel is dark until you light it.
 */
public class BlockLining extends BlockStationDevice {
    public enum Piece { WALL, HAUNCH, CROWN, NICHE, TROUGH }

    public final Piece piece;

    public BlockLining(String material, Piece piece) {
        super(piece == Piece.TROUGH ? "cable_trough" : "tunnel_" + material + "_" + piece.name().toLowerCase(java.util.Locale.ROOT), Material.ROCK, tips(material, piece));
        this.piece = piece;
        setHardness(piece == Piece.TROUGH ? 1.5f : 2.5f);
        setResistance(30f);
        setSoundType(SoundType.STONE);
        setHarvestLevel("pickaxe", 0);
        if (piece != Piece.TROUGH) setLightOpacity(255);
    }

    private static String[] tips(String material, Piece p) {
        String m = material.equals("concrete") ? "Precast concrete segment (bolted ring joints)" : material.equals("brick") ? "Engineering brick lining (blue-red, 4 rings)" : "Dressed stone masonry lining";
        return switch (p) {
            case WALL -> new String[]{m + ": tunnel side wall", "Face it into the tunnel (place it standing inside)"};
            case HAUNCH -> new String[]{m + ": curved haunch, the shoulder between wall and roof", "Goes on top of the walls, curve facing in"};
            case CROWN -> new String[]{m + ": arched roof crown", "Fills the roof between the haunches"};
            case NICHE -> new String[]{m + ": refuge niche - a recess in the wall to stand clear of trains", "White-painted with a REFUGE sign"};
            default -> new String[]{"Cable trough: concrete troughing with lids, along the tunnel walkway", "Lay it end to end beside the track"};
        };
    }

    /** solid boxes in north-facing model space (the inner face looks north) */
    private double[][] boxes() {
        return switch (piece) {
            case WALL -> new double[][]{{0, 0, 6, 16, 16, 16}};
            case HAUNCH -> new double[][]{{0, 0, 6, 16, 16, 16}, {0, 6, 4, 16, 16, 6}, {0, 12, 0, 16, 16, 4}};
            case CROWN -> new double[][]{{0, 0, 0, 16, 10, 16}};
            case NICHE -> new double[][]{{0, 0, 13, 16, 16, 16}, {0, 0, 0, 2, 16, 13}, {14, 0, 0, 16, 16, 13}, {2, 13, 0, 14, 16, 13}};
            default -> new double[][]{{0, 0, 3, 16, 5, 15}};
        };
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        double[][] b = boxes();
        if (b.length == 1) return rotated(state.getValue(FACING), b[0][0], b[0][1], b[0][2], b[0][3], b[0][4], b[0][5]);
        return FULL_BLOCK_AABB;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> out, @Nullable Entity e, boolean actual) {
        EnumFacing f = state.getValue(FACING);
        for (double[] b : boxes()) addCollisionBoxToList(pos, entityBox, out, rotated(f, b[0], b[1], b[2], b[3], b[4], b[5]));
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        if (piece == Piece.TROUGH) return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
        return face == state.getValue(FACING) && piece != Piece.CROWN ? BlockFaceShape.UNDEFINED : BlockFaceShape.SOLID;   // the back and sides take torches, cables, gear
    }

    @Override public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return piece != Piece.TROUGH && side != state.getValue(FACING); }
}
