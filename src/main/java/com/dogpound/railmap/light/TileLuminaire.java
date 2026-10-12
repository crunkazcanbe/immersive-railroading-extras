package com.dogpound.railmap.light;

import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.depot.Mains;
import com.dogpound.railmap.server.StationData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A station / yard luminaire (night lighting): canopy LED batten, yard floodlight head, lit station name sign and the
 * maintained emergency light. It really lights the area (block light from the block itself; a floodlight also throws
 * light blocks on the ground it points at), draws its watts from the IR Extras grid (an AC mains cable beside it) or
 * runs off the 12 V DC control supply as an LED fitting, and goes dark in a power cut - except the emergency light,
 * which runs on its own battery for emergencyHours.
 * <p>
 * Switching: MODE AUTO / ON / OFF. On AUTO it obeys the lighting contactor panel or photocell controller on its control
 * wire (LightingBus; CIRCUIT 1-4 picks the contactor), or with none its own photocell (lux, with hysteresis). Lamp
 * failures come from the asset register (LampHealth): a failing driver flickers, a failed one is dark.
 */
public abstract class TileLuminaire extends DepotTile implements ILamp {
    public enum Kind {
        //       name                 full light, watts (FE/s), halo colour, battery
        CANOPY("Canopy light", 15, 4, 0xFFF1D8, false),
        FLOOD("Yard floodlight", 15, 14, 0xF2F6FF, false),
        SIGN("Station name sign", 11, 3, 0xFFFFFF, false),
        EMERGENCY("Emergency light", 12, 1, 0xF4FFF4, true);

        public final String label;
        public final int light, watts, rgb;
        public final boolean battery;

        Kind(String label, int light, int watts, int rgb, boolean battery) { this.label = label; this.light = light; this.watts = watts; this.rgb = rgb; this.battery = battery; }
    }

    public final Kind kind;
    private final Mains feed = new Mains();
    private int level, battSecs = -1, testSecs;
    private boolean on, darkLatch;
    private double health;
    private long lastLight = -10000;
    private long[] helpers = new long[0];
    private String name = "";
    private final java.util.Random rng = new java.util.Random();

    protected TileLuminaire(Kind kind) { this.kind = kind; }

    // ---- identity ------------------------------------------------------------------------------------------------------
    @Override public String wiredName() { return kind.label + " " + Math.floorMod(pos.getX() * 7 + pos.getZ() * 3 + pos.getY(), 1000); }
    @Override public String lampName() { return wiredName(); }
    @Override public boolean lampOn() { return level > 0; }
    @Override public boolean lampFailed() { return health >= 1; }
    @Override public int lampCircuit() { return Math.floorMod(ctl.get("s_circ"), 4) + 1; }
    public int level() { return level; }
    public String signName() { return name.isEmpty() ? "STATION" : name; }
    public boolean onBattery() { return on && !supply(); }
    boolean ownsHelper(long p) { if (level <= 0) return false; for (long h : helpers) if (h == p) return true; return false; }
    public int helperCount() { return helpers.length; }

    private int battMax() { return (int) Math.round(LightConfig.emergencyHours * 3600); }
    /** mains from the grid, or the 12 V control supply (the fitting runs as a 12 V LED) */
    private boolean supply() { return testSecs == 0 && (feed.ok() || powered()); }

    @Override public String gearState() {
        if (health >= 1) return "LAMP FAILED - repair it (asset register)";
        if (level == 0) return !supply() ? (kind.battery ? "DARK - no supply, battery flat" : "DARK - no supply") : "switched off";
        String s = onBattery() ? String.format(Locale.ROOT, "ON EMERGENCY BATTERY %d%%", 100 * battSecs / Math.max(1, battMax())) : "lit (" + feedName() + ")";
        return health > 0 ? s + " - FLICKERING" : s;
    }

    private String feedName() { return feed.ok() ? feed.what : "12 V LED"; }

    @Override public void gearInfo(Map<String, Object> m) {
        m.put("light", level); m.put("mains", feed.what); m.put("circuit", lampCircuit()); m.put("health", Math.round(health * 100) / 100.0);
        if (kind.battery) m.put("batteryPct", 100 * battSecs / Math.max(1, battMax()));
        if (kind == Kind.FLOOD) m.put("groundLights", helpers.length);
    }

    @Override public int wiredLamp() { return term.burnt || !term.powered() && !feed.ok() ? 3 : health > 0 ? 2 : 1; }

    // ---- controls ------------------------------------------------------------------------------------------------------
    @Override protected String press(String id, EntityPlayer who) {
        if (id.equals("btest") && kind.battery) { testSecs = 30; return "§eBattery duration test: supply cut for 30 s"; }
        return null;
    }

    /** the lens and battery LEDs show on battery / mains too (12 V-only gear would blank them) */
    @Override public int controlValue(String id) {
        if (id.equals("lens") || id.equals("batt") || id.equals("chg") || id.equals("obst") || kind.battery && id.equals("litl")) return ctl.get(id);
        return super.controlValue(id);
    }
    @Override protected boolean keepTextUnpowered() { return true; }

    // ---- the work ------------------------------------------------------------------------------------------------------
    @Override
    protected void work() {
        if (feed.tick(world, pos, term, wiredName(), on && !onBattery() ? kind.watts : 0)) controlsChanged();
        if (age % 20 != 0) return;
        long now = world.getTotalWorldTime();
        if (battSecs < 0) battSecs = battMax();
        if (testSecs > 0) testSecs--;
        if (age % 200 == 0) health = LampHealth.of(world, pos);
        if (kind == Kind.SIGN && (name.isEmpty() || age % 600 == 0)) name = nearestStation();

        double lx = Lux.at(world, pos.up());
        if (darkLatch ? lx > 70 : lx < 35) darkLatch = !darkLatch;          // its own photocell, with hysteresis
        int mode = Math.floorMod(ctl.get("s_mode"), 3);                      // AUTO / ON / OFF
        int cmd = LightingBus.command(world, pos);
        boolean demand = mode == 1 || mode == 0 && (cmd >= 0 ? cmd == 1 : darkLatch);
        boolean sup = supply();
        boolean wantOn;
        if (kind.battery) {
            boolean maintained = ctl.get("k_maint") == 0;                     // MAINTAINED (lit with the others) / NON-MAINTAINED
            if (sup) { wantOn = mode != 2 && maintained && demand; battSecs = Math.min(battMax(), battSecs + 4); }
            else { wantOn = mode != 2 && battSecs > 0; if (wantOn) battSecs--; }   // supply lost: the battery holds it lit
        } else wantOn = sup && demand;

        boolean steady = wantOn != on;
        on = wantOn;
        boolean vis = LampHealth.visible(health, on, rng);
        int full = !sup && kind.battery ? 9 : kind.light;
        int target = vis ? full : 0;
        if (health > 0 && LightConfig.flickerLightTicks == 0) target = on && !(health >= 1 && com.dogpound.railmap.asset.AssetConfig.defectConsequences) ? full : 0;   // lens-only flicker
        if (target != level && LampHealth.mayChange(now, lastLight, steady || health == 0)) {
            boolean wasOn = level > 0;
            level = target;
            lastLight = now;
            world.checkLight(pos);
            if (kind == Kind.FLOOD && (wasOn != (level > 0)) && (steady || health == 0)) floodGround(level > 0);
            controlsChanged();
        }
        if (kind == Kind.FLOOD && level > 0 && age % 6000 == 0) floodGround(true);   // re-check its ground light every 5 min
        put("lens", vis);
        put("pwr", powered()); put("mainsl", feed.ok()); put("litl", on); put("cell", darkLatch); put("ctrl", cmd >= 0);
        put("flt", health > 0);
        if (kind == Kind.FLOOD) put("obst", sup && darkLatch && (age / 20) % 3 != 0);   // the red obstruction light on the mast top, slow flash after dark
        if (kind.battery) {
            put("batt", battSecs > battMax() / 10); put("chg", sup && battSecs < battMax());
            text("pct", (100 * battSecs / Math.max(1, battMax())) + "%");
        }
        if (kind == Kind.SIGN) text("name", signName());
        text("circ", "C" + lampCircuit());
    }

    private String nearestStation() {
        try {
            String best = "";
            double bd = 128 * 128;
            for (Map.Entry<Long, String> e : StationData.get(world).names().entrySet()) {
                double d = BlockPos.fromLong(e.getKey()).distanceSq(pos);
                if (d < bd && e.getValue() != null && !e.getValue().isEmpty()) { bd = d; best = e.getValue(); }
            }
            return best.toUpperCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---- the floodlight's ground light -----------------------------------------------------------------------------
    private EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        for (net.minecraft.block.properties.IProperty<?> p : s.getPropertyKeys())
            if (p.getName().equals("facing") && s.getValue(p) instanceof EnumFacing f && f.getAxis() != EnumFacing.Axis.Y) return f;
        return EnumFacing.NORTH;
    }

    /** light (or clear) the ground in front of the floodlight: rows every 3 blocks out to floodReach, widening like the beam */
    void floodGround(boolean light) {
        if (!light || !LightConfig.floodHelpers) { LightHelpers.clear(world, helpers); helpers = new long[0]; markDirty(); return; }
        EnumFacing f = facing(), side = f.rotateY();
        List<Long> out = new ArrayList<>();
        int reach = LightConfig.floodReach;
        for (int d = 3; d <= reach; d += 3) {
            int half = 1 + d / 3;
            for (int l = -half; l <= half; l += 3) {
                BlockPos col = pos.offset(f, d).offset(side, l);
                BlockPos spot = ground(col);
                if (spot == null) continue;
                int lv = Math.max(9, 15 - d * 6 / reach);
                LightHelpers.queue(world, spot, lv);
                out.add(spot.toLong());
            }
        }
        java.util.Set<Long> keep = new java.util.HashSet<>(out);
        for (long h : helpers) if (!keep.contains(h)) LightHelpers.queue(world, BlockPos.fromLong(h), 0);   // only what moved is cleared
        helpers = out.stream().mapToLong(Long::longValue).toArray();
        markDirty();
    }

    /** the air block just above the ground under (x, z), searching down from the head; null if none / water */
    private BlockPos ground(BlockPos col) {
        if (!world.isBlockLoaded(col)) return null;
        for (int dy = 2; dy >= -40; dy--) {
            BlockPos p = col.up(dy);
            if (p.getY() < 1) return null;
            IBlockState s = world.getBlockState(p);
            if (s.getMaterial().isLiquid()) return null;
            if (s.getMaterial().blocksMovement() || s.getBlock() instanceof com.dogpound.railmap.track.TrackBlock) {
                BlockPos up = p.up();
                IBlockState u = world.getBlockState(up);
                return u.getBlock() == LightHelpers.helper || u.getMaterial() == net.minecraft.block.material.Material.AIR ? up : null;
            }
        }
        return null;
    }

    /** broken: take its ground light with it */
    public void removed() { if (helpers.length > 0) { LightHelpers.clear(world, helpers); helpers = new long[0]; } }

    // ---- client: halos -----------------------------------------------------------------------------------------------
    @Override public void onLoad() { super.onLoad(); if (world != null && world.isRemote) LampRegistry.add(this); }
    @Override public void invalidate() { super.invalidate(); LampRegistry.remove(this); }
    @Override public void onChunkUnload() { super.onChunkUnload(); LampRegistry.remove(this); }

    @Override public float haloLevel() { return ctl.get("lens") == 1 ? (level > 0 && level < kind.light ? 0.6f : 1f) : 0f; }

    @Override public double[][] haloPoints() {
        int c = kind.rgb;
        switch (kind) {
            case CANOPY: return new double[][]{{8, 9.6, 8, 9, c}};
            case FLOOD: return new double[][]{{3.5, 23.0, 0.5, 7, c}, {12.5, 23.0, 0.5, 7, c}};
            case SIGN: return new double[][]{{-4, 14.2, 5.6, 5, 0xFFF6E0}, {8, 14.2, 5.6, 5, 0xFFF6E0}, {20, 14.2, 5.6, 5, 0xFFF6E0},
                    {-4, 14.2, 10.4, 5, 0xFFF6E0}, {8, 14.2, 10.4, 5, 0xFFF6E0}, {20, 14.2, 10.4, 5, 0xFFF6E0}};
            default: return new double[][]{{4.6, 10.0, 3.0, 4, c}, {11.4, 10.0, 3.0, 4, c}, {8, 2.8, 14.2, 3, 0x30FF60}};
        }
    }

    // ---- save ------------------------------------------------------------------------------------------------------------
    @Override protected void saveExtra(NBTTagCompound t) {
        t.setInteger("lvl", level); t.setBoolean("on", on); t.setBoolean("dark", darkLatch); t.setInteger("batt", battSecs);
        t.setLong("lastL", lastLight); t.setString("sname", name); t.setFloat("health", (float) health);
        net.minecraft.nbt.NBTTagList l = new net.minecraft.nbt.NBTTagList();
        for (long h : helpers) l.appendTag(new net.minecraft.nbt.NBTTagLong(h));
        t.setTag("helpers", l);
        NBTTagCompound f = new NBTTagCompound();
        feed.write(f);
        t.setTag("feed", f);
    }

    @Override protected void loadExtra(NBTTagCompound t) {
        int old = level;
        level = t.getInteger("lvl"); on = t.getBoolean("on"); darkLatch = t.getBoolean("dark");
        battSecs = t.hasKey("batt") ? t.getInteger("batt") : -1; lastLight = t.getLong("lastL"); name = t.getString("sname");
        health = t.getFloat("health");
        net.minecraft.nbt.NBTTagList l = t.getTagList("helpers", 4);
        helpers = new long[l.tagCount()];
        for (int i = 0; i < helpers.length; i++) helpers[i] = ((net.minecraft.nbt.NBTTagLong) l.get(i)).getLong();
        feed.read(t.getCompoundTag("feed"));
        if (world != null && world.isRemote && old != level) world.checkLight(pos);
    }

    // ---- the four fittings -----------------------------------------------------------------------------------------
    public static class Canopy extends TileLuminaire { public Canopy() { super(Kind.CANOPY); } }
    public static class Flood extends TileLuminaire { public Flood() { super(Kind.FLOOD); } }
    public static class Sign extends TileLuminaire { public Sign() { super(Kind.SIGN); } }
    public static class Emergency extends TileLuminaire { public Emergency() { super(Kind.EMERGENCY); } }
}
