package com.dogpound.railmap.alarm;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * The unified alarm manager (her ChatGPT list: "unified alarm manager"): every alarm in IR Extras - grid trips, faults,
 * burn-outs, motors, wired gear, signals, wrecks, ALL STOP, freight, the control room - lands here with a category and
 * a severity, and follows the real annunciator sequence (ISA-18.1): a NEW alarm flashes fast and sounds the horn,
 * ACKnowledged it burns steady, when the condition CLEARS it flashes slow until RESET. Repeats of the same alarm count
 * up instead of flooding the list. Annunciator panels, the console's ALARMS tab and chat all read from this.
 */
public final class AlarmManager extends WorldSavedData {
    public enum Cat {
        POWER("POWER TRIP"), FAULT("SHORT / EARTH|FAULT"), FIRE("FIRE /|BURN-OUT"), MACHINE("MOTOR / GEN|FAULT"),
        AUTO("AUTO|CONTROL"), WIRED("12 V /|WIRED FAULT"), SIGNAL("SIGNAL|FAULT"), INCIDENT("TRAIN|INCIDENT"),
        ALLSTOP("ALL STOP"), FREIGHT("FREIGHT"), CONSOLE("CONTROL|ROOM"), GENERAL("GENERAL");
        public final String tile;
        Cat(String tile) { this.tile = tile; }
    }

    public enum Sev { CRITICAL, MAJOR, MINOR, INFO }

    public static final class Alarm {
        public int id;
        public String key = "", text = "";
        public Cat cat = Cat.GENERAL;
        public Sev sev = Sev.MINOR;
        public long pos;
        public long first, last;
        public int count = 1;
        public boolean active, acked;
        /** a one-off event (a trip, a burn-out): new until acknowledged, then gone */
        public boolean momentary;
        public String ackBy = "";
        public long shelvedUntil;
        /** alarm correlation (#72): the root alarm this one is a consequence of (0 = none), and how many it caused */
        public int parent, children;

        /** 0 normal (gone), 1 new (fast flash), 2 acknowledged (steady), 3 cleared but not reset (slow flash) */
        public int seq(long now) {
            if (shelvedUntil > now) return 0;
            if (active) return acked ? 2 : 1;
            if (momentary) return acked ? 0 : 1;
            return acked ? 0 : 3;
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id); t.setString("key", key); t.setString("text", text); t.setString("cat", cat.name()); t.setString("sev", sev.name());
            t.setLong("pos", pos); t.setLong("first", first); t.setLong("last", last); t.setInteger("count", count);
            t.setBoolean("active", active); t.setBoolean("mom", momentary); t.setBoolean("acked", acked); t.setString("ackBy", ackBy); t.setLong("shelf", shelvedUntil);
            t.setInteger("parent", parent); t.setInteger("kids", children);
            return t;
        }

        static Alarm read(NBTTagCompound t) {
            Alarm a = new Alarm();
            a.id = t.getInteger("id"); a.key = t.getString("key"); a.text = t.getString("text");
            try { a.cat = Cat.valueOf(t.getString("cat")); } catch (IllegalArgumentException e) { a.cat = Cat.GENERAL; }
            try { a.sev = Sev.valueOf(t.getString("sev")); } catch (IllegalArgumentException e) { a.sev = Sev.MINOR; }
            a.pos = t.getLong("pos"); a.first = t.getLong("first"); a.last = t.getLong("last"); a.count = t.getInteger("count");
            a.active = t.getBoolean("active"); a.momentary = t.getBoolean("mom"); a.acked = t.getBoolean("acked"); a.ackBy = t.getString("ackBy"); a.shelvedUntil = t.getLong("shelf");
            a.parent = t.getInteger("parent"); a.children = t.getInteger("kids");
            return a;
        }
    }

    private static final String NAME = "railmap_alarms";
    private final List<Alarm> alarms = new ArrayList<>();
    private int nextId = 1;
    /** the horn is quiet until the next new alarm */
    public boolean silenced;
    /** bumps on every change (annunciators / consoles refresh) */
    public int version;

    public AlarmManager() { super(NAME); }
    public AlarmManager(String n) { super(n); }

    public static AlarmManager get(World w) {
        MapStorage s = w.getPerWorldStorage();
        AlarmManager d = (AlarmManager) s.getOrLoadData(AlarmManager.class, NAME);
        if (d == null) { d = new AlarmManager(); s.setData(NAME, d); }
        return d;
    }

    public List<Alarm> all() { return alarms; }

    /**
     * raise (or re-raise) an alarm. `key` identifies the condition (same key = same alarm, counted); `standing` alarms stay
     * active until clear(key); momentary ones (a trip, a burn-out) are active then clear at once, waiting for ACK + RESET.
     */
    public Alarm raise(World w, String key, Cat cat, Sev sev, String text, BlockPos at, boolean standing) {
        long now = w.getTotalWorldTime();
        Alarm a = null;
        for (Alarm x : alarms) if (x.key.equals(key) && (x.active || !x.acked)) { a = x; break; }
        if (a == null) {
            a = new Alarm();
            a.id = nextId++;
            a.key = key;
            a.first = now;
            alarms.add(0, a);
        } else a.count++;
        a.cat = cat; a.sev = sev; a.text = clean(text); a.pos = at == null ? 0 : at.toLong(); a.last = now;
        a.active = standing; a.momentary = !standing; a.acked = false; a.ackBy = "";
        Correlator.link(this, a, now);                                          // #72: tie it to its probable root cause
        Recorder.event(w, a.sev + " " + a.cat.tile.replace("|", " ") + ": " + a.text);
        com.dogpound.railmap.program.ProgramData.post(w, "alarm"); com.dogpound.railmap.program.ProgramData.post(w, "alarm:" + cat.name());   // program EVENT conditions (#98)
        silenced = false;
        try { com.dogpound.railmap.cctv.Cctv.onAlarm(w, key, a.text, at); } catch (RuntimeException | LinkageError ignored) { }   // CCTV: bookmark the cameras that see it
        trim();
        changed();
        return a;
    }

    /** the condition behind a standing alarm went away */
    public void clear(String key) {
        for (Alarm a : alarms) if (a.key.equals(key) && a.active) { a.active = false; changed(); }
    }

    public void ack(int id, String who) {
        for (Alarm a : alarms) if ((id < 0 || a.id == id) && !a.acked) { a.acked = true; a.ackBy = who; }
        changed();
    }

    /** RESET: acknowledged, cleared alarms go back to normal (drop off the annunciator) */
    public void reset() {
        for (Alarm a : alarms) if (!a.active && !a.acked) a.acked = true;   // reset also acknowledges what has already cleared
        changed();
    }

    public void shelve(int id, long until) {
        for (Alarm a : alarms) if (a.id == id) a.shelvedUntil = until;
        changed();
    }

    public void silence() { silenced = true; changed(); }

    /** what one annunciator window shows: the worst sequence of its category (1 new beats 3 cleared beats 2 acked) */
    public int tile(Cat c, long now) {
        int best = 0;
        for (Alarm a : alarms) {
            if (a.cat != c) continue;
            int s = a.seq(now);
            if (s == 1) return 1;
            if (s == 3) best = 3;
            else if (s == 2 && best == 0) best = 2;
        }
        return best;
    }

    public boolean horn(long now) {
        if (silenced) return false;
        for (Alarm a : alarms) if (a.seq(now) == 1 && a.sev != Sev.INFO && !(a.parent != 0 && com.dogpound.railmap.incident.IncidentConfig.quietConsequences)) return true;
        return false;
    }

    public Alarm byId(int id) {
        for (Alarm a : alarms) if (a.id == id) return a;
        return null;
    }

    /** something outside changed alarms (a group ACK): annunciators / consoles refresh */
    public void touch() { changed(); }

    public int count(int seq, long now) {
        int n = 0;
        for (Alarm a : alarms) if (a.seq(now) == seq) n++;
        return n;
    }

    public Alarm latest(long now) {
        for (Alarm a : alarms) if (a.seq(now) != 0) return a;
        return null;
    }

    private void trim() {
        while (alarms.size() > 200) {                                           // drop the oldest finished ones
            int k = -1;
            for (int i = alarms.size() - 1; i >= 0; i--) if (!alarms.get(i).active && alarms.get(i).acked) { k = i; break; }
            if (k < 0) k = alarms.size() - 1;
            alarms.remove(k);
        }
    }

    private void changed() { version++; markDirty(); }

    /** without colour codes and emoji (annunciators and the console use a plain font) */
    static String clean(String s) {
        StringBuilder b = new StringBuilder();
        String t = s.replaceAll("\u00a7.", "");
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isSurrogate(c) || c >= 0x2600 && c <= 0x27BF || c == 0xFE0F) continue;
            b.append(c);
        }
        return b.toString().trim();
    }

    // ---- the two old alarm paths feed in here -----------------------------------------------------------------------
    /** classify a free-text alarm from the grid / signal box and raise it (momentary unless it has a clear-key) */
    public static void fromText(World w, String text, BlockPos at) {
        if (w == null || w.isRemote) return;
        String t = clean(text).toLowerCase(Locale.ROOT);
        Cat c; Sev s = Sev.MAJOR;
        if (t.contains("burnt out") || t.contains("burn") || t.contains("fire")) { c = Cat.FIRE; s = Sev.CRITICAL; }
        else if (t.contains("short circuit") || t.contains("earth") || t.contains("flashover") || t.contains("breaker failed")) { c = Cat.FAULT; s = Sev.CRITICAL; }
        else if (t.contains("tripped") || t.contains("trip")) c = Cat.POWER;
        else if (t.contains("motor") || t.contains("stalled") || t.contains("gcb") || t.contains("generator")) c = Cat.MACHINE;
        else if (t.contains("auto controller")) { c = Cat.AUTO; s = Sev.MINOR; }
        else if (t.startsWith("wired:")) c = Cat.WIRED;
        else if (t.contains("signal")) c = Cat.SIGNAL;
        else if (t.contains("wreck") || t.contains("derail") || t.contains("incident") || t.contains("stranded") || t.contains("line clear")) { c = Cat.INCIDENT; s = t.contains("line clear") ? Sev.INFO : Sev.CRITICAL; }
        else if (t.contains("all stop")) { c = Cat.ALLSTOP; s = Sev.CRITICAL; }
        else if (t.contains("freight") || t.contains("contract")) { c = Cat.FREIGHT; s = t.contains("failed") ? Sev.MAJOR : Sev.INFO; }
        else if (t.contains("console")) { c = Cat.CONSOLE; s = Sev.CRITICAL; }
        else c = Cat.GENERAL;
        AlarmManager m = get(w);
        if (t.startsWith("wired:")) {                                           // standing: the WIRED page raises and clears these
            String key = "wired:" + (at == null ? 0 : at.toLong());
            if (t.contains("back to normal")) m.clear(key);
            else m.raise(w, key, c, Sev.MAJOR, text, at, true);
            return;
        }
        m.raise(w, c.name() + ":" + (at == null ? 0 : at.toLong()) + ":" + t.replaceAll("[0-9]", "").hashCode(), c, s, text, at, false);
    }

    // ---- save -----------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        alarms.clear();
        NBTTagList l = t.getTagList("alarms", 10);
        for (int i = 0; i < l.tagCount(); i++) alarms.add(Alarm.read(l.getCompoundTagAt(i)));
        nextId = Math.max(1, t.getInteger("next"));
        silenced = t.getBoolean("silenced");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Alarm a : alarms) l.appendTag(a.write());
        t.setTag("alarms", l);
        t.setInteger("next", nextId);
        t.setBoolean("silenced", silenced);
        return t;
    }
}
