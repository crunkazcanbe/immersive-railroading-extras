package com.dogpound.railmap.depot;

import com.dogpound.railmap.block.BlockWideDesk;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.item.ItemSignalWrench;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.function.Supplier;

/**
 * A three-block-wide depot / crew / station office (TileFurniture: drawers, cupboards, shelves that show what is on
 * them). With the Signal Wrench, clicking one of its plates links whatever the wrench picked (a stabling road rail).
 */
public class DepotDesk extends BlockWideDesk {
    public DepotDesk(String id, Material m, Supplier<TileEntity> tile, String... tips) { super(id, m, tile, tips); }

    /** furniture that takes a wrench-picked block on one of its controls */
    public interface Linkable { String link(String control, BlockPos pick); }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (!(player.getHeldItem(hand).getItem() instanceof ItemSignalWrench)) return false;
        TileEntity t = world.getTileEntity(pos);
        if (!(t instanceof Linkable l) || !(t instanceof IControlled m)) return false;
        if (world.isRemote) return true;
        Panel.C c = IControlled.aimed(m, player);
        BlockPos pick = ItemSignalWrench.getPick(player.getHeldItem(hand));
        String r = c == null ? "§7Aim the wrench at the plate to link (ROAD)" : pick == null ? "§7Pick a rail with the Signal Wrench first" : l.link(c.id, pick);
        player.sendStatusMessage(new TextComponentString(r == null ? "§7Nothing to link there" : r), true);
        return true;
    }
}
