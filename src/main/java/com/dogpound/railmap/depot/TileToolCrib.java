package com.dogpound.railmap.depot;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import java.util.LinkedList;

/**
 * Tool Crib: the depot's caged tool store. A pegboard of eight hooks shows the tools hung on it, four drawers and a
 * tall cabinet are real storage, and the cage key locks them to whoever placed the crib. The sign-out register logs
 * who took (SIGN OUT) or brought back (SIGN IN) the tool in their hand; the last four entries show on its display.
 */
public class TileToolCrib extends TileFurniture {
    private final LinkedList<String> register = new LinkedList<>();

    public TileToolCrib() {
        super(new String[]{"t1", "t2", "t3", "t4", "cab"},
                new String[]{"Tool drawer", "Tool drawer", "Tool drawer", "Tool drawer", "Tool cabinet"},
                new int[]{9, 9, 9, 9, 27}, 8);
    }

    @Override public String wiredName() { return "Tool crib"; }
    @Override public String gearState() { return (ctl.get("k_cage") == 1 ? "cage locked" : "cage open") + (register.isEmpty() ? "" : " · " + register.getFirst()); }
    @Override public double[][] shelfSpots() { return SPOTS; }
    static double[][] SPOTS = new double[8][];
    static { for (int i = 0; i < 8; i++) SPOTS[i] = new double[]{-12.5 + (i % 4) * 3.3, 15.2 - (i / 4) * 5.0, 14.4}; }

    @Override protected boolean mechanical(String id) { return id.startsWith("k_"); }

    @Override
    protected String refuse(int k, EntityPlayer who) {
        if (ctl.get("k_cage") == 1 && placer != null && !placer.equals(who.getUniqueID())) return "§cThe tool crib cage is locked (" + placerName + " has the key)";
        return null;
    }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        if (who != null && placer != null && !placer.equals(who.getUniqueID())) return "§cOnly " + placerName + " has the cage key";
        ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
        return ctl.get(id) == 1 ? "§eCage locked" : "§aCage open";
    }

    private long testUntil;

    /** LAMP TEST lights every lamp and segment for 3 s */
    @Override public int controlValue(String id) { return term.powered() && ctl.get("lamptest") == 1 && !mechanical(id) ? Math.max(1, ctl.get(id)) : super.controlValue(id); }
    @Override public void update() { super.update(); if (!world.isRemote && testUntil > 0 && world.getTotalWorldTime() > testUntil) { testUntil = 0; ctl.set("lamptest", 0); controlsChanged(); } }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (id.equals("test")) { testUntil = world.getTotalWorldTime() + 60; ctl.set("lamptest", 1); return "§7Lamp test"; }
        if (!id.equals("out") && !id.equals("in")) return null;
        if (who == null) return "§7Sign the register in person";
        ItemStack h = who.getHeldItemMainhand();
        if (h.isEmpty()) return "§7Hold the tool you are signing " + (id.equals("out") ? "out" : "in");
        String e = DepotClock.hhmm(world) + " " + (id.equals("out") ? "OUT " : "IN ") + who.getName() + " " + h.getDisplayName();
        register.addFirst(e);
        while (register.size() > 40) register.removeLast();
        markDirty();
        return "§aSigned " + (id.equals("out") ? "out" : "in") + ": " + h.getDisplayName();
    }

    @Override
    protected boolean refresh() {
        boolean ch = StationControls.put(ctl, "pwr", true) | StationControls.put(ctl, "cagel", ctl.get("k_cage") == 1);
        for (int i = 0; i < 4; i++) ch |= StationControls.text(ctl, "r" + (i + 1), i < register.size() ? cut(register.get(i).toUpperCase(java.util.Locale.ROOT), 30) : "");
        int out = 0;
        for (String s : register) if (s.contains(" OUT ")) out++; else if (s.contains(" IN ")) out--;
        ch |= StationControls.put(ctl, "nout", Math.max(0, out));
        return ch;
    }

    @Override protected void writeExtra(NBTTagCompound t) { NBTTagList l = new NBTTagList(); for (String s : register) l.appendTag(new NBTTagString(s)); t.setTag("reg", l); }
    @Override protected void readExtra(NBTTagCompound t) { register.clear(); NBTTagList l = t.getTagList("reg", Constants.NBT.TAG_STRING); for (int i = 0; i < l.tagCount(); i++) register.add(l.getStringTagAt(i)); }
}
