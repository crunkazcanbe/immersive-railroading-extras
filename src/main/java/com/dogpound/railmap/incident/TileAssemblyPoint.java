package com.dogpound.railmap.incident;

import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Emergency Assembly Point (her ChatGPT list #70): the green safe-area sign on its post, with the fire warden's
 * roll-call box. During an evacuation that covers it the box counts the people standing within 10 m (players and
 * villagers) against the number the incident says were in the zone, and reports the count to incident command; ROLL
 * CALL writes the names into the incident log, ALL IN is the warden declaring everybody accounted for. The sign is
 * a sign (no power needed); the roll-call box needs 12 V.
 */
public class TileAssemblyPoint extends SafetyTile {
    private int count;
    private final List<String> names = new ArrayList<>();

    @Override public String wiredName() { return "Assembly point"; }

    @Override
    public String gearState() {
        IncidentLog.Rec r = IncidentOps.evacAt(world, pos, 64);
        return r == null ? "quiet" : "EVACUATION #" + r.id + " · " + count + " here of " + r.paxAffected;
    }

    @Override public void gearInfo(Map<String, Object> m) { m.put("count", count); }
    @Override protected boolean keepTextUnpowered() { return false; }

    @Override
    protected void serverTick() {
        if (age % 20 != 0) return;
        IncidentLog.Rec r = IncidentOps.evacAt(world, pos, 64);
        names.clear();
        int n = 0;
        for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(pos).grow(10, 4, 10))) {
            if (e instanceof EntityPlayer || e instanceof EntityVillager) { n++; names.add(e.getName()); }
        }
        count = n;
        if (r != null && powered()) IncidentOps.accounted(world, r, n, pos.getX() + "," + pos.getZ());
        put("evac", r != null);
        put("count", n);
        put("need", r == null ? 0 : r.paxAffected);
        put("all", r != null && r.paxAccounted >= r.paxAffected && r.paxAffected > 0 || ctl.get("allin") == 1 && r != null);
        if (r == null) ctl.set("allin", 0);
        text("inc", r == null ? "NO EVACUATION" : "EVAC INC " + r.id);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (!powered()) return "§cThe roll-call box has no 12 V";
        IncidentLog.Rec r = IncidentOps.evacAt(world, pos, 64);
        String w = who == null ? "warden" : who.getName();
        switch (id) {
            case "rollcall":
                if (r == null) return "§7No evacuation in progress";
                IncidentLog.get(world).log(world, r, "ROLL CALL at assembly point " + pos.getX() + "," + pos.getZ() + ": " + count + " present" + (names.isEmpty() ? "" : " - " + String.join(", ", names)));
                return "§e" + count + " present, logged";
            case "allin":
                if (r == null) return "§7No evacuation in progress";
                ctl.set("allin", 1);
                r.paxAccounted = Math.max(r.paxAccounted, r.paxAffected);
                IncidentLog.get(world).log(world, r, "WARDEN (" + w + ") at " + pos.getX() + "," + pos.getZ() + ": ALL ACCOUNTED FOR");
                return "§aEveryone accounted for - control told";
            case "test": return lampTest();
            default: return null;
        }
    }

    @Override protected void save(NBTTagCompound t) { }
    @Override protected void load(NBTTagCompound t) { }
}
