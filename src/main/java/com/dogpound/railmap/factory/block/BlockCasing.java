package com.dogpound.railmap.factory.block;

import com.dogpound.railmap.factory.Factory;
import com.dogpound.railmap.factory.machine.IndustryData;
import com.dogpound.railmap.factory.machine.TileMachine;
import com.dogpound.railmap.factory.ore.FactoryTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Machine Casing: the steel frame every plant is built from. Once its machine is commissioned the casing is drawn as
 * part of the machine's one big model (so it hides itself); break it and the machine stops being a machine.
 */
public class BlockCasing extends Block {
    public static final PropertyBool FORMED = PropertyBool.create("formed");

    public BlockCasing() {
        super(Material.IRON);
        setRegistryName(Factory.MODID, "machine_casing");
        setTranslationKey(Factory.MODID + ".machine_casing");
        setHardness(4f); setResistance(12f); setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(FactoryTab.INSTANCE);
        setDefaultState(blockState.getBaseState().withProperty(FORMED, false));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FORMED); }
    @Override public IBlockState getStateFromMeta(int m) { return getDefaultState().withProperty(FORMED, m == 1); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(FORMED) ? 1 : 0; }
    @Override @SuppressWarnings("deprecation")
    public EnumBlockRenderType getRenderType(IBlockState s) { return s.getValue(FORMED) ? EnumBlockRenderType.INVISIBLE : EnumBlockRenderType.MODEL; }
    @Override @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState s) { return !s.getValue(FORMED); }

    /** the controller this formed part belongs to, or null */
    public static BlockPos ownerOf(World w, BlockPos p) { return w.isRemote ? null : IndustryData.get(w).owner(p); }

    @Override
    public void breakBlock(World w, BlockPos p, IBlockState s) {
        unformOwner(w, p);
        super.breakBlock(w, p, s);
    }

    public static void unformOwner(World w, BlockPos p) {
        if (w.isRemote) return;
        BlockPos o = IndustryData.get(w).owner(p);
        if (o != null && w.getTileEntity(o) instanceof TileMachine m) m.unform("a part was removed at " + p.getX() + " " + p.getY() + " " + p.getZ());
        IndustryData.get(w).clear(p);
    }
}
