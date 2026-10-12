package com.dogpound.railmap.yard;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.sim.AdhesionSim;
import com.dogpound.railmap.sim.ConsistSim;
import com.dogpound.railmap.sim.EngineSim;
import com.dogpound.railmap.sim.SimData;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.Terminal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * Locomotive Diagnostic Panel (her ChatGPT list item 8: "fault codes readable from a locomotive diagnostic panel", item
 * 12: "adhesion-control settings on locomotive control panels"). A depot test set plugged into the nearest locomotive
 * within 12 blocks (SELECT steps through the others): live gauges (rpm, coolant, oil pressure, boost, battery volts,
 * traction motor temperature, fuel, adhesion), a four-page screen (ENGINE, ADHESION, CONSIST, FAULTS), START / STOP the
 * engine (through IR's own engine switch, so the start sequence runs), FAULT RESET, a SAND test, PRINT (the fault log),
 * and the switches that really set the locomotive: AESS, RUN / ISOLATE, auto sanders, traction control, WSP. Needs 12 V.
 */
public class TileLocoDiag extends TileEntity implements ITickable, IControlled, IWiredGear {
    private static final double REACH = 12;
    private final ControlState ctl = new ControlState();
    private final Terminal term = new Terminal();
    private UUID linked;

    @Override public ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); } }
    @Override public int controlValue(String id) { return term.powered() ? ctl.get(id) : 0; }
    @Override public String ptext(String key) { return term.powered() ? ctl.text(key) : ""; }

    private List<Locomotive> inReach() {
        List<Locomotive> out = new ArrayList<>();
        for (Locomotive l : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) {
            Vec3d p = l.getPosition();
            if (!l.isDead() && pos.distanceSqToCenter(p.x, p.y, p.z) <= REACH * REACH) out.add(l);
        }
        out.sort(Comparator.comparingDouble(l -> pos.distanceSqToCenter(l.getPosition().x, l.getPosition().y, l.getPosition().z)));
        return out;
    }

    private Locomotive loco() {
        List<Locomotive> l = inReach();
        if (l.isEmpty()) return null;
        if (linked != null) for (Locomotive x : l) if (x.getUUID().equals(linked)) return x;
        linked = l.get(0).getUUID();
        return l.get(0);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (!term.powered()) return "§cThe test set is dark - no 12 V";
        if (id.equals("page")) return null;
        Locomotive l = loco();
        if (id.equals("sel")) {
            List<Locomotive> all = inReach();
            if (all.isEmpty()) return "§7No locomotive within 12 blocks";
            int i = 0;
            for (int k = 0; k < all.size(); k++) if (all.get(k).getUUID().equals(linked)) i = (k + 1) % all.size();
            linked = all.get(i).getUUID();
            refresh();
            return "§bConnected to " + name(all.get(i)) + " (" + (i + 1) + "/" + all.size() + ")";
        }
        if (l == null) return "§7No locomotive within 12 blocks to plug into";
        SimData data = SimData.get(world);
        SimData.Car c = data.of(l.getUUID());
        String r;
        switch (id) {
            case "start":
                if (!(l instanceof LocomotiveDiesel d)) return "§7Not a diesel";
                if (c.isolated) return "§cEngine switch is on ISOLATE";
                d.setTurnedOn(true);
                r = "§aEngine start requested - " + EngineSim.family(d).label + " start sequence";
                break;
            case "stop":
                if (!(l instanceof LocomotiveDiesel d2)) return "§7Not a diesel";
                d2.setTurnedOn(false);
                c.aessStopped = false;
                r = "§eEngine stop";
                break;
            case "reset":
                r = l instanceof LocomotiveDiesel d3 ? EngineSim.reset(world, d3) : "§7No engine faults on this unit";
                break;
            case "sandp":
                c.sandHold = 3;
                r = l.prideSandLevel() <= 0 && l.prideSandLevel() > -1 ? "§cSandbox empty - fill slot 3 with sand" : "§7Sanders on for 3 s";
                break;
            case "print":
                if (who != null) {
                    who.sendMessage(new TextComponentString("§b--- " + name(l) + " fault log ---"));
                    if (l instanceof LocomotiveDiesel d4) {
                        who.sendMessage(new TextComponentString("§7" + EngineSim.line1(d4, c)));
                        who.sendMessage(new TextComponentString("§7" + EngineSim.line2(d4, c)));
                    }
                    who.sendMessage(new TextComponentString(String.format(Locale.ROOT, "§7%.1f engine hours, %d starts · oil %d%% coolant %d%% battery %d%%",
                            c.hours, c.starts, Math.round(c.oil * 100), Math.round(c.coolant * 100), Math.round(c.battery * 100))));
                    if (c.faultLog.isEmpty()) who.sendMessage(new TextComponentString("§aNo faults logged"));
                    for (String f : c.faultLog) who.sendMessage(new TextComponentString((c.faults.stream().anyMatch(x -> f.contains(x)) ? "§c" : "§8") + f));
                }
                r = "§7Fault log printed";
                break;
            case "aess": c.aess = !c.aess; r = "§7AESS " + (c.aess ? "ON" : "OFF"); break;
            case "iso":
                c.isolated = !c.isolated;
                r = c.isolated ? "§eEngine ISOLATED - it idles but gives no power" : "§aEngine on RUN";
                break;
            case "sand": c.autoSand = !c.autoSand; r = "§7Automatic sanders " + (c.autoSand ? "ON" : "OFF"); break;
            case "tc": c.tractionControl = !c.tractionControl; r = "§7Traction control " + (c.tractionControl ? "ON" : "OFF"); break;
            case "wsp": c.wsp = !c.wsp; r = "§7Wheel-slide protection " + (c.wsp ? "ON" : "OFF"); break;
            default: return "";
        }
        data.markDirty();
        refresh();
        return r;
    }

    @Override
    public void update() {
        if (world.isRemote) return;
        if (term.tick(world, pos, wiredName())) controlsChanged();
        long now = world.getTotalWorldTime();
        if (now % 10 == (pos.toLong() & 7)) {
            try { refresh(); } catch (LinkageError | RuntimeException ex) { com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] loco diag: {}", ex.toString()); }
        }
    }

    private void refresh() {
        if (!term.powered()) return;
        boolean ch = false;
        Locomotive l = loco();
        SimData.Car c = l == null ? null : SimData.get(world).peek(l.getUUID());
        LocomotiveDiesel d = l instanceof LocomotiveDiesel x ? x : null;
        ch |= StationControls.put(ctl, "power", true);
        ch |= StationControls.put(ctl, "llink", l != null);
        ch |= StationControls.put(ctl, "lrun", d != null && c != null && c.phase == EngineSim.RUN);
        ch |= StationControls.put(ctl, "lslip", c != null && c.slip);
        ch |= StationControls.put(ctl, "lslide", c != null && (c.slide || c.wspActive));
        ch |= StationControls.put(ctl, "lflt", c != null && !c.faults.isEmpty());
        ch |= StationControls.put(ctl, "lhot", d != null && d.getEngineTemperature() > 100 || c != null && !Double.isNaN(c.tmC) && c.tmC > 170);
        ch |= StationControls.put(ctl, "loil", c != null && d != null && c.phase == EngineSim.RUN && c.oilKpa < 160);
        ch |= StationControls.put(ctl, "lbat", c != null && c.battery < 0.3f);
        ch |= StationControls.put(ctl, "rpm", c == null ? 0 : (int) Math.round(c.rpm));
        ch |= StationControls.put(ctl, "temp", d == null ? 0 : Math.round(d.getEngineTemperature()));
        ch |= StationControls.put(ctl, "oil", c == null ? 0 : (int) Math.round(c.oilKpa));
        ch |= StationControls.put(ctl, "boost", c == null ? 0 : (int) Math.round(c.boostKpa));
        ch |= StationControls.put(ctl, "bat", c == null ? 0 : (int) Math.round(EngineSim.volts(c)));
        ch |= StationControls.put(ctl, "tm", c == null || Double.isNaN(c.tmC) ? 0 : (int) Math.round(c.tmC));
        int fuel = 0;
        if (d != null) {
            try { fuel = (int) Math.round(100.0 * d.getLiquidAmount() / Math.max(1, d.getTankCapacity().MilliBuckets())); } catch (RuntimeException ignored) { }
        }
        ch |= StationControls.put(ctl, "fuel", fuel);
        ch |= StationControls.put(ctl, "adh", l == null ? 0 : Math.round(l.adhesionCoefficient() * 100));
        ch |= StationControls.put(ctl, "aess", c != null && c.aess);
        ch |= StationControls.put(ctl, "iso", c != null && c.isolated);
        ch |= StationControls.put(ctl, "sand", c != null && c.autoSand);
        ch |= StationControls.put(ctl, "tc", c != null && c.tractionControl);
        ch |= StationControls.put(ctl, "wsp", c != null && c.wsp);
        String[] t = new String[5];
        if (l == null) {
            t[0] = "NO LOCOMOTIVE";
            t[1] = "WITHIN 12 BLOCKS";
        } else {
            t[0] = cut(name(l).toUpperCase(Locale.ROOT), 26);
            switch (ctl.get("page")) {
                case 0:
                    if (d == null || c == null) { t[1] = "NO ENGINE DATA"; break; }
                    t[1] = String.format(Locale.ROOT, "%s %d RPM N%d", c.aessStopped ? "AESS STOP" : new String[]{"SHUT DOWN", "PRELUBE", "CRANKING", "RUNNING"}[Math.max(0, Math.min(3, c.phase))],
                            Math.round(c.rpm), EngineSim.notch(d));
                    t[2] = String.format(Locale.ROOT, "COOL %dC%s OIL %dKPA", Math.round(d.getEngineTemperature()), c.fanHigh ? " F2" : c.fanLow ? " F1" : "", Math.round(c.oilKpa));
                    t[3] = String.format(Locale.ROOT, "BOOST %dKPA %dL/H", Math.round(c.boostKpa), Math.round(c.fuelLph));
                    t[4] = c.derate < 1 ? String.format(Locale.ROOT, "POWER %d%% %s", Math.round(c.derate * 100), c.derateWhy.toUpperCase(Locale.ROOT))
                            : String.format(Locale.ROOT, "BATT %.1fV TM %dC", EngineSim.volts(c), Double.isNaN(c.tmC) ? 0 : Math.round(c.tmC));
                    break;
                case 1:
                    t[1] = String.format(Locale.ROOT, "ADHESION %d%% %s", Math.round(l.adhesionCoefficient() * 100), c == null ? "" : c.railWhy.toUpperCase(Locale.ROOT));
                    t[2] = (c != null && c.slip ? "SLIP " : "") + (c != null && c.slide ? "SLIDE " : "") + (c != null && c.wspActive ? "WSP ACTIVE" : c != null && !c.slip && !c.slide ? "WHEELS OK" : "");
                    float sand = l.prideSandLevel();
                    t[3] = sand < 0 ? "NO SANDBOX" : String.format(Locale.ROOT, "SAND %d%% %s", Math.round(sand * 100), l.isSanding ? "SANDING" : "");
                    t[4] = c == null ? "" : String.format(Locale.ROOT, "FLAT %dMM TC %s WSP %s", Math.round(c.flatMm), c.tractionControl ? "ON" : "OFF", c.wsp ? "ON" : "OFF");
                    break;
                case 2: {
                    ConsistSim.Summary s = ConsistSim.summary(world, l);
                    t[1] = String.format(Locale.ROOT, "%,.0fT %d CARS %dM", s.tonnes, s.cars, Math.round(s.lengthM));
                    t[2] = String.format(Locale.ROOT, "GRADE %+.1f%% BRAKED %d%%", s.gradePct, Math.round(s.brakedPct));
                    t[3] = String.format(Locale.ROOT, "DRAFT %dKN BUFF %dKN", Math.round(s.maxDraftN / 1000), Math.round(s.maxBuffN / 1000));
                    t[4] = String.format(Locale.ROOT, "KNUCKLE %d%% TE %d/%dKN", Math.round(s.worstCoupler * 100), Math.round(s.teUsedN / 1000), Math.round(s.teAvailN / 1000));
                    break;
                }
                case 4: {                                   // her list item 108: auxiliary systems
                    if (c == null) { t[1] = "NO AUX DATA"; break; }
                    t[1] = String.format(Locale.ROOT, "AUX %s %d/%dKW", c.auxSrc, Math.round(c.auxKw), Math.round(c.auxCap));
                    t[2] = String.format(Locale.ROOT, "MR %.1fBAR COMP %s", l.getMainAirReservoir() * 10, c.compTrip ? "TRIP" : c.compRun ? "RUN" : c.compressor == 1 ? "AUTO" : "OFF");
                    t[3] = String.format(Locale.ROOT, "BAT %s %.1fV CAB %dC", c.batteryOn ? "ON" : "OFF", com.dogpound.railmap.sim.AuxSim.volts(c), Math.round(Double.isNaN(c.cabC) ? 0 : c.cabC));
                    t[4] = String.format(Locale.ROOT, "HL %s HVAC %s%s", new String[]{"OFF", "DIM", "BRT"}[Math.max(0, Math.min(2, c.headlight))], c.hvac ? "ON" : "OFF", c.convTrip ? " CONV TRIP" : "");
                    break;
                }
                case 5: {                                   // her list item 106: the MU consist
                    java.util.List<String> mu = com.dogpound.railmap.sim.MultiUnit.lines(world, l);
                    if (mu.isEmpty()) { t[1] = "SINGLE UNIT"; t[2] = "MU " + com.dogpound.railmap.sim.MultiUnit.system(world, l); break; }
                    for (int i = 0; i < 4 && i < mu.size(); i++) t[i + 1] = cut(mu.get(i).toUpperCase(Locale.ROOT), 26);
                    break;
                }
                case 6: {                                   // her 48-next #5: telemetry trends per operating hour
                    com.dogpound.railmap.depot.FleetData.Unit u = com.dogpound.railmap.depot.FleetData.get(world).peek(l.getUUID());
                    if (u == null || u.tele.size() < 4) { t[1] = "TREND: COLLECTING"; t[2] = (u == null ? 0 : u.tele.size()) + " SAMPLES (30 S EACH)"; break; }
                    for (int k = 0; k < 4; k++) {
                        double v = com.dogpound.railmap.depot.Telemetry.last(u, k), sl = com.dogpound.railmap.depot.Telemetry.slope(u, k);
                        t[k + 1] = String.format(Locale.ROOT, "%s %s %s/H", com.dogpound.railmap.depot.Telemetry.CH[k], Double.isNaN(v) ? "-" : String.valueOf(Math.round(v)),
                                Double.isNaN(sl) ? "-" : String.format(Locale.ROOT, "%+.1f", sl));
                    }
                    break;
                }
                case 7: {                                   // predictive P-codes + remaining life
                    com.dogpound.railmap.depot.FleetData.Unit u = com.dogpound.railmap.depot.FleetData.get(world).peek(l.getUUID());
                    if (u == null || u.pcodes.isEmpty()) {
                        t[1] = "NO PREDICTIVE CODES";
                        double h = u == null ? -1 : com.dogpound.railmap.depot.Telemetry.rulHours(u);
                        t[2] = h < 0 ? "RUL: NOT ENOUGH DATA" : String.format(Locale.ROOT, "FIRST FAILURE IN ~%.1f H", h);
                        break;
                    }
                    int i = 1;
                    for (String f : u.pcodes) { if (i > 4) break; t[i++] = cut(f, 26); }
                    break;
                }
                default: {
                    if (c == null || c.faults.isEmpty()) { t[1] = "NO ACTIVE FAULTS"; t[2] = c == null ? "" : String.format(Locale.ROOT, "%.1f H %d STARTS", c.hours, c.starts); break; }
                    int i = 1;
                    for (String f : c.faults) { if (i > 4) break; t[i++] = cut(f.toUpperCase(Locale.ROOT), 26); }
                }
            }
        }
        for (int i = 0; i < 5; i++) ch |= StationControls.text(ctl, "d" + (i + 1), t[i] == null ? "" : t[i]);
        if (ch) controlsChanged();
    }

    static String cut(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }

    static String name(Locomotive l) {
        if (l.tag != null && !l.tag.isEmpty()) return l.tag;
        return l.getDefinition() == null ? "Loco" : l.getDefinition().name();
    }

    // ---- 12 V + console
    @Override public Terminal term() { return term; }
    @Override public void gearChanged() { controlsChanged(); }
    @Override public String wiredName() { return "Loco diagnostic panel"; }
    @Override public String gearState() {
        Locomotive l = loco();
        if (l == null) return "no locomotive in reach";
        SimData.Car c = SimData.get(world).peek(l.getUUID());
        return name(l) + (c == null ? "" : " · " + c.faults.size() + " faults");
    }
    @Override public int wiredLamp() {
        if (term.burnt || !term.powered()) return 3;
        Locomotive l = loco();
        SimData.Car c = l == null ? null : SimData.get(world).peek(l.getUUID());
        return c != null && !c.faults.isEmpty() ? 3 : l != null ? 1 : 2;
    }
    @Override public Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }
    @Override public void gearInfo(Map<String, Object> m) {
        Locomotive l = loco();
        if (l == null) return;
        m.put("loco", name(l));
        SimData.Car c = SimData.get(world).peek(l.getUUID());
        if (c != null) { m.put("rpm", Math.round(c.rpm)); m.put("faults", new ArrayList<>(c.faults)); m.put("battery", c.battery); }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { super.writeToNBT(t); ctl.write(t); term.write(t); if (linked != null) t.setUniqueId("linked", linked); return t; }
    @Override public void readFromNBT(NBTTagCompound t) { super.readFromNBT(t); ctl.read(t); term.read(t); if (!t.hasKey("term")) term.legacy = false; linked = t.hasUniqueId("linked") ? t.getUniqueId("linked") : null; }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager n, net.minecraft.network.play.server.SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
}
