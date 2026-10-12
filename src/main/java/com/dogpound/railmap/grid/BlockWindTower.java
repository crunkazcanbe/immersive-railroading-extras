package com.dogpound.railmap.grid;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/** One section of a wind turbine's steel tower: stack them on a Wind Turbine; the nacelle and rotor sit on the top one. */
public class BlockWindTower extends Block {
    private static final AxisAlignedBB TUBE = new AxisAlignedBB(3 / 16.0, 0, 3 / 16.0, 13 / 16.0, 1, 13 / 16.0);

    public BlockWindTower() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "wind_tower");
        setTranslationKey(RailMap.MODID + ".wind_tower");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(3f); setResistance(20f); setSoundType(SoundType.METAL);
    }

    @Override @SuppressWarnings("deprecation") public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return TUBE; }
    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public boolean isFullCube(IBlockState s) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        t.add("§7Stack on a Wind Turbine: the nacelle and rotor go on top");
        t.add("§7Each section is ~7 m of real tower: higher hub = stronger wind. 250 kW needs 6, 1.5 MW 10, 3 MW 14");
    }
}
