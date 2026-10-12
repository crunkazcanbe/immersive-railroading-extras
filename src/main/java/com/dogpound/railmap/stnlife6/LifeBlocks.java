package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import com.dogpound.railmap.depot.DepotDesk;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** batch 6c station life (#11-15): bus station, taxi rank, station hotel, pub + kitchen, boarding lift + gap filler */
public final class LifeBlocks {
    private LifeBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new DepotBlock("bus_bay", TileBusBay::new, new double[]{5, 0, 5, 11, 16, 11},
                        "Bus Bay stop pole: ROUTE + STOP wheels put it on a timetabled bus route, countdown, REQUEST",
                        "Buses stop on the road next to it. 12 V + mains"),
                new DepotBlock("bus_departures", TileBusBoard::new, new double[]{0, 0, 5, 16, 16, 11},
                        "Bus Departure Board: runs routes 1-4 between the bays (SERVICE keys, HEADWAY), SEND, RECALL, ANNOUNCE",
                        "Buses are road traffic that drive bay to bay; right-click one at a bay to board (fare). 12 V + mains"),
                new DepotBlock("taxi_rank", TileTaxiRank::new, new double[]{3, 0, 3, 13, 16, 13},
                        "Taxi Rank call point: pick a destination, CALL, get in - the meter runs, fare in Pride coins",
                        "Needs a road within 6 blocks. TARIFF selector, RANK key. 12 V + mains"),
                new DepotDesk("hotel_reception", Material.WOOD, TileHotelDesk::new,
                        "Station Hotel Reception: CHECK IN cuts a key card for a room lock, nightly rate, CHECK OUT re-keys",
                        "Key cabinet, cash drawer, linen cupboard, brochure shelf. 12 V + mains"),
                new DepotBlock("room_lock", TileRoomLock::new, new double[]{4, 1, 13, 12, 15, 16},
                        "Hotel Room Lock: card reader beside a room's IRON door - only its key card opens it",
                        "ROOM wheels, DND, CLEANED (housekeeping). Fail-secure on 12 V"),
                new DepotBlock("room_wardrobe", TileRoomWardrobe::new, new double[]{1, 0, 4, 15, 16, 16},
                        "Hotel Room Wardrobe + safe: real storage, locked to the room's key card while it is let",
                        "Hat shelf shows what is on it. Safe keypad 12 V"),
                new DepotDesk("pub_bar", Material.WOOD, TileBar::new,
                        "Station Pub Bar: three working taps (ale hand pump, lager + cider on gas), keg cellar, menu + ORDER",
                        "Pints need glasses (glass bottles) on the glass shelf; CHANGE KEGS from the cellar. 12 V + mains"),
                new DepotDesk("pub_kitchen", Material.IRON, TileKitchen::new,
                        "Pub Kitchen: fryer, grill, oven, hob turn pantry ingredients into dishes on the pass",
                        "Orders from the bar; CHEF key cooks them; HOOD fan or smoke alarm. 12 V + mains"),
                new BlockLiftDevice("boarding_lift", TileBoardingLift::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Accessibility Boarding Lift: raises you from the platform to the floor of the stopped train",
                        "STAFF key, UP/DOWN, gates interlocked with the train + gap fillers, E-STOP, HAND LOWER. 12 V + mains"),
                new BlockLiftDevice("gap_filler", TileGapFiller::new, new double[]{0, 0, 0, 16, 2, 16},
                        "Platform Gap Filler: a plate that slides out over the gap when a train stops, in before it leaves",
                        "Place at the platform edge facing the track. AUTO / OUT / IN, obstruction stop. 12 V + mains"));
    }

    public static void registerTiles() {
        reg(TileBusBay.class, "bus_bay");
        reg(TileBusBoard.class, "bus_departures");
        reg(TileTaxiRank.class, "taxi_rank");
        reg(TileHotelDesk.class, "hotel_reception");
        reg(TileRoomLock.class, "room_lock");
        reg(TileRoomWardrobe.class, "room_wardrobe");
        reg(TileBar.class, "pub_bar");
        reg(TileKitchen.class, "pub_kitchen");
        reg(TileBoardingLift.class, "boarding_lift");
        reg(TileGapFiller.class, "gap_filler");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
