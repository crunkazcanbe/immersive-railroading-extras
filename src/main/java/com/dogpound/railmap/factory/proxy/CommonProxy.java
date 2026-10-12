package com.dogpound.railmap.factory.proxy;

import com.dogpound.railmap.factory.Factory;
import com.dogpound.railmap.factory.block.BlockCasing;
import com.dogpound.railmap.factory.block.BlockController;
import com.dogpound.railmap.factory.block.BlockPort;
import com.dogpound.railmap.factory.block.TilePort;
import com.dogpound.railmap.factory.machine.MachineType;
import com.dogpound.railmap.factory.machine.TileMachine;
import com.dogpound.railmap.factory.ore.ItemOreStage;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class CommonProxy {
    public static final List<Block> BLOCKS = new ArrayList<>();
    public static final Map<MachineType, BlockController> controllers = new EnumMap<>(MachineType.class);
    public static BlockCasing casing;
    public static ItemOreStage crushed, washed, concentrate;
    public static com.dogpound.railmap.factory.ore.ItemMaterial material;
    public static com.dogpound.railmap.factory.ore.ItemCrate crate;

    public void preInit() { MinecraftForge.EVENT_BUS.register(this); }
    public void init() {}

    @SubscribeEvent
    public void blocks(RegistryEvent.Register<Block> e) {
        casing = new BlockCasing();
        BLOCKS.add(casing);
        for (BlockPort.Kind k : BlockPort.Kind.values()) BLOCKS.add(new BlockPort(k));
        for (MachineType t : MachineType.values()) { BlockController c = new BlockController(t); controllers.put(t, c); BLOCKS.add(c); }
        for (com.dogpound.railmap.factory.conveyor.BlockConveyor.Tier t : com.dogpound.railmap.factory.conveyor.BlockConveyor.Tier.values())
            BLOCKS.add(new com.dogpound.railmap.factory.conveyor.BlockConveyor(t));
        for (com.dogpound.railmap.factory.block.BlockPipe.Kind k : com.dogpound.railmap.factory.block.BlockPipe.Kind.values())
            BLOCKS.add(new com.dogpound.railmap.factory.block.BlockPipe(k));
        for (Block b : BLOCKS) e.getRegistry().register(b);
        GameRegistry.registerTileEntity(TileMachine.class, new ResourceLocation(Factory.OLD_MODID, "machine"));
        GameRegistry.registerTileEntity(TilePort.class, new ResourceLocation(Factory.OLD_MODID, "port"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.factory.block.TilePipe.class, new ResourceLocation(Factory.OLD_MODID, "pipe"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.factory.conveyor.TileConveyor.class, new ResourceLocation(Factory.OLD_MODID, "conveyor"));
    }

    @SubscribeEvent
    public void items(RegistryEvent.Register<Item> e) {
        for (Block b : BLOCKS) e.getRegistry().register(new ItemBlock(b).setRegistryName(b.getRegistryName()));
        crushed = new ItemOreStage("crushed", "Crushed %s Ore", "Jaw crusher output (1 ore -> 2). Smelts to 1 ingot, or wash it for more");
        washed = new ItemOreStage("washed", "Washed %s Ore", "Washing plant output (1 crushed -> 2). Smelts to 1 ingot, or float it for more");
        concentrate = new ItemOreStage("concentrate", "%s Concentrate", "Flotation output (2 washed -> 3). Smelts to 1 ingot; the Induction Smelter makes 4 from 3");
        e.getRegistry().registerAll(crushed, washed, concentrate);
        material = new com.dogpound.railmap.factory.ore.ItemMaterial();
        crate = new com.dogpound.railmap.factory.ore.ItemCrate();
        e.getRegistry().registerAll(material, crate);
    }
}
