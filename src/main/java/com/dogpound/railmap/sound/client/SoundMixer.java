package com.dogpound.railmap.sound.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.sound.SoundConfig;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.Sound;
import net.minecraft.client.audio.SoundEventAccessor;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Applies config/irextras_sound.cfg to every IR Extras sound as the client is about to play it: a switched-off group is
 * silenced, the others are scaled by master x group volume. Looping sounds (ITickableSound) are only switched on / off
 * here - LoopSounds scales their volume itself, so the sound engine keeps tracking the same object.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class SoundMixer {
    private SoundMixer() {}

    /** master x group volume for an irextras sound path, 0 = off */
    public static float gain(String path) {
        SoundConfig.Group g = SoundConfig.groupOf(path);
        if (!g.enabled) return 0f;
        return SoundConfig.master / 100f * g.volume / 100f;
    }

    @SubscribeEvent
    public static void onPlay(PlaySoundEvent e) {
        ISound s = e.getResultSound();
        if (s == null || !RailMap.MODID.equals(s.getSoundLocation().getNamespace())) return;
        float g = gain(s.getSoundLocation().getPath());
        if (g <= 0f) { e.setResultSound(null); return; }
        if (Math.abs(g - 1f) < 0.01f || s instanceof ITickableSound) return;
        e.setResultSound(new Scaled(s, g));
    }

    /** the same sound, louder or quieter */
    private static final class Scaled implements ISound {
        private final ISound d;
        private final float g;
        Scaled(ISound d, float g) { this.d = d; this.g = g; }
        @Override public ResourceLocation getSoundLocation() { return d.getSoundLocation(); }
        @Override public SoundEventAccessor createAccessor(SoundHandler h) { return d.createAccessor(h); }
        @Override public Sound getSound() { return d.getSound(); }
        @Override public SoundCategory getCategory() { return d.getCategory(); }
        @Override public boolean canRepeat() { return d.canRepeat(); }
        @Override public int getRepeatDelay() { return d.getRepeatDelay(); }
        @Override public float getVolume() { return d.getVolume() * g; }
        @Override public float getPitch() { return d.getPitch(); }
        @Override public float getXPosF() { return d.getXPosF(); }
        @Override public float getYPosF() { return d.getYPosF(); }
        @Override public float getZPosF() { return d.getZPosF(); }
        @Override public AttenuationType getAttenuationType() { return d.getAttenuationType(); }
    }
}
