package com.dogpound.railmap.pax;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.crew.CrewTicker;
import com.dogpound.railmap.interior.Interior;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * A crew member you can see (batch 5 #7 #9): a driver, guard or ticket inspector in uniform. Built on the visible
 * passenger (same plain, never-saved, can't-be-hit entity and the same code-painted skins with uniform variants), but
 * moved by {@link CrewNpcs} from crew.CrewTicker's state: walking between the Crew Office and the train, sitting in the
 * cab, standing at the rear door to dispatch (whistle + hand signal), or walking the aisle and the platform checking
 * tickets. Role is in variant bits 16-17; ACTION is the arm pose (flag up, whistle, ticket check, look, tea).
 */
public class EntityCrew extends EntityPax {
    public static final int STAND = 0, WALK = 1, PIN = 2;
    private static final DataParameter<Byte> ACTION = EntityDataManager.createKey(EntityCrew.class, DataSerializers.BYTE);
    /** "car uuid|x|y|z|yaw offset|aisle half-length|sit": the client pins them to its own copy of the car, so they ride
     *  in the cab / aisle without trailing behind a fast train (the tracker only sends positions every 2 ticks) */
    private static final DataParameter<String> PINNED = EntityDataManager.createKey(EntityCrew.class, DataSerializers.STRING);

    public String staff = "";
    int mode;
    double gx, gy, gz;
    float gyaw;
    UUID car;
    double lx, ly, lz, aisle, aisleDir = 1;
    boolean sit;
    long lastOrder, checkStart, checkUntil;
    EntityPax checking;

    public EntityCrew(World w) { super(w); }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(ACTION, (byte) 0);
        dataManager.register(PINNED, "");
    }

    private String pinKey = "";
    private UUID cCar;
    private double[] cPin;
    private double cLz, cDir = 1;

    public int role() { return variant() >> 16 & 3; }
    public int action() { return dataManager.get(ACTION); }
    void setAction(int a) { if (action() != a) dataManager.set(ACTION, (byte) a); }

    @Override
    public void onUpdate() {
        if (world.isRemote) { super.onUpdate(); clientPin(); return; }
        prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
        prevRotationYaw = rotationYaw; prevRotationPitch = rotationPitch;
        if (world.getTotalWorldTime() - lastOrder > 60 || posY < -64) { setDead(); return; }      // CrewNpcs stopped giving orders: gone
        try { move(); } catch (RuntimeException | LinkageError e) { setDead(); }
    }

    private void move() {
        switch (mode) {
            case PIN -> {
                EntityRollingStock s = CrewTicker.car(world, car);
                if (s == null) { mode = STAND; unpin(); return; }
                if (aisle > 0) {                                                       // walking the aisle, end to end
                    lz += aisleDir * 0.035;
                    if (Math.abs(lz) > aisle) { lz = Math.signum(lz) * aisle; aisleDir = -aisleDir; }
                }
                double[] p = Interior.toWorld(s, lx, ly, lz);
                double[] q = Interior.toWorld(s, lx, ly, lz + (aisle > 0 ? aisleDir : 1));
                setPosition(p[0], p[1], p[2]);
                rotationYaw = PaxGraph.yaw(q[0] - p[0], q[2] - p[2]) + (aisle > 0 ? 0 : gyaw);
                setState(sit ? SIT : aisle > 0 ? TO_SPOT : WAIT);
                if (!sit && aisle <= 0) look(rotationYaw);
            }
            case WALK -> {
                double dx = gx - posX, dz = gz - posZ, d = Math.sqrt(dx * dx + dz * dz);
                double sp = com.dogpound.railmap.crew.CrewConfig.walkSpeed / 20.0;
                if (d <= Math.max(sp, 0.05)) { setPosition(gx, ground(gx, posY, gz), gz); setState(WAIT); rotationYaw = gyaw; look(gyaw); mode = STAND; return; }
                double nx = posX + dx / d * sp, nz = posZ + dz / d * sp;
                setPosition(nx, ground(nx, posY, nz), nz);
                float want = PaxGraph.yaw(dx, dz);
                rotationYaw += net.minecraft.util.math.MathHelper.clamp(net.minecraft.util.math.MathHelper.wrapDegrees(want - rotationYaw), -20, 20);
                setState(TO_SPOT);
            }
            default -> {
                setState(sit ? SIT : WAIT);
                rotationYaw += net.minecraft.util.math.MathHelper.clamp(net.minecraft.util.math.MathHelper.wrapDegrees(gyaw - rotationYaw), -15, 15);
                look(gyaw);
            }
        }
    }

    private void clientPin() {
        String k = dataManager.get(PINNED);
        if (!k.equals(pinKey)) {
            pinKey = k;
            cCar = null;
            if (!k.isEmpty()) try {
                String[] p = k.split("\\|");
                cCar = UUID.fromString(p[0]);
                cPin = new double[]{Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5])};
                cLz = cPin[2];
            } catch (RuntimeException e) { cCar = null; }
        }
        if (cCar == null) return;
        EntityRollingStock s;
        try { s = cam72cam.mod.world.World.get(world).getEntity(cCar, EntityRollingStock.class); } catch (RuntimeException | LinkageError e) { s = null; }
        if (s == null) return;
        if (cPin[4] > 0) { cLz += cDir * 0.035; if (Math.abs(cLz) > cPin[4]) { cLz = Math.signum(cLz) * cPin[4]; cDir = -cDir; } }
        double[] p = Interior.toWorld(s, cPin[0], cPin[1], cPin[4] > 0 ? cLz : cPin[2]);
        double[] q = Interior.toWorld(s, cPin[0], cPin[1], (cPin[4] > 0 ? cLz + cDir : cPin[2] + 1));
        setPosition(p[0], p[1], p[2]);
        rotationYaw = PaxGraph.yaw(q[0] - p[0], q[2] - p[2]) + (cPin[4] > 0 ? 0 : (float) cPin[3]);
    }

    /** stand on whatever is underfoot (steps up one block, down three; slabs and carpets count) */
    double ground(double x, double y, double z) {
        int by = (int) Math.floor(y + 0.01);
        for (int dy : new int[]{0, 1, -1, -2, -3}) {
            BlockPos p = new BlockPos(Math.floor(x), by + dy, Math.floor(z));
            if (!world.isBlockLoaded(p)) return y;
            IBlockState here = world.getBlockState(p), below = world.getBlockState(p.down());
            if (here.getMaterial().blocksMovement()) {
                AxisAlignedBB bb = here.getCollisionBoundingBox(world, p);
                if (bb != null && bb.maxY <= 0.55 && !world.getBlockState(p.up()).getMaterial().blocksMovement()) return p.getY() + bb.maxY;   // slab / step
                continue;
            }
            if (below.getMaterial().blocksMovement()) {
                AxisAlignedBB bb = below.getCollisionBoundingBox(world, p.down());
                return p.getY() - 1 + (bb == null ? 1 : bb.maxY);
            }
        }
        return y;
    }

    // ---- orders (CrewNpcs) -------------------------------------------------------------------------------------------
    void stand(double x, double y, double z, float yaw, boolean sitDown) {
        lastOrder = world.getTotalWorldTime();
        sit = sitDown;
        gyaw = yaw;
        double d2 = (x - posX) * (x - posX) + (z - posZ) * (z - posZ);
        if (d2 > 0.04) walkTo(x, y, z, yaw);
        else { mode = STAND; car = null; aisle = 0; unpin(); }
    }

    void walkTo(double x, double y, double z, float yaw) {
        lastOrder = world.getTotalWorldTime();
        gx = x; gy = y; gz = z; gyaw = yaw; mode = WALK; car = null; aisle = 0; sit = false;
        unpin();
    }

    void pin(UUID c, double x, double y, double z, float yawOff, boolean sitDown, double aisleHalf) {
        lastOrder = world.getTotalWorldTime();
        if (!c.equals(car)) { lz = z; aisleDir = 1; }
        if (aisleHalf <= 0) lz = z;
        car = c; lx = x; ly = y; gyaw = yawOff; sit = sitDown; aisle = aisleHalf; mode = PIN;
        String k = String.format(java.util.Locale.ROOT, "%s|%.3f|%.3f|%.3f|%.1f|%.3f", c, x, y, z, yawOff, aisleHalf);
        if (!k.equals(dataManager.get(PINNED))) dataManager.set(PINNED, k);
    }

    private void unpin() { if (!dataManager.get(PINNED).isEmpty()) dataManager.set(PINNED, ""); }

    void keep() { lastOrder = world.getTotalWorldTime(); }
}
