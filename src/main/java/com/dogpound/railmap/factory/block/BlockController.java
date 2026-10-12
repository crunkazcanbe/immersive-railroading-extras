package com.dogpound.railmap.factory.block;

import com.dogpound.railmap.factory.Factory;
import com.dogpound.railmap.factory.machine.MachineType;
import com.dogpound.railmap.factory.machine.TileMachine;
import com.dogpound.railmap.factory.ore.FactoryTab;
import com.dogpound.railmap.grid.BlockGrid;
import com.dogpound.railmap.grid.Panel;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/**
 * A plant's controller: the block with its control panel. Place it facing you where the pattern's C is, build the
 * casing around it, then right-click it with the Electrician's Screwdriver to commission the plant.
 */
public class BlockController extends Block implements ITileEntityProvider {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public final MachineType type;

    public BlockController(MachineType type) {
        super(Material.IRON);
        this.type = type;
        setRegistryName(Factory.MODID, type.id);
        setTranslationKey(Factory.MODID + "." + type.id);
        setHardness(4f); setResistance(12f); setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(FactoryTab.INSTANCE);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(BlockCasing.FORMED, false));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, BlockCasing.FORMED); }
    @Override public IBlockState getStateFromMeta(int m) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(m & 3)).withProperty(BlockCasing.FORMED, (m & 4) != 0); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(FACING).getHorizontalIndex() | (s.getValue(BlockCasing.FORMED) ? 4 : 0); }
    @Override @SuppressWarnings("deprecation")
    public EnumBlockRenderType getRenderType(IBlockState s) { return s.getValue(BlockCasing.FORMED) ? EnumBlockRenderType.INVISIBLE : EnumBlockRenderType.MODEL; }
    @Override @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState s) { return !s.getValue(BlockCasing.FORMED); }

    @Override
    public IBlockState getStateForPlacement(World w, BlockPos p, EnumFacing f, float hx, float hy, float hz, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override public TileEntity createNewTileEntity(World w, int meta) { return new TileMachine(type); }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        t.add("§7" + type.tip);
        t.add(String.format("§8%d x %d x %d plant · draws up to %,d FE/t (%.1f kW) at the drive coupling", type.width(), type.height(), type.depth(), type.power, type.power * 2.0));
        t.add("§8Build the casing around it, then right-click with the Electrician's Screwdriver");
    }

    /** a burnt-out plant smokes all over, with the odd flame */
    @Override
    public void randomDisplayTick(IBlockState s, World w, BlockPos p, java.util.Random rnd) {
        if (w.getTileEntity(p) instanceof TileMachine wm && !wm.burnt && wm.type() == com.dogpound.railmap.factory.machine.MachineType.WELDING_CELL && wm.pv("anim") == 1) {
            // weld sparks off the seam on the jig (model point 8, 18, 25 px, turned like the plant)
            EnumFacing f = s.getValue(FACING);
            int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
            double lx = 8 / 16.0 - 0.5, lz = 25 / 16.0 - 0.5, a = Math.toRadians(-90 * turns);
            double wx = lx * Math.cos(a) + lz * Math.sin(a), wz = -lx * Math.sin(a) + lz * Math.cos(a);
            for (int k = 0; k < 4; k++)
                w.spawnParticle(net.minecraft.util.EnumParticleTypes.FIREWORKS_SPARK, p.getX() + 0.5 + wx, p.getY() + 18 / 16.0, p.getZ() + 0.5 + wz,
                        (rnd.nextDouble() - 0.5) * 0.15, rnd.nextDouble() * 0.12, (rnd.nextDouble() - 0.5) * 0.15);
        }
        if (!(w.getTileEntity(p) instanceof TileMachine m) || !m.burnt) return;
        for (int k = 0; k < 2; k++) {
            double x = p.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 3, z = p.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 3, y = p.getY() + 1 + rnd.nextDouble() * 2;
            w.spawnParticle(rnd.nextInt(3) == 0 ? net.minecraft.util.EnumParticleTypes.SMOKE_LARGE : net.minecraft.util.EnumParticleTypes.SMOKE_NORMAL, x, y, z, 0, 0.03, 0);
        }
        if (rnd.nextInt(5) == 0) w.spawnParticle(net.minecraft.util.EnumParticleTypes.FLAME, p.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 2, p.getY() + 1.0, p.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 2, 0, 0.01, 0);
    }

    @Override
    public boolean onBlockActivated(World w, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (!(w.getTileEntity(pos) instanceof TileMachine m)) return false;
        ItemStack held = player.getHeldItem(hand);
        boolean screwdriver = !held.isEmpty() && held.getItem() == com.dogpound.railmap.proxy.CommonProxy.screwdriver;
        if (w.isRemote) return true;
        if (held.getItem() instanceof com.dogpound.railmap.wire.ItemWire) {      // land a control wire on its terminal
            player.sendStatusMessage(new TextComponentString(com.dogpound.railmap.wire.WireNet.landWire(m, com.dogpound.railmap.wire.ItemWire.colour(held))), true);
            return true;
        }
        if (screwdriver && m.burnt && !player.isSneaking()) { player.sendStatusMessage(new TextComponentString(m.repair()), true); return true; }
        if (screwdriver) {
            player.sendMessage(new TextComponentString(player.isSneaking() && m.formed() ? m.unform("decommissioned with the screwdriver") : m.tryForm()));
            return true;
        }
        if (!m.formed()) {
            player.sendStatusMessage(new TextComponentString("§e" + type.label + " isn't commissioned yet: build its casing, then right-click with the Electrician's Screwdriver"), true);
            return true;
        }
        boolean front = side == state.getValue(FACING);
        if (front && m.pv("door") == 0) { player.sendStatusMessage(new TextComponentString(m.press("door", player)), true); return true; }   // open the cabinet
        Panel.C c = BlockGrid.panelHit(type.panel(), state.getValue(FACING), pos, player);
        if (front && c == null) { player.sendStatusMessage(new TextComponentString(m.press("door", player)), true); return true; }          // shut it
        player.sendStatusMessage(new TextComponentString(c != null ? m.press(c.id, player) : m.status()), true);
        return true;
    }

    @Override
    public void breakBlock(World w, BlockPos p, IBlockState s) {
        if (!w.isRemote && w.getTileEntity(p) instanceof TileMachine m && m.formed()) m.unform("its controller was removed");
        super.breakBlock(w, p, s);
    }

    @Override @SuppressWarnings("deprecation") public boolean hasComparatorInputOverride(IBlockState s) { return true; }
    @Override @SuppressWarnings("deprecation")
    public int getComparatorInputOverride(IBlockState s, World w, BlockPos p) {
        return w.getTileEntity(p) instanceof com.dogpound.railmap.factory.machine.TileMachine m ? m.comparator() : 0;
    }
}
