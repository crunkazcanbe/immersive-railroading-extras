package com.dogpound.railmap.modelrail;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.depot.DepotBlock;
import com.dogpound.railmap.item.ItemSignalWrench;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.List;

/** Registers the model railway's blocks, items and tile on its own (no shared proxy lists touched). */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class ModelRailRegistry {
    private ModelRailRegistry() {}

    public static Block desk, table;
    public static ItemMiniStock stock;

    /** the control desk: right-click anywhere but a control opens the layout editor */
    public static final class BlockDesk extends DepotBlock {
        public BlockDesk() {
            super("model_railway_controller", TileModelRailway::new, new double[]{0, 0, 1, 16, 16, 16},
                    "Model Railway Control Desk: two cab controllers, points P1-P6, signals S1-S4, key, E-stop, meters, screens, fast clock",
                    "Build a 2x2 to 4x4 Model Railway Table behind it. Right-click the table to edit the layout and place trains. MIRROR mode shows the real railway. 12 V + mains");
        }

        @Override
        public boolean onBlockActivated(World w, BlockPos pos, IBlockState state, EntityPlayer p, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
            if (p.getHeldItem(hand).getItem() instanceof ItemSignalWrench) return super.onBlockActivated(w, pos, state, p, hand, side, hx, hy, hz);
            if (p.isSneaking()) return false;
            if (w.isRemote) com.dogpound.railmap.modelrail.client.ModelRailClient.open(pos);
            return true;
        }
    }

    /** one baseboard section: four legs, fascia, green baize top. The desk in front of 2x2..4x4 of them runs the layout */
    public static final class BlockTable extends Block {
        private static final AxisAlignedBB BOX = new AxisAlignedBB(0, 0, 0, 1, 13 / 16.0, 1);

        public BlockTable() {
            super(Material.WOOD);
            setRegistryName(RailMap.MODID, "model_railway_table");
            setTranslationKey(RailMap.MODID + ".model_railway_table");
            setCreativeTab(RailMapTab.INSTANCE);
            setHardness(1.5f);
            setSoundType(SoundType.WOOD);
        }

        @Override public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return BOX; }
        @Override public boolean isOpaqueCube(IBlockState s) { return false; }
        @Override public boolean isFullCube(IBlockState s) { return false; }
        @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }
        @Override public BlockFaceShape getBlockFaceShape(IBlockAccess w, IBlockState s, BlockPos p, EnumFacing f) { return f == EnumFacing.DOWN ? BlockFaceShape.UNDEFINED : f == EnumFacing.UP ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED; }

        @Override
        public boolean onBlockActivated(World w, BlockPos pos, IBlockState state, EntityPlayer p, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
            if (p.isSneaking()) return false;
            TileModelRailway t = TileModelRailway.owner(w, pos);
            if (t == null) {
                if (!w.isRemote) p.sendStatusMessage(new TextComponentString("§7Put a Model Railway Control Desk at the front edge of a 2x2 to 4x4 table"), true);
                return true;
            }
            if (w.isRemote) com.dogpound.railmap.modelrail.client.ModelRailClient.open(t.getPos());
            return true;
        }

        @Override
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
            tip.add("§7Model railway baseboard section (legs, fascia, baize top)");
            tip.add("§8Place 2x2 up to 4x4 of them behind a Model Railway Control Desk");
        }
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> e) {
        e.getRegistry().registerAll(desk = new BlockDesk(), table = new BlockTable());
        GameRegistry.registerTileEntity(TileModelRailway.class, new ResourceLocation(RailMap.MODID, "model_railway"));
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(new ItemBlock(desk).setRegistryName(desk.getRegistryName()));
        e.getRegistry().register(new ItemBlock(table).setRegistryName(table.getRegistryName()));
        e.getRegistry().register(stock = new ItemMiniStock());
    }
}
