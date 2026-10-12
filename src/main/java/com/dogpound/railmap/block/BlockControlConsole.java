package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.proxy.CommonProxy;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center console (requested feature). 3 wide x 2 tall: this block is the middle of the desk; five invisible
 * {@link BlockConsolePart}s fill the rest so it's solid and every part of it clicks through to here.
 *
 * Right-click a control on the desk to operate it; right-click the big screen (or anywhere else) to open the full
 * Control Center. Electrician's screwdriver repairs a burnt-out console.
 */
public class BlockControlConsole extends BlockDispatcherBoard {
    public BlockControlConsole() {
        super("dispatcher_board");
    }

    /** the five other blocks of the console: desk left / right, and the screen row above all three */
    public static List<BlockPos> parts(BlockPos pos, EnumFacing f) {
        List<BlockPos> l = new ArrayList<>();
        BlockPos left = pos.offset(f.rotateY()), right = pos.offset(f.rotateYCCW());
        l.add(left);
        l.add(right);
        l.add(pos.up());
        l.add(left.up());
        l.add(right.up());
        return l;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote) return;
        EnumFacing f = state.getValue(FACING);
        for (BlockPos p : parts(pos, f)) {
            if (!world.getBlockState(p).getBlock().isReplaceable(world, p)) {
                world.setBlockToAir(pos);
                if (placer instanceof EntityPlayer) {
                    EntityPlayer pl = (EntityPlayer) placer;
                    if (!pl.capabilities.isCreativeMode) pl.inventory.addItemStackToInventory(new ItemStack(this));
                    pl.sendStatusMessage(new TextComponentString("§cThe Control Center needs a clear space 3 wide and 2 tall"), true);
                }
                return;
            }
        }
        for (BlockPos p : parts(pos, f)) world.setBlockState(p, CommonProxy.consolePart.getDefaultState(), 3);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        EnumFacing f = state.getValue(FACING);
        super.breakBlock(world, pos, state);
        for (BlockPos p : parts(pos, f)) {
            if (world.getBlockState(p).getBlock() == CommonProxy.consolePart) world.setBlockToAir(p);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return true;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileDispatcherBoard)) return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
        TileDispatcherBoard c = (TileDispatcherBoard) te;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() instanceof com.dogpound.railmap.grid.ItemScrewdriver) {
            if (!world.isRemote) {
                String r = c.repair();
                player.sendStatusMessage(new TextComponentString(r != null ? r : c.burnt ? "" : "§7Console: mains " + c.acWhat + " · desk " + c.dcWhat), true);
            }
            return true;
        }
        Panel.C hit = c.hit(player);
        if (hit != null) {
            if (!world.isRemote) c.press(hit.id, player);
            return true;
        }
        // anywhere else (the big screen, the cabinet): the full Control Center - its computer needs the mains
        if (!c.screenOn()) {
            if (!world.isRemote) player.sendStatusMessage(new TextComponentString(c.burnt ? "\u00a7cBurnt out - repair it with the electrician's screwdriver"
                    : "\u00a7cNo power to the control center: " + c.acWhat), true);
            return true;
        }
        return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
    }
}
