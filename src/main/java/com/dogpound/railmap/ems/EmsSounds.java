package com.dogpound.railmap.ems;

import com.dogpound.railmap.RailMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** emergency-vehicle sirens + the station shops' machine sounds (tools/gen_sounds_ems.py synthesises the .oggs) */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class EmsSounds {
    private EmsSounds() {}

    public static final SoundEvent SIREN_FIRE = make("ems_siren_fire");          // wail
    public static final SoundEvent SIREN_AMBULANCE = make("ems_siren_ambulance");// hi-lo two-tone
    public static final SoundEvent SIREN_POLICE = make("ems_siren_police");      // yelp
    public static final SoundEvent AIR_HORN = make("ems_air_horn");
    public static final SoundEvent TURNOUT = make("ems_turnout");                // station turnout tones
    public static final SoundEvent COFFEE_GRIND = make("coffee_grind");
    public static final SoundEvent COFFEE_STEAM = make("coffee_steam");
    public static final SoundEvent COFFEE_POUR = make("coffee_pour");
    public static final SoundEvent NEWS_PRESS = make("news_press");

    private static SoundEvent make(String id) {
        ResourceLocation rl = new ResourceLocation(RailMap.MODID, id);
        return new SoundEvent(rl).setRegistryName(rl);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<SoundEvent> e) {
        e.getRegistry().registerAll(SIREN_FIRE, SIREN_AMBULANCE, SIREN_POLICE, AIR_HORN, TURNOUT, COFFEE_GRIND, COFFEE_STEAM, COFFEE_POUR, NEWS_PRESS);
    }
}
