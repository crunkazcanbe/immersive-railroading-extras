package com.dogpound.railmap.admin;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;

/**
 * Railway backups and recovery (her ChatGPT list #119).
 * <ul>
 *   <li><b>Snapshots</b>: every IR Extras saved record in every dimension (lines and timetables, companies and
 *   ownership, leases, alarms, grid, signalling, depots, incidents, statistics... - whatever the mod keeps, found by
 *   its class, so new systems are included automatically) is copied in memory on the server thread in the same tick
 *   (consistent), then written by a background thread to {@code <world>/irextras_backups/<time>/} with a CRC manifest.
 *   Automatic every {@link AdminConfig#snapshotMinutes}, the last {@link AdminConfig#snapshotsKept} kept.</li>
 *   <li><b>Restore</b>: a snapshot is verified (every file readable, CRCs match) and staged; it is put in place at
 *   the next server start, before the worlds load (the records being live, that is the only consistent moment). The
 *   files it replaces are kept as a "pre-restore" snapshot.</li>
 *   <li><b>Crash recovery checks</b>: a RUNNING marker that a clean shutdown removes; found at start = the last run
 *   died. Then every IR Extras record file is test-read, ownership is checked against leases, admin pauses / locks are
 *   known gone, and a RECOVERY alarm says what was found and how old the newest snapshot is.</li>
 * </ul>
 */
public final class Backups {
    private Backups() {}

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "IRExtras-backup"); t.setDaemon(true); return t; });
    public static volatile String lastResult = "";
    public static volatile long lastSnapshotMs;
    public static volatile boolean writing;
    public static final List<String> RECOVERY = new ArrayList<>();
    static String stagedNotice = "";

    static File root(MinecraftServer s) { return new File(s.getWorld(0).getSaveHandler().getWorldDirectory(), "irextras_backups"); }

    @SuppressWarnings("unchecked")
    private static List<WorldSavedData> loaded(MapStorage m) {
        for (Field f : MapStorage.class.getDeclaredFields())
            if (List.class.isAssignableFrom(f.getType())) {
                try { f.setAccessible(true); return new ArrayList<>((List<WorldSavedData>) f.get(m)); } catch (ReflectiveOperationException | RuntimeException e) { RailMap.LOG.warn("[IR Extras] backup: {}", e.toString()); }
            }
        return Collections.emptyList();
    }

    private static String rel(WorldServer w) {
        String f = w.provider.getSaveFolder();
        return f == null ? "data" : f + "/data";
    }

    /** take a snapshot now (server thread); the files are written in the background */
    public static String snapshot(MinecraftServer s, String why) {
        if (writing) return "§7A snapshot is still being written";
        Map<String, NBTTagCompound> files = new TreeMap<>();
        Set<WorldSavedData> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (WorldServer w : s.worlds) {
            for (WorldSavedData d : loaded(w.getPerWorldStorage())) add(files, seen, rel(w), d);
            if (w.provider.getDimension() == 0) for (WorldSavedData d : loaded(w.getMapStorage())) add(files, seen, "data", d);
        }
        String id = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + (why.isEmpty() ? "" : "-" + why.replaceAll("[^A-Za-z0-9]", ""));
        File dir = new File(root(s), id);
        long tick = s.getWorld(0).getTotalWorldTime();
        writing = true;
        IO.submit(() -> {
            try {
                StringBuilder man = new StringBuilder("tick " + tick + "\nreason " + why + "\n");
                for (Map.Entry<String, NBTTagCompound> e : files.entrySet()) {
                    File f = new File(dir, e.getKey());
                    f.getParentFile().mkdirs();
                    CompressedStreamTools.writeCompressed(e.getValue(), Files.newOutputStream(f.toPath()));
                    man.append("file ").append(e.getKey()).append(' ').append(crc(f)).append('\n');
                }
                Files.write(new File(dir, "manifest.txt").toPath(), man.toString().getBytes(StandardCharsets.UTF_8));
                prune(root(s));
                lastSnapshotMs = System.currentTimeMillis();
                lastResult = "Snapshot " + id + ": " + files.size() + " records";
            } catch (IOException | RuntimeException ex) {
                lastResult = "Snapshot " + id + " FAILED: " + ex;
                RailMap.LOG.warn("[IR Extras] railway snapshot failed: {}", ex.toString());
            } finally { writing = false; }
        });
        for (WorldServer w : s.worlds) if (w.provider.getDimension() == 0) Recorder.event(w, "BACKUP: snapshot " + id + " (" + files.size() + " records)");
        return "§aSnapshot " + id + " taken (" + files.size() + " railway records), writing in the background";
    }

    private static void add(Map<String, NBTTagCompound> files, Set<WorldSavedData> seen, String rel, WorldSavedData d) {
        if (d == null || !seen.add(d) || !d.getClass().getName().startsWith("com.dogpound.railmap.")) return;
        try {
            NBTTagCompound root = new NBTTagCompound();
            root.setTag("data", d.writeToNBT(new NBTTagCompound()).copy());          // copy: no tags shared with the live record
            files.put(rel + "/" + d.mapName + ".dat", root);
        } catch (RuntimeException e) { RailMap.LOG.warn("[IR Extras] backup of {} failed: {}", d.mapName, e.toString()); }
    }

    static long crc(File f) throws IOException { CRC32 c = new CRC32(); c.update(Files.readAllBytes(f.toPath())); return c.getValue(); }

    private static void prune(File root) {
        List<File> l = list(root);
        for (int i = AdminConfig.snapshotsKept; i < l.size(); i++) delete(l.get(i));
    }

    private static void delete(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File x : k) delete(x);
        f.delete();
    }

    /** snapshots, newest first */
    public static List<File> list(File root) {
        File[] d = root.listFiles(f -> f.isDirectory() && new File(f, "manifest.txt").exists());
        List<File> l = d == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(d));
        l.sort((a, b) -> b.getName().compareTo(a.getName()));
        return l;
    }

    public static List<File> list(MinecraftServer s) { return list(root(s)); }

    /** null = good; else what is wrong */
    static String verify(File dir) {
        try {
            int n = 0;
            for (String line : Files.readAllLines(new File(dir, "manifest.txt").toPath(), StandardCharsets.UTF_8)) {
                if (!line.startsWith("file ")) continue;
                String[] p = line.split(" ");
                File f = new File(dir, p[1]);
                if (!f.exists()) return p[1] + " missing";
                if (crc(f) != Long.parseLong(p[2])) return p[1] + " damaged (checksum)";
                CompressedStreamTools.readCompressed(Files.newInputStream(f.toPath()));
                n++;
            }
            return n == 0 ? "empty snapshot" : null;
        } catch (IOException | RuntimeException e) { return e.toString(); }
    }

    public static String stage(MinecraftServer s, String id) {
        File dir = null;
        for (File f : list(s)) if (f.getName().startsWith(id)) { dir = f; break; }
        if (dir == null) return "§cNo snapshot " + id;
        String bad = verify(dir);
        if (bad != null) return "§cSnapshot " + dir.getName() + " failed its check: " + bad;
        try { Files.write(new File(root(s), "RESTORE").toPath(), dir.getName().getBytes(StandardCharsets.UTF_8)); } catch (IOException e) { return "§cCan't stage: " + e; }
        return "§eSnapshot " + dir.getName() + " verified and STAGED - it replaces the railway records at the next server start (restart now to complete; /irextras admin backup cancel to undo)";
    }

    public static String cancel(MinecraftServer s) { return new File(root(s), "RESTORE").delete() ? "§aStaged restore cancelled" : "§7Nothing staged"; }

    /** FMLServerAboutToStart: put a staged snapshot in place before any world loads */
    public static void applyStaged(MinecraftServer s) {
        try {
            File world = s.getActiveAnvilConverter().getFile(s.getFolderName(), "");
            File root = new File(world, "irextras_backups"), mark = new File(root, "RESTORE");
            if (!mark.exists()) return;
            String id = new String(Files.readAllBytes(mark.toPath()), StandardCharsets.UTF_8).trim();
            mark.delete();
            File dir = new File(root, id);
            String bad = verify(dir);
            if (bad != null) { stagedNotice = "Staged restore " + id + " NOT applied: " + bad; RailMap.LOG.error("[IR Extras] {}", stagedNotice); return; }
            File pre = new File(root, new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + "-prerestore");
            StringBuilder man = new StringBuilder("tick 0\nreason before restoring " + id + "\n");
            int n = 0;
            for (String line : Files.readAllLines(new File(dir, "manifest.txt").toPath(), StandardCharsets.UTF_8)) {
                if (!line.startsWith("file ")) continue;
                String r = line.split(" ")[1];
                File live = new File(world, r);
                if (live.exists()) {
                    File keep = new File(pre, r);
                    keep.getParentFile().mkdirs();
                    Files.copy(live.toPath(), keep.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    man.append("file ").append(r).append(' ').append(crc(keep)).append('\n');
                }
                live.getParentFile().mkdirs();
                Files.copy(new File(dir, r).toPath(), live.toPath(), StandardCopyOption.REPLACE_EXISTING);
                n++;
            }
            pre.mkdirs();
            Files.write(new File(pre, "manifest.txt").toPath(), man.toString().getBytes(StandardCharsets.UTF_8));
            stagedNotice = "Railway restored from snapshot " + id + " (" + n + " records; the replaced ones are in " + pre.getName() + ")";
            RailMap.LOG.info("[IR Extras] {}", stagedNotice);
        } catch (IOException | RuntimeException e) {
            stagedNotice = "Staged restore failed: " + e;
            RailMap.LOG.error("[IR Extras] {}", stagedNotice);
        }
    }

    // ---- crash detection --------------------------------------------------------------------------------------------
    /** overworld loaded (server side) */
    static void worldLoaded(World w) {
        MinecraftServer s = w.getMinecraftServer();
        if (s == null) return;
        File root = new File(w.getSaveHandler().getWorldDirectory(), "irextras_backups"), mark = new File(root, "RUNNING");
        boolean crashed = mark.exists();
        root.mkdirs();
        try { Files.write(mark.toPath(), String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8)); } catch (IOException ignored) { }
        List<File> snaps = list(root);
        if (!snaps.isEmpty()) lastSnapshotMs = snaps.get(0).lastModified();
        if (!stagedNotice.isEmpty()) {
            AlarmManager.get(w).raise(w, "admin:restore", AlarmManager.Cat.CONSOLE, stagedNotice.contains("NOT") || stagedNotice.contains("failed") ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.INFO, stagedNotice, null, false);
            Recorder.event(w, "BACKUP: " + stagedNotice);
            stagedNotice = "";
        }
        if (crashed) recovery(w, s, snaps);
    }

    static void worldUnloaded(World w) {
        new File(new File(w.getSaveHandler().getWorldDirectory(), "irextras_backups"), "RUNNING").delete();
    }

    static void recovery(World w, MinecraftServer s, List<File> snaps) {
        RECOVERY.clear();
        int files = 0, bad = 0;
        File world = w.getSaveHandler().getWorldDirectory();
        List<File> dirs = new ArrayList<>(Collections.singletonList(new File(world, "data")));
        File[] dims = world.listFiles(f -> f.isDirectory() && f.getName().startsWith("DIM"));
        if (dims != null) for (File d : dims) dirs.add(new File(d, "data"));
        Set<String> ours = new HashSet<>();
        if (!snaps.isEmpty()) try {
            for (String line : Files.readAllLines(new File(snaps.get(0), "manifest.txt").toPath(), StandardCharsets.UTF_8))
                if (line.startsWith("file ")) ours.add(new File(line.split(" ")[1]).getName());
        } catch (IOException ignored) { }
        for (File d : dirs) {
            File[] fs = d.listFiles(f -> f.getName().endsWith(".dat") && (f.getName().startsWith("irextras") || f.getName().startsWith("railmap") || ours.contains(f.getName())));
            if (fs == null) continue;
            for (File f : fs) {
                files++;
                try { CompressedStreamTools.readCompressed(Files.newInputStream(f.toPath())); }
                catch (IOException | RuntimeException e) { bad++; RECOVERY.add("DAMAGED: " + d.getParentFile().getName() + "/" + f.getName() + " - restore a snapshot"); }
            }
        }
        // ownership: a leased loco belongs to its lessee only
        int fixed = 0;
        for (WorldServer ws : s.worlds) {
            CompanyData cd = CompanyData.get(ws);
            for (AdminData.Lease l : AdminData.get(ws).leases) {
                if (l.state != AdminData.State.ACTIVE || !l.loco) continue;
                for (Company c : cd.all())
                    if (c.id != l.lesseeCo && c.trains.remove(l.stock)) { fixed++; cd.markDirty(); }
                Company to = cd.byId(l.lesseeCo);
                if (to != null && to.trains.add(l.stock)) { fixed++; cd.markDirty(); }
            }
            int st = AdminData.get(ws).stopped.size();
            if (st > 0) RECOVERY.add(st + " train(s) still admin safe-stopped in dim " + ws.provider.getDimension() + " - release them when ready");
        }
        if (fixed > 0) RECOVERY.add("Lease ownership repaired on " + fixed + " record(s)");
        RECOVERY.add("Admin pauses and desk control locks were cleared by the restart");
        String age = snaps.isEmpty() ? "NO SNAPSHOT" : "newest snapshot " + snaps.get(0).getName() + " (" + (System.currentTimeMillis() - snaps.get(0).lastModified()) / 60000 + " min old)";
        RECOVERY.add(0, "Unclean shutdown detected: " + files + " railway record files checked, " + bad + " damaged; " + age);
        AlarmManager.get(w).raise(w, "admin:recovery", AlarmManager.Cat.CONSOLE, bad > 0 ? AlarmManager.Sev.CRITICAL : AlarmManager.Sev.MAJOR,
                "RECOVERY: server restarted after a crash - " + files + " files checked, " + bad + " damaged, " + age, null, false);
        for (String r : RECOVERY) Recorder.event(w, "RECOVERY: " + r);
        RailMap.LOG.warn("[IR Extras] recovery checks: {}", RECOVERY);
    }
}
