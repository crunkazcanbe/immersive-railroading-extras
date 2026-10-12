package com.dogpound.railmap.yard;

import cam72cam.immersiverailroading.entity.*;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.MovingBlock;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.scan.SwitchControl;
import com.dogpound.railmap.settings.ISettingsHolder;
import com.dogpound.railmap.settings.Setting;
import com.dogpound.railmap.settings.SettingsStore;
import com.dogpound.railmap.signal.TrackFollower;
import com.dogpound.railmap.sim.SimConfig;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.Terminal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Hump Yard Control Panel (her ChatGPT list item 11: automatic freight shunting and wagon classification).
 * <p>
 * Set-up with the Signal Wrench: pick a rail, then right-click on the panel: the CREST lamp (the hump crest, or the kick
 * point of a flat yard), the RETARDER lamp (the master retarder at the foot of the hump), a route button 1-8 (that class
 * track's clearance point - the first rail past its last switch). On the Settings Console (sneak + wrench) each class
 * track gets its rule: destinations (a freight contract's destination, e.g. "Steelworks, Mill"), "item:coal",
 * "owner:Name", "empty", "loaded", "bad" (bad-order cars: worn, wheel flats, broken knuckles) or "*" for everything else,
 * plus its length.
 * <p>
 * SCAN finds the train standing behind the crest (the nearest locomotive whose track leads over the crest = the
 * pusher, and every car in front of it), reads each car's waybill / load / condition and blocks consecutive cars for the
 * same track into cuts: the cut list on the screen. START: the panel drives the pusher at hump speed (MODE HUMP) or kick
 * speed (MODE FLAT) towards the crest; as each cut reaches it, the route to its track is set (switches thrown, never
 * under a rolling car), the cut's air is bled, its coupler is lifted and it rolls by gravity (or momentum: the pusher
 * brakes after a kick). Retarders (AUTO: down to the exit speed; CLOSE: stop) work the hand brakes of cars passing the
 * master retarder, and every class track's entry eases cars down to coupling speed; a car at rest on its track is tied
 * down (hand brake, air cut back in) and timed (dwell). An outbound engine coupling up releases them (or REL HB).
 * Track lamps show occupancy, route set and full; the counter shows the cars on each track. STOP holds, E-STOP stops
 * the pusher and closes the retarders. Needs 12 V; on the console's WIRED page.
 */
public class TileYardPanel extends TileEntity implements ITickable, IControlled, IWiredGear, ISettingsHolder {
    public static final int TRACKS = 8;
    static final int IDLE = 0, HUMPING = 1, HOLD = 2, DONE = 3;
    static final String[] STATE = {"IDLE", "HUMPING", "HOLD", "DONE"};
    static final int[] EXIT_KMH = {6, 8, 10, 12, 15};

    private final ControlState ctl = new ControlState();
    private final Terminal term = new Terminal();
    private final SettingsStore cfg = new SettingsStore(this);

    BlockPos crest, retarder;
    final BlockPos[] clear = new BlockPos[TRACKS];
    int state = IDLE;
    String holdWhy = "";
    UUID pusher;
    final List<Cut> cuts = new ArrayList<>();
    final Map<UUID, Integer> rolling = new HashMap<>();
    final Map<UUID, Long> arrived = new HashMap<>();
    final LinkedList<String> log = new LinkedList<>();
    int routeSet = -1;
    long kickBrakeUntil;
    boolean fault;

    private final TrackPath[] body = new TrackPath[TRACKS];
    private long bodyAt = -1;
    final int[] count = new int[TRACKS];
    final double[] tonnes = new double[TRACKS], usedM = new double[TRACKS];
    final long[] oldest = new long[TRACKS];
    private double pusherKmh;
    private boolean inRetarder;
    private final Map<UUID, Long> stalledSince = new HashMap<>();

    static final class Cut {
        final List<UUID> cars = new ArrayList<>();
        int track = -1;
        String dest = "", why = "";
        double tonnes;
        int status;                     // 0 waiting, 1 rolling, 2 classified, 3 no track
        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            NBTTagList l = new NBTTagList();
            for (UUID u : cars) l.appendTag(new NBTTagString(u.toString()));
            t.setTag("cars", l);
            t.setInteger("track", track); t.setString("dest", dest); t.setString("why", why);
            t.setDouble("t", tonnes); t.setInteger("st", status);
            return t;
        }
        static Cut read(NBTTagCompound t) {
            Cut c = new Cut();
            NBTTagList l = t.getTagList("cars", Constants.NBT.TAG_STRING);
            for (int i = 0; i < l.tagCount(); i++) try { c.cars.add(UUID.fromString(l.getStringTagAt(i))); } catch (IllegalArgumentException ignored) { }
            c.track = t.getInteger("track"); c.dest = t.getString("dest"); c.why = t.getString("why");
            c.tonnes = t.getDouble("t"); c.status = t.getInteger("st");
            return c;
        }
    }

    // ---- controls ------------------------------------------------------------------------------------------------------

    @Override public ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); } }
    @Override public int controlValue(String id) { return term.powered() ? ctl.get(id) : 0; }
    @Override public String ptext(String key) { return term.powered() ? ctl.text(key) : ""; }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (!term.powered()) return "§cThe yard panel is dark - no 12 V";
        if (id.length() == 2 && id.charAt(0) == 'r' && Character.isDigit(id.charAt(1))) {
            int i = id.charAt(1) - '1';
            if (state == HUMPING) return "§cHumping - the panel sets the routes itself (STOP first for a manual route)";
            String r = setRoute(i, null);
            refresh();
            return r;
        }
        switch (id) {
            case "scan": { String r = scan(); refresh(); return r; }
            case "start": { String g = YardPlanner.guardStart(world, this); if (g != null) return g; String r = start(); refresh(); return r; }
            case "stop":
                if (state == HUMPING) { state = HOLD; holdWhy = "STOP pressed"; addLog("STOP - humping held"); refresh(); return "§eHumping held - START to carry on"; }
                return "§7Not humping";
            case "relhb": { int n = releaseAll(); refresh(); return "§aHand brakes released on " + n + " car" + (n == 1 ? "" : "s"); }
            case "estop": {
                boolean in = ctl.get("estop") == 0;
                ctl.set("estop", in ? 1 : 0);
                if (in) {
                    if (state == HUMPING) state = HOLD;
                    holdWhy = "E-STOP";
                    addLog("E-STOP - pusher stopped, retarders closed");
                    Recorder.event(world, "Yard panel E-STOP at " + pos.getX() + " " + pos.getZ());
                }
                refresh();
                return in ? "§cYARD EMERGENCY STOP - pusher braked, retarders closed" : "§aE-stop released";
            }
            case "mode":
                if (state == HUMPING) return "§cStop humping before changing the mode";
                return null;
            default: return null;
        }
    }

    // ---- set-up (Signal Wrench) -----------------------------------------------------------------------------------------

    /** link a picked rail to the control the wrench was clicked on */
    public String link(String id, BlockPos pick) {
        if (id == null) return "§7Aim at the CREST lamp, the RETARDER lamp or a route button 1-8";
        if (id.equals("crest") || id.equals("hsg") || id.equals("hsr")) { crest = pick; bodyAt = -1; markDirty(); return "§bHump crest / kick point set: " + at(pick); }
        if (id.equals("rc")) { retarder = pick; markDirty(); return "§bMaster retarder set: " + at(pick); }
        int i = trackOf(id);
        if (i >= 0) { clear[i] = pick; bodyAt = -1; markDirty(); return "§bClass track " + (i + 1) + " clearance point set: " + at(pick) + " §7- give it a rule on the Settings Console"; }
        return "§7That control can't be linked - aim at the CREST lamp, the RETARDER lamp or a route button 1-8";
    }

    static int trackOf(String id) {
        if (id.length() == 2 && "rlocf".indexOf(id.charAt(0)) >= 0 && Character.isDigit(id.charAt(1))) {
            int i = id.charAt(1) - '1';
            return i >= 0 && i < TRACKS ? i : -1;
        }
        return -1;
    }

    public String linkStatus() {
        StringBuilder b = new StringBuilder("§7Crest " + (crest == null ? "§c-§7" : at(crest)) + " · retarder " + (retarder == null ? "-" : at(retarder)) + " · tracks");
        for (int i = 0; i < TRACKS; i++) b.append(' ').append(clear[i] == null ? "§8" + (i + 1) : "§a" + (i + 1)).append("§7");
        return b.toString();
    }

    static String at(BlockPos p) { return p == null ? "-" : p.getX() + " " + p.getY() + " " + p.getZ(); }

    // ---- the tick ---------------------------------------------------------------------------------------------------------

    @Override
    public void update() {
        if (world.isRemote) return;
        if (term.tick(world, pos, wiredName())) controlsChanged();
        long now = world.getTotalWorldTime();
        if (now % 5 != (pos.toLong() & 3)) return;
        try {
            if (!term.powered()) {
                if (state == HUMPING) { state = HOLD; holdWhy = "lost 12 V"; addLog("12 V lost - humping stopped"); }
                return;
            }
            if (bodyAt < 0 || now - bodyAt > 1200) buildBodies(now);
            Map<UUID, EntityMoveableRollingStock> stock = new HashMap<>();
            for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityMoveableRollingStock.class)) if (!s.isDead()) stock.put(s.getUUID(), s);
            if (state == HUMPING || state == HOLD) hump(stock, now);
            rollingCars(stock, now);
            if (now % 20 < 5) tracks(stock, now);
            if (now % 10 < 5) refresh();
        } catch (LinkageError | RuntimeException ex) {
            com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] yard panel at {} failed: {}", pos, ex.toString());
        }
    }

    // ---- class tracks ------------------------------------------------------------------------------------------------------

    private void buildBodies(long now) {
        bodyAt = now;
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        for (int i = 0; i < TRACKS; i++) {
            body[i] = null;
            if (clear[i] == null) continue;
            TrackPath route = route(umc, i);
            TileRail r = rail(umc, clear[i]);
            if (r == null) continue;
            Vec3i from = null;
            if (route != null && route.steps.size() >= 2) from = route.steps.get(route.steps.size() - 2).pos;
            if (from == null) continue;
            body[i] = TrackPath.ahead(umc, r, from, Math.max(20, cfg.num("len" + (i + 1))));
        }
    }

    /** the way from the crest to a class track's clearance point (tried both ways off the crest piece) */
    private TrackPath route(cam72cam.mod.world.World umc, int i) {
        if (crest == null || clear[i] == null) return null;
        TileRail c = rail(umc, crest);
        if (c == null) return null;
        double[] wp = TrackFollower.worldPath(c);
        TrackPath best = null;
        TrackPath.Goal goal = TrackPath.near(clear[i].getX() + 0.5, clear[i].getY(), clear[i].getZ() + 0.5, 3);
        List<Vec3i> starts = new ArrayList<>();
        if (wp != null && wp.length >= 6) {
            starts.add(new Vec3i(Math.floor(wp[0]), Math.floor(wp[1]), Math.floor(wp[2])));
            starts.add(new Vec3i(Math.floor(wp[wp.length - 3]), Math.floor(wp[wp.length - 2]), Math.floor(wp[wp.length - 1])));
        } else starts.add(null);
        for (Vec3i from : starts) {
            TrackPath p = TrackPath.find(umc, c, from, 1500, goal);
            if (p != null && (best == null || p.length < best.length)) best = p;
        }
        return best;
    }

    static TileRail rail(cam72cam.mod.world.World umc, BlockPos p) {
        return TrackFollower.railUnder(umc, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    /** throw every switch from the crest to track i; refuses a switch with a car on it */
    String setRoute(int i, Map<UUID, EntityMoveableRollingStock> stock) {
        if (crest == null) return "§cLink the hump crest first (Signal Wrench: pick a rail, click the CREST lamp)";
        if (clear[i] == null) return "§cTrack " + (i + 1) + " has no clearance point (Signal Wrench: pick a rail, click route button " + (i + 1) + ")";
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        TrackPath p = route(umc, i);
        if (p == null) return "§cNo way found from the crest to track " + (i + 1);
        if (stock == null) {
            stock = new HashMap<>();
            for (EntityMoveableRollingStock s : umc.getEntities(EntityMoveableRollingStock.class)) stock.put(s.getUUID(), s);
        }
        // detector locking: no switch moves under a car
        for (Map.Entry<Vec3i, cam72cam.immersiverailroading.library.SwitchState> e : p.switches.entrySet()) {
            Vec3i v = e.getKey();
            int idx = p.indexOf(v);
            double at = idx <= 0 ? 0 : p.steps.get(idx - 1).distance;
            double end = idx < 0 ? at : p.steps.get(idx).distance;
            for (EntityMoveableRollingStock s : stock.values()) {
                Vec3d q = s.getPosition();
                double d = MovingBlock.along(p, q.x, q.y, q.z, 2.5);
                if (d >= 0 && d > at - 8 && d < end + 8) return "§eSwitch at " + v.x + " " + v.z + " is occupied - waiting";
            }
        }
        int n = 0;
        for (Map.Entry<Vec3i, cam72cam.immersiverailroading.library.SwitchState> e : p.switches.entrySet()) {
            Vec3i v = e.getKey();
            if (SwitchControl.set(world, new BlockPos(v.x, v.y, v.z), e.getValue() == cam72cam.immersiverailroading.library.SwitchState.TURN)) n++;
        }
        routeSet = i;
        return "§aRoute set: crest to track " + (i + 1) + " (" + n + " switch" + (n == 1 ? "" : "es") + ")";
    }

    private boolean onTrack(int i, EntityMoveableRollingStock s) {
        if (body[i] == null) return false;
        Vec3d q = s.getPosition();
        return MovingBlock.along(body[i], q.x, q.y, q.z, 2.5) >= 0;
    }

    int trackUnder(EntityMoveableRollingStock s) {   // package: yard planner (next-48 #4)
        for (int i = 0; i < TRACKS; i++) if (onTrack(i, s)) return i;
        return -1;
    }

    private void tracks(Map<UUID, EntityMoveableRollingStock> stock, long now) {
        Arrays.fill(count, 0);
        Arrays.fill(tonnes, 0);
        Arrays.fill(usedM, 0);
        Arrays.fill(oldest, 0);
        Set<UUID> present = new HashSet<>();
        for (EntityMoveableRollingStock s : stock.values()) {
            Vec3d q = s.getPosition();
            if (pos.distanceSqToCenter(q.x, q.y, q.z) > 800 * 800) continue;
            int i = trackUnder(s);
            if (i < 0) continue;
            present.add(s.getUUID());
            count[i]++;
            tonnes[i] += s.getWeight() / 1000.0;
            usedM[i] += s.getDefinition().getLength(s.gauge);
            Long a = arrived.get(s.getUUID());
            if (a == null && !rolling.containsKey(s.getUUID())) { a = now; arrived.put(s.getUUID(), now); }
            if (a != null && (oldest[i] == 0 || a < oldest[i])) oldest[i] = a;
            // an outbound engine has coupled up: the crew lets the hand brakes off
            if (s instanceof EntityCoupleableRollingStock c && !(s instanceof Locomotive) && s.getHandBrake() > 0) {
                for (EntityCoupleableRollingStock o : c.getTrain()) if (o instanceof Locomotive) { s.setHandBrake(0); s.locked = true; break; }
            }
        }
        arrived.keySet().retainAll(present);
    }

    private int releaseAll() {
        int n = 0;
        for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityMoveableRollingStock.class)) {
            if (trackUnder(s) >= 0 && s.getHandBrake() > 0) { s.setHandBrake(0); s.locked = true; n++; }
        }
        addLog("Hand brakes released: " + n + " cars");
        return n;
    }

    // ---- scan: the train behind the crest, its cut list --------------------------------------------------------------------

    private static final class Approach {
        Locomotive loco;
        TrackPath path;
        int rev;
        double crestAlong;
    }

    private Approach approach(Locomotive l) {
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        Vec3d p = l.getPosition();
        TileRail rail = TrackFollower.railUnder(umc, p.x, p.y, p.z);
        if (rail == null || crest == null) return null;
        Approach best = null;
        for (int rev : new int[]{1, -1}) {
            Vec3d f = cam72cam.immersiverailroading.util.VecUtil.fromWrongYaw(rev, l.getRotationYaw());
            Vec3i from = TrackPath.behind(rail, p.x, p.y, p.z, f.x, f.z);
            TrackPath path = TrackPath.find(umc, rail, from, cfg.num("reach") + 50, TrackPath.near(crest.getX() + 0.5, crest.getY(), crest.getZ() + 0.5, 3));
            if (path == null) continue;
            double ca = MovingBlock.along(path, crest.getX() + 0.5, crest.getY() + 0.5, crest.getZ() + 0.5, 3);
            if (ca < 0) ca = path.length;
            if (best == null || ca < best.crestAlong) {
                best = new Approach();
                best.loco = l; best.path = path; best.rev = rev; best.crestAlong = ca;
            }
        }
        return best;
    }

    /** cars in front of the pusher, nearest the crest first */
    private List<EntityCoupleableRollingStock> ahead(Approach a) {
        List<EntityCoupleableRollingStock> out = new ArrayList<>();
        Map<EntityCoupleableRollingStock, Double> along = new HashMap<>();
        Vec3d lp = a.loco.getPosition();
        double me = MovingBlock.along(a.path, lp.x, lp.y, lp.z, 3);
        for (EntityCoupleableRollingStock s : a.loco.getTrain(false)) {
            if (s == a.loco || s instanceof Locomotive) continue;
            Vec3d q = s.getPosition();
            double d = MovingBlock.along(a.path, q.x, q.y, q.z, 3);
            if (d < 0 || d <= me) continue;
            along.put(s, d);
            out.add(s);
        }
        out.sort((x, y) -> Double.compare(along.get(y), along.get(x)));
        return out;
    }

    String scan() {
        if (crest == null) return "§cLink the hump crest first (Signal Wrench: pick a rail, click the CREST lamp)";
        if (state == HUMPING) return "§cHumping - STOP first";
        Approach best = null;
        Vec3d c = new Vec3d(crest.getX() + 0.5, crest.getY(), crest.getZ() + 0.5);
        int reach = cfg.num("reach");
        for (Locomotive l : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) {
            if (l.isDead() || l.getPosition().distanceTo(c) > reach) continue;
            Approach a = approach(l);
            if (a == null || ahead(a).isEmpty()) continue;
            if (best == null || a.crestAlong < best.crestAlong) best = a;
        }
        cuts.clear();
        if (best == null) { pusher = null; addLog("SCAN: no train behind the crest"); return "§eNo train found behind the crest (a locomotive with cars in front of it, within " + reach + " m)"; }
        pusher = best.loco.getUUID();
        Classifier k = new Classifier(world, this);
        Cut cur = null;
        for (EntityCoupleableRollingStock s : ahead(best)) {
            Classifier.Result r = k.classify(s);
            if (cur == null || cur.track != r.track || r.track < 0) {
                cur = new Cut();
                cur.track = r.track;
                cur.dest = r.dest;
                cur.why = r.why;
                cur.status = r.track < 0 ? 3 : 0;
                cuts.add(cur);
            }
            cur.cars.add(s.getUUID());
            cur.tonnes += s.getWeight() / 1000.0;
        }
        state = IDLE;
        int cars = 0, nt = 0;
        for (Cut ct : cuts) { cars += ct.cars.size(); if (ct.status == 3) nt++; }
        addLog(String.format(Locale.ROOT, "SCAN: %d cars in %d cuts behind %s%s", cars, cuts.size(), name(best.loco), nt > 0 ? ", " + nt + " with no track" : ""));
        markDirty();
        return String.format(Locale.ROOT, "§aCut list: %d cars in %d cuts, pusher %s%s", cars, cuts.size(), name(best.loco),
                nt > 0 ? " §c(" + nt + " cut" + (nt > 1 ? "s" : "") + " have no class track - give a track a rule)" : "");
    }

    String start() {
        if (ctl.get("estop") == 1) return "§cE-STOP is in - release it first";
        int mode = ctl.get("mode");
        if (mode == 0) return "§cSet MODE to HUMP or FLAT";
        if (cuts.isEmpty() || pusher == null) return "§cSCAN a train first";
        if (state == HUMPING) return "§7Already humping";
        state = HUMPING;
        holdWhy = "";
        fault = false;
        // bleed the cars so cut-off cuts don't brake themselves on the hump
        Map<UUID, EntityMoveableRollingStock> byId = new HashMap<>();
        for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityMoveableRollingStock.class)) byId.put(s.getUUID(), s);
        for (Cut c : cuts) for (UUID u : c.cars) { EntityMoveableRollingStock s = byId.get(u); if (s != null && c.status == 0) { s.setHandBrake(0); } }
        addLog((mode == 1 ? "HUMPING" : "FLAT SWITCHING") + " started");
        Recorder.event(world, "Yard at " + pos.getX() + " " + pos.getZ() + ": " + (mode == 1 ? "humping" : "flat switching") + " started, " + cuts.size() + " cuts");
        return "§aHumping started - " + cuts.size() + " cuts";
    }

    // ---- humping ---------------------------------------------------------------------------------------------------------------

    private void hump(Map<UUID, EntityMoveableRollingStock> stock, long now) {
        EntityMoveableRollingStock ps = pusher == null ? null : stock.get(pusher);
        if (!(ps instanceof Locomotive loco)) {
            if (state == HUMPING) { hold("pusher not loaded / gone"); }
            return;
        }
        double speed = Math.abs(loco.getCurrentSpeed().metric());
        pusherKmh = speed;
        Approach a = approach(loco);
        if (state != HUMPING || ctl.get("estop") == 1) { drive(loco, a == null ? 0 : a.rev, speed, 0); return; }
        if (a == null) { hold("pusher can't see the crest any more"); drive(loco, 0, speed, 0); return; }
        if (loco instanceof LocomotiveDiesel d && !d.isTurnedOn()) d.setTurnedOn(true);
        loco.setHandBrake(0);
        loco.setIndependentBrake(0);
        int mode = ctl.get("mode");
        Cut cut = null;
        int k = 0;
        for (Cut c : cuts) { k++; if (c.status == 0 || c.status == 3) { cut = c; break; } }
        if (cut == null) {
            state = DONE;
            addLog("All cuts humped");
            Recorder.event(world, "Yard at " + pos.getX() + " " + pos.getZ() + ": humping complete");
            drive(loco, a.rev, speed, 0);
            return;
        }
        if (cut.status == 3) { hold("cut " + k + " has no class track (" + cut.why + ")"); drive(loco, a.rev, speed, 0); return; }
        List<EntityCoupleableRollingStock> front = ahead(a);
        // the cut's cars still in the train, and the car behind it
        int lastIdx = -1;
        for (int i = 0; i < front.size(); i++) if (cut.cars.contains(front.get(i).getUUID())) lastIdx = i;
        if (lastIdx < 0) { cut.status = 1; return; }            // already gone (cut by hand)
        EntityCoupleableRollingStock last = front.get(lastIdx);
        EntityCoupleableRollingStock next = lastIdx + 1 < front.size() ? front.get(lastIdx + 1) : loco;
        double target = mode == 1 ? cfg.num("kmh") : cfg.num("kick");
        if (kickBrakeUntil > now) target = 0;
        // the route goes in as the cut nears the crest; it waits while a switch is still occupied
        EntityCoupleableRollingStock first = front.get(0);
        Vec3d fp = first.getPosition();
        double firstAlong = MovingBlock.along(a.path, fp.x, fp.y, fp.z, 3);
        if (routeSet != cut.track && firstAlong > a.crestAlong - 60) {
            String r = setRoute(cut.track, stock);
            if (routeSet != cut.track) {
                target = 0;
                holdWhy = r.replaceAll("§.", "");
            } else { holdWhy = ""; addLog("Route T" + (cut.track + 1) + " set for cut " + k); }
        }
        // the coupling between the cut and the rest has reached the crest: lift the pin
        Vec3d lpos = last.getPosition(), npos = next.getPosition();
        double la = MovingBlock.along(a.path, lpos.x, lpos.y, lpos.z, 3), na = MovingBlock.along(a.path, npos.x, npos.y, npos.z, 3);
        double coupling = (la - half(last) + na + half(next)) / 2;
        if (routeSet == cut.track && coupling >= a.crestAlong - 0.5) {
            EntityCoupleableRollingStock.CouplerType t1 = last.getCouplerFor(next), t2 = next.getCouplerFor(last);
            if (t1 != null) last.setCouplerEngaged(t1, false);
            if (t2 != null) next.setCouplerEngaged(t2, false);
            for (UUID u : cut.cars) {
                EntityMoveableRollingStock s = stock.get(u);
                if (s == null) continue;
                s.locked = false;                                  // bled: no air, rolls free
                s.setHandBrake(0);
                rolling.put(u, cut.track);
            }
            cut.status = 1;
            addLog(String.format(Locale.ROOT, "Cut %d: %d car%s %.0f t -> T%d %s", k, cut.cars.size(), cut.cars.size() == 1 ? "" : "s", cut.tonnes, cut.track + 1, cut.dest));
            if (mode == 2) kickBrakeUntil = now + 100;              // flat yard: the engine stops dead, the cut runs on
            target = mode == 2 ? 0 : target;
        }
        drive(loco, a.rev, speed, target);
    }

    private void hold(String why) {
        if (state != HOLD || !why.equals(holdWhy)) {
            addLog("HOLD: " + why);
            AlarmManager.get(world).raise(world, "yard:" + pos.toLong() + ":hold", AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR, "Yard humping held: " + why, pos, false);
        }
        state = HOLD;
        holdWhy = why;
        fault = true;
    }

    /** throttle / brake the pusher toward a target speed in the direction of the crest */
    private static void drive(Locomotive l, int rev, double speed, double target) {
        if (rev != 0 && l.getReverser() * rev <= 0.05f) {
            if (speed > 1) { l.setThrottle(0); l.setTrainBrake(1); return; }
            l.setReverser(rev);
        }
        float step = Math.max(0.0625f, l.getThrottleDelta());
        if (target < 0.5) {
            l.setThrottle(0);
            l.setTrainBrake(speed > 0.5 ? 1f : 0.6f);
        } else if (speed > target + 1.5) {
            l.setThrottle(0);
            l.setTrainBrake((float) Math.min(1, (speed - target) / 6 + 0.25));
        } else if (speed < target - 1) {
            l.setTrainBrake(0);
            l.setThrottle(Math.min(0.75f, l.getThrottle() + step));
        } else {
            l.setTrainBrake(0);
            if (speed > target) l.setThrottle(Math.max(0f, l.getThrottle() - step));
        }
    }

    static double half(EntityMoveableRollingStock s) {
        try { return s.getDefinition().getLength(s.gauge) / 2; } catch (RuntimeException e) { return 6; }
    }

    // ---- cars rolling to their tracks: retarders, tie-down -------------------------------------------------------------------

    private void rollingCars(Map<UUID, EntityMoveableRollingStock> stock, long now) {
        int retMode = ctl.get("ret");
        if (ctl.get("estop") == 1) retMode = 2;
        double exit = EXIT_KMH[Math.max(0, Math.min(EXIT_KMH.length - 1, ctl.get("retspd")))];
        double couple = cfg.num("couple");
        inRetarder = false;
        for (Iterator<Map.Entry<UUID, Integer>> it = rolling.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> e = it.next();
            EntityMoveableRollingStock s = stock.get(e.getKey());
            if (s == null) continue;                                 // unloaded: keep it for later
            int track = e.getValue();
            double speed = Math.abs(s.getCurrentSpeed().metric());
            Vec3d q = s.getPosition();
            boolean inRet = retarder != null && retarder.distanceSqToCenter(q.x, q.y, q.z) < 36;
            int on = trackUnder(s);
            float hb = 0;
            if (inRet && retMode > 0) {
                inRetarder = true;
                double t = retMode == 2 ? 0 : exit;
                if (speed > t) hb = (float) Math.min(1, 0.3 + (speed - t) / 5);
            } else if (on == track && speed > couple) {
                hb = (float) Math.min(0.8, 0.15 + (speed - couple) / 8);   // the track retarder at the entry
            }
            if (on == track && body[track] != null && MovingBlock.along(body[track], q.x, q.y, q.z, 2.5) > body[track].length - 15) {
                hb = 1;                                              // the skate at the far end: never off the end of the track
            }
            if (on >= 0 && on != track) {
                AlarmManager.get(world).raise(world, "yard:" + pos.toLong() + ":misroute", AlarmManager.Cat.AUTO, AlarmManager.Sev.MAJOR,
                        "Yard: " + name(s) + " went into track " + (on + 1) + " instead of " + (track + 1), pos, false);
                addLog("MISROUTE: " + name(s) + " in T" + (on + 1) + " (for T" + (track + 1) + ")");
                fault = true;
                e.setValue(on);
                track = on;
            }
            if (on == track && speed < 0.3) {
                hb = 1;                                              // at rest on its track: tied down, air cut back in
                s.locked = true;
                arrived.put(e.getKey(), now);
                stalledSince.remove(e.getKey());
                it.remove();
                for (Cut c : cuts) if (c.cars.contains(e.getKey()) && c.status == 1) {
                    boolean all = true;
                    for (UUID u : c.cars) if (rolling.containsKey(u)) { all = false; break; }
                    if (all) c.status = 2;
                }
            } else if (on < 0 && speed < 0.2) {
                long since = stalledSince.computeIfAbsent(e.getKey(), u -> now);
                if (now - since > 200) {
                    AlarmManager.get(world).raise(world, "yard:" + pos.toLong() + ":stall", AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR,
                            "Yard: " + name(s) + " stalled short of track " + (track + 1) + " - fouling the ladder?", pos, false);
                    addLog("STALLED: " + name(s) + " short of T" + (track + 1));
                    fault = true;
                    hb = 1;
                    s.locked = true;
                    stalledSince.remove(e.getKey());
                    it.remove();
                }
            } else stalledSince.remove(e.getKey());
            Float rg = TileRetarder.grip(e.getKey());                        // batch-3 #25: a real retarder has this car - it brakes it
            if (rg != null && hb < 1) hb = rg;                                 // (tie-down at rest and the end skate still win)
            if (Math.abs(s.getHandBrake() - hb) > 0.04f) s.setHandBrake(hb);
        }
    }

    // ---- display ------------------------------------------------------------------------------------------------------------

    void refresh() {
        boolean ch = false;
        long now = world.getTotalWorldTime();
        for (int i = 0; i < TRACKS; i++) {
            int len = Math.max(1, cfg.num("len" + (i + 1)));
            ch |= StationControls.put(ctl, "o" + (i + 1), count[i] > 0);
            ch |= StationControls.put(ctl, "l" + (i + 1), routeSet == i);
            ch |= StationControls.put(ctl, "c" + (i + 1), count[i]);
            ch |= StationControls.put(ctl, "f" + (i + 1), clear[i] != null && usedM[i] > len * 0.9);
        }
        boolean moving = state == HUMPING && pusherKmh > 0.5 && ctl.get("estop") == 0;
        ch |= StationControls.put(ctl, "hsg", moving);
        ch |= StationControls.put(ctl, "hsr", !moving);
        ch |= StationControls.put(ctl, "crest", crest != null);
        ch |= StationControls.put(ctl, "rc", inRetarder || ctl.get("ret") == 2 || ctl.get("estop") == 1);
        ch |= StationControls.put(ctl, "power", true);
        ch |= StationControls.put(ctl, "run", state == HUMPING);
        ch |= StationControls.put(ctl, "hold", state == HOLD);
        ch |= StationControls.put(ctl, "fault", fault);
        ch |= StationControls.put(ctl, "done", state == DONE);
        ch |= StationControls.put(ctl, "kmh", (int) Math.round(pusherKmh));
        String[] lines = new String[6];
        int page = ctl.get("page");
        if (page == 0) {
            lines[0] = String.format(Locale.ROOT, "%s %s%s", STATE[state], cuts.isEmpty() ? "- SCAN a train" : cuts.size() + " CUTS",
                    holdWhy.isEmpty() ? "" : " - " + holdWhy.toUpperCase(Locale.ROOT));
            int shown = 1, k = 0;
            int firstOpen = 0;
            for (int i = 0; i < cuts.size(); i++) if (cuts.get(i).status == 0 || cuts.get(i).status == 3) { firstOpen = Math.max(0, i - 1); break; }
            for (int i = firstOpen; i < cuts.size() && shown < 6; i++) {
                Cut c = cuts.get(i);
                k = i + 1;
                String st = c.status == 0 ? "WAIT" : c.status == 1 ? "ROLL" : c.status == 2 ? "IN" : "NO TRK";
                lines[shown++] = String.format(Locale.ROOT, "%2d %2dC %5.0fT %s %-11s %s", k, c.cars.size(), c.tonnes,
                        c.track < 0 ? "--" : "T" + (c.track + 1), cut(c.dest.toUpperCase(Locale.ROOT), 11), st);
            }
        } else if (page == 1) {
            for (int l = 0; l < 4; l++) {
                StringBuilder b = new StringBuilder();
                for (int j = 0; j < 2; j++) {
                    int i = l * 2 + j;
                    long dwell = oldest[i] == 0 ? 0 : (now - oldest[i]) / 1200;
                    b.append(String.format(Locale.ROOT, "T%d %-6s %2dC %3dM", i + 1, cut(rule(i).toUpperCase(Locale.ROOT), 6), count[i], dwell));
                    if (j == 0) b.append(" | ");
                }
                lines[l] = b.toString();
            }
            lines[4] = String.format(Locale.ROOT, "TONS %s", tons());
            lines[5] = String.format(Locale.ROOT, "RET %s EXIT %d KMH CPL %d", new String[]{"OFF", "AUTO", "CLOSED"}[Math.max(0, Math.min(2, ctl.get("ret")))],
                    EXIT_KMH[Math.max(0, Math.min(4, ctl.get("retspd")))], cfg.num("couple"));
        } else {
            int i = 0;
            for (String s : log) { if (i >= 6) break; lines[i++] = cut(s.toUpperCase(Locale.ROOT), 46); }
        }
        for (int i = 0; i < 6; i++) ch |= StationControls.text(ctl, "s" + (i + 1), lines[i] == null ? "" : lines[i]);
        if (ch) controlsChanged();
    }

    private String tons() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < TRACKS; i++) b.append(i == 0 ? "" : " ").append(Math.round(tonnes[i]));
        return b.toString();
    }

    String rule(int i) {
        String r = cfg.text("t" + (i + 1)).trim();
        return r.isEmpty() ? (clear[i] == null ? "-" : "?") : r;
    }

    static String cut(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }

    void addLog(String s) {
        log.addFirst(Recorder.clock(world, world.getTotalWorldTime()) + " " + s);
        while (log.size() > 30) log.removeLast();
        markDirty();
    }

    static String name(EntityMoveableRollingStock s) {
        if (s.tag != null && !s.tag.isEmpty()) return s.tag;
        return s.getDefinition() == null ? "car" : s.getDefinition().name();
    }

    // ---- 12 V + console --------------------------------------------------------------------------------------------------------

    @Override public Terminal term() { return term; }
    @Override public void gearChanged() { controlsChanged(); }
    @Override public String wiredName() { return "Yard control panel"; }
    @Override public String gearState() {
        int open = 0;
        for (Cut c : cuts) if (c.status == 0) open++;
        return STATE[state] + (cuts.isEmpty() ? "" : " · " + open + "/" + cuts.size() + " cuts to go") + (routeSet >= 0 ? " · route T" + (routeSet + 1) : "")
                + (holdWhy.isEmpty() ? "" : " · " + holdWhy);
    }
    @Override public int wiredLamp() { return term.burnt || !term.powered() ? 3 : fault ? 3 : state == HUMPING ? 1 : 2; }
    @Override public Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }
    @Override public void gearInfo(Map<String, Object> m) {
        m.put("yardState", STATE[state]);
        m.put("cuts", cuts.size());
        m.put("route", routeSet + 1);
        for (int i = 0; i < TRACKS; i++) m.put("track" + (i + 1), count[i]);
    }

    // ---- settings console ----------------------------------------------------------------------------------------------------

    @Override public String settingsTitle() { return "Yard Control Panel"; }
    @Override public SettingsStore settings() { return cfg; }

    @Override
    public List<Setting> settingDefs() {
        List<Setting> l = new ArrayList<>();
        String t = "Class tracks", h = "Hump", st = "Status";
        for (int i = 0; i < TRACKS; i++) {
            l.add(Setting.text(t, "t" + (i + 1), "Track " + (i + 1) + " takes",
                    "Destinations (a contract's destination name), item:coal, owner:Name, empty, loaded, bad (bad order), * = everything else. Comma separated",
                    i == 0 ? "bad" : i == TRACKS - 1 ? "*" : "", 60));
            l.add(Setting.num(t, "len" + (i + 1), "Track " + (i + 1) + " length", "From its clearance point to the buffers", 200, 20, 2000, 10, "m"));
        }
        l.add(Setting.num(h, "kmh", "Hump speed", "The pusher shoves the train over the crest at this speed", (int) Math.round(SimConfig.humpKmh), 1, 15, 1, "km/h"));
        l.add(Setting.num(h, "kick", "Kick speed (FLAT)", "Flat switching: the engine shoves to this speed, the cut is cut off and runs on", 10, 3, 25, 1, "km/h"));
        l.add(Setting.num(h, "couple", "Coupling speed", "The class-track retarders ease cars down to this so they couple without damage", 6, 2, 15, 1, "km/h"));
        l.add(Setting.num(h, "reach", "Train search reach", "SCAN looks for a pusher within this distance of the crest", 300, 50, 1500, 50, "m"));
        l.add(Setting.info(st, "State", gearState()));
        l.add(Setting.info(st, "Links", linkStatus().replaceAll("§.", "")));
        for (int i = 0; i < TRACKS; i++) l.add(Setting.info(st, "Track " + (i + 1), clear[i] == null ? "no clearance point" : count[i] + " cars, " + Math.round(tonnes[i]) + " t, " + Math.round(usedM[i]) + " m used"));
        IWiredGear.settings(l, st, this);
        return l;
    }

    @Override public void onSettingsChanged(String key) { bodyAt = -1; markDirty(); }

    // ---- save ---------------------------------------------------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        ctl.write(t);
        term.write(t);
        cfg.write(t);
        if (crest != null) t.setLong("crest", crest.toLong());
        if (retarder != null) t.setLong("ret", retarder.toLong());
        for (int i = 0; i < TRACKS; i++) if (clear[i] != null) t.setLong("clear" + i, clear[i].toLong());
        t.setInteger("state", state);
        t.setString("holdWhy", holdWhy);
        if (pusher != null) t.setUniqueId("pusher", pusher);
        t.setInteger("routeSet", routeSet);
        NBTTagList cl = new NBTTagList();
        for (Cut c : cuts) cl.appendTag(c.write());
        t.setTag("cuts", cl);
        NBTTagCompound r = new NBTTagCompound();
        for (Map.Entry<UUID, Integer> e : rolling.entrySet()) r.setInteger(e.getKey().toString(), e.getValue());
        t.setTag("rolling", r);
        NBTTagCompound a = new NBTTagCompound();
        for (Map.Entry<UUID, Long> e : arrived.entrySet()) a.setLong(e.getKey().toString(), e.getValue());
        t.setTag("arrived", a);
        NBTTagList lg = new NBTTagList();
        for (String s : log) lg.appendTag(new NBTTagString(s));
        t.setTag("log", lg);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        ctl.read(t);
        term.read(t);
        if (!t.hasKey("term")) term.legacy = false;
        cfg.read(t);
        crest = t.hasKey("crest") ? BlockPos.fromLong(t.getLong("crest")) : null;
        retarder = t.hasKey("ret") ? BlockPos.fromLong(t.getLong("ret")) : null;
        for (int i = 0; i < TRACKS; i++) clear[i] = t.hasKey("clear" + i) ? BlockPos.fromLong(t.getLong("clear" + i)) : null;
        state = t.getInteger("state");
        holdWhy = t.getString("holdWhy");
        pusher = t.hasUniqueId("pusher") ? t.getUniqueId("pusher") : null;
        routeSet = t.hasKey("routeSet") ? t.getInteger("routeSet") : -1;
        cuts.clear();
        NBTTagList cl = t.getTagList("cuts", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cl.tagCount(); i++) cuts.add(Cut.read(cl.getCompoundTagAt(i)));
        rolling.clear();
        NBTTagCompound r = t.getCompoundTag("rolling");
        for (String k : r.getKeySet()) try { rolling.put(UUID.fromString(k), r.getInteger(k)); } catch (IllegalArgumentException ignored) { }
        arrived.clear();
        NBTTagCompound a = t.getCompoundTag("arrived");
        for (String k : a.getKeySet()) try { arrived.put(UUID.fromString(k), a.getLong(k)); } catch (IllegalArgumentException ignored) { }
        log.clear();
        NBTTagList lg = t.getTagList("log", Constants.NBT.TAG_STRING);
        for (int i = 0; i < lg.tagCount(); i++) log.add(lg.getStringTagAt(i));
        bodyAt = -1;
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager n, net.minecraft.network.play.server.SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
}
