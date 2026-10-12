package com.dogpound.railmap.freight;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.biz.BizData;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.cctv.CamType;
import com.dogpound.railmap.cctv.CctvData;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.Supply;
import com.dogpound.railmap.item.ItemContainer;
import com.dogpound.railmap.proxy.CommonProxy;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.*;

/**
 * Yard Gantry Crane (batch 5 #1): a rail-mounted gantry (RMG) spanning a truck lane, a rail lane and a four-row stack
 * yard, four bays long, worked from the operator's cab desk. The whole gantry travels along its crane rails (along the
 * desk), the trolley runs across the yard on the girders, and the hoist lowers the spreader on four ropes.
 * <ul>
 *   <li>Real lifts: containers come off and go onto the IR freight car standing in the rail lane under a bay (or any
 *   other mod's wagon / vehicle with an inventory there), the truck chassis in the truck lane (gated in / out at the
 *   desk) and the stack: 4 bays x 4 rows x 4 tiers, each slot real storage, drawn as stacked boxes.</li>
 *   <li>Joysticks (gantry, trolley, hoist: 2 speed notches each way, spring back to centre), twistlock LOCK / UNLOCK with
 *   the landed and locked lamps, the load cell (box + spreader tare, SWL), the anemometer (slows over 80 % of the wind
 *   limit, storm pins over it), ANTI-SWAY (the spreader swings on its ropes after every start and stop; twistlocks only
 *   work with it steady), E-STOP, the travel horn and alarms.</li>
 *   <li>AUTO: a job list - built by PLAN (discharge cars for this station into the stack, load boxes for elsewhere onto
 *   cars with room, deliveries onto trucks with the DELIVERY key), by TRACKER (the box on the nearest container tracking
 *   terminal's screen) or by FROM / TO + ADD JOB - worked one lift after another.</li>
 *   <li>Spreader camera on the CCTV system (CamType.CRANE), looking straight down.</li>
 *   <li>Power: 12 V control terminal + its drives' real supply from the grid touching the desk: 480 V, 4.16 kV or
 *   13.8 kV 3-phase (the cable reel on the gantry pays out the trailing cable); hoist, trolley and travel motors draw it.</li>
 * </ul>
 */
public class TileYardGantry extends TileFurniture {
    public static final int BAYS = 4, ROWS = 4, TIERS = 4, LANES = 2 + ROWS;
    public static final String[] LANE_NAMES = {"TRUCK", "RAIL", "ROW 1", "ROW 2", "ROW 3", "ROW 4"};
    /** bay centres along the crane rails (x) and lane centres across the yard (z), block units from the desk block's corner */
    public static final double[] BAY_X = {-9.1, -2.7, 3.7, 10.1};
    public static final double[] LANE_Z = {3.6, 6.6, 9.8, 12.5, 15.2, 17.9};
    public static final double BOX_H = 2.6, BOX_L = 6.0, BOX_W = 2.44, TRUCK_DECK = 1.4, CAR_DECK = 1.1;
    public static final double TRAVEL = 13.4, GIRDER = 14.4, LEG_Z0 = 1.9, LEG_Z1 = 19.6, SPREADER_T = 10.5;
    public static final double GX_MIN = BAY_X[0] - 0.6, GX_MAX = BAY_X[BAYS - 1] + 0.6, TZ_MIN = LANE_Z[0] - 0.6, TZ_MAX = LANE_Z[LANES - 1] + 0.6;
    private static final double[] SPEED_G = {0.05, 0.14}, SPEED_T = {0.04, 0.11}, SPEED_H = {0.03, 0.08};
    private static final long LIFT_FEE = 200;

    // ---- the yard (synced to clients) -----------------------------------------------------------------------------------
    public final ItemStack[][][] stack = new ItemStack[BAYS][ROWS][TIERS];
    public final ItemStack[] truck = new ItemStack[BAYS];
    public ItemStack carrying = ItemStack.EMPTY;
    public double gx = BAY_X[1], tz = LANE_Z[2], hy = TRAVEL, swayX, swayZ;
    public boolean locked, hv;
    public double reel = 50;
    /** client: drawn positions easing toward the real ones */
    public double drawG = BAY_X[1], drawT = LANE_Z[2], drawH = TRAVEL, drawSX, drawSZ;

    private double svX, svZ, vG, vT, vH;
    private final Deque<double[]> queue = new ArrayDeque<>();
    public final List<int[]> jobs = new ArrayList<>();
    private String fault = "";
    private int syncIn, camNo, serial;
    private long feesToday, feesDay, lastJoy, lifts;
    private double kwNow, kwSum, wind;
    private int kwN;
    final Supply mains = new Supply(Elec.Level.AC480, Elec.Level.MV4160, Elec.Level.MV13800);

    public TileYardGantry() {
        super(new String[]{"locker", "spares"}, new String[]{"Operator's locker", "Spreader spares cabinet"}, new int[]{9, 9}, 3);
        for (ItemStack[][] b : stack) for (ItemStack[] r : b) Arrays.fill(r, ItemStack.EMPTY);
        Arrays.fill(truck, ItemStack.EMPTY);
        for (String j : new String[]{"j_g", "j_t", "j_h"}) ctl.set(j, 2);                  // joysticks start centred
    }

    /** the station this yard serves: {key, name} (key Long.MIN_VALUE / "" when none within 160 blocks) */
    private long hereKey() { Map.Entry<Long, String> e = com.dogpound.railmap.stn.StnData.station(world, pos, 160); return e == null ? Long.MIN_VALUE + 1 : e.getKey(); }
    private String hereName() { Map.Entry<Long, String> e = com.dogpound.railmap.stn.StnData.station(world, pos, 160); return e == null ? pos.getX() + "," + pos.getZ() : e.getValue(); }

    @Override public double[][] shelfSpots() { return new double[][]{{24.4, 9.4, 13.4}, {26.4, 9.4, 13.4}, {28.4, 9.4, 13.4}}; }   // tools/gen_bb_rmg.py
    @Override protected boolean mechanical(String id) { return id.startsWith("k_") || id.startsWith("m_"); }
    @Override public String wiredName() { return "Yard gantry " + name(); }
    public String name() { return "RMG " + (serial == 0 ? "?" : serial); }
    public boolean estop() { return ctl.get("m_estop") == 1; }
    public boolean keyOn() { return ctl.get("k_power") == 1; }
    public boolean running() { return term.powered() && keyOn() && !estop() && mains.ok && !storm(); }
    public boolean storm() { return GantryConfig.windStop && wind > GantryConfig.windLimitMs; }
    public boolean busy() { return !queue.isEmpty(); }
    public double wind() { return wind; }
    public double kw() { return kwNow; }
    public String fault() { return fault; }
    public int camNo() { return camNo; }

    // ---- geometry ----------------------------------------------------------------------------------------------------------
    public static int bayAt(double x) { for (int i = 0; i < BAYS; i++) if (Math.abs(BAY_X[i] - x) < 0.35) return i; return -1; }
    public static int laneAt(double z) { for (int i = 0; i < LANES; i++) if (Math.abs(LANE_Z[i] - z) < 0.35) return i; return -1; }
    public static int tiers(ItemStack[] r) { int n = 0; for (ItemStack s : r) if (!s.isEmpty()) n++; return n; }

    /** height of the top of whatever stands at (bay, lane): 0 bare ground */
    public double top(int b, int l) {
        return switch (l) {
            case 0 -> truckHere(b) ? TRUCK_DECK + (truckBoxes(b) > 0 ? BOX_H : 0) : 0;
            case 1 -> carInv[b] != null ? CAR_DECK + (carBoxes(b) > 0 ? BOX_H : 0) : 0;
            default -> tiers(stack[b][l - 2]) * BOX_H;
        };
    }

    public boolean hasBox(int b, int l) { return l == 0 ? truckBoxes(b) > 0 : l == 1 ? carBoxes(b) > 0 : tiers(stack[b][l - 2]) > 0; }

    public boolean room(int b, int l) {
        return switch (l) {
            case 0 -> truckHere(b) && truckBoxes(b) == 0;
            case 1 -> carInv[b] != null && carFree(b) > 0;
            default -> tiers(stack[b][l - 2]) < TIERS;
        };
    }

    /** the highest thing under the spreader's footprint (between slots) */
    private double floorUnder() {
        double m = 0;
        for (int b = 0; b < BAYS; b++) {
            if (Math.abs(BAY_X[b] - gx) > BOX_L) continue;
            for (int l = 0; l < LANES; l++) if (Math.abs(LANE_Z[l] - tz) < BOX_W) m = Math.max(m, top(b, l));
        }
        return m;
    }

    /** model-frame point (block units from the desk block's centre, like BAY_X / LANE_Z; y from its base) -> world */
    public double[] toWorld(double lx, double ly, double lz) {
        EnumFacing f = controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        double x = lx, z = lz;
        for (int i = 0; i < turns; i++) { double nx = -z, nz = x; x = nx; z = nz; }
        return new double[]{pos.getX() + 0.5 + x, pos.getY() + ly, pos.getZ() + 0.5 + z};
    }

    // ---- the cars in the rail lane, the trucks in the truck lane ---------------------------------------------------------
    private final UUID[] carId = new UUID[BAYS];
    private final IItemHandler[] carInv = new IItemHandler[BAYS];
    private final IItemHandler[] truckInv = new IItemHandler[BAYS];

    private void findVehicles() {
        Arrays.fill(carId, null); Arrays.fill(carInv, null); Arrays.fill(truckInv, null);
        try {
            cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
            if (w != null)
                for (EntityRollingStock s : w.getEntities(EntityRollingStock.class)) {
                    if (s.isDead() || !(s instanceof Freight f) || f.cargoItems == null) continue;
                    if (s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null && Math.abs(m.getCurrentSpeed().metric()) > 2) continue;
                    int[] bl = local(s.getPosition().x, s.getPosition().y, s.getPosition().z);
                    if (bl != null && bl[1] == 1 && carInv[bl[0]] == null) { carId[bl[0]] = s.getUUID(); carInv[bl[0]] = f.cargoItems.internal; }
                }
        } catch (LinkageError | RuntimeException ignored) { }
        double[] a = toWorld(GX_MIN - 3, -1, LANE_Z[0] - 2), c = toWorld(GX_MAX + 3, 4, LANE_Z[1] + 2);
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(a[0], a[1], a[2], c[0], c[1], c[2]))) {   // other mods' wagons / road vehicles
            if (e instanceof EntityPlayer || e.isDead) continue;
            IItemHandler h = e.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) ? e.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)
                    : e instanceof IInventory inv && inv.getSizeInventory() > 0 ? new InvWrapper(inv) : null;
            if (h == null) continue;
            int[] bl = local(e.posX, e.posY, e.posZ);
            if (bl == null) continue;
            if (bl[1] == 1 && carInv[bl[0]] == null) { carId[bl[0]] = e.getUniqueID(); carInv[bl[0]] = h; }
            else if (bl[1] == 0 && truckInv[bl[0]] == null) truckInv[bl[0]] = h;
        }
    }

    /** a world point -> {bay, lane} it stands in (truck / rail lanes only), or null */
    private int[] local(double wx, double wy, double wz) {
        if (Math.abs(wy - pos.getY()) > 3) return null;
        double[] o = toWorld(0, 0, 0), ax = toWorld(1, 0, 0), az = toWorld(0, 0, 1);
        double dx = wx - o[0], dz = wz - o[2];
        double lx = dx * (ax[0] - o[0]) + dz * (ax[2] - o[2]), lz = dx * (az[0] - o[0]) + dz * (az[2] - o[2]);
        for (int b = 0; b < BAYS; b++) {
            if (Math.abs(lx - BAY_X[b]) > 3.2) continue;
            for (int l = 0; l < 2; l++) if (Math.abs(lz - LANE_Z[l]) < 1.4) return new int[]{b, l};
        }
        return null;
    }

    public boolean carHere(int b) { return carInv[b] != null; }
    public boolean truckHere(int b) { return truckInv[b] != null || !truck[b].isEmpty() || ctl.get("tk" + b) == 1; }

    private int carBoxes(int b) { return count(carInv[b]); }
    private int carFree(int b) {
        IItemHandler h = carInv[b];
        if (h == null) return 0;
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) if (h.getStackInSlot(i).isEmpty()) n++;
        return Math.min(n, 2 - carBoxes(b));                                            // a 40 ft flat takes two 20 ft boxes
    }
    private int truckBoxes(int b) { return truckInv[b] != null ? count(truckInv[b]) : truck[b].isEmpty() ? 0 : 1; }
    private static int count(IItemHandler h) { int n = 0; if (h != null) for (int i = 0; i < h.getSlots(); i++) if (ItemContainer.is(h.getStackInSlot(i))) n++; return n; }

    // ---- ticking -----------------------------------------------------------------------------------------------------------
    @Override
    public void update() {
        if (world.isRemote) {
            drawG += Math.max(-0.3, Math.min(0.3, gx - drawG));
            drawT += Math.max(-0.25, Math.min(0.25, tz - drawT));
            drawH += Math.max(-0.2, Math.min(0.2, hy - drawH));
            drawSX += (swayX - drawSX) * 0.3; drawSZ += (swayZ - drawSZ) * 0.3;
            return;
        }
        super.update();
    }

    @Override
    protected void serverTick() {
        if (serial == 0 && age > 2) { serial = 100 + (int) Math.floorMod(pos.toLong() * 2654435761L, 900L); markDirty(); }   // ponytail: serial from the position, a counter if two ever clash
        if (age % 20 == 0) findVehicles();
        if (age % 20 == 5) second();
        double pg = gx, pt = tz, ph = hy;
        if (running()) {
            if (!queue.isEmpty()) step();
            else if (ctl.get("k_auto") == 1 && !jobs.isEmpty()) startJob();
            else joysticks();
        } else if (!queue.isEmpty() && (estop() || !keyOn())) queue.clear();
        // the spreader swings on its ropes: the trolley's / gantry's acceleration drives it, the ropes' length sets its period
        double ng = gx - pg, nt = tz - pt;
        boolean anti = ctl.get("k_sway") == 1 && running();
        double[] sx = sway(swayX, svX, ng - vG, GIRDER - hy, anti), sz = sway(swayZ, svZ, nt - vT, GIRDER - hy, anti);
        swayX = sx[0]; svX = sx[1]; swayZ = sz[0]; svZ = sz[1];
        vG = ng; vT = nt; vH = hy - ph;
        double moved = Math.abs(ng) + Math.abs(nt) + Math.abs(vH);
        kwSum += moved < 1e-6 ? 0 : (Math.abs(ng) > 0 ? 8 * 22 * Math.abs(ng) / SPEED_G[1] : 0) + (Math.abs(nt) > 0 ? 2 * 37 * Math.abs(nt) / SPEED_T[1] : 0)
                + (Math.abs(vH) > 0 ? 2 * 160 * Math.abs(vH) / SPEED_H[1] * (vH > 0 ? 0.4 + load() / 40 : 0.15) : 0);
        kwN++;
        boolean swinging = Math.abs(swayX) + Math.abs(swayZ) > 0.01;
        if ((moved > 1e-6 || swinging) && --syncIn <= 0) { syncIn = 4; controlsChanged(); }
        if (moved > 1e-6 && age % 30 == 0) world.playSound(null, pos, PackSounds.ev("grid.motor_run"), SoundCategory.BLOCKS, 0.35f, 0.7f + (float) (moved * 2));
    }

    /**
     * one tick of the spreader swinging on its ropes (a pendulum of the rope length, block = metre, tick = 1/20 s):
     * accel = the trolley's / gantry's change of speed this tick; ANTI-SWAY damps it out in a couple of seconds.
     * {offset, velocity}
     */
    public static double[] sway(double s, double v, double accel, double ropeLen, boolean anti) {
        double w2 = 9.81 / Math.max(1.0, ropeLen) / 400.0;
        // ANTI-SWAY = the drive moves the trolley under the load (an extra restoring pull, 0.02 s) and damps the swing:
        // near-critical, so a 0.5 m swing is out in ~2 s. Only damping the speed (old 0.82) left it creeping back.
        v = (v - w2 * s - (anti ? 0.02 * s : 0) - accel) * (anti ? 0.75 : 0.994);
        s = Math.max(-1.2, Math.min(1.2, s + v));
        if (Math.abs(s) < 1e-4 && Math.abs(v) < 1e-5) return new double[]{0, 0};
        return new double[]{s, v};
    }

    /** once a second: supply, wind, alarms, camera, fees */
    private void second() {
        double motors = kwN == 0 ? 0 : kwSum / kwN;
        kwSum = 0; kwN = 0;
        boolean mcb = com.dogpound.railmap.cabinet.Cabinet.ok(ctl, "main");
        double watts = !mcb || !keyOn() ? 0 : (15 + motors) * 1000;
        boolean wasOk = mains.ok;
        mains.tick(world, pos, watts, this::burn, this::tell);
        kwNow = mains.ok ? watts / 1000 * mains.share : 0;
        hv = mains.level != null && mains.level.volts > 1000;
        reel = Math.max(3, Math.min(97, 50 + gx / (GX_MAX - GX_MIN) * 90));
        wind = windAt();
        AlarmManager am = AlarmManager.get(world);
        String k = ":" + pos.toLong();
        if (keyOn() && !mains.ok && term.powered()) am.raise(world, "rmg:sup" + k, AlarmManager.Cat.POWER, AlarmManager.Sev.MAJOR, name() + ": drives have no supply - " + mains.what, pos, false);
        else am.clear("rmg:sup" + k);
        if (wasOk && !mains.ok && keyOn()) Recorder.event(world, name() + " lost its supply: " + mains.what);
        if (storm()) am.raise(world, "rmg:wind" + k, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, String.format(Locale.ROOT, "%s: wind %.0f m/s over the %.0f m/s limit - storm pins in, crane stopped", name(), wind, GantryConfig.windLimitMs), pos, false);
        else am.clear("rmg:wind" + k);
        if (estop()) am.raise(world, "rmg:estop" + k, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, name() + ": EMERGENCY STOP in", pos, false);
        else am.clear("rmg:estop" + k);
        camera();
        if (age % 1200 == 5) {
            BizData.get(world).custody.exposure(world, carrying, pos, 1200);
            for (ItemStack[][] b : stack) for (ItemStack[] r : b) for (ItemStack s : r) BizData.get(world).custody.exposure(world, s, pos, 1200);
            com.dogpound.railmap.biz.Intermodal.Stat st = BizData.get(world).intermodal.stat(world, pos, false);
            st.quay = boxes().size(); st.quayCap = BAYS * ROWS * TIERS;
        }
        controlsChanged();
    }

    private double windAt() {
        double base;
        com.dogpound.railmap.weather.WeatherData.Cell c = null;
        if (com.dogpound.railmap.weather.Weather.on()) {
            c = com.dogpound.railmap.weather.WeatherData.get(world).cells.get(com.dogpound.railmap.weather.WeatherData.key(pos.getX() >> 6, pos.getZ() >> 6));
            if (c != null && c.lastTick == 0) c = null;
        }
        base = c != null ? c.wind : world.isThundering() ? 17 : world.isRaining() ? 9 : 3.5;
        double t = world.getTotalWorldTime();
        return Math.max(0, base * (1 + 0.12 * Math.sin(t / 37.0) + 0.08 * Math.sin(t / 11.0)) * (GIRDER > 12 ? 1.1 : 1));   // gusts; it blows harder up at the girders
    }

    public double load() { return carrying.isEmpty() ? 0 : ItemContainer.weightKg(carrying) / 1000.0; }

    private void tell(String msg) { for (EntityPlayer p : world.playerEntities) if (p.getDistanceSq(pos) < 32 * 32) p.sendStatusMessage(new TextComponentString(msg), true); }

    private void burn() {
        term.burnt = true;
        if (world instanceof WorldServer ws) ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 30, 0.3, 0.4, 0.3, 0.02);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.FUSE_BLOW, SoundCategory.BLOCKS, 1f, 0.8f);
        String msg = name() + " drives BURNT OUT: " + mains.what + " - screwdriver to repair once the supply is right";
        AlarmManager.get(world).raise(world, "rmg:burn:" + pos.toLong(), AlarmManager.Cat.FIRE, AlarmManager.Sev.MAJOR, msg, pos, true);
        Recorder.event(world, msg);
        controlsChanged();
    }

    @Override
    public void gearChanged() {
        if (!term.burnt && mains.burnt) {
            mains.burnt = false;
            Recorder.event(world, name() + " repaired (screwdriver) - drive cabinet rewired");
            AlarmManager.get(world).clear("rmg:burn:" + pos.toLong());
        }
        super.gearChanged();
    }

    // ---- the spreader camera ---------------------------------------------------------------------------------------------------
    private void camera() {
        if (!GantryConfig.camera || serial == 0) return;
        CctvData cd = CctvData.get(world);
        String id = "rmg@" + pos.toLong();
        CctvData.Cam c = camNo > 0 ? cd.cam(camNo) : null;
        if (c == null || !id.equals(c.train)) {
            c = cd.byTrain(id);
            if (c == null) {
                c = new CctvData.Cam();
                c.no = camNo > 0 && cd.cam(camNo) == null ? camNo : cd.allocCam();
                c.type = CamType.CRANE.ordinal(); c.train = id; c.dim = world.provider.getDimension(); c.pos = pos.toLong();
                c.name = (name() + " SPREADER").toUpperCase(Locale.ROOT);
                c.fov = CamType.CRANE.fov; c.pitch = CamType.CRANE.pitch;
                cd.cams.put(c.no, c);
                cd.changed();
            }
            camNo = c.no;
            markDirty();
        }
        int st = term.powered() ? CctvData.ONLINE : CctvData.OFF;
        if (c.status != st) { c.status = st; cd.changed(); }
    }

    /** TrainCams: where the spreader camera is ({x, y, z, yaw, pitch}), either side */
    public static double[] camPose(World w, String uuid, float pt) {
        try {
            BlockPos p = BlockPos.fromLong(Long.parseLong(uuid.substring(4)));
            if (!w.isBlockLoaded(p) || !(w.getTileEntity(p) instanceof TileYardGantry g)) return null;
            boolean cl = w.isRemote;
            double[] o = g.toWorld(cl ? g.drawG + g.drawSX : g.gx + g.swayX, (cl ? g.drawH : g.hy) - 0.05, cl ? g.drawT + g.drawSZ : g.tz + g.swayZ);
            return new double[]{o[0], o[1], o[2], g.controlFacing().getHorizontalAngle(), CamType.CRANE.pitch};
        } catch (RuntimeException e) { return null; }
    }

    // ---- motion ------------------------------------------------------------------------------------------------------------
    private double slow() { return wind > GantryConfig.windLimitMs * 0.8 ? 0.5 : 1; }

    private void joysticks() {
        long now = world.getTotalWorldTime();
        if (now - lastJoy > 60) for (String j : new String[]{"j_g", "j_t", "j_h"}) if (ctl.get(j) != 2) { ctl.set(j, 2); controlsChanged(); }   // spring back to centre
        int g = ctl.get("j_g") - 2, t = ctl.get("j_t") - 2, h = ctl.get("j_h") - 2;
        if (g != 0) gx = clamp(gx + Math.signum(g) * SPEED_G[Math.abs(g) - 1] * slow(), GX_MIN, GX_MAX);
        if (t != 0) {
            double nt = clamp(tz + Math.signum(t) * SPEED_T[Math.abs(t) - 1] * slow(), TZ_MIN, TZ_MAX);
            if (hy - (carrying.isEmpty() ? 0 : BOX_H) >= floorAt(gx, nt) - 0.01) tz = nt; else snag("the spreader would hit a stack - hoist up first");
        }
        if (h != 0) {
            double floor = floorUnder() + (carrying.isEmpty() ? 0 : BOX_H);
            hy = clamp(hy + Math.signum(h) * SPEED_H[Math.abs(h) - 1] * (carrying.isEmpty() ? 1 : 0.6), floor, TRAVEL);
        }
    }

    private double floorAt(double x, double z) { double ox = gx, oz = tz; gx = x; tz = z; double f = floorUnder(); gx = ox; tz = oz; return f; }

    private long lastSnag;

    private void snag(String why) {
        if (world.getTotalWorldTime() - lastSnag < 40) return;
        lastSnag = world.getTotalWorldTime();
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.7f, 0.8f);
        tell("§c" + name() + ": " + why);
    }

    private static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
    private static double toward(double v, double t, double s) { return Math.abs(t - v) <= s ? t : v + Math.signum(t - v) * s; }

    private void step() {
        double[] st = queue.peek();
        char op = (char) st[0];
        boolean done;
        switch (op) {
            case 'G' -> { gx = toward(gx, st[1], SPEED_G[1] * slow()); done = gx == st[1]; }
            case 'M' -> { tz = toward(tz, st[1], SPEED_T[1] * slow()); done = tz == st[1]; }
            case 'S' -> done = Math.abs(swayX) < 0.05 && Math.abs(swayZ) < 0.05 && Math.abs(svX) + Math.abs(svZ) < 0.01;
            case 'H', 'P', 'D' -> {
                if (op != 'H' && st[1] < 0) {
                    int b = bayAt(gx), l = laneAt(tz);
                    if (b < 0 || l < 0) { fail("The spreader isn't over a container slot"); return; }
                    if (op == 'P' && !hasBox(b, l)) { fail("Nothing to pick up at bay " + (b + 1) + " " + LANE_NAMES[l]); return; }
                    if (op == 'D' && !room(b, l)) { fail("No room at bay " + (b + 1) + " " + LANE_NAMES[l]); return; }
                    st[1] = op == 'P' ? top(b, l) : top(b, l) + BOX_H;
                }
                hy = toward(hy, st[1], SPEED_H[1] * (carrying.isEmpty() ? 1 : 0.6));
                done = hy == st[1];
            }
            case 'L' -> { String r = lock(); if (r != null) { fail(r); return; } done = true; }
            default -> { String r = unlock(); if (r != null) { fail(r); return; } done = true; }
        }
        if (done) queue.poll();
    }

    private void fail(String why) {
        queue.clear();
        fault = why;
        ctl.set("fault", 1);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.8f, 1.0f);
        AlarmManager.get(world).raise(world, "rmg:fault:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, name() + ": " + why, pos, false);
        controlsChanged();
    }

    private void travel(int b, int l) {
        queue.add(new double[]{'H', TRAVEL});
        queue.add(new double[]{'G', BAY_X[b]});
        queue.add(new double[]{'M', LANE_Z[l]});
        queue.add(new double[]{'S', 0});
    }
    private void pick() { queue.add(new double[]{'P', -1}); queue.add(new double[]{'S', 0}); queue.add(new double[]{'L', 0}); queue.add(new double[]{'H', TRAVEL}); }
    private void drop() { queue.add(new double[]{'D', -1}); queue.add(new double[]{'S', 0}); queue.add(new double[]{'U', 0}); queue.add(new double[]{'H', TRAVEL}); }

    private void startJob() {
        int[] j = jobs.remove(0);
        if (!carrying.isEmpty()) { fail("Set down the box on the spreader before AUTO"); return; }
        if (!hasBox(j[0], j[1])) { fault = "JOB SKIPPED: NOTHING AT " + slot(j[0], j[1]); return; }
        if (!room(j[2], j[3])) { fault = "JOB SKIPPED: NO ROOM AT " + slot(j[2], j[3]); return; }
        fault = "";
        horn();
        travel(j[0], j[1]); pick(); travel(j[2], j[3]); drop();
    }

    public static String slot(int b, int l) { return "B" + (b + 1) + " " + LANE_NAMES[l]; }

    private void horn() { world.playSound(null, pos, PackSounds.ev("alarm.klaxon"), SoundCategory.BLOCKS, 0.6f, 1.4f); }

    // ---- the twistlocks ------------------------------------------------------------------------------------------------------
    private boolean steady() { return Math.abs(swayX) < 0.12 && Math.abs(swayZ) < 0.12; }

    private String lock() {
        int b = bayAt(gx), l = laneAt(tz);
        if (b < 0 || l < 0) return "The spreader isn't over a container slot";
        if (!carrying.isEmpty()) return "The spreader already holds a box";
        if (!hasBox(b, l) || Math.abs(hy - top(b, l)) > 0.15) return "The spreader isn't landed on a container";
        if (!steady()) return "The spreader is swinging - wait (or ANTI-SWAY on) before locking";
        ItemStack box = ItemStack.EMPTY;
        String yard = hereName();
        switch (l) {
            case 0 -> {
                if (truckInv[b] != null) { for (int i = 0; i < truckInv[b].getSlots() && box.isEmpty(); i++) if (ItemContainer.is(truckInv[b].getStackInSlot(i))) box = truckInv[b].extractItem(i, 1, false); }
                else { box = truck[b]; truck[b] = ItemStack.EMPTY; }
                BizData.get(world).custody.event(world, box, "TRUCK", pos, placerName, "lifted off a truck by " + name() + " at " + yard);
            }
            case 1 -> {
                for (int i = 0; i < carInv[b].getSlots() && box.isEmpty(); i++) if (ItemContainer.is(carInv[b].getStackInSlot(i))) box = carInv[b].extractItem(i, 1, false);
                BlockPos y = industryYard();
                if (!box.isEmpty() && y != null && carId[b] != null) ContractData.get(world).onMoved(world, false, y, carId[b], Collections.singletonList(box.copy()));
                BizData.get(world).custody.event(world, box, "RAIL UNLOAD", pos, placerName, "lifted off car " + (carId[b] == null ? "?" : carId[b].toString().substring(0, 8)) + " by " + name());
                BizData.get(world).intermodal.stat(world, pos, false).railMoves++;
            }
            default -> {
                ItemStack[] r = stack[b][l - 2];
                for (int i = TIERS - 1; i >= 0; i--) if (!r[i].isEmpty()) { box = r[i]; r[i] = ItemStack.EMPTY; break; }
            }
        }
        if (box.isEmpty()) return "The box won't come free";
        double t = ItemContainer.weightKg(box) / 1000.0 + SPREADER_T;
        if (t > GantryConfig.swlT && ctl.get("k_ovr") == 0) {
            putBack(b, l, box);
            AlarmManager.get(world).raise(world, "rmg:ovl:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, String.format(Locale.ROOT, "%s: OVERLOAD %.1f t on the load cell (SWL %.0f t) - lift refused", name(), t, GantryConfig.swlT), pos, false);
            return String.format(Locale.ROOT, "OVERLOAD: %.1f t over the %.0f t SWL", t, GantryConfig.swlT);
        }
        carrying = box;
        locked = true;
        lifts++;
        ItemContainer.addFees(box, LIFT_FEE);
        if (ItemContainer.since(box) == 0) ItemContainer.setSince(box, world.getTotalWorldTime());
        world.playSound(null, pos, PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.9f, 0.9f);
        return null;
    }

    private void putBack(int b, int l, ItemStack box) {
        switch (l) {
            case 0 -> { if (truckInv[b] != null) ItemHandlerHelper.insertItem(truckInv[b], box, false); else truck[b] = box; }
            case 1 -> ItemHandlerHelper.insertItem(carInv[b], box, false);
            default -> { ItemStack[] r = stack[b][l - 2]; r[tiers(r)] = box; }
        }
    }

    private String unlock() {
        int b = bayAt(gx), l = laneAt(tz);
        if (b < 0 || l < 0) return "The spreader isn't over a container slot";
        if (carrying.isEmpty()) { locked = false; return null; }
        if (Math.abs(hy - (top(b, l) + BOX_H)) > 0.15) return "The box isn't set down - lower it first";
        if (!steady()) return "The box is swinging - let it settle before unlocking";
        String yard = hereName();
        switch (l) {
            case 0 -> {
                if (!truckHere(b)) return "No truck in the truck lane at bay " + (b + 1);
                if (truckInv[b] != null) { if (!ItemHandlerHelper.insertItem(truckInv[b], carrying.copy(), false).isEmpty()) return "The vehicle has no room"; }
                else { if (!truck[b].isEmpty()) return "The truck is loaded already"; truck[b] = carrying; }
                BizData.get(world).custody.event(world, carrying, "TRUCK", pos, placerName, "set on a truck by " + name() + " at " + yard);
                BizData.get(world).intermodal.stat(world, pos, false).gateMoves++;
            }
            case 1 -> {
                if (carInv[b] == null) return "No car in the rail lane at bay " + (b + 1);
                if (carFree(b) <= 0 || !ItemHandlerHelper.insertItem(carInv[b], carrying.copy(), false).isEmpty()) return "The car has no room for the box";
                BlockPos y = industryYard();
                if (y != null && carId[b] != null) ContractData.get(world).onMoved(world, true, y, carId[b], Collections.singletonList(carrying.copy()));
                BizData.get(world).custody.event(world, carrying, "RAIL LOAD", pos, placerName, "set on car " + (carId[b] == null ? "?" : carId[b].toString().substring(0, 8)) + " by " + name()
                        + (ItemContainer.destName(carrying).isEmpty() ? "" : " for " + ItemContainer.destName(carrying)));
                BizData.get(world).intermodal.stat(world, pos, false).railMoves++;
            }
            default -> {
                ItemStack[] r = stack[b][l - 2];
                int t = tiers(r);
                if (t >= TIERS) return "Bay " + (b + 1) + " " + LANE_NAMES[l] + " is stacked " + TIERS + " high";
                r[t] = carrying;
            }
        }
        carrying = ItemStack.EMPTY;
        locked = false;
        world.playSound(null, pos, PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.9f, 1.05f);
        return null;
    }

    private BlockPos industryYard() {
        Set<Long> l = com.dogpound.railmap.industry.TileIndustry.LOADED.get(world.provider.getDimension());
        BlockPos best = null;
        double bd = 64 * 64;
        if (l != null) for (long k : l) { BlockPos p = BlockPos.fromLong(k); double d = p.distanceSq(pos); if (d < bd) { bd = d; best = p; } }
        return best;
    }

    // ---- the job planner --------------------------------------------------------------------------------------------------------
    /** PLAN: discharge, load and deliver - the jobs a yard planner would give this crane now. Returns how many. */
    int plan() {
        long here = hereKey();
        int[][] h = new int[BAYS][ROWS];
        for (int b = 0; b < BAYS; b++) for (int r = 0; r < ROWS; r++) h[b][r] = tiers(stack[b][r]);
        int[] carRoom = new int[BAYS], carHas = new int[BAYS];
        boolean[] truckFree = new boolean[BAYS];
        for (int b = 0; b < BAYS; b++) { carRoom[b] = carFree(b); carHas[b] = carBoxes(b); truckFree[b] = truckHere(b) && truckBoxes(b) == 0; }
        int n0 = jobs.size();
        // 1. discharge: boxes on cars that are for this station (or for nowhere) go into the stack
        for (int b = 0; b < BAYS; b++) {
            if (carInv[b] == null) continue;
            for (int i = 0; i < carInv[b].getSlots(); i++) {
                ItemStack s = carInv[b].getStackInSlot(i);
                if (!ItemContainer.is(s) || ItemContainer.dest(s) != here && ItemContainer.dest(s) != Long.MIN_VALUE) continue;
                int[] to = freeStack(h, b);
                if (to == null) break;
                h[to[0]][to[1]]++;
                jobs.add(new int[]{b, 1, to[0], to[1] + 2});
                carRoom[b]++;
            }
        }
        // 2. load: boxes in the stack bound elsewhere go onto a car with room (top boxes first)
        for (int b = 0; b < BAYS; b++)
            for (int r = 0; r < ROWS; r++) {
                for (int t = tiers(stack[b][r]) - 1; t >= 0; t--) {
                    ItemStack s = stack[b][r][t];
                    long d = ItemContainer.dest(s);
                    if (d == Long.MIN_VALUE || d == here) break;                         // only the box on top can go; stop at one that stays
                    int car = -1;
                    for (int c = 0; c < BAYS; c++) if (carInv[c] != null && carRoom[c] > 0 && (car < 0 || Math.abs(c - b) < Math.abs(car - b))) car = c;
                    if (car < 0) break;
                    carRoom[car]--;
                    jobs.add(new int[]{b, r + 2, car, 1});
                }
            }
        // 3. deliveries: boxes for this station onto empty trucks (DELIVERY key)
        if (ctl.get("k_deliv") == 1)
            for (int b = 0; b < BAYS; b++)
                for (int r = 0; r < ROWS; r++) {
                    int t = tiers(stack[b][r]) - 1;
                    if (t < 0 || ItemContainer.dest(stack[b][r][t]) != here) continue;
                    for (int k = 0; k < BAYS; k++) if (truckFree[k]) { truckFree[k] = false; jobs.add(new int[]{b, r + 2, k, 0}); break; }
                }
        while (jobs.size() > 24) jobs.remove(jobs.size() - 1);
        return jobs.size() - n0;
    }

    /** the stack slot with room nearest bay b, lowest first */
    static int[] freeStack(int[][] h, int b) {
        int[] best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int k = 0; k < BAYS; k++) for (int r = 0; r < ROWS; r++) if (h[k][r] < TIERS) { int sc = Math.abs(k - b) * 10 + h[k][r] * 3 + r; if (sc < bestScore) { bestScore = sc; best = new int[]{k, r}; } }
        return best;
    }

    /** TRACKER: the box on the nearest container tracking terminal becomes a job (to a car with room, else an empty truck) */
    String trackerJob() {
        TileContainerTracker best = null;
        double bd = 64 * 64;
        for (TileEntity te : new ArrayList<>(world.loadedTileEntityList)) if (te instanceof TileContainerTracker t && !te.isInvalid() && te.getPos().distanceSq(pos) < bd) { bd = te.getPos().distanceSq(pos); best = t; }
        if (best == null) return "§7No container tracking terminal within 64 blocks";
        String id = best.selected();
        if (id.isEmpty()) return "§7The tracking terminal has no box on its screen - SCAN or FIND one there";
        int[] at = null;
        for (int b = 0; b < BAYS && at == null; b++) {
            for (int r = 0; r < ROWS; r++) { int t = tiers(stack[b][r]) - 1; if (t >= 0 && ItemContainer.id(stack[b][r][t]).equals(id)) { at = new int[]{b, r + 2}; break; } else for (int k = 0; k < t; k++) if (ItemContainer.id(stack[b][r][k]).equals(id)) return "§c" + id + " is under other boxes at " + slot(b, r + 2) + " - dig it out (FROM/TO) first"; }
            if (at == null && ItemContainer.is(truck[b]) && ItemContainer.id(truck[b]).equals(id)) at = new int[]{b, 0};
        }
        if (at == null) return "§7" + id + " is not in this crane's yard";
        for (int c = 0; c < BAYS; c++) if (carInv[c] != null && carFree(c) > 0 && at[1] != 1) { jobs.add(new int[]{at[0], at[1], c, 1}); return "§aJob: " + id + " " + slot(at[0], at[1]) + " -> car at bay " + (c + 1); }
        for (int k = 0; k < BAYS; k++) if (truckHere(k) && truckBoxes(k) == 0 && at[1] != 0) { jobs.add(new int[]{at[0], at[1], k, 0}); return "§aJob: " + id + " " + slot(at[0], at[1]) + " -> truck at bay " + (k + 1); }
        return "§c" + id + " found at " + slot(at[0], at[1]) + " but there is no car or truck with room";
    }

    // ---- the cab desk ------------------------------------------------------------------------------------------------------------
    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        ctl.set(id, ctl.get(id) == 0 ? 1 : 0);
        boolean on = ctl.get(id) == 1;
        switch (id) {
            case "m_estop" -> { if (on) { queue.clear(); Recorder.event(world, name() + " EMERGENCY STOP by " + (who == null ? "console" : who.getName())); } return on ? "§cEMERGENCY STOP - crane halted, brakes on" : "§aE-stop released (twist)"; }
            case "k_power" -> { if (on) world.playSound(null, pos, PackSounds.ev("grid.motor_start"), SoundCategory.BLOCKS, 0.7f, 0.8f); else queue.clear(); return on ? "§aCrane ON - drives energised" : "§eCrane OFF"; }
            case "k_auto" -> { return on ? "§aAUTO: working the job list (" + jobs.size() + " jobs)" : "§eAUTO off - the joysticks are yours"; }
            case "k_sway" -> { return on ? "§aANTI-SWAY on" : "§eANTI-SWAY off - the spreader swings"; }
            case "k_deliv" -> { return on ? "§aDELIVERY: PLAN puts boxes for this station onto empty trucks" : "§eDELIVERY off"; }
            case "k_ovr" -> { if (on) Recorder.event(world, name() + " overload OVERRIDE key turned by " + (who == null ? "console" : who.getName())); return on ? "§cOVERLOAD OVERRIDE - the SWL check is bypassed" : "§aOverload protection back on"; }
            default -> { return null; }
        }
    }

    private int sel(String id, int n) { return Math.floorMod(ctl.get(id), n); }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (id.startsWith("j_")) {                                                   // joysticks: click = a notch forward, sneak-click = a notch back
            int v = ctl.get(id), d = who != null && who.isSneaking() ? -1 : 1;
            int nv = Math.max(0, Math.min(4, v + d));
            ctl.set(id, nv);
            lastJoy = world.getTotalWorldTime();
            String[] p = {"<< FAST", "< SLOW", "CENTRE", "> SLOW", ">> FAST"};
            return "§e" + (id.equals("j_g") ? "GANTRY" : id.equals("j_t") ? "TROLLEY" : "HOIST") + ": " + p[nv] + (running() ? "" : " §c(crane not running)");
        }
        switch (id) {
            case "s_fb", "s_tb", "s_gate" -> { ctl.set(id, (sel(id, BAYS) + 1) % BAYS); return "§7" + (id.equals("s_fb") ? "FROM" : id.equals("s_tb") ? "TO" : "GATE") + " BAY " + (sel(id, BAYS) + 1); }
            case "s_fl", "s_tl" -> { ctl.set(id, (sel(id, LANES) + 1) % LANES); return "§7" + (id.equals("s_fl") ? "FROM" : "TO") + " " + LANE_NAMES[sel(id, LANES)]; }
            case "ack" -> {
                ctl.set("fault", 0); fault = "";
                for (String k : new String[]{"rmg:fault:", "rmg:ovl:"}) AlarmManager.get(world).clear(k + pos.toLong());
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.ACK_BEEP, SoundCategory.BLOCKS, 0.6f, 1f);
                return "§aAlarms acknowledged + reset";
            }
            case "horn" -> { horn(); return ""; }
            case "addjob" -> {
                int[] j = {sel("s_fb", BAYS), sel("s_fl", LANES), sel("s_tb", BAYS), sel("s_tl", LANES)};
                if (j[0] == j[2] && j[1] == j[3]) return "§7FROM and TO are the same slot";
                jobs.add(j);
                return "§aJob added: " + slot(j[0], j[1]) + " -> " + slot(j[2], j[3]) + " (" + jobs.size() + " in the list)";
            }
            case "plan" -> { int n = plan(); return n == 0 ? "§7Nothing to plan: no cars to discharge or load" + (ctl.get("k_deliv") == 1 ? ", no deliveries" : "") : "§a" + n + " job(s) planned - AUTO key to work them"; }
            case "tracker" -> { return trackerJob(); }
            case "clear" -> { int n = jobs.size(); jobs.clear(); return "§e" + n + " job(s) cleared"; }
            case "print" -> {
                List<String> out = new ArrayList<>();
                out.add(name() + " - YARD MAP");
                for (int b = 0; b < BAYS; b++) {
                    out.add("BAY " + (b + 1) + ": truck " + (truckBoxes(b) > 0 ? "LOADED" : truckHere(b) ? "empty" : "-") + ", car " + (carInv[b] != null ? carBoxes(b) + " box(es)" : "-"));
                    for (int r = 0; r < ROWS; r++) { StringBuilder sb = new StringBuilder(" R" + (r + 1) + ":"); for (int t = 0; t < tiers(stack[b][r]); t++) sb.append(' ').append(ItemContainer.id(stack[b][r][t])); out.add(sb.toString()); }
                }
                com.dogpound.railmap.stn.StnUtil.print(world, pos, controlFacing(), name() + " yard map", name(), out);
                return "§aYard map printed";
            }
            case "cam" -> { camera(); return camNo > 0 ? "§aSpreader camera is CCTV CAM " + camNo + " - call it up on any monitor or the CCTV desk" : "§7Camera off (config)"; }
            case "gatein", "gateout" -> { return gate(id.equals("gatein"), who); }
            case "tkin", "tkout" -> {
                int b = sel("s_gate", BAYS);
                if (id.equals("tkin")) { if (truckInv[b] != null || ctl.get("tk" + b) == 1) return "§7A truck already stands at bay " + (b + 1); ctl.set("tk" + b, 1); horn(); return "§aEmpty truck called forward to bay " + (b + 1); }
                if (!truck[b].isEmpty()) return "§7The truck at bay " + (b + 1) + " is loaded - GATE OUT the box first";
                ctl.set("tk" + b, 0);
                return "§7Truck at bay " + (b + 1) + " sent away";
            }
            default -> { }
        }
        if (!com.dogpound.railmap.economy.Perms.has(who, com.dogpound.railmap.economy.Perms.CRANE)) return com.dogpound.railmap.economy.Perms.no("operate the crane");
        if (!running()) return estop() ? "§cE-STOP is in" : !keyOn() ? "§cThe crane is switched OFF (CRANE key)" : !mains.ok ? "§cNo supply to the drives: " + mains.what : storm() ? "§cWIND over the limit - storm pins in" : "§cNo 12 V";
        if (busy() && !id.equals("stop")) return "§7The crane is busy - wait, or STOP";
        int b = bayAt(gx), l = laneAt(tz);
        switch (id) {
            case "lock": { String r = lock(); controlsChanged(); return r == null ? "§aTwistlocks LOCKED - " + ItemContainer.pretty(carrying) + String.format(Locale.ROOT, " (%.1f t)", load()) : "§c" + r; }
            case "unlock": { String r = unlock(); controlsChanged(); return r == null ? "§aTwistlocks released" : "§c" + r; }
            case "pick": if (b < 0 || l < 0) return "§7Drive the spreader over a slot first"; pick(); return "§7Lifting at " + slot(b, l);
            case "drop": if (b < 0 || l < 0) return "§7Drive the spreader over a slot first"; drop(); return "§7Setting down at " + slot(b, l);
            case "go": { int fb = sel("s_fb", BAYS), fl = sel("s_fl", LANES); horn(); travel(fb, fl); return "§7Travelling to " + slot(fb, fl); }
            case "bayl", "bayr": {
                int cur = b >= 0 ? b : nearestBay();
                int nb = Math.max(0, Math.min(BAYS - 1, cur + (id.equals("bayr") ? 1 : -1)));
                horn();
                queue.add(new double[]{'G', BAY_X[nb]});
                return "§7Gantry to bay " + (nb + 1);
            }
            case "stop": { queue.clear(); return "§eStopped"; }
            default: return null;
        }
    }

    private int nearestBay() { int best = 0; for (int i = 1; i < BAYS; i++) if (Math.abs(BAY_X[i] - gx) < Math.abs(BAY_X[best] - gx)) best = i; return best; }

    /** a truck at the gate bay: GATE IN the box you hold onto it (or hire one), GATE OUT the box on it to you */
    private String gate(boolean in, EntityPlayer who) {
        if (!(who instanceof EntityPlayerMP pl)) return "§7That is done at the gate, in person";
        int b = sel("s_gate", BAYS);
        com.dogpound.railmap.biz.Intermodal im = BizData.get(world).intermodal;
        String full = im.gateRefuse(world, pos, pl);
        if (full != null) return full;
        if (in) {
            if (truckInv[b] != null) return "§7A vehicle with its own load bed stands there - load it directly";
            if (!truck[b].isEmpty()) return "§7The truck at bay " + (b + 1) + " is loaded already";
            ItemStack h = pl.getHeldItemMainhand();
            if (!ItemContainer.is(h)) return "§7Hold a container to gate it in";
            truck[b] = h.splitStack(1);
            ctl.set("tk" + b, 1);
            ItemContainer.setSince(truck[b], world.getTotalWorldTime());
            String appt = im.gate(world, pos, pl, true);
            BizData.get(world).custody.event(world, truck[b], "TRUCK", pos, pl.getName(), "gated in at " + name() + " bay " + (b + 1) + appt);
            return "§aGated in on the truck at bay " + (b + 1) + ": " + ItemContainer.pretty(truck[b]) + appt;
        }
        if (truck[b].isEmpty()) return "§7No box on the truck at bay " + (b + 1);
        long due = ItemContainer.fees(truck[b]);
        if (due > 0) {
            if (!Coins.charge(pl, due, "Crane fees, " + ItemContainer.pretty(truck[b]))) return "§c" + Coins.fmt(due) + " in crane fees are due on that box";
            collected(due, "Fees on " + ItemContainer.pretty(truck[b]));
            ItemContainer.addFees(truck[b], -due);
        }
        ItemContainer.setSince(truck[b], 0);
        String appt = im.gate(world, pos, pl, false);
        BizData.get(world).custody.event(world, truck[b], "TRUCK", pos, pl.getName(), "gated out at " + name() + appt);
        String n = ItemContainer.pretty(truck[b]);
        if (!pl.inventory.addItemStackToInventory(truck[b])) pl.dropItem(truck[b], false);
        truck[b] = ItemStack.EMPTY;
        ctl.set("tk" + b, 0);
        return "§aGated out: " + n + (due > 0 ? " (fees " + Coins.fmt(due) + " paid)" : "");
    }

    private void collected(long cents, String memo) {
        long day = world.getTotalWorldTime() / CompanyData.DAY;
        if (day != feesDay) { feesDay = day; feesToday = 0; }
        feesToday += cents;
        Company c = placer == null ? null : CompanyData.get(world).of(placer);
        if (c != null) CompanyData.get(world).credit(world, c, Company.Kind.HANDLING, cents, memo);
    }

    // ---- screens + lamps -----------------------------------------------------------------------------------------------------
    @Override
    protected boolean refresh() {
        boolean ch = false;
        int b = bayAt(gx), l = laneAt(tz);
        boolean landed = b >= 0 && l >= 0 && (carrying.isEmpty() ? hasBox(b, l) && Math.abs(hy - top(b, l)) < 0.15 : Math.abs(hy - (top(b, l) + BOX_H)) < 0.15);
        ch |= StationControls.put(ctl, "pw", term.powered());
        ch |= StationControls.put(ctl, "sup", mains.ok);
        ch |= StationControls.put(ctl, "hvl", mains.ok && hv);
        ch |= StationControls.put(ctl, "on", running());
        ch |= StationControls.put(ctl, "ready", running() && queue.isEmpty());
        ch |= StationControls.put(ctl, "lockl", locked);
        ch |= StationControls.put(ctl, "unlk", !locked);
        ch |= StationControls.put(ctl, "landed", landed);
        ch |= StationControls.put(ctl, "movel", busy() || ctl.get("j_g") != 2 || ctl.get("j_t") != 2 || ctl.get("j_h") != 2);
        ch |= StationControls.put(ctl, "windw", wind > GantryConfig.windLimitMs * 0.8);
        ch |= StationControls.put(ctl, "storm", storm());
        ch |= StationControls.put(ctl, "swayl", !steady());
        ch |= StationControls.put(ctl, "autol", ctl.get("k_auto") == 1);
        ch |= StationControls.put(ctl, "estopl", estop());
        ch |= StationControls.put(ctl, "ovl", load() + SPREADER_T > GantryConfig.swlT * 0.95);
        ch |= StationControls.put(ctl, "carl", b >= 0 && carInv[b] != null);
        ch |= StationControls.put(ctl, "truckl", b >= 0 && truckHere(b));
        ch |= StationControls.put(ctl, "g_load", (int) Math.round(carrying.isEmpty() ? SPREADER_T : load() + SPREADER_T));
        ch |= StationControls.put(ctl, "g_wind", (int) Math.round(wind));
        ch |= StationControls.put(ctl, "g_kw", (int) Math.round(kwNow));
        ch |= StationControls.put(ctl, "g_reel", (int) Math.round(reel));
        for (int k = 0; k < BAYS; k++) ch |= StationControls.put(ctl, "bl" + k, k == b);
        ch |= StationControls.text(ctl, "stat", !fault.isEmpty() ? cut(fault.toUpperCase(Locale.ROOT), 30) : estop() ? "EMERGENCY STOP" : storm() ? "STORM PINS - WIND" : !mains.ok ? "NO SUPPLY" : !keyOn() ? "CRANE OFF"
                : busy() ? (ctl.get("k_auto") == 1 ? "AUTO JOB - " : "WORKING - ") + jobs.size() + " QUEUED" : "READY");
        ch |= StationControls.text(ctl, "pos", (b >= 0 ? "BAY " + (b + 1) : String.format(Locale.ROOT, "X%+.1f", gx)) + " " + (l >= 0 ? LANE_NAMES[l] : String.format(Locale.ROOT, "Z%.1f", tz)) + String.format(Locale.ROOT, " H%.1f", hy));
        ch |= StationControls.text(ctl, "load", String.format(Locale.ROOT, "LOAD %.1f T SWL %.0f", carrying.isEmpty() ? SPREADER_T : load() + SPREADER_T, GantryConfig.swlT));
        ch |= StationControls.text(ctl, "wind", String.format(Locale.ROOT, "WIND %.0f M/S LIM %.0f", wind, GantryConfig.windLimitMs));
        ch |= StationControls.text(ctl, "sway", String.format(Locale.ROOT, "SWAY %.2f M %s", Math.hypot(swayX, swayZ), ctl.get("k_sway") == 1 ? "AS ON" : "AS OFF"));
        ch |= StationControls.text(ctl, "supl", mains.ok ? (mains.level == null ? "SUPPLY" : mains.level.label.replace("φ", "PH").toUpperCase(Locale.ROOT)) + String.format(Locale.ROOT, " %.0f KW", kwNow) : cut(mains.what.toUpperCase(Locale.ROOT), 28));
        ItemStack focus = !carrying.isEmpty() ? carrying : b >= 0 && l >= 2 && tiers(stack[b][l - 2]) > 0 ? stack[b][l - 2][tiers(stack[b][l - 2]) - 1] : ItemStack.EMPTY;
        ch |= StationControls.text(ctl, "box", focus.isEmpty() ? "NO BOX" : ItemContainer.pretty(focus));
        ch |= StationControls.text(ctl, "dst", focus.isEmpty() || ItemContainer.destName(focus).isEmpty() ? "" : "TO " + cut(ItemContainer.destName(focus).toUpperCase(Locale.ROOT), 14));
        for (int i = 0; i < 4; i++) ch |= StationControls.text(ctl, "j" + (i + 1), i < jobs.size() ? (i + 1) + " " + slot(jobs.get(i)[0], jobs.get(i)[1]) + " > " + slot(jobs.get(i)[2], jobs.get(i)[3]) : i == 0 ? "NO JOBS" : "");
        for (int r = 0; r < ROWS; r++) {
            StringBuilder sb = new StringBuilder("R" + (r + 1) + " ");
            for (int k = 0; k < BAYS; k++) sb.append(tiers(stack[k][r])).append(k < BAYS - 1 ? " " : "");
            ch |= StationControls.text(ctl, "m" + (r + 1), sb.toString());
        }
        ch |= StationControls.text(ctl, "from", slot(sel("s_fb", BAYS), sel("s_fl", LANES)) + " > " + slot(sel("s_tb", BAYS), sel("s_tl", LANES)));
        ch |= StationControls.text(ctl, "gate", "GATE BAY " + (sel("s_gate", BAYS) + 1) + (truck[sel("s_gate", BAYS)].isEmpty() ? (truckHere(sel("s_gate", BAYS)) ? " EMPTY TRUCK" : " NO TRUCK") : " LOADED"));
        ch |= StationControls.text(ctl, "camt", camNo > 0 ? "CAM " + camNo : "CAM -");
        ch |= StationControls.text(ctl, "fees", Coins.fmt(feesToday));
        ch |= StationControls.text(ctl, "lifts", lifts + " LIFTS");
        return ch;
    }

    @Override public String gearState() { return (running() ? "RUNNING" : keyOn() ? "stopped" : "off") + " · " + mains.status().replaceAll("§.", "") + " · " + boxes().size() + " boxes · " + jobs.size() + " jobs" + (busy() ? " · working" : ""); }
    @Override public void gearInfo(Map<String, Object> m) { m.put("supply", mains.what); m.put("kw", kwNow); m.put("wind", wind); m.put("jobs", jobs.size()); m.put("boxes", boxes().size()); m.put("cam", camNo); }
    @Override public int wiredLamp() { return !term.powered() || estop() || mains.burnt ? 3 : busy() ? 2 : 1; }

    /** every box in the yard, for the console: {id, where, dest, contents} */
    public List<String[]> boxes() {
        List<String[]> out = new ArrayList<>();
        if (!carrying.isEmpty()) out.add(new String[]{ItemContainer.pretty(carrying), "ON THE SPREADER", ItemContainer.destName(carrying), ItemContainer.manifest(carrying)});
        for (int b = 0; b < BAYS; b++) {
            if (!truck[b].isEmpty()) out.add(new String[]{ItemContainer.pretty(truck[b]), "B" + (b + 1) + " TRUCK", ItemContainer.destName(truck[b]), ItemContainer.manifest(truck[b])});
            for (int r = 0; r < ROWS; r++) for (int t = 0; t < TIERS; t++) { ItemStack s = stack[b][r][t]; if (!s.isEmpty()) out.add(new String[]{ItemContainer.pretty(s), "B" + (b + 1) + " R" + (r + 1) + " T" + (t + 1), ItemContainer.destName(s), ItemContainer.manifest(s)}); }
        }
        return out;
    }

    /** /irextras gantry give: test boxes into the stack */
    int give(int n, EntityPlayer p) {
        int k = 0;
        for (int b = 0; b < BAYS && k < n; b++) for (int r = 0; r < ROWS && k < n; r++) while (tiers(stack[b][r]) < 2 && k < n) {
            ItemStack box = ItemContainer.make(CommonProxy.container, "TST", world.rand.nextInt(1_000_000), p == null ? null : p.getUniqueID(), p == null ? "test" : p.getName());
            stack[b][r][tiers(stack[b][r])] = box; k++;
        }
        controlsChanged();
        return k;
    }

    @Override
    public void dropAll() {
        super.dropAll();
        List<ItemStack> all = new ArrayList<>(Arrays.asList(truck));
        all.add(carrying);
        for (ItemStack[][] b : stack) for (ItemStack[] r : b) all.addAll(Arrays.asList(r));
        for (ItemStack s : all) if (!s.isEmpty()) net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, s);
        if (camNo > 0) { CctvData cd = CctvData.get(world); CctvData.Cam c = cd.cam(camNo); if (c != null && ("rmg@" + pos.toLong()).equals(c.train)) { cd.cams.remove(camNo); cd.changed(); } }
        for (String k : new String[]{"rmg:sup:", "rmg:wind:", "rmg:estop:", "rmg:fault:", "rmg:ovl:", "rmg:burn:"}) AlarmManager.get(world).clear(k + pos.toLong());
    }

    @Override public AxisAlignedBB getRenderBoundingBox() { return INFINITE_EXTENT_AABB; }

    // ---- save / sync -----------------------------------------------------------------------------------------------------------
    @Override
    protected void writeExtra(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (ItemStack[][] b : stack) for (ItemStack[] r : b) for (ItemStack s : r) l.appendTag(s.writeToNBT(new NBTTagCompound()));
        t.setTag("stack", l);
        NBTTagList tk = new NBTTagList();
        for (ItemStack s : truck) tk.appendTag(s.writeToNBT(new NBTTagCompound()));
        t.setTag("truck", tk);
        t.setTag("carry", carrying.writeToNBT(new NBTTagCompound()));
        t.setDouble("gx", gx); t.setDouble("tz", tz); t.setDouble("hy", hy); t.setDouble("sx", swayX); t.setDouble("sz", swayZ);
        t.setBoolean("locked", locked); t.setBoolean("hv", hv); t.setDouble("reel", reel);
        t.setString("fault", fault); t.setInteger("cam", camNo); t.setInteger("serial", serial);
        t.setLong("fees", feesToday); t.setLong("feesDay", feesDay); t.setLong("lifts", lifts); t.setDouble("wind", wind); t.setDouble("kw", kwNow);
        NBTTagList q = new NBTTagList();
        for (double[] s : queue) { NBTTagCompound x = new NBTTagCompound(); x.setDouble("o", s[0]); x.setDouble("v", s[1]); q.appendTag(x); }
        t.setTag("queue", q);
        int[] js = new int[jobs.size() * 4];
        for (int i = 0; i < jobs.size(); i++) System.arraycopy(jobs.get(i), 0, js, i * 4, 4);
        t.setIntArray("jobs", js);
        mains.write(t);
    }

    @Override
    protected void readExtra(NBTTagCompound t) {
        NBTTagList l = t.getTagList("stack", 10);
        int i = 0;
        for (ItemStack[][] b : stack) for (ItemStack[] r : b) for (int k = 0; k < TIERS; k++, i++) r[k] = i < l.tagCount() ? new ItemStack(l.getCompoundTagAt(i)) : ItemStack.EMPTY;
        NBTTagList tk = t.getTagList("truck", 10);
        for (int k = 0; k < BAYS; k++) truck[k] = k < tk.tagCount() ? new ItemStack(tk.getCompoundTagAt(k)) : ItemStack.EMPTY;
        carrying = new ItemStack(t.getCompoundTag("carry"));
        if (t.hasKey("gx")) { gx = t.getDouble("gx"); tz = t.getDouble("tz"); hy = t.getDouble("hy"); }
        swayX = t.getDouble("sx"); swayZ = t.getDouble("sz");
        locked = t.getBoolean("locked"); hv = t.getBoolean("hv"); reel = t.hasKey("reel") ? t.getDouble("reel") : 50;
        fault = t.getString("fault"); camNo = t.getInteger("cam"); serial = t.getInteger("serial");
        feesToday = t.getLong("fees"); feesDay = t.getLong("feesDay"); lifts = t.getLong("lifts"); wind = t.getDouble("wind"); kwNow = t.getDouble("kw");
        queue.clear();
        NBTTagList q = t.getTagList("queue", 10);
        for (int k = 0; k < q.tagCount(); k++) queue.add(new double[]{q.getCompoundTagAt(k).getDouble("o"), q.getCompoundTagAt(k).getDouble("v")});
        jobs.clear();
        int[] js = t.getIntArray("jobs");
        for (int k = 0; k + 3 < js.length; k += 4) jobs.add(new int[]{js[k], js[k + 1], js[k + 2], js[k + 3]});
        mains.read(t);
        if (world == null || !world.isRemote) return;
        if (Math.abs(drawG - gx) > 6) drawG = gx;
        if (Math.abs(drawT - tz) > 6) drawT = tz;
        if (Math.abs(drawH - hy) > 6) drawH = hy;
    }

    /** clients get the boxes without their contents (a full yard is 64 boxes of 27 stacks each) */
    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound t = writeToNBT(new NBTTagCompound());
        for (String k : new String[]{"stack", "truck"}) {
            NBTTagList l = t.getTagList(k, 10);
            for (int i = 0; i < l.tagCount(); i++) slim(l.getCompoundTagAt(i));
        }
        slim(t.getCompoundTag("carry"));
        return t;
    }

    /** ItemStack.writeToNBT shares the stack's own tag: copy it before taking the contents out */
    private static void slim(NBTTagCompound c) {
        if (!c.hasKey("tag")) return;
        NBTTagCompound tg = c.getCompoundTag("tag").copy();
        tg.removeTag("inv");
        c.setTag("tag", tg);
    }
}
