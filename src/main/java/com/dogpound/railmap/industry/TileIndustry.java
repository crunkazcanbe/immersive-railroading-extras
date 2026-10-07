package com.dogpound.railmap.industry;

import com.dogpound.railmap.settings.ISettingsHolder;
import com.dogpound.railmap.settings.Setting;
import com.dogpound.railmap.settings.SettingsStore;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One industry: a stockpile (slots 0-8 take deliveries, 9-17 hold what it makes) plus a production
 * cycle. Loading Silos / Unloading Pits, hoppers and any mod's pipes reach the stockpile through the
 * normal item capability: they can only put in what the industry uses and only take out what it makes.
 */
public class TileIndustry extends TileEntity implements ITickable, ISettingsHolder {
    private IndustryKind kind = IndustryKind.COAL_MINE;
    private final SettingsStore cfg = new SettingsStore(this);
    private final ItemStackHandler stock = new ItemStackHandler(18) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };
    private long fe, produced, consumed, cycles, prosperity;
    private int progress;
    private String status = "starting up";

    public TileIndustry() { }
    public TileIndustry(IndustryKind k) { kind = k; }

    public IndustryKind kind() { return kind; }

    // ---- item specs "modid:item:meta:count" ------------------------------------------------------
    static ItemStack spec(String s) {
        String[] p = s.split(":");
        Item it = Item.REGISTRY.getObject(new ResourceLocation(p[0], p[1]));
        return it == null ? ItemStack.EMPTY : new ItemStack(it, Integer.parseInt(p[3]), Integer.parseInt(p[2]));
    }

    private boolean accepts(ItemStack s) {
        for (String in : kind.in) { ItemStack w = spec(in); if (!w.isEmpty() && w.isItemEqual(s)) return true; }
        return false;
    }

    private int countOf(ItemStack w, int from, int to) {
        int n = 0;
        for (int i = from; i < to; i++) if (stock.getStackInSlot(i).isItemEqual(w)) n += stock.getStackInSlot(i).getCount();
        return n;
    }

    private void take(ItemStack w, int n, int from, int to) {
        for (int i = from; i < to && n > 0; i++) {
            if (!stock.getStackInSlot(i).isItemEqual(w)) continue;
            n -= stock.extractItem(i, n, false).getCount();
        }
    }

    /** put into the output slots; false if it doesn't all fit (nothing is put then) */
    private boolean give(List<ItemStack> outs) {
        for (ItemStack o : outs) {
            ItemStack rest = o.copy();
            for (int i = 9; i < 18 && !rest.isEmpty(); i++) rest = stock.insertItem(i, rest, true);
            if (!rest.isEmpty()) return false;
        }
        for (ItemStack o : outs) {
            ItemStack rest = o.copy();
            for (int i = 9; i < 18 && !rest.isEmpty(); i++) rest = stock.insertItem(i, rest, false);
        }
        return true;
    }

    // ---- production -------------------------------------------------------------------------------
    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (kind == IndustryKind.POWER_STATION) pushPower();
        if (!cfg.bool("on")) { status = "switched off"; return; }
        double speed = cfg.num("speed") / 100.0;
        if (kind == IndustryKind.FARM) speed *= world.isDaytime() ? 1.5 : 0.4;
        if (++progress < kind.cycle * 20 / Math.max(0.1, speed)) return;
        progress = 0;
        cycle();
        markDirty();
    }

    private void cycle() {
        int outCount = 0;
        for (int i = 9; i < 18; i++) outCount += stock.getStackInSlot(i).getCount();
        if (!kind.out.equals(new String[0]) && kind.out.length > 0 && outCount >= cfg.num("cap")) { status = "stockpile full - waiting for a train to collect"; return; }
        // inputs
        List<ItemStack> needs = new ArrayList<>();
        for (String s : kind.in) needs.add(spec(s));
        if (kind.anyInput()) {
            long value = 0;
            for (ItemStack w : needs) {
                int have = countOf(w, 0, 9);
                if (have < w.getCount()) continue;
                take(w, w.getCount(), 0, 9);
                consumed += w.getCount();
                value += w.getCount() * 3L;
            }
            if (value == 0) { prosperity = Math.max(0, prosperity - 1); status = "no goods delivered - the town is waiting"; return; }
            prosperity += value;
            cycles++;
            status = "supplied - town population " + population();
            return;
        }
        for (ItemStack w : needs) if (countOf(w, 0, 9) < w.getCount()) { status = "waiting for " + w.getCount() + " " + w.getDisplayName(); return; }
        if (kind.needsFE > 0 && cfg.bool("power") && fe < kind.needsFE) { status = "no power - wire it to the grid or any mod's cables (" + kind.needsFE + " FE per cycle)"; return; }
        List<ItemStack> outs = new ArrayList<>();
        for (String s : kind.out) outs.add(spec(s));
        if (!outs.isEmpty() && !give(outs)) { status = "stockpile full - waiting for a train to collect"; return; }
        for (ItemStack w : needs) { take(w, w.getCount(), 0, 9); consumed += w.getCount(); }
        if (kind.needsFE > 0 && cfg.bool("power")) fe -= kind.needsFE;
        if (kind.makesFE > 0) fe = Math.min(200_000, fe + kind.makesFE);
        for (ItemStack o : outs) produced += o.getCount();
        cycles++;
        status = kind.makesFE > 0 ? "burning coal - generating" : "working";
    }

    long population() { return 100 + prosperity / 10; }

    private void pushPower() {
        int rate = cfg.num("exportRate");
        for (EnumFacing f : EnumFacing.values()) {
            if (fe <= 0) return;
            TileEntity te = world.getTileEntity(pos.offset(f));
            if (te == null || !te.hasCapability(CapabilityEnergy.ENERGY, f.getOpposite())) continue;
            IEnergyStorage e = te.getCapability(CapabilityEnergy.ENERGY, f.getOpposite());
            if (e == null || !e.canReceive()) continue;
            fe -= e.receiveEnergy((int) Math.min(rate, fe), false);
        }
    }

    // ---- capabilities: the stockpile and the power connection ---------------------------------------
    private final IItemHandler face = new IItemHandler() {
        @Override public int getSlots() { return 18; }
        @Override public ItemStack getStackInSlot(int slot) { return stock.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack s, boolean sim) {
            if (slot >= 9 || !accepts(s)) return s;
            return stock.insertItem(slot, s, sim);
        }
        @Override public ItemStack extractItem(int slot, int n, boolean sim) {
            if (slot < 9) return ItemStack.EMPTY;
            return stock.extractItem(slot, n, sim);
        }
        @Override public int getSlotLimit(int slot) { return 64; }
    };

    private final IEnergyStorage energy = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean sim) {
            if (!canReceive()) return 0;
            int in = (int) Math.max(0, Math.min(max, 100_000 - fe));
            if (!sim) fe += in;
            return in;
        }
        @Override public int extractEnergy(int max, boolean sim) {
            if (!canExtract()) return 0;
            int out = (int) Math.min(max, fe);
            if (!sim) fe -= out;
            return out;
        }
        @Override public int getEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, fe); }
        @Override public int getMaxEnergyStored() { return kind.makesFE > 0 ? 200_000 : 100_000; }
        @Override public boolean canExtract() { return kind.makesFE > 0; }
        @Override public boolean canReceive() { return kind.needsFE > 0; }
    };

    @Override
    public boolean hasCapability(Capability<?> cap, EnumFacing f) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        if (cap == CapabilityEnergy.ENERGY && (kind.needsFE > 0 || kind.makesFE > 0)) return true;
        return super.hasCapability(cap, f);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> cap, EnumFacing f) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return (T) face;
        if (cap == CapabilityEnergy.ENERGY && (kind.needsFE > 0 || kind.makesFE > 0)) return (T) energy;
        return super.getCapability(cap, f);
    }

    public int comparator() {
        int n = 0;
        for (int i = 9; i < 18; i++) n += stock.getStackInSlot(i).getCount();
        return Math.min(15, n * 15 / Math.max(1, cfg.num("cap")));
    }

    public String statusLine() {
        return "§b" + label() + " §7· " + status + (kind.needsFE > 0 || kind.makesFE > 0 ? String.format(Locale.ROOT, " · %,d FE", fe) : "");
    }

    String label() {
        return new ItemStack(world.getBlockState(pos).getBlock()).getDisplayName();
    }

    // ---- Settings Console ------------------------------------------------------------------------------
    @Override public String settingsTitle() { return world == null ? "Industry" : label(); }

    @Override
    public List<Setting> settingDefs() {
        List<Setting> l = new ArrayList<>();
        l.add(Setting.bool("Production", "on", "Working", "", true));
        l.add(Setting.num("Production", "speed", "Production rate", "", 100, 10, 400, 10, "%"));
        if (kind.out.length > 0)
            l.add(Setting.num("Production", "cap", "Stop when the stockpile holds", "Keeps the yard from overflowing until a train collects", 384, 16, 576, 16, "items"));
        if (kind.needsFE > 0)
            l.add(Setting.bool("Production", "power", "Needs electricity", "Realistic: the works stop without " + kind.needsFE + " FE per cycle from the grid / any mod's cables", true));
        if (kind.makesFE > 0)
            l.add(Setting.num("Production", "exportRate", "Send out per tick", "Into the Grid Intake or any mod's cables touching it", 4000, 100, 100_000, 100, "FE/t"));
        if (world != null && !world.isRemote) {
            l.add(Setting.info("Status", "Now", status));
            l.add(Setting.info("Status", "Needs", kind.in.length == 0 ? "nothing - it's a primary producer" : String.join(", ", names(kind.in))));
            l.add(Setting.info("Status", "Makes", kind.makesFE > 0 ? kind.makesFE + " FE per coal" : kind.out.length == 0 ? "a bigger, happier town" : String.join(", ", names(kind.out))));
            for (Map.Entry<String, Integer> e : stockLines().entrySet()) l.add(Setting.info("Stockpile", e.getKey(), String.valueOf(e.getValue())));
            l.add(Setting.info("Totals", "Produced / consumed", produced + " / " + consumed));
            if (kind.needsFE > 0 || kind.makesFE > 0) l.add(Setting.info("Totals", "Energy stored", String.format(Locale.ROOT, "%,d FE", fe)));
            if (kind == IndustryKind.TOWN_MARKET) l.add(Setting.info("Totals", "Population", String.valueOf(population())));
        }
        return l;
    }

    private static List<String> names(String[] specs) {
        List<String> out = new ArrayList<>();
        for (String s : specs) { ItemStack w = spec(s); out.add(w.getCount() + " " + w.getDisplayName()); }
        return out;
    }

    private Map<String, Integer> stockLines() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < 18; i++) {
            ItemStack s = stock.getStackInSlot(i);
            if (!s.isEmpty()) m.merge(s.getDisplayName(), s.getCount(), Integer::sum);
        }
        if (m.isEmpty()) m.put("(empty)", 0);
        return m;
    }

    @Override public SettingsStore settings() { return cfg; }
    @Override public void onSettingsChanged(String key) { markDirty(); }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setString("kind", kind.name());
        t.setTag("stock", stock.serializeNBT());
        t.setLong("fe", fe); t.setLong("produced", produced); t.setLong("consumed", consumed);
        t.setLong("cycles", cycles); t.setLong("prosperity", prosperity);
        t.setInteger("progress", progress); t.setString("status", status);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        try { kind = IndustryKind.valueOf(t.getString("kind")); } catch (IllegalArgumentException ignored) { }
        stock.deserializeNBT(t.getCompoundTag("stock"));
        fe = t.getLong("fe"); produced = t.getLong("produced"); consumed = t.getLong("consumed");
        cycles = t.getLong("cycles"); prosperity = t.getLong("prosperity");
        progress = t.getInteger("progress"); status = t.getString("status");
        cfg.read(t);
    }
}
