package com.dogpound.railmap.freight6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.Locale;

/**
 * Grain Leg (batch 6 #26): the bucket elevator - boot, twin casings, head house with the 30 kW drive and the
 * distributor (turnhead) that spouts to a bin or the dryer - with the plant's MCC and control desk at its foot.
 * START / STOP the leg, turn the DISTRIBUTOR (BIN 1-6 / DRYER), AUTO runs it whenever the pit has grain. It lifts
 * F6Config.legRate items a second from the Receiving Pit (within 12 blocks) to the bin (within 24 - numbered west to
 * east) or into the Grain Dryer's garner. A full bin or garner CHOKES the leg (alarm, it stops). The motor ammeter and
 * the head-pulley speed switch read the real load. Its screen has pages (PAGE): plant / bins / totals. Mains + 12 V.
 */
public class TileGrainLeg extends com.dogpound.railmap.depot.DepotTile {
    static final String[] ROUTE = {"BIN 1", "BIN 2", "BIN 3", "BIN 4", "BIN 5", "BIN 6", "DRYER"};
    private boolean run, choke;
    private int moved, total, amps, page;
    private String last = "";

    public TileGrainLeg() { super(new String[]{"spares"}, new String[]{"Leg spares (buckets, belt clips)"}, new int[]{9}); }

    @Override public String wiredName() { return "Grain leg"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return run ? 300 : 3; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : (run ? "RUNNING to " + ROUTE[route()] : choke ? "CHOKED" : "stopped") + String.format(Locale.ROOT, " · %s t moved", Grain.t(total)); }

    public boolean running() { return run; }
    int route() { return Math.floorMod(ctl.get("s_route"), ROUTE.length); }
    TileGrainPit pit() { List<TileGrainPit> l = StnUtil.near(world, TileGrainPit.class, pos, 12); return l.isEmpty() ? null : l.get(0); }
    TileGrainDryer dryer() { List<TileGrainDryer> l = StnUtil.near(world, TileGrainDryer.class, pos, 16); return l.isEmpty() ? null : l.get(0); }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "start" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (!mainsOk()) return "§cNo mains for the leg drive (" + mains.what + ")";
                if (pit() == null) return "§cNo Grain Receiving Pit within 12 blocks";
                if (route() < 6 && Grain.bin(world, pos, route()) == null) return "§c" + ROUTE[route()] + " does not exist - place Grain Silos within 24 blocks";
                if (route() == 6 && dryer() == null) return "§cNo Grain Dryer within 16 blocks";
                run = true; choke = false; moved = 0;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.motor_start"), net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 1f);
                AlarmManager.get(world).clear("grain:choke:" + pos.toLong());
                return "§aLeg running - spouting to " + ROUTE[route()];
            }
            case "stop" -> { run = false; return String.format(Locale.ROOT, "§eLeg stopped - %s t this run", Grain.t(moved)); }
            case "page" -> { page = (page + 1) % 3; return "§7Screen page " + (page + 1) + "/3"; }
            case "reset" -> { choke = false; AlarmManager.get(world).clear("grain:choke:" + pos.toLong()); return "§7Choke reset - clear the spout, then START"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        switch (id) {
            case "m_estop" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) run = false; return ctl.get(id) == 1 ? "§cE-STOP - leg braked" : "§aE-stop released"; }
            case "s_route" -> {
                if (run) return "§cStop the leg before turning the distributor";
                ctl.set(id, (ctl.get(id) + 1) % ROUTE.length);
                return "§eDistributor: " + ROUTE[route()];
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        TileGrainPit pit = pit();
        if (!run && ctl.get("k_auto") == 1 && powered() && mainsOk() && !choke && ctl.get("m_estop") == 0 && pit != null && pit.count() > 0) run = true;
        amps = run ? 8 : 0;
        if (run) {
            if (!powered() || !mainsOk() || pit == null || ctl.get("m_estop") == 1) run = false;
            else {
                ItemStack s = pit.draw(F6Config.legRate);
                if (!s.isEmpty()) {
                    int put;
                    String to = ROUTE[route()];
                    if (route() == 6) { TileGrainDryer d = dryer(); put = d == null ? 0 : d.fill(s, s.getCount(), pit.moist); }
                    else { TileGrainSilo b = Grain.bin(world, pos, route()); put = b == null ? 0 : b.add(s, s.getCount(), pit.moist); }
                    if (put < s.getCount()) {
                        ItemStack back = s.copy(); back.setCount(s.getCount() - put); pit.giveBack(back);
                        run = false; choke = true;
                        AlarmManager.get(world).raise(world, "grain:choke:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR,
                                "Grain leg at " + pos.getX() + "," + pos.getZ() + ": LEG CHOKE - " + to + " full or holds another grain", pos, true);
                        Recorder.event(world, "Grain leg choked spouting to " + to);
                        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("alarm.annunciator"), net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 1f);
                    }
                    moved += put; total += put; last = Grain.name(s);
                    amps = 8 + put * 40 / Math.max(1, F6Config.legRate);
                    if (age % 60 == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.conveyor"), net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 0.9f);
                } else if (ctl.get("k_auto") == 1) run = false;                  // AUTO: the pit ran dry, stop until more comes
            }
        }
        TileGrainDryer dr = dryer();
        List<TileGrainSilo> bins = Grain.bins(world, pos);
        put("pwr", powered()); put("mainsl", mains.ok()); put("runl", run); put("chokel", choke); put("pitl", pit != null && pit.count() > 0);
        put("autol", ctl.get("k_auto") == 1); put("dryerl", dr != null && dr.burning()); put("speedl", run);
        put("ampg", Math.min(100, amps * 2)); put("pitg", pit == null ? 0 : pit.count() * 100 / (27 * 64));
        for (int i = 0; i < Grain.MAX_BINS; i++) { put("b" + i, i < bins.size() ? bins.get(i).pct() : 0); put("bl" + i, route() == i); }
        put("bl6", route() == 6);
        text("amps", String.valueOf(amps));
        text("rpm", run ? "62" : "0");
        switch (page) {
            case 1 -> {
                StringBuilder a = new StringBuilder(), b = new StringBuilder();
                for (int i = 0; i < bins.size(); i++) (i < 3 ? a : b).append(i + 1).append(':').append(bins.get(i).pct()).append("% ");
                text("line1", "BINS " + (a.length() == 0 ? "NONE" : a.toString().trim()));
                text("line2", b.toString().trim());
                text("line3", dr == null ? "NO DRYER" : String.format(Locale.ROOT, "DRYER %s T %.1f%%", Grain.t(dr.qty()), dr.moist()));
            }
            case 2 -> {
                text("line1", "MOVED " + Grain.t(total) + " T");
                text("line2", "THIS RUN " + Grain.t(moved) + " T");
                text("line3", cut(last.toUpperCase(Locale.ROOT), 18));
            }
            default -> {
                text("line1", (run ? "RUN > " : choke ? "CHOKE " : "STOP  ") + ROUTE[route()]);
                text("line2", pit == null ? "NO PIT" : String.format(Locale.ROOT, "PIT %d  %.1f%%", pit.count(), pit.moist));
                text("line3", String.format(Locale.ROOT, "%d A  %s RPM", amps, run ? "62" : "0"));
            }
        }
        text("pageno", (page + 1) + "/3");
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setBoolean("run", run); t.setBoolean("choke", choke); t.setInteger("moved", moved); t.setInteger("total", total); t.setInteger("page", page); t.setString("last", last); }
    @Override protected void loadExtra(NBTTagCompound t) { run = t.getBoolean("run"); choke = t.getBoolean("choke"); moved = t.getInteger("moved"); total = t.getInteger("total"); page = t.getInteger("page"); last = t.getString("last"); }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 4, 2); }
}
