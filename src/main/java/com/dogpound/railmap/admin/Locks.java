package com.dogpound.railmap.admin;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.*;

/**
 * Multiplayer command locking (her ChatGPT list #115). The railway is split into control areas - the named areas an
 * admin draws, else automatic square cells. The first person to give a signalling / dispatching / power command in an
 * area takes control of it at their desk; anyone else's command there, from any other desk or the console, is refused
 * with who holds it and where. Control lapses after a quiet spell or when the holder leaves; otherwise it changes
 * hands by a handover: the other dispatcher requests it, the holder accepts (both get the area's state - driverless
 * trains, held trains, open alarms - so nothing is forgotten) or refuses. Admins can force-release. Not saved: a
 * restart frees every desk.
 */
public final class Locks {
    private Locks() {}

    public static final class Lock {
        public final int dim;
        public final String area;
        public UUID holder, want;
        public String name = "", desk = "", wantName = "";
        public long since, last, wantAt;
        Lock(int dim, String area) { this.dim = dim; this.area = area; }
    }

    private static final Map<String, Lock> LOCKS = new LinkedHashMap<>();

    private static String key(World w, String area) { return w.provider.getDimension() + "|" + area.toUpperCase(Locale.ROOT); }

    public static String area(World w, BlockPos p) {
        for (AdminData.Rect r : AdminData.get(w).areas) if (r.has(p.getX(), p.getZ())) return r.name;
        int c = AdminConfig.lockCellBlocks;
        return "CELL " + Math.floorDiv(p.getX(), c) + "/" + Math.floorDiv(p.getZ(), c);
    }

    static boolean live(World w, Lock l) {
        if (l == null || l.holder == null) return false;
        if (w.getTotalWorldTime() - l.last > AdminConfig.lockIdleSeconds * 20L) return false;
        return FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(l.holder) != null;
    }

    /** null = this person may command here (they now hold the area); else why not */
    static String claim(World w, EntityPlayer p, BlockPos at, String desk, boolean take) {
        String a = area(w, at);
        long now = w.getTotalWorldTime();
        Lock l = LOCKS.get(key(w, a));
        if (l != null && !p.getUniqueID().equals(l.holder) && live(w, l))
            return "§6⛔ " + a + " is under the control of " + l.name + " at " + l.desk + " (" + mins(now - l.since) + ") - request a handover: /irextras admin desk request " + a;
        if (!take && (l == null || !p.getUniqueID().equals(l.holder))) return null;     // trackside gear: free to use, takes nothing
        if (l == null || !p.getUniqueID().equals(l.holder)) {
            if (l == null) { l = new Lock(w.provider.getDimension(), a); LOCKS.put(key(w, a), l); }
            l.holder = p.getUniqueID(); l.name = p.getName(); l.since = now; l.want = null;
            Recorder.event(w, "CONTROL: " + p.getName() + " took control of " + a + " at " + (desk == null ? "a desk" : desk));
        }
        if (desk != null) l.desk = desk;
        l.last = now;
        return null;
    }

    public static List<Lock> list(World w) {
        List<Lock> o = new ArrayList<>();
        for (Lock l : LOCKS.values()) if (l.dim == w.provider.getDimension() && live(w, l)) o.add(l);
        return o;
    }

    public static Lock of(World w, String area) { Lock l = LOCKS.get(key(w, area)); return live(w, l) ? l : null; }

    public static String request(World w, EntityPlayerMP p, String area) {
        Lock l = of(w, area);
        if (l == null) return "§a" + area + " is free - just use a desk there to take control";
        if (p.getUniqueID().equals(l.holder)) return "§7You already hold " + area;
        l.want = p.getUniqueID(); l.wantName = p.getName(); l.wantAt = w.getTotalWorldTime();
        EntityPlayerMP h = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(l.holder);
        if (h != null) {
            TextComponentString m = new TextComponentString("§e☎ " + p.getName() + " asks to take over " + l.area + ". ");
            m.appendSibling(button("[HAND OVER]", "/irextras admin desk accept " + l.area, TextFormatting.GREEN));
            m.appendText(" ");
            m.appendSibling(button("[REFUSE]", "/irextras admin desk refuse " + l.area, TextFormatting.RED));
            h.sendMessage(m);
        }
        Recorder.event(w, "CONTROL: " + p.getName() + " requested a handover of " + l.area + " from " + l.name);
        return "§eHandover of " + l.area + " requested from " + l.name;
    }

    private static TextComponentString button(String text, String cmd, TextFormatting col) {
        TextComponentString b = new TextComponentString(text);
        b.setStyle(new Style().setColor(col).setBold(true).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd)));
        return b;
    }

    public static String accept(World w, EntityPlayerMP p, String area) {
        Lock l = of(w, area);
        if (l == null || !p.getUniqueID().equals(l.holder)) return "§7You don't hold " + area;
        if (l.want == null) return "§7Nobody has asked for " + area;
        EntityPlayerMP to = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(l.want);
        if (to == null) { l.want = null; return "§7" + l.wantName + " is no longer online"; }
        List<String> brief = briefing(w, l.area);
        to.sendMessage(new TextComponentString("§a✔ " + p.getName() + " handed " + l.area + " over to you. Handover notes:"));
        for (String s : brief) to.sendMessage(new TextComponentString("§7  " + s));
        String was = l.name;
        l.holder = to.getUniqueID(); l.name = to.getName(); l.since = l.last = w.getTotalWorldTime(); l.want = null; l.desk = "(handover)";
        Recorder.event(w, "CONTROL: " + was + " handed " + l.area + " over to " + to.getName());
        return "§a" + l.area + " handed over to " + to.getName() + " (" + brief.size() + " handover notes sent)";
    }

    public static String refuse(World w, EntityPlayerMP p, String area) {
        Lock l = of(w, area);
        if (l == null || !p.getUniqueID().equals(l.holder) || l.want == null) return "§7No handover to refuse";
        EntityPlayerMP to = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(l.want);
        if (to != null) to.sendMessage(new TextComponentString("§c" + p.getName() + " refused to hand over " + l.area + " (try again once they are clear)"));
        l.want = null;
        return "§eHandover refused";
    }

    public static String release(World w, EntityPlayer p, String area, boolean force) {
        Lock l = LOCKS.get(key(w, area));
        if (l == null || !live(w, l)) return "§7Nobody holds " + area;
        if (!force && (p == null || !p.getUniqueID().equals(l.holder))) return "§7You don't hold " + area + " (" + l.name + " does)";
        LOCKS.remove(key(w, area));
        Recorder.event(w, "CONTROL: " + area + " released" + (force ? " by admin " + (p == null ? "console" : p.getName()) + " (was " + l.name + ")" : " by " + l.name));
        EntityPlayerMP h = force ? FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(l.holder) : null;
        if (h != null && h != p) h.sendMessage(new TextComponentString("§c⚠ An admin released your control of " + area));
        return "§a" + area + " released";
    }

    /** what the next dispatcher must know about an area */
    public static List<String> briefing(World w, String area) {
        List<String> o = new ArrayList<>();
        int trains = 0, held = 0, stopped = 0;
        for (RailwayData.AutoTrain a : RailwayData.get(w).trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            if (r == null || r.entityId < 0) continue;
            net.minecraft.entity.Entity e = w.getEntityByID(r.entityId);
            if (e == null || !area(w, e.getPosition()).equalsIgnoreCase(area)) continue;
            trains++;
            if (r.status.toLowerCase(Locale.ROOT).contains("held") || r.status.toLowerCase(Locale.ROOT).contains("blocked")) { held++; o.add("HELD: " + (a.label.isEmpty() ? "train" : a.label) + " - " + r.status); }
            if (AdminData.get(w).stopped.containsKey(a.loco)) stopped++;
        }
        int al = 0;
        for (AlarmManager.Alarm x : AlarmManager.get(w).all())
            if (x.pos != 0 && area(w, BlockPos.fromLong(x.pos)).equalsIgnoreCase(area) && !x.acked) { al++; if (al <= 5) o.add("ALARM: " + x.text); }
        o.add(0, trains + " driverless trains in the area, " + held + " held, " + stopped + " admin safe-stopped, " + al + " unacknowledged alarms");
        return o;
    }

    static String mins(long ticks) { long m = ticks / 1200; return m < 1 ? "just now" : m + " min"; }
}
