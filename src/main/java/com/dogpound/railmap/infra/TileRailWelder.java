package com.dogpound.railmap.infra;

import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.asset.AssetData;
import com.dogpound.railmap.asset.AssetKind;
import com.dogpound.railmap.track.Rails;
import com.dogpound.railmap.weather.Weather;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.List;
import java.util.Locale;

/**
 * Mobile Flash-Butt Rail Welder (#42): a road-rail welding machine (TileTrackMachine) that cuts a defective length out
 * of the rail, welds in a closure rail ("plug": 2 iron ingots into the hopper) and finishes it. SEEK drives it along the
 * track to the next rail defect logged for welding (squats, weld defects, bolt-hole cracks, small transverse defects -
 * the rail flaw classes, #41). Then the sequence, each step its own button and in order:
 * CUT (rail saw) - ALIGN (clamps, gap) - PREHEAT (flashing until the rail ends reach ~1100 C) - WELD (flash + upset)
 * - SHEAR (trim the upset collar) - GRIND (profile the head) - UT (ultrasonic check). Weld below ~1000 C and the UT
 * finds lack of fusion (a new UIC 421 defect to recut); weld far off the rail's stress-free temperature and the log
 * warns that the rail must be destressed. A clean UT closes the defect (REPAIRED + VERIFIED).
 */
public class TileRailWelder extends TileTrackMachine {
    static final String[] STEPS = {"IDLE", "CUT", "ALIGN", "PREHEAT", "WELD", "SHEAR", "GRIND", "UT"};
    static final int[] SECS = {0, 15, 8, 12, 6, 5, 10, 6};
    static final String[] IDS = {"", "cut", "align", "heat", "weld", "shear", "grind", "ut"};
    /** steps done (0..7), the one running (0 none) and when it ends */
    private int done, running, target, plugs, welds;
    private long until;
    private double temp = 20, upset;
    private boolean seek, fusionOk = true;
    private String result = "";

    @Override public String wiredName() { return "Rail welder"; }

    @Override
    public String gearState() {
        return String.format(Locale.ROOT, "%s · step %s · fuel %.0f L · plugs %d · %d welds%s", state, STEPS[done], fuel, plugs, welds, target == 0 ? "" : " · defect #" + target);
    }

    @Override public int wiredLamp() { return !powered() ? 3 : fuel < 20 || plugs == 0 ? 2 : 1; }

    private AssetData.Defect weldable(BlockPos rail) {
        for (AssetData.Defect df : RailFlaws.openAt(world, rail)) {
            if (df.stage < AssetData.DISCOVERED) continue;                               // nobody knows it's there
            RailFlaws.Flaw f = RailFlaws.of(df.type);
            if (f == null || f.remedy.equals("WELD") || f == RailFlaws.Flaw.TRANSVERSE && df.seen < 15) return df;
        }
        return null;
    }

    @Override
    protected void serverTick() {
        long now = world.getTotalWorldTime();
        if (age % 20 == 9) {
            battery();
            burn(running == 3 || running == 4 ? 1 : running > 0 ? 0.5 : travel ? 0.3 : 0);
            double tgt = running == 3 ? Math.min(1150, 20 + 1100.0 * (1 - (until - now) / (SECS[3] * 20.0))) : running == 4 ? 1350 : 0;
            temp = tgt > temp ? tgt : Math.max(Weather.railC(world, pos), temp - (temp > 400 ? 40 : 8));
        }
        if (estop() && (engine || travel || running > 0)) { engine = false; travel = false; seek = false; running = 0; state = "E-STOP"; log("EMERGENCY STOP"); }
        if (running > 0 && now >= until) finishStep();
        if (running == 1 || running == 6) sparks(EnumParticleTypes.LAVA, 4);
        if (running == 3 || running == 4) sparks(EnumParticleTypes.FIREWORKS_SPARK, running == 4 ? 14 : 5);
        if ((running == 1 || running == 6) && age % 30 == 0) world.playSound(null, pos, com.dogpound.railmap.ModSounds.LOOP_CRUSHER, SoundCategory.BLOCKS, 0.6f, 1.6f);
        if ((running == 3 || running == 4) && age % 20 == 0) world.playSound(null, pos, com.dogpound.railmap.ModSounds.ARC_BUZZ, SoundCategory.BLOCKS, 0.8f, running == 4 ? 0.8f : 1.1f);
        if (travel && engine && !estop() && now >= nextStep) {
            if (seek && weldable(pos.down()) != null) {
                travel = false; seek = false;
                AssetData.Defect df = weldable(pos.down());
                state = "AT DEFECT #" + df.id;
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1f);
            } else if (!possessionOk()) { travel = false; seek = false; state = "NO POSSESSION"; }
            else {
                String[] why = {""};
                BlockPos c = next(why);
                if (c == null) { travel = false; seek = false; state = why[0]; }
                else { nextStep = now + 20; state = seek ? "SEEKING DEFECT" : "TRAVELLING"; panel(); moveTo(c); return; }
            }
        }
        if (age % 5 == 0) panel();
    }

    private void sparks(EnumParticleTypes t, int n) {
        if (age % 3 == 0 && world instanceof WorldServer ws) ws.spawnParticle(t, pos.getX() + 0.5, pos.getY() - 0.6, pos.getZ() + 0.5, n, 0.25, 0.1, 0.25, 0.08);
    }

    private void panel() {
        commonPanel();
        for (int i = 1; i <= 7; i++) put("l" + i, done >= i);
        put("arc", running == 3 || running == 4); put("busy", running > 0); put("move", travel); put("noplug", plugs == 0);
        put("g_temp", (int) Math.round(Math.min(100, temp / 14)));
        text("step", running > 0 ? STEPS[running] + " " + Math.max(0, (until - world.getTotalWorldTime()) / 20) + " S" : "DONE " + STEPS[done]);
        text("temp", String.format(Locale.ROOT, "WELD %.0f C", temp));
        text("amp", running == 4 ? "I 52 KA UPSET" : running == 3 ? "I 38 KA FLASH" : "I 0 KA");
        text("upset", String.format(Locale.ROOT, "UPSET %.1f MM", upset));
        double rail = Weather.railC(world, pos), sft = Weather.on() ? Weather.cell(world, pos).sft : 27;
        text("rail", String.format(Locale.ROOT, "RAIL %.0f C SFT %.0f", rail, sft));
        AssetData.Defect df = target == 0 ? weldable(pos.down()) : AssetData.get(world).defect(target);
        text("target", df == null ? "NO DEFECT HERE" : String.format(Locale.ROOT, "#%d %s %.1fMM", df.id, df.type.substring(0, Math.min(14, df.type.length())).toUpperCase(Locale.ROOT), df.seen));
        text("plug", "PLUGS " + plugs);
        text("res", result);
    }

    private String begin(int step, EntityPlayer p) {
        if (!engine) return "§cStart the engine (the generator powers the welding head)";
        if (estop()) return "§cE-STOP is latched";
        if (travel) return "§cStop the machine first";
        if (running > 0) return "§c" + STEPS[running] + " still running";
        if (!possessionOk()) return "§cNO POSSESSION - the line is open";
        if (step != done + 1) return "§cOut of sequence: next is " + STEPS[Math.min(7, done + 1)] + (done == 7 ? " (sequence complete - CUT starts a new one)" : "");
        if (step == 1) {
            AssetData.Defect df = weldable(pos.down());
            if (df == null) return "§cNo weldable rail defect logged in this rail - SEEK one, or find it with the ultrasonic tester";
            if (plugs < 1) return "§cNo closure rail - put 2 iron ingots in the plug hopper";
            target = df.id;
            plugs--;
            fusionOk = true; upset = 0; result = "";
        }
        running = step;
        until = world.getTotalWorldTime() + Math.round(SECS[step] * 20 * Math.max(0.2, InfraConfig.possessionTaskScale));
        log(STEPS[step] + " started by " + who(p) + (target == 0 ? "" : " (defect #" + target + ")"));
        return "§b" + STEPS[step] + " (" + SECS[step] + " s)";
    }

    private void finishStep() {
        int s = running;
        running = 0;
        done = s;
        switch (s) {
            case 3: if (temp < 1000) { fusionOk = false; result = "PREHEAT LOW " + Math.round(temp) + " C"; } break;
            case 4: {
                upset = 12 + world.rand.nextDouble() * 4;
                if (temp < 1000) fusionOk = false;
                double rail = Weather.railC(world, pos), sft = Weather.on() ? Weather.cell(world, pos).sft : 27;
                if (Math.abs(rail - sft) > 10) log(String.format(Locale.ROOT, "welded at %.0f C rail, %.0f C off the stress-free temperature - destress this rail before the hot weather", rail, rail - sft));
                welds++;
                break;
            }
            case 7: ultrasonic(); break;
            default:
        }
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.4f, 1.2f);
        state = STEPS[s] + " DONE";
    }

    private void ultrasonic() {
        AssetData d = AssetData.get(world);
        AssetData.Defect df = d.defect(target);
        TileRail piece = Rails.pieceAt(world, pos.down());
        if (df != null && df.stage < AssetData.REPAIRED) RailFlaws.repaired(world, df, "cut out, closure rail flash-butt welded in by the rail welder", fusionOk);
        if (fusionOk) { result = "UT CLEAN - DEFECT CLOSED"; log("weld UT clean - defect #" + target + " closed"); }
        else {
            result = "UT: LACK OF FUSION";
            if (piece != null) {
                AssetData.Asset a = d.at(AssetKind.TRACK, Rails.pos(piece).toLong());
                if (a != null) {
                    AssetData.Defect nd = d.addDefect(world, a.id, a.pos, RailFlaws.Flaw.WELD.type(), "mm", 3 + world.rand.nextDouble() * 2, RailFlaws.Flaw.WELD.crit);
                    nd.seen = nd.size; nd.unc = 0.5; nd.by = "rail welder UT"; nd.method = "weld UT";
                    nd.step(world, AssetData.CONFIRMED, "weld UT: lack of fusion - the rail ends were too cold");
                }
            }
            log("weld UT FAILED: lack of fusion (preheat too cold) - recut the weld");
        }
        com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "rail_welded", "defect", target, "clean", fusionOk);
        target = 0;
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        for (int i = 1; i <= 7; i++) if (IDS[i].equals(id)) {
            if (i == 1 && done == 7) done = 0;
            return begin(i, p);
        }
        switch (id) {
            case "test": return lampTest();
            case "start": return startStop(p);
            case "seek": case "go": {
                if (travel) { travel = false; seek = false; state = "STOPPED"; return "§eStopped"; }
                if (!engine) return "§cStart the engine first";
                if (running > 0) return "§cFinish the step first";
                if (done > 0 && done < 7) return "§cThe weld is open (" + STEPS[done] + " done) - finish the sequence before moving";
                if (!possessionOk()) return "§cNO POSSESSION - the line is open";
                travel = true; seek = id.equals("seek"); nextStep = world.getTotalWorldTime() + 10;
                return seek ? "§aSeeking the next logged rail defect" : "§aTravelling";
            }
            case "m_estop": {
                ctl.set("m_estop", 1 - ctl.get("m_estop"));
                if (ctl.get("m_estop") == 1) { engine = false; travel = false; seek = false; running = 0; state = "E-STOP"; }
                return ctl.get("m_estop") == 1 ? "§cEMERGENCY STOP" : "§7E-stop released";
            }
            case "z_fuel": return refuel(p);
            case "z_plug": {
                if (p == null) return "§7Plugs are loaded at the hopper";
                ItemStack h = p.getHeldItemMainhand();
                if (h.getItem() != Items.IRON_INGOT || h.getCount() < 2) return "§7Closure rails: " + plugs + " - hold 2 iron ingots to cut a new plug";
                if (plugs >= 4) return "§7The hopper holds 4 plugs";
                if (!p.capabilities.isCreativeMode) h.shrink(2);
                plugs++;
                return "§aClosure rail loaded (" + plugs + ")";
            }
            case "s_dir": if (travel) return "§cStop before reversing"; return null;
            default: return null;
        }
    }

    @Override
    protected void store(NBTTagCompound t) {
        storeMachine(t);
        t.setInteger("done", done); t.setInteger("run", running); t.setInteger("target", target); t.setInteger("plugs", plugs); t.setInteger("welds", welds);
        t.setLong("until", until); t.setDouble("temp", temp); t.setDouble("upset", upset); t.setBoolean("seek", seek); t.setBoolean("fusion", fusionOk); t.setString("result", result);
    }

    @Override
    protected void restore(NBTTagCompound t) {
        restoreMachine(t);
        done = t.getInteger("done"); running = t.getInteger("run"); target = t.getInteger("target"); plugs = t.getInteger("plugs"); welds = t.getInteger("welds");
        until = t.getLong("until"); temp = t.hasKey("temp") ? t.getDouble("temp") : 20; upset = t.getDouble("upset"); seek = t.getBoolean("seek");
        fusionOk = !t.hasKey("fusion") || t.getBoolean("fusion"); result = t.getString("result");
    }

    static List<String> describe() { return java.util.Arrays.asList(STEPS); }
}
