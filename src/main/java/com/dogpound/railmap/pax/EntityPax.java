package com.dogpound.railmap.pax;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One visible passenger. Deliberately a plain {@link Entity}, not a living one: no AI, health, potions or the
 * LivingUpdateEvent every other mod in a 900-mod pack would run on it; IR's trains ignore non-living entities, so it can
 * never be hit or block a train. No collision box (players and each other walk straight through), can't be hurt, and
 * it is never saved: a passenger is only a picture of the numbers in PassengerData, so a chunk unloading or a restart
 * loses nobody - the station draws them again from the numbers. All the thinking runs on the server; the client only
 * smooths the movement and swings the legs.
 */
public class EntityPax extends Entity {
    public static final int ARRIVE = 0, TICKET = 1, TO_SPOT = 2, WAIT = 3, SIT = 4, CONCOURSE = 5, BOARD = 6, EXIT = 7, FOLLOW = 8, ALIGHT = 9;
    public static final String[] STATES = {"Arriving", "Buying a ticket", "Going to the platform", "Waiting", "Sitting", "In the concourse",
            "Boarding", "Heading for the exit", "Travelling with family", "Getting off"};
    // variant bits: skin 0-5, accessory 6-8, kid 9, wheelchair 10, slim 11, accessory colour 12-15
    public static final int SKINS = 48;
    public static final int ACC_NONE = 0, ACC_SUITCASE = 1, ACC_BACKPACK = 2, ACC_SHOULDER = 3, ACC_BRIEFCASE = 4;

    private static final DataParameter<Integer> VARIANT = EntityDataManager.createKey(EntityPax.class, DataSerializers.VARINT);
    private static final DataParameter<Byte> STATE = EntityDataManager.createKey(EntityPax.class, DataSerializers.BYTE);
    private static final DataParameter<String> DEST = EntityDataManager.createKey(EntityPax.class, DataSerializers.STRING);
    private static final DataParameter<String> NOTE = EntityDataManager.createKey(EntityPax.class, DataSerializers.STRING);
    private static final DataParameter<Float> HEAD = EntityDataManager.createKey(EntityPax.class, DataSerializers.FLOAT);

    // ---- server brain (never saved) ----
    PaxWorld.Site site;
    long dest;
    int node = -1, spot = -1, timer, doorNode = -1, stuck;
    PaxGraph.Seat seat;
    EntityPax parent;
    final List<EntityPax> kids = new ArrayList<>();
    UUID train;
    double[] doorPt;
    double speed = 0.085, ox, oz;
    long since;
    boolean reacted;

    // ---- client smoothing / animation ----
    private int lerpSteps;
    private double lx, ly, lz;
    private float lyaw;
    public float limbSwing, limbSwingAmount, prevLimbSwingAmount, head, prevHead;

    public EntityPax(World w) {
        super(w);
        setSize(0.6f, 1.8f);
        noClip = true;
        isImmuneToFire = true;
        setEntityInvulnerable(true);
    }

    @Override
    protected void entityInit() {
        dataManager.register(VARIANT, 0);
        dataManager.register(STATE, (byte) 0);
        dataManager.register(DEST, "");
        dataManager.register(NOTE, "");
        dataManager.register(HEAD, 0f);
    }

    // ---- synced look --------------------------------------------------------------------------------------------------
    public int variant() { return dataManager.get(VARIANT); }
    void setVariant(int v) { dataManager.set(VARIANT, v); if (kid()) setSize(0.4f, 1.1f); }
    public int skin() { return variant() & 63; }
    public int accessory() { return variant() >> 6 & 7; }
    public boolean kid() { return (variant() >> 9 & 1) != 0; }
    public boolean wheelchair() { return (variant() >> 10 & 1) != 0; }
    public boolean slim() { return (variant() >> 11 & 1) != 0; }
    public int accColour() { return variant() >> 12 & 15; }
    public static int makeVariant(int skin, int acc, boolean kid, boolean chair, boolean slim, int colour) {
        return (skin & 63) | (acc & 7) << 6 | (kid ? 1 : 0) << 9 | (chair ? 1 : 0) << 10 | (slim ? 1 : 0) << 11 | (colour & 15) << 12;
    }
    public int state() { return dataManager.get(STATE); }
    void setState(int s) { if (state() != s) dataManager.set(STATE, (byte) s); }
    public String destName() { return dataManager.get(DEST); }
    void setDestName(String s) { if (!s.equals(destName())) dataManager.set(DEST, s); }
    public String note() { return dataManager.get(NOTE); }
    void setNote(String s) { if (!s.equals(note())) dataManager.set(NOTE, s); }
    public boolean sitting() { return state() == SIT || wheelchair(); }
    void look(float yaw) { if (Math.abs(MathHelper.wrapDegrees(yaw - dataManager.get(HEAD))) > 2) dataManager.set(HEAD, yaw); }

    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        if (VARIANT.equals(key) && kid()) setSize(0.4f, 1.1f);
    }

    // ---- safety: never in anyone's way, never hurt, never saved -------------------------------------------------------
    @Override public AxisAlignedBB getCollisionBoundingBox() { return null; }
    @Override public AxisAlignedBB getCollisionBox(Entity e) { return null; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }         // never catches a click; the tooltip does its own look test
    @Override public void applyEntityCollision(Entity e) { }
    @Override public boolean attackEntityFrom(DamageSource s, float a) { return false; }
    @Override public boolean hitByEntity(Entity e) { return true; }
    @Override public boolean isImmuneToExplosions() { return true; }
    @Override public boolean writeToNBTOptional(NBTTagCompound t) { return false; }
    @Override public boolean writeToNBTAtomically(NBTTagCompound t) { return false; }
    @Override public boolean getAlwaysRenderNameTag() { return false; }
    @Override public boolean hasCustomName() { return false; }
    @Override public boolean isPushedByWater() { return false; }
    @Override protected void readEntityFromNBT(NBTTagCompound t) { }
    @Override protected void writeEntityToNBT(NBTTagCompound t) { }
    @Override public boolean shouldRenderInPass(int pass) { return pass == 0; }

    @Override
    public void setDead() {
        super.setDead();
        if (!world.isRemote) release();
    }

    void release() {
        if (site != null && spot >= 0) site.taken.remove(spot);
        spot = -1;
        if (seat != null && seat.taken == this) seat.taken = null;
        seat = null;
    }

    // ---- ticking ------------------------------------------------------------------------------------------------------
    @Override
    public void onUpdate() {
        prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
        prevRotationYaw = rotationYaw; prevRotationPitch = rotationPitch;
        if (world.isRemote) { clientTick(); return; }
        if (site == null || site.dead || posY < -64) { setDead(); return; }
        try { brain(); } catch (RuntimeException e) { setDead(); }
    }

    @Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int inc, boolean teleport) {
        lx = x; ly = y; lz = z; lyaw = yaw; lerpSteps = Math.max(1, inc);
    }

    private void clientTick() {
        if (lerpSteps > 0) {
            double nx = posX + (lx - posX) / lerpSteps, ny = posY + (ly - posY) / lerpSteps, nz = posZ + (lz - posZ) / lerpSteps;
            rotationYaw += MathHelper.wrapDegrees(lyaw - rotationYaw) / lerpSteps;
            lerpSteps--;
            setPosition(nx, ny, nz);
        }
        prevLimbSwingAmount = limbSwingAmount;
        double dx = posX - prevPosX, dz = posZ - prevPosZ;
        float f = sitting() ? 0 : Math.min(1f, MathHelper.sqrt(dx * dx + dz * dz) * 4f);
        limbSwingAmount += (f - limbSwingAmount) * 0.4f;
        limbSwing += limbSwingAmount;
        prevHead = head;
        float want = dataManager.get(HEAD);
        float rel = MathHelper.clamp(MathHelper.wrapDegrees(want - rotationYaw), -70, 70);
        if (state() != WAIT && state() != SIT && state() != CONCOURSE && state() != TICKET) rel = 0;
        head += (rel - head) * 0.15f;
    }

    // ---- the brain ------------------------------------------------------------------------------------------------------
    private void brain() {
        PaxGraph g = site.g;
        if (g == null) { setDead(); return; }
        switch (state()) {
            case FOLLOW -> follow();
            case ARRIVE -> { if (walk(PaxGraph.F_MACHINE)) { setState(TICKET); timer = 40 + rand.nextInt(60); faceMachine(); } }
            case TICKET -> { if (--timer <= 0) toPlatform(); }
            case TO_SPOT -> {
                if (seat == null && spot < 0) site.pickSpot(this);
                long goal = seat != null ? seat.node : spot >= 0 ? spot : PaxGraph.F_PLATFORM;
                if (walk(goal)) {
                    if (seat != null) { setPosition(seat.x, seat.y, seat.z); rotationYaw = seat.yaw; setState(SIT); }
                    else { setState(WAIT); rotationYaw = node >= 0 ? g.faceYaw[node] + (rand.nextFloat() - 0.5f) * 40 : rotationYaw; }
                    if (since == 0) since = world.getTotalWorldTime();
                    look(rotationYaw); timer = 40 + rand.nextInt(120);
                }
            }
            case WAIT, SIT -> idle(g);
            case CONCOURSE -> {
                if (spot < 0) site.pickConcourse(this);
                if (walk(spot >= 0 ? spot : PaxGraph.F_CONCOURSE)) idle(g);
            }
            case BOARD -> board(g);
            case EXIT -> { if (walk(PaxGraph.F_EXIT)) setDead(); }
            case ALIGHT -> { if (doorNode < 0 || walk(doorNode)) { setState(EXIT); } }
            default -> setDead();
        }
    }

    void toPlatform() { release(); stuck = 0; setState(TO_SPOT); }
    void toConcourse() { release(); stuck = 0; setState(CONCOURSE); }
    void toExit(String why) { release(); stuck = 0; setState(EXIT); if (why != null) setNote(why); for (EntityPax k : kids) k.setNote(why == null ? "" : why); }

    void toBoard(UUID loco, int door, double[] pt) {
        release(); stuck = 0; train = loco; doorNode = door; doorPt = pt; timer = 0; setState(BOARD);
    }

    private void idle(PaxGraph g) {
        if (--timer > 0) return;
        timer = 60 + rand.nextInt(160);
        int r = rand.nextInt(10);
        BlockPos b = nearestBoard(g);
        if (b != null && (r < 4 || site.disrupted(dest))) look(PaxGraph.yaw(b.getX() + 0.5 - posX, b.getZ() + 0.5 - posZ));
        else if (r < 8) look(state() == SIT ? rotationYaw + (rand.nextFloat() - 0.5f) * 90 : (node >= 0 && g.platform[node] ? g.faceYaw[node] : rotationYaw) + (rand.nextFloat() - 0.5f) * 60);
        else if (state() == WAIT && rand.nextInt(4) == 0) toPlatform();       // shuffle along a bit
    }

    private BlockPos nearestBoard(PaxGraph g) {
        BlockPos best = null; double bd = 18 * 18;
        for (BlockPos b : g.boards) { double d = b.distanceSqToCenter(posX, posY, posZ); if (d < bd) { bd = d; best = b; } }
        return best;
    }

    private void faceMachine() {
        int[] m = null; double bd = Double.MAX_VALUE;
        for (int[] x : site.g.machines) { double d = PaxGraph.sq(x[1] + 0.5 - posX) + PaxGraph.sq(x[3] + 0.5 - posZ); if (d < bd) { bd = d; m = x; } }
        if (m != null) { rotationYaw = PaxGraph.yaw(m[1] + 0.5 - posX, m[3] + 0.5 - posZ); look(rotationYaw); }
    }

    private void board(PaxGraph g) {
        PaxWorld.Door d = site.door(world, train, doorNode);
        if (d == null) { setDead(); return; }                                   // the train left: they're aboard (the numbers already have them)
        if (timer > 0) {                                                        // stepping in through the doorway
            stepToward(doorPt[0], doorPt[1], doorPt[2], 0.09);
            if (--timer <= 0) setDead();
            return;
        }
        if (walk(doorNode) && PaxWorld.doorsOpen(d)) timer = 12;
    }

    private void follow() {
        if (parent == null || parent.isDead) { setDead(); return; }
        setState(FOLLOW);
        double tx = parent.posX + ox, ty = parent.posY, tz = parent.posZ + oz;
        if (parent.state() == SIT && parent.seat != null) { tx = parent.posX + ox * 1.5; tz = parent.posZ + oz * 1.5; ty = parent.node >= 0 ? site.g.cy(parent.node) : parent.posY; }
        double d2 = PaxGraph.sq(tx - posX) + PaxGraph.sq(tz - posZ);
        if (d2 > 64) setPosition(tx, ty, tz);
        else if (d2 > 0.04) stepToward(tx, ty, tz, Math.max(0.06, parent.speed * 1.15));
        else { rotationYaw = parent.rotationYaw; look(parent.dataManager.get(HEAD)); }
    }

    /** one step along the field towards a goal; true = there */
    private boolean walk(long goal) {
        PaxGraph g = site.g;
        int[] f = g.field(goal);
        if (node < 0 || node >= g.n) node = g.nearest(posX, posY, posZ, 6, false);
        if (node < 0) { setDead(); return false; }
        double tx = g.cx(node) + ox, ty = g.cy(node), tz = g.cz(node) + oz;
        if (PaxGraph.sq(tx - posX) + PaxGraph.sq(tz - posZ) + PaxGraph.sq(ty - posY) <= speed * speed * 1.2) {
            setPosition(tx, ty, tz);
            if (f[node] == PaxGraph.INF) { if (++stuck > 3) { return true; } }            // can't get there from here: stop trying
            int nx = g.next(f, node, rand);
            if (nx < 0) return true;
            node = nx;
            BlockPos gate = g.gates.get(nx);
            if (gate != null) site.gate(world, gate);
            tx = g.cx(node) + ox; ty = g.cy(node); tz = g.cz(node) + oz;
        }
        stepToward(tx, ty, tz, speed);
        return false;
    }

    private void stepToward(double tx, double ty, double tz, double sp) {
        double dx = tx - posX, dy = ty - posY, dz = tz - posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d <= sp) { setPosition(tx, ty, tz); return; }
        setPosition(posX + dx / d * sp, posY + dy / d * sp, posZ + dz / d * sp);
        if (dx * dx + dz * dz > 1e-4) {
            float want = PaxGraph.yaw(dx, dz);
            rotationYaw += MathHelper.clamp(MathHelper.wrapDegrees(want - rotationYaw), -20, 20);   // (no head sync while walking: the head follows the body)
        }
    }

    public boolean waitingType() {
        int s = state();
        if (s == FOLLOW) return parent != null && !parent.isDead && parent.waitingType();
        return s == ARRIVE || s == TICKET || s == TO_SPOT || s == WAIT || s == SIT || s == CONCOURSE;
    }
}
