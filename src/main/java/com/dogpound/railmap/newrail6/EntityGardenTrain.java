package com.dogpound.railmap.newrail6;

import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * A ride-on garden railway train (batch 6 #19): a little 0-4-0 tank locomotive, a driving truck and two sit-astride
 * passenger cars on 7 1/4 inch gauge Garden Track - one entity, the cars drawn and seated where they are behind the
 * locomotive along the track. The first rider drives from the driving truck (W = open the regulator, S = brake); with
 * nobody driving it runs on its own when a Garden Station dispatches it (at the station's line speed) and stops at
 * every station that shows red. Right-click to sit (6 seats), sneak to get off, sneak + hit to pick the set back up.
 */
public class EntityGardenTrain extends Entity {
    static final DataParameter<BlockPos> CUR = EntityDataManager.createKey(EntityGardenTrain.class, DataSerializers.BLOCK_POS);
    static final DataParameter<Byte> SIDES = EntityDataManager.createKey(EntityGardenTrain.class, DataSerializers.BYTE);
    static final DataParameter<Float> T = EntityDataManager.createKey(EntityGardenTrain.class, DataSerializers.FLOAT);
    static final DataParameter<Float> V = EntityDataManager.createKey(EntityGardenTrain.class, DataSerializers.FLOAT);
    static final DataParameter<Integer> NUM = EntityDataManager.createKey(EntityGardenTrain.class, DataSerializers.VARINT);

    /** car centres behind the locomotive's centre (m): driving truck, car 1, car 2 */
    public static final double[] CARS = {1.25, 2.6, 3.95};
    public static final double ACC = 0.6, BRAKE = 1.2, EBRAKE = 3.0;

    /** server: driverless dispatch, and the station block it last left (not to stop there again) */
    boolean running;
    BlockPos left;
    double target;
    long stoppedAt = -1;
    double lineSpeed = 1.5;
    /** client: the cars' poses this tick and last (for smooth drawing) */
    public double[][] carPose = new double[3][], prevCarPose = new double[3][];

    public EntityGardenTrain(World w) {
        super(w);
        setSize(0.9f, 1.0f);
        ignoreFrustumCheck = true;
    }

    @Override
    protected void entityInit() {
        dataManager.register(CUR, BlockPos.ORIGIN);
        dataManager.register(SIDES, (byte) 0);
        dataManager.register(T, 0.5f);
        dataManager.register(V, 0f);
        dataManager.register(NUM, 1);
    }

    public Garden.State state() {
        byte b = dataManager.get(SIDES);
        return new Garden.State(dataManager.get(CUR), EnumFacing.byHorizontalIndex(b & 3), EnumFacing.byHorizontalIndex(b >> 2 & 3), dataManager.get(T));
    }

    void set(Garden.State s) {
        dataManager.set(CUR, s.cur);
        dataManager.set(SIDES, (byte) (s.in.getHorizontalIndex() | s.out.getHorizontalIndex() << 2));
        dataManager.set(T, (float) s.t);
    }

    public double speed() { return dataManager.get(V); }
    public int number() { return dataManager.get(NUM); }
    public String name() { return "GARDEN TRAIN " + number(); }

    /** place a new train on track at p, heading out the side nearest `look` */
    public static EntityGardenTrain place(World w, BlockPos p, EnumFacing look, int number) {
        Garden.Shape sh = Garden.at(w, p);
        if (sh == null) return null;
        EnumFacing out = sh.has(look) ? look : sh.has(look.rotateY()) ? look.rotateY() : sh.b;
        EntityGardenTrain e = new EntityGardenTrain(w);
        e.dataManager.set(NUM, number);
        Garden.State s = new Garden.State(p, sh.other(out), out, 0.5);
        Garden.State fit = Garden.back(w, s, CARS[2] + 0.7);                   // room for the cars behind
        if (fit.end) s = Garden.forward(w, fit, CARS[2] + 0.7);
        e.set(s);
        double[] q = s.pose();
        e.setLocationAndAngles(q[0], q[1], q[2], (float) q[3], 0);
        return e;
    }

    public EntityPlayer driver() {
        List<Entity> ps = getPassengers();
        return !ps.isEmpty() && ps.get(0) instanceof EntityPlayer p ? p : null;
    }

    @Override
    public void onUpdate() {
        prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
        prevRotationYaw = rotationYaw;
        super.onUpdate();
        Garden.State s = state();
        if (world.isRemote) { place(s); poses(s); return; }
        if (Garden.at(world, s.cur) == null) {                                   // the track under it was lifted
            eject(); dropSet(); return;
        }
        double v = speed();
        EntityPlayer drv = driver();
        TileGardenStation st = NR6Ticker.gardenStation(world, s.cur);
        boolean estop = TileGardenStation.estopNear(world, s.cur);
        double want;
        if (drv != null) want = drv.moveForward > 0.1 ? NR6Config.gardenTopSpeed : drv.moveForward < -0.1 ? -1 : v;
        else want = running ? lineSpeed : 0;
        // the station's starter signal: stop at mid-platform when it shows red
        if (st != null && !s.cur.equals(left) && st.red(this)) {
            double toGo = (0.5 - s.t) * Garden.len(s.in, s.out);
            if (toGo >= -0.05) {
                double vmax = Math.sqrt(2 * BRAKE * Math.max(0, toGo));
                if (want > vmax || want < 0) want = vmax;
                if (toGo < 0.03 && v < 0.35) { v = 0; want = 0; if (stoppedAt < 0) { stoppedAt = world.getTotalWorldTime(); st.arrived(this); } }
            }
        }
        if (st != null && stoppedAt >= 0 && !st.red(this)) {                    // starter cleared: away
            left = s.cur; stoppedAt = -1; running = true;
            st.departed(this);
        }
        if (st == null && left != null && !left.equals(s.cur)) left = null;
        if (estop) want = 0;
        if (want < 0) v = Math.max(0, v - BRAKE * 0.05);
        else if (want > v) v = Math.min(want, v + ACC * 0.05);
        else v = Math.max(want, v - (estop ? EBRAKE : BRAKE) * 0.05);
        if (v > 0) {
            Garden.State n = Garden.forward(world, s, v * 0.05);
            if (n.end) { v = 0; running = false; }
            if (!n.cur.equals(s.cur) && ticksExisted % 2 == 0) PackSounds.play(world, posX, posY, posZ, "train.rail_joint", 0.15f, 1.8f);
            s = n;
            set(s);
        }
        dataManager.set(V, (float) v);
        place(s);
        if (v > 0.2 && ticksExisted % 40 == 0 && world.rand.nextInt(3) == 0) PackSounds.play(world, posX, posY, posZ, "cab.whistle", 0.25f, 1.9f);
    }

    void place(Garden.State s) {
        double[] q = s.pose();
        setPosition(q[0], q[1], q[2]);
        rotationYaw = (float) q[3];
    }

    /** client: where the three cars are */
    void poses(Garden.State s) {
        for (int i = 0; i < CARS.length; i++) {
            prevCarPose[i] = carPose[i];
            carPose[i] = Garden.back(world, s, CARS[i]).pose();
            if (prevCarPose[i] == null) prevCarPose[i] = carPose[i];
        }
    }

    void eject() { for (Entity e : new java.util.ArrayList<>(getPassengers())) e.dismountRidingEntity(); }

    void dropSet() {
        setDead();
        entityDropItem(new ItemStack(NR6Registry.gardenSet), 0.3f);
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (world.isRemote || isDead) return false;
        if (src.getTrueSource() instanceof EntityPlayer p && p.isSneaking() && getPassengers().isEmpty()) {
            if (p.capabilities.isCreativeMode) setDead(); else dropSet();
            return true;
        }
        return false;
    }

    @Override
    public boolean processInitialInteract(EntityPlayer p, EnumHand hand) {
        if (world.isRemote || isPassenger(p)) return true;
        if (getPassengers().size() >= 6) { p.sendStatusMessage(new TextComponentString("§cAll six seats taken"), true); return true; }
        p.startRiding(this);
        p.sendStatusMessage(new TextComponentString(getPassengers().size() == 1 ? "§bYou are driving " + name() + " - W regulator, S brake, sneak to get off" : "§bAll aboard " + name()), true);
        return true;
    }

    @Override protected boolean canFitPassenger(Entity e) { return getPassengers().size() < 6; }
    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public AxisAlignedBB getCollisionBoundingBox() { return null; }
    @Override public boolean shouldRiderSit() { return true; }
    @Override public double getMountedYOffset() { return 0.25; }

    @Override
    public void updatePassenger(Entity e) {
        if (!isPassenger(e)) return;
        int k = getPassengers().indexOf(e);
        Garden.State s = state();
        double[] q = Garden.back(world, s, CARS[k / 2] + (k % 2 == 0 ? -0.25 : 0.3)).pose();
        e.setPosition(q[0], q[1] + 0.32 + e.getYOffset(), q[2]);
    }

    @Override public boolean isInRangeToRenderDist(double d) { return d < 128 * 128; }

    @Override
    protected void readEntityFromNBT(NBTTagCompound t) {
        BlockPos c = BlockPos.fromLong(t.getLong("cur"));
        set(new Garden.State(c, EnumFacing.byHorizontalIndex(t.getByte("in")), EnumFacing.byHorizontalIndex(t.getByte("out")), t.getDouble("t")));
        dataManager.set(NUM, Math.max(1, t.getInteger("num")));
        running = t.getBoolean("run");
        lineSpeed = t.hasKey("line") ? t.getDouble("line") : 1.5;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound t) {
        Garden.State s = state();
        t.setLong("cur", s.cur.toLong());
        t.setByte("in", (byte) s.in.getHorizontalIndex());
        t.setByte("out", (byte) s.out.getHorizontalIndex());
        t.setDouble("t", s.t);
        t.setInteger("num", number());
        t.setBoolean("run", running);
        t.setDouble("line", lineSpeed);
    }

    public String line() {
        return String.format(Locale.ROOT, "%s %.1f M/S %s", name(), speed(), driver() != null ? "DRIVEN" : running ? "AUTO" : "STOPPED");
    }
}
