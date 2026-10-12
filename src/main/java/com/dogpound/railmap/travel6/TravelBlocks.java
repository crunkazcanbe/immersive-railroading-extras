package com.dogpound.railmap.travel6;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import com.dogpound.railmap.depot.DepotDesk;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** passenger travel blocks (batch 6b #6-10); CommonProxy registers them (the boom arm registers itself in TravelRegistry) */
public final class TravelBlocks {
    private TravelBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new DepotBlock("card_reader", TileCardReader::new, new double[]{5, 0, 5, 11, 16, 11},
                        "Pride Card reader: touch a Pride Card to tap in / out at a station",
                        "MODE key AUTO / ENTRY / EXIT · fare from the fare zones · daily cap. 12 V"),
                new DepotBlock("card_topup", TileTopUp::new, new double[]{1, 0, 3, 15, 16, 15},
                        "Pride Card Top-Up Machine: new cards, top-ups, balance, journey history, lost cards, refunds",
                        "Touch a card on the pad · pages BALANCE / HISTORY / TOP UP / SERVICES · card stock behind the SERVICE key. 12 V + mains"),
                new DepotDesk("left_luggage", Material.IRON, TileLeftLuggage::new,
                        "Left luggage: twelve lockers (8 small, 4 large) rented by the hour, opened with your own PIN",
                        "Click a free door, HOURS + RENT, key a PIN + ENT · overstay charged · tamper + uncollected alarms · STAFF key. 12 V + mains"),
                new DepotDesk("bag_checkin", Material.WOOD, TileBagCheckin::new,
                        "Baggage check-in desk: weigh, tag and check a bag through to another station",
                        "BAG DROP scale, DEST < >, WEIGH, TAG (claim tag) · bags go onto a Baggage Belt behind it or wait in the hold. 12 V + mains"),
                new DepotBlock("bag_belt", TileBagBelt::new, new double[]{0, 0, 0, 16, 7, 16},
                        "Baggage belt: carries bags (and anything) away from whoever laid it, belt to belt",
                        "Hands bags to a Baggage Hall make-up cart or a check-in hold · START / STOP / E-STOP · jam alarm. 12 V + mains"),
                new DepotDesk("bag_hall", Material.IRON, TileBagHall::new,
                        "Baggage Hall: reclaim carousel (bags going round) + make-up cart for outbound bags",
                        "Show your claim tag at the carousel · trains with a baggage car load / unload here. 12 V + mains"),
                new BlockParkBarrier(),
                new DepotBlock("park_pay", TileParkPay::new, new double[]{3, 0, 4, 13, 16, 12},
                        "Park & ride pay station with the SPACES FREE sign: counts the vehicles really parked",
                        "Ticket in the slot · PAY (bank) or touch a Pride Card · park & ride rate after a rail journey · GARAGE radius + bays. 12 V + mains"));
    }

    public static void registerTiles() {
        reg(TileCardReader.class, "card_reader");
        reg(TileTopUp.class, "card_topup");
        reg(TileLeftLuggage.class, "left_luggage");
        reg(TileBagCheckin.class, "bag_checkin");
        reg(TileBagBelt.class, "bag_belt");
        reg(TileBagHall.class, "bag_hall");
        reg(TileParkBarrier.class, "park_barrier");
        reg(TileParkPay.class, "park_pay");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
