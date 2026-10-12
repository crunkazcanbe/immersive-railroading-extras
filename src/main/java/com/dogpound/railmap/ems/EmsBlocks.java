package com.dogpound.railmap.ems;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotDesk;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** emergency services (#6): the three station desks and the police cordon barrier */
public final class EmsBlocks {
    private EmsBlocks() {}

    public static BlockCordon cordon;

    public static List<Block> create() {
        cordon = new BlockCordon();
        return Arrays.asList(
                new DepotDesk("fire_station", Material.IRON, TileEmergencyStation.Fire::new,
                        "Fire Station watch room: dispatch screen, turnout bell, equipment lockers - one fire engine",
                        "AUTO turns out to fires, dangerous goods and serious derailments; puts out the fire. 12 V + mains"),
                new DepotDesk("ambulance_station", Material.IRON, TileEmergencyStation.Ambulance::new,
                        "Ambulance Station: dispatch screen, alarm, drug + kit lockers - one ambulance",
                        "AUTO answers casualties; treats the injured, carries the badly hurt to bed or a station. 12 V + mains"),
                new DepotDesk("police_station", Material.IRON, TileEmergencyStation.Police::new,
                        "Police Station front desk: dispatch screen, alarm, equipment lockers - one patrol car",
                        "AUTO answers serious incidents: cordons the scene, closes nearby level crossings. 12 V + mains"),
                cordon);
    }

    public static void registerTiles() {
        reg(TileEmergencyStation.Fire.class, "fire_station");
        reg(TileEmergencyStation.Ambulance.class, "ambulance_station");
        reg(TileEmergencyStation.Police.class, "police_station");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
