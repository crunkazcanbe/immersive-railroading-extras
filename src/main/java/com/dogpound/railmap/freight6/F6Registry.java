package com.dogpound.railmap.freight6;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch 6 freight + fun (#26 grain elevator, #27 oil tank farm, #28 rail-served warehouse, #29 parcel lockers,
 * #30 holiday trains) registers itself: blocks, tiles, the decoration kit, the forklift and its own network channel -
 * no shared proxy lists touched.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class F6Registry {
    private F6Registry() {}

    public static final List<Block> BLOCKS = new ArrayList<>();
    public static ItemHolidayKit kit;

    static Block b(String id, java.util.function.Supplier<net.minecraft.tileentity.TileEntity> t, double[] box, String... tips) {
        Block x = new DepotBlock(id, t, box, tips);
        BLOCKS.add(x);
        return x;
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> e) {
        F6Net.init();
        double[] full = {0, 0, 0, 16, 16, 16};
        // #26 grain elevator
        b("grain_pit", TileGrainPit::new, new double[]{0, 0, 0, 16, 4, 16},
                "Grain Receiving Pit: grated hopper, pit conveyor, dust collector, intake scale - empties the hopper car standing over it",
                "START opens the car gate (grain only), PROBE samples moisture, DUST key runs the dust collector. Mains + 12 V");
        b("grain_leg", TileGrainLeg::new, full,
                "Grain Leg: bucket elevator with head house, distributor to BIN 1-6 or the DRYER, the plant MCC and control desk",
                "Lifts grain from the pit (12 blocks) to the bin / dryer you pick. AUTO, choke alarm, ammeter, paged screen. Mains + 12 V");
        b("grain_dryer", TileGrainDryer::new, full,
                "Grain Dryer: mixed-flow tower dryer with garner, coal burner, fan, plenum and cooling section",
                "Fill it via the leg (DRYER), BURNER key + START: dries to the TARGET % and discharges to a bin. Coal / charcoal fuel. Mains + 12 V");
        b("grain_silo", TileGrainSilo::new, full,
                "Grain Silo: corrugated steel bin on a hopper bottom - level gauge, cable thermometers, aeration fan, outlet gate",
                "Stores 1 000 t of one grain. Wet grain heats (HOT SPOT) - aerate or dry it. Gate lever feeds the loadout. Mains + 12 V");
        b("grain_loadout", TileGrainLoadout::new, full,
                "Grain Loadout: batch-weigh loadout over the track - surge bin, weigh hopper, telescopic spout",
                "FROM BIN, QTY, lower the SPOUT, START: 10 t batches weighed into the hopper car under it. Mains + 12 V");
        // #27 oil tank farm
        b("oil_tank", TileOilTank::new, full,
                "Oil Storage Tank: welded tank in its bund - 400 000 L, servo gauge, foam chambers, spiral stair, valve manifold",
                "Pipes in through the INLET valve, out through the OUTLET valve; buckets via FILL. HIGH / HIGH-HIGH alarms. 12 V");
        b("oil_rack", TileOilRack::new, full,
                "Tank Car Loading Rack: top-loading gantry with arm, preset meter, ground verification, overfill probe, vapour recovery",
                "TANK, preset, GROUND, ARM, START - loads (or UNLOADs) the tank car standing at it. Mains + 12 V");
        b("oil_firegas", TileFireGas::new, full,
                "Tank Farm Fire and Gas Panel: gas detection (% LEL), heat detection, deluge + foam system, ESD, beacon and horn",
                "Gas 25 % LEL or fire = ESD of every rack and tank. AUTO deluge puts fires out (water + slimeball foam drums). Mains + 12 V");
        // #28 warehouse
        b("warehouse_dock", TileWarehouseDock::new, full,
                "Warehouse Dock Door: roll-up door, dock leveller, shelter, traffic lights, dock lamp and control station",
                "OPEN the door, lower the LEVELLER, DERAIL key on: SECURED - the forklifts can work the car. Mains + 12 V");
        b("warehouse_rack", TileWarehouseRack::new, full,
                "Warehouse Pallet Rack: three-level selective racking - 3 x 27 slots of real storage, shows its pallets",
                "Click a level to open it. The Warehouse Office's forklifts put away into / pick from racks within 24 blocks");
        b("warehouse_office", TileWarehouseOffice::new, full,
                "Warehouse Office: WMS desk with paged screen, pick-list tray, radio base - runs 1-3 forklift drivers",
                "DOCK, then UNLOAD (car to racks) or LOAD (racks to car, pick list = what to load). AUTO unloads secured docks. 12 V");
        // #29 parcel lockers
        b("parcel_lockers", TileParcelLockers::new, full,
                "Station Parcel Lockers: 24 compartments (S / M / L) round a keypad tower - opened by a 6-digit code",
                "Mail for players without a mailbox, courier DROPs and RealmCoin robot orders (REGISTER) arrive here. Codes by chat. 12 V");
        // #30 holidays
        b("holiday_controller", TileHolidayController::new, new double[]{3, 0, 4, 13, 16, 13},
                "Festive Lighting Controller: season (date / forced), time switch, light patterns, light show, PA greeting",
                "Feeds every festive display within 48 blocks. 12 V + mains");
        b("xmas_tree", TileXmasTree::new, new double[]{3, 0, 3, 13, 16, 13},
                "Station Christmas Tree: spruce in a planter, baubles, tinsel, star and 12 V LED string - presents under it",
                "Lit by a Festive Lighting Controller in the Christmas season. Christmas trains leave presents. 12 V");
        b("holiday_lights", TileHolidayLights::new, new double[]{0, 10, 12, 16, 16, 16},
                "Festive String Lights: canopy garland with a swagged 12 V LED festoon and bows",
                "Colours and pattern from the Festive Lighting Controller. 12 V");
        b("halloween_display", TileHalloweenDisplay::new, new double[]{1, 0, 1, 15, 14, 15},
                "Halloween Display: jack-o'-lanterns on hay bales, scarecrow, cobwebbed lamp post, TRICK OR TREAT sign, treat bowl",
                "Lanterns flicker in the Halloween season (Festive Lighting Controller). Ghost trains fill the bowl at night. 12 V");
        for (Block x : BLOCKS) e.getRegistry().register(x);
        reg(TileGrainPit.class, "grain_pit"); reg(TileGrainLeg.class, "grain_leg"); reg(TileGrainDryer.class, "grain_dryer"); reg(TileGrainSilo.class, "grain_silo");
        reg(TileGrainLoadout.class, "grain_loadout"); reg(TileOilTank.class, "oil_tank"); reg(TileOilRack.class, "oil_rack"); reg(TileFireGas.class, "oil_firegas");
        reg(TileWarehouseDock.class, "warehouse_dock"); reg(TileWarehouseRack.class, "warehouse_rack"); reg(TileWarehouseOffice.class, "warehouse_office");
        reg(TileParcelLockers.class, "parcel_lockers"); reg(TileHolidayController.class, "holiday_controller"); reg(TileXmasTree.class, "xmas_tree");
        reg(TileHolidayLights.class, "holiday_lights"); reg(TileHalloweenDisplay.class, "halloween_display");
    }

    static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) { GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id)); }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        for (Block x : BLOCKS) e.getRegistry().register(new ItemBlock(x).setRegistryName(x.getRegistryName()));
        e.getRegistry().register(kit = new ItemHolidayKit());
    }

    @SubscribeEvent
    public static void entities(RegistryEvent.Register<EntityEntry> e) {
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityForklift.class).id(new ResourceLocation(RailMap.MODID, "forklift"), 7841)
                .name(RailMap.MODID + ".forklift").tracker(96, 1, false).build());
    }
}
