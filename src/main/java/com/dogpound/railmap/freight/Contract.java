package com.dogpound.railmap.freight;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * One freight contract (her ChatGPT list: "freight contracts from Pride Industry demand"): move `qty` of an item from a
 * producing industry to the one that needs it, before the deadline. Only cars billed to the contract count: a car is
 * billed when it is loaded with the contract's goods at the origin, and its load counts when it is unloaded at the
 * destination.
 */
public final class Contract {
    public enum State {
        OPEN("open"), ACCEPTED("in progress"), DONE("delivered"), EXPIRED("expired"), DECLINED("declined");
        public final String label;
        State(String label) { this.label = label; }
    }

    public int id;
    public String item = "";            // modid:item:meta
    public String itemName = "";
    public int qty, loaded, delivered;
    public BlockPos from = BlockPos.ORIGIN, to = BlockPos.ORIGIN;
    public String fromName = "", toName = "";
    public int distance;
    public long pay;                    // Pride coin cents, on time
    public long posted, deadline;       // world ticks
    public State state = State.OPEN;
    public UUID by;                     // who accepted it
    public String byName = "";
    public long paid;                   // what it actually paid
    public boolean collected;           // paid out to the player (else waiting at an office)
    /** an automatic order (her list #89 / #87 / #91): the buyer ran low and asked for it */
    public boolean order;
    /** the world tick the goods are ready at the origin (#93: a production batch); 0 = ready when posted */
    public long readyAt;
    /** billed cars: IR stock UUID -> items of this contract loaded into it, not yet delivered */
    public final Map<UUID, Integer> cars = new HashMap<>();

    public ItemStack stack() {
        if (fluid()) return ItemStack.EMPTY;
        String[] p = item.split(":");
        net.minecraft.item.Item it = net.minecraft.item.Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(p[0], p[1]));
        return it == null ? ItemStack.EMPTY : new ItemStack(it, 1, p.length > 2 ? Integer.parseInt(p[2]) : 0);
    }

    /** a fluid contract (her list #87: diesel by tank car): item is "fluid:<name>", qty is litres */
    public boolean fluid() { return item.startsWith("fluid:"); }
    public String fluidName() { return fluid() ? item.substring(6).split(":")[0] : ""; }
    public boolean matchesFluid(net.minecraftforge.fluids.FluidStack f) { return fluid() && f != null && f.getFluid() != null && f.getFluid().getName().equals(fluidName()); }

    public boolean matches(ItemStack s) {
        if (fluid()) return false;
        ItemStack w = stack();
        return !w.isEmpty() && w.isItemEqual(s);
    }

    /** pay now: full before the deadline, then 10 % less each in-game hour late, never under a quarter */
    public long payNow(long now) {
        if (now <= deadline) return pay;
        long hoursLate = (now - deadline) / 1000;
        return Math.max(pay / 4, pay - pay * hoursLate / 10);
    }

    public static String money(long cents) { return String.format(Locale.ROOT, "%,d.%02d", cents / 100, Math.abs(cents % 100)); }

    /** "1d 4h" / "late 3h" */
    public String timeLeft(long now) {
        long t = deadline - now;
        boolean late = t < 0;
        String s = span(Math.abs(t));
        if (!late && readyAt > now) return "batch " + span(readyAt - now) + ", due " + s;
        return late ? "LATE " + s : s;
    }

    private static String span(long t) {
        long h = t / 1000, d = h / 24;
        return d > 0 ? d + "d " + (h % 24) + "h" : h > 0 ? h + "h" : (t * 60 / 1000) + "m";
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("id", id); t.setString("item", item); t.setString("name", itemName);
        t.setInteger("qty", qty); t.setInteger("loaded", loaded); t.setInteger("delivered", delivered);
        t.setLong("from", from.toLong()); t.setLong("to", to.toLong()); t.setString("fromN", fromName); t.setString("toN", toName);
        t.setInteger("dist", distance); t.setLong("pay", pay); t.setLong("posted", posted); t.setLong("deadline", deadline);
        t.setString("state", state.name()); if (by != null) t.setUniqueId("by", by); t.setString("byN", byName);
        t.setLong("paid", paid); t.setBoolean("collected", collected);
        t.setBoolean("order", order); t.setLong("readyAt", readyAt);
        NBTTagCompound c = new NBTTagCompound();
        for (Map.Entry<UUID, Integer> e : cars.entrySet()) c.setInteger(e.getKey().toString(), e.getValue());
        t.setTag("cars", c);
        return t;
    }

    public static Contract read(NBTTagCompound t) {
        Contract k = new Contract();
        k.id = t.getInteger("id"); k.item = t.getString("item"); k.itemName = t.getString("name");
        k.qty = t.getInteger("qty"); k.loaded = t.getInteger("loaded"); k.delivered = t.getInteger("delivered");
        k.from = BlockPos.fromLong(t.getLong("from")); k.to = BlockPos.fromLong(t.getLong("to"));
        k.fromName = t.getString("fromN"); k.toName = t.getString("toN"); k.distance = t.getInteger("dist");
        k.pay = t.getLong("pay"); k.posted = t.getLong("posted"); k.deadline = t.getLong("deadline");
        try { k.state = State.valueOf(t.getString("state")); } catch (IllegalArgumentException ex) { k.state = State.OPEN; }
        if (t.hasUniqueId("by")) k.by = t.getUniqueId("by");
        k.byName = t.getString("byN"); k.paid = t.getLong("paid"); k.collected = t.getBoolean("collected");
        k.order = t.getBoolean("order"); k.readyAt = t.getLong("readyAt");
        NBTTagCompound c = t.getCompoundTag("cars");
        for (String key : c.getKeySet()) { try { k.cars.put(UUID.fromString(key), c.getInteger(key)); } catch (IllegalArgumentException ignored) { } }
        return k;
    }
}
