package com.dogpound.railmap.ems;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.incident.IncidentLog;
import com.dogpound.railmap.stn.StnData;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * Fire / Ambulance / Police Station (emergency services #6): the station's watch-room desk. A dispatch screen (station,
 * vehicle status, the selected incident with its distance and ETA, last and average response times, runs today, where
 * the vehicle is), two equipment lockers and a log drawer (real storage), a display shelf, the alarm bell, and the
 * radio. One vehicle per station. The AUTO / MANUAL key: in AUTO it turns out by itself to incidents that need it
 * (Ems.autoDispatch); DISPATCH sends it to the incident picked with INC < / >, RECALL brings it home, BELL rings the
 * turnout bell, RADIO reports the vehicle's status to everyone near the station, LOG prints the run history.
 * The CREW selector (2 / 4 / 6) sets how many firefighters work the scene; the SIRENS key can make a run silent.
 * What is in the lockers matters: water buckets give the fire crew foam/water (faster), golden apples or potions are
 * the paramedics' drugs (each treatment that uses them takes one), fences/fence gates add barriers to the police cordon.
 * 12 V + mains; with no supply the station can't be called.
 */
public abstract class TileEmergencyStation extends StnFurniture {
    static double[][] SPOTS = {{-11.883, 7.0, 13.4}, {-10.25, 7.0, 13.4}, {-8.617, 7.0, 13.4}, {-6.983, 7.0, 13.4}, {-5.35, 7.0, 13.4}, {-3.717, 7.0, 13.4}};   // tools/gen_bb_shops.py
    private int sel, runsToday, runsTotal, respN, lastRun = -1;
    private long respSum, respLast = -1, daySeen = -1, testUntil;
    private String last = "", place = "";

    protected TileEmergencyStation() {
        super(new String[]{"lockA", "lockB", "logd"}, new String[]{"Equipment locker A", "Equipment locker B", "Station log drawer"}, new int[]{27, 27, 9}, 6);
    }

    public abstract Ems.Kind kind();

    public static class Fire extends TileEmergencyStation { @Override public Ems.Kind kind() { return Ems.Kind.FIRE; } }
    public static class Ambulance extends TileEmergencyStation { @Override public Ems.Kind kind() { return Ems.Kind.AMBULANCE; } }
    public static class Police extends TileEmergencyStation { @Override public Ems.Kind kind() { return Ems.Kind.POLICE; } }

    @Override public String wiredName() { return kind().station; }
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_") || id.startsWith("s_"); }
    @Override public String gearState() { return !live() ? "NO SUPPLY - can't be called" : status() + (auto() ? " · AUTO" : " · manual") + " · " + runsToday + " runs today"; }

    public boolean auto() { return ctl.get("k_manual") == 0; }
    public int crew() { return new int[]{2, 4, 6}[Math.floorMod(ctl.get("s_crew"), 3)]; }
    public EmsData.Run current() {
        for (EmsData.Run r : EmsData.get(world).active()) if (r.station == pos.toLong()) return r;
        return null;
    }
    public boolean available() { return live() && current() == null; }
    public String why() { return !term.powered() ? "no 12 V" : !mains.ok() ? "no mains" : current() != null ? "vehicle out on run #" + current().id : "ready"; }
    public String status() { EmsData.Run r = current(); return r == null ? "AVAILABLE" : r.stage.name().replace('_', ' '); }
    public String unitNo() { return String.valueOf(1 + Math.floorMod(pos.getX() * 7 + pos.getZ() * 13, 9)); }
    public String callSign() { return (place.isEmpty() ? pos.getX() + "," + pos.getZ() : place) + " " + kind().station; }

    /** where the vehicle comes out: three blocks in front of the desk, on the ground */
    public BlockPos bay() {
        BlockPos p = pos.offset(controlFacing(), 3);
        BlockPos g = RoadPath.ground(world, p.up(), 4);
        return g != null ? g : p;
    }

    // ---- what the lockers hold ---------------------------------------------------------------------------------------
    private int count(Item... items) {
        int n = 0;
        for (int k = 0; k < 2; k++)
            for (int i = 0; i < storage(k).getSizeInventory(); i++) {
                ItemStack s = storage(k).getStackInSlot(i);
                for (Item it : items) if (!s.isEmpty() && s.getItem() == it) n += s.getCount();
            }
        return n;
    }

    public boolean foam() { return kind() == Ems.Kind.FIRE && count(Items.WATER_BUCKET) > 0; }
    public int barrierStock() { return kind() != Ems.Kind.POLICE ? 0 : Math.min(32, count(Item.getItemFromBlock(Blocks.OAK_FENCE), Item.getItemFromBlock(Blocks.OAK_FENCE_GATE), Item.getItemFromBlock(Blocks.IRON_BARS))); }

    /** a treatment that uses the drugs: takes one golden apple / potion from a locker; false = none left */
    public boolean useMedical() {
        if (kind() != Ems.Kind.AMBULANCE || world.rand.nextInt(6) != 0) return kind() == Ems.Kind.AMBULANCE && count(Items.GOLDEN_APPLE, Items.POTIONITEM) > 0;
        for (int k = 0; k < 2; k++)
            for (int i = 0; i < storage(k).getSizeInventory(); i++) {
                ItemStack s = storage(k).getStackInSlot(i);
                if (s.isEmpty() || s.getItem() != Items.GOLDEN_APPLE && s.getItem() != Items.POTIONITEM) continue;
                s.shrink(1);
                if (s.getItem() == Items.POTIONITEM) StnFurniture.insert(storage(k), new ItemStack(Items.GLASS_BOTTLE));
                if (s.isEmpty()) storage(k).setInventorySlotContents(i, ItemStack.EMPTY);
                storage(k).markDirty();
                return true;
            }
        return false;
    }

    // ---- run callbacks (Ems) ----------------------------------------------------------------------------------------------
    void turnedOut(EmsData.Run r) {
        r.silent = ctl.get("k_silent") == 1;
        r.crew = crew();
        lastRun = r.id; runsToday++; runsTotal++;
        last = "TURNOUT RUN " + r.id;
        world.playSound(null, pos, EmsSounds.TURNOUT, SoundCategory.BLOCKS, 1.4f, 1.0f);
        if (kind() == Ems.Kind.FIRE) world.playSound(null, pos, com.dogpound.railmap.ModSounds.FIRE_BELL, SoundCategory.BLOCKS, 1.5f, 1.0f);
        markDirty();
    }
    void left(EmsData.Run r) { last = "MOBILE RUN " + r.id; world.playSound(null, bay(), EmsSounds.AIR_HORN, SoundCategory.NEUTRAL, 1.5f, kind() == Ems.Kind.POLICE ? 1.3f : 1.0f); markDirty(); }
    void onScene(EmsData.Run r) { respLast = r.response(); respSum += respLast; respN++; last = "ON SCENE " + IncidentLog.mins(respLast); markDirty(); }
    void back(EmsData.Run r) { last = "BACK " + IncidentLog.mins(r.back - r.turnout); markDirty(); }

    public long avgResponse() { return respN == 0 ? -1 : respSum / respN; }

    /** incidents this station could go to, nearest first */
    List<IncidentLog.Rec> incidents() {
        List<IncidentLog.Rec> l = new ArrayList<>(IncidentLog.get(world).active());
        l.removeIf(r -> r.pos().distanceSq(pos) > (double) EmsConfig.maxRange * EmsConfig.maxRange);
        l.sort(Comparator.comparingDouble(r -> r.pos().distanceSq(pos)));
        return l;
    }

    IncidentLog.Rec selected() { List<IncidentLog.Rec> l = incidents(); return l.isEmpty() ? null : l.get(Math.floorMod(sel, l.size())); }

    @Override
    protected String act(String id, EntityPlayer who) {
        String by = who == null ? "console" : who.getName();
        switch (id) {
            case "dispatch": {
                IncidentLog.Rec r = selected();
                if (r == null) return "§7No open incident in range - use /irextras ems send " + kind().name().toLowerCase(Locale.ROOT) + " for a test run";
                return Ems.dispatch(world, this, r, by);
            }
            case "recall": {
                EmsData.Run r = current();
                if (r == null) return "§7The vehicle is in the station";
                Ems.leaveScene(world, r, "recalled by " + by);
                return "§e" + r.vehicle + " recalled to base";
            }
            case "incprev": case "incnext": {
                List<IncidentLog.Rec> l = incidents();
                if (l.isEmpty()) return "§7No open incidents";
                sel = Math.floorMod(sel + (id.equals("incnext") ? 1 : -1), l.size());
                IncidentLog.Rec r = l.get(sel);
                return "§eIncident #" + r.id + " " + r.type.label + ": " + r.what;
            }
            case "bell":
                world.playSound(null, pos, kind() == Ems.Kind.FIRE ? com.dogpound.railmap.ModSounds.FIRE_BELL : EmsSounds.TURNOUT, SoundCategory.BLOCKS, 1.4f, 1.0f);
                return "§cTURNOUT BELL";
            case "radio": {
                EmsData.Run r = current();
                String msg = "§b[" + kind().code + " RADIO] " + callSign() + ": " + (r == null ? "all units available" : r.vehicle + " " + r.stage.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                        + String.format(Locale.ROOT, " at %.0f, %.0f", r.vx, r.vz) + (r.incident >= 0 ? ", incident #" + r.incident : ""));
                for (EntityPlayer p : world.playerEntities) if (p.getDistanceSq(pos) < 96 * 96) p.sendMessage(new TextComponentString(msg));
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.RELAY, SoundCategory.BLOCKS, 0.6f, 1.6f);
                return "";
            }
            case "log": {
                List<String> out = new ArrayList<>();
                out.add(callSign().toUpperCase(Locale.ROOT) + " - RUN LOG");
                out.add("Runs today " + runsToday + ", total " + runsTotal + ", avg response " + (avgResponse() < 0 ? "-" : IncidentLog.mins(avgResponse())));
                for (String s : EmsData.get(world).history) if (s.contains(kind().vehicle)) out.add(s);
                StnUtil.print(world, pos, controlFacing(), kind().station + " log", callSign(), out);
                return "§aRun log printed";
            }
            case "test": testUntil = world.getTotalWorldTime() + 60; return "§7Lamp test";
            case "ack": last = "ACK"; return "§7Call acknowledged";
            default: return null;
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 5) return;
        long day = world.getWorldTime() / 24000;
        if (daySeen < 0) daySeen = day;
        if (day != daySeen) { daySeen = day; runsToday = 0; markDirty(); }
        if (age % 200 == 5) { Map.Entry<Long, String> st = StnData.station(world, pos, 256); place = st == null ? "" : st.getValue(); }
    }

    /** an open incident in range wants this service and nobody has gone */
    boolean callWaiting() {
        EmsData d = EmsData.get(world);
        for (IncidentLog.Rec r : incidents()) if (!d.sent(r.id, kind()) && Ems.wants(world, kind(), r)) return true;
        return false;
    }

    @Override
    protected boolean refresh() {
        EmsData.Run r = current();
        IncidentLog.Rec inc = selected();
        boolean t = world.getTotalWorldTime() < testUntil;
        EmsData.Stage s = r == null ? null : r.stage;
        boolean ch = false;
        ch |= StationControls.put(ctl, "pwr", term.powered());
        ch |= StationControls.put(ctl, "avail", t || r == null && live());
        ch |= StationControls.put(ctl, "turnout", t || s == EmsData.Stage.TURNOUT);
        ch |= StationControls.put(ctl, "resp", t || s == EmsData.Stage.RESPONDING || s == EmsData.Stage.TRANSPORT);
        ch |= StationControls.put(ctl, "scene", t || s == EmsData.Stage.ON_SCENE);
        ch |= StationControls.put(ctl, "ret", t || s == EmsData.Stage.RETURNING);
        ch |= StationControls.put(ctl, "autol", t || auto());
        ch |= StationControls.put(ctl, "callw", t || callWaiting());
        ch |= StationControls.put(ctl, "lockok", t || (kind() == Ems.Kind.FIRE ? foam() : kind() == Ems.Kind.AMBULANCE ? count(Items.GOLDEN_APPLE, Items.POTIONITEM) > 0 : barrierStock() > 0));
        ch |= StationControls.put(ctl, "silentl", t || ctl.get("k_silent") == 1);
        ch |= StationControls.put(ctl, "gresp", respLast < 0 ? 0 : (int) Math.min(100, respLast * 100 / (20 * 600)));
        ch |= StationControls.put(ctl, "gruns", Math.min(100, runsToday * 10));
        ch |= StationControls.text(ctl, "stn", StnUtil.up(StnUtil.cut(callSign(), 26)));
        ch |= StationControls.text(ctl, "status", (r == null ? "AVAILABLE" : StnUtil.up(r.vehicle) + " " + r.stage.name().replace('_', ' ')) + (auto() ? " AUTO" : ""));
        ch |= StationControls.text(ctl, "inc", inc == null ? "NO INCIDENTS" : "#" + inc.id + " " + StnUtil.up(StnUtil.cut(inc.type.label + " " + inc.what, 22)));
        double dist = inc == null ? 0 : Math.sqrt(inc.pos().distanceSq(pos));
        ch |= StationControls.text(ctl, "eta", inc == null ? "" : String.format(Locale.ROOT, "%.0f M ETA %s", dist, IncidentLog.mins((long) (dist * 1.3 / Math.max(0.05, EmsConfig.speed)) + kind().turnoutSec * 20L)));
        ch |= StationControls.text(ctl, "rtime", "LAST " + (respLast < 0 ? "-" : IncidentLog.mins(respLast)) + " AVG " + (avgResponse() < 0 ? "-" : IncidentLog.mins(avgResponse())));
        ch |= StationControls.text(ctl, "runs", "RUNS TODAY " + runsToday + " TOTAL " + runsTotal);
        ch |= StationControls.text(ctl, "veh", r == null ? "VEHICLE IN BAY" : String.format(Locale.ROOT, "VEH %.0f,%.0f %s", r.vx, r.vz, r.work > 0 ? "WORK " + r.work : ""));
        ch |= StationControls.text(ctl, "crewn", "CREW " + crew() + (ctl.get("k_silent") == 1 ? " SILENT" : " SIRENS"));
        ch |= StationControls.text(ctl, "last", last);
        ch |= StationControls.text(ctl, "clock", com.dogpound.railmap.biz.BizData.hhmm(world.getWorldTime()));
        return ch;
    }

    public String[] row() {
        EmsData.Run r = current();
        return new String[]{callSign(), kind().code, !live() ? "!NO SUPPLY" : r == null ? "+AVAILABLE" : "~" + r.stage.name().replace('_', ' '), auto() ? "AUTO" : ".manual",
                String.valueOf(runsToday), respLast < 0 ? "-" : IncidentLog.mins(respLast), avgResponse() < 0 ? "-" : IncidentLog.mins(avgResponse()), String.valueOf(crew())};
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setInteger("sel", sel); t.setInteger("rT", runsToday); t.setInteger("rA", runsTotal); t.setInteger("lastRun", lastRun); t.setInteger("respN", respN);
        t.setLong("respS", respSum); t.setLong("respL", respLast); t.setLong("dayS", daySeen); t.setString("last", last); t.setString("place", place);
    }

    @Override
    protected void load(NBTTagCompound t) {
        sel = t.getInteger("sel"); runsToday = t.getInteger("rT"); runsTotal = t.getInteger("rA"); lastRun = t.hasKey("lastRun") ? t.getInteger("lastRun") : -1; respN = t.getInteger("respN");
        respSum = t.getLong("respS"); respLast = t.hasKey("respL") ? t.getLong("respL") : -1; daySeen = t.hasKey("dayS") ? t.getLong("dayS") : -1; last = t.getString("last"); place = t.getString("place");
    }
}
