package com.dogpound.railmap.incident;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.TileFurniture;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * The Incident Command Desk (her ChatGPT list #67-70): the emergency control centre's desk. On the wall the incident
 * board lists every open incident (number, type, stage, level, how long); PREV / NEXT picks one, the left monitor
 * shows it (what, where, response needed vs sent, dangerous goods, people, trains held) and the right one the VIEW:
 * the plan (rescue / evacuation), the timeline, or the train recorder download. Orders: ACCEPT command, LEVEL, CREW
 * (breakdown depot), RESCUE engine, FIRE service, MEDIC, PROTECT adjacent lines, ISOLATE traction power, EVAC, ALL
 * CLEAR, REOPEN the next section, CLOSE, PRINT the report, ACK its alarms; the red phone calls control. Real storage:
 * six drawers, the grab-bag cupboard and the plan chest; the shelf shows what's on it.
 */
public class TileIncidentDesk extends TileFurniture {
    public static final String[] STORE_IDS = {"dl1", "dl2", "dl3", "dr1", "dr2", "dr3", "grab", "plans"};
    public static final String[] STORE_NAMES = {"Incident Log Drawer", "Forms Drawer", "Maps Drawer", "Radio Drawer", "Torch Drawer", "First Aid Drawer", "Grab-Bag Cupboard", "Plan Chest"};
    public static final int[] STORE_SIZES = {9, 9, 9, 9, 9, 9, 27, 18};
    private static final double[][] SPOTS = {{19.933, 7.0, 13.4}, {21.6, 7.0, 13.4}, {23.267, 7.0, 13.4}, {24.933, 7.0, 13.4}, {26.6, 7.0, 13.4}, {28.267, 7.0, 13.4}};
    @Override public double[][] shelfSpots() { return SPOTS; }

    public TileIncidentDesk() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, 6); }

    @Override protected boolean mechanical(String id) { return id.equals("view"); }

    private IncidentLog.Rec selected(List<IncidentLog.Rec> act) {
        if (act.isEmpty()) return null;
        int s = Math.floorMod(ctl.get("sel"), act.size());
        return act.get(s);
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        IncidentLog log = IncidentLog.get(world);
        List<IncidentLog.Rec> act = log.active();
        String name = who == null ? "console" : who.getName();
        switch (id) {
            case "prev": ctl.set("sel", ctl.get("sel") - 1); return "";
            case "next": ctl.set("sel", ctl.get("sel") + 1); return "";
            case "phone": {
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PHONE_RING, SoundCategory.BLOCKS, 0.6f, 1.0f);
                IncidentLog.Rec r = selected(act);
                if (r == null) return "§7Control: nothing to report";
                log.log(world, r, "phone: incident desk (" + name + ") reported to control");
                return "§eControl has the report for incident #" + r.id;
            }
            case "print": { print(who, selected(act)); return "§7Printing..."; }
            case "ack": {
                IncidentLog.Rec r = selected(act);
                if (r == null) return "§7No incident selected";
                AlarmManager am = AlarmManager.get(world);
                int n = 0;
                for (AlarmManager.Alarm a : am.all())
                    if (!a.acked && a.pos != 0 && net.minecraft.util.math.BlockPos.fromLong(a.pos).distanceSq(r.pos()) <= (r.r + 32) * (r.r + 32)) { am.ack(a.id, name); n++; }
                return "§e" + n + " alarm(s) at the incident acknowledged";
            }
            default: {
                IncidentLog.Rec r = selected(act);
                if (r == null) return "§7No open incident";
                String res = IncidentOps.order(world, r.id, id, name);
                return res == null ? null : res;
            }
        }
    }

    private void print(EntityPlayer who, IncidentLog.Rec r) {
        List<String> lines = r == null ? null : r.open() ? IncidentLog.finalReport(world, r) : r.report;
        if (r == null) {                                                           // nothing open: the latest scheduled report
            List<String[]> reps = Analytics.get(world).reports();
            if (reps.isEmpty()) lines = java.util.Arrays.asList(Reports.now(world)); else lines = java.util.Arrays.asList(reps.get(0));
        }
        printBook(world, pos, controlFacing(), r == null ? "Railway Report" : "Incident " + r.id, "Incident Command", lines);
    }

    /** a written book out of the desk's printer (also used by the maintenance terminal) */
    public static void printBook(World world, net.minecraft.util.math.BlockPos pos, EnumFacing f, String title, String author, List<String> lines) {
        NBTTagList pages = new NBTTagList();
        StringBuilder p = new StringBuilder();
        int n = 0;
        for (String l : lines) {
            String s = l.startsWith("D|") || l.startsWith("W|") || l.startsWith("N|") ? "§l" + l.substring(2) + "§r" : l;
            if (n >= 9 || p.length() + s.length() > 230) { pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString())))); p = new StringBuilder(); n = 0; }
            p.append(s.length() > 200 ? s.substring(0, 200) : s).append('\n');
            n++;
            if (pages.tagCount() >= 48) break;
        }
        if (p.length() > 0) pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString()))));
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("title", title.length() > 30 ? title.substring(0, 30) : title);
        t.setString("author", author);
        t.setTag("pages", pages);
        book.setTagCompound(t);
        EntityItem e = new EntityItem(world, pos.getX() + 0.5 + f.getXOffset() * 0.7, pos.getY() + 1.1, pos.getZ() + 0.5 + f.getZOffset() * 0.7, book);
        e.motionX = f.getXOffset() * 0.05; e.motionZ = f.getZOffset() * 0.05;
        world.spawnEntity(e);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("alarm.teleprinter"), SoundCategory.BLOCKS, 0.4f, 1.0f);
    }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        IncidentLog log = IncidentLog.get(world);
        List<IncidentLog.Rec> act = log.active();
        long now = world.getTotalWorldTime();
        int sel = act.isEmpty() ? 0 : Math.floorMod(ctl.get("sel"), act.size());
        int from = Math.max(0, Math.min(sel - 2, act.size() - 6));
        ch |= txt("title", act.isEmpty() ? "INCIDENT BOARD - NO OPEN INCIDENTS" : "INCIDENT BOARD - " + act.size() + " OPEN");
        boolean anyNew = false;
        for (int k = 1; k <= 6; k++) {
            int i = from + k - 1;
            IncidentLog.Rec r = i < act.size() ? act.get(i) : null;
            ch |= put("r" + k + "s", r == null ? 0 : i == sel ? 1 : r.stage == IncidentLog.Stage.REPORTED ? 1 : 0);
            ch |= txt("r" + k + "a", r == null ? "" : "#" + r.id + " " + r.type.label + " " + r.stage.name().replace('_', ' '));
            ch |= txt("r" + k + "b", r == null ? "" : "L" + r.level + " " + IncidentLog.mins(now - r.opened));
            if (r != null && r.stage == IncidentLog.Stage.REPORTED) anyNew = true;
        }
        IncidentLog.Rec r = act.isEmpty() ? null : act.get(sel);
        String[] m = new String[6];
        if (r == null) { m[0] = "NO INCIDENT SELECTED"; for (int i = 1; i < 6; i++) m[i] = ""; }
        else {
            m[0] = "#" + r.id + " " + r.type.label + " LEVEL " + r.level;
            m[1] = cut(r.what, 40);
            m[2] = "AT " + (int) r.x + "," + (int) r.z + " R " + (int) r.r + " CMD " + (r.commander.isEmpty() ? "-" : cut(r.commander, 12));
            m[3] = "NEED " + r.needLine();
            m[4] = r.haz.isEmpty() ? "NO DANGEROUS GOODS" : cut(r.haz, 40);
            m[5] = "PAX " + r.paxAffected + " SAFE " + r.paxAccounted + " HELD " + r.services + " SECT " + open(r) + "/" + r.secs.size();
        }
        for (int i = 0; i < 6; i++) ch |= txt("m" + (i + 1), m[i].toUpperCase(Locale.ROOT));
        int view = Math.floorMod(ctl.get("view"), 3);
        List<String> pl = new java.util.ArrayList<>();
        if (r != null) {
            if (view == 0) pl.addAll(r.plan.isEmpty() ? java.util.Collections.singletonList("NO PLAN YET - RESCUE / EVAC MAKE ONE") : r.plan);
            else if (view == 1) for (int i = r.log.size() - 1; i >= 0 && pl.size() < 6; i--) { String[] p = r.log.get(i).split("\\|", 2); pl.add(Recorder.clock(world, Long.parseLong(p[0])) + " " + (p.length > 1 ? p[1] : "")); }
            else pl.addAll(r.blackbox.isEmpty() ? java.util.Collections.singletonList("NO TRAIN RECORDER DOWNLOAD") : r.blackbox);
        }
        for (int i = 0; i < 6; i++) ch |= txt("p" + (i + 1), i < pl.size() ? cut(pl.get(i), 44).toUpperCase(Locale.ROOT) : "");
        ch |= put("power", 1);
        ch |= put("newinc", anyNew);
        ch |= put("command", r != null && !r.commander.isEmpty());
        ch |= put("crewout", r != null && r.has(IncidentLog.CREW));
        ch |= put("onscene", r != null && r.onScene > 0);
        boolean prot = false, iso = false;
        if (r != null) for (IncidentLog.Sec s : r.secs) prot |= s.key.startsWith("adj:") && !s.released;
        if (r != null) iso = r.has(IncidentLog.ISOLATE);
        ch |= put("protected", prot);
        ch |= put("isolated", iso);
        ch |= put("evac", r != null && r.evac);
        ch |= put("hazmat", r != null && r.hazKind != 0);
        ch |= put("reopening", r != null && r.stage == IncidentLog.Stage.REOPENING);
        ch |= txt("resp", r == null || r.onScene == 0 ? "--" : IncidentLog.mins(r.onScene - r.opened).toUpperCase(Locale.ROOT));
        ch |= txt("open", r == null ? "--" : IncidentLog.mins(now - r.opened).toUpperCase(Locale.ROOT));
        ch |= put("held", r == null ? 0 : r.services);
        ch |= put("pax", r == null ? 0 : r.paxAffected);
        return ch;
    }

    private static int open(IncidentLog.Rec r) { int n = 0; for (IncidentLog.Sec s : r.secs) if (!s.released) n++; return n; }

    private boolean put(String id, int v) { return com.dogpound.railmap.block.StationControls.put(ctl, id, v); }
    private boolean put(String id, boolean v) { return com.dogpound.railmap.block.StationControls.put(ctl, id, v); }
    private boolean txt(String id, String t) { return com.dogpound.railmap.block.StationControls.text(ctl, id, t); }

    @Override public String wiredName() { return "Incident command desk"; }
    @Override public String gearState() { return term.powered() ? IncidentLog.get(world).active().size() + " open incident(s)" : "NO 12 V - desk dark"; }
}
