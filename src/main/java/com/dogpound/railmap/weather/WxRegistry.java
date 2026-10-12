package com.dogpound.railmap.weather;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.block.BlockWideDesk;
import com.dogpound.railmap.cctv.TrainCams;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.List;

/** Registers the weather-train gear on its own (her IR Extras list #4): the depot desk and the plough attachment. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class WxRegistry {
    private WxRegistry() {}

    public static Block depot;
    public static Item plough;

    public static final class BlockWxDepot extends BlockWideDesk {
        public BlockWxDepot() {
            super("wx_depot_desk", Material.WOOD, TileWxDepot::new,
                    "Weather Train Depot desk: plans snowplough, sandite (railhead treatment) and de-icing runs, calls the trains",
                    "Forecast panel + run board, AUTO key schedules on the forecast. Drawers, sandite stock cupboard, shelf. 12 V + 120 / 240 V");
        }
    }

    /** a snowplough bolted to the front of a loco: use beside an IR locomotive to fit it, sneak-use to take it off */
    public static final class ItemPlough extends Item {
        public ItemPlough() {
            setRegistryName(RailMap.MODID, "snow_plough");
            setTranslationKey(RailMap.MODID + ".snow_plough");
            setMaxStackSize(4);
            setCreativeTab(RailMapTab.INSTANCE);
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            ItemStack s = p.getHeldItem(hand);
            if (w.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, s);
            EntityRollingStock rs = TrainCams.nearLoco(w, p);
            if (rs == null) { p.sendStatusMessage(new TextComponentString("§7Stand next to a locomotive (within 6 blocks) to fit the plough"), true); return new ActionResult<>(EnumActionResult.FAIL, s); }
            WxTrainData d = WxTrainData.get(w);
            String id = rs.getUUID().toString(), name = TrainCams.name(rs);
            if (p.isSneaking()) {
                if (!d.fitted.remove(id)) { p.sendStatusMessage(new TextComponentString("§7" + name + " has no plough fitted"), true); return new ActionResult<>(EnumActionResult.FAIL, s); }
                d.markDirty();
                if (!p.addItemStackToInventory(new ItemStack(this))) p.dropItem(new ItemStack(this), false);
                p.sendStatusMessage(new TextComponentString("§eSnowplough taken off " + name), true);
                return new ActionResult<>(EnumActionResult.SUCCESS, s);
            }
            if (!d.fitted.add(id)) { p.sendStatusMessage(new TextComponentString("§7" + name + " already has a plough"), true); return new ActionResult<>(EnumActionResult.FAIL, s); }
            d.markDirty();
            d.log(w, "snowplough fitted to " + name + " by " + p.getName());
            if (!p.capabilities.isCreativeMode) s.shrink(1);
            p.sendStatusMessage(new TextComponentString("§aSnowplough fitted to " + name + " - it clears snow and sweeps the rail head above 5 km/h"), true);
            return new ActionResult<>(EnumActionResult.SUCCESS, s);
        }

        @Override
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
            tip.add("§7Use beside an IR locomotive to fit it; sneak-use to take it off");
            tip.add("§8Clears snow and ice from the track it runs over, throws it aside, restores adhesion");
        }
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> e) {
        e.getRegistry().register(depot = new BlockWxDepot());
        GameRegistry.registerTileEntity(TileWxDepot.class, new ResourceLocation(RailMap.MODID, "wx_depot_desk"));
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(new ItemBlock(depot).setRegistryName(depot.getRegistryName()));
        e.getRegistry().register(plough = new ItemPlough());
    }
}
