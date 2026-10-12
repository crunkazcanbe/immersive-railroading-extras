package com.dogpound.railmap.grid;

import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.settings.Setting;
import com.dogpound.railmap.settings.SettingsStore;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A grid machine. The real state (stored energy, breaker position, network) lives in {@link GridData} so the grid
 * runs with this chunk unloaded; the tile is the face of it: energy input from other mods, the settings console,
 * the hum, the lamps.
 */
public class TileGrid extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder, PanelHost, com.dogpound.railmap.wire.IWiredMachine, com.dogpound.railmap.controls.IControlled {
    private final SettingsStore cfg = new SettingsStore(this);
    private GridKind kind = GridKind.TRANSFORMER;
    private int lastState = -1;

    public TileGrid() {}
    public TileGrid(GridKind k) { kind = k; }

    public GridKind kind() { return kind; }

    // ---- control terminal: Instrument Wire landed on the machine (gauges, Control Buttons, the Data Link read it) ----
    private int ctlWire = -1;
    @Override public int ctlWire() { return ctlWire; }
    @Override public void setCtlWire(int c) {
        ctlWire = c; panelDirty = true; sync();
        if (world != null) world.markBlockRangeForRenderUpdate(pos.add(-1, -1, -1), pos.add(1, 1, 1));
    }
    @Override public String wiredName() { return kind.label; }
    /** front panel + the working controls modelled on its other faces (indicators the front already has are not repeated) */
    private static final java.util.Map<GridKind, Panel> WIRED = new java.util.EnumMap<>(GridKind.class);
    @Override public Panel wiredPanel() {
        Panel f = Panel.of(kind);
        if (getBlockType() == null) return f;
        return WIRED.computeIfAbsent(kind, k -> {
            com.dogpound.railmap.controls.Layout l = layout();
            if (l.isEmpty()) return f;
            Panel p = new Panel(f != null ? f.title : kind.label, 0, 0, 16, 16, 0, 0);
            java.util.Set<String> seen = new java.util.HashSet<>();
            if (f != null) for (Panel.C c : f.cs) { p.cs.add(c); seen.add(c.id); }
            for (Panel.C c : l.all.cs) if (seen.add(c.id)) p.cs.add(c);
            return p;
        });
    }
    @Override public String wiredPress(String id, String who) {
        if (!loto.isEmpty()) return "§cLOCKED OUT by " + loto + " - only they can remove the padlock";
        com.dogpound.railmap.RailMap.LOG.debug("[IR Extras] {} pressed '{}' over the wire ({})", kind, id, who);
        return press(id, null);
    }
    @Override public int wiredState(String id) { return pv(id); }
    @Override public int wiredLamp() {
        if (isBurnt()) return 3;
        if (kind == GridKind.MOTOR) return motorState == 3 ? 3 : motorState > 0 ? 1 : 2;
        if (kind.generator()) return running ? 1 : 2;
        Reading r = new Reading();
        r.read(world, pos, BlockInstrument.Inst.LAMP);
        return r.lamp == 0 ? 2 : r.lamp;
    }
    @Override public void wiredRead(Reading r, BlockInstrument.Inst k) { r.read(world, pos, k); }
    @Override public java.util.Map<String, Object> wiredInfo() { return remoteInfo(); }

    // ---- wind turbine with a real tower (requested feature) -----------------------------------------------
    public static final String[] ROTORS = {"250 kW · 30 m rotor", "1.5 MW · 77 m rotor", "3 MW · 100 m rotor"};
    private static final double[] ROTOR_W = {250_000, 1_500_000, 3_000_000};
    private static final int[] ROTOR_SECTIONS = {6, 10, 14};
    /** rotor radius in blocks (drawn) and in real metres (for the rpm) */
    public static final double[] ROTOR_R = {3, 5, 7}, ROTOR_RM = {15, 38.5, 50};
    public double windNow;

    public int rotorSize() { String r = cfg.text("rotor"); for (int i = 0; i < ROTORS.length; i++) if (ROTORS[i].equals(r)) return i; return 1; }

    /** Wind Tower Sections stacked on the turbine */
    public int towerSections() {
        if (world == null) return 0;
        int n = 0;
        while (n < 48 && world.getBlockState(pos.up(n + 1)).getBlock() instanceof BlockWindTower) n++;
        return n;
    }

    /** hub height above the ground, metres: the turbine is built ~1:7 (a 1.5 MW rotor drawn 10 blocks across is 77 m),
     *  so a tower section stands for 7 m; a turbine on a hill catches a little more */
    public double hubHeightM() { return (towerSections() + 2) * 7.0 + Math.max(0, pos.getY() - 64) * 1.5; }

    /** is the rotor turning (works the same on the client from synced state, for the drawing) */
    public boolean rotorTurning() {
        int s = towerSections();
        if (s == 0 || s < ROTOR_SECTIONS[rotorSize()] || windStopped || estop || estopFault || isBurnt()) return false;
        double v = Wind.at(world, hubHeightM());
        return v >= 3 && (v <= 25 || !cfg.bool("stormProtect"));
    }

    /** rotor rpm (tip-speed ratio 7) */
    public double rotorRpm() {
        double v = Math.min(14, Wind.at(world, hubHeightM()));
        return rotorTurning() ? 7 * v / ROTOR_RM[rotorSize()] * 60 / (2 * Math.PI) : 0;
    }

    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        if (kind == GridKind.WIND) return new net.minecraft.util.math.AxisAlignedBB(pos).grow(8, 0, 8).expand(0, 60, 0);
        return super.getRenderBoundingBox();
    }
    @Override public double getMaxRenderDistanceSquared() { return kind == GridKind.WIND ? 256 * 256 : super.getMaxRenderDistanceSquared(); }

    // ---- burn-outs (requested feature) ----------------------------------------
    /** client copy of the node's burnt flag, for the scorch marks and smoke */
    public boolean burntClient;
    private long lastTrip = -100000;

    public boolean isBurnt() {
        if (world == null) return false;
        if (world.isRemote) return burntClient;
        GridData.Node n = GridData.get(world).node(pos);
        return n != null && n.burnt;
    }

    /** a machine destroys itself: a crack, smoke, flames, and it goes dead (an open circuit) until repaired */
    public static void burnOut(net.minecraft.world.World w, BlockPos p, String why) {
        GridData d = GridData.get(w);
        GridData.Node n = d.node(p);
        if (n == null || n.burnt) return;
        n.burnt = true; n.burntWhy = why; d.touch();
        History.log(w, p, "BURN", "BURNT OUT: " + why);
        w.playSound(null, p, com.dogpound.railmap.ModSounds.ARC_FLASH, net.minecraft.util.SoundCategory.BLOCKS, 0.7f, 1.0f);
        w.playSound(null, p, net.minecraft.init.SoundEvents.BLOCK_FIRE_AMBIENT, net.minecraft.util.SoundCategory.BLOCKS, 1.0f, 0.8f);
        if (w instanceof net.minecraft.world.WorldServer ws) {
            ws.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE, p.getX() + 0.5, p.getY() + 0.9, p.getZ() + 0.5, 30, 0.35, 0.3, 0.35, 0.03);
            ws.spawnParticle(net.minecraft.util.EnumParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 25, 0.35, 0.3, 0.35, 0.02);
            ws.spawnParticle(net.minecraft.util.EnumParticleTypes.LAVA, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.0);
        }
        String label = n.kind.label;
        GridTicker.alarm(w, p.toLong(), String.format(java.util.Locale.ROOT, "§4🔥 %s at %d %d %d BURNT OUT: %s - it's dead until you fix it with a screwdriver", label, p.getX(), p.getY(), p.getZ(), why));
        if (w.getTileEntity(p) instanceof TileGrid t) { t.panelDirty = true; t.sync(); IBlockState st = w.getBlockState(p); w.notifyBlockUpdate(p, st, st, 3); }
    }

    /** a screwdriver on a burnt-out machine: new windings / boards in */
    public String repair() {
        GridData d = GridData.get(world);
        GridData.Node n = d.node(pos);
        if (n == null || !n.burnt) return null;
        n.burnt = false; n.burntWhy = ""; n.heat = 0; n.hot = false; d.touch();
        Insulation.renew(n);
        History.log(world, pos, "REPAIR", "rebuilt after burn-out: " + (kind == GridKind.MOTOR ? "rewound windings" : kind == GridKind.TRANSFORMER || kind == GridKind.TRACTION ? "new windings and fresh oil" : "new electronics"));
        if (kind == GridKind.MOTOR) motorState = 0;
        lastTrip = -100000;
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.air_tool"), net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 1.0f);
        panelDirty = true; sync(); IBlockState st = world.getBlockState(pos); world.notifyBlockUpdate(pos, st, st, 3);
        return "§aRepaired the " + kind.label + " - " + (kind == GridKind.MOTOR ? "rewound windings" : kind == GridKind.TRANSFORMER || kind == GridKind.TRACTION ? "new windings and fresh oil" : "new electronics") + ". Find out why it burnt before you switch it back on!";
    }

    /** a motor overload trip: the second one while it's still hot burns the windings */
    private void motorTripped(String why) {
        long now = world.getTotalWorldTime();
        if (now - lastTrip < 2400) burnOut(world, pos, "windings burnt - " + why + " twice in two minutes");
        lastTrip = now;
    }

    GridData.Node nodePublic() { return node(); }
    boolean hasPv(String k) { return ps.containsKey(k); }
    boolean isRunning() { return running; }
    boolean hasRelayPublic() { return hasRelay(); }
    boolean lr() { return pv("lr") == 1; }
    /** black-start panel: breakers it could not close this run */
    private final java.util.Set<Long> skip = new java.util.HashSet<>();
    java.util.Set<Long> skip() { return skip; }

    /** a breaker with an IED on it (the IED times its own overcurrent in real amps) */
    static boolean hasIed(GridData d, BlockPos bp) {
        for (EnumFacing f : EnumFacing.values()) { GridData.Node nb = d.node(bp.offset(f)); if (nb != null && nb.kind == GridKind.IED) return true; }
        return false;
    }

    private GridData.Node node() {
        GridData d = GridData.get(world);
        GridData.Node n = d.node(pos);
        if (n == null) { d.add(pos, kind); n = d.node(pos); }
        return n;
    }

    // ---- energy from other mods (intake / battery) -----------------------------------------------------------
    private final IEnergyStorage energy = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean sim) {
            if (world == null || world.isRemote || !canReceive()) return 0;
            GridData.Node n = node();
            long room = kind.capacity - n.energy;
            int in = (int) Math.max(0, Math.min(room, Math.min(max, cfg.num("inRate"))));
            if (!sim && in > 0) { n.energy += in; received += in; GridData.get(world).markDirty(); }
            return in;
        }
        @Override public int extractEnergy(int max, boolean sim) { return 0; }
        @Override public int getEnergyStored() { return world == null ? 0 : (int) Math.min(Integer.MAX_VALUE, node().energy); }
        @Override public int getMaxEnergyStored() { return kind.capacity; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return kind == GridKind.INTAKE || kind == GridKind.BATTERY || (kind == GridKind.FEEDER && cfg.bool("direct")); }
    };

    // ---- power plants: fuel/steam from any mod's pipes, power out to any mod's cables ------------------------
    private final net.minecraftforge.fluids.FluidTank tank = new net.minecraftforge.fluids.FluidTank(16_000) {
        @Override public boolean canFillFluidType(net.minecraftforge.fluids.FluidStack fs) { return fuelValue(fs) > 0; }
    };
    { tank.setCanDrain(false); }
    private boolean running;
    private int lastOut;                // FE/t made in the last update
    private String fault = "";

    /** FE per mB: what each fluid is worth in this machine (0 = it won't take it) */
    int fuelValue(net.minecraftforge.fluids.FluidStack fs) {
        if (fs == null || fs.getFluid() == null) return 0;
        String n = fs.getFluid().getName().toLowerCase(Locale.ROOT);
        if (kind == GridKind.TURBINE) return n.contains("steam") ? 3 : 0;
        if (kind != GridKind.DIESEL) return 0;
        if (n.contains("diesel") || n.contains("fuel")) return 450;          // diesel, biodiesel, IE/PneumaticCraft/Thermal fuels
        if (n.contains("gasoline") || n.contains("petrol") || n.contains("kerosene") || n.contains("lpg")) return 380;
        if (n.contains("ethanol") || n.contains("alcohol")) return 300;
        if (n.contains("oil") || n.contains("crude") || n.contains("tar")) return n.contains("seed") || n.contains("plant") ? 120 : 160;
        if (n.contains("creosote")) return 80;
        return 0;
    }

    private final IEnergyStorage genEnergy = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean sim) { return 0; }
        @Override public int extractEnergy(int max, boolean sim) {
            if (world == null || world.isRemote || !cfg.bool("export")) return 0;
            GridData.Node n = node();
            int out = (int) Math.max(0, Math.min(n.energy, Math.min(max, cfg.num("exportRate"))));
            if (!sim && out > 0) { n.energy -= out; GridData.get(world).markDirty(); }
            return out;
        }
        @Override public int getEnergyStored() { return world == null ? 0 : (int) Math.min(Integer.MAX_VALUE, node().energy); }
        @Override public int getMaxEnergyStored() { return kind.capacity; }
        @Override public boolean canExtract() { return cfg.bool("export"); }
        @Override public boolean canReceive() { return false; }
    };

    @Override
    public boolean hasCapability(Capability<?> cap, EnumFacing f) {
        if (cap == CapabilityEnergy.ENERGY && (kind == GridKind.INTAKE || kind == GridKind.BATTERY || kind == GridKind.FEEDER || kind.generator())) return true;
        if (cap == net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && kind.buttons()) return true;
        return super.hasCapability(cap, f);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> cap, EnumFacing f) {
        if (cap == CapabilityEnergy.ENERGY && (kind == GridKind.INTAKE || kind == GridKind.BATTERY || kind == GridKind.FEEDER)) return (T) energy;
        if (cap == CapabilityEnergy.ENERGY && kind.generator()) return (T) genEnergy;
        if (cap == net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && kind.buttons()) return (T) tank;
        return super.getCapability(cap, f);
    }

    /** the IR rail this feeder bonds to (closest within 4 blocks), cached; null if none */
    private BlockPos railCache;
    private long railAt = -1000;   // not Long.MIN_VALUE: now - MIN_VALUE overflows negative and the scan never ran
    public BlockPos nearestRail() {
        if (world == null) return null;
        long now = world.getTotalWorldTime();
        if (now - railAt < 40) return railCache;
        railAt = now;
        railCache = null;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.getAllInBoxMutable(pos.add(-4, -2, -4), pos.add(4, 2, 4))) {
            if (!world.isBlockLoaded(p)) continue;
            net.minecraft.util.ResourceLocation id = world.getBlockState(p).getBlock().getRegistryName();
            // IR track pieces (block_rail / block_rail_gag) on both sides; UMC's typed lookup misses them on the client
            if (id == null || !"immersiverailroading".equals(id.getNamespace()) || !id.getPath().startsWith("block_rail")) continue;
            double d = p.distanceSq(pos);
            if (d < best) { best = d; railCache = p.toImmutable(); }
        }
        return railCache;
    }

    public boolean jumper() { return cfg.bool("jumper"); }
    public int cableColour() {
        switch (cfg.text("cableColour")) {
            case "Red": return 0xA02020;
            case "Copper": return 0xC06A2B;
            case "Yellow": return 0xE0C020;
            case "Pride pink": return 0xF5A9B8;
            default: return 0x151515;
        }
    }

    /** one update of a power plant (every 10 ticks): returns the block state to show (0 stopped, 1 running, 2 fault) */
    private int generate(GridData.Node n) {
        lastOut = 0;
        fault = "";
        if (estop || estopFault) { running = false; fault = estop ? "EMERGENCY STOP" : "E-STOP - press RESET"; return 2; }
        if (kind.buttons() && pv("mode") == 0 && ps.containsKey("mode")) running = false;     // mode OFF
        boolean rs = world.isBlockPowered(pos);
        String m = cfg.text("redstone");
        if (m.equals("Power runs it")) running = rs;
        else if (m.equals("Power stops it") && rs) running = false;
        if (kind.buttons() && (pv("batiso") == 1 || pv("trip") == 1)) { running = false; fault = pv("trip") == 1 ? "tripped by hand" : "battery isolator OFF"; return 0; }
        if (kind == GridKind.SOLAR && ps.containsKey("dc") && pv("dc") == 0) { fault = "DC isolator OFF"; return 0; }
        if (kind == GridKind.WIND && windStopped) { fault = "stopped (brake on)"; return 0; }
        if (cfg.bool("stopWhenFull") && n.energy >= kind.capacity) { fault = "full (waiting for load)"; return running ? 1 : 0; }
        if (!running && kind.buttons()) return 0;
        int throttle = Math.max(10, Math.min(100, cfg.num("throttle")));
        long made = 0;
        switch (kind) {
            case DIESEL: case TURBINE: {
                int want = (kind == GridKind.DIESEL ? 4 : 120) * throttle / 100 * 10;        // mB per 10 ticks
                net.minecraftforge.fluids.FluidStack in = tank.getFluid();
                int value = fuelValue(in);
                if (in == null || value == 0 || in.amount <= 0) { fault = kind == GridKind.DIESEL ? "out of fuel" : "no steam"; return 2; }
                int burn = Math.min(want, in.amount);
                tank.drainInternal(burn, true);
                made = (long) burn * value;
                if (speed < 0.9f) { lastOut = 0; fault = String.format(Locale.ROOT, "running up - %.1f Hz", speed * 60); return 1; }
                break;
            }
            case SOLAR: {
                if (!running && cfg.bool("parkable")) return 0;
                if (!world.canSeeSky(pos.up())) { fault = "no open sky above"; return 2; }
                double sunDeg = sunAngle(world, 1f);                                  // 0 = overhead, -90 = rising in the east, +90 = setting in the west
                if (Math.abs(sunDeg) >= 90) { fault = "night"; return 2; }
                double tilt = solarTilt(world, 1f);
                double incidence = Math.cos(Math.toRadians(sunDeg - tilt));           // sunlight striking the deck square-on = 1
                double k = world.isThundering() ? 0.2 : world.isRaining() ? 0.4 : 1.0;
                double diffuse = 0.08 * Math.cos(Math.toRadians(sunDeg));             // sky glow even off-angle
                double frac = (Math.max(0, incidence) * Math.cos(Math.toRadians(sunDeg)) * 0.15 + Math.max(0, incidence) * 0.85) * k + diffuse;
                if (pv("track") == 1 && pv("stow") == 0 && !world.isThundering()) frac *= 0.98;  // the tracker drive's own draw
                made = (long) (cfg.num("panels") * 12 * frac) * 10;
                break;
            }
            case WIND: {
                int secs = towerSections();
                if (secs > 0) {                                                  // a real tower turbine
                    int sz = rotorSize();
                    double v = Wind.at(world, hubHeightM());
                    windNow = v;
                    if (secs < ROTOR_SECTIONS[sz]) { fault = String.format(java.util.Locale.ROOT, "tower too short: a %s needs %d sections (has %d)", ROTORS[sz], ROTOR_SECTIONS[sz], secs); return 0; }
                    if (v > 25) {
                        if (cfg.bool("stormProtect")) { fault = String.format(java.util.Locale.ROOT, "feathered: storm cut-out at %.0f m/s", v); return 2; }
                        burnOut(world, pos, String.format(java.util.Locale.ROOT, "OVERSPEED in a %.0f m/s storm with storm protection off - blades and gearbox wrecked", v));
                        return 2;
                    }
                    double frac = Wind.curve(v);
                    if (frac <= 0) { fault = String.format(java.util.Locale.ROOT, "waiting for wind: %.1f m/s (cuts in at 3)", v); return 1; }
                    made = (long) (ROTOR_W[sz] * frac / Elec.W_PER_FE_T) * 10;
                    break;
                }
                if (!running && cfg.bool("parkable")) return 0;
                double height = Math.max(0, Math.min(1.5, (pos.getY() - 60) / 60.0));
                double weather = world.isThundering() ? 2.0 : world.isRaining() ? 1.5 : 1.0;
                double gust = 0.75 + 0.5 * world.rand.nextDouble();
                if (world.isThundering() && cfg.bool("stormStop")) { fault = "feathered: storm protection"; return 2; }
                made = (long) ((20 + 60 * height) * weather * gust) * 10;
                break;
            }
            default: return 0;
        }
        if (!n.gcb) { lastOut = (int) (made / 10); fault = "GCB open - not feeding"; return 1; }   // running, breaker open
        long room = kind.capacity - n.energy;
        made = Math.max(0, Math.min(made, room));
        n.energy += made;
        lastOut = (int) (made / 10);
        if (made > 0) GridData.get(world).markDirty();
        return 1;
    }

    // ---- the real controls (Panel) -----------------------------------------------------------------------------
    // ps = control positions + readings the client draws (lamps, needles, knobs); pt = display texts. Synced.
    private final java.util.Map<String, Integer> ps = new java.util.HashMap<>();
    private final java.util.Map<String, String> pt = new java.util.HashMap<>();
    private boolean panelDirty;
    private String pressId = "";
    private long pressAt = -100;
    private int crank;              // generator: ticks left cranking before it fires
    private boolean estop;          // emergency stop latched in (generators, battery, wind)
    private boolean estopFault;     // still needs RESET after the mushroom is released
    private boolean windStopped;    // wind turbine: STOP pressed (brake on)
    private int spring = 100;       // breaker closing spring charge, 0..100
    private boolean locked;         // disconnector padlock (lock-out tag-out)
    private int lampTest;           // SCADA: ticks of lamp test left
    private int ackHash;            // SCADA: alarms acknowledged
    private long peak;              // meter: peak demand FE/t
    private double peakW;           // smart meter: peak demand, W
    // generator shaft + synchronising (requested feature)
    private float speed, speedSet = 1f, phase;
    private int ufCount, olCount;
    // motor + starter
    private int motorState;         // 0 stopped, 1 starting, 2 running, 3 overload-tripped
    private int startLeft, startTotal, stallCount, lowV;
    private long delivered;         // FE given to the driven machine this second
    /** lockout / tagout: the name on the padlock hung on this device ("" = none) */
    private String loto = "";
    private int page;               // meter: display page
    private long received;          // intake: FE taken in since the last panel update
    private String rectFault = "";
    private boolean txAlarm;        // transformer alarm latched until RESET

    static final String REAL = "Real (back = primary)", LEGACY = "Legacy pass-through";

    /** copy the console's electrical settings into the grid node (and the machine's facing: its back is the primary) */
    private void electrics(GridData d, GridData.Node n) {
        boolean changed = false;
        IBlockState st = world.getBlockState(pos);
        if (st.getBlock() instanceof BlockGrid && st.getPropertyKeys().contains(BlockGrid.FACING)) {
            byte f = (byte) st.getValue(BlockGrid.FACING).getHorizontalIndex();
            if (n.facing != f) { n.facing = f; changed = true; }
        }
        boolean converter = kind.converter();
        if (converter) {
            if (!cfg.has("sides")) cfg.put("sides", n.realSides ? REAL : LEGACY);
            boolean real = REAL.equals(cfg.text("sides"));
            if (n.realSides != real) { n.realSides = real; changed = true; }
            String pri = Elec.Level.byName(cfg.text("primaryLv"), defaultPrimary(kind)).name();
            if (kind == GridKind.UPS) cfg.put("sides", REAL);
            String sec = kind == GridKind.UPS ? Elec.Level.DC12.name() : kind == GridKind.TRACTION ? Elec.Level.AC25000.name() : kind == GridKind.RECTIFIER ? Elec.Level.dc(n.dcv).name()
                    : Elec.Level.byName(cfg.text("secondaryLv"), kind == GridKind.DCDC ? Elec.Level.DC48 : Elec.Level.AC480).name();
            if (!pri.equals(n.primary) || !sec.equals(n.level)) { n.primary = pri; n.level = sec; changed = true; }
            n.kva = kind == GridKind.UPS ? 3 : Math.max(10, cfg.num("kva"));
        }
        if (kind.buttons()) n.kva = Math.max(10, cfg.num("kva"));
        if (kind == GridKind.INTAKE || kind.generator()) {
            String l = Elec.Level.byName(cfg.text("outLv"), kind == GridKind.INTAKE ? Elec.Level.MV13800 : Elec.Level.AC480).name();
            if (!l.equals(n.level)) { n.level = l; changed = true; }
        }
        if (changed) d.touch();
    }

    static Elec.Level defaultPrimary(GridKind k) {
        switch (k) {
            case TRACTION: return Elec.Level.HV69000;
            case RECTIFIER: return Elec.Level.MV4160;
            case INVERTER: return Elec.Level.DC750;
            case DCDC: return Elec.Level.DC750;
            case UPS: return Elec.Level.AC240;
            default: return Elec.Level.MV13800;
        }
    }

    private static String[] labels(Elec.Level... ls) { String[] r = new String[ls.length]; for (int i = 0; i < ls.length; i++) r[i] = ls[i].label; return r; }

    public float soundLevel() {
        if (!kind.converter() && !kind.buttons()) return 0.7f;
        return cfg.bool("sound") ? cfg.num("volume") / 100f : 0f;
    }

    public int pv(String k) { Integer v = ps.get(k); return v == null ? 0 : v; }
    public String ptext(String k) { String v = pt.get(k); return v == null ? "" : v; }
    public boolean pressedRecently(String id, long now) { return id.equals(pressId) && now - pressAt < 6; }
    /** client: where each lever is drawn right now, easing toward its position */
    public final java.util.Map<String, float[]> anim = new java.util.HashMap<>();
    @Override public java.util.Map<String, float[]> anim() { return anim; }

    // ---- the working controls modelled on the machine (requested feature). Layout = assets/irextras/controls/<block>.json from
    // tools/gen_bb_grid*.py. Their values live in ps like the front panel's, so the console, Control Buttons and the
    // model all see one state; a modelled handle runs the same press() as its front-panel twin.
    private final com.dogpound.railmap.controls.ControlState ctl = new com.dogpound.railmap.controls.ControlState();
    @Override public com.dogpound.railmap.controls.ControlState controls() { return ctl; }
    @Override public void controlsChanged() { panelDirty = true; sync(); }
    @Override public int controlValue(String id) { return pv(id); }
    @Override public String controlPress(String id, EntityPlayer who) { return press(id, who); }

    /** instrument / control supply healthy: lamps, displays and electric controls need it, mechanical handles don't */
    private boolean aux(GridData.Node n) {
        if (n.burnt) return false;                                              // the electronics are wrecked
        if (!com.dogpound.railmap.cabinet.Cabinet.ok(this, "ctl")) return false;  // its cabinet's 12 V aux MCB off / fuse out
        if (kind == GridKind.DIESEL) return pv("batiso") == 0;                  // the genset runs off its own 12 V battery
        if (kind == GridKind.AUTO) return pv("mcb") == 0;                       // PLC supply MCB
        if (kind == GridKind.IED) return n.st("aux") == 1;                       // station battery / UPS
        if (kind == GridKind.PQA || kind == GridKind.ATS || kind == GridKind.RESTORE) return pv("lpwr") == 1;
        return true;
    }

    /** what the modelled lamps, gauges, flags and displays show: all real readings */
    private void modelTick() {
        if (layout().isEmpty()) return;
        GridData d = GridData.get(world);
        GridData.Node n = node();
        GridData.Net net = d.netOf(pos);
        boolean live = net != null && net.problem.isEmpty();
        boolean aux = aux(n);
        switch (kind) {
            case TRANSFORMER: case TRACTION: case METER:
                put("oillvl", (int) Math.max(5, Math.min(100, 45 + n.heat * 0.5)));   // the conservator float: oil swells as it heats
                if (kind == GridKind.METER) put("modem", aux && live);
                break;
            case INTAKE:
                for (int i = 1; i <= 3; i++) put("vpis" + i, n.energy > 0);          // VPIS neons run off the incoming HV itself
                put("surges", n.surges);
                break;
            case FEEDER: put("surges", n.surges); put("live", live); break;
            case RECTIFIER: put("brun", aux && n.dcOn && live); put("bflt", aux && !rectFault.isEmpty()); break;
            case INVERTER: case DCDC: put("brun", aux && pv("lrun") == 1); put("bflt", aux && pv("lfault") == 1); break;
            case BREAKER: {
                put("mclosed", aux && n.breaker == 0); put("mopen", aux && n.breaker != 0); put("mtrip", aux && n.breaker == 2);
                put("target", n.breaker == 2);                                       // mechanical: drops on a trip
                put("springf", spring >= 100);
                put("ops", ops % 100000);
                put("sf6", n.burnt ? 18 : 82);                                       // a blow-out vents the gas
                text("rly", !aux ? "" : n.breaker == 2 ? "TRIP I>" : n.breaker == 0 ? "I " + pv("amps") + "%" : "OPEN");
                for (int i = 1; i <= 3; i++) put("vpis" + i, n.breaker == 0 && live);   // cable side live
                break;
            }
            case BATTERY: {
                int soc = (int) (n.energy * 100 / Math.max(1, kind.capacity));
                put("brun", aux && n.batOn); put("bflt", aux && (estop || estopFault)); put("bfire", aux && pv("released") == 1);
                text("bms", !aux ? "" : String.format(Locale.ROOT, "SOC %d%% %s", soc, !n.batOn ? "OFF" : pv("chg") == 1 ? "CHG" : pv("dis") == 1 ? "DIS" : "IDLE"));
                break;
            }
            case CAPACITOR: for (int i = 1; i <= 4; i++) put("ms" + i, aux && n.steps >= i); break;
            case DIESEL: case TURBINE:
                put("brun", aux && running); put("bflt", aux && pv("lfault") == 1);
                if (kind == GridKind.TURBINE) put("lube", running ? 72 : 86);       // oil drawn up into the bearings when it runs
                break;
            case SOLAR: put("ltcu", aux && pv("ltrack") == 1); put("lmon", aux && pv("dc") == 1 && lastOut > 0); break;
            case WIND: put("brun", aux && pv("run") == 1); put("bflt", aux && pv("fault") == 1); break;
            case SCADA: {
                for (int i = 1; i <= 3; i++) put("pwr" + i, aux);
                put("lprn", aux);
                long t = world.getWorldTime() % 24000;
                text("clock", aux ? String.format(Locale.ROOT, "%02d:%02d", (t / 1000 + 6) % 24, t % 1000 * 60 / 1000) : "");
                break;
            }
            case AUTO: {
                put("llight", aux); put("lcpu", aux); put("lnet", aux);
                String[] io = {"master", "ar", "shed", "gen", "lar", "lshed", "lgen"};
                for (int i = 0; i < io.length; i++) put("io" + (i + 1), aux && pv(io[i]) == 1);
                put("sgreen", aux && pv("master") == 1 && pv("healthy") == 1);
                put("samber", aux && (pv("lar") == 1 || pv("lshed") == 1));
                put("sred", aux && pv("lalarm") == 1);
                break;
            }
            case EARTHING: put("vpis1", live && !n.earthOn); put("vpis2", live && !n.earthOn); break;   // neutral voltage until earthed
            case RELAY: put("ldc", aux); break;
            default: break;
        }
        if (kind.control() || kind == GridKind.UPS) GridGear.display(this, d, n);
    }

    void put(String k, int v) { Integer o = ps.put(k, v); if (o == null || o != v) panelDirty = true; }
    /** cabinet.Cabinet: its door / MCB / fuse / relay / PLC positions live here with the other controls */
    public void cabPut(String k, int v) { put(k, v); }
    public void cabText(String k, String v) { text(k, v); }
    void put(String k, boolean v) { put(k, v ? 1 : 0); }
    void text(String k, String v) { if (!v.equals(pt.put(k, v))) panelDirty = true; }

    private void sync() {
        if (!panelDirty || world == null || world.isRemote) return;
        panelDirty = false;
        markDirty();
        IBlockState st = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, st, st, 3);
    }

    /** the effective trip setting of the breaker at bp: its rating scaled by the Protection Relay touching it */
    static int relayRating(GridData d, BlockPos bp, int rating) {
        for (EnumFacing f : EnumFacing.values()) {
            GridData.Node nb = d.node(bp.offset(f));
            if (nb != null && nb.kind == GridKind.RELAY) return Math.max(1, rating * nb.relaySet / 100);
        }
        return rating;
    }

    void rectifierTripped(String why) { rectFault = why; put("fault", true); sync(); }

    private void click(float pitch) { world.playSound(null, pos, ModSounds.RELAY, SoundCategory.BLOCKS, 0.7f, pitch); }
    private void clunk(float pitch) { world.playSound(null, pos, ModSounds.BREAKER, SoundCategory.BLOCKS, 0.9f, pitch); }

    /** a control was operated (BlockGrid works out which one from the player's line of sight) */
    String press(String id, EntityPlayer p) {
        if (isBurnt() && !id.equals("door")) { GridData.Node bn = GridData.get(world).node(pos); return "§4🔥 BURNT OUT" + (bn != null ? " (" + bn.burntWhy + ")" : "") + " - fix it with a screwdriver"; }
        if (!loto.isEmpty() && !id.equals("display") && !id.equals("ack") && !id.equals("lamptest") && !id.equals("door"))
            return "§c🔒 LOCKED OUT by " + loto + " - DANGER, DO NOT OPERATE. Only they can take their padlock off (sneak + right-click empty-handed)";
        pressId = id;
        pressAt = world.getTotalWorldTime();
        panelDirty = true;
        Panel pnl = Panel.of(kind);
        if (pnl != null) for (Panel.C c : pnl.cs) if (c.id.equals(id)) {
            net.minecraft.util.SoundEvent cs = ModSounds.forControl(c.t);
            if (cs != null) world.playSound(null, pos, cs, SoundCategory.BLOCKS, 0.6f, 0.95f + world.rand.nextFloat() * 0.1f);
            break;
        }
        String r = operate(id, p);
        panelTick();
        sync();
        return r;
    }

    private String operate(String id, EntityPlayer p) {
        String who = p == null ? "remote control" : p.getName();
        if (id.equals("door")) {                                                // the cabinet door
            put("door", 1 - pv("door"));
            world.playSound(null, pos, pv("door") == 1 ? com.dogpound.railmap.sound.PackSounds.ev("machine.door_open") : com.dogpound.railmap.sound.PackSounds.ev("machine.door_close"), SoundCategory.BLOCKS, 0.7f, 1.1f);
            return pv("door") == 1 ? "§7Cabinet open - right-click a blank spot inside to shut it" : "§7Cabinet shut";
        }
        GridData d = GridData.get(world);
        GridData.Node n = node();
        GridData.Net net = d.netOf(pos);
        if (kind.control() || kind == GridKind.UPS) { String r = GridGear.press(this, id, p); if (r != null) return r; }
        switch (kind) {
            case TRANSFORMER: case TRACTION:
                switch (id) {
                    case "raise": case "lower": {
                        if (n.avr) return "§eAVR is in AUTO - switch it to MAN to move the tap by hand";
                        int t = Math.max(1, Math.min(17, n.tap + (id.equals("raise") ? 1 : -1)));
                        if (t == n.tap) return "§7Tap changer at its end stop (" + t + ")";
                        n.tap = t; d.markDirty(); world.playSound(null, pos, ModSounds.TAP_CHANGE, SoundCategory.BLOCKS, 0.9f, id.equals("raise") ? 1.05f : 0.95f);
                        return "§e" + (id.equals("raise") ? "▲" : "▼") + " Tap " + t + " · " + String.format(Locale.ROOT, "%+.1f%%", (t - 9) * 1.25);
                    }
                    case "avr": n.avr = !n.avr; d.markDirty(); click(1.2f); return n.avr ? "§aAVR AUTO - the tap changer holds the voltage by itself" : "§eAVR MANUAL - use RAISE / LOWER";
                    case "cool": n.cool = (n.cool + 1) % 3; d.markDirty(); click(1f); return "§7Cooling fans: " + new String[]{"§cOFF", "§aAUTO", "§bON"}[n.cool];
                    case "reset":
                        if (n.hot) return "§cStill too hot to reset (" + Math.round(n.heat) + " °C rise) - let it cool";
                        txAlarm = false; click(0.9f); return "§aAlarm reset";
                }
                break;
            case RECTIFIER:
                switch (id) {
                    case "vsel": {
                        int[] vs = {600, 750, 1500, 3000};
                        int i = 0; while (i < 3 && vs[i] != n.dcv) i++;
                        n.dcv = vs[(i + 1) % 4]; d.touch(); click(1.1f);
                        return "§eDC system voltage " + n.dcv + " V §7(third rail 750 V · monorail / maglev beam 1500 V)";
                    }
                    case "dcclose":
                        if (!rectFault.isEmpty()) return "§cFault: " + rectFault + " - press RESET first";
                        if (n.dcOn) return "§7DC breaker already closed";
                        n.dcOn = true; d.touch(); clunk(1.2f); return "§aDC breaker CLOSED - DC on the line";
                    case "dcopen":
                        if (!n.dcOn) return "§7DC breaker already open";
                        n.dcOn = false; d.touch(); clunk(0.9f); return "§cDC breaker OPEN - line dead";
                    case "reset": rectFault = ""; click(0.9f); return "§aFault reset";
                }
                break;
            case MOTOR:
                switch (id) {
                    case "start":
                        if (pv("hoa") != 0) return "§eSelector is in " + (pv("hoa") == 1 ? "OFF" : "AUTO (redstone runs it)") + " - turn it to HAND";
                        if (motorState == 3) return "§cOverload relay tripped - press RESET first";
                        if (motorState != 0) return "§7Already " + (motorState == 1 ? "starting" : "running");
                        return beginStart(net);
                    case "stop":
                        if (motorState == 0) return "§7Already stopped";
                        motorState = 0; click(0.8f); world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.motor_stop"), SoundCategory.BLOCKS, 0.9f, 1f); return "§c■ Motor stopped - contactor open";
                    case "reset":
                        if (motorState != 3) return "§7Nothing to reset";
                        motorState = 0; click(0.9f); return "§aOverload relay reset";
                    case "hoa": {
                        int m = (pv("hoa") + 1) % 3; put("hoa", m); click(1.1f);
                        if (m == 1 && motorState != 3) motorState = 0;
                        return "§7Selector: " + new String[]{"§aHAND (START / STOP buttons)", "§cOFF", "§bAUTO (redstone runs it)"}[m];
                    }
                    case "estop":
                        estop = !estop;
                        if (estop) { estopFault = true; if (motorState != 3) motorState = 0; clunk(0.6f); return "§c§lEMERGENCY STOP"; }
                        estopFault = false; click(1f); return "§7E-STOP released";
                }
                break;
            case INVERTER: case DCDC:
                switch (id) {
                    case "run":
                        if (n.dcOn) { n.dcOn = false; d.touch(); clunk(0.9f); return "§c" + kind.label + " STOPPED - output isolated"; }
                        if (estop || estopFault) return "§cE-STOP - twist it out and RESET first";
                        if (!rectFault.isEmpty()) return "§cFault: " + rectFault + " - press RESET first";
                        n.dcOn = true; d.touch(); clunk(1.2f); return "§a" + kind.label + " RUNNING - soft-start, output ramping up";
                    case "reset":
                        if (estop) return "§cE-STOP still pressed in";
                        rectFault = ""; estopFault = false; click(0.9f); return "§aFault reset";
                    case "estop":
                        estop = !estop;
                        if (estop) { estopFault = true; n.dcOn = false; d.touch(); clunk(0.6f); return "§c§lEMERGENCY STOP"; }
                        click(1f); return "§7E-STOP released - RESET, then RUN";
                }
                break;
            case BREAKER: {
                boolean local = pv("lr") == 0;
                switch (id) {
                    case "lr": put("lr", local ? 1 : 0); click(1.3f);
                        return local ? "§eREMOTE - redstone / remote control only, the panel is locked" : "§aLOCAL - operate from this panel";
                    case "reset":
                        if (n.breaker != 2) return "§7Nothing to reset";
                        setBreaker((byte) 1, "lockout reset by " + who); return "§aLockout reset - breaker OPEN, ready to close";
                    case "open":
                        if (!local) return "§eIn REMOTE - switch the key to LOCAL";
                        if (n.breaker != 0) return "§7Already open";
                        setBreaker((byte) 1, "opened by " + who); return "§cBreaker OPEN - downstream is dead (safe to work)";
                    case "close": case "handle": {
                        if (!local) return "§eIn REMOTE - switch the key to LOCAL";
                        if (id.equals("handle") && n.breaker == 0) { setBreaker((byte) 1, "opened by hand by " + who); return "§cBreaker OPEN - downstream is dead (safe to work)"; }
                        if (n.breaker == 2) return "§6TRIPPED - lockout: press RESET before closing";
                        if (n.breaker == 0) return "§7Already closed";
                        if (spring < 100) return "§eClosing spring still charging (" + spring + "%) - wait";
                        if (!hasRelay()) return "§cWon't close: no Protection Relay touching this breaker (it can't detect faults)";
                        if (!earthedBothSides(d)) return "§cWon't close: this network isn't earthed - fit an Earthing Switch";
                        { String il = GridGear.interlock(world, pos); if (!il.isEmpty()) return "§c" + il; }
                        { String pw = Permits.closeBlocked(world, pos); if (!pw.isEmpty()) return "§c" + pw; }
                        setBreaker((byte) 0, "closed by " + who);
                        spring = 0;
                        return "§aBreaker CLOSED - downstream is live";
                    }
                }
                break;
            }
            case ISOLATOR:
                if (id.equals("lock")) {
                    if (n.breaker == 0) return "§cOnly an OPEN disconnector can be padlocked";
                    locked = !locked; click(0.8f);
                    return locked ? "§e🔒 Padlocked OPEN - nobody can close it (lock-out / tag-out)" : "§7🔓 Padlock removed";
                }
                if (id.equals("handle")) {
                    if (locked) return "§e🔒 Padlocked - remove the padlock first";
                    return click(p);
                }
                break;
            case EARTHING:
                if (id.equals("handle")) {
                    if (n.earthOn) { String pw = Permits.earthBlocked(world, pos); if (!pw.isEmpty()) return "§c" + pw; }
                    n.earthOn = !n.earthOn; d.touch(); clunk(n.earthOn ? 1.1f : 0.8f);
                    History.log(world, pos, n.earthOn ? "CLOSE" : "OPEN", (n.earthOn ? "earth applied by " : "earth removed by ") + who);
                    return n.earthOn ? "§aNeutral EARTHED - protection can see faults" : "§c⚠ Earth REMOVED - breakers will refuse to close on this network";
                }
                break;
            case INTAKE:
                if (id.equals("main")) {
                    n.mainOn = !n.mainOn; d.touch(); clunk(n.mainOn ? 1.2f : 0.85f);
                    return n.mainOn ? "§aIncomer ON - supply into the railway grid" : "§cIncomer OFF - grid isolated from the supply";
                }
                break;
            case METER:
                if (id.equals("display")) { page = (page + 1) % METER_PAGES.length; click(1.5f); return "§7Display: " + METER_PAGES[page]; }
                if (id.equals("demand")) { peak = 0; click(1.2f); return "§eMax demand reset"; }
                break;
            case BATTERY:
                switch (id) {
                    case "start":
                        if (estop || estopFault) return "§cE-STOP - release it and RESET... (press E-STOP again to twist it out)";
                        n.batOn = true; d.touch(); clunk(1.2f); return "§aInverter STARTED";
                    case "stop": n.batOn = false; d.touch(); click(0.8f); return "§cInverter STOPPED - battery isolated";
                    case "mode": n.batMode = (n.batMode + 1) % 3; d.markDirty(); click(1.1f); return "§7Mode: " + new String[]{"§bCHARGE only", "§aAUTO", "§6DISCHARGE only"}[n.batMode];
                    case "estop":
                        estop = !estop;
                        if (estop) { estopFault = true; n.batOn = false; d.touch(); clunk(0.6f); return "§c§lEMERGENCY STOP"; }
                        estopFault = false; put("released", 0); click(1f); return "§7E-STOP released - press START";
                    case "release":
                        if (pv("released") == 1) return "§7Already released - twist the E-STOP out on the door panel to reset";
                        put("released", 1); estop = true; estopFault = true; n.batOn = false; d.touch(); clunk(0.5f);
                        world.playSound(null, pos, ModSounds.ALARM, SoundCategory.BLOCKS, 1f, 1f);
                        return "§c§l🔥 FIRE SUPPRESSION RELEASED - BESS tripped. Twist the E-STOP out on the door panel to reset";
                }
                break;
            case AUTO:
                switch (id) {
                    case "master": case "ar": case "shed": case "gen": {
                        put(id, 1 - pv(id)); click(1.2f);
                        return "§7" + (id.equals("master") ? "AUTO control" : id.equals("ar") ? "Auto-reclose" : id.equals("shed") ? "Load shedding" : "Standby generator start")
                                + (pv(id) == 1 ? " §aON" : " §cOFF");
                    }
                    case "ack": autoAck = autoHash; click(1.4f); return "§eAlarms acknowledged";
                    case "reset": arTrips.clear(); autoLog("lockouts reset"); click(0.9f); return "§aAuto-reclose lockouts reset";
                    case "lamptest": lampTest = 60; click(1.6f); return "§7Lamp test";
                    case "mcb":
                        put("mcb", 1 - pv("mcb")); click(0.7f);
                        if (pv("mcb") == 1) { autoAlarm = false; return "§cPLC supply MCB OFF - the Auto Controller is dead (no reclose, shedding or generator start)"; }
                        return "§aPLC supply MCB ON - controller booting";
                }
                break;
            case SCADA:
                if (id.equals("ack")) { ackHash = problemHash(d); click(1.4f); return "§eAlarms acknowledged"; }
                if (id.equals("lamptest")) { lampTest = 60; click(1.6f); return "§7Lamp test"; }
                break;
            case DIESEL: case TURBINE:
                switch (id) {
                    case "mode": {
                        int m = (pv("mode") + 1) % 3; put("mode", m); click(1.1f);
                        if (m == 0 && running) { running = false; }
                        return "§7Mode: " + new String[]{"§cOFF (won't start)", "§eMANUAL", "§aAUTO (starts itself when the grid runs low)"}[m];
                    }
                    case "batiso":
                        put("batiso", 1 - pv("batiso")); clunk(1f);
                        if (pv("batiso") == 1) { running = false; crank = 0; n.gcb = false; d.touch(); return "§cBattery isolator OFF - controller and fuel solenoid dead, the set stops"; }
                        return "§aBattery isolator ON - controller powered";
                    case "trip":
                        if (pv("trip") == 0) {
                            put("trip", 1); running = false; crank = 0; n.gcb = false; d.touch(); clunk(0.5f);
                            return "§c§lTURBINE TRIPPED by hand - trip + throttle valve slammed shut";
                        }
                        put("trip", 0); click(1f); return "§aTrip lever re-latched - press START";
                    case "start":
                        if (pv("batiso") == 1) return "§cBattery isolator is OFF - no cranking power";
                        if (pv("trip") == 1) return "§cTrip lever is down - latch it on the side of the turbine first";
                        if (estop) return "§cE-STOP pushed in - press it again to twist it out, then RESET";
                        if (estopFault) return "§6Press RESET after an emergency stop";
                        if (pv("mode") != 1) return pv("mode") == 0 ? "§cMode is OFF" : "§eIn AUTO it starts itself - switch to MAN";
                        if (running || crank > 0) return "§7Already running";
                        crank = kind == GridKind.DIESEL ? 3 : 4; clunk(0.6f);
                        return "§a▶ Cranking...";
                    case "stop":
                        if (pv("mode") == 2) return "§eIn AUTO - switch to MAN or OFF to stop it";
                        if (!running && crank == 0) return "§7Already stopped";
                        running = false; crank = 0; click(0.6f); return "§c■ " + kind.label + " stopping";
                    case "up": case "down": {
                        if (!n.gcb) {                                         // not paralleled: the governor sets the SPEED (frequency)
                            speedSet = Math.max(0.95f, Math.min(1.05f, speedSet + (id.equals("up") ? 0.002f : -0.002f)));
                            click(id.equals("up") ? 1.4f : 1.1f);
                            return String.format(Locale.ROOT, "§e%s Speed setpoint %.2f Hz", id.equals("up") ? "▲" : "▼", speedSet * 60);
                        }
                        int t = Math.max(10, Math.min(100, cfg.num("throttle") + (id.equals("up") ? 10 : -10)));
                        cfg.set("throttle", Integer.toString(t)); click(id.equals("up") ? 1.4f : 1.1f);
                        return "§e" + (id.equals("up") ? "▲" : "▼") + " Governor " + t + "%";
                    }
                    case "gcbclose":
                        if (n.gcb) return "§7Generator breaker already closed";
                        if (!running) return "§cWon't close: the set isn't running (nothing to synchronise)";
                        if (speed < 0.95f) return String.format(Locale.ROOT, "§cWon't close: still running up (%.1f Hz)", speed * 60);
                        {
                            double bus = busSpeed(d, net, n);
                            if (bus >= 0 && (Math.abs(phase) > 20 || Math.abs(speed - bus) > 0.004))
                                return String.format(Locale.ROOT, "§c⛔ Sync-check relay BLOCKED: %+.0f° out of phase, slip %+.2f Hz. Use GOV ▲▼ until the synchroscope creeps slowly toward 12 o'clock, then close",
                                        phase, (speed - bus) * 60);
                            n.gcb = true; d.touch(); clunk(1.2f);
                            return bus < 0 ? "§aGCB CLOSED onto a dead bus - this set now makes the grid" : "§aGCB CLOSED in sync - paralleled with the grid";
                        }
                    case "gcbopen":
                        if (!n.gcb) return "§7Generator breaker already open";
                        n.gcb = false; d.touch(); clunk(0.9f); return "§cGCB OPEN - running, not feeding";
                    case "reset":
                        if (estop) return "§cRelease the E-STOP first (press it again)";
                        estopFault = false; fault = ""; click(0.9f); return "§aFaults reset";
                    case "estop":
                        estop = !estop;
                        if (estop) { estopFault = true; running = false; crank = 0; n.gcb = false; d.touch(); clunk(0.5f); return "§c§lEMERGENCY STOP"; }
                        click(1f); return "§7E-STOP released - press RESET";
                }
                break;
            case SOLAR:
                if (id.equals("track")) { put("track", 1 - pv("track")); click(1.1f); return pv("track") == 1 ? "§aTRACKING - the deck follows the sun across the sky" : "§7FIXED - deck parked flat"; }
                if (id.equals("stow")) { put("stow", 1 - pv("stow")); clunk(0.9f); return pv("stow") == 1 ? "§eSTOWED flat (storm position)" : "§aReleased from stow"; }
                if (id.equals("volt")) {
                    Elec.Level[] ls = {Elec.Level.DC48, Elec.Level.DC600, Elec.Level.DC1500, Elec.Level.AC240, Elec.Level.AC480};
                    Elec.Level cur = Elec.Level.byName(cfg.text("outLv"), Elec.Level.AC480);
                    int i = 0; while (i < ls.length && ls[i] != cur) i++;
                    Elec.Level nx = ls[(i + 1) % ls.length];
                    cfg.put("outLv", nx.label); markDirty(); click(1.3f);
                    return "§bOutput " + nx.label + (nx.ac ? " (string inverter on)" : " (straight off the strings - DC)");
                }
                if (id.equals("dc")) { put("dc", pv("dc") == 0 ? 1 : 0); clunk(1.1f); return pv("dc") == 0 ? "§7DC isolator OFF - panels disconnected" : "§aDC isolator ON"; }
                break;
            case WIND:
                switch (id) {
                    case "start": if (estop || estopFault) return "§cE-STOP - release and RESET first"; windStopped = false; clunk(1f); return "§aTurbine released - yawing into the wind";
                    case "stop": windStopped = true; click(0.7f); return "§cTurbine STOPPED - brake on, blades feathered";
                    case "reset": if (estop) return "§cRelease the E-STOP first"; estopFault = false; click(0.9f); return "§aReset";
                    case "estop": estop = !estop; if (estop) { estopFault = true; clunk(0.5f); return "§c§lEMERGENCY STOP - rotor brake"; } click(1f); return "§7E-STOP released - press RESET";
                }
                break;
            case CAPACITOR:
                switch (id) {
                    case "auto": n.capAuto = !n.capAuto; d.markDirty(); click(1.1f); return n.capAuto ? "§aPF controller AUTO" : "§eManual - use STEP+ / STEP-";
                    case "stepin": case "stepout": {
                        if (n.capAuto) return "§eIn AUTO - switch to MAN to step by hand";
                        int s2 = Math.max(0, Math.min(4, n.steps + (id.equals("stepin") ? 1 : -1)));
                        n.steps = s2; d.markDirty(); clunk(1.3f); return "§7Capacitor steps in: " + s2 + " / 4";
                    }
                }
                break;
            case RELAY:
                switch (id) {
                    case "pickupset": {
                        int[] vs = {50, 80, 100, 120};
                        int i = 0; while (i < 3 && vs[i] != n.relaySet) i++;
                        n.relaySet = vs[(i + 1) % 4]; d.markDirty(); click(1.1f);
                        return "§7Pick-up " + n.relaySet + "% of the breaker rating";
                    }
                    case "test": {
                        boolean any = false;
                        for (EnumFacing f : EnumFacing.values())
                            if (world.getTileEntity(pos.offset(f)) instanceof TileGrid b && b.kind == GridKind.BREAKER && GridData.get(world).node(b.pos).breaker == 0) {
                                b.setBreaker((byte) 2, "TRIPPED: relay test by " + who); b.sync(); any = true;
                            }
                        return any ? "§6Relay TEST - breaker tripped (secondary injection)" : "§7Relay TEST - no closed breaker to trip";
                    }
                    case "reset": click(0.9f); return "§aRelay flags reset";
                }
                break;
            default: break;
        }
        return click(p);
    }

    private int problemHash(GridData d) {
        int h = 0;
        for (GridData.Net nn : d.nets()) if (!nn.problem.isEmpty()) h = h * 31 + nn.id * 7 + nn.problem.hashCode();
        return h;
    }

    /** every panel update: run the controls' logic and set the lamps / needles / displays the client draws */
    private void panelTick() {
        if (Panel.of(kind) != null) frontTick();
        modelTick();
    }

    private void frontTick() {
        GridData d = GridData.get(world);
        GridData.Node n = node();
        GridData.Net net = d.netOf(pos);
        put("loto", !loto.isEmpty());
        text("lotoBy", loto);
        long load = net == null ? 0 : net.lastSecond / 20;
        boolean flash = false;
        switch (kind) {
            case TRANSFORMER: case TRACTION: {
                double v = net == null ? 0 : net.volt;
                if (n.hot || v > 1.08) txAlarm = true;
                put("tap", n.tap);
                double kv = net == null || !net.problem.isEmpty() ? 0 : net.level != null ? net.level.volts / 1000 * v : (kind == GridKind.TRACTION ? 25 : 11) * v;
                put("volts", (int) Math.round(kv * 10));
                put("avr", n.avr ? 1 : 0); put("cool", n.cool);
                put("temp", Math.round(n.heat));
                put("load", net == null ? 0 : net.level != null ? (int) Math.min(150, net.sVA * 100 / Math.max(1, n.kva * 1000.0))
                        : (int) Math.min(150, load * 100 / Math.max(1, (long) GridData.RATING * Math.max(1, net.transformers))));
                put("fanrun", n.cool == 2 || (n.cool == 1 && n.heat > 40));
                put("alarm", txAlarm); put("trip", n.hot);
                break;
            }
            case RECTIFIER: {
                int[] vs = {600, 750, 1500, 3000};
                int i = 0; while (i < 3 && vs[i] != n.dcv) i++;
                put("vsel", i);
                boolean on = n.dcOn && net != null && net.problem.isEmpty();
                put("dcv", on ? (int) Math.round(n.dcv * Math.max(0.5, Math.min(1.1, net.volt))) : 0);
                int pct = net == null ? 0 : (int) Math.min(150, load * 100 / Math.max(1, (long) GridData.RATING * Math.max(1, net.transformers)));
                put("dca", n.dcOn ? pct : 0); put("load", n.dcOn ? pct : 0);
                put("dcon", n.dcOn); put("dcoff", !n.dcOn); put("fault", !rectFault.isEmpty()); put("fans", on && pct > 30);
                break;
            }
            case MOTOR: {
                if (!ps.containsKey("hoa")) put("hoa", 0);
                double sRated = motorKw() * 1000 / (0.92 * 0.86);
                put("amps", (int) Math.min(999, Math.round(n.loadVA / Math.max(1, sRated) * 100)));
                put("lrun", motorState == 2); put("lstart", motorState == 1); put("ltrip", motorState == 3); put("estop", estop);
                boolean vfd = "VFD".equals(cfg.text("starter"));
                double ramp = motorState == 2 ? 1 : motorState == 1 && startTotal > 0 ? 1 - startLeft / (double) startTotal : 0;
                text("out", vfd ? String.format(Locale.ROOT, "%.1f Hz", 60 * ramp) : String.format(Locale.ROOT, "%.0f kW", delivered * Elec.W_PER_FE_T / 10 / 1000.0));
                delivered = 0;
                break;
            }
            case INVERTER: case DCDC: {
                boolean live = net != null && net.problem.isEmpty() && net.level != null;
                GridData.Net up = n.upNet >= 0 && n.upNet < d.nets().size() ? d.nets().get(n.upNet) : null;
                boolean inOk = up != null && up.level != null && up.problem.isEmpty() && !up.level.ac;
                put("run", n.dcOn ? 1 : 0);
                put("vin", inOk ? (int) Math.round(up.level.volts * (up.puSource > 0 ? up.puSource : 1)) : 0);
                put("vout", live && n.dcOn ? (int) Math.round(net.level.volts * net.puSource) : 0);
                put("kw", live && n.dcOn ? (int) Math.round(net.pW / 1000) : 0);
                put("load", live && n.dcOn ? (int) Math.min(150, net.sVA * 100 / Math.max(1, n.kva * 1000.0)) : 0);
                put("lrun", live && n.dcOn); put("ldc", inOk); put("lfault", !rectFault.isEmpty() || estopFault); put("estop", estop);
                put("fans", live && n.dcOn && net.sVA > n.kva * 300.0);
                break;
            }
            case BREAKER: {
                if (spring < 100) spring = Math.min(100, spring + 20);           // motor recharges the closing spring (~5 s)
                put("handle", n.breaker == 0 ? 0 : n.breaker == 2 ? 2 : 1);
                put("flag", n.breaker == 0 ? 1 : 0);
                put("lclosed", n.breaker == 0); put("lopen", n.breaker == 1); put("ltrip", n.breaker == 2); put("lockout", n.breaker == 2);
                put("spring", spring >= 100);
                put("amps", n.breaker == 0 ? (int) Math.min(100, load * 100 / Math.max(1, relayRating(d, pos, cfg.num("rating")))) : 0);
                break;
            }
            case ISOLATOR: put("handle", n.breaker == 0 ? 0 : 1); put("closed", n.breaker == 0); put("open", n.breaker != 0); put("lock", locked); break;
            case EARTHING: put("handle", n.earthOn ? 0 : 1); put("earthed", n.earthOn); put("unearthed", !n.earthOn); break;
            case INTAKE:
                put("main", n.mainOn ? 1 : 0); put("supply", n.energy > 0); put("on", n.mainOn);
                put("import", (int) (received / 10)); received = 0;
                put("stored", (int) (n.energy * 100 / Math.max(1, kind.capacity)));
                break;
            case METER: {
                peak = Math.max(peak, load);
                text("page", meterPage(net, n));
                put("pulse", load > 0 && (world.getTotalWorldTime() / 10) % Math.max(1, 20 - Math.min(19, load / 200)) == 0);
                break;
            }
            case BATTERY: {
                int soc = (int) (n.energy * 100 / Math.max(1, kind.capacity));
                put("soc", soc); put("socg", soc); put("mode", n.batMode);
                put("run", n.batOn); put("fault", estop || estopFault); put("estop", estop);
                put("chg", n.batOn && n.batMode != 2 && soc < 100); put("dis", n.batOn && n.batMode != 0 && load > 0);
                break;
            }
            case AUTO: {
                int live = 0, dead = 0;
                for (GridData.Net nn : d.nets()) if (GridRemote.controlOnly(d, nn)) continue; else if (nn.problem.isEmpty()) live++; else dead++;
                boolean t = lampTest > 0;
                text("events", autoEvent.length() > 30 ? autoEvent.substring(0, 30) : autoEvent);
                text("stats", "RECL " + arDone + " SHED " + shedList.size() + " GEN " + genDone);
                put("lar", t || pv("master") == 1 && pv("ar") == 1 && arTrips.values().stream().anyMatch(a -> a[0] > 0 && a[0] < 3));
                put("lshed", t || !shedList.isEmpty());
                put("lgen", t || genRunning);
                put("lalarm", t || autoAlarm);
                put("healthy", t || dead == 0 && live > 0);
                if (lampTest > 0) lampTest = Math.max(0, lampTest - 10);
                break;
            }
            case SCADA: {
                int live = 0, dead = 0;
                for (GridData.Net nn : d.nets()) if (GridRemote.controlOnly(d, nn)) continue; else if (nn.problem.isEmpty()) live++; else dead++;
                text("count", "LIVE " + live + " DEAD " + dead);
                int h = problemHash(d);
                put("alarm", h != 0 && h != ackHash || lampTest > 0); put("healthy", dead == 0 && live > 0 || lampTest > 0);
                if (lampTest > 0) lampTest = Math.max(0, lampTest - 10);
                put("lamptest", lampTest > 0);
                break;
            }
            case DIESEL: case TURBINE: {
                int mode = pv("mode");
                if (!ps.containsKey("mode")) { put("mode", 1); mode = 1; }
                if (mode == 2 && !estop && !estopFault && net != null) {                 // AUTO: start on low grid, stop when full
                    long cap = Math.max(1, net.capacity);
                    if (!running && crank == 0 && loto.isEmpty() && pv("batiso") == 0 && pv("trip") == 0 && net.energy * 100 / cap < 25) crank = kind == GridKind.DIESEL ? 3 : 4;
                    if (running && net.energy * 100 / cap > 90) running = false;
                }
                if (crank > 0 && --crank == 0) {
                    FluidStackHolder fsh = new FluidStackHolder(tank.getFluid());
                    if (fuelValue(fsh.fs) == 0 || fsh.fs.amount <= 0) { fault = kind == GridKind.DIESEL ? "failed to start: no fuel" : "failed to start: no steam"; clunk(0.4f); }
                    else { running = true; clunk(0.8f); }
                }
                int thr = cfg.num("throttle");
                put("rpm", crank > 0 && speed < 0.15f ? 15 : Math.round(speed * 100));
                put("hz", Math.round(speed * 600));
                put("sync", n.gcb || busSpeed(d, net, n) < 0 ? 0 : Math.round(phase));
                put("out", lastOut);
                put("lrun", running); put("lgcb", n.gcb); put("lfault", estopFault || !fault.isEmpty() && !fault.startsWith("full"));
                int fuel = tank.getFluidAmount() * 100 / tank.getCapacity();
                put("fuel", fuel); put("llow", fuel < 15); put("estop", estop);
                break;
            }
            case SOLAR:
                if (!ps.containsKey("dc")) put("dc", 1);
                if (!ps.containsKey("track")) put("track", 1);
                {
                    double sd = sunAngle(world, 1f);
                    boolean day = Math.abs(sd) < 90;
                    double wk = world.isThundering() ? 0.2 : world.isRaining() ? 0.4 : 1.0;
                    put("kw", (int) Math.round(lastOut * Elec.W_PER_FE_T / 1000));
                    put("irr", day ? (int) Math.round(1000 * Math.cos(Math.toRadians(sd)) * wk) : 0);
                    put("sunang", day ? (int) Math.round(90 - Math.abs(sd)) : 0);
                    put("tiltg", (int) Math.round(Math.abs(solarTilt(world, 1f))));
                    text("lvl", Elec.Level.byName(cfg.text("outLv"), Elec.Level.AC480).label);
                    put("ltrack", pv("track") == 1 && day && pv("stow") == 0 && !world.isThundering());
                    put("lstow", pv("stow") == 1 || world.isThundering());
                    put("lnight", !day);
                }
                put("out", lastOut); put("grid", lastOut > 0 && n.gcb); put("fault", pv("dc") == 1 && !fault.isEmpty() && !fault.equals("night"));
                break;
            case WIND:
                put("run", lastOut > 0); put("fault", estop || estopFault || windStopped || !fault.isEmpty()); put("estop", estop); put("out", lastOut);
                break;
            case CAPACITOR: {
                put("auto", n.capAuto ? 1 : 0);
                for (int i = 1; i <= 4; i++) put("s" + i, n.steps >= i);
                double need = net == null ? 0 : Math.max(0, Math.min(4, Math.ceil(4.0 * load / ((double) GridData.RATING * Math.max(1, net.transformers)))));
                double pf = need == 0 ? 0.99 : 0.78 + 0.21 * Math.min(1, n.steps / need) - (n.steps > need + 1 ? 0.04 * (n.steps - need - 1) : 0);
                put("pf", (int) Math.round(pf * 100));
                break;
            }
            case RELAY: {
                int[] vs = {50, 80, 100, 120};
                int i = 0; while (i < 3 && vs[i] != n.relaySet) i++;
                put("pickupset", i); put("set", n.relaySet);
                int ratingNear = 0; boolean tripped = false;
                for (EnumFacing f : EnumFacing.values()) {
                    if (world.getTileEntity(pos.offset(f)) instanceof TileGrid b && b.kind == GridKind.BREAKER) {
                        ratingNear = b.cfg.num("rating") * n.relaySet / 100;
                        tripped |= d.node(b.pos).breaker == 2;
                    }
                }
                int pct = ratingNear == 0 ? 0 : (int) Math.min(150, load * 100 / ratingNear);
                put("load", pct); put("pickup", pct >= 80); put("trip", tripped); put("healthy", ratingNear > 0);
                break;
            }
            case ARRESTER: put("surges", n.surges); break;
            default: break;
        }
    }

    /** tiny holder so the crank check reads the tank once */
    private static final class FluidStackHolder { final net.minecraftforge.fluids.FluidStack fs; FluidStackHolder(net.minecraftforge.fluids.FluidStack f) { fs = f; } }

    // ---- ticking: lamps, hum, overload ---------------------------------------------------------------------
    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (kind == GridKind.MOTOR) motorDrive();
        if (kind == GridKind.AUTO && pv("mcb") == 0 && world.getTotalWorldTime() % 20 == (pos.hashCode() & 15)) autoControl();
        if ((kind.control() || kind == GridKind.UPS) && world.getTotalWorldTime() % 20 == (pos.hashCode() & 15)) { GridGear.second(this); panelDirty = true; }
        if (world.getTotalWorldTime() % 10 != (pos.hashCode() & 7)) return;
        GridData d = GridData.get(world);
        GridData.Node n = node();
        GridData.Net net = d.netOf(pos);
        boolean live = net != null && net.problem.isEmpty();
        if (n.burnt) { if (kind == GridKind.MOTOR) motorState = 3; }
        else if (kind.converter() && n.sided() && n.upNet >= 0 && n.upNet < d.nets().size()) {       // fed far over its rating: it fries
            GridData.Net up = d.nets().get(n.upNet);
            Elec.Level rated = Elec.Level.byName(n.primary, Elec.Level.MV13800);
            if (up.level != null && up.problem.isEmpty() && up.level.volts > rated.volts * 1.15)
                burnOut(world, pos, "fed " + up.level.label + " but built for " + rated.label);
        }
        if (kind.buttons()) spin(d, n, net);
        if (kind == GridKind.MOTOR) motor(d, n, net);
        int state = kind == GridKind.BREAKER || kind == GridKind.ISOLATOR ? n.breaker : kind.generator() ? generate(n) : kind == GridKind.MOTOR ? (motorState == 3 ? 2 : motorState > 0 ? 1 : 0) : live ? 1 : 0;
        if (state != lastState) {
            lastState = state;
            IBlockState st = world.getBlockState(pos);
            if (st.getBlock() instanceof BlockGrid && st.getValue(BlockGrid.STATE) != state)
                world.setBlockState(pos, st.withProperty(BlockGrid.STATE, state), 3);
        }
        // plant hum / whine / fans / engines: client GridSounds loops, following the synced panel values
        if (kind == GridKind.BREAKER) {
            n.rating = cfg.num("rating");
            try { n.kA = Integer.parseInt(cfg.text("kA").replace(" kA", "")); } catch (RuntimeException e) { n.kA = 25; }
            if (n.breaker == 2 && cfg.bool("autoReclose") && world.getTotalWorldTime() % 20 == 0 && world.rand.nextInt(Math.max(1, cfg.num("recloseAfter"))) == 0)
                setBreaker((byte) 0, "auto-reclosed");
            if (!ps.containsKey("lr")) put("lr", cfg.text("redstone").equals("Ignore") ? 0 : 1);
            boolean rs = world.isBlockPowered(pos);
            String m = pv("lr") == 1 ? cfg.text("redstone") : "Ignore";                  // remote control only in REMOTE
            if (m.equals("Power opens") && rs && n.breaker == 0) setBreaker((byte) 1, "opened by redstone");
            if (m.equals("Power opens") && !rs && n.breaker == 1 && cfg.bool("rsReclose")) setBreaker((byte) 0, "closed by redstone");
            if (m.equals("Power closes") && rs != (n.breaker == 0) && n.breaker != 2) setBreaker(rs ? (byte) 0 : (byte) 1, "switched by redstone");
        }
        if (kind == GridKind.FEEDER) { n.feeds = cfg.text("feeds"); n.range = cfg.num("range"); n.ownFirst = !"Railway grid".equals(cfg.text("order")); }
        GridGear.mirror(this, n);
        electrics(d, n);
        panelTick();
        sync();
    }

    /** closing joins the networks on each side: refuse if they carry power but none of them is earthed */
    boolean earthedBothSides(GridData d) {
        boolean powered = false, earthed = false;
        for (EnumFacing f : EnumFacing.values()) {
            GridData.Net nb = d.node(pos.offset(f)) == null ? null : d.netOf(pos.offset(f));
            if (nb == null) continue;
            powered |= nb.intake;
            earthed |= nb.earthed;
        }
        return !powered || earthed;
    }

    /** a Protection Relay touching this breaker (real breakers are blind without one) */
    boolean hasRelay() {
        GridData d = GridData.get(world);
        for (EnumFacing f : EnumFacing.values()) {
            GridData.Node nb = d.node(pos.offset(f));
            if (nb != null && nb.kind.relay()) return true;
        }
        return false;
    }

    // ---- generator shaft, synchroscope and protection -----------------------------------------------------------

    /** the speed (per unit) of the live bus this set would close onto; -1 = dead bus (nothing else feeding it) */
    private double busSpeed(GridData d, GridData.Net net, GridData.Node self) {
        if (net == null) return -1;
        if (net.utility) return 1;
        for (long p : net.nodes) {
            GridData.Node o = d.nodes().get(p);
            if (o != null && o != self && (o.kind == GridKind.DIESEL || o.kind == GridKind.TURBINE) && o.feeding()) return o.speed;
        }
        for (long c : net.ins) { GridData.Node cv = d.nodes().get(c); if (cv != null && cv.passes()) return 1; }   // fed through a converter / inverter
        return -1;
    }

    /** every 10 ticks: the shaft runs up / down, droops with load, the synchroscope turns, protection watches */
    private void spin(GridData d, GridData.Node n, GridData.Net net) {
        double load = n.gcb ? Math.max(0, n.genLoad) : 0;
        double bus = busSpeed(d, net, n);
        float target = running ? (float) (speedSet - 0.04 * Math.min(1, load) - (load > 1 ? 0.3 * (load - 1) : 0)) : 0f;
        if (n.gcb && bus == 1 && running) target = 1f;                       // locked to the utility: it can only push power
        speed += (target - speed) * (target > speed ? 0.12f : 0.2f);         // ~5 s run-up, quicker run-down
        if (Math.abs(target - speed) < 0.0005f) speed = target;
        n.speed = speed;
        if (n.gcb || bus < 0) phase = 0;
        else { phase += (float) ((speed - bus) * 360 * 60 * 0.5); phase = ((phase % 360) + 540) % 360 - 180; }
        String tripped = null;
        if (n.gcb && running && target < 0.94f && speed < 0.94f) { if (++ufCount >= 3) tripped = String.format(Locale.ROOT, "UNDER-FREQUENCY %.1f Hz (overloaded on its own)", speed * 60); }
        else ufCount = 0;
        if (n.gcb && load > 1.1) { if (++olCount >= 10) tripped = String.format(Locale.ROOT, "OVERLOAD %.0f%% of its %d kVA", load * 100, n.kva); }
        else olCount = 0;
        if (n.gcb && !running && speed < 0.9f && bus >= 0) tripped = "REVERSE POWER (stopped while on the grid)";
        if (tripped != null) {
            n.gcb = false; ufCount = olCount = 0; d.touch(); clunk(0.7f);
            fault = tripped;
            GridTicker.alarm(world, pos.toLong(), "§c⚡ " + kind.label + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " GCB TRIPPED: " + tripped);
        }
        // AUTO: the auto-synchroniser trims the speed a hair above the bus and closes as the phases line up
        if (pv("mode") == 2 && running && !n.gcb && speed > 0.95f && loto.isEmpty() && !estopFault) {
            if (bus < 0) { n.gcb = true; d.touch(); clunk(1.2f); }
            else {
                speedSet = (float) (bus + 0.0015);
                if (Math.abs(phase) < 15 && Math.abs(speed - bus) < 0.004) { n.gcb = true; d.touch(); clunk(1.2f); }
            }
        }
    }

    // ---- motor + starter -------------------------------------------------------------------------------------------

    private double motorKw() {
        try { return Double.parseDouble(cfg.text("kw").replace(" kW", "")); } catch (RuntimeException e) { return 75; }
    }

    private String beginStart(GridData.Net net) {
        if (estop || estopFault) return "§cE-STOP - release it first";
        String st = cfg.text("starter");
        startTotal = st.equals("Star-delta") ? 120 : st.equals("Soft starter") ? 100 : st.equals("VFD") ? 160 : 60;
        startLeft = startTotal; stallCount = 0; lowV = 0;
        motorState = 1; clunk(1.1f);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.motor_start"), SoundCategory.BLOCKS, 0.9f, st.equals("VFD") ? 0.8f : 1f);
        return "§a▶ Starting (" + st + ")";
    }

    /** every 10 ticks: supply checks, the start (inrush), stall and thermal-overload protection, what it draws */
    private void motor(GridData d, GridData.Node n, GridData.Net net) {
        Elec.Level lv = net == null ? null : net.level;
        boolean vfd = "VFD".equals(cfg.text("starter"));
        String bad = net == null || !net.problem.isEmpty() ? "no supply" : lv == null ? "legacy network (set converters to Real)"
                : !lv.ac && !vfd ? "DC supply - only a VFD can run a motor from DC"
                : lv.ac && lv.phases != 3 && !vfd ? "single-phase supply - a 3-phase motor won't start (fit a VFD)" : "";
        if (!ps.containsKey("hoa")) put("hoa", 0);
        if (pv("hoa") == 2) {                                                     // AUTO: redstone runs it
            boolean rs = world.isBlockPowered(pos) || remoteRun;                // redstone or a SCADA run command
            if (rs && motorState == 0 && loto.isEmpty() && bad.isEmpty()) beginStart(net);
            if (!rs && (motorState == 1 || motorState == 2)) motorState = 0;
        }
        if ((motorState == 1 || motorState == 2) && !bad.isEmpty()) {          // undervoltage release drops the contactor
            motorState = 0;
            GridTicker.alarm(world, pos.toLong(), "§e⚠ Motor at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " dropped out: " + bad);
        }
        double sRated = motorKw() * 1000 / (0.92 * 0.86);
        n.st.put("mva", (int) Math.min(Integer.MAX_VALUE, Math.round(sRated)));   // the thermal model's motor rating (Thermal.models, her 48-next #22)
        if (motorState == 2 && n.st("t49") == 1) { n.st.remove("t49"); motorState = 3; clunk(0.6f); motorTripped("49 thermal overload (inverse-time)"); }
        double pu = n.pu > 0 ? n.pu : 1;
        String st = cfg.text("starter");
        if (motorState == 1) {
            double k = st.equals("Star-delta") ? 2.2 : st.equals("Soft starter") ? 3.2 : vfd ? 1.1 : 6.5;
            double pf = st.equals("Star-delta") ? 0.35 : st.equals("Soft starter") ? 0.4 : vfd ? 0.95 : 0.3;
            if (st.equals("Star-delta") && startLeft <= 10) k = 4.0;            // the star -> delta changeover kick
            n.loadVA = k * sRated * pu;
            n.loadVar = n.loadVA * Math.sqrt(1 - pf * pf);
            startLeft -= 10;
            if (pu < 0.75 && startTotal - startLeft > 10) {
                if (++stallCount >= 4) {
                    motorState = 3; clunk(0.6f); motorTripped("stalled on start");
                    GridTicker.alarm(world, pos.toLong(), String.format(Locale.ROOT, "§c⚡ Motor STALLED at %d %d %d: only %.0f%% voltage at its terminals while starting - use a soft starter / VFD, a bigger cable or transformer",
                            pos.getX(), pos.getY(), pos.getZ(), pu * 100));
                }
            } else stallCount = 0;
            if (motorState == 1 && startLeft <= 0) motorState = 2;
        } else if (motorState == 2) {
            double p = n.drawLast * Elec.W_PER_FE_T / 20.0;
            double q = sRated * (vfd ? 0.05 : 0.35);                             // magnetising current (a VFD supplies its own)
            n.loadVar = q;
            n.loadVA = Math.sqrt(p * p + q * q);
            if (pu < 0.85) {                                                     // low volts: more current for the same torque -> it heats
                if (++lowV >= 20) {
                    motorState = 3; clunk(0.6f); motorTripped("overloaded on low voltage");
                    GridTicker.alarm(world, pos.toLong(), String.format(Locale.ROOT, "§c⚡ Motor at %d %d %d tripped on OVERLOAD: running at %.0f%% voltage draws too much current",
                            pos.getX(), pos.getY(), pos.getZ(), pu * 100));
                }
            } else lowV = Math.max(0, lowV - 1);
        }
        if (motorState == 0 || motorState == 3) { n.loadVA = 0; n.loadVar = 0; }
        if (estop && motorState != 3) motorState = 0;
    }

    /** every tick while running: the shaft drives the machine on its back face (any mod: FE / RF) */
    private void motorDrive() {
        if (motorState != 2) return;
        IBlockState st = world.getBlockState(pos);
        if (!(st.getBlock() instanceof BlockGrid)) return;
        EnumFacing front = st.getValue(BlockGrid.FACING), back = front.getOpposite();
        TileEntity t = world.getTileEntity(pos.offset(back));
        if (t == null || t instanceof TileGrid || !t.hasCapability(CapabilityEnergy.ENERGY, front)) return;
        net.minecraftforge.energy.IEnergyStorage es = t.getCapability(CapabilityEnergy.ENERGY, front);
        if (es == null) return;
        double eff = "VFD".equals(cfg.text("starter")) ? 0.93 : 0.95;
        int rated = (int) Math.round(motorKw() * 1000 / Elec.W_PER_FE_T);
        int want = es.receiveEnergy(Math.max(1, rated), true);
        if (want <= 0) return;
        GridData d = GridData.get(world);
        GridData.Net net = d.netOf(pos);
        if (net == null || !net.problem.isEmpty()) return;
        long got = d.draw(net, (long) Math.ceil(want / eff));
        int give = es.receiveEnergy((int) Math.floor(got * eff), false);
        node().drawThis += got;
        delivered += give;
    }

    // ---- lockout / tagout ----------------------------------------------------------------------------------------

    /** is this device in the state a padlock may hold it in? (open / off / stopped / earthed) */
    public String lockable() {
        GridData.Node n = node();
        switch (kind) {
            case BREAKER: case ISOLATOR: return n.breaker != 0 ? "" : "close it? No - OPEN it first";
            case EARTHING: return n.earthOn ? "" : "switch the earth ON first (locked earthed)";
            case DIESEL: case TURBINE: return !running && crank == 0 && !n.gcb ? "" : "STOP it and open its GCB first";
            case MOTOR: return motorState == 0 || motorState == 3 ? "" : "STOP it first";
            case RECTIFIER: case INVERTER: case DCDC: case UPS: return !n.dcOn ? "" : "switch its output OFF first";
            case INTAKE: return !n.mainOn ? "" : "turn the MAIN SWITCH off first";
            case BATTERY: return !n.batOn ? "" : "STOP it first";
            default: return "nothing to lock on this one";
        }
    }

    public String loto() { return loto; }
    public void setLoto(String who) { loto = who; panelDirty = true; markDirty(); panelTick(); sync(); }

    static final String[] METER_PAGES = {"real power", "energy", "peak demand", "power factor", "voltage", "current"};

    /** the smart meter's display, in real units */
    private String meterPage(GridData.Net net, GridData.Node n) {
        if (net == null) return "NO NET";
        peakW = Math.max(peakW, net.pW);
        double kwh = net.total * 100.0 / 3.6e6;                                   // 1 FE = 100 J (1 FE/t = 2 kW)
        double v = net.level == null ? 0 : net.level.volts * (n.pu > 0 ? n.pu : net.puSource);
        switch (page) {
            case 0: return "P " + Elec.si(net.pW, "W");
            case 1: return String.format(Locale.ROOT, "%.1f kWh", kwh);
            case 2: return "PK " + Elec.si(peakW, "W");
            case 3: return String.format(Locale.ROOT, "PF %.2f", net.pf);
            case 4: return "V " + Elec.si(v, "V");
            default: return "I " + Elec.si(net.amps, "A");
        }
    }

    void setBreaker(byte b, String why) {
        GridData d = GridData.get(world);
        GridData.Node n = node();
        if (n.breaker == b) return;
        if (b == 0 && !loto.isEmpty()) { last = "refused to close: locked out by " + loto; return; }
        if (b == 0 && kind == GridKind.BREAKER && !hasRelay()) { last = "refused to close: no Protection Relay"; return; }
        if (b == 0 && kind == GridKind.BREAKER && !com.dogpound.railmap.asset.Commissioning.mayEnergise(world, pos)) { last = "refused to close: UNCOMMISSIONED (config commissioningRequired)"; return; }
        n.breaker = b;
        ops++;
        n.ops++;
        n.wear += 0.0001f;                                                      // mechanical wear: ~10,000 operations
        if (b == 2) n.lastTrip = why.replaceFirst("^TRIPPED: ", "");
        History.log(world, pos, b == 0 ? "CLOSE" : b == 2 ? "TRIP" : "OPEN", why);
        d.touch();
        world.playSound(null, pos, b == 2 ? ModSounds.BREAKER_TRIP : b == 0 ? ModSounds.BREAKER_CLOSE : ModSounds.BREAKER, SoundCategory.BLOCKS, 1f, 1f);
        if (b == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.spring_charge"), SoundCategory.BLOCKS, 0.6f, 1f);
        if (b == 2) world.playSound(null, pos, ModSounds.ALARM, SoundCategory.BLOCKS, 0.8f, 1f);
        last = why;
        markDirty();
        world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
    }

    private String last = "";

    /** overload protection, called once a second by GridTicker */
    void checkOverload(GridData.Net net) {
        GridData.Node n = node();
        int lim = relayRating(GridData.get(world), pos, cfg.num("rating"));
        if (n.breaker == 0 && net.lastSecond > (long) lim * 20) {
            trips++;
            setBreaker((byte) 2, "TRIPPED: overload " + net.lastSecond / 20 + " FE/t > " + lim);
        }
    }

    private int trips;
    private int ops;                // breaker: mechanical operation counter

    String click(EntityPlayer p) {
        if (p == null) return "ok";                                             // a remote (SCADA) command, no player to talk to
        GridData d = GridData.get(world);
        GridData.Node n = node();
        GridData.Net net = d.netOf(pos);
        { String gs = GridGear.status(this, n); if (gs != null) return gs; }
        switch (kind) {
            case BREAKER:
                return "§7Breaker " + (n.breaker == 0 ? "§cCLOSED (live)" : n.breaker == 1 ? "§aOPEN (dead)" : "§6TRIPPED - press RESET")
                        + "§7 · " + (pv("lr") == 0 ? "LOCAL" : "REMOTE") + " · use the handle or CLOSE / OPEN" + (last.isEmpty() ? "" : " · last: " + last);
            case SCADA:
                return summary(d);
            case AUTO:
                return "§7Auto Controller · " + (pv("master") == 1 ? "§aAUTO ON" : "§cAUTO OFF") + "§7 · last: " + autoEvent
                        + " · breakers it works need their key in REMOTE";
            case ISOLATOR: {
                boolean opening = n.breaker == 0;
                long load = net == null ? 0 : net.lastSecond / 20;
                n.breaker = opening ? (byte) 1 : (byte) 0;
                d.touch();
                markDirty();
                if (opening && load > 0) {
                    // breaking load current with an off-load switch: arc flash
                    world.createExplosion(null, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 1.2f, false);
                    p.setFire(4);
                    world.playSound(null, pos, ModSounds.BREAKER, SoundCategory.BLOCKS, 1.5f, 0.5f);
                    last = "ARC FLASH: opened on load (" + load + " FE/t) by " + p.getName();
                    n.ops++; History.log(world, pos, "FAULT", last);
                    return "§c§l⚡ ARC FLASH! §r§cYou opened a disconnector carrying " + load + " FE/t. Open the breaker first, then the disconnector.";
                }
                world.playSound(null, pos, ModSounds.RELAY, SoundCategory.BLOCKS, 1f, 0.7f);
                last = (opening ? "opened" : "closed") + " by " + p.getName();
                n.ops++; History.log(world, pos, opening ? "OPEN" : "CLOSE", last);
                return opening ? "§eDisconnector OPEN - visible break, safe to work downstream" : "§aDisconnector CLOSED";
            }
            case DIESEL: case TURBINE: case SOLAR: case WIND: {
                net.minecraftforge.fluids.FluidStack in = tank.getFluid();
                String fuel = kind.buttons() ? " · " + (in == null ? "tank empty" : String.format(Locale.ROOT, "%,d mB %s", in.amount, in.getLocalizedName())) : "";
                return "§7" + kind.label + " · " + (running || !kind.buttons() ? (fault.isEmpty() ? "§arunning " + lastOut + " FE/t" : "§e" + fault) : "§cstopped")
                        + "§7" + fuel + String.format(Locale.ROOT, " · stored %,d FE", n.energy);
            }
            default:
                if (net == null) return "§7" + kind.label + " - not connected";
                String e = String.format(Locale.ROOT, "%,d / %,d FE", net.energy, net.capacity);
                return "§7" + kind.label + " · network " + net.id + " · " + e + " · load " + net.lastSecond / 20 + " FE/t · "
                        + (net.problem.isEmpty() ? "§alive" : "§c" + net.problem);
        }
    }

    private static String summary(GridData d) {
        int live = 0, dead = 0;
        for (GridData.Net n : d.nets()) if (n.problem.isEmpty()) live++; else dead++;
        return "§bGrid: §a" + live + " live §7· §c" + dead + " dead §7networks · " + d.nodes().size() + " parts";
    }

    int comparator() {
        if (world == null) return 0;
        GridData.Net net = GridData.get(world).netOf(pos);
        if (net == null) return 0;
        if (kind == GridKind.METER) return (int) Math.min(15, net.lastSecond / 20 / Math.max(1, cfg.num("fullScale") / 15));
        return net.capacity == 0 ? 0 : (int) Math.min(15, net.energy * 15 / net.capacity);
    }

    // ---- Settings Console ------------------------------------------------------------------------------------
    @Override public String settingsTitle() { return kind.label; }

    /** HV plant: settings are changed under a permit (IEDs: only in TEST mode) */
    @Override public String denySettings(EntityPlayer p) {
        if (kind == GridKind.IED) return node().st("test") == 1 ? null : "§cIED settings are locked IN SERVICE - turn its key to TEST first";
        if (kind.control() || p.capabilities.isCreativeMode) return null;
        String why = Permits.mayWork(p, pos);
        return why.isEmpty() ? null : why;
    }
    @Override public SettingsStore settings() { return cfg; }

    @Override
    public List<Setting> settingDefs() {
        List<Setting> l = new ArrayList<>();
        String g = "Machine", s = "Sound", st = "Status";
        switch (kind) {
            case INTAKE:
            case BATTERY:
                l.add(Setting.num(g, "inRate", "Max input", "Most RF / FE accepted per tick from cables", 20000, 100, 1_000_000, 100, "FE/t"));
                break;
            case BREAKER:
                l.add(Setting.choice(g, "kA", "Breaking capacity", "The biggest short-circuit current it can interrupt. If the network's fault current is bigger, a fault destroys the breaker (check it with the Multimeter)", "25 kA", "10 kA", "25 kA", "40 kA", "63 kA"));
                l.add(Setting.num(g, "rating", "Trip rating", "Trips when the network draws more than this", 2000, 50, 100000, 50, "FE/t"));
                l.add(Setting.bool(g, "autoReclose", "Auto-reclose", "Try closing again by itself after a trip", false));
                l.add(Setting.num(g, "recloseAfter", "Reclose after about", "Average wait before an auto-reclose", 30, 1, 600, 1, "s"));
                l.add(Setting.choice(g, "redstone", "Redstone", "What a redstone signal does", "Ignore", "Ignore", "Power opens", "Power closes"));
                l.add(Setting.bool(g, "rsReclose", "Close again when redstone stops", "For 'Power opens'", true));
                break;
            case FEEDER:
                l.add(Setting.choice(g, "feeds", "Feeds", "Which kind of line this clamp energises", "Auto", "Auto", "Beam", "Wire", "Third rail"));
                l.add(Setting.num(g, "range", "Section length", "Line within this many blocks is live", 64, 8, 512, 8, "blocks"));
                l.add(Setting.bool(g, "direct", "Take power straight from cables", "Any mod's cable, wire connector or generator touching it feeds the line directly (Immersive Engineering, Mekanism, Thermal, EnderIO...)", true));
                l.add(Setting.num(g, "inRate", "Max input", "Most RF / FE accepted per tick from cables", 20000, 100, 1_000_000, 100, "FE/t"));
                l.add(Setting.choice(g, "order", "Use first", "Which supply trains draw from first", "Own store", "Own store", "Railway grid"));
                l.add(Setting.bool(g, "jumper", "Jumper cable to the rail", "Draw the bonding cable from the feeder to the nearest track", true));
                l.add(Setting.choice(g, "cableColour", "Cable colour", "", "Black", "Black", "Red", "Copper", "Yellow", "Pride pink"));
                break;
            case METER:
                l.add(Setting.num(g, "fullScale", "Full scale", "Load that gives comparator 15", 3000, 15, 100000, 15, "FE/t"));
                break;
            case DIESEL: case TURBINE:
                l.add(Setting.num(g, "throttle", "Power", "How hard it runs (the ▲ / ▼ buttons on the panel too)", 100, 10, 100, 10, "%"));
                l.add(Setting.choice(g, "redstone", "Redstone", "Let redstone start / stop it", "Ignore", "Ignore", "Power runs it", "Power stops it"));
                l.add(Setting.bool(g, "stopWhenFull", "Idle when its store is full", "Saves fuel when nothing is drawing", true));
                l.add(Setting.num(g, "kva", "Rating", "Apparent power the set carries. Over 110 % for 5 s and it trips; on its own it slows down (frequency falls) and the under-frequency relay trips it at 56.4 Hz",
                        kind == GridKind.DIESEL ? 2000 : 5000, 50, 100000, 50, "kVA"));
                break;
            case MOTOR:
                l.add(Setting.choice(g, "kw", "Rated power", "Shaft power. Above ~250 kW real motors are usually 4.16 kV", "75 kW", "7.5 kW", "22 kW", "75 kW", "250 kW", "1000 kW"));
                l.add(Setting.choice(g, "starter", "Starter", "Direct on line: ~6.5x current for 3 s. Star-delta: ~2.2x for 6 s. Soft starter: ~3.2x ramp 5 s. VFD: ~1.1x, smooth 8 s ramp, also runs from DC",
                        "Direct on line", "Direct on line", "Star-delta", "Soft starter", "VFD"));
                break;
            case SOLAR:
                l.add(Setting.num(g, "panels", "Panel rows", "How many rows the array has (more = more power)", 4, 1, 8, 1, ""));
                l.add(Setting.bool(g, "stopWhenFull", "Idle when its store is full", "", true));
                break;
            case WIND:
                l.add(Setting.choice(g, "rotor", "Rotor", "Bigger rotors make more but need a taller tower (stack Wind Tower Sections on it)", ROTORS[1], ROTORS));
                l.add(Setting.bool(g, "stormProtect", "Storm protection (tower turbines)", "Feathers and brakes above 25 m/s. OFF in a thunderstorm = overspeed: the blades and gearbox wreck themselves", true));
                l.add(Setting.bool(g, "stormStop", "Feather the blades in storms (compact turbine)", "Real turbines stop in very high wind", false));
                l.add(Setting.bool(g, "stopWhenFull", "Idle when its store is full", "", true));
                break;
            default:
                break;
        }
        GridGear.settings(this, l);
        String el = "Electrical";
        switch (kind) {
            case INTAKE:
                l.add(Setting.choice(el, "outLv", "Supply voltage", "The voltage the utility supplies at this connection",
                        Elec.Level.MV13800.label, labels(Elec.Level.HV69000, Elec.Level.MV13800, Elec.Level.MV4160, Elec.Level.AC480)));
                break;
            case DIESEL: case TURBINE: case SOLAR: case WIND:
                l.add(Setting.choice(el, "outLv", "Output voltage", "What the generator / inverter puts out - step it up with a Power Transformer for long runs",
                        Elec.Level.AC480.label, kind == GridKind.SOLAR ? labels(Elec.Level.AC480, Elec.Level.AC240, Elec.Level.DC48, Elec.Level.DC600, Elec.Level.DC1500) : labels(Elec.Level.AC480, Elec.Level.MV4160, Elec.Level.MV13800)));
                break;
            case TRANSFORMER: case TRACTION: case RECTIFIER: case INVERTER: case DCDC: {
                l.add(Setting.choice(el, "sides", "Connections", "Real: the BACK is the primary (input), every other face the secondary (output) - two networks at two voltages. Legacy: the old pass-through", REAL, REAL, LEGACY));
                Elec.Level[] ac = {Elec.Level.HV69000, Elec.Level.MV13800, Elec.Level.MV4160, Elec.Level.AC480, Elec.Level.AC208, Elec.Level.AC240, Elec.Level.AC120};
                Elec.Level dp = defaultPrimary(kind);
                Elec.Level[] dc = {Elec.Level.DC12, Elec.Level.DC24, Elec.Level.DC48, Elec.Level.DC600, Elec.Level.DC750, Elec.Level.DC1500, Elec.Level.DC3000};
                Elec.Level[] acOut = {Elec.Level.AC480, Elec.Level.AC208, Elec.Level.AC240, Elec.Level.AC120};
                l.add(Setting.choice(el, "primaryLv", "Rated primary", "The voltage its back side is built for - much more and it flashes over, much less and it locks out", dp.label,
                        kind == GridKind.TRACTION ? labels(Elec.Level.HV69000, Elec.Level.MV13800) : kind == GridKind.INVERTER || kind == GridKind.DCDC ? labels(dc) : labels(ac)));
                if (kind == GridKind.TRANSFORMER)
                    l.add(Setting.choice(el, "secondaryLv", "Rated secondary", "What it steps the voltage to", Elec.Level.AC480.label, labels(ac)));
                if (kind == GridKind.INVERTER)
                    l.add(Setting.choice(el, "secondaryLv", "AC output", "The AC it makes (sine-wave, 60 Hz)", Elec.Level.AC480.label, labels(acOut)));
                if (kind == GridKind.DCDC)
                    l.add(Setting.choice(el, "secondaryLv", "DC output", "The DC voltage it regulates to", Elec.Level.DC48.label, labels(dc)));
                l.add(Setting.num(el, "kva", "Rating", "Apparent power it carries before it overheats", kind == GridKind.TRACTION ? 10000 : 5000, 50, 100000, 50, "kVA"));
                break;
            }
            default: break;
        }
        if (kind.generator()) {
            l.add(Setting.bool("Other mods", "export", "Give power to other mods' cables", "Any mod's cable touching it can pull power out", true));
            l.add(Setting.num("Other mods", "exportRate", "Most given per tick", "", 4000, 100, 1_000_000, 100, "FE/t"));
        }
        if (kind.converter() || kind.buttons()) {
            l.add(Setting.bool(s, "sound", "Hum", "The sound of energised plant", true));
            l.add(Setting.num(s, "volume", "Volume", "Hum volume", 60, 0, 200, 10, "%"));
        }
        if (world != null && !world.isRemote) {
            GridData d = GridData.get(world);
            GridData.Net net = d.netOf(pos);
            GridData.Node n = node();
            l.add(Setting.info(st, "Network", net == null ? "-" : "#" + net.id + " · " + net.nodes.size() + " parts"));
            l.add(Setting.info(st, "Live", net == null ? "no" : net.problem.isEmpty() ? "yes" : "no - " + net.problem));
            if (net != null) {
                l.add(Setting.info(st, "Has", (net.intake ? "intake " : "") + (net.transformer ? "transformer " : "") + (net.rectifier ? "rectifier(DC) " : "")
                        + (net.traction ? "traction(AC) " : "")));
                l.add(Setting.info(st, "Energy", String.format(Locale.ROOT, "%,d / %,d FE", net.energy, net.capacity)));
                l.add(Setting.info(st, "Load", net.lastSecond / 20 + " FE/t"));
                l.add(Setting.info(st, "Energy used", String.format(Locale.ROOT, "%,d FE", net.total)));
            }
            if (net != null && net.level != null) {
                String es = "Electrical";
                double v = net.level.volts * (n.pu > 0 ? n.pu : net.puSource);
                l.add(Setting.info(es, "Level", net.level.label + (net.level.ac ? " · " + net.level.hz + " Hz · " + net.level.phases + "φ" : " · DC")));
                l.add(Setting.info(es, "Voltage here", Elec.si(v, "V") + String.format(Locale.ROOT, " (%.1f%%)", v / net.level.volts * 100)));
                l.add(Setting.info(es, "Current here", Elec.si(n.amps, "A")));
                l.add(Setting.info(es, "Real power P", Elec.si(net.pW, "W")));
                l.add(Setting.info(es, "Reactive power Q", Elec.si(net.qVar, "var")));
                l.add(Setting.info(es, "Apparent power S", Elec.si(net.sVA, "VA")));
                l.add(Setting.info(es, "Power factor", String.format(Locale.ROOT, "%.2f", net.pf)));
                l.add(Setting.info(es, "Network current", Elec.si(net.amps, "A")));
                l.add(Setting.info(es, "Cable losses", Elec.si(net.lossW, "W")));
                l.add(Setting.info(es, "Efficiency", String.format(Locale.ROOT, "%.1f%%", net.eff * 100)));
            } else if (net != null) l.add(Setting.info("Electrical", "Level", "legacy network (no voltage levels) - set converters to Real connections"));
            if (kind == GridKind.TRANSFORMER || kind == GridKind.TRACTION) {
                l.add(Setting.info(st, "Winding temperature", String.format(Locale.ROOT, "%.0f °C rise%s", n.heat, n.hot ? " - TRIPPED (overheated)" : "")));
                if (net != null) l.add(Setting.info(st, "Rating", (GridData.RATING * Math.max(1, net.transformers)) + " FE/t for this network (" + net.transformers + " transformer(s))"));
            }
            if (kind == GridKind.BREAKER) l.add(Setting.info(st, "Protection relay", hasRelay() ? "fitted" : "MISSING - breaker will not close"));
            if (net != null && kind.machine()) {
                l.add(Setting.info(st, "Earthed", net.earthed ? "yes" : "NO - fit an Earthing Switch"));
                l.add(Setting.info(st, "Capacitor bank", net.capacitor ? "yes (clean power)" : "no - DC rough (70%), AC losses (90%)"));
                l.add(Setting.info(st, "Surge arrester", net.arrester ? "yes" : "no - storms will trip it"));
            }
            if (kind == GridKind.FEEDER) {
                l.add(Setting.info(st, "Own store", String.format(Locale.ROOT, "%,d / %,d FE (from cables touching it)", n.energy, kind.capacity)));
                BlockPos r = nearestRail();
                l.add(Setting.info(st, "Clamped to", r == null ? "no track within 4 blocks - put it beside the line" : "track at " + r.getX() + ", " + r.getY() + ", " + r.getZ()));
            }
            if (kind == GridKind.BREAKER) {
                l.add(Setting.info(st, "Breaker", n.breaker == 0 ? "closed" : n.breaker == 1 ? "open" : "TRIPPED"));
                l.add(Setting.info(st, "Trips", String.valueOf(trips)));
                l.add(Setting.info(st, "Last", last.isEmpty() ? "-" : last));
            }
            if (kind == GridKind.INTAKE || kind == GridKind.BATTERY || kind.generator())
                l.add(Setting.info(st, "Stored here", String.format(Locale.ROOT, "%,d / %,d FE", n.energy, kind.capacity)));
            if (kind.generator()) {
                l.add(Setting.info(st, "Running", (running || !kind.buttons() ? (fault.isEmpty() ? "yes" : fault) : "stopped (press START on the panel)")));
                l.add(Setting.info(st, "Output", lastOut + " FE/t"));
            }
            if (kind.buttons()) {
                net.minecraftforge.fluids.FluidStack in = tank.getFluid();
                l.add(Setting.info(st, kind == GridKind.DIESEL ? "Fuel" : "Steam", in == null ? "empty - pipe it in from any mod" : String.format(Locale.ROOT, "%,d / 16,000 mB %s", in.amount, in.getLocalizedName())));
            }
            if (kind == GridKind.TRANSFORMER || kind == GridKind.TRACTION || kind.electronic())
                l.add(Setting.info(st, "Thermal", Thermal.describe(n)));
            if (kind == GridKind.BREAKER && hasIed(d, pos)) l.add(Setting.info(st, "Overcurrent", "timed by the IED in real amps (the FE/t trip rating sets its rated current)"));
            String hs = "History";
            l.add(Setting.info(hs, "Counters", History.counters(n)));
            if (net != null && net.level != null) l.add(Setting.info(hs, "Megger test", Insulation.megohms(n) + (Insulation.passes(n, net.level.volts) ? " - PASS" : " - FAIL (needs " + Math.round(net.level.volts / 1000 + 1) + " MΩ)")));
            int i = 0;
            for (String e : History.recent(n, 20)) l.add(Setting.info(hs, "#" + (++i), e));
            if (i == 0) l.add(Setting.info(hs, "Log", "nothing recorded yet"));
            if (kind == GridKind.SCADA) {
                for (GridData.Net nn : d.nets())
                    l.add(Setting.info(st, "Network #" + nn.id, nn.nodes.size() + " parts · " + (nn.problem.isEmpty() ? "LIVE" : nn.problem)
                            + String.format(Locale.ROOT, " · %,d FE · %d FE/t", nn.energy, nn.lastSecond / 20)));
            }
        }
        return l;
    }

    /** the lamp / state changing must not throw this tile away (it holds the running flag, the fuel tank, settings) */
    @Override
    public boolean shouldRefresh(net.minecraft.world.World w, net.minecraft.util.math.BlockPos p, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setString("kind", kind.name());
        t.setInteger("trips", trips); t.setInteger("ops", ops);
        t.setBoolean("running", running);
        t.setBoolean("remoteRun", remoteRun);
        t.setTag("tank", tank.writeToNBT(new NBTTagCompound()));
        NBTTagCompound pc = new NBTTagCompound();
        for (java.util.Map.Entry<String, Integer> e : ps.entrySet()) pc.setInteger(e.getKey(), e.getValue());
        t.setTag("ps", pc);
        NBTTagCompound tc = new NBTTagCompound();
        for (java.util.Map.Entry<String, String> e : pt.entrySet()) tc.setString(e.getKey(), e.getValue());
        t.setTag("pt", tc);
        t.setString("pressId", pressId); t.setLong("pressAt", pressAt);
        t.setBoolean("estop", estop); t.setBoolean("estopFault", estopFault); t.setBoolean("windStopped", windStopped);
        t.setInteger("spring", spring); t.setBoolean("locked", locked); t.setLong("peak", peak); t.setInteger("page", page);
        t.setString("rectFault", rectFault); t.setBoolean("txAlarm", txAlarm); t.setInteger("ackHash", ackHash);
        t.setFloat("speed", speed); t.setFloat("speedSet", speedSet); t.setInteger("motorState", motorState); t.setString("loto", loto); t.setDouble("peakW", peakW); t.setInteger("ctlWire", ctlWire);
        if (world != null && !world.isRemote) t.setBoolean("burnt", isBurnt());
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        try { kind = GridKind.valueOf(t.getString("kind")); } catch (IllegalArgumentException e) { /* keep */ }
        trips = t.getInteger("trips"); ops = t.getInteger("ops");
        running = t.getBoolean("running");
        remoteRun = t.getBoolean("remoteRun");
        tank.readFromNBT(t.getCompoundTag("tank"));
        NBTTagCompound pc = t.getCompoundTag("ps");
        for (String k : pc.getKeySet()) ps.put(k, pc.getInteger(k));
        NBTTagCompound tc = t.getCompoundTag("pt");
        for (String k : tc.getKeySet()) pt.put(k, tc.getString(k));
        pressId = t.getString("pressId"); pressAt = t.getLong("pressAt");
        estop = t.getBoolean("estop"); estopFault = t.getBoolean("estopFault"); windStopped = t.getBoolean("windStopped");
        spring = t.hasKey("spring") ? t.getInteger("spring") : 100; locked = t.getBoolean("locked");
        peak = t.getLong("peak"); page = t.getInteger("page");
        rectFault = t.getString("rectFault"); txAlarm = t.getBoolean("txAlarm"); ackHash = t.getInteger("ackHash");
        speed = t.getFloat("speed"); speedSet = t.hasKey("speedSet") ? t.getFloat("speedSet") : 1f; motorState = t.getInteger("motorState") == 1 ? 0 : t.getInteger("motorState");
        loto = t.getString("loto"); peakW = t.getDouble("peakW");
        ctlWire = t.hasKey("ctlWire") ? t.getInteger("ctlWire") : -1;
        burntClient = t.getBoolean("burnt");
        cfg.read(t);
    }

    // the client needs the console settings too (feeder cable colour/visibility)
    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    // ---- SCADA: remote control from OpenComputers (Data Link) and the Auto Controller --------------------------
    private boolean remoteRun;   // a motor in AUTO runs on a remote command as well as on redstone

    /** the controls a computer may name for this machine (its panel's buttons and selectors) */
    public List<String> remoteControls() {
        List<String> l = new ArrayList<>();
        Panel pn = Panel.of(kind);
        if (pn == null && getBlockType() != null && !layout().isEmpty()) pn = layout().all;   // model-only controls (IED, ATS, UPS...)
        if (pn != null) for (Panel.C c : pn.cs) if (c.id != null && !c.id.isEmpty() && c.t != Panel.T.LAMP && c.t != Panel.T.GAUGE && c.t != Panel.T.DIGITS && c.t != Panel.T.TILE && c.t != Panel.T.LABEL) l.add(c.id);
        if (kind == GridKind.BREAKER) { l.remove("lr"); l.remove("handle"); if (!l.contains("open")) l.add("open"); if (!l.contains("close")) l.add("close"); if (!l.contains("reset")) l.add("reset"); }
        return l;
    }

    /**
     * A remote command: the same interlocks as the panel. Breakers obey only with their key in REMOTE (a real
     * LOCAL / REMOTE switch); disconnectors and earthing switches are manual-only; a motor takes start / stop in AUTO.
     * Returns "ok" or the reason it was refused / what the panel said.
     */
    public String remote(String id, String who) {
        if (!loto.isEmpty()) return "LOCKED OUT by " + loto + " - only they can remove the padlock";
        GridData d = GridData.get(world);
        GridData.Node n = node();
        if (n == null) return "not on a network";
        switch (kind) {
            case BREAKER: {
                if (pv("lr") != 1) return "breaker key is in LOCAL - turn it to REMOTE for SCADA control";
                switch (id) {
                    case "open": case "trip":
                        if (n.breaker != 0) return "already open";
                        setBreaker((byte) 1, "opened by SCADA (" + who + ")"); sync(); return "ok";
                    case "close":
                        if (n.breaker == 2) return "TRIPPED - lockout: reset first";
                        if (n.breaker == 0) return "already closed";
                        if (spring < 100) return "closing spring still charging (" + spring + "%)";
                        if (!hasRelay()) return "no Protection Relay touching it";
                        if (!earthedBothSides(d)) return "network not earthed";
                        { String il = GridGear.interlock(world, pos); if (!il.isEmpty()) return il; }
                        { String pw = Permits.closeBlocked(world, pos); if (!pw.isEmpty()) return pw; }
                        setBreaker((byte) 0, "closed by SCADA (" + who + ")"); spring = 0; sync(); return "ok";
                    case "reset":
                        if (n.breaker != 2) return "nothing to reset";
                        setBreaker((byte) 1, "lockout reset by SCADA (" + who + ")"); sync(); return "ok";
                    default: return "a breaker takes: open, close, reset";
                }
            }
            case ISOLATOR: case EARTHING: return "manual-only device - operate it at the switch itself";
            case MOTOR:
                if (id.equals("start") || id.equals("stop")) {
                    if (pv("hoa") != 2) return "selector is in " + (pv("hoa") == 0 ? "HAND" : "OFF") + " - remote control needs AUTO";
                    remoteRun = id.equals("start"); markDirty();
                    if (!remoteRun && (motorState == 1 || motorState == 2)) motorState = 0;
                    return "ok";
                }
                break;
            case RELAY: if (id.equals("test")) return "relay test is local-only"; break;
            default: break;
        }
        if (!remoteControls().contains(id)) return "no control '" + id + "' on this " + kind.label + " (has: " + String.join(", ", remoteControls()) + ")";
        String r = press(id, null);
        if (r == null || r.equals("ok")) return "ok";
        boolean refused = r.startsWith("§c") || r.startsWith("§e") || r.startsWith("§6") || r.startsWith("§7Already") || r.startsWith("§7Nothing");
        return (refused ? "" : "ok: ") + r.replaceAll("§.", "");
    }

    // ---- next-48 #44: chaos scenarios trip a breaker and put it back exactly as it was (test mode only) -------------
    public byte breakerState() { GridData.Node n = node(); return n == null ? -1 : n.breaker; }
    public void chaosTrip(String why) { if (kind == GridKind.BREAKER && node() != null && node().breaker == 0) { setBreaker((byte) 2, "TRIPPED: " + why); sync(); } }
    public void chaosRestore(byte was, String why) { if (kind == GridKind.BREAKER && node() != null && node().breaker != was) { setBreaker(was, why); sync(); } }

    private static final String[] BREAKER_STATE = {"closed", "open", "tripped"}, MOTOR_STATE = {"stopped", "starting", "running", "overload tripped"};

    /** everything a computer can read about this machine */
    public java.util.Map<String, Object> remoteInfo() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        GridData d = GridData.get(world);
        GridData.Node n = node();
        GridData.Net net = d.netOf(pos);
        m.put("x", pos.getX()); m.put("y", pos.getY()); m.put("z", pos.getZ());
        m.put("kind", kind.id.replace("grid_", "")); m.put("name", kind.label);
        if (n == null) return m;
        m.put("network", n.net);
        if (net != null) {
            m.put("live", net.problem.isEmpty()); m.put("problem", net.problem);
            if (net.level != null) {
                m.put("level", net.level.label);
                m.put("volts", Math.round(net.level.volts * (n.pu > 0 ? n.pu : net.puSource)));
                m.put("hz", net.level.ac ? Math.round(net.level.hz * net.hzPu * 100) / 100.0 : 0);
            }
        }
        m.put("amps", Math.round(n.amps * 10) / 10.0);
        m.put("locked", !loto.isEmpty());
        switch (kind) {
            case BREAKER: m.put("state", BREAKER_STATE[Math.max(0, Math.min(2, n.breaker))]); m.put("remote", pv("lr") == 1); break;
            case ISOLATOR: m.put("state", n.breaker == 0 ? "closed" : "open"); break;
            case EARTHING: m.put("earthed", n.earthOn); break;
            case DIESEL: case TURBINE:
                m.put("running", running); m.put("breakerClosed", n.gcb); m.put("mode", new String[]{"off", "manual", "auto"}[Math.max(0, Math.min(2, pv("mode")))]);
                m.put("genHz", Math.round(n.speed * 600) / 10.0); m.put("loadPct", Math.round(n.genLoad * 100)); m.put("fault", fault);
                m.put("fuel", n.energy); break;
            case MOTOR: m.put("state", MOTOR_STATE[Math.max(0, Math.min(3, motorState))]); m.put("selector", new String[]{"hand", "off", "auto"}[Math.max(0, Math.min(2, pv("hoa")))]); m.put("kw", motorKw()); break;
            case BATTERY: m.put("on", n.batOn); m.put("energy", n.energy); m.put("capacity", kind.capacity); break;
            case INTAKE: m.put("on", n.mainOn); m.put("energy", n.energy); m.put("capacity", kind.capacity); break;
            case RECTIFIER: case INVERTER: case DCDC: m.put("on", n.dcOn); m.put("fault", rectFault); break;
            case SOLAR: case WIND: m.put("energy", n.energy);
                if (kind == GridKind.WIND && towerSections() > 0) {
                    m.put("wind", Math.round(Wind.at(world, hubHeightM()) * 10) / 10.0); m.put("windFrom", Wind.compass(Wind.from(world)));
                    m.put("rpm", Math.round(rotorRpm() * 10) / 10.0); m.put("hubM", Math.round(hubHeightM())); m.put("rotor", ROTORS[rotorSize()]);
                }
                break;
            default: break;
        }
        GridGear.info(this, n, m);
        m.put("controls", remoteControls());
        return m;
    }

    // ---- Auto Controller: the default program (no computer needed) ------------------------------------------
    private final java.util.Map<Long, long[]> arTrips = new java.util.HashMap<>();   // breaker -> {reclose attempts, tick of the last attempt}
    private final java.util.Deque<Long> shedList = new java.util.ArrayDeque<>();      // breakers we opened to shed load (last first)
    private String autoEvent = "standing by";
    private int arDone, genDone, autoHash, autoAck;
    private long lastShed, calmSince;
    private boolean autoAlarm, genRunning;

    private void autoLog(String s) {
        autoEvent = s;
        GridTicker.alarm(world, pos.toLong(), "§b⚙ Auto Controller: §7" + s);
    }

    private static String at(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }

    private TileGrid tileAt(BlockPos p) { return world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileGrid g ? g : null; }

    public boolean autoAlarmOut() { return autoAlarm && pv("mcb") == 0; }

    private void autoControl() {
        GridData d = GridData.get(world);
        long now = world.getTotalWorldTime();
        java.util.List<String> alarms = GridRemote.alarms(world);
        autoHash = alarms.isEmpty() ? 0 : alarms.hashCode();
        boolean was = autoAlarm;
        autoAlarm = autoHash != 0 && autoHash != autoAck;
        if (autoAlarm && now % 40 < 20) world.playSound(null, pos, com.dogpound.railmap.ModSounds.BUZZER, SoundCategory.BLOCKS, 1f, 1.0f);
        if (was != autoAlarm) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        if (pv("master") != 1) return;

        boolean stressed = false;
        int hotNet = -1;
        genRunning = false;
        for (java.util.Map.Entry<Long, GridData.Node> e : new java.util.ArrayList<>(d.nodes().entrySet())) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            GridData.Node n = e.getValue();
            // 1) auto-reclose: a tripped breaker gets reset + closed after 5 s dead time, three tries, then lockout
            if (n.kind == GridKind.BREAKER && pv("ar") == 1) {
                long[] s = arTrips.get(e.getKey());
                if (n.breaker == 2) {
                    if (s == null) { s = new long[]{0, now}; arTrips.put(e.getKey(), s); autoLog("breaker " + at(p) + " TRIPPED - reclosing in 5 s"); }
                    if (s[0] < 3 && now - s[1] >= 100) {
                        TileGrid g = tileAt(p);
                        if (g != null) {
                            g.remote("reset", "auto-reclose");
                            String r = g.remote("close", "auto-reclose");
                            s[0]++; s[1] = now; arDone++;
                            if (s[0] >= 3 && !r.startsWith("ok")) autoLog("LOCKOUT: breaker " + at(p) + " - " + r);
                            else autoLog(r.startsWith("ok") ? "reclosed " + at(p) + " (try " + s[0] + "/3)" : "reclose " + at(p) + " refused: " + r);
                        }
                    } else if (s[0] >= 3 && s[1] > 0) { autoLog("LOCKOUT: breaker " + at(p) + " tripped 3 times - fix the fault, then RESET LOCKOUT"); s[1] = -1; }
                } else if (s != null && n.breaker == 0 && s[1] > 0 && now - s[1] > 600) arTrips.remove(e.getKey());   // held 30 s: count cleared
            }
            // a transformer about to overheat (derated by heat / failed fans): shed what it feeds before it trips (#40 + #42)
            if ((n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION) && n.heat > 85 && !n.hot) { stressed = true; hotNet = n.net; }
            // generators: overloaded = stress; note any running
            if (n.kind == GridKind.DIESEL || n.kind == GridKind.TURBINE) {
                TileGrid g = tileAt(p);
                if (g != null && g.running) genRunning = true;
                if (n.gcb && n.genLoad > 1.0) stressed = true;
            }
        }
        // 2) load shedding: low frequency or an overloaded generator -> open the heaviest REMOTE breaker on that network
        GridData.Net worst = null;
        for (GridData.Net net : d.nets()) {
            if (net.level == null || !net.level.ac || !net.problem.isEmpty() || net.utility) continue;
            if (net.hzPu < 0.985) { stressed = true; worst = net; }
        }
        if (pv("shed") == 1) {
            if (stressed) {
                calmSince = now;
                if (now - lastShed >= 40) {
                    // priority order (#40): non-essential first (heaviest of them first), then essential; critical never
                    BlockPos best = null; double amps = -1; int bestPrio = -1;
                    for (java.util.Map.Entry<Long, GridData.Node> e : d.nodes().entrySet()) {
                        GridData.Node n = e.getValue();
                        if (n.kind != GridKind.BREAKER || n.breaker != 0 || shedList.contains(e.getKey())) continue;
                        int prio = n.get("prio", 2);
                        if (prio == 0) continue;                                               // critical: signalling, control, safety
                        if (worst != null && n.net != worst.id) continue;
                        if (worst == null && hotNet >= 0 && n.net != hotNet) continue;
                        TileGrid g = tileAt(BlockPos.fromLong(e.getKey()));
                        if (g == null || g.pv("lr") != 1) continue;
                        if (prio > bestPrio || prio == bestPrio && n.amps > amps) { amps = n.amps; bestPrio = prio; best = BlockPos.fromLong(e.getKey()); }
                    }
                    if (best != null) {
                        String r = tileAt(best).remote("open", "load shedding");
                        if (r.startsWith("ok")) { shedList.push(best.toLong()); autoLog("SHED " + (bestPrio == 2 ? "non-essential" : "ESSENTIAL") + " load: opened " + at(best) + String.format(java.util.Locale.ROOT, " (%.0f A)", amps));
                            History.log(world, best, "SHED", (bestPrio == 2 ? "non-essential" : "essential") + String.format(java.util.Locale.ROOT, " load shed by the Auto Controller (%.0f A)", amps)); }
                        lastShed = now;
                    }
                }
            } else if (!shedList.isEmpty() && now - calmSince > 600 && now - lastShed > 200 && headroom(d)) {   // 30 s steady + spare capacity: restore one at a time, essential first
                BlockPos p = BlockPos.fromLong(shedList.pop());
                TileGrid g = tileAt(p);
                String r = g == null ? "unloaded" : g.remote("close", "load restore");
                autoLog(r.startsWith("ok") ? "restored load: closed " + at(p) : "restore " + at(p) + " refused: " + r);
                lastShed = now;
            }
        }
        // 3) standby generator start: a dead / sagging network -> crank the gensets (MAN mode) and sync them on
        if (pv("gen") == 1) {
            boolean need = stressed;
            for (GridData.Net net : d.nets()) if (net.problem.startsWith("No energy")) need = true;
            for (java.util.Map.Entry<Long, GridData.Node> e : d.nodes().entrySet()) {
                GridData.Node n = e.getValue();
                if (n.kind != GridKind.DIESEL && n.kind != GridKind.TURBINE) continue;
                BlockPos p = BlockPos.fromLong(e.getKey());
                TileGrid g = tileAt(p);
                if (g == null || g.pv("mode") == 0) continue;
                if (need && !g.running) {
                    String r = g.remote("start", "standby start");
                    if (r.startsWith("ok")) { genDone++; autoLog("standby start: " + n.kind.label + " " + at(p)); }
                } else if (g.running && !n.gcb && g.pv("mode") == 1) {
                    String r = g.remote("gcbclose", "standby start");
                    if (r.startsWith("ok")) autoLog("generator " + at(p) + " synchronised and on the bus");
                }
            }
        }
    }

    /** room to pick load back up: no generator above 80 %, no island below 59.8 Hz, no transformer above 70 °C rise */
    private static boolean headroom(GridData d) {
        for (GridData.Node n : d.nodes().values()) {
            if ((n.kind == GridKind.DIESEL || n.kind == GridKind.TURBINE) && n.feeding() && (n.genLoad > 0.8 || n.speed < 0.997)) return false;
            if ((n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION) && n.heat > 70) return false;
        }
        return true;
    }

    // ---- solar tracking -------------------------------------------------------------------------------------------
    /** where the sun is: degrees from overhead, negative = east (morning), positive = west (afternoon); |x| > 90 = night */
    public static double sunAngle(net.minecraft.world.World w, float pt) {
        double a = w.getCelestialAngle(pt) * 360.0;
        return a > 180 ? a - 360 : a;
    }

    /** the tracker deck's tilt about its north-south axis (degrees, same sign as the sun) */
    public double solarTilt(net.minecraft.world.World w, float pt) {
        if (pv("track") != 1 || pv("stow") == 1 || pv("dc") == 0 || w.isThundering()) return 0;
        double sd = sunAngle(w, pt);
        if (Math.abs(sd) >= 90) return 0;                              // night: back to flat
        return Math.max(-60, Math.min(60, sd));                        // ±60° drive limit, like a real single-axis tracker
    }
}
