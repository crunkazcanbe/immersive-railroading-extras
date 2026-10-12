package com.dogpound.railmap.client;

import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.grid.BlockGrid;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.grid.TileGrid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Live plant sounds (requested feature): every energised grid machine near you runs looping sounds whose loudness and
 * pitch follow what it is really doing - transformer hum swells with load, fans run when the panel says they do, the
 * rectifier / inverter whine rises with load, engines follow their rpm, wind turbine blades swoosh. Client only: the
 * server already syncs the panel values (load, fanrun, fans, rpm, ...) and the block's live state.
 */
public final class GridSounds {
    private GridSounds() {}

    private static final double RANGE = 24;
    private static final Map<String, Loop> PLAYING = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) { stopAll(); return; }
        if (mc.world.getTotalWorldTime() % 5 != 0) return;
        Map<String, Want> want = new HashMap<>();
        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (!(te instanceof TileGrid)) continue;
            BlockPos p = te.getPos();
            if (mc.player.getDistanceSq(p) > RANGE * RANGE) continue;
            TileGrid g = (TileGrid) te;
            float lvl = g.soundLevel();
            if (lvl <= 0) continue;
            IBlockState st = mc.world.getBlockState(p);
            int state = st.getBlock() instanceof BlockGrid && st.getPropertyKeys().contains(BlockGrid.STATE) ? st.getValue(BlockGrid.STATE) : 0;
            layers(g, state, lvl, p, want);
        }
        for (Map.Entry<String, Want> w : want.entrySet()) {
            Loop l = PLAYING.get(w.getKey());
            if (l == null || l.isDonePlaying()) {
                l = new Loop(w.getValue().sound, w.getValue().pos, w.getValue().pitch);
                PLAYING.put(w.getKey(), l);
                mc.getSoundHandler().playSound(l);
            }
            l.target(w.getValue().volume, w.getValue().pitch);
        }
        for (Iterator<Map.Entry<String, Loop>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Loop> en = it.next();
            if (!want.containsKey(en.getKey())) { en.getValue().fadeOut(); it.remove(); }
        }
    }

    /** the sounds a machine makes right now */
    private static void layers(TileGrid g, int state, float lvl, BlockPos p, Map<String, Want> want) {
        GridKind k = g.kind();
        float load = Math.max(0, Math.min(1.5f, g.pv("load") / 100f));
        switch (k) {
            case TRANSFORMER: case TRACTION:
                if (state != 1) return;
                // light / normal / heavy load hum (more harmonics + winding buzz as the load rises), crossfaded
                if (load < 0.3f) add(want, p, "hum_l", com.dogpound.railmap.sound.PackSounds.ev("grid.hum_light"), lvl * (0.3f + 0.5f * load), 1f);
                else if (load > 0.85f) add(want, p, "hum_h", com.dogpound.railmap.sound.PackSounds.ev("grid.hum_heavy"), lvl * (0.3f + 0.5f * load), 0.98f + 0.04f * load);
                else add(want, p, "hum", ModSounds.HUM_TRANSFORMER, lvl * (0.3f + 0.5f * load), 0.98f + 0.04f * load);
                if (g.pv("fanrun") == 1) add(want, p, "fan", ModSounds.FAN_LOOP, lvl * 0.45f, 1f);
                break;
            case RECTIFIER:
                if (state != 1 || g.pv("dcon") != 1) return;
                add(want, p, "whine", ModSounds.WHINE_RECT, lvl * (0.25f + 0.45f * load), 0.92f + 0.2f * load);
                if (g.pv("fans") == 1) add(want, p, "fan", ModSounds.FAN_LOOP, lvl * 0.4f, 1.1f);
                break;
            case INVERTER: case DCDC:
                if (state != 1 || g.pv("lrun") != 1) return;
                add(want, p, "whine", ModSounds.WHINE_INV, lvl * (0.2f + 0.45f * load), 0.85f + 0.3f * load);
                if (g.pv("fans") == 1) add(want, p, "fan", ModSounds.FAN_LOOP, lvl * 0.35f, 1.2f);
                break;
            case DIESEL: case TURBINE: {
                float rpm = Math.min(1.1f, g.pv("rpm") / 100f);
                if (rpm <= 0.02f) return;
                add(want, p, "engine", k == GridKind.DIESEL ? ModSounds.ENGINE_DIESEL : ModSounds.ENGINE_TURBINE,
                        lvl * (0.45f + 0.45f * rpm), 0.5f + 0.5f * rpm);
                break;
            }
            case WIND:
                if (state == 1) add(want, p, "blades", ModSounds.WIND_LOOP, lvl * 0.6f, 1f);
                break;
            case MOTOR:                                              // 1 = starting / running: the loop fades in like a run-up
                if (state == 1) add(want, p, "motor", com.dogpound.railmap.sound.PackSounds.ev("grid.motor_run"), lvl * 0.5f, 0.9f + 0.1f * Math.min(1, load));
                break;
            case CAPACITOR:
                if (state == 1) add(want, p, "hum", ModSounds.CAP_HUM, lvl * 0.25f, 1f);
                break;
            default: break;
        }
    }

    private static void add(Map<String, Want> want, BlockPos p, String layer, SoundEvent s, float volume, float pitch) {
        want.put(p.toLong() + ":" + layer, new Want(s, p, Math.min(1.5f, volume), pitch));
    }

    private static void stopAll() {
        for (Loop l : PLAYING.values()) l.kill();
        PLAYING.clear();
    }

    private static final class Want {
        final SoundEvent sound; final BlockPos pos; final float volume, pitch;
        Want(SoundEvent sound, BlockPos pos, float volume, float pitch) { this.sound = sound; this.pos = pos; this.volume = volume; this.pitch = pitch; }
    }

    /** one looping layer at a machine; eases to its target loudness / pitch (engines spool up and down) */
    private static final class Loop extends MovingSound {
        private float tv, tp = 1;
        private boolean fading;

        Loop(SoundEvent s, BlockPos p, float pitch0) {
            super(s, SoundCategory.BLOCKS);
            repeat = true;
            repeatDelay = 0;
            volume = 0.01f;
            pitch = pitch0;
            xPosF = p.getX() + 0.5f; yPosF = p.getY() + 0.5f; zPosF = p.getZ() + 0.5f;
        }

        void target(float v, float p) { tv = v; tp = p; fading = false; }
        void fadeOut() { fading = true; tv = 0; }
        void kill() { donePlaying = true; }

        @Override
        public void update() {
            volume += (tv - volume) * 0.08f;
            pitch += (tp - pitch) * 0.04f;
            if (fading && volume < 0.01f) donePlaying = true;
        }
    }
}
