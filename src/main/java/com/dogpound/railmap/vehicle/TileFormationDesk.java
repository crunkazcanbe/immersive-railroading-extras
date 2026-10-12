package com.dogpound.railmap.vehicle;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.depot.DepotTicker;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.sim.Appearance;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Formation &amp; Livery Desk (her ChatGPT list items 107 + 110): a depot planning desk beside a siding.
 * <ul>
 *   <li>its library holds up to 12 formations, edited on the monitor (click it: the FORMATION EDITOR - IR's whole
 *       vehicle list, order, way round, paint variant, lead cab, livery) with length, mass, braked mass %, power, kW/t,
 *       starting TE / the grade it can start on, top speed and seats worked out from IR's definitions, and checked
 *       against the siding length and ruling grade set on the desk's selectors;</li>
 *   <li>READ copies the train standing on the desk's siding (within 24 blocks) into a new formation;</li>
 *   <li>APPLY sets that train to the formation: paint variants, the lead cab (it leads the MU when nobody drives);</li>
 *   <li>PAINT repaints the train in the selected livery (builder's or the operating company's colour) and resets the
 *       weathering; RESTORE rebuilds damaged body panels - both billed to the company's maintenance account;</li>
 *   <li>PLACE builds the formation on the siding - only with the key at BUILD and a creative / operator player
 *       (it makes stock from nothing).</li>
 * </ul>
 * The board shows the selected formation car by car; lamps show the check result and the train on the siding's dirt
 * and damage. Needs 12 V.
 */
public class TileFormationDesk extends TileFurniture {
    public static final String[] STORE_IDS = {"dl1", "dl2", "dr1", "cup1"};
    public static final String[] STORE_NAMES = {"Drawings drawer", "Livery samples drawer", "Stationery drawer", "Paint store"};
    public static final int[] STORE_SIZES = {9, 9, 9, 27};
    public static final int SHELF = 5;
    private static final double[][] SPOTS = {{-11.72, 7.0, 13.4}, {-9.76, 7.0, 13.4}, {-7.8, 7.0, 13.4}, {-5.84, 7.0, 13.4}, {-3.88, 7.0, 13.4}};
    @Override public double[][] shelfSpots() { return SPOTS; }

    public static final int MAX = 12;
    public static final int[] LENGTHS = {0, 100, 150, 200, 300, 400, 600};
    public static final double[] GRADES = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0};
    static final long PAINT_COST = 5_000, RESTORE_COST = 60;   // cents per car / per % per car

    public final List<Formation> library = new ArrayList<>();

    public TileFormationDesk() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, SHELF); }

    @Override public String wiredName() { return "Formation desk"; }

    public int sel() { return library.isEmpty() ? -1 : Math.floorMod(ctl.get("sel"), library.size()); }
    public Formation current() { int i = sel(); return i < 0 ? null : library.get(i); }
    public double maxLen() { return LENGTHS[Math.floorMod(ctl.get("len"), LENGTHS.length)]; }
    public double grade() { return GRADES[Math.floorMod(ctl.get("grade"), GRADES.length)]; }
    boolean buildKey() { return ctl.get("bkey") == 1; }

    @Override protected boolean mechanical(String id) { return id.equals("bkey"); }

    // ---- the siding --------------------------------------------------------------------------------------------------

    /** the train on the desk's siding: the stock nearest the desk within 24 blocks, as its whole train */
    List<EntityCoupleableRollingStock> train() {
        EntityCoupleableRollingStock best = null;
        double bd = 24 * 24;
        for (EntityCoupleableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityCoupleableRollingStock.class)) {
            Vec3d p = s.getPosition();
            double d = pos.distanceSqToCenter(p.x, p.y, p.z);
            if (!s.isDead() && d < bd) { bd = d; best = s; }
        }
        if (best == null) return Collections.emptyList();
        // walk from one end so the order (and which way each car faces) is the train's own
        EntityCoupleableRollingStock end = best;
        for (EntityCoupleableRollingStock s : best.getTrain(false))
            if (s.getCoupledUUID(CouplerType.FRONT) == null || s.getCoupledUUID(CouplerType.BACK) == null) { end = s; break; }
        List<EntityCoupleableRollingStock> out = new ArrayList<>();
        for (EntityCoupleableRollingStock.DirectionalStock d : end.getDirectionalTrain(false)) out.add(d.stock);
        return out;
    }

    private Map<UUID, Boolean> directions(EntityCoupleableRollingStock end) {
        Map<UUID, Boolean> m = new HashMap<>();
        for (EntityCoupleableRollingStock.DirectionalStock d : end.getDirectionalTrain(false)) m.put(d.stock.getUUID(), d.direction);
        return m;
    }

    /** the IR rail nearest the desk (where PLACE builds) */
    private BlockPos siding() {
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 12; dz++)
                for (int dy = -3; dy <= 2; dy++) {
                    BlockPos p = pos.add(dx, dy, dz);
                    if (w.getBlockEntity(new Vec3i(p.getX(), p.getY(), p.getZ()), TileRailBase.class) == null) continue;
                    double d = p.distanceSq(pos);
                    if (d < bd) { bd = d; best = p; }
                }
        return best;
    }

    // ---- buttons -------------------------------------------------------------------------------------------------------

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        ctl.set("bkey", buildKey() ? 0 : 1);
        controlsChanged();
        return buildKey() ? "§eKey at BUILD - PLACE will build stock (creative / operators only)" : "§7Key at SAFE";
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "edit", "screen" -> {
                if (!(who instanceof EntityPlayerMP mp)) return "§7The editor is on the monitor - click it";
                RailMap.NETWORK.sendTo(new PacketFormation.OpenMsg(pos), mp);
                return "";
            }
            case "prev" -> { if (library.isEmpty()) return "§7The library is empty - NEW or READ a train"; ctl.set("sel", Math.floorMod(sel() - 1, library.size())); return ""; }
            case "next" -> { if (library.isEmpty()) return "§7The library is empty - NEW or READ a train"; ctl.set("sel", Math.floorMod(sel() + 1, library.size())); return ""; }
            case "new" -> {
                if (library.size() >= MAX) return "§cThe library is full (" + MAX + ") - DELETE one first";
                Formation f = new Formation();
                f.name = "FORMATION " + (library.size() + 1);
                library.add(f);
                ctl.set("sel", library.size() - 1);
                markDirty();
                return "§aNew formation - click the monitor to edit it";
            }
            case "del" -> {
                if (library.isEmpty()) return "§7Nothing to delete";
                Formation f = library.remove(sel());
                ctl.set("sel", Math.max(0, sel()));
                markDirty();
                return "§eDeleted " + f.name;
            }
            case "read" -> { return read(); }
            case "check" -> {
                Formation f = current();
                if (f == null) return "§7No formation selected";
                Formation.Stats s = f.stats(maxLen(), grade());
                if (who != null) {
                    say(who, "§b--- " + f.name + " ---");
                    say(who, line1(s));
                    say(who, line2(s));
                    for (String p : s.problems) say(who, "§c✖ " + p);
                    for (String n : s.notes) say(who, "§e• " + n);
                }
                world.playSound(null, pos, s.problems.isEmpty() ? com.dogpound.railmap.ModSounds.BEEP_OK : com.dogpound.railmap.ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.6f, 1.2f);
                return s.problems.isEmpty() ? "§aFormation checks out" : "§c" + s.problems.size() + " problem" + (s.problems.size() > 1 ? "s" : "");
            }
            case "apply" -> { return apply(); }
            case "paint" -> { return paint(who); }
            case "restore" -> { return restore(who); }
            case "place" -> { return place(who); }
            default -> { return null; }
        }
    }

    private static void say(EntityPlayer p, String s) { p.sendMessage(new net.minecraft.util.text.TextComponentString(s)); }

    static String line1(Formation.Stats s) {
        return String.format(Locale.ROOT, "§7%.0f m · %.0f t · braked %.0f%% · %d units (%d powered, %d cab cars) · %d seats", s.lengthM, s.tonnes, s.brakedPct, s.locos, s.powered, s.cabs, s.seats);
    }

    static String line2(Formation.Stats s) {
        return String.format(Locale.ROOT, "§7%.0f kW · %.1f kW/t · start TE %.0f kN · starts on %.1f%% · max %.0f km/h", s.kw, s.kwPerT, s.teKn, s.startGradePct, s.maxKmh);
    }

    private String read() {
        List<EntityCoupleableRollingStock> t = train();
        if (t.isEmpty()) return "§7No train on the siding (within 24 blocks)";
        if (library.size() >= MAX) return "§cThe library is full - DELETE one first";
        Map<UUID, Boolean> dir = directions(t.get(0));
        Formation f = new Formation();
        f.name = "READ " + Recorder.clock(world, world.getTotalWorldTime());
        SimData data = SimData.get(world);
        for (int i = 0; i < t.size(); i++) {
            EntityCoupleableRollingStock s = t.get(i);
            f.cars.add(new Formation.Car(s.getDefinitionID(), !dir.getOrDefault(s.getUUID(), true), s.getTexture()));
            SimData.Car c = data.peek(s.getUUID());
            if (c != null && c.muLead) f.lead = i;
            if (c != null && c.livery == 1) f.livery = 1;
        }
        library.add(f);
        ctl.set("sel", library.size() - 1);
        markDirty();
        return "§aRead " + t.size() + " vehicles off the siding into " + f.name;
    }

    private String apply() {
        Formation f = current();
        if (f == null) return "§7No formation selected";
        List<EntityCoupleableRollingStock> t = train();
        if (t.isEmpty()) return "§7No train on the siding";
        if (t.size() != f.cars.size()) return "§cThe train has " + t.size() + " vehicles, the formation " + f.cars.size();
        // the train may stand either way round: match the formation's order forwards or backwards
        boolean rev = !t.get(0).getDefinitionID().equals(f.cars.get(0).def);
        SimData data = SimData.get(world);
        int n = 0;
        for (int i = 0; i < t.size(); i++) {
            EntityCoupleableRollingStock s = t.get(rev ? t.size() - 1 - i : i);
            Formation.Car c = f.cars.get(i);
            if (!s.getDefinitionID().equals(c.def)) return "§cVehicle " + (i + 1) + " is a " + Formation.shortName(s.getDefinition()) + ", the formation wants " + Formation.shortName(c.definition());
            if (!c.texture.isEmpty() && !c.texture.equals(s.getTexture())) { s.setTexture(c.texture); n++; }
            data.of(s.getUUID()).muLead = i == f.lead && s instanceof Locomotive;
        }
        data.markDirty();
        Recorder.event(world, "Formation " + f.name + " applied to the train on the siding");
        return "§aApplied " + f.name + ": lead cab set" + (n > 0 ? ", " + n + " paint variants changed" : "");
    }

    private Company payer(List<EntityCoupleableRollingStock> t) {
        if (!RailMapConfig.railwayEconomy) return null;
        CompanyData cd = CompanyData.get(world);
        for (EntityCoupleableRollingStock s : t) { Company c = cd.ownerOfTrain(s.getUUID(), ""); if (c != null) return c; }
        return null;
    }

    private boolean bill(List<EntityCoupleableRollingStock> t, long cents, String memo) {
        Company c = payer(t);
        if (c == null) return true;
        return CompanyData.get(world).debit(world, c, Company.Kind.MAINT, cents, memo, true);
    }

    private String paint(EntityPlayer who) {
        List<EntityCoupleableRollingStock> t = train();
        if (t.isEmpty()) return "§7No train on the siding";
        int liv = Math.floorMod(ctl.get("livsel"), 2);
        if (liv == 1 && RailMapConfig.railwayEconomy && payer(t) == null) return "§cThis train has no operating company - no company livery to paint";
        if (!bill(t, PAINT_COST * t.size(), "Repaint " + t.size() + " vehicles")) return "§cThe company can't pay for the repaint";
        SimData data = SimData.get(world);
        for (EntityCoupleableRollingStock s : t) {
            SimData.Car c = data.of(s.getUUID());
            c.livery = liv;
            FleetData.Unit u = DepotTicker.live(s.getUUID());
            c.paintKm = u == null ? 0 : u.km;
            if (u != null) u.note(world, "repainted - " + (liv == 1 ? "company livery" : "builder's paint"));
        }
        data.markDirty();
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.7f, 1.0f);
        Recorder.event(world, "Train repainted (" + t.size() + " vehicles, " + (liv == 1 ? "company livery" : "builder's paint") + ")" + (who == null ? "" : " by " + who.getName()));
        return "§aRepainted " + t.size() + " vehicles in " + (liv == 1 ? "the company livery" : "the builder's paint");
    }

    private String restore(EntityPlayer who) {
        List<EntityCoupleableRollingStock> t = train();
        if (t.isEmpty()) return "§7No train on the siding";
        SimData data = SimData.get(world);
        float dmg = 0;
        for (EntityCoupleableRollingStock s : t) {
            if (s.wreck > 0) return "§c" + Formation.shortName(s.getDefinition()) + " is still off the rails - rerail it first";
            SimData.Car c = data.peek(s.getUUID());
            if (c != null) dmg += c.body;
        }
        if (dmg <= 0) return "§7No body damage on this train";
        if (!bill(t, Math.round(RESTORE_COST * dmg), "Body repairs")) return "§cThe company can't pay for the body repairs";
        for (EntityCoupleableRollingStock s : t) {
            SimData.Car c = data.peek(s.getUUID());
            if (c == null || c.body <= 0) continue;
            FleetData.Unit u = DepotTicker.live(s.getUUID());
            if (u != null) u.note(world, String.format(Locale.ROOT, "body restored (%d %% damage)", Math.round(c.body)));
            c.body = 0;
        }
        data.markDirty();
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.air_tool"), SoundCategory.BLOCKS, 0.7f, 1.0f);
        Recorder.event(world, "Body panels restored on the train at the formation desk" + (who == null ? "" : " by " + who.getName()));
        return "§aBody panels restored";
    }

    private String place(EntityPlayer who) {
        Formation f = current();
        if (f == null || f.cars.isEmpty()) return "§7No formation to build";
        if (!buildKey()) return "§cTurn the key to BUILD first";
        if (who == null || !(who.capabilities.isCreativeMode || who.canUseCommand(2, ""))) return "§cOnly a creative player or an operator can build stock from the desk";
        BlockPos at = siding();
        if (at == null) return "§cNo track within 12 blocks of the desk";
        List<String> ids = new ArrayList<>();
        List<Boolean> flips = new ArrayList<>();
        List<String> tex = new ArrayList<>();
        for (Formation.Car c : f.cars) { ids.add(c.def); flips.add(c.flip); tex.add(c.texture); }
        EnumFacing face = controlFacing();
        float yaw = face.rotateY().getHorizontalAngle();
        int n = com.dogpound.railmap.server.ConsistPlacer.placeFormation(cam72cam.mod.world.World.get(world), at, yaw, ids, flips, tex);
        Recorder.event(world, "Formation " + f.name + " built at the desk by " + who.getName() + " (" + n + "/" + f.cars.size() + ")");
        return n == f.cars.size() ? "§aBuilt " + f.name + " on the siding - APPLY sets its lead cab" : "§ePlaced " + n + " of " + f.cars.size() + " - the track ran out";
    }

    /** from the editor (PacketFormation "save"): put a formation into library slot i (i == size: add) */
    public String save(int i, Formation f) {
        if (!term.powered()) return "§cThe desk is dark - no 12 V";
        if (f.cars.size() > 48) return "§cAt most 48 vehicles";
        for (Formation.Car c : f.cars) { EntityRollingStockDefinition d = c.definition(); if (d == null) return "§cUnknown vehicle " + c.def; }
        if (f.name.length() > 24) f.name = f.name.substring(0, 24);
        if (i >= 0 && i < library.size()) library.set(i, f);
        else if (library.size() < MAX) { library.add(f); i = library.size() - 1; }
        else return "§cThe library is full";
        ctl.set("sel", i);
        markDirty();
        refresh();
        controlsChanged();
        return "§aSaved " + f.name;
    }

    // ---- screens and lamps -------------------------------------------------------------------------------------------

    @Override
    protected boolean refresh() {
        boolean ch = false;
        Formation f = current();
        Formation.Stats s = f == null ? null : f.stats(maxLen(), grade());
        ch |= StationControls.text(ctl, "title", f == null ? "LIBRARY EMPTY - NEW OR READ" : cut(f.name.toUpperCase(Locale.ROOT), 22) + "  " + (sel() + 1) + "/" + library.size());
        for (int r = 1; r <= 8; r++) {
            Formation.Car c = f != null && r - 1 < f.cars.size() ? f.cars.get(r - 1) : null;
            String t = c == null ? "" : String.format(Locale.ROOT, "%d %s %s", r, c.flip ? "<" : ">", cut(Formation.shortName(c.definition()).toUpperCase(Locale.ROOT), 20));
            if (r == 8 && f != null && f.cars.size() > 8) t = "+ " + (f.cars.size() - 7) + " MORE";
            ch |= StationControls.text(ctl, "r" + r, t);
            ch |= StationControls.put(ctl, "lead" + r, f != null && f.lead == r - 1 && c != null);
            ch |= StationControls.put(ctl, "pow" + r, c != null && c.definition() instanceof cam72cam.immersiverailroading.registry.LocomotiveDefinition ld && !ld.isCabCar());
        }
        ch |= StationControls.text(ctl, "m1", s == null ? "" : String.format(Locale.ROOT, "%.0f M  %.0f T  BRK %.0f%%", s.lengthM, s.tonnes, s.brakedPct));
        ch |= StationControls.text(ctl, "m2", s == null ? "" : String.format(Locale.ROOT, "%.0f KW  %.1f KW/T", s.kw, s.kwPerT));
        ch |= StationControls.text(ctl, "m3", s == null ? "" : String.format(Locale.ROOT, "TE %.0f KN  START %.1f%%", s.teKn, s.startGradePct));
        ch |= StationControls.text(ctl, "m4", s == null ? "" : String.format(Locale.ROOT, "MAX %.0f KM/H  %d SEATS", s.maxKmh, s.seats));
        ch |= StationControls.text(ctl, "m5", s == null ? "" : s.problems.isEmpty() ? (s.notes.isEmpty() ? "CHECKS OK" : s.notes.get(0)) : s.problems.get(0));
        ch |= StationControls.text(ctl, "lenv", maxLen() <= 0 ? "ANY" : Math.round(maxLen()) + " M");
        ch |= StationControls.text(ctl, "gradev", String.format(Locale.ROOT, "%.1f%%", grade()));
        ch |= StationControls.put(ctl, "power", term.powered());
        ch |= StationControls.put(ctl, "ok", s != null && s.problems.isEmpty());
        ch |= StationControls.put(ctl, "bad", s != null && !s.problems.isEmpty());
        // the train on the siding (every 2 s)
        if (age % 40 == 3 || !ctl.texts.containsKey("trainn")) {
            List<EntityCoupleableRollingStock> t = train();
            float dirt = 0, body = 0;
            SimData data = SimData.get(world);
            for (EntityCoupleableRollingStock x : t) {
                dirt = Math.max(dirt, Appearance.dirt(DepotTicker.live(x.getUUID())));
                SimData.Car c = data.peek(x.getUUID());
                if (c != null) body = Math.max(body, c.body);
            }
            ch |= StationControls.text(ctl, "trainn", t.isEmpty() ? "SIDING EMPTY" : cut(Formation.shortName(t.get(0).getDefinition()).toUpperCase(Locale.ROOT), 14) + " +" + (t.size() - 1));
            ch |= StationControls.put(ctl, "train", !t.isEmpty());
            ch |= StationControls.put(ctl, "dirty", dirt > 0.5f);
            ch |= StationControls.put(ctl, "damaged", body > 0);
            ch |= StationControls.put(ctl, "dirtg", Math.round(dirt * 100));
            ch |= StationControls.put(ctl, "bodyg", Math.round(body));
        }
        return ch;
    }

    @Override
    public String gearState() {
        Formation f = current();
        return !term.powered() ? "NO 12 V" : library.size() + " formations" + (f == null ? "" : " · " + f.name + " (" + f.cars.size() + " vehicles)");
    }

    @Override protected void writeExtra(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Formation f : library) l.appendTag(f.write());
        t.setTag("lib", l);
    }

    @Override protected void readExtra(NBTTagCompound t) {
        library.clear();
        NBTTagList l = t.getTagList("lib", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount() && i < MAX; i++) library.add(Formation.read(l.getCompoundTagAt(i)));
    }
}
