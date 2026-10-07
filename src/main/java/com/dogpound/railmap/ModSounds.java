package com.dogpound.railmap;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** IR Extras' own sounds (synthesised by tools/gen_sounds.py, listed in sounds.json). */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class ModSounds {
    public static final SoundEvent DOOR_CHIME = make("door_chime");
    public static final SoundEvent DOOR_OPEN = make("door_open");
    public static final SoundEvent DOOR_WARN = make("door_warn");
    public static final SoundEvent DOOR_CLOSE = make("door_close");
    public static final SoundEvent PSD_MOVE = make("psd_move");
    public static final SoundEvent HUM_TRANSFORMER = make("hum_transformer");
    public static final SoundEvent HUM_RECTIFIER = make("hum_rectifier");
    public static final SoundEvent BREAKER = make("breaker_clunk");
    public static final SoundEvent RELAY = make("relay_click");
    public static final SoundEvent ALARM = make("alarm");

    private ModSounds() {}

    private static SoundEvent make(String id) {
        ResourceLocation rl = new ResourceLocation(RailMap.MODID, id);
        return new SoundEvent(rl).setRegistryName(rl);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<SoundEvent> e) {
        e.getRegistry().registerAll(DOOR_CHIME, DOOR_OPEN, DOOR_WARN, DOOR_CLOSE, PSD_MOVE, HUM_TRANSFORMER,
                HUM_RECTIFIER, BREAKER, RELAY, ALARM);
    }
}
