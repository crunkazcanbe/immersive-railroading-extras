package com.dogpound.railmap.steam;

import cam72cam.immersiverailroading.entity.FreightTank;
import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Locale;

/**
 * Coaling Tower (batch 5 #2): a reinforced-concrete bunker on legs over the track, with a skip hoist up its side and a
 * counterweighted chute with an electro-pneumatic gate. Multiblock: it works with a Coal Receiving Pit beside it
 * (FORMED lamp). The skip (2 t = 40 coal) loads from the pit, the 55 kW hoist motor winds it up the tower, it tips into
 * the bunker and runs back down - one cycle per HOIST, or continuously on AUTO while the pit has coal and the bunker has
 * room. Lower the CHUTE lever over the tender / tank loco standing under it, pick the quantity (1/4, 1/2, FULL) and press
 * DISCHARGE: coal runs down the chute into its bunker until that much is in, it is full, you STOP or the bunker is empty.
 * The weigh beam shows the tonnes loaded. Mains for the hoist + 12 V for the controls; the chute lever is mechanical.
 */
public class TileCoalingTower extends com.dogpound.railmap.depot.DepotTile {
    static final int SKIP = 40, RATE = 4;           // coal per skip, coal per second down the chute
    static final String[] QTY = {"1/4", "1/2", "FULL"};
    private int bunker, skipLoad, phase, loaded, loadedTotal, cycles, tipT, tgt;
    private float skip;                              // 0 bottom .. 100 top
    private boolean flowing;

    public TileCoalingTower() { super(new String[]{"locker"}, new String[]{"Tower locker (oil, ropes, sledge)"}, new int[]{9}); }

    @Override public String wiredName() { return "Coaling tower"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return phase == 2 || phase == 4 ? 550 : flowing ? 40 : 5; }
    @Override public String gearState() {
        return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "bunker %d/%d coal · skip %s · %s", bunker, SteamConfig.bunkerItems, PHASES[phase], flowing ? "DISCHARGING" : !formed() ? "NOT FORMED (pit beside + bunker above)" : "ready");
    }
    static final String[] PHASES = {"AT BOTTOM", "LOADING", "HOISTING", "TIPPING", "LOWERING"};

    public int bunker() { return bunker; }

    TileCoalPit pit() {
        for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.HORIZONTALS) if (world.getTileEntity(pos.offset(f)) instanceof TileCoalPit p) return p;
        return null;
    }

    /** the elevated bunker: 2-14 blocks straight above the base (null = not formed) */
    public net.minecraft.util.math.BlockPos bunkerPos() {
        for (int dy = 2; dy <= 14; dy++) if (world.getBlockState(pos.up(dy)).getBlock() instanceof BlockCoalBunker) return pos.up(dy);
        return null;
    }
    boolean formed() { return pit() != null && bunkerPos() != null; }

    /** the tender / tank engine under the chute (within 5 blocks of the bunker, below it) */
    FreightTank under() { net.minecraft.util.math.BlockPos b = bunkerPos(); return b == null ? null : Stock.nearest(world, b, 8, FreightTank.class, s -> Stock.steamStock(s) && s.getPosition().y < b.getY()); }
    boolean chuteDown() { return ctl.get("m_chute") == 1; }
    int target() { int q = Math.floorMod(ctl.get("s_qty"), 3); FreightTank t = under(); int room = t == null ? 0 : Stock.coalRoom(t); return q == 2 ? room : q == 1 ? room / 2 + 1 : room / 4 + 1; }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "hoist" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (pit() == null) return "§cNOT FORMED - place a Coal Receiving Pit beside the tower";
                if (bunkerPos() == null) return "§cNOT FORMED - set the Coaling Tower Bunker 2-14 blocks above the tower";
                if (!mainsOk()) return "§cNo mains for the hoist (" + mains.what + ")";
                if (phase != 0) return "§7The skip is already running (" + PHASES[phase] + ")";
                if (bunker >= SteamConfig.bunkerItems) return "§eThe bunker is full";
                phase = 1;
                return "§aSkip loading at the pit";
            }
            case "discharge" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (!chuteDown()) return "§cLower the chute first (CHUTE lever)";
                FreightTank t = under();
                if (t == null) return "§cNo tender or tank engine under the chute";
                if (!Stock.standing(t)) return "§cThe engine must be standing";
                if (bunker <= 0) return "§cThe bunker is empty - hoist coal up first";
                if (Stock.coalRoom(t) <= 0) return "§7Its bunker is full";
                flowing = true; loaded = 0; tgt = target();
                world.playSound(null, pos, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.7f, 0.6f);
                return "§aGate open - coaling " + Stock.name(t) + " (" + QTY[Math.floorMod(ctl.get("s_qty"), 3)] + ")";
            }
            case "stop" -> { flowing = false; if (phase == 2) phase = 4; return "§eGate shut" + (loaded > 0 ? String.format(Locale.ROOT, " - %.2f t loaded", loaded * 0.05) : ""); }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        switch (id) {
            case "m_estop" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) { flowing = false; if (phase == 2) phase = 4; } return ctl.get(id) == 1 ? "§cE-STOP - hoist braked, gate shut" : "§aE-stop released"; }
            case "m_chute" -> {
                if (flowing) return "§cShut the gate before raising the chute";
                ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ctl.lever"), SoundCategory.BLOCKS, 0.8f, 0.7f);
                return ctl.get(id) == 1 ? "§eChute lowered over the track" : "§7Chute raised clear of the loading gauge";
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        boolean run = powered() && mainsOk() && ctl.get("m_estop") == 0;
        TileCoalPit pit = pit();
        // the skip hoist, every tick
        switch (phase) {
            case 1 -> { if (age % 20 == 0) { if (pit == null) phase = 0; else { skipLoad = pit.take(SKIP); phase = skipLoad > 0 ? 2 : 0; } } }
            case 2 -> { if (run && bunkerPos() != null) { skip = Math.min(100, skip + 0.6f); if (skip >= 100) { phase = 3; tipT = 0; } } }
            case 3 -> { if (++tipT > 30) {
                int put = Math.min(skipLoad, SteamConfig.bunkerItems - bunker);
                bunker += put; skipLoad -= put; cycles++;
                world.playSound(null, pos.up(2), SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 1.0f, 0.6f);
                phase = 4; } }
            case 4 -> { if (run) { skip = Math.max(0, skip - 0.8f); if (skip <= 0) phase = ctl.get("k_auto") == 1 && pit != null && pit.coal() > 0 && bunker < SteamConfig.bunkerItems && run ? 1 : 0; } }
            default -> { if (ctl.get("k_auto") == 1 && run && pit != null && pit.coal() >= SKIP / 2 && bunker <= SteamConfig.bunkerItems - SKIP && age % 40 == 0) phase = 1; }
        }
        if (age % 5 == 0) put("skipg", Math.round(skip));
        if (age % 20 != 0) return;
        FreightTank t = under();
        if (flowing) {
            if (!run || !chuteDown() || t == null || !Stock.standing(t) || bunker <= 0) {
                flowing = false;
                if (bunker <= 0) AlarmManager.get(world).raise(world, "coal:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "Coaling tower at " + pos.getX() + "," + pos.getZ() + ": BUNKER EMPTY", pos, false);
            } else {
                int want = Math.min(RATE, Math.min(bunker, tgt - loaded));
                int put = want <= 0 ? 0 : Stock.addCoal(t, want);
                bunker -= put; loaded += put; loadedTotal += put;
                if (put > 0) world.playSound(null, pos, SoundEvents.BLOCK_GRAVEL_FALL, SoundCategory.BLOCKS, 0.9f, 0.7f);
                if (put < want || loaded >= tgt) {
                    flowing = false;
                    Recorder.event(world, String.format(Locale.ROOT, "Coaling tower: %s coaled, %.2f t", Stock.name(t), loaded * 0.05));
                    world.playSound(null, pos, ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.5f, 1.0f);
                }
            }
        }
        if (bunker > SteamConfig.bunkerItems / 10) AlarmManager.get(world).clear("coal:" + pos.toLong());
        net.minecraft.util.math.BlockPos bp = bunkerPos();
        put("bdy", bp == null ? 0 : bp.getY() - pos.getY());
        put("pwr", powered()); put("mainsl", mains.ok()); put("formed", pit != null && bp != null);
        put("hoistl", phase == 2 || phase == 4); put("topl", phase == 3); put("botl", phase == 0 || phase == 1);
        put("chutel", chuteDown()); put("flow", flowing); put("low", bunker < SteamConfig.bunkerItems / 10); put("full", bunker >= SteamConfig.bunkerItems);
        put("locol", t != null); put("autol", ctl.get("k_auto") == 1 && run);
        put("bunkerg", bunker * 100 / Math.max(1, SteamConfig.bunkerItems));
        put("pitg", pit == null ? 0 : pit.coal() * 100 / (54 * 64));
        put("roomg", t == null ? 0 : Math.max(0, 100 - Stock.coalRoom(t) * 100 / Math.max(1, (t.cargoItems.getSlotCount() - 2) * 64)));
        text("tons", String.format(Locale.ROOT, "%.2f", loaded * 0.05));
        text("bunk", String.format(Locale.ROOT, "%.1f T", bunker * 0.05));
        text("line1", t == null ? "NO ENGINE UNDER CHUTE" : cut(Stock.name(t).toUpperCase(Locale.ROOT), 20));
        text("line2", PHASES[phase] + " " + cycles + " SKIPS");
    }

    /** the skip's height (0..1) for the renderer */
    public float skip() { return ctl.get("skipg") / 100f; }

    @Override protected void saveExtra(NBTTagCompound t) {
        t.setInteger("bunker", bunker); t.setInteger("skipLoad", skipLoad); t.setInteger("phase", phase); t.setFloat("skip", skip);
        t.setBoolean("flow", flowing); t.setInteger("loaded", loaded); t.setInteger("lt", loadedTotal); t.setInteger("cyc", cycles); t.setInteger("tgt", tgt);
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        bunker = t.getInteger("bunker"); skipLoad = t.getInteger("skipLoad"); phase = t.getInteger("phase"); skip = t.getFloat("skip");
        flowing = t.getBoolean("flow"); loaded = t.getInteger("loaded"); loadedTotal = t.getInteger("lt"); cycles = t.getInteger("cyc"); tgt = t.getInteger("tgt");
    }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 4, 2); }
}
