package com.dogpound.railmap.incident;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.grid.GridData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.Locale;
import java.util.Map;

/**
 * Fire Alarm & Suppression Panel (her ChatGPT list #69): a fire alarm control panel for an equipment room, a station
 * or a stretch of line, with the water-mist suppression system's controls and an extinguisher cabinet underneath.
 * It watches its zone (8-32 m): flames, burning creatures, burning rolling stock (wrecked tank cars) = FIRE; an
 * overheating or burnt-out grid machine (transformer over temperature) = PRE-ALARM. On FIRE it raises the alarm,
 * opens a fire incident (closing the line through the zone), reads the placards of the cars in the zone (dangerous
 * goods change the response: foam, cordon, evacuation, clean-up time), may ISOLATE the traction feeders (key AUTO or
 * the button), and in AUTO counts down preDischargeSeconds then releases the water mist (ABORT switch holds it; the
 * RELEASE mushroom starts it by hand): flames in the zone go out, burning things are put out. The agent cylinder
 * then needs refilling - water buckets into the AGENT store, 25 % each. RESET once the fire is out; EVACUATE starts
 * the evacuation. No 12 V = FAULT (the panel is dead - an alarm says so).
 */
public class TileFirePanel extends TileFurniture {
    public static final String[] STORE_IDS = {"agent", "ext", "kit"};
    public static final String[] STORE_NAMES = {"Water-Mist Agent Fill (water buckets)", "Extinguisher Cabinet", "Fire Point Kit"};
    public static final int[] STORE_SIZES = {9, 9, 9};
    static final String[] STATE = {"NORMAL", "PRE-ALARM", "FIRE", "PRE-DISCHARGE", "DISCHARGED"};

    private int state, agent = 100, countdown, fires, quiet, incident;
    private String placard = "", rule = "", why = "";
    private boolean silenced, wasPowered;

    public TileFirePanel() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, 1); }

    @Override protected boolean mechanical(String id) { return id.startsWith("k_") || id.startsWith("s_") || id.startsWith("m_"); }

    public int zone() { return new int[]{8, 16, 24, 32}[Math.floorMod(ctl.get("s_zone"), 4)]; }

    @Override
    protected void serverTick() {
        if (!term.powered()) {
            if (wasPowered || age == 2) AlarmManager.get(world).raise(world, "firepanel:" + pos.toLong(), AlarmManager.Cat.WIRED, AlarmManager.Sev.MAJOR,
                    "Fire panel at " + pos.getX() + "," + pos.getZ() + " has no 12 V - fire detection OUT OF SERVICE", pos, true);
            wasPowered = false;
            return;
        }
        if (!wasPowered) { AlarmManager.get(world).clear("firepanel:" + pos.toLong()); wasPowered = true; }
        refill();
        if (age % 40 == 5) scan();
        if (state == 3 && ctl.get("s_abort") == 0 && age % 20 == 0 && --countdown <= 0) discharge("automatic");
        if (state >= 2 && !silenced && age % 30 == 0) world.playSound(null, pos, com.dogpound.railmap.ModSounds.FIRE_BELL, SoundCategory.BLOCKS, 1.0f, age % 60 == 0 ? 1.4f : 1.0f);
        if (state == 3 && age % 10 == 0) world.playSound(null, pos, com.dogpound.railmap.ModSounds.KLAXON, SoundCategory.BLOCKS, 0.9f, 1.0f);
        if (ctl.get("m_release") == 1 && state != 3 && state != 4) {                   // manual release pulled
            if (agent < 25) { ctl.set("m_release", 0); } else { state = 3; countdown = Math.min(10, IncidentConfig.preDischargeSeconds); markDirty(); }
        }
    }

    private void refill() {
        if (age % 40 != 11 || agent >= 100) return;
        IInventory inv = storage(0);
        for (int i = 0; i < inv.getSizeInventory() && agent < 100; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.getItem() == Items.WATER_BUCKET) { inv.setInventorySlotContents(i, new ItemStack(Items.BUCKET)); agent = Math.min(100, agent + 25); markDirty(); }
        }
    }

    /** look over the zone: flames, burning things, burning cars, hot machines; read the placards */
    private void scan() {
        int r = zone();
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBoxMutable(pos.add(-r, -3, -r), pos.add(r, 4, r)))
            if (world.getBlockState(p).getBlock() == Blocks.FIRE) n++;
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(r, 4, r);
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, box)) if (e.isBurning()) n++;
        Hazmat.Placard worst = new Hazmat.Placard();
        int burningCars = 0;
        try {
            for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
                Vec3d p = s.getPosition();
                if (Math.abs(p.x - pos.getX()) > r || Math.abs(p.z - pos.getZ()) > r || Math.abs(p.y - pos.getY()) > 6) continue;
                Hazmat.Placard pl = Hazmat.of(s);
                if (Hazmat.worse(pl.kind, worst.kind)) worst = pl;
                if (s instanceof EntityCoupleableRollingStock c && c.wreckFire > 0) burningCars++;
            }
        } catch (RuntimeException | LinkageError ignored) { }
        n += burningCars;
        boolean hot = false;
        GridData gd = GridData.get(world);
        for (Map.Entry<Long, GridData.Node> e : gd.nodes().entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            if (Math.abs(p.getX() - pos.getX()) > r || Math.abs(p.getZ() - pos.getZ()) > r) continue;
            if (e.getValue().hot || e.getValue().heat > 90 || e.getValue().burnt) { hot = true; why = (e.getValue().burnt ? "burnt-out " : "overheating ") + e.getValue().kind.name().toLowerCase(Locale.ROOT); }
        }
        placard = worst.line();
        rule = Hazmat.RULE[worst.kind];
        fires = n;
        if (n > 0) {
            quiet = 0;
            why = burningCars > 0 ? burningCars + " burning vehicle(s)" : n + " seat(s) of fire";
            if (state < 2 || state == 4) alarm(worst);
        } else if (state == 2 || state == 3 || state == 4) {
            if (++quiet >= 10) {                                                      // 20 s with nothing burning
                IncidentLog.Rec rec = IncidentLog.get(world).byId(incident);
                if (rec != null && rec.open() && rec.contained == 0) IncidentOps.fireOut(world, rec, state == 4 ? "suppression discharged" : "burnt out / put out by hand");
                if (state == 3) state = 2;
            }
        } else if (hot && state == 0) {
            state = 1;
            AlarmManager.get(world).raise(world, "firepre:" + pos.toLong(), AlarmManager.Cat.FIRE, AlarmManager.Sev.MAJOR, "FIRE PANEL PRE-ALARM " + pos.getX() + "," + pos.getZ() + ": " + why, pos, true);
        } else if (!hot && state == 1) {
            state = 0;
            AlarmManager.get(world).clear("firepre:" + pos.toLong());
        }
        markDirty();
    }

    private void alarm(Hazmat.Placard haz) {
        state = 2;
        silenced = false;
        AlarmManager.get(world).raise(world, "fire:" + pos.toLong(), AlarmManager.Cat.FIRE, AlarmManager.Sev.CRITICAL,
                "FIRE at panel " + pos.getX() + "," + pos.getZ() + ": " + why + (haz.kind == 0 ? "" : " - " + haz.line()), pos, true);
        IncidentLog.Rec rec = IncidentOps.fire(world, pos, zone(), "fire: " + why, haz);
        incident = rec.id;
        if (ctl.get("k_iso") == 1) isolate("fire panel (auto)");
        if (ctl.get("k_mode") == 1 && agent >= 25) { state = 3; countdown = IncidentConfig.preDischargeSeconds; }
    }

    private String isolate(String who) {
        int n = IncidentOps.isolate(world, pos.getX() + 0.5, pos.getZ() + 0.5, zone(), who);
        ctl.set("isodone", n > 0 ? 1 : ctl.get("isodone"));
        IncidentLog.Rec rec = IncidentLog.get(world).byId(incident);
        if (rec != null) { rec.needs |= IncidentLog.ISOLATE; if (n > 0) rec.assigned |= IncidentLog.ISOLATE; IncidentLog.get(world).log(world, rec, n + " feeder breaker(s) opened from the fire panel"); }
        return n == 0 ? "§7No feeder breakers cover this zone" + (IncidentConfig.fireIsolatesPower ? "" : " (fireIsolatesPower is off)") : "§e" + n + " feeder breaker(s) opened";
    }

    private void discharge(String how) {
        if (agent < 25) { state = 2; return; }
        int r = zone(), out = 0;
        for (BlockPos p : BlockPos.getAllInBox(pos.add(-r, -3, -r), pos.add(r, 4, r)))
            if (world.getBlockState(p).getBlock() == Blocks.FIRE) { world.setBlockToAir(p); out++; }
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(pos).grow(r, 4, r))) if (e.isBurning()) { e.extinguish(); out++; }
        try {
            for (EntityCoupleableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityCoupleableRollingStock.class)) {
                Vec3d p = s.getPosition();
                if (Math.abs(p.x - pos.getX()) <= r && Math.abs(p.z - pos.getZ()) <= r && s.wreckFire > 0) { s.wreckFire = 0; out++; }
            }
        } catch (RuntimeException | LinkageError ignored) { }
        agent = 0;
        state = 4;
        ctl.set("m_release", 0);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("alarm.gas_release"), SoundCategory.BLOCKS, 2.0f, 1.0f);
        if (world instanceof WorldServer ws) ws.spawnParticle(EnumParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5, 120, r / 2.0, 1.5, r / 2.0, 0.02);
        IncidentLog.Rec rec = IncidentLog.get(world).byId(incident);
        if (rec != null) IncidentLog.get(world).log(world, rec, "WATER MIST DISCHARGED (" + how + "): " + out + " seat(s) of fire knocked down");
        markDirty();
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        String name = who == null ? "console" : who.getName();
        switch (id) {
            case "silence": silenced = true; return "§eBuzzer silenced";
            case "reset":
                if (fires > 0) return "§cCan't reset - there is still fire in the zone";
                state = 0; silenced = false; countdown = 0; ctl.set("isodone", 0);
                AlarmManager.get(world).clear("fire:" + pos.toLong());
                AlarmManager.get(world).clear("firepre:" + pos.toLong());
                return "§aPanel reset";
            case "evac": {
                IncidentLog log = IncidentLog.get(world);
                IncidentLog.Rec rec = log.byId(incident);
                if (rec == null || !rec.open()) { rec = log.open(world, IncidentLog.Type.EVAC, "evacuation from fire panel " + pos.getX() + "," + pos.getZ(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, zone(), 2); incident = rec.id; Analytics.count(world, "incidents", 1); }
                return IncidentOps.startEvac(world, log, rec, name + " (fire panel)");
            }
            case "isolate": return isolate(name);
            case "discharge":
                if (state != 3) return "§7Nothing counting down";
                discharge("released by " + name); return "§cWater mist released";
            case "test": ctl.set("lamptest", 1); world.playSound(null, pos, com.dogpound.railmap.ModSounds.BUZZER, SoundCategory.BLOCKS, 1f, 1.0f); return "§7Lamp + sounder test";
            default: return null;
        }
    }

    @Override
    protected boolean refresh() {
        boolean ch = false, test = ctl.get("lamptest") == 1 && world.getTotalWorldTime() - ctl.pressAt < 60;
        if (!test && ctl.get("lamptest") == 1) { ctl.set("lamptest", 0); ch = true; }
        ch |= put("power", 1);
        ch |= put("fire", test || state >= 2 && state != 4 || state == 4 && fires > 0);
        ch |= put("pre", test || state == 1);
        ch |= put("fault", test || agent < 25);
        ch |= put("disch", test || state == 4);
        ch |= put("predis", test || state == 3);
        ch |= put("iso", test || ctl.get("isodone") == 1);
        ch |= put("abort", test || ctl.get("s_abort") == 1 && state == 3);
        ch |= put("auto", test || ctl.get("k_mode") == 1);
        ch |= put("sil", test || silenced);
        ch |= put("haz", test || !placard.isEmpty());
        ch |= put("agent", agent);
        ch |= put("cnt", state == 3 ? countdown : 0);
        ch |= put("zonem", zone());
        ch |= txt("stat", (STATE[state] + (state >= 2 && !why.isEmpty() ? ": " + why : state == 1 ? ": " + why : "")).toUpperCase(Locale.ROOT));
        ch |= txt("plac", placard.isEmpty() ? "NO DANGEROUS GOODS IN ZONE" : placard.toUpperCase(Locale.ROOT));
        ch |= txt("rule", placard.isEmpty() ? "STANDARD RESPONSE" : rule.toUpperCase(Locale.ROOT));
        return ch;
    }

    private boolean put(String id, int v) { return StationControls.put(ctl, id, v); }
    private boolean put(String id, boolean v) { return StationControls.put(ctl, id, v); }
    private boolean txt(String id, String t) { return StationControls.text(ctl, id, t); }

    @Override public String wiredName() { return "Fire alarm panel"; }
    @Override public String gearState() { return term.powered() ? STATE[state] + " · zone " + zone() + " m · agent " + agent + " %" + (placard.isEmpty() ? "" : " · " + placard) : "NO 12 V - FAULT, detection out of service"; }
    @Override public int wiredLamp() { return !term.powered() || state >= 2 ? 3 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("state", STATE[state]); m.put("agent", agent); m.put("fires", fires); m.put("placard", placard); }

    @Override protected void writeExtra(NBTTagCompound t) { t.setInteger("st", state); t.setInteger("agent", agent); t.setInteger("cd", countdown); t.setInteger("inc", incident); t.setBoolean("sil", silenced); t.setString("pl", placard); t.setString("why", why); t.setBoolean("wp", wasPowered); }
    @Override protected void readExtra(NBTTagCompound t) { state = t.getInteger("st"); agent = t.hasKey("agent") ? t.getInteger("agent") : 100; countdown = t.getInteger("cd"); incident = t.getInteger("inc"); silenced = t.getBoolean("sil"); placard = t.getString("pl"); why = t.getString("why"); wasPowered = t.getBoolean("wp"); }
}
