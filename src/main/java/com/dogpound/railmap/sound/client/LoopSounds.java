package com.dogpound.railmap.sound.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.TilePaSpeaker;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.depot.TileFuelPoint;
import com.dogpound.railmap.depot.TileTrainWash;
import com.dogpound.railmap.depot.TileWheelLathe;
import com.dogpound.railmap.factory.machine.MachineType;
import com.dogpound.railmap.factory.machine.TileMachine;
import com.dogpound.railmap.sound.PackSounds;
import com.dogpound.railmap.sound.SoundConfig;
import com.dogpound.railmap.track.TileTunnelFan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.*;

/**
 * Looping machine sounds, client side (the grid's own plant has client/GridSounds). Twice a second it looks at the
 * loaded machines near the player, asks each what it is doing from its synced panel values (the same lamps the model
 * shows), and keeps one looping {@link MovingSound} per running machine - the nearest {@link SoundConfig#maxLoops}
 * within {@link SoundConfig#loopRange} blocks. Loops fade in / out, never restart while the machine keeps running, and
 * different machines pick different recorded variations so a hall of crushers doesn't phase. Machines that make
 * periodic noises while they run (rolling mill bangs, presses, robot arms) get those too.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class LoopSounds {
    private LoopSounds() {}

    private static final Map<String, Loop> PLAYING = new HashMap<>();
    private static final Random RNG = new Random();
    private static long lastWhistle;

    /** what a tile wants to sound like right now */
    private static final class Want {
        final String key, id; final BlockPos pos; final float volume, pitch; final double d2;
        Want(String key, String id, BlockPos pos, float volume, float pitch, double d2) {
            this.key = key; this.id = id; this.pos = pos; this.volume = volume; this.pitch = pitch; this.d2 = d2;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) { stopAll(); return; }
        if (mc.isGamePaused() || mc.world.getTotalWorldTime() % 10 != 0) return;
        double r = SoundConfig.loopRange, r2 = r * r;
        List<Want> want = new ArrayList<>();
        for (TileEntity te : mc.world.loadedTileEntityList) {
            BlockPos p = te.getPos();
            double d2 = mc.player.getDistanceSqToCenter(p);
            if (d2 > r2 || te.isInvalid()) continue;
            try { wants(mc.world, te, p, d2, want); } catch (RuntimeException ignored) { /* a half-loaded tile: next pass */ }
        }
        want.sort(Comparator.comparingDouble(w -> w.d2));
        Set<String> keep = new HashSet<>();
        for (int i = 0; i < want.size() && keep.size() < SoundConfig.maxLoops; i++) {
            Want w = want.get(i);
            SoundEvent s = PackSounds.get(w.id);
            String key = w.key + "|" + w.id;
            if (s == null || keep.contains(key)) continue;                   // ambience: only the nearest speaker plays it
            keep.add(key);
            Loop l = PLAYING.get(key);
            if (l == null || l.isDonePlaying()) {
                l = new Loop(s, w.pos, w.pitch);
                PLAYING.put(key, l);
                mc.getSoundHandler().playSound(l);
            }
            l.target(w.volume * SoundMixer.gain(w.id), w.pitch);
            l.at(w.pos);
        }
        for (Iterator<Map.Entry<String, Loop>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Loop> en = it.next();
            if (!keep.contains(en.getKey())) en.getValue().fadeOut();
            if (en.getValue().isDonePlaying()) it.remove();
        }
    }

    private static void wants(World w, TileEntity te, BlockPos p, double d2, List<Want> out) {
        String k = String.valueOf(p.toLong());
        if (te instanceof TileMachine m) {
            if (!m.formed() || m.pv("anim") != 1 || m.type() == null) return;
            String id = machineLoop(m.type());
            if (id != null) out.add(new Want(k, id, p, 0.9f, 1f, d2));
            oneShots(w, m, p);
            // works whistle at the 06:00 / 18:00 shift change (heavy industry)
            MachineType ty = m.type();
            if ((ty == MachineType.BLAST_FURNACE || ty == MachineType.ROLLING_MILL || ty == MachineType.COKE_OVEN) && w.getWorldTime() % 12000 < 10
                    && System.currentTimeMillis() - lastWhistle > 20000 && (lastWhistle = System.currentTimeMillis()) > 0)
                Minecraft.getMinecraft().getSoundHandler().playSound(new PositionedSoundRecord(PackSounds.ev("cab.whistle"), SoundCategory.BLOCKS,
                        4f, 0.8f, p.getX() + 0.5f, p.getY() + 4f, p.getZ() + 0.5f));
        } else if (te instanceof TileWheelLathe t) {
            if (on(t, "cutting")) out.add(new Want(k, "machine.lathe", p, 0.8f, 1f, d2));
        } else if (te instanceof TileTrainWash t) {
            if (on(t, "busy")) out.add(new Want(k, "machine.wash", p, 0.9f, 1f, d2));
            else if (on(t, "runl")) out.add(new Want(k, "machine.pump", p, 0.35f, 1.1f, d2));
        } else if (te instanceof com.dogpound.railmap.depot.TileBrakeTest t) {
            if (on(t, "run")) out.add(new Want(k, "cab.compressor", p, 0.7f, 1f, d2));   // the test rig's air compressor
        } else if (te instanceof TileFuelPoint t) {
            if (on(t, "pumpl")) out.add(new Want(k, "machine.fuel_pump", p, 0.7f, 1f, d2));
        } else if (te instanceof TileTunnelFan t) {
            if (t.running()) out.add(new Want(k, "track.jet_fan", p, 1.0f, 0.92f + Math.floorMod(p.hashCode(), 16) / 100f, d2));
        } else if (te instanceof TilePaSpeaker && SoundConfig.stationAmbience) {
            BlockPos up = p.up(2);
            if (!w.canSeeSky(up) && !w.canSeeSky(p.up(6))) {
                // under a canopy: rain drums on it, wind gusts along the platform
                if (w.isRaining() && w.getBiome(p).canRain())
                    out.add(new Want("amb", "station.rain", p.up(3), w.isThundering() ? 1.0f : 0.6f, 1f, d2));
            }
            if (w.isThundering() || p.getY() > 90 || w.isRaining())
                out.add(new Want("amb", "station.wind", p, w.isThundering() ? 0.8f : 0.4f, 1f, d2 + 1));   // one per station: the nearest speaker
        }
    }

    private static boolean on(TileEntity t, String id) { return t instanceof IControlled c && c.controlValue(id) != 0; }

    /** the steady running sound of each factory machine */
    static String machineLoop(MachineType t) {
        switch (t) {
            case JAW_CRUSHER: return "machine.crusher";
            case CAR_DUMPER: case WIRE_DRAWER: case ROLLING_MILL: return "grid.motor_run";
            case WASHING_PLANT: return "machine.wash";
            case FLOTATION: case ELECTROLYSER: return "machine.bubbles";
            case SMELTER: return "grid.rectifier";
            case ASSEMBLY_LINE: case BULK_LOADER: case UNLOADER: case OVERHEAD_LOADER: case CRATE_PACKER: return "machine.conveyor";
            case BOREHOLE_PUMP: case TANK_LOADER: case TANK_UNLOADER: case REFINERY: case TIE_PLANT: case OIL_PRESS: return "machine.pump";
            case COKE_OVEN: case BLAST_FURNACE: return "machine.furnace";
            case SAWMILL: return "machine.saw";
            case WINDMILL: return "machine.windmill";
            case ARC_FURNACE: return "machine.arc_furnace";
            case WELDING_CELL: return "machine.welding";
            default: return null;
        }
    }

    /** the irregular noises a running machine makes, heard by this client */
    private static void oneShots(World w, TileMachine m, BlockPos p) {
        String id = null;
        float chance = 0;
        switch (m.type()) {
            case ROLLING_MILL: id = "machine.mill_bang"; chance = 0.18f; break;
            case OIL_PRESS: id = "machine.press"; chance = 0.12f; break;
            case WELDING_CELL: case ASSEMBLY_LINE: id = RNG.nextInt(3) == 0 ? "ui.robot" : "machine.servo"; chance = 0.2f; break;
            case CRATE_PACKER: id = "machine.clunk"; chance = 0.15f; break;
            default: break;
        }
        if (id == null || RNG.nextFloat() >= chance) return;
        SoundEvent s = PackSounds.get(id);
        if (s == null) return;
        Minecraft.getMinecraft().getSoundHandler().playSound(new PositionedSoundRecord(s, SoundCategory.BLOCKS,
                0.8f, 0.94f + RNG.nextFloat() * 0.12f, p.getX() + 0.5f, p.getY() + 0.5f, p.getZ() + 0.5f));
    }

    private static void stopAll() {
        for (Loop l : PLAYING.values()) l.kill();
        PLAYING.clear();
    }

    /** one looping sound at a machine: eases to its target loudness / pitch, fades out when told */
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
        void at(BlockPos p) { xPosF = p.getX() + 0.5f; yPosF = p.getY() + 0.5f; zPosF = p.getZ() + 0.5f; }
        void kill() { donePlaying = true; }

        @Override
        public void update() {
            volume += (tv - volume) * 0.06f;
            pitch += (tp - pitch) * 0.04f;
            if (fading && volume < 0.01f) donePlaying = true;
        }
    }
}
