package com.dogpound.railmap.cctv;

import com.dogpound.railmap.item.ItemSignalWrench;
import com.dogpound.railmap.safety.SafetyBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.function.Supplier;

/** Every CCTV block but the desk: the cameras, the wall / ceiling monitors and the NVR rack. */
public class CctvBlock extends SafetyBlock {
    public enum Kind { CAMERA, MONITOR, CEILING_MONITOR, NVR }

    public final Kind kind;
    public final CamType cam;

    public CctvBlock(String id, Kind kind, CamType cam, Supplier<TileEntity> tile, double[] box, String... tips) {
        super(id, tile, box, tips);
        this.kind = kind;
        this.cam = cam;
        setHardness(kind == Kind.NVR ? 3f : 1.5f);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (player.getHeldItem(hand).getItem() instanceof ItemSignalWrench) return super.onBlockActivated(world, pos, state, player, hand, side, hx, hy, hz);
        if (player.getHeldItem(hand).getItem() instanceof ItemConfigurator) return false;     // the configurator's own use opens the camera set-up
        TileEntity t = world.getTileEntity(pos);
        if (t instanceof TileMonitor m) {
            if (world.isRemote) com.dogpound.railmap.cctv.client.CctvClient.openMonitor(m.master().getPos());
            return true;
        }
        if (world.isRemote) return t instanceof CctvTile;
        if (t instanceof TileCamera c) player.sendStatusMessage(new TextComponentString("§bCAM " + c.no + " " + c.name + "§7 - " + c.gearState() + " §8(CCTV Configurator to set it up)"), true);
        else if (t instanceof TileNvr n) player.sendStatusMessage(new TextComponentString("§bNVR§7 - " + n.gearState()), true);
        return t instanceof CctvTile;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileCamera c && c.no > 0) {
            if (c.running()) c.videoLoss("camera removed / destroyed");
            CctvData d = CctvData.get(world);
            d.cams.remove(c.no);
            d.changed();
            Cctv.CAMS.remove(c);
        }
        super.breakBlock(world, pos, state);
    }
}
