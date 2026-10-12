package com.dogpound.railmap.safety;

import com.dogpound.railmap.block.BlockStationDevice;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.item.ItemSignalWrench;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.function.Supplier;

/**
 * One block for every piece of signalling-safety gear: faces the player who places it, its tile does the work. With
 * the Signal Wrench, right-clicking one of the model's controls links whatever the wrench picked (a signal, a head,
 * a rail...) to that control - axle sections, describer berths, plunger zones, lab targets. Breaking it tells the
 * safety systems it is gone (the lab clears its faults, an evaluator's holds go).
 */
public class SafetyBlock extends BlockStationDevice {
    private final Supplier<TileEntity> tile;
    private final double[] box;

    public SafetyBlock(String id, Supplier<TileEntity> tile, double[] box, String... tips) {
        super(id, Material.IRON, tips);
        this.tile = tile;
        this.box = box;
        setHardness(2.5f);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return tile.get(); }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return box == null ? FULL_BLOCK_AABB : rotated(state.getValue(FACING), box[0], box[1], box[2], box[3], Math.min(16, box[4]), box[5]);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (!(player.getHeldItem(hand).getItem() instanceof ItemSignalWrench)) return false;
        TileEntity t = world.getTileEntity(pos);
        if (!(t instanceof SafetyTile g)) return false;
        if (world.isRemote) return true;
        Panel.C c = IControlled.aimed(g, player);
        BlockPos pick = ItemSignalWrench.getPick(player.getHeldItem(hand));
        String r;
        if (c == null) r = "§7Aim the wrench at the control you want to link (a section, berth, target...)";
        else if (pick == null) r = "§7Pick something with the Signal Wrench first (right-click a signal, rail, head or mast), then click " + c.label.replace("|", " ");
        else r = g.link(c.id, pick, player);
        if (r == null) r = "§7The wrench can't link anything to " + (c == null ? "that" : c.label.replace("|", " "));
        player.sendStatusMessage(new TextComponentString(r), true);
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) SafetyTicker.removed(world, pos);
        if (!world.isRemote && world.getTileEntity(pos) instanceof com.dogpound.railmap.block.IFurniture f) f.dropAll();   // desks / racks with drawers
        super.breakBlock(world, pos, state);
    }
}
