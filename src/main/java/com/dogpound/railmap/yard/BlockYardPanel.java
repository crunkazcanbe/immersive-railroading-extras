package com.dogpound.railmap.yard;

import com.dogpound.railmap.block.BlockStationDevice;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.item.ItemSignalWrench;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/** The Hump Yard Control Panel (see TileYardPanel). Signal Wrench with a picked rail on a lamp / route button = link it. */
public class BlockYardPanel extends BlockStationDevice {
    public BlockYardPanel() {
        super("yard_panel", Material.IRON,
                "Hump / flat yard control: cut list from waybills, routes to 8 class tracks, retarders, driverless pusher",
                "Signal Wrench: pick a rail, click CREST / RETARDER / a route button to link it; sneak = settings. Needs 12 V DC");
        setHardness(3f);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hx, float hy, float hz) {
        ItemStack held = player.getHeldItem(hand);
        if (!(held.getItem() instanceof ItemSignalWrench) || player.isSneaking()) return false;
        if (world.isRemote) return true;
        TileEntity t = world.getTileEntity(pos);
        if (!(t instanceof TileYardPanel y)) return true;
        BlockPos pick = ItemSignalWrench.getPick(held);
        Panel.C c = IControlled.aimed(y, player);
        String msg = pick == null ? y.linkStatus() + " §8(pick a rail with the wrench first)" : y.link(c == null ? null : c.id, pick);
        player.sendStatusMessage(new TextComponentString(msg), true);
        return true;
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileYardPanel(); }
}
