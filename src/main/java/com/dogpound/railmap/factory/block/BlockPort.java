package com.dogpound.railmap.factory.block;

import com.dogpound.railmap.factory.Factory;
import com.dogpound.railmap.factory.ore.FactoryTab;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The ports a plant has at fixed, real places: the ore hopper, the discharge chute, the water inlet and the drive
 * coupling an electric motor bolts onto. Pipes, conveyors and any mod's cables connect here.
 */
public class BlockPort extends Block implements ITileEntityProvider {
    public enum Kind {
        ITEM_IN("item_input_hatch", "Item Input Hatch", "Feed it with any pipe, conveyor or hopper"),
        ITEM_OUT("item_output_hatch", "Item Output Hatch", "Pushes the machine's products into the inventory or pipe it touches; pipes may also pull"),
        FLUID_IN("water_inlet", "Water Inlet", "Pipe water in from any mod (pumps, tanks), or set it next to water"),
        FLUID_OUT("fluid_output_hatch", "Fluid Output Hatch", "Pipes pull the machine's fluid out here (molten metal from the smelter: Tinkers' casting tables take it)"),
        DRIVE("drive_coupling", "Drive Coupling", "Bolt an IR Extras Electric Motor's shaft here (or connect any mod's power cable): the machine's power in"),
        POWER("power_inlet", "Power Inlet", "Electrical plants plug straight into an IR Extras grid here: touch it with a cable, breaker or transformer. The plant has a rated voltage - too much and it burns or BLOWS UP");

        public final String id, label, tip;
        Kind(String id, String label, String tip) { this.id = id; this.label = label; this.tip = tip; }
    }

    public final Kind kind;

    public BlockPort(Kind kind) {
        super(Material.IRON);
        this.kind = kind;
        setRegistryName(Factory.MODID, kind.id);
        setTranslationKey(Factory.MODID + "." + kind.id);
        setHardness(4f); setResistance(12f); setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(FactoryTab.INSTANCE);
        setDefaultState(blockState.getBaseState().withProperty(BlockCasing.FORMED, false));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, BlockCasing.FORMED); }
    @Override public IBlockState getStateFromMeta(int m) { return getDefaultState().withProperty(BlockCasing.FORMED, m == 1); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(BlockCasing.FORMED) ? 1 : 0; }
    @Override @SuppressWarnings("deprecation")
    public EnumBlockRenderType getRenderType(IBlockState s) { return s.getValue(BlockCasing.FORMED) ? EnumBlockRenderType.INVISIBLE : EnumBlockRenderType.MODEL; }
    @Override @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState s) { return !s.getValue(BlockCasing.FORMED); }

    @Override public TileEntity createNewTileEntity(World w, int meta) { return new TilePort(); }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) { t.add("§7" + kind.tip); }

    @Override
    public void breakBlock(World w, BlockPos p, IBlockState s) {
        BlockCasing.unformOwner(w, p);
        super.breakBlock(w, p, s);
    }
}
