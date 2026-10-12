package com.dogpound.railmap.admin;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Approval Panel (her "next 48" list #43): the wall-mounted authorisation station for the request -> approve -> execute
 * workflow. Its screen pages through the open requests (&lt; &gt;): what, where, who asked, how many approvals it has
 * and needs, and how long an approval has left. Approving is physical: turn an AUTHORISE key. A single request takes
 * one key; a DUAL request takes both keys turned by TWO DIFFERENT people within 10 s of each other (the two-person
 * rule) - one key on its own times out. REJECT refuses it. The bottom lines show the audit trail. 12 V.
 */
public class TileApprovalPanel extends SafetyTile implements com.dogpound.railmap.block.IFurniture {
    private final com.dogpound.railmap.ctl.Drawers drawers = new com.dogpound.railmap.ctl.Drawers(this, new String[]{"tray"}, new String[]{"Request forms tray"}, new int[]{9});
    @Override public net.minecraft.inventory.IInventory storage(int k) { return drawers.storage(k); }
    @Override public String storeName(int k) { return drawers.name(k); }
    @Override public int storeSize(int k) { return drawers.size(k); }
    @Override public void dropAll() { drawers.dropAll(this); }

    private int sel;
    private String keyA = "", keyB = "";
    private long keyAAt, keyBAt;

    @Override public String wiredName() { return "Approval panel"; }
    @Override public String gearState() { return !powered() ? "dark - no 12 V" : Approvals.get(world).open().size() + " open request(s)"; }
    @Override public int wiredLamp() { return !powered() ? 3 : Approvals.get(world).open().isEmpty() ? 1 : 2; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("open", Approvals.get(world).open().size()); }

    private Approvals.Req current() { List<Approvals.Req> l = Approvals.get(world).open(); if (l.isEmpty()) return null; sel = Math.floorMod(sel, l.size()); return l.get(sel); }

    @Override
    protected void serverTick() {
        long now = world.getTotalWorldTime();
        if (!keyA.isEmpty() && now - keyAAt > 200) { keyA = ""; ctl.set("k_a", 0); controlsChanged(); }
        if (!keyB.isEmpty() && now - keyBAt > 200) { keyB = ""; ctl.set("k_b", 0); controlsChanged(); }
        if (age % 20 != 1) return;
        Approvals a = Approvals.get(world);
        a.tick(world);
        List<Approvals.Req> open = a.open();
        Approvals.Req r = current();
        put("pwr", powered());
        put("pend", !open.isEmpty());
        put("appr", r != null && r.state == Approvals.State.APPROVED);
        put("dual", r != null && r.dual);
        put("ka", !keyA.isEmpty());
        put("kb", !keyB.isEmpty());
        put("nopen", open.size());
        text("r1", r == null ? "NO OPEN REQUESTS" : String.format(Locale.ROOT, "#%d %s @ %s", r.id, r.action.toUpperCase(Locale.ROOT), r.where()));
        text("r2", r == null ? "" : "BY " + r.requester.toUpperCase(Locale.ROOT) + " " + r.approvers.size() + "/" + r.needed() + (r.dual ? " DUAL" : " SINGLE"));
        text("r3", r == null ? "" : r.state == Approvals.State.APPROVED ? String.format(Locale.ROOT, "APPROVED %d S LEFT", Math.max(0, (r.expires - now) / 20)) : "AWAITING " + (r.needed() - r.approvers.size()) + " APPROVAL" + (r.needed() - r.approvers.size() == 1 ? "" : "S"));
        text("r4", r == null ? "" : r.detail.toUpperCase(Locale.ROOT));
        text("t1", a.trail.size() > 0 ? a.trail.get(0) : "");
        text("t2", a.trail.size() > 1 ? a.trail.get(1) : "");
        text("pg", open.isEmpty() ? "0/0" : (sel + 1) + "/" + open.size());
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        { String dr = drawers.open(id, who, this); if (dr != null) return dr; }
        switch (id) {
            case "test": return lampTest();
            case "prev": sel--; return "§7Request " + (current() == null ? "-" : "#" + current().id);
            case "next": sel++; return "§7Request " + (current() == null ? "-" : "#" + current().id);
            default: break;
        }
        if (!powered()) return "§cThe panel is dark - no 12 V";
        Approvals a = Approvals.get(world);
        Approvals.Req r = current();
        if (r == null) return "§7No open requests";
        String name = who == null ? null : who.getName();
        switch (id) {
            case "reject": return a.reject(world, r.id, name == null ? "console" : name);
            case "k_a": case "k_b": {
                if (name == null) return "§cAuthorise keys are turned by hand";
                if (r.state != Approvals.State.REQUESTED) return "§7#" + r.id + " is " + r.state;
                if (r.requester.equals(name)) return "§cYou can't authorise your own request";
                long now = world.getTotalWorldTime();
                boolean isA = id.equals("k_a");
                if (isA) { keyA = name; keyAAt = now; ctl.set("k_a", 1); } else { keyB = name; keyBAt = now; ctl.set("k_b", 1); }
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.7f, 1.0f);
                if (!r.dual) { String res = a.approve(world, r.id, name); keyA = keyB = ""; ctl.set("k_a", 0); ctl.set("k_b", 0); return res; }
                String other = isA ? keyB : keyA;
                long otherAt = isA ? keyBAt : keyAAt;
                if (other.isEmpty() || now - otherAt > 200) return "§eKey " + (isA ? "A" : "B") + " turned by " + name + " - a SECOND person must turn the other key within 10 s";
                if (other.equals(name)) return "§cThe same person can't turn both keys - two-person rule";
                a.approve(world, r.id, other);
                String res = a.approve(world, r.id, name);
                Recorder.event(world, "APPROVAL #" + r.id + " dual authorisation at the panel: " + other + " + " + name);
                keyA = keyB = ""; ctl.set("k_a", 0); ctl.set("k_b", 0);
                return res;
            }
            default: return null;
        }
    }

    @Override protected void save(NBTTagCompound t) { drawers.write(t); t.setInteger("sel", sel); }
    @Override protected void load(NBTTagCompound t) { drawers.read(t); sel = t.getInteger("sel"); }
}
