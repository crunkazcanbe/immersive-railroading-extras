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
public class TilePowerPost extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder {
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
        if (world.isRemote || world.getTotalWorldTime() % 20 != 0) return;
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
            world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_LIGHTNING_THUNDER, net.minecraft.util.SoundCategory.BLOCKS, 0.6f, 1.6f);
        }
        if (kind == Kind.CHARGER) com.dogpound.railmap.server.Electric.chargeNear(world, pos, this);
    }

    /** Energised: breaker closed, not tripped, no redstone, and (if required) energy in store. */
    public boolean live() {
        if (tripped) return false;
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
        String fe = needsEnergy() ? String.format(" · %,d / %,d FE", energy.getEnergyStored(), energy.getMaxEnergyStored()) : " · free power (config)";
        String nm = cfg.text("name").trim();
        return (kind == Kind.SUBSTATION ? "§d⚡ Substation§7 · " : "§d🔋 Charging Station§7 · ") + (nm.isEmpty() ? "" : nm + " · ") + cfg.text("voltage") + " · " + state + "§7" + fe;
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
        cfg.read(nbt);
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
        cfg.write(nbt);
        return nbt;
    }

    public BlockPos where() {
        return pos;
    }
}
