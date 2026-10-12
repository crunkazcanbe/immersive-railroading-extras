package com.dogpound.railmap.admin;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.ConsoleControl;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.IWiredMachine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Administrative simulation controls (her ChatGPT list #117): inspect a train, safe-stop / release it, diagnose a
 * signal (or any wired gear), and export a fault report. Subsystem pause lives in {@link Sched}.
 * A safe stop is a service brake, not an emergency: driverless trains are held by the control centre hold (they stop
 * at their braking curve), driven ones get throttle off and the brake handle walked up to full service, the driver
 * told why; held until an admin releases it (kept over restarts).
 */
public final class AdminOps {
    private AdminOps() {}

    public static List<Locomotive> locos(World w) {
        List<Locomotive> o = new ArrayList<>();
        try { for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) if (!l.isDead()) o.add(l); } catch (LinkageError | RuntimeException ignored) { }
        o.sort(Comparator.comparing(l -> name(w, l)));
        return o;
    }

    public static Locomotive nearest(World w, double x, double y, double z, double max) {
        Locomotive best = null;
        double bd = max * max;
        for (Locomotive l : locos(w)) {
            double d = l.getPosition().distanceToSquared(new cam72cam.mod.math.Vec3d(x, y, z));
            if (d < bd) { bd = d; best = l; }
        }
        return best;
    }

    public static String name(World w, Locomotive l) {
        RailwayData.AutoTrain a = RailwayData.get(w).trains().get(l.getUUID());
        String n = l.getDefinition() == null ? "loco" : l.getDefinition().name();
        return a != null && !a.label.isEmpty() ? a.label + " (" + n + ")" : n;
    }

    public static double kmh(Locomotive l) { return l.getCurrentSpeed() == null ? 0 : Math.abs(l.getCurrentSpeed().metric()); }

    public static List<String> inspect(World w, Locomotive l) {
        List<String> o = new ArrayList<>();
        UUID u = l.getUUID();
        cam72cam.mod.math.Vec3d p = l.getPosition();
        o.add("§b" + name(w, l) + " §7" + u.toString().substring(0, 8));
        o.add(String.format(Locale.ROOT, "At %.0f %.0f %.0f, %.1f km/h, throttle %d%%, train brake %d%%, reverser %s%s", p.x, p.y, p.z, kmh(l),
                Math.round(l.getThrottle() * 100), Math.round(l.getTrainBrakePos() * 100), l.getReverser() > 0.05 ? "fwd" : l.getReverser() < -0.05 ? "rev" : "neutral",
                l.wreck != 0 ? " §cWRECKED" : ""));
        RailwayData.AutoTrain a = RailwayData.get(w).trains().get(u);
        Autopilot.Run r = Autopilot.run(u);
        o.add(a == null ? "Driven by people (not driverless)" : "Driverless " + a.mode + " on line " + a.line + ": " + (r == null ? "-" : r.status)
                + (r != null && !r.nextStopName.isEmpty() ? ", next " + r.nextStopName : "") + ", delay " + Disruptions.delaySeconds(u) + " s");
        Company c = CompanyData.get(w).ownerOfTrain(u, a == null ? null : a.line);
        AdminData.Lease ls = Commercial.activeLease(w, u);
        o.add("Owner: " + (c == null ? "none" : c.name) + (ls == null ? "" : " - on lease L" + ls.id + " from " + ls.lessorName + ", day " + ls.daysRun + "/" + ls.days));
        WearData.Record wr = WearData.get(w).peek(u);
        if (wr != null) {
            StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "Wear (%.0f km, %.0f since service): ", wr.km, wr.kmSinceService));
            for (WearData.Part pt : WearData.Part.values()) b.append(pt.label).append(' ').append(Math.round(wr.wear[pt.ordinal()])).append("% ");
            o.add(b.toString());
        }
        String why = AdminData.get(w).stopped.get(u);
        if (why != null) o.add("§cADMIN SAFE-STOP: " + why);
        if (ConsoleControl.held(w.provider.getDimension(), u)) o.add("Held by the control centre");
        if (Disruptions.isHeld(w.provider.getDimension(), u)) o.add("Held for a blocked line");
        return o;
    }

    public static String safeStop(World w, Locomotive l, String who, String reason) {
        AdminData d = AdminData.get(w);
        d.stopped.put(l.getUUID(), (reason == null || reason.isEmpty() ? "admin safe stop" : reason) + " - " + who);
        d.markDirty();
        if (RailwayData.get(w).trains().containsKey(l.getUUID())) ConsoleControl.hold(w.provider.getDimension(), l.getUUID(), true);
        l.setThrottle(0);
        tellCrew(w, l, "§c⛔ ADMIN SAFE STOP: " + d.stopped.get(l.getUUID()) + " - brakes applied, wait for release");
        Recorder.event(w, "ADMIN: " + who + " safe-stopped " + name(w, l));
        AlarmManager.get(w).raise(w, "admin:stop:" + l.getUUID(), AlarmManager.Cat.AUTO, AlarmManager.Sev.INFO, "ADMIN SAFE STOP: " + name(w, l) + " (" + who + ")",
                new BlockPos(l.getPosition().x, l.getPosition().y, l.getPosition().z), true);
        return "§eSafe stop: " + name(w, l) + " braking at " + Math.round(kmh(l)) + " km/h";
    }

    public static String release(World w, UUID u, String who) {
        AdminData d = AdminData.get(w);
        if (d.stopped.remove(u) == null) return "§7That train isn't admin-stopped";
        d.markDirty();
        if (RailwayData.get(w).trains().containsKey(u)) ConsoleControl.hold(w.provider.getDimension(), u, false);
        AlarmManager.get(w).clear("admin:stop:" + u);
        Locomotive l = Autopilot.find(w, u);
        if (l != null) tellCrew(w, l, "§a✔ Admin safe stop released - you may proceed");
        Recorder.event(w, "ADMIN: " + who + " released safe stop" + (l == null ? "" : " of " + name(w, l)));
        return "§aReleased";
    }

    /** every second: driven safe-stopped trains get their brake walked up (a service application, ~4 s to full) */
    static void holdStopped(World w) {
        AdminData d = AdminData.get(w);
        if (d.stopped.isEmpty()) return;
        boolean drv;
        for (UUID u : d.stopped.keySet()) {
            Locomotive l = Autopilot.find(w, u);
            if (l == null) continue;
            drv = RailwayData.get(w).trains().containsKey(u);
            if (drv) { ConsoleControl.hold(w.provider.getDimension(), u, true); continue; }
            l.setThrottle(0);
            l.setTrainBrake(Math.min(1f, l.getTrainBrakePos() + 0.25f));
        }
    }

    static void tellCrew(World w, Locomotive l, String msg) {
        cam72cam.mod.math.Vec3d p = l.getPosition();
        for (EntityPlayer pl : w.playerEntities) if (pl.getDistanceSq(p.x, p.y, p.z) < 20 * 20) pl.sendStatusMessage(new TextComponentString(msg), false);
    }

    // ---- diagnose ----------------------------------------------------------------------------------------------------
    public static TileSignalMast nearestSignal(World w, BlockPos at) {
        TileSignalMast best = null;
        double bd = 64 * 64;
        for (TileSignalMast m : SignalRegistry.masts(w.provider.getDimension())) {
            double d = m.getPos().distanceSq(at);
            if (d < bd && !m.isInvalid()) { bd = d; best = m; }
        }
        return best;
    }

    /** a signal or any wired gear: state, supply, readings, why it shows what it shows, its open alarms */
    public static List<String> diagnose(World w, TileEntity t) {
        List<String> o = new ArrayList<>();
        BlockPos p = t.getPos();
        if (t instanceof IWiredMachine m) {
            o.add("§b" + m.wiredName() + " §7at " + p.getX() + "," + p.getY() + "," + p.getZ());
            if (t instanceof IWiredGear g) o.add("State: " + g.gearState() + " | 12 V: " + g.term().status());
            StringBuilder b = new StringBuilder("Readings:");
            for (Map.Entry<String, Object> e : m.wiredInfo().entrySet()) b.append(' ').append(e.getKey()).append('=').append(e.getValue());
            o.add(b.toString());
        } else o.add("§b" + t.getClass().getSimpleName() + " at " + p.getX() + "," + p.getY() + "," + p.getZ());
        if (t instanceof TileSignalMast s) {
            o.add("Aspect shown " + s.shownAspect().label + " (computed " + s.aspect().label + "), mode " + s.mode().label + ", " + s.clearBlocks() + " block(s) clear ahead, block "
                    + Math.round(s.blockLength()) + " m" + (s.routeSet() ? ", route SET" : "") + (s.relayControlled() ? ", relay-controlled" : ""));
            List<String> why = new ArrayList<>();
            if (s.lampFailed()) why.add("lamp failed");
            if (!s.term().powered()) why.add("no 12 V");
            if (s.term().burnt) why.add("electronics burnt (screwdriver)");
            if (!s.safetyHold().isEmpty()) why.add("safety hold: " + s.safetyHold());
            if (s.aspect().isStop() && why.isEmpty()) why.add(s.clearBlocks() == 0 ? "block ahead occupied or not proven clear" : "held at stop by route / interlocking");
            o.add(why.isEmpty() ? "Diagnosis: working normally" : "Diagnosis: " + String.join("; ", why));
        }
        int n = 0;
        for (AlarmManager.Alarm a : AlarmManager.get(w).all())
            if (a.pos == p.toLong() && a.seq(w.getTotalWorldTime()) != 0) { o.add("Alarm: " + a.sev + " " + a.text + (a.acked ? " (acked)" : "")); n++; }
        if (n == 0) o.add("No open alarms on it");
        return o;
    }

    // ---- fault report --------------------------------------------------------------------------------------------
    public static List<String> faultReport(World w) {
        List<String> o = new ArrayList<>();
        long now = w.getTotalWorldTime();
        o.add("RAILWAY FAULT REPORT " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date()) + " (tick " + now + ", dim " + w.provider.getDimension() + ")");
        o.add(String.format(Locale.ROOT, "Server %.1f ms/tick, IR Extras %.2f ms/tick, profile %s", Sched.mspt(w.getMinecraftServer()), Sched.costMs(w.getMinecraftServer()), Profiles.current()));
        o.add("Paused: " + (Sched.PAUSED.isEmpty() ? "none" : Sched.PAUSED.keySet()) + "; desks held: " + Locks.list(w).size());
        int k = 0;
        for (AlarmManager.Alarm a : AlarmManager.get(w).all()) {
            if (a.seq(now) == 0) continue;
            BlockPos p = BlockPos.fromLong(a.pos);
            o.add("ALARM " + a.sev + " " + a.cat + ": " + a.text + (a.pos == 0 ? "" : " @" + p.getX() + "," + p.getY() + "," + p.getZ()) + (a.acked ? " [acked " + a.ackBy + "]" : ""));
            if (++k >= 60) break;
        }
        for (UUID u : AdminData.get(w).stopped.keySet()) o.add("SAFE-STOPPED " + u.toString().substring(0, 8) + ": " + AdminData.get(w).stopped.get(u));
        for (Locomotive l : locos(w)) {
            if (l.wreck != 0) o.add("WRECK " + name(w, l));
            WearData.Record r = WearData.get(w).peek(l.getUUID());
            if (r != null && r.worst() >= 80) o.add(String.format(Locale.ROOT, "WORN %s: %s %.0f%%", name(w, l), r.worstPart().label, r.worst()));
        }
        for (Disruptions.Incident in : Disruptions.incidents(w.provider.getDimension())) if (in.blocking()) o.add("BLOCKED LINE #" + in.id + ": " + Disruptions.why(in));
        o.addAll(Backups.RECOVERY);
        return o;
    }

    public static File exportReport(World w, List<String> lines) throws IOException {
        File dir = new File(w.getSaveHandler().getWorldDirectory(), "data/irextras_reports");
        dir.mkdirs();
        File f = new File(dir, "fault-report-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".json");
        StringBuilder b = new StringBuilder("{\"report\":[\n");
        for (int i = 0; i < lines.size(); i++)
            b.append("  \"").append(lines.get(i).replaceAll("§.", "").replace("\\", "\\\\").replace("\"", "\\\"")).append('"').append(i < lines.size() - 1 ? ",\n" : "\n");
        b.append("]}\n");
        Files.write(f.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        return f;
    }

    /** a written book out of a console printer */
    static void printBook(World world, BlockPos pos, EnumFacing f, String title, List<String> lines) {
        NBTTagList pages = new NBTTagList();
        StringBuilder p = new StringBuilder();
        int n = 0;
        for (String l : lines) {
            String s = l.replaceAll("§.", "");
            if (n >= 9 || p.length() + s.length() > 230) { pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString())))); p = new StringBuilder(); n = 0; }
            p.append(s.length() > 200 ? s.substring(0, 200) : s).append('\n');
            n++;
            if (pages.tagCount() >= 48) break;
        }
        if (p.length() > 0) pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString()))));
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("title", title.length() > 30 ? title.substring(0, 30) : title);
        t.setString("author", "Railway Admin");
        t.setTag("pages", pages);
        book.setTagCompound(t);
        net.minecraft.entity.item.EntityItem e = new net.minecraft.entity.item.EntityItem(world, pos.getX() + 0.5 + f.getXOffset() * 0.7, pos.getY() + 1.1, pos.getZ() + 0.5 + f.getZOffset() * 0.7, book);
        e.motionX = f.getXOffset() * 0.05; e.motionZ = f.getZOffset() * 0.05;
        world.spawnEntity(e);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.2f, 1.0f);
    }
}
