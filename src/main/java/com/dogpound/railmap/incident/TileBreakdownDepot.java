package com.dogpound.railmap.incident;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

/**
 * The Breakdown Crew Depot (her ChatGPT list #68): the tool van store where the breakdown gang waits on call. Its
 * three lockers are real storage and really matter: hydraulic rerailing jacks (pistons, 2 needed), packing timbers
 * and ramps (planks / logs, 16), lifting slings (leads, 2). With the full kit each car is rerailed / cut up faster;
 * with none, slower. The crew board shows READY / TURNING OUT / ON THE ROAD / ON SITE / RETURNING, the job, the ETA;
 * the turn-out bell rings. AVAILABLE key ON CALL / STOOD DOWN, CREWS 1 or 2 (two gangs work quicker). TURN OUT sends
 * it to the nearest wreck waiting for a crew; wrecks call it themselves through incident command (IncidentHooks).
 * Needs 12 V for the turn-out system (no power: it can't be called).
 */
public class TileBreakdownDepot extends TileFurniture {
    public static final String[] STORE_IDS = {"jacks", "pack", "slings"};
    public static final String[] STORE_NAMES = {"Rerailing Jacks Locker", "Packing & Ramps Store", "Lifting Gear Locker"};
    public static final int[] STORE_SIZES = {9, 18, 9};
    static final String[] STATE = {"READY", "TURNING OUT", "ON THE ROAD", "ON SITE", "RETURNING"};

    private int state, job, eta;
    private long since;
    private String jobWhat = "";
    private long jobPos;

    public TileBreakdownDepot() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, 1); }

    @Override protected boolean mechanical(String id) { return id.startsWith("k_") || id.startsWith("s_"); }

    public boolean ready() { return term.powered() && ctl.get("k_avail") == 0 && state == 0; }

    private int count(IInventory inv, Item... items) {
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            for (Item it : items) if (!s.isEmpty() && s.getItem() == it) n += s.getCount();
        }
        return n;
    }

    int jacks() { return count(storage(0), Item.getItemFromBlock(Blocks.PISTON), Item.getItemFromBlock(Blocks.STICKY_PISTON)); }
    int packing() { return count(storage(1), Item.getItemFromBlock(Blocks.PLANKS), Item.getItemFromBlock(Blocks.LOG), Item.getItemFromBlock(Blocks.LOG2)); }
    int slings() { return count(storage(2), Items.LEAD); }

    /** 0..3 kit items complete */
    int kit() { return (jacks() >= 2 ? 1 : 0) + (packing() >= 16 ? 1 : 0) + (slings() >= 2 ? 1 : 0); }

    String kitLine() { return kit() == 3 ? "full rerailing kit" : kit() == 0 ? "NO KIT (slow)" : kit() + "/3 of the kit"; }

    /** how long each car takes, against the railway's default (full kit 0.6, none 1.4; two gangs x0.75) */
    double workFactor() {
        double f = new double[]{1.4, 1.15, 0.85, 0.6}[kit()];
        return f * (ctl.get("s_crews") == 1 ? 0.75 : 1.0);
    }

    void turnOut(int id, String what, int etaSeconds, BlockPos at) {
        job = id; jobWhat = what; eta = etaSeconds; jobPos = at.toLong();
        state = 1; since = world.getTotalWorldTime();
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.FIRE_BELL, SoundCategory.BLOCKS, 1.2f, 1.0f);
        Recorder.event(world, "Breakdown crew at " + pos.getX() + "," + pos.getZ() + " turned out to incident #" + id + ", ETA " + etaSeconds + " s");
        markDirty();
    }

    void standDown(String why) {
        if (state == 0) return;
        state = 4; since = world.getTotalWorldTime();
        Recorder.event(world, "Breakdown crew at " + pos.getX() + "," + pos.getZ() + " returning (" + why + ")");
        markDirty();
    }

    @Override
    protected void serverTick() {
        long t = world.getTotalWorldTime() - since;
        if (state == 1 && t >= 600) { state = 2; markDirty(); }                     // 30 s to turn out
        else if (state == 2 && t >= eta * 20L) { state = 3; markDirty(); }
        else if (state == 4 && t >= Math.max(600, eta * 20L)) { state = 0; job = 0; jobWhat = ""; markDirty(); }
        if (state == 1 && t % 20 == 0 && t < 200) world.playSound(null, pos, com.dogpound.railmap.ModSounds.FIRE_BELL, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "turnout": return IncidentHooks.manual(world, this);
            case "standdown":
                if (state == 0) return "§7The crew is in";
                standDown("stood down by " + (who == null ? "console" : who.getName()));
                return "§eCrew stood down - returning";
            case "test": ctl.set("lamptest", 1); return "§7Lamp test";
            default: return null;
        }
    }

    @Override
    protected boolean refresh() {
        boolean ch = false, test = ctl.get("lamptest") == 1 && world.getTotalWorldTime() - ctl.pressAt < 60;
        if (!test && ctl.get("lamptest") == 1) { ctl.set("lamptest", 0); ch = true; }
        ch |= put("power", 1);
        ch |= put("ready", test || state == 0 && ctl.get("k_avail") == 0);
        ch |= put("out", test || state == 1 || state == 2);
        ch |= put("onsite", test || state == 3);
        ch |= put("returning", test || state == 4);
        ch |= put("bell", test || state == 1);
        ch |= put("kjack", test || jacks() >= 2);
        ch |= put("kpack", test || packing() >= 16);
        ch |= put("ksling", test || slings() >= 2);
        ch |= put("kit", kit() * 100 / 3);
        ch |= txt("stat", ctl.get("k_avail") == 1 && state == 0 ? "STOOD DOWN" : STATE[state]);
        ch |= txt("job", job == 0 ? "NO JOB" : ("INC " + job + " " + cut(jobWhat, 16)).toUpperCase(java.util.Locale.ROOT));
        long left = Math.max(0, eta - (world.getTotalWorldTime() - since) / 20);
        ch |= txt("eta", state == 1 || state == 2 ? "ETA " + left + " S" : state == 3 ? "ON SITE" : "--");
        ch |= put("jacks", Math.min(9, jacks()));
        ch |= put("packs", Math.min(99, packing()));
        ch |= put("slingn", Math.min(9, slings()));
        return ch;
    }

    private boolean put(String id, int v) { return StationControls.put(ctl, id, v); }
    private boolean put(String id, boolean v) { return StationControls.put(ctl, id, v); }
    private boolean txt(String id, String t) { return StationControls.text(ctl, id, t); }

    @Override public String wiredName() { return "Breakdown crew depot"; }
    @Override public String gearState() { return term.powered() ? STATE[state] + (job == 0 ? "" : " · incident #" + job) + " · " + kitLine() : "NO 12 V - can't be called"; }
    @Override public void gearInfo(java.util.Map<String, Object> m) { m.put("state", STATE[state]); m.put("job", job); m.put("kit", kit()); }

    @Override protected void writeExtra(NBTTagCompound t) { t.setInteger("st", state); t.setInteger("job", job); t.setInteger("eta", eta); t.setLong("since", since); t.setString("jw", jobWhat); t.setLong("jp", jobPos); }
    @Override protected void readExtra(NBTTagCompound t) { state = t.getInteger("st"); job = t.getInteger("job"); eta = t.getInteger("eta"); since = t.getLong("since"); jobWhat = t.getString("jw"); jobPos = t.getLong("jp"); }
}
