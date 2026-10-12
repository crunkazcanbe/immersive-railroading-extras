package com.dogpound.railmap.light;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** night lighting blocks: luminaires, the floodlight mast, the photocell controller, the contactor panel, the invisible light block */
public final class LightBlocks {
    private LightBlocks() {}

    public static List<Block> create() {
        LightHelpers.helper = new BlockLightHelper();
        return Arrays.asList(
                new BlockLuminaire("canopy_light", TileLuminaire.Canopy::new, new double[]{1, 9, 5, 15, 16, 11},
                        "Canopy Light: LED batten hung under a platform canopy - real light 15",
                        "AUTO / ON / OFF, CIRCUIT 1-4 for the contactor panel. Mains or 12 V. Ages + fails (asset register)"),
                new BlockLuminaire("yard_floodlight", TileLuminaire.Flood::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Yard Floodlight: twin floodlight head for the top of a mast - lights the yard ahead of it",
                        "Throws real light 20 blocks along its facing. AUTO / ON / OFF, CIRCUIT. Mains or 12 V"),
                new BlockMastSection(),
                new BlockLuminaire("station_name_sign", TileLuminaire.Sign::new, new double[]{0, 0, 6, 16, 16, 10},
                        "Lit Station Name Sign: the nearest station's name, lit from its hood at night",
                        "Name from the station (StationData). AUTO / ON / OFF, CIRCUIT. Mains or 12 V"),
                new BlockLuminaire("emergency_light", TileLuminaire.Emergency::new, new double[]{1, 5, 9, 15, 16, 16},
                        "Emergency Light: twin-spot maintained emergency luminaire with its own battery",
                        "Stays lit for 3 h on battery in a power cut. MAINTAINED / NON-MAINT key, battery TEST"),
                new com.dogpound.railmap.depot.DepotBlock("lighting_controller", TileLightController::new, new double[]{3, 1, 11, 13, 16, 16},
                        "Photocell Lighting Controller: dusk-dawn switch with a live lux readout and night-cut timer",
                        "Its output switches the lamps (or the contactor panel) on its control wire. 12 V"),
                new com.dogpound.railmap.depot.DepotBlock("lighting_contactor", TileLightingContactor::new, new double[]{0, 0, 7, 16, 16, 16},
                        "Lighting Contactor Panel: four lighting circuits K1-K4, HAND / OFF / AUTO each",
                        "Switches every lamp on its control wire by circuit; counts lamps, alarms failures. 12 V"),
                LightHelpers.helper);
    }

    public static void registerTiles() {
        reg(TileLuminaire.Canopy.class, "canopy_light");
        reg(TileLuminaire.Flood.class, "yard_floodlight");
        reg(TileLuminaire.Sign.class, "station_name_sign");
        reg(TileLuminaire.Emergency.class, "emergency_light");
        reg(TileLightController.class, "lighting_controller");
        reg(TileLightingContactor.class, "lighting_contactor");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
