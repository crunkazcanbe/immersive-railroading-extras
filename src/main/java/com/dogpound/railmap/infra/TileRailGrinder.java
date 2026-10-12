package com.dogpound.railmap.infra;

import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.asset.AssetData;
import com.dogpound.railmap.track.Rails;
import com.dogpound.railmap.track.TrackData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.Locale;

/**
 * Rail Grinding Machine (#42): a self-propelled grinding unit (TileTrackMachine). Lower the GRINDING HEADS and DRIVE:
 * at 1-3 working speeds it grinds every piece of track it runs over - the rotating stones take the corrugation and
 * rolling-contact fatigue off the rail (the track model's rail-surface figure falls, a little head metal goes), shallow
 * head checks and squats (rail flaw classes, #41) are ground out, and the onboard eddy-current probe verifies it. The
 * stones wear (STONES: hold flint to fit a new set), standing still with the heads down burns the rail, the WATER spray
 * damps the sparks (they can light the lineside with the harsh switch grinderFires). Its laser profile gauge shows the
 * rail surface before and after each piece.
 */
public class TileRailGrinder extends TileTrackMachine {
    private double stones = 1, water = 1000;
    private int ground;
    private String profile = "";
    private long lastPiece;

    @Override public String wiredName() { return "Rail grinder"; }

    private boolean headsDown() { return ctl.get("m_heads") == 1; }

    @Override
    public String gearState() {
        return String.format(Locale.ROOT, "%s · fuel %.0f L · stones %.0f%% · %d pieces ground%s", state, fuel, stones * 100, ground, coverText().isEmpty() ? "" : " · " + coverText());
    }

    @Override public int wiredLamp() { return !powered() ? 3 : stones < 0.1 || fuel < 20 ? 2 : 1; }

    @Override
    protected void serverTick() {
        long now = world.getTotalWorldTime();
        boolean work = engine && headsDown() && stones > 0 && !estop();
        if (age % 20 == 5) {
            battery();
            burn(work ? (travel ? 1 : 0.6) : travel ? 0.3 : 0);
            if (work && !travel) burnRail();
            if (work && ctl.get("s_water") == 1) water = Math.max(0, water - 2);
            if (engine && age % 60 == 5) world.playSound(null, pos, work ? com.dogpound.railmap.ModSounds.LOOP_CRUSHER : com.dogpound.railmap.ModSounds.ENGINE_DIESEL, SoundCategory.BLOCKS, work ? 0.7f : 0.4f, work ? 1.3f : 1f);
        }
        if (estop() && (engine || travel)) { engine = false; travel = false; state = "E-STOP"; log("EMERGENCY STOP"); }
        if (travel && engine && !estop() && now >= nextStep) {
            if (!possessionOk()) { travel = false; state = "NO POSSESSION"; }
            else {
                String[] why = {""};
                BlockPos c = next(why);
                if (c == null) { travel = false; state = why[0]; world.playSound(null, pos, com.dogpound.railmap.ModSounds.BUZZER, SoundCategory.BLOCKS, 0.5f, 1f); }
                else {
                    nextStep = now + new int[]{40, 20, 10}[Math.max(0, Math.min(2, ctl.get("s_spd")))];
                    if (work) grind(c.down());
                    state = work ? "GRINDING" : "TRAVELLING";
                    panel();
                    moveTo(c);
                    return;
                }
            }
        }
        if (work && world instanceof WorldServer ws && age % 4 == 0)
            ws.spawnParticle(ctl.get("s_water") == 1 && water > 0 ? EnumParticleTypes.WATER_SPLASH : EnumParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() - 0.7, pos.getZ() + 0.5, 6, 0.4, 0.05, 0.4, 0.05);
        if (age % 5 == 0) panel();
    }

    private void panel() {
        commonPanel();
        boolean work = engine && headsDown() && stones > 0 && !estop();
        put("heads", headsDown()); put("spark", work); put("wet", ctl.get("s_water") == 1 && water > 0); put("end", state.equals("END OF TRACK") || state.equals("TRAIN AHEAD") || state.equals("OBSTRUCTED"));
        put("move", travel); put("worn", stones < 0.15);
        put("g_stone", (int) Math.round(stones * 100)); put("g_water", (int) Math.round(water / 10));
        text("spd", travel ? new String[]{"1.8", "3.6", "7.2"}[Math.max(0, Math.min(2, ctl.get("s_spd")))] + " KM/H" : "0.0 KM/H");
        text("stone", String.format(Locale.ROOT, "STONES %.0f%%", stones * 100));
        text("done", "GROUND " + ground + " PCS");
        text("prof", profile.isEmpty() ? "PROFILE --" : profile);
    }

    /** one piece of track under the passing heads */
    private void grind(BlockPos rail) {
        TileRail piece = Rails.pieceAt(world, rail);
        if (piece == null) return;
        BlockPos key = Rails.pos(piece);
        if (key.toLong() == lastPiece) { stones = Math.max(0, stones - 0.0015); return; }   // same piece, next metre
        lastPiece = key.toLong();
        TrackData td = TrackData.get(world);
        TrackData.Seg s = td.seg(key);
        double before = s.get(TrackData.Metric.SURFACE);
        double cut = 7 * Math.min(1, stones * 1.5) * new double[]{1.3, 1, 0.7}[Math.max(0, Math.min(2, ctl.get("s_spd")))];   // slower = more passes of the stones
        s.set(TrackData.Metric.SURFACE, Math.max(0, before - cut));
        s.add(TrackData.Metric.RAIL, 0.012);
        s.lastWork = "RAIL GROUND";
        s.workedAt = world.getTotalWorldTime();
        td.markDirty();
        profile = String.format(Locale.ROOT, "SURF %.0f%%>%.0f%%", before, s.get(TrackData.Metric.SURFACE));
        stones = Math.max(0, stones - 0.004);
        ground++;
        int fixed = 0;
        for (AssetData.Defect df : RailFlaws.openAt(world, rail)) {
            RailFlaws.Flaw f = RailFlaws.of(df.type);
            if (f != RailFlaws.Flaw.HEAD_CHECKS && f != RailFlaws.Flaw.SQUAT) continue;
            if (df.size > 2.5) continue;                                                  // too deep to grind out
            df.size = Math.max(0, df.size - 0.8);
            if (df.size <= 0.2) { RailFlaws.repaired(world, df, "ground out by the rail grinder", true); fixed++; }
        }
        if (fixed > 0) log(fixed + " shallow rail defect(s) ground out at " + Rails.xz(key));
        if (InfraConfig.grinderFires && (ctl.get("s_water") != 1 || water <= 0) && world.rand.nextDouble() < 0.06) sparkFire();
        if (ground % 25 == 0) com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "rail_ground", "pieces", ground);
    }

    /** heads down, standing still: the stones burn a flat into the rail */
    private void burnRail() {
        TileRail piece = Rails.pieceAt(world, pos.down());
        if (piece == null) return;
        TrackData.Seg s = TrackData.get(world).seg(Rails.pos(piece));
        s.add(TrackData.Metric.SURFACE, 3);
        state = "STANDING - BURNING THE RAIL";
        TrackData.get(world).markDirty();
    }

    private void sparkFire() {
        for (int i = 0; i < 6; i++) {
            BlockPos b = pos.add(world.rand.nextInt(7) - 3, -1, world.rand.nextInt(7) - 3);
            if ((world.getBlockState(b).getBlock() == Blocks.GRASS || world.getBlockState(b).getBlock() == Blocks.TALLGRASS.getDefaultState().getBlock()) && world.isAirBlock(b.up())) {
                world.setBlockState(b.up(), Blocks.FIRE.getDefaultState());
                log("sparks set the lineside alight at " + b.getX() + "," + b.getZ());
                return;
            }
        }
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        switch (id) {
            case "test": return lampTest();
            case "start": return startStop(p);
            case "go": {
                if (travel) { travel = false; state = "STOPPED"; return "§eStopped"; }
                if (!engine) return "§cStart the engine first";
                if (estop()) return "§cE-STOP is latched";
                if (!possessionOk()) return "§cNO POSSESSION - the line is open (take a possession or a line block first)";
                travel = true; nextStep = world.getTotalWorldTime() + 10;
                log("working run started by " + who(p) + (headsDown() ? " - heads DOWN" : " - travelling"));
                return headsDown() ? "§aGrinding run started" : "§aTravelling (heads up)";
            }
            case "m_heads": {
                ctl.set("m_heads", 1 - ctl.get("m_heads"));
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.LEVER_HEAVY, SoundCategory.BLOCKS, 0.6f, 1f);
                return ctl.get("m_heads") == 1 ? "§6Grinding heads LOWERED" : "§7Grinding heads raised";
            }
            case "m_estop": {
                ctl.set("m_estop", 1 - ctl.get("m_estop"));
                if (ctl.get("m_estop") == 1) { engine = false; travel = false; state = "E-STOP"; }
                return ctl.get("m_estop") == 1 ? "§cEMERGENCY STOP" : "§7E-stop released";
            }
            case "z_fuel": return refuel(p);
            case "z_stones": {
                if (p == null) return "§7Stones are changed at the machine";
                ItemStack h = p.getHeldItemMainhand();
                if (h.getItem() != Items.FLINT || h.getCount() < 4) return String.format(Locale.ROOT, "§7Stones %.0f%% - hold 4 flint to fit a new set", stones * 100);
                if (engine) return "§cStop the engine before changing stones";
                if (!p.capabilities.isCreativeMode) h.shrink(4);
                stones = 1;
                log("new set of grinding stones fitted by " + p.getName());
                return "§aNew grinding stones fitted";
            }
            case "z_water": {
                if (p == null) return "§7Fill it at the tank";
                ItemStack h = p.getHeldItemMainhand();
                if (h.getItem() != Items.WATER_BUCKET) return String.format(Locale.ROOT, "§7Spray tank %.0f L - hold a water bucket to fill", water);
                if (!p.capabilities.isCreativeMode) p.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                water = Math.min(1000, water + 250);
                return String.format(Locale.ROOT, "§bSpray tank %.0f L", water);
            }
            case "s_dir": if (travel) return "§cStop before reversing"; return null;
            default: return null;
        }
    }

    @Override
    protected void store(NBTTagCompound t) {
        storeMachine(t);
        t.setDouble("stones", stones); t.setDouble("water", water); t.setInteger("ground", ground); t.setString("prof", profile); t.setLong("lastPiece", lastPiece);
    }

    @Override
    protected void restore(NBTTagCompound t) {
        restoreMachine(t);
        stones = t.hasKey("stones") ? t.getDouble("stones") : 1; water = t.hasKey("water") ? t.getDouble("water") : 1000;
        ground = t.getInteger("ground"); profile = t.getString("prof"); lastPiece = t.getLong("lastPiece");
    }
}
