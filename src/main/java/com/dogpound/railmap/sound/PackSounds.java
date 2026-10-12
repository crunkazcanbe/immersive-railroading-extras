package com.dogpound.railmap.sound;

import com.dogpound.railmap.RailMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The realistic sound pack (tools/gen_soundpack.py): every event id listed in assets/irextras/soundpack.txt is
 * registered here ("cab.horn", "signal.lever_reverse", "machine.crusher", ...), each with 3-6 synthesised variations
 * in sounds.json. Dotted ids are the pack's; group = the part before the first dot (SoundConfig volumes / switches).
 * <p>Use {@link #play} on the server (heard by everyone near) or {@link #get} for a SoundEvent. Unknown ids are silent,
 * never a crash.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class PackSounds {
    private PackSounds() {}

    private static final Map<String, SoundEvent> ALL = new LinkedHashMap<>();

    static {
        try (InputStream in = PackSounds.class.getResourceAsStream("/assets/irextras/soundpack.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String l; (l = r.readLine()) != null; ) {
                    l = l.trim();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    ResourceLocation rl = new ResourceLocation(RailMap.MODID, l);
                    ALL.put(l, new SoundEvent(rl).setRegistryName(rl));
                }
            }
        } catch (Exception e) {
            RailMap.LOG.warn("IR Extras sound pack list unreadable: {}", e.toString());
        }
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<SoundEvent> e) {
        e.getRegistry().registerAll(ALL.values().toArray(new SoundEvent[0]));
    }

    /** the registered event, or null when the pack doesn't have it */
    public static SoundEvent get(String id) { return ALL.get(id); }

    public static boolean has(String id) { return ALL.containsKey(id); }

    /** the event for a world.playSound call; a missing id falls back to a quiet vanilla click instead of a crash */
    public static SoundEvent ev(String id) {
        SoundEvent s = ALL.get(id);
        return s != null ? s : net.minecraft.init.SoundEvents.UI_BUTTON_CLICK;
    }

    /** server: play a pack sound at a block for everyone in earshot (group switch + volumes apply on each client) */
    public static void play(World w, BlockPos pos, String id, float volume, float pitch) {
        SoundEvent s = ALL.get(id);
        if (s == null || w == null || w.isRemote) return;
        w.playSound(null, pos, s, SoundCategory.BLOCKS, volume, pitch);
    }

    /** server: play at a point (trains, entities) */
    public static void play(World w, double x, double y, double z, String id, float volume, float pitch) {
        SoundEvent s = ALL.get(id);
        if (s == null || w == null || w.isRemote) return;
        w.playSound(null, x, y, z, s, SoundCategory.NEUTRAL, volume, pitch);
    }

    /** client: a sound only this player hears (GUI keys, cab switches) */
    public static void local(EntityPlayer p, String id, float volume, float pitch) {
        SoundEvent s = ALL.get(id);
        if (s != null && p != null) p.playSound(s, volume, pitch);
    }

    /** small random pitch spread so repeated clicks never sound machine-gunned */
    public static float vary(World w) { return 0.95f + (w == null ? 0.05f : w.rand.nextFloat() * 0.1f); }

    public static int count() { return ALL.size(); }
}
