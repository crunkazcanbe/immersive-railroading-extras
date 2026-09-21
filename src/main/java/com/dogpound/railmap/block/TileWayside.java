package com.dogpound.railmap.block;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The two measuring devices that sit in the rail: a <b>weighbridge</b> and an <b>AEI reader</b>.
 * <p>
 * Both work the same way — watch the rail beside them, note every vehicle from the moment the
 * head end arrives until the last one clears, then file a report. What they record differs:
 * the scale weighs the train, the reader identifies it.
 * <p>
 * Positions only refresh a few times a second, so a train at line speed moves several blocks
 * between looks. Like the defect detector, this measures against the <i>stretch</i> a vehicle
 * covered since the last sample rather than where it happens to be right now — otherwise a fast
 * train steps straight over the sensor without ever being seen.
 */
public class TileWayside extends TileEntity implements ITickable, com.dogpound.railmap.signal.IScalable {

    /** how close to the rail the device reads, in blocks */
    private static final double RANGE = 2.5D;
    /** ticks of empty rail before the train counts as clear and the report is filed */
    private static final int CLEAR_TICKS = 40;
    /** one tonne of IR weight — IR reports in kg */
    private static final double KG_PER_TONNE = 1000.0D;

    public enum Kind { SCALE, AEI }

    private final Kind kind;
    /** dialled with the Signal Wrench so the device matches whatever gauge she runs */
    private float scale = 1f;
    private int ticks;
    private int clearFor;
    private boolean passing;
    private int comparator;

    private final Set<Integer> counted = new HashSet<>();
    private final Map<Integer, double[]> lastSeen = new HashMap<>();
    private final List<String> manifest = new ArrayList<>();

    private double totalKg;
    private double heaviestKg;
    private String heaviest = "";
    private int overloaded;
    private int units;
    private double topKmh;
    private String heading = "";
    private String report = "";

    public TileWayside() {
        this(Kind.SCALE);
    }

    public TileWayside(Kind kind) {
        this.kind = kind;
    }

    public static class Scale extends TileWayside {
        public Scale() {
            super(Kind.SCALE);
        }
    }

    public static class Aei extends TileWayside {
        public Aei() {
            super(Kind.AEI);
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        if (++ticks % 10 != 0) {
            return;
        }
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
        if (w == null) {
            return;
        }
        double cx = pos.getX() + 0.5D, cz = pos.getZ() + 0.5D;
        boolean present = false;
        Map<Integer, double[]> now = new HashMap<>();

        for (EntityRollingStock stock : w.getEntities(EntityRollingStock.class)) {
            if (stock.isDead()) {
                continue;
            }
            cam72cam.mod.math.Vec3d p = stock.getPosition();
            if (Math.abs(p.y - pos.getY()) > 4) {
                continue;
            }
            int id = stock.getUUID().hashCode();
            now.put(id, new double[]{p.x, p.z});
            double[] was = lastSeen.getOrDefault(id, new double[]{p.x, p.z});
            if (distToSegment(cx, cz, was[0], was[1], p.x, p.z) > RANGE) {
                continue;
            }
            present = true;
            if (counted.add(id)) {
                record(stock);
            }
        }
        lastSeen.clear();
        lastSeen.putAll(now);

        if (present) {
            passing = true;
            clearFor = 0;
        } else if (passing) {
            clearFor += 10;
            if (clearFor >= CLEAR_TICKS) {
                file();
            }
        }
    }

    /** Note one vehicle as it goes over. */
    private void record(EntityRollingStock stock) {
        units++;
        double kg = stock.getWeight();
        double maxKg = stock.getMaxWeight();
        totalKg += kg;
        if (kg > heaviestKg) {
            heaviestKg = kg;
            heaviest = nameOf(stock);
        }
        if (maxKg > 0 && kg > maxKg) {
            overloaded++;
        }
        if (stock instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null) {
            double kmh = Math.abs(m.getCurrentSpeed().metric());
            if (kmh > topKmh) {
                topKmh = kmh;
                heading = m.getCurrentSpeed().metric() >= 0 ? "forward" : "reverse";
            }
        }
        if (kind == Kind.AEI && manifest.size() < 40) {
            manifest.add(nameOf(stock) + " · " + typeOf(stock) + " · " + loadOf(stock));
        }
    }

    /** The train has cleared — publish what was measured and reset for the next one. */
    private void file() {
        if (units > 0) {
            report = kind == Kind.SCALE ? scaleReport() : aeiReport();
            comparator = Math.max(1, Math.min(15, units));
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        }
        passing = false;
        clearFor = 0;
        counted.clear();
        manifest.clear();
        units = 0;
        totalKg = 0;
        heaviestKg = 0;
        overloaded = 0;
        topKmh = 0;
        heaviest = "";
        heading = "";
        markDirty();
    }

    private String scaleReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Weighed ").append(units).append(units == 1 ? " vehicle · " : " vehicles · ");
        sb.append(tonnes(totalKg)).append(" t total");
        if (!heaviest.isEmpty()) {
            sb.append(" · heaviest ").append(heaviest).append(' ').append(tonnes(heaviestKg)).append(" t");
        }
        if (units > 0) {
            sb.append(" · avg ").append(tonnes(totalKg / units)).append(" t");
        }
        sb.append(overloaded > 0 ? " · " + overloaded + " OVERLOADED" : " · no overloads");
        return sb.toString();
    }

    private String aeiReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(units).append(units == 1 ? " unit" : " units");
        if (topKmh > 0) {
            sb.append(" · ").append(Math.round(topKmh)).append(" km/h ").append(heading);
        }
        sb.append(" · ").append(tonnes(totalKg)).append(" t");
        return sb.toString();
    }

    /** The full consist list, newest pass first — what the reader actually saw. */
    public List<String> manifest() {
        return new ArrayList<>(manifest);
    }

    public String statusLine() {
        String label = kind == Kind.SCALE ? "Track scale" : "AEI reader";
        if (report.isEmpty()) {
            return label + " — nothing weighed yet. Run a train over it.";
        }
        return label + " — " + report;
    }

    public int redstoneOutput() {
        return comparator;
    }

    @Override
    public float scale() {
        return scale;
    }

    @Override
    public void setScale(float s) {
        scale = com.dogpound.railmap.signal.IScalable.clamp(s);
        markDirty();
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net,
                             net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        double r = 2.0D * Math.max(1f, scale);
        return new net.minecraft.util.math.AxisAlignedBB(pos).grow(r, r, r);
    }

    private static String nameOf(EntityRollingStock stock) {
        EntityRollingStockDefinition def = stock.getDefinition();
        return def == null ? stock.getDefinitionID() : def.name();
    }

    private static String typeOf(EntityRollingStock stock) {
        if (stock instanceof Locomotive) {
            return "loco";
        }
        if (stock instanceof FreightTank) {
            return "tank";
        }
        if (stock instanceof Freight) {
            return "freight";
        }
        return "stock";
    }

    private static String loadOf(EntityRollingStock stock) {
        if (stock instanceof FreightTank ft) {
            return ft.getPercentLiquidFull() + "% full";
        }
        if (stock instanceof Freight f) {
            int pct = f.getPercentCargoFull();
            return pct <= 0 ? "empty" : pct + "% loaded";
        }
        return "—";
    }

    private static String tonnes(double kg) {
        return String.format("%.1f", kg / KG_PER_TONNE);
    }

    private static double distToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double len2 = vx * vx + vz * vz;
        double k = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / len2));
        double dx = px - (ax + k * vx), dz = pz - (az + k * vz);
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setString("report", report);
        t.setInteger("comparator", comparator);
        if (scale != 1f) t.setFloat("scale", scale);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        report = t.getString("report");
        comparator = t.getInteger("comparator");
        scale = t.hasKey("scale") ? com.dogpound.railmap.signal.IScalable.clamp(t.getFloat("scale")) : 1f;
    }
}
