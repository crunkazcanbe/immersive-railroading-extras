package com.dogpound.railmap.crew;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.depot.Crew;
import com.dogpound.railmap.depot.DepotClock;
import com.dogpound.railmap.depot.DepotConfig;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Crew Office (batch 5 #7): where railway crew sign on. A three-block room against a wall: the ROSTER BOARD (every
 * crew member: role, state, shift diagram, train / beat, fatigue), the SIGN-ON TERMINAL (the selected person in
 * detail: what they're doing now, diagram, competency - traction passes + route knowledge from the Crew Booking-On
 * Desk and the Driver Competency Assessor - hours, rest, checks), four real crew lockers, a staff fridge, the desk
 * drawer, a notice board ledge that shows what you pin on it, and the break room: a kettle you fill with a water
 * bucket and BOIL on the mains (crew signing on or on a break here get a brew, which eases their fatigue; TEA gives you
 * one). HIRE takes on a driver / guard / inspector (ROLE selector), SHIFT and LEN set their diagram, ALLOC puts the
 * selected driver or guard on the selected train (the competency check is shown), BEAT gives a guard or inspector a
 * line, SIGN ON / SIGN OFF / BREAK by hand, the AUTO key runs the diagrams. 12 V control + 230 V mains.
 */
public class TileCrewOffice extends StnFurniture {
    static double[][] SPOTS = {{-12.017, 20.6, 14.0}, {-10.05, 20.6, 14.0}, {-8.083, 20.6, 14.0}, {-6.117, 20.6, 14.0}, {-4.15, 20.6, 14.0}, {-2.183, 20.6, 14.0}};   // tools/gen_bb_crew.py
    private int scur, tcur, lcur;
    private String lastMsg = "", dismissArm = "";
    private long dismissAt, testUntil, boilUntil;
    private double water;               // litres in the kettle (1.7 max)
    private int cups;                   // brewed and still hot
    private long brewedAt;
    private boolean unloading;

    public TileCrewOffice() {
        super(new String[]{"lk1", "lk2", "lk3", "lk4", "fr", "dr"}, new String[]{"Crew locker 1", "Crew locker 2", "Crew locker 3", "Crew locker 4", "Staff fridge", "Desk drawer"},
                new int[]{27, 27, 27, 27, 9, 9}, 6);
    }

    @Override public String wiredName() { return "Crew office"; }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : onDuty() + " crew on duty" + (cups > 0 ? " · tea brewed" : ""); }
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_") || id.startsWith("s_"); }

    private CrewData data() { return CrewData.get(world); }
    List<CrewData.Staff> staff() { return new ArrayList<>(data().staff.values()); }
    CrewData.Staff sel() { List<CrewData.Staff> l = staff(); if (l.isEmpty()) return null; scur = Math.floorMod(scur, l.size()); return l.get(scur); }
    List<UUID> trains() { List<UUID> l = new ArrayList<>(RailwayData.get(world).trains().keySet()); l.sort(Comparator.comparing(u -> FleetData.get(world).unit(u).name)); return l; }
    UUID train() { List<UUID> l = trains(); if (l.isEmpty()) return null; tcur = Math.floorMod(tcur, l.size()); return l.get(tcur); }
    String line() { List<String> l = new ArrayList<>(RailwayData.get(world).lines().keySet()); Collections.sort(l); l.add(0, ""); lcur = Math.floorMod(lcur, l.size()); return l.get(lcur); }
    int onDuty() { int n = 0; for (CrewData.Staff s : data().staff.values()) if (on(s)) n++; return n; }

    boolean on(CrewData.Staff s) {
        if (s.role != CrewData.DRIVER) return s.onDuty;
        FleetData.Driver d = FleetData.get(world).drivers.get(s.name);
        return d != null && d.onDuty;
    }

    @Override public int controlValue(String id) { return term.powered() && ctl.get("lamptest") == 1 && !mechanical(id) && !id.startsWith("lk") ? Math.max(1, ctl.get(id)) : super.controlValue(id); }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        if (id.equals("k_auto")) {
            CrewData c = data();
            c.autoRoster = !c.autoRoster;
            c.markDirty();
            ctl.set(id, c.autoRoster ? 1 : 0);
            FleetData.get(world).crewNote(world, "Crew diagrams " + (c.autoRoster ? "RUN AUTOMATICALLY" : "on manual") + " (" + StnFurniture.who(who) + ")");
            return c.autoRoster ? "§aAUTO ROSTER: crew sign on and off by their diagrams" : "§eManual roster: sign crew on and off here";
        }
        return null;                                                           // the ROLE selector just turns
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        CrewData c = data();
        FleetData f = FleetData.get(world);
        CrewData.Staff s = sel();
        long now = world.getTotalWorldTime();
        String r = switch (id) {
            case "test" -> { testUntil = now + 60; ctl.set("lamptest", 1); yield "§7Lamp test"; }
            case "prev" -> { scur--; yield ""; }
            case "next" -> { scur++; yield ""; }
            case "tprev" -> { tcur--; yield ""; }
            case "tnext" -> { tcur++; yield ""; }
            case "lprev" -> { lcur--; yield ""; }
            case "lnext" -> { lcur++; yield ""; }
            case "hire" -> {
                int role = 1 + Math.floorMod(ctl.get("s_role"), 3);
                CrewData.Staff n = CrewTicker.hire(world, role, null);
                n.office = pos.toLong();
                scur = staff().indexOf(n);
                yield "§a" + n.name + " hired - set a SHIFT and ALLOC" + (role == CrewData.INSPECTOR ? " a BEAT" : " a train");
            }
            case "sh-", "sh+" -> {
                if (s == null) yield "§7Hire someone first";
                s.start = s.start < 0 ? 6 : Math.floorMod(s.start + (id.equals("sh+") ? 1 : -1), 24);
                c.markDirty();
                yield "§e" + s.name + " diagram " + shift(s);
            }
            case "ln-", "ln+" -> {
                if (s == null) yield "§7Hire someone first";
                s.hours = Math.max(2, Math.min(DepotConfig.dutyMaxHours, s.hours + (id.equals("ln+") ? 1 : -1)));
                c.markDirty();
                yield "§e" + s.name + " diagram " + shift(s);
            }
            case "nodiag" -> { if (s == null) yield "§7Nobody selected"; s.start = -1; c.markDirty(); yield "§e" + s.name + ": no diagram (signed on by hand)"; }
            case "alloc" -> {
                UUID t = train();
                if (s == null || t == null) yield "§7Select a crew member and a train";
                if (s.role == CrewData.INSPECTOR) yield "§7Inspectors work a BEAT, not a train";
                s.train = t;
                c.markDirty();
                if (s.role == CrewData.DRIVER) {
                    FleetData.Driver d = f.drivers.get(s.name);
                    if (d == null) yield "§cNo depot record for " + s.name;
                    Locomotive l = Autopilot.find(world, t);
                    RailwayData.AutoTrain a = RailwayData.get(world).train(t);
                    String no = Crew.cannot(world, d, l, a == null ? "" : a.line);
                    String res = Crew.assign(world, f, d, t, "crew office");
                    yield no == null ? res : res + " §6(" + no + ")";
                }
                for (CrewData.Staff g : c.staff.values()) if (g != s && g.role == CrewData.GUARD && t.equals(g.train)) { g.train = null; f.crewNote(world, "GUARD HANDOVER " + f.unit(t).name + ": " + g.name + " -> " + s.name); }
                f.crewNote(world, s.name + " (guard) rostered to " + f.unit(t).name);
                yield "§a" + s.name + " guards " + f.unit(t).name;
            }
            case "beat" -> {
                if (s == null) yield "§7Nobody selected";
                s.line = line();
                c.markDirty();
                yield "§e" + s.name + (s.line.isEmpty() ? " works any line" : " works the " + s.line);
            }
            case "signon" -> {
                if (s == null) yield "§7Nobody selected";
                if (!Rules.rested(now, s.lastOff, CrewConfig.restHours)) yield "§c" + s.name + " has not had " + CrewConfig.restHours + " h rest since signing off";
                if (s.role == CrewData.DRIVER) { FleetData.Driver d = f.drivers.get(s.name); yield d == null ? "§cNo depot record" : Crew.signOn(world, f, d); }
                if (s.onDuty) yield s.name + " is already on duty";
                s.onDuty = true; s.dutyStart = now; c.markDirty();
                f.crewNote(world, s.name + " (" + CrewData.ROLE[s.role].toLowerCase(Locale.ROOT) + ") SIGNED ON at the crew office");
                CrewTicker.Live L = CrewTicker.live(world, s.name);
                L.phase = CrewTicker.OFF;                                       // the ticker starts them at the office
                yield "§a" + s.name + " signed on at " + DepotClock.hhmm(world);
            }
            case "signoff" -> {
                if (s == null) yield "§7Nobody selected";
                if (s.role == CrewData.DRIVER) { FleetData.Driver d = f.drivers.get(s.name); if (d == null) yield "§cNo depot record"; s.lastOff = now; c.markDirty(); yield Crew.signOff(world, f, d); }
                if (!s.onDuty) yield s.name + " is not on duty";
                CrewTicker.signOff(world, c, f, s, CrewTicker.live(world, s.name), now);
                yield "§e" + s.name + " signed off";
            }
            case "brk" -> {
                if (s == null) yield "§7Nobody selected";
                if (s.role == CrewData.DRIVER) { FleetData.Driver d = f.drivers.get(s.name); yield d == null ? "§cNo depot record" : Crew.takeBreak(world, f, d); }
                s.breakUntil = now + Math.round(DepotConfig.breakMinutes * DepotClock.TICKS_PER_HOUR / 60);
                c.markDirty();
                yield "§e" + s.name + " on a " + DepotConfig.breakMinutes + " minute break";
            }
            case "dismiss" -> {
                if (s == null) yield "§7Nobody selected";
                if (!s.name.equals(dismissArm) || now - dismissAt > 60) { dismissArm = s.name; dismissAt = now; yield "§6Press DISMISS again within 3 s to let " + s.name + " go"; }
                dismissArm = "";
                yield CrewTicker.dismiss(world, s.name);
            }
            case "print" -> {
                List<String> out = new ArrayList<>();
                out.add("CREW ROSTER " + DepotClock.stamp(world));
                for (CrewData.Staff x : c.staff.values())
                    out.add(CrewData.ROLE[x.role] + " " + x.name + " " + shift(x) + " " + (x.role == CrewData.INSPECTOR ? "beat " + (x.line.isEmpty() ? "any" : x.line) : trainOf(x)) + (on(x) ? " ON DUTY" : ""));
                out.add("");
                out.add("DUTY LOG");
                out.addAll(f.crewLog);
                StnUtil.print(world, pos, controlFacing(), "Crew roster", "Crew office", out);
                yield "§aRoster printed";
            }
            case "kettle" -> {
                if (who == null) yield "§7Fill the kettle by hand";
                ItemStack h = who.getHeldItemMainhand();
                if (h.getItem() != Items.WATER_BUCKET) yield water > 0.2 ? String.format(Locale.ROOT, "§7Kettle: %.1f L", water) : "§7The kettle is empty - hold a water bucket and click it";
                water = 1.7;
                if (!who.capabilities.isCreativeMode) who.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.tap"), SoundCategory.BLOCKS, 0.6f, 1.1f);
                yield "§aKettle filled (1.7 L)";
            }
            case "boil" -> {
                if (water < 0.3) yield "§cThe kettle is empty - fill it with a water bucket";
                if (boilUntil > now) yield "§7It's already on";
                boilUntil = now + 600;
                yield "§aKettle on (3 kW)";
            }
            case "tea" -> {
                if (cups <= 0) yield "§7No tea brewed - BOIL the kettle";
                if (who == null) yield "§7Tea is drunk in person";
                cups--;
                who.addPotionEffect(new PotionEffect(MobEffects.SPEED, 1200, 0, false, true));
                who.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 200, 0, false, true));
                yield "§aA proper brew. Ahh.";
            }
            default -> null;
        };
        if (r != null && !r.isEmpty()) lastMsg = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(r);
        return r;
    }

    String trainOf(CrewData.Staff s) {
        FleetData f = FleetData.get(world);
        UUID t = s.role == CrewData.DRIVER ? (f.drivers.get(s.name) == null ? s.train : f.drivers.get(s.name).train) : s.train;
        return t == null ? "SPARE" : f.unit(t).name;
    }

    static String shift(CrewData.Staff s) { return s.start < 0 ? "NO DIAG" : String.format(Locale.ROOT, "%02d-%02d", s.start, (s.start + s.hours) % 24); }

    @Override
    protected void work() {
        long now = world.getTotalWorldTime();
        if (testUntil > 0 && now > testUntil) { testUntil = 0; ctl.set("lamptest", 0); controlsChanged(); }
        if (boilUntil > 0 && now >= boilUntil) {                                // boiled: a pot of tea, a cup per 0.25 L
            boilUntil = 0;
            if (mains.ok()) {
                cups = (int) Math.floor(water / 0.25);
                water = 0;
                brewedAt = now;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.4f, 1.8f);
            }
        } else if (boilUntil > now && !mains.ok()) boilUntil = 0;              // power cut: the kettle goes cold
        if (cups > 0 && now - brewedAt > 6000) cups = 0;                     // stewed and cold
        if (age % 100 == 17) {
            CrewData c = data();
            if (c.offices.add(pos.toLong())) c.markDirty();
            if (cups > 0)                                                      // crew signing on or on a break here get a cup
                for (CrewData.Staff s : c.staff.values()) {
                    CrewTicker.Live L = CrewTicker.all(world).get(s.name);
                    if (L == null || cups <= 0 || s.tea > 0.5 || L.at == null || pos.distanceSq(L.at[0], L.at[1], L.at[2]) > 36) continue;
                    if (L.phase == CrewTicker.OFFICE || L.phase == CrewTicker.BREAK) { s.tea = 1; cups--; c.markDirty(); L.action = CrewTicker.A_TEA; L.actionUntil = now + 200; }
                }
        }
    }

    @Override
    protected void serverTick() {
        if (mains.tick(world, pos, term, wiredName(), term.powered() ? 4 + (boilUntil > world.getTotalWorldTime() ? 150 : 0) : 0)) controlsChanged();
        work();
    }

    @Override
    protected boolean refresh() {
        CrewData c = data();
        FleetData f = FleetData.get(world);
        boolean ch = StationControls.put(ctl, "k_auto", c.autoRoster);
        List<CrewData.Staff> ss = staff();
        if (!ss.isEmpty()) scur = Math.floorMod(scur, ss.size());
        int top = ss.isEmpty() ? 0 : scur / 8 * 8, hour = DepotClock.hour(world);
        boolean over = false, tired = false, rest = false, noGuard = false;
        long now = world.getTotalWorldTime();
        for (CrewData.Staff s : ss) {
            double fa = CrewTicker.fatigue(world, s);
            if (fa > 1.0) tired = true;
            if (on(s) && (s.role == CrewData.DRIVER ? f.drivers.get(s.name) != null && Crew.dutyHours(world, f.drivers.get(s.name)) >= DepotConfig.dutyMaxHours : DepotClock.hours(s.dutyStart, now) >= DepotConfig.dutyMaxHours)) over = true;
            if (!on(s) && Rules.inShift(hour, s.start, s.hours) && !Rules.rested(now, s.lastOff, CrewConfig.restHours)) rest = true;
            if (s.role == CrewData.GUARD && s.train != null && !s.onDuty && Rules.inShift(hour, s.start, s.hours)) noGuard = true;
        }
        for (int i = 0; i < 8; i++) {
            CrewData.Staff s = top + i < ss.size() ? ss.get(top + i) : null;
            String row = "";
            if (s != null) {
                CrewTicker.Live L = CrewTicker.all(world).get(s.name);
                String st = !on(s) ? "OFF" : L == null ? "ON" : CrewTicker.PHASE[L.phase].split(" ")[0];
                row = String.format(Locale.ROOT, "%-3s %-11s %-8s %-7s %-11s %3d%%", CrewData.ROLE[s.role].substring(0, 3), cut(s.name.toUpperCase(Locale.ROOT), 11), cut(st, 8), shift(s),
                        cut((s.role == CrewData.INSPECTOR ? (s.line.isEmpty() ? "ANY LINE" : s.line) : trainOf(s)).toUpperCase(Locale.ROOT), 11), Math.round(CrewTicker.fatigue(world, s) * 100));
            }
            ch |= StationControls.text(ctl, "r" + (i + 1), row);
            ch |= StationControls.put(ctl, "r" + (i + 1) + "s", s != null && top + i == scur);
        }
        CrewData.Staff s = sel();
        String[] t = new String[6];
        Arrays.fill(t, "");
        if (s == null) t[0] = "NO CREW - SET ROLE AND PRESS HIRE";
        else {
            CrewTicker.Live L = CrewTicker.all(world).get(s.name);
            t[0] = CrewData.ROLE[s.role] + " " + s.name.toUpperCase(Locale.ROOT) + (on(s) ? " - ON DUTY" : " - OFF");
            t[1] = L == null || !on(s) ? "OFF DUTY" : (CrewTicker.PHASE[L.phase] + (L.doing.isEmpty() ? "" : " - " + L.doing)).toUpperCase(Locale.ROOT);
            t[2] = "DIAGRAM " + shift(s) + (s.start >= 0 ? " " + s.hours + "H" : "") + " - " + (s.role == CrewData.INSPECTOR ? "BEAT " + (s.line.isEmpty() ? "ANY" : s.line) : trainOf(s)).toUpperCase(Locale.ROOT);
            if (s.role == CrewData.DRIVER) {
                FleetData.Driver d = f.drivers.get(s.name);
                t[3] = d == null ? "NO DEPOT RECORD" : "PASSED " + ((d.quals & 1) != 0 ? "D" : "-") + ((d.quals & 2) != 0 ? "E" : "-") + ((d.quals & 4) != 0 ? "S" : "-") + " ROUTES " + d.routes.size()
                        + String.format(Locale.ROOT, " DUTY %.1fH", d == null ? 0 : Crew.dutyHours(world, d));
            } else t[3] = String.format(Locale.ROOT, "DUTY %.1fH", s.onDuty ? DepotClock.hours(s.dutyStart, now) : 0);
            t[4] = String.format(Locale.ROOT, "FATIGUE %d%% REST %s TEA %s", Math.round(CrewTicker.fatigue(world, s) * 100), Rules.rested(now, s.lastOff, CrewConfig.restHours) ? "OK" : "SHORT", s.tea > 0 ? "YES" : "NO");
            t[5] = s.role == CrewData.INSPECTOR ? "CHECKS " + s.checks + " CAUGHT " + s.caught + " FINES " + com.dogpound.railmap.economy.Coins.fmt(s.fined) : s.mistakes > 0 ? "MISTAKES " + s.mistakes : "";
        }
        for (int i = 0; i < 6; i++) ch |= StationControls.text(ctl, "t" + (i + 1), cut(t[i], 40));
        UUID tr = train();
        String l = line();
        ch |= StationControls.text(ctl, "tsel", tr == null ? "NO TRAINS" : cut(f.unit(tr).name.toUpperCase(Locale.ROOT), 16));
        ch |= StationControls.text(ctl, "lsel", l.isEmpty() ? "ANY LINE" : cut(l.toUpperCase(Locale.ROOT), 12));
        ch |= StationControls.text(ctl, "rolesel", CrewData.ROLE[1 + Math.floorMod(ctl.get("s_role"), 3)]);
        ch |= StationControls.text(ctl, "clock", DepotClock.stamp(world));
        ch |= StationControls.text(ctl, "msg", cut(lastMsg, 40));
        ch |= StationControls.put(ctl, "ndty", onDuty());
        ch |= StationControls.put(ctl, "pwr", term.powered());
        ch |= StationControls.put(ctl, "overh", over) | StationControls.put(ctl, "fatl", tired) | StationControls.put(ctl, "restl", rest) | StationControls.put(ctl, "nogd", noGuard);
        ch |= StationControls.put(ctl, "boill", boilUntil > now) | StationControls.put(ctl, "teal", cups > 0) | StationControls.put(ctl, "autol", c.autoRoster);
        ch |= StationControls.put(ctl, "waterg", (int) Math.round(water / 1.7 * 100));
        ch |= StationControls.text(ctl, "cups", cups > 0 ? cups + " CUPS" : water > 0.2 ? String.format(Locale.ROOT, "%.1f L", water) : "EMPTY");
        return ch;
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("sc", scur); t.setInteger("tc", tcur); t.setInteger("lc", lcur); t.setDouble("water", water); t.setInteger("cups", cups); t.setLong("brew", brewedAt); }
    @Override protected void load(NBTTagCompound t) { scur = t.getInteger("sc"); tcur = t.getInteger("tc"); lcur = t.getInteger("lc"); water = t.getDouble("water"); cups = t.getInteger("cups"); brewedAt = t.getLong("brew"); }

    @Override public void onChunkUnload() { unloading = true; super.onChunkUnload(); }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote && !unloading) { CrewData c = CrewData.get(world); if (c.offices.remove(pos.toLong())) c.markDirty(); }
    }
}
