package com.dogpound.railmap.depot;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Depot Stores (her ChatGPT list #53): long-span racking with six real bins (brakes and hoses, bearings and couplers,
 * motors and power kits, electrics, wheelsets and bogies, consumables). The stock board counts what is really in the
 * bins - every spare type, with a LOW lamp under the reorder level set on the MIN selector - and job cards within 32
 * blocks draw their parts from here. STOCKTAKE prints the count to chat and the event recorder.
 */
public class TileDepotStores extends TileFurniture implements com.dogpound.railmap.industry.IndustryLink.Endpoint {
    static final int[] MINS = {1, 2, 4, 8};

    public TileDepotStores() {
        super(new String[]{"b1", "b2", "b3", "b4", "b5", "b6"},
                new String[]{"Bin: brakes + hoses", "Bin: bearings + couplers", "Bin: motors + power kits", "Bin: electrics", "Bin: wheelsets + bogies", "Bin: consumables"},
                new int[]{27, 27, 27, 27, 27, 27}, 1);
    }

    public int count(ItemSpare.Spare p) {
        int n = 0;
        for (int k = 0; k < 6; k++) n += Stores.count(storage(k), p);
        return n;
    }

    public int take(ItemSpare.Spare p, int want) {
        int got = 0;
        for (int k = 0; k < 6 && got < want; k++) got += Stores.take(storage(k), p, want - got);
        if (got > 0) controlsChanged();
        return got;
    }

    @Override public String wiredName() { return "Depot stores"; }

    // ---- her list #91: the stores order spares by train when a bin falls under the MIN reorder level -------------------
    @Override public String endpointName() { return "Depot stores"; }
    @Override public java.util.List<net.minecraft.item.ItemStack> makes() { return java.util.Collections.emptyList(); }
    @Override public boolean accepts(net.minecraft.item.ItemStack s) { return s.getItem() instanceof ItemSpare && ItemSpare.of(s) != ItemSpare.Spare.BOGIE_BAD; }

    /** each spare under the MIN level: enough to bring it to twice the level (defective bogies are never ordered) */
    @Override
    public java.util.List<net.minecraft.item.ItemStack> wants() {
        java.util.List<net.minecraft.item.ItemStack> l = new java.util.ArrayList<>();
        for (ItemSpare.Spare p : ItemSpare.Spare.values()) {
            if (p == ItemSpare.Spare.BOGIE_BAD || !p.rolling()) continue;   // infrastructure spares: purchase orders (asset.Procurement)
            int n = count(p);
            if (n < min()) l.add(ItemSpare.stack(p, min() * 2 - n));
        }
        return l;
    }

    @Override public String endpointState() { int low = wants().size(); return low == 0 ? "STOCKED" : low + " LOW"; }
    @Override public void onLoad() { com.dogpound.railmap.industry.IndustryLink.register(this); }
    @Override public void invalidate() { super.invalidate(); com.dogpound.railmap.industry.IndustryLink.forget(this); }
    @Override public String gearState() { int low = 0; for (ItemSpare.Spare p : ItemSpare.Spare.values()) if (p.rolling() && count(p) < min()) low++; return low + " spares below the reorder level"; }
    @Override protected boolean mechanical(String id) { return id.startsWith("s_"); }

    int min() { return MINS[Math.floorMod(ctl.get("s_min"), MINS.length)]; }

    private long testUntil;

    /** LAMP TEST lights every lamp and segment for 3 s */
    @Override public int controlValue(String id) { return term.powered() && ctl.get("lamptest") == 1 && !mechanical(id) ? Math.max(1, ctl.get(id)) : super.controlValue(id); }
    @Override public void update() { super.update(); if (!world.isRemote && testUntil > 0 && world.getTotalWorldTime() > testUntil) { testUntil = 0; ctl.set("lamptest", 0); controlsChanged(); } }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (id.equals("test")) { testUntil = world.getTotalWorldTime() + 60; ctl.set("lamptest", 1); return "§7Lamp test"; }
        if (!id.equals("count")) return null;
        StringBuilder b = new StringBuilder("Stocktake " + DepotClock.stamp(world) + ":");
        for (ItemSpare.Spare p : ItemSpare.Spare.values()) b.append(' ').append(p.label).append(' ').append(count(p)).append(count(p) < min() ? " (LOW)" : "").append(';');
        Recorder.event(world, "Depot stores " + b);
        if (who != null) who.sendMessage(new net.minecraft.util.text.TextComponentString("§7" + b));
        return "§aStocktake done";
    }

    @Override
    protected boolean refresh() {
        boolean ch = StationControls.put(ctl, "pwr", true);
        int low = 0;
        for (ItemSpare.Spare p : ItemSpare.Spare.values()) {
            int n = count(p);
            ch |= StationControls.put(ctl, "c" + p.ordinal(), n);
            ch |= StationControls.put(ctl, "l" + p.ordinal(), n < min());
            if (n < min() && p.rolling()) low++;
        }
        ch |= StationControls.put(ctl, "nlow", low);
        return ch;
    }
}
