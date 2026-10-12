package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMapConfig;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Railway power equipment (list §10/§11): a {@link Kind#SUBSTATION} energises every contact wire
 * and third rail within {@code substationRange}; a {@link Kind#CHARGER} charges parked electric
 * and battery trains beside it. Both store Forge Energy so any generator mod can feed them.
 * <p>
 * Breaker: a redstone signal (or a right-click) opens it — that section goes dead, a planned
 * outage. Thunderstorms can trip a substation too (weather, §16); right-click resets it.
 */
public class TilePowerPost extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder, com.dogpound.railmap.wire.IWiredGear, com.dogpound.railmap.controls.IControlled {
    // ---- HV in (requested feature): a grid cable or machine touching
    // the post feeds its store from the real grid (600 V .. 69 kV), drawn as load on that network; Forge Energy still works.
    private String hvWhat = "no grid cable";
    private long hvLast;

    private void feedFromGrid() {
        hvWhat = "no grid cable";
        com.dogpound.railmap.grid.GridData d = com.dogpound.railmap.grid.GridData.get(world);
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos nb = pos.offset(f);
            if (d.node(nb) == null) continue;
            com.dogpound.railmap.grid.GridData.Net n = d.netAt(nb, f.getOpposite());
            if (n == null) continue;
            if (!n.problem.isEmpty()) { hvWhat = "grid supply dead: " + n.problem; continue; }
            if (n.level == null || n.level.volts < 600) { hvWhat = "grid supply too low: " + (n.level == null ? "no voltage" : n.level.label) + " (needs 600 V - 69 kV)"; continue; }
            if (n.level.volts > 69000) { hvWhat = "grid supply too high: " + n.level.label; continue; }
            int want = Math.min(100_000, energy.getMaxEnergyStored() - energy.getEnergyStored());
            long got = want <= 0 ? 0 : d.draw(n, want);
            if (got > 0) energy.receiveEnergy((int) got, false);
            hvLast = got;
            hvWhat = n.level.label + " from the grid" + (got > 0 ? String.format(java.util.Locale.ROOT, " · %,d FE/s", got) : " · store full");
            return;
        }
    }

    // ---- 12 V terminal: the substation's protection + control panel (remote CLOSE / TRIP / RESET from the console)
    private final com.dogpound.railmap.wire.Terminal term = new com.dogpound.railmap.wire.Terminal();
    @Override public com.dogpound.railmap.wire.Terminal term() { return term; }
    @Override public void gearChanged() { markDirty(); if (world != null && !world.isRemote) { net.minecraft.block.state.IBlockState gs = world.getBlockState(pos); world.notifyBlockUpdate(pos, gs, gs, 3); } }
    @Override public String wiredName() { String nm = cfg.text("name").trim(); return (kind == Kind.SUBSTATION ? "Substation" : "Charging station") + (nm.isEmpty() ? "" : " " + nm); }
    @Override public String gearState() { return (tripped ? "TRIPPED" : open ? "breaker OPEN" : live() ? "LIVE" : "no energy") + (term.powered() ? "" : " · control panel dead (no 12 V)"); }
    @Override public int wiredLamp() { return term.burnt || !term.powered() || tripped ? 3 : live() ? 1 : 2; }
    @Override public void gearInfo(java.util.Map<String, Object> m) { m.put("system", cfg.text("voltage")); m.put("storedFE", energy.getEnergyStored()); m.put("hvSupply", hvWhat); m.put("deliveredFE", drawn); m.put("stormTrips", trips); }
    // ---- working controls on the model (requested feature). Substation: FEEDER CB panel (CLOSE / TRIP / RESET, breaker + trip + HV-in lamps, line volts,
    // feeder amps, energy store, LOCAL / REMOTE selector) and the transformer's oil thermometer. Charger: START / STOP,
    // a latching EMERGENCY STOP, READY / CHARGING / FAULT lamps, the status display and the beacon. The same controls
    // are the console's WIRED page (ids close / trip / reset as before, so Control Buttons keep working).
    private final com.dogpound.railmap.controls.ControlState ctl = new com.dogpound.railmap.controls.ControlState();
    private long drawnBefore, fePerSec;
    private float oilC = 25;
    @Override public com.dogpound.railmap.controls.ControlState controls() { return ctl; }
    @Override public void controlsChanged() { gearChanged(); }
    @Override public com.dogpound.railmap.grid.Panel wiredPanel() { return layout().all; }
    @Override public int wiredState(String id) { return controlValue(id); }
    @Override public String wiredPress(String id, String who) { return com.dogpound.railmap.controls.IControlled.operate(this, id, null); }

    /** what the train draw is costing the feeder right now, kW (FE/s x W per FE/s) */
    private double kw() { return fePerSec * com.dogpound.railmap.grid.Elec.W_PER_FE_T / 20.0 / 1000.0; }

    /** the system's nominal volts from the Settings ("1500 V DC", "25 kV AC") */
    private double systemVolts() {
        String v = cfg.text("voltage");
        try {
            double n = Double.parseDouble(v.replaceAll("[^0-9.].*$", ""));
            return v.contains("kV") ? n * 1000 : n;
        } catch (NumberFormatException e) { return 1500; }
    }

    private boolean charging() { return kind == Kind.CHARGER && fePerSec > 0; }
    private boolean estop() { return ctl.get("estop") == 1; }

    /** server, once a second: the meters, lamps and display read the real state; clients get it with the tile */
    private void readings() {
        boolean p = term.powered();
        Map<String, Integer> v = new java.util.HashMap<>();
        Map<String, String> tx = new java.util.HashMap<>();
        double load = Math.min(1.5, kw() / 1500.0);                          // the 1.5 MVA rating plate
        oilC += (float) ((25 + 60 * load * load - oilC) * 0.02);              // oil warms and cools slowly
        if (kind == Kind.SUBSTATION) {
            v.put("volts", live() ? (int) Math.round(100 - 8 * Math.min(1, load)) : 0);   // regulation: sags under load
            v.put("amps", live() ? (int) Math.round(kw() * 1000 / systemVolts()) : 0);
            v.put("temp", Math.round(oilC));
            v.put("closed", p && !open && !tripped ? 1 : 0);
            v.put("opened", p && (open || tripped) ? 1 : 0);
            v.put("tripped", p && tripped ? 1 : 0);
            v.put("grid", p && hvWhat.contains("from the grid") ? 1 : 0);
            v.put("store", p ? (int) (100L * energy.getEnergyStored() / Math.max(1, energy.getMaxEnergyStored())) : 0);
        } else {
            boolean fault = estop() || tripped || !open && !live();
            v.put("ready", p && live() && !charging() ? 1 : 0);
            v.put("charging", p && charging() ? 1 : 0);
            v.put("fault", p && fault ? 1 : 0);
            v.put("beacon", p && charging() ? 1 : 0);
            int pct = (int) (100L * energy.getEnergyStored() / Math.max(1, energy.getMaxEnergyStored()));
            tx.put("disp1", !p ? "" : estop() ? "EMERGENCY STOP" : open ? "STOPPED" : !live() ? (needsEnergy() && energy.getEnergyStored() <= 0 ? "NO SUPPLY" : "OFF (REDSTONE)")
                    : charging() ? "CHARGING" : "READY");
            tx.put("disp2", !p ? "" : charging() ? String.format(java.util.Locale.ROOT, "%.0f KW", kw()) : needsEnergy() ? "STORE " + pct + "%" : "GRID FREE");
        }
        boolean ch = false;
        for (Map.Entry<String, Integer> e : v.entrySet()) if (ctl.get(e.getKey()) != e.getValue()) { ctl.set(e.getKey(), e.getValue()); ch = true; }
        for (Map.Entry<String, String> e : tx.entrySet()) if (!ctl.text(e.getKey()).equals(e.getValue())) { ctl.texts.put(e.getKey(), e.getValue()); ch = true; }
        if (ch) gearChanged();
    }

    @Override
    public String controlPress(String id, net.minecraft.entity.player.EntityPlayer who) {
        boolean remote = who == null, local = ctl.get("mode") == 1;
        switch (id) {
            case "mode":
                if (remote) return "§7The LOCAL / REMOTE selector is turned at the cubicle";
                ctl.set("mode", local ? 0 : 1);
                return local ? "§eControl: REMOTE - the console works the breaker" : "§eControl: LOCAL - the cubicle buttons work, the console is locked out";
            case "estop":                                                       // hard-wired: works with no 12 V, like real ones
                if (remote) return "§7The emergency stop is at the charger";
                if (estop()) { ctl.set("estop", 0); return "§aEmergency stop released - press START to charge again"; }
                ctl.set("estop", 1);
                open = true;
                cfg.set("closed", "false");
                return "§cEMERGENCY STOP - charger off, latched (click it again to twist it out)";
            case "close": case "trip": case "reset": break;
            default: return null;                                              // lamps, meters, displays: just named
        }
        if (!term.powered()) return "§cThe control panel is dead - no 12 V";
        if (kind == Kind.SUBSTATION) {
            if (remote && local) return "§cSelector at LOCAL - the console is locked out";
            if (!remote && !local && !id.equals("trip")) return "§eSelector at REMOTE - turn it to LOCAL to work the breaker here (TRIP always works)";
        }
        switch (id) {
            case "close":
                if (estop()) return "§cEMERGENCY STOP pressed - twist it out first";
                if (tripped) return "§eTripped - RESET first";
                open = false;
                break;
            case "trip": open = true; break;
            default:
                if (!tripped) return "§7Nothing to reset";
                tripped = false; tripTimer = 0; break;
        }
        cfg.set("closed", Boolean.toString(!open));
        readings();
        return kind == Kind.CHARGER ? (open ? "§cCharger STOPPED" : "§aCharger STARTED") : open ? "§cBreaker OPEN · section dead" : tripped ? "§eStill tripped" : "§aBreaker CLOSED · section live";
    }

    @Override
    public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }

    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private long drawn, trips;
    private int tripTimer;

    @Override public String settingsTitle() { return kind == Kind.SUBSTATION ? "Substation" : "Charging Station"; }
    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }
    @Override public void onSettingsChanged(String key) { if (key.equals("closed")) open = !cfg.bool("closed"); markDirty(); }

    public int range() { return cfg.num("range"); }
    public int reach() { return cfg.num("reach"); }
    public double rateMul() { return cfg.num("rate") / 100.0; }
    private boolean needsEnergy() { String e = cfg.text("energy"); return "Yes".equals(e) || "Config".equals(e) && RailMapConfig.powerNeedsEnergy; }

    @Override
    public java.util.List<com.dogpound.railmap.settings.Setting> settingDefs() {
        java.util.List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        String g = "General", sup = kind == Kind.SUBSTATION ? "Supply" : "Charging", pr = "Protection", rs = "Redstone", st = "Status";
        l.add(com.dogpound.railmap.settings.Setting.text(g, "name", "Name", "Shown on the board and in messages", "", 32));
        l.add(com.dogpound.railmap.settings.Setting.bool(g, "closed", "Breaker closed", "Open it for a planned outage (that section goes dead)", true));
        l.add(com.dogpound.railmap.settings.Setting.choice(g, "voltage", "System", "Which electrification this feeds (shown on readouts)", "25 kV AC", "600 V DC (tram)", "750 V DC (third rail)", "1500 V DC", "3 kV DC", "15 kV AC", "25 kV AC"));
        l.add(com.dogpound.railmap.settings.Setting.choice(g, "energy", "Needs Forge Energy", "Config = follow the mod config", "Config", "Config", "Yes", "No"));
        if (kind == Kind.SUBSTATION) {
            l.add(com.dogpound.railmap.settings.Setting.num(sup, "range", "Feeds wire within", "Contact wire / third rail this close is live", RailMapConfig.substationRange, 16, 4096, 16, "blocks"));
            l.add(com.dogpound.railmap.settings.Setting.num(sup, "rate", "Energy use", "Percent of the normal FE per running train", 100, 10, 500, 10, "%"));
        } else {
            l.add(com.dogpound.railmap.settings.Setting.num(sup, "reach", "Charges trains within", "", 6, 2, 32, 1, "blocks"));
            l.add(com.dogpound.railmap.settings.Setting.num(sup, "rate", "Charge speed", "Percent of the normal charge rate", 100, 10, 1000, 10, "%"));
            l.add(com.dogpound.railmap.settings.Setting.num(sup, "parked", "Counts as parked below", "", 1, 0, 30, 1, "km/h"));
        }
        l.add(com.dogpound.railmap.settings.Setting.choice(pr, "storm", "Storm trips", "Thunderstorms can trip the breaker", "Config", "Config", "Never", "Rare", "Often"));
        l.add(com.dogpound.railmap.settings.Setting.bool(pr, "autoReset", "Reset by itself after a storm", "", false));
        l.add(com.dogpound.railmap.settings.Setting.num(pr, "resetDelay", "Auto-reset after", "", 30, 5, 600, 5, "s"));
        l.add(com.dogpound.railmap.settings.Setting.choice(rs, "rsMode", "Redstone", "", "Power opens the breaker", "Power opens the breaker", "Power closes the breaker", "Ignore redstone"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "State", world == null ? "-" : net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(status())));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Energy stored", String.format(java.util.Locale.ROOT, "%,d / %,d FE", energy.getEnergyStored(), energy.getMaxEnergyStored())));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Energy delivered", String.format(java.util.Locale.ROOT, "%,d FE", drawn)));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Storm trips", String.valueOf(trips)));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "HV supply", hvWhat));
        com.dogpound.railmap.wire.IWiredGear.settings(l, st, this);
        return l;
    }
    public enum Kind { SUBSTATION, CHARGER }

    /** dimension → position → live substation tiles, for the train tick. */
    private static final Map<Integer, Map<Long, TilePowerPost>> REG = new ConcurrentHashMap<>();

    private final EnergyStorage energy = new EnergyStorage(1_000_000, 100_000, 100_000) {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            int r = super.receiveEnergy(maxReceive, simulate);
            if (r > 0 && !simulate) markDirty();
            return r;
        }
    };
    private Kind kind = Kind.SUBSTATION;
    /** Breaker opened by hand. */
    private boolean open;
    /** Tripped by a storm. */
    private boolean tripped;

    public TilePowerPost() {}

    public TilePowerPost(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static Collection<TilePowerPost> all(int dim) {
        Map<Long, TilePowerPost> m = REG.get(dim);
        return m == null ? java.util.Collections.emptyList() : m.values();
    }

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) REG.computeIfAbsent(world.provider.getDimension(), d -> new ConcurrentHashMap<>()).put(pos.toLong(), this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        unregister();
    }

    @Override
    public void onChunkUnload() {
        unregister();
    }

    private void unregister() {
        if (world == null) return;
        Map<Long, TilePowerPost> m = REG.get(world.provider.getDimension());
        if (m != null) m.remove(pos.toLong());
    }

    @Override
    public void update() {
        if (world.isRemote) return;
        if (term.tick(world, pos, wiredName())) gearChanged();
        if (world.getTotalWorldTime() % 20 != 0) return;
        feedFromGrid();
        fePerSec = drawn - drawnBefore;
        drawnBefore = drawn;
        readings();
        // storms (§16): about one trip per substation per 20 storm-minutes (Settings: Never / Rare / Often)
        String storm = cfg.text("storm");
        int odds = switch (storm) { case "Never" -> 0; case "Rare" -> 4800; case "Often" -> 300; default -> RailMapConfig.stormOutages ? 1200 : 0; };
        if (tripped && cfg.bool("autoReset") && ++tripTimer >= cfg.num("resetDelay")) { tripped = false; tripTimer = 0; markDirty(); }
        if (kind == Kind.SUBSTATION && odds > 0 && world.isThundering() && !tripped
                && world.rand.nextInt(odds) == 0 && world.canSeeSky(pos.up())) {
            tripped = true;
            tripTimer = 0;
            trips++;
            markDirty();
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.ARC_FLASH, net.minecraft.util.SoundCategory.BLOCKS, 0.6f, 1.0f);
        }
        if (kind == Kind.CHARGER) com.dogpound.railmap.server.Electric.chargeNear(world, pos, this);
    }

    /** Energised: breaker closed, not tripped, no redstone, and (if required) energy in store. */
    public boolean live() {
        if (tripped || estop()) return false;
        boolean powered = world.isBlockPowered(pos);
        boolean closed = switch (cfg.text("rsMode")) {
            case "Power closes the breaker" -> powered && !open;
            case "Ignore redstone" -> !open;
            default -> !open && !powered;
        };
        if (!closed) return false;
        return !needsEnergy() || energy.getEnergyStored() > 0;
    }

    /** Take energy for running trains; free when power isn't required. Returns true if supplied. */
    public boolean draw(int fe) {
        fe = (int) Math.round(fe * rateMul());
        drawn += fe;
        if (!needsEnergy()) return true;
        if (energy.extractEnergy(fe, true) < fe) return false;
        energy.extractEnergy(fe, false);
        markDirty();
        return true;
    }

    /** Right-click: reset a storm trip, else toggle the breaker. */
    public String click() {
        if (tripped) { tripped = false; markDirty(); return "§aBreaker reset after the storm"; }
        open = !open;
        cfg.set("closed", Boolean.toString(!open));
        markDirty();
        return open ? "§cBreaker OPEN · section dead" : "§aBreaker closed · section live";
    }

    public String status() {
        String state = tripped ? "§cTRIPPED by storm (right-click to reset)" : open ? "§cbreaker open" : world.isBlockPowered(pos) ? "§credstone: off" : live() ? "§alive" : "§6no energy";
        String hv = " · HV: " + hvWhat;
        String fe = needsEnergy() ? String.format(" · %,d / %,d FE", energy.getEnergyStored(), energy.getMaxEnergyStored()) : " · free power (config)";
        String nm = cfg.text("name").trim();
        return (kind == Kind.SUBSTATION ? "§d⚡ Substation§7 · " : "§d🔋 Charging Station§7 · ") + (nm.isEmpty() ? "" : nm + " · ") + cfg.text("voltage") + " · " + state + "§7" + fe + hv;
    }

    @Override
    public boolean hasCapability(Capability<?> cap, EnumFacing facing) {
        return cap == CapabilityEnergy.ENERGY || super.hasCapability(cap, facing);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> cap, EnumFacing facing) {
        return cap == CapabilityEnergy.ENERGY ? (T) energy : super.getCapability(cap, facing);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        kind = nbt.getByte("kind") == 1 ? Kind.CHARGER : Kind.SUBSTATION;
        open = nbt.getBoolean("open");
        tripped = nbt.getBoolean("tripped");
        drawn = nbt.getLong("drawn");
        trips = nbt.getLong("trips");
        term.read(nbt);
        cfg.read(nbt);
        ctl.read(nbt);
        drawnBefore = drawn;
        int e = nbt.getInteger("fe");
        energy.extractEnergy(Integer.MAX_VALUE, false);
        // EnergyStorage has no setter: refill by receive (bounded by maxReceive, so loop)
        while (e > 0) { int r = energy.receiveEnergy(e, false); if (r <= 0) break; e -= r; }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setByte("kind", (byte) kind.ordinal());
        nbt.setBoolean("open", open);
        nbt.setBoolean("tripped", tripped);
        nbt.setInteger("fe", energy.getEnergyStored());
        nbt.setLong("drawn", drawn);
        nbt.setLong("trips", trips);
        term.write(nbt);
        cfg.write(nbt);
        ctl.write(nbt);
        return nbt;
    }

    public BlockPos where() {
        return pos;
    }
}
