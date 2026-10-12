package com.dogpound.railmap.cctv;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.BlockWideDesk;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.ArrayList;
import java.util.List;

/** Registers the CCTV blocks, items and tiles on its own (no shared proxy lists touched). */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class CctvRegistry {
    private CctvRegistry() {}

    public static final List<Block> BLOCKS = new ArrayList<>();
    public static ItemDrive drive;
    public static ItemTrainCamera trainCamera;
    public static ItemConfigurator configurator;
    public static Block ptz;

    /** the desk: three blocks wide (invisible solid ends), 12 V + mains */
    public static final class BlockCctvDesk extends BlockWideDesk {
        public BlockCctvDesk() {
            super("cctv_desk", Material.IRON, TileCctvDesk::new,
                    "CCTV control desk: video matrix keypad, PTZ joystick, zoom / focus / iris, tours, alarm windows, spot monitor + 2x2 live preview wall",
                    "N CAM / M MON / N PRESET / N SET on the keypad; ARM key = motion alarms; REC LOCK = NVR footage lock. 12 V + mains");
        }
    }

    private static CctvBlock cam(CamType t, double[] box, String a, String b) {
        return new CctvBlock(t.block, CctvBlock.Kind.CAMERA, t, TileCamera::new, box, a, b + " Needs 12 V + PoE (NVR switch) or 230 V mains; wire it to an NVR.");
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> e) {
        BLOCKS.add(cam(CamType.DOME, new double[]{3, 6, 3, 13, 16, 13}, "Ceiling dome camera: vandal-resistant smoked dome, 70° lens", "Hang it under a ceiling."));
        BLOCKS.add(cam(CamType.BULLET, new double[]{4, 6, 0, 12, 14, 16}, "Bullet camera: wall-mount, sunshield, 58° lens", "Mount on a wall; aim it with the Configurator."));
        BLOCKS.add(ptz = cam(CamType.PTZ, new double[]{3, 3, 2, 13, 16, 16}, "PTZ camera: pan / tilt / zoom motors (30x), presets, wiper", "Drive it from the CCTV desk joystick."));
        BLOCKS.add(cam(CamType.FISHEYE, new double[]{2, 11, 2, 14, 16, 14}, "Fisheye 360 camera: ceiling disc, 140° circular image, digital dewarp + pan", "Dewarp in the monitor menu."));
        BLOCKS.add(cam(CamType.NIGHT, new double[]{4, 6, 0, 12, 14, 16}, "Night / IR camera: IR illuminator ring, monochrome at night", "IR mode AUTO / ON / OFF."));
        BLOCKS.add(cam(CamType.THERMAL, new double[]{4, 5, 0, 12, 14, 16}, "Thermal camera: false-colour palette, living things glow hot", "Sees in total darkness."));
        BLOCKS.add(cam(CamType.PLATFORM, new double[]{5, 0, 1, 11, 16, 11}, "Platform / station camera on a pole, 75° wide lens", "Stands on the platform."));
        BLOCKS.add(cam(CamType.DOORBELL, new double[]{5, 1, 13.5, 11, 14, 16}, "Doorbell camera: press the button - it chimes and calls the desk", "Mount on a wall by a door."));
        BLOCKS.add(new CctvBlock("cctv_monitor", CctvBlock.Kind.MONITOR, null, TileMonitor::new, new double[]{0, 0, 12, 16, 16, 16},
                "CCTV wall monitor: live video, CH+ / CH-, split 1/4/9, overlay, playback; side by side they merge into a video wall (up to 4x4)",
                "Right-click the screen for the menu. Needs 12 V + mains (one mains feed powers a whole wall)"));
        BLOCKS.add(new CctvBlock("cctv_ceiling_monitor", CctvBlock.Kind.CEILING_MONITOR, null, TileMonitor::new, new double[]{0, 2, 6, 16, 16, 10},
                "Ceiling-hung platform monitor: the small screen over a platform", "Same as the wall monitor. Needs 12 V + mains"));
        BLOCKS.add(new CctvBlock("cctv_nvr", CctvBlock.Kind.NVR, null, TileNvr::new, null,
                "CCTV NVR rack: 9 drive bays (real storage for hard drives), 16-port PoE switch, recording + playback",
                "Its wire bus is the video network: land a wire colour on cameras, monitors and the desk and run it here. Needs 12 V + mains"));
        BLOCKS.add(new BlockCctvDesk());
        for (Block b : BLOCKS) e.getRegistry().register(b);
        reg(TileCamera.class, "cctv_camera");
        reg(TileMonitor.class, "cctv_monitor");
        reg(TileNvr.class, "cctv_nvr");
        reg(TileCctvDesk.class, "cctv_desk");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) { GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id)); }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        for (Block b : BLOCKS) e.getRegistry().register(new ItemBlock(b).setRegistryName(b.getRegistryName()));
        e.getRegistry().registerAll(drive = new ItemDrive(), trainCamera = new ItemTrainCamera(), configurator = new ItemConfigurator());
    }
}
