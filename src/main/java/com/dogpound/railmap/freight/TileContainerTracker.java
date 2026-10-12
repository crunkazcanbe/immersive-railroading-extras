package com.dogpound.railmap.freight;

import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.biz.BizData;
import com.dogpound.railmap.biz.Custody;
import com.dogpound.railmap.item.ItemContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Container Tracking Terminal (ChatGPT batch 3 #22): the yard clerk's terminal on the custody records every terminal,
 * port, crane, truck gate and washout plant keeps (Custody). Look a box up by putting it in the SCAN tray (SCAN), by
 * holding it (FIND), or page through the yard with PREV / NEXT under the FILTER (all, moving in the last day, flagged,
 * sealed, reefer, dirty / contaminated). The screens show its ISO 6346 number and whether the check digit is right,
 * status and seal, where it was last seen and by what (ship, quay, tractor, gate, crane, rail car, wash), who holds it,
 * its condition (clean, moisture, temperature, cold-chain quality) and its last handling event. PRINT gives you the
 * whole trace on paper.
 */
public class TileContainerTracker extends CargoTile {
    static final String[] FILTERS = {"ALL", "MOVING", "FLAGGED", "SEALED", "REEFER", "DIRTY"};
    private String sel = "";

    public TileContainerTracker() { super(new String[]{"scan"}, new String[]{"Scan tray"}, new int[]{1}); }

    @Override protected long load() { return 3; }                    // screens, scanner, printer
    /** the box this terminal has on its screens (a yard gantry's TRACKER button makes it a crane job), "" = none */
    public String selected() { return sel; }
    @Override public String wiredName() { return "Container tracking terminal"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : !mainsOk() ? mains.what : sel.isEmpty() ? "no box selected" : "showing " + sel; }

    List<Custody.Rec> list() {
        long now = world.getTotalWorldTime();
        int f = Math.floorMod(ctl.get("s_filter"), FILTERS.length);
        List<Custody.Rec> l = new ArrayList<>();
        for (Custody.Rec r : BizData.get(world).custody.recs.values()) {
            if (r.id.startsWith("K")) continue;                                   // contract consignments are on the console
            boolean in = switch (f) {
                case 1 -> now - r.dwellSince < 24000;
                case 2 -> r.status.equals("FLAGGED") || r.status.equals("REJECTED");
                case 3 -> r.seal > 0 && !r.sealBroken;
                case 4 -> r.reefer;
                case 5 -> r.clean < 60 || !r.contam.isEmpty() || "HAZ".equals(r.lastLoad);
                default -> true;
            };
            if (in) l.add(r);
        }
        l.sort((a, b) -> Long.compare(b.dwellSince, a.dwellSince));
        return l;
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        Custody cu = BizData.get(world).custody;
        switch (id) {
            case "scan" -> {
                ItemStack b = store(0).getStackInSlot(0);
                if (!ItemContainer.is(b)) return "§7Put a container in the scan tray";
                cu.box(b);
                sel = ItemContainer.id(b);
                beep(true);
                return "§aScanned " + ItemContainer.pretty(b);
            }
            case "find" -> {
                if (who == null || !ItemContainer.is(who.getHeldItemMainhand())) return "§7Hold the container to look it up";
                cu.box(who.getHeldItemMainhand());
                sel = ItemContainer.id(who.getHeldItemMainhand());
                beep(true);
                return "§a" + ItemContainer.pretty(who.getHeldItemMainhand());
            }
            case "next", "prev" -> {
                List<Custody.Rec> l = list();
                if (l.isEmpty()) { sel = ""; beep(false); return "§7No containers under " + FILTERS[Math.floorMod(ctl.get("s_filter"), FILTERS.length)]; }
                int i = 0;
                for (int k = 0; k < l.size(); k++) if (l.get(k).id.equals(sel)) { i = Math.floorMod(k + (id.equals("next") ? 1 : -1), l.size()); break; }
                sel = l.get(i).id;
                return null;
            }
            case "print" -> {
                Custody.Rec r = sel.isEmpty() ? null : cu.recs.get(sel);
                if (r == null || who == null) return "§7Select a container first";
                ItemStack t = new ItemStack(Items.PAPER);
                t.setStackDisplayName("§fTracking Report: " + r.id);
                NBTTagList lore = new NBTTagList();
                lore.appendTag(new NBTTagString("§7" + r.status + " · seal " + (r.seal == 0 ? "-" : r.seal + (r.sealBroken ? " BROKEN" : "")) + " · owner " + r.owner));
                for (int i = Math.max(0, r.events.size() - 10); i < r.events.size(); i++) {
                    String[] p = r.events.get(i).split("\\|", 5);
                    if (p.length == 5) lore.appendTag(new NBTTagString("§8" + com.dogpound.railmap.alarm.Recorder.clock(world, Long.parseLong(p[0])) + " " + p[1] + " " + p[2] + " " + p[3] + ": " + p[4]));
                }
                t.getOrCreateSubCompound("display").setTag("Lore", lore);
                if (!who.inventory.addItemStackToInventory(t)) who.dropItem(t, false);
                world.playSound(null, pos, ModSounds.PRINTER, SoundCategory.BLOCKS, 0.6f, 1f);
                return "§aTracking report printed";
            }
            default -> { return null; }
        }
    }

    private void beep(boolean ok) { world.playSound(null, pos, ok ? ModSounds.BEEP_OK : ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.6f, 1f); }

    @Override
    protected void work() {
        if (age % 10 != 0) return;
        Custody.Rec r = sel.isEmpty() ? null : BizData.get(world).custody.recs.get(sel);
        long now = world.getTotalWorldTime();
        boolean valid = r != null && Custody.validId(r.id);
        put("pwr", powered()); put("valid", valid); put("bad", r != null && !valid);
        put("flag", r != null && (r.status.equals("FLAGGED") || r.status.equals("REJECTED")));
        put("sealed", r != null && r.seal > 0 && !r.sealBroken); put("reeferl", r != null && r.reefer);
        put("contam", r != null && (!r.contam.isEmpty() || r.food && r.clean < 60));
        List<Custody.Rec> l = list();
        int at = 0;
        for (int k = 0; k < l.size(); k++) if (r != null && l.get(k).id.equals(r.id)) at = k + 1;
        text("cnt", at + "/" + l.size() + " " + FILTERS[Math.floorMod(ctl.get("s_filter"), FILTERS.length)]);
        if (r == null) { text("id", "SCAN OR FIND"); for (String k : new String[]{"st", "loc", "who", "cond", "last"}) text(k, ""); return; }
        text("id", (r.id.length() == 11 ? r.id.substring(0, 4) + " " + r.id.substring(4, 10) + " " + r.id.substring(10) : r.id) + (valid ? " CHK OK" : " CHK BAD"));
        text("st", r.status + " SEAL " + (r.seal == 0 ? "-" : r.seal + (r.sealBroken ? " BRK" : "")));
        text("loc", (r.lastMode.isEmpty() ? "?" : r.lastMode) + " @ " + (r.lastPlace.isEmpty() ? "?" : r.lastPlace));
        text("who", (r.lastHolder.isEmpty() ? "-" : r.lastHolder) + " " + Math.max(0, (now - r.dwellSince) / 1000) + "H AGO");
        text("cond", String.format(Locale.ROOT, "CL %d%% MO %.0f%% %.0fC%s", Math.round(r.clean), r.moisture, r.tempC, r.reefer ? " Q" + Math.round(r.coldQ) : ""));
        String last = r.events.isEmpty() ? "" : r.events.get(r.events.size() - 1);
        String[] p = last.split("\\|", 5);
        text("last", p.length == 5 ? p[4] : "");
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setString("sel", sel); }
    @Override protected void loadExtra(NBTTagCompound t) { sel = t.getString("sel"); }
}
