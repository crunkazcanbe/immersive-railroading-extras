package com.dogpound.railmap.shop;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotDesk;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** station shops (#5): newsstand, coffee kiosk, ticket office counter (wide desks) */
public final class ShopBlocks {
    private ShopBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new DepotDesk("newsstand", Material.WOOD, TileNewsstand::new,
                        "Newsstand: prints and sells The Pride Rail Times - a real daily paper from the day's events",
                        "Stock paper + ink sacs, PRINT, BUY. Headlines on the front board. 12 V + mains"),
                new DepotDesk("coffee_kiosk", Material.WOOD, TileCoffeeKiosk::new,
                        "Coffee Kiosk: espresso machine, milk fridge, menu board",
                        "Cocoa beans, milk buckets, paper cups. BOILER key, then order a drink. 12 V + mains"),
                new DepotDesk("ticket_office", Material.WOOD, TileTicketOffice::new,
                        "Ticket Office Counter: a staffed booking window selling the railway's tickets",
                        "Keypad / DEST, SINGLE / RETURN, ISSUE. Paper = blank ticket stock. 12 V + mains"));
    }

    public static void registerTiles() {
        reg(TileNewsstand.class, "newsstand");
        reg(TileCoffeeKiosk.class, "coffee_kiosk");
        reg(TileTicketOffice.class, "ticket_office");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
