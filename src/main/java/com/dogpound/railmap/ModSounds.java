package com.dogpound.railmap;

import com.dogpound.railmap.grid.Panel;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** IR Extras' own sounds (synthesised by tools/gen_sounds.py + gen_sounds_plant.py, listed in sounds.json). */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class ModSounds {
    private static final List<SoundEvent> ALL = new ArrayList<>();

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
    // ---- plant loops (client GridSounds follows each machine's live state)
    public static final SoundEvent FAN_LOOP = make("fan_loop");
    public static final SoundEvent WHINE_RECT = make("whine_rect");
    public static final SoundEvent WHINE_INV = make("whine_inv");
    public static final SoundEvent ENGINE_DIESEL = make("engine_diesel");
    public static final SoundEvent ENGINE_TURBINE = make("engine_turbine");
    public static final SoundEvent WIND_LOOP = make("wind_loop");
    public static final SoundEvent CAP_HUM = make("cap_hum");
    // ---- plant + control-room one-shots
    public static final SoundEvent TAP_CHANGE = make("tap_change");
    public static final SoundEvent BREAKER_CLOSE = make("breaker_close");
    public static final SoundEvent BREAKER_TRIP = make("breaker_trip");
    public static final SoundEvent ARC_FLASH = make("arc_flash");
    public static final SoundEvent CABLE_SIZZLE = make("cable_sizzle");
    public static final SoundEvent BUTTON = make("button_press");
    public static final SoundEvent ESTOP = make("estop_hit");
    public static final SoundEvent SELECTOR = make("selector_turn");
    public static final SoundEvent KEY = make("key_switch");
    public static final SoundEvent LEVER_HEAVY = make("lever_heavy");
    public static final SoundEvent LEVER_CLACK = make("lever_clack");
    public static final SoundEvent ALARM_HORN = make("alarm_horn");
    public static final SoundEvent ACK_BEEP = make("ack_beep");
    public static final SoundEvent PHONE_RING = make("phone_ring");
    public static final SoundEvent CROSSING_BELL = make("crossing_bell");
    public static final SoundEvent POINT_MOTOR = make("point_motor");
    public static final SoundEvent CONTACTOR = make("contactor");
    public static final SoundEvent DIESEL_START = make("diesel_start");
    public static final SoundEvent DIESEL_STOP = make("diesel_stop");
    // ---- gen_sounds.py "Pride sound pack" ids already used across the mod (sound-pack agent 10-10: were undefined)
    public static final SoundEvent CTL_PUSH = make("ctl_push");
    public static final SoundEvent CTL_KEY = make("ctl_key");
    public static final SoundEvent CTL_ESTOP = make("ctl_estop");
    public static final SoundEvent CTL_TOGGLE = make("ctl_toggle");
    public static final SoundEvent FUSE_BLOW = make("fuse_blow");
    public static final SoundEvent ARC_BUZZ = make("arc_buzz");
    public static final SoundEvent LOOP_CONVEYOR = make("loop_conveyor");
    public static final SoundEvent LOOP_CRUSHER = make("loop_crusher");
    public static final SoundEvent BUZZER = make("buzzer");
    public static final SoundEvent KLAXON = make("klaxon");
    public static final SoundEvent FIRE_BELL = make("fire_bell");
    public static final SoundEvent BLOCK_BELL = make("block_bell");
    public static final SoundEvent PA_CHIME = make("pa_chime");
    public static final SoundEvent PRINTER = make("printer");
    public static final SoundEvent BEEP_OK = make("beep_ok");
    public static final SoundEvent BEEP_ERROR = make("beep_error");
    public static final SoundEvent GATE = make("gate");
    public static final SoundEvent CASH = make("cash");

    private ModSounds() {}

    private static SoundEvent make(String id) {
        ResourceLocation rl = new ResourceLocation(RailMap.MODID, id);
        SoundEvent s = new SoundEvent(rl).setRegistryName(rl);
        ALL.add(s);
        return s;
    }

    /** what operating a control of this type sounds like (null: silent, e.g. an invisible drawer zone) */
    public static SoundEvent forControl(Panel.T t) {
        switch (t) {
            case PUSH: return BUTTON;
            case MUSHROOM: return ESTOP;
            case SELECTOR: return SELECTOR;
            case KEY: return KEY;
            case LEVER: return LEVER_HEAVY;
            case PULL: return LEVER_CLACK;
            case ZONE: return null;
            default: return BUTTON;
        }
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<SoundEvent> e) {
        e.getRegistry().registerAll(ALL.toArray(new SoundEvent[0]));
    }
}
