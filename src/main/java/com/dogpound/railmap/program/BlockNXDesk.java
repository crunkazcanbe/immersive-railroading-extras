package com.dogpound.railmap.program;

import com.dogpound.railmap.block.BlockDispatcherBoard;
import com.dogpound.railmap.graph.RailNetwork;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockNXDesk extends BlockDispatcherBoard {
    public BlockNXDesk() {
        super("nx_desk");
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
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return new AxisAlignedBB(0, 0, 0, 1, 13.0 / 16, 1);
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) return false;
        if (world.isRemote) return true;
        TileNXDesk desk = (TileNXDesk) world.getTileEntity(pos);
        if (desk == null) return true;
        RailNetwork net = desk.getNetwork();
        if (net.signals == null || net.signals.isEmpty()) {
            player.sendStatusMessage(new TextComponentString("No signals found near this desk (it maps the track around it)"), true);
            return true;
        }
        Vec3d eye = player.getPositionEyes(1f);
        Vec3d look = player.getLook(1f);
        double planeY = pos.getY() + NXLayout.TOP;
        if (look.y >= -0.01) return true;
        double t = (planeY - eye.y) / look.y;
        double lx = eye.x + look.x * t - pos.getX();
        double lz = eye.z + look.z * t - pos.getZ();
        NXLayout L = new NXLayout(net, state.getValue(FACING));
        if (!L.onTop(lx, lz)) {
            player.sendStatusMessage(new TextComponentString("Look at the desk top and press a signal button"), true);
            return true;
        }
        com.dogpound.railmap.graph.SignalNode best = null;
        double bestDist = Double.MAX_VALUE;
        for (com.dogpound.railmap.graph.SignalNode sig : net.signals) {
            double[] d = L.onDesk(sig.pos.getX() + 0.5, sig.pos.getZ() + 0.5);
            double dx = d[0] - lx;
            double dz = d[1] - lz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < bestDist) {
                bestDist = dist;
                best = sig;
            }
        }
        if (best == null || bestDist > 0.09) {
            player.sendStatusMessage(new TextComponentString("No signal button there"), true);
        } else {
            String msg = desk.press(best.pos, player);
            player.sendStatusMessage(new TextComponentString(msg), true);
        }
        return true;
    }

    @Override
    public net.minecraft.tileentity.TileEntity createTileEntity(World world, IBlockState state) {
        return new TileNXDesk();
    }
}
