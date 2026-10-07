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
public class TileWayside extends TileEntity implements ITickable, com.dogpound.railmap.signal.IScalable, com.dogpound.railmap.settings.ISettingsHolder {

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
        if (pulseLeft > 0 && --pulseLeft == 0) {
            comparator = 0;
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
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
            if (Math.abs(p.y - pos.getY()) > cfg.num("height")) {
                continue;
            }
            int id = stock.getUUID().hashCode();
            now.put(id, new double[]{p.x, p.z});
            double[] was = lastSeen.getOrDefault(id, new double[]{p.x, p.z});
            if (distToSegment(cx, cz, was[0], was[1], p.x, p.z) > cfg.num("range") / 2.0D) {
                continue;
            }
            present = true;
            if (cfg.bool("skipLocos") && stock instanceof Locomotive) {
                continue;
            }
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
            if (clearFor >= cfg.num("clearSec") * 20) {
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
            comparator = outputFor();
            if (cfg.text("output").startsWith("Pulse")) pulseLeft = cfg.num("pulse");
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
            trains++;
            history.add(0, report);
            while (history.size() > 6) history.remove(history.size() - 1);
            if (cfg.bool("announce") || (cfg.bool("alarmChat") && overloaded > 0)) announce();
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
        sb.append(tonnes(totalKg)).append(' ').append(unit()).append(" total");
        if (!heaviest.isEmpty()) {
            sb.append(" · heaviest ").append(heaviest).append(' ').append(tonnes(heaviestKg)).append(' ').append(unit());
        }
        if (units > 0) {
            sb.append(" · avg ").append(tonnes(totalKg / units)).append(' ').append(unit());
        }
        sb.append(overloaded > 0 ? " · " + overloaded + " OVERLOADED" : " · no overloads");
        return sb.toString();
    }

    private String aeiReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(units).append(units == 1 ? " unit" : " units");
        if (topKmh > 0) {
            boolean mph = cfg.text("speed").equals("mph");
            sb.append(" · ").append(Math.round(mph ? topKmh / 1.609344D : topKmh)).append(mph ? " mph " : " km/h ").append(heading);
        }
        sb.append(" · ").append(tonnes(totalKg)).append(' ').append(unit());
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

    private String tonnes(double kg) {
        switch (cfg.text("weight")) {
            case "short tons": return String.format("%.1f", kg / 907.18474D);
            case "kg": return String.format("%.0f", kg);
            default: return String.format("%.1f", kg / KG_PER_TONNE);
        }
    }

    private String unit() {
        switch (cfg.text("weight")) {
            case "short tons": return "tn";
            case "kg": return "kg";
            default: return "t";
        }
    }

    /** what the comparator shows after a train, per the console */
    private int outputFor() {
        String how = cfg.text("output");
        if (how.startsWith("Overload")) return overloaded > 0 ? 15 : 0;
        if (how.startsWith("Weight")) return Math.max(1, Math.min(15, (int) Math.ceil(totalKg / KG_PER_TONNE / Math.max(1, cfg.num("tPerLevel")))));
        if (how.startsWith("Speed")) return Math.max(1, Math.min(15, (int) Math.ceil(topKmh / 10D)));
        if (how.startsWith("Pulse")) return 15;
        return Math.max(1, Math.min(15, units));
    }

    private void announce() {
        String label = cfg.text("name").isEmpty() ? (kind == Kind.SCALE ? "Track scale" : "AEI reader") : cfg.text("name");
        String msg = "\u00a7d[" + label + "]\u00a7r " + report;
        double r = cfg.num("radius");
        for (net.minecraft.entity.player.EntityPlayer p : world.playerEntities) {
            if (p.getDistanceSq(pos) <= r * r) p.sendMessage(new net.minecraft.util.text.TextComponentString(msg));
        }
    }

    // ---- Settings Console (sneak-right-click with the Signal Wrench) ----
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private final List<String> history = new ArrayList<>();
    private int pulseLeft;
    private int trains;

    @Override
    public String settingsTitle() {
        return kind == Kind.SCALE ? "Track Scale" : "AEI Reader";
    }

    @Override
    public List<com.dogpound.railmap.settings.Setting> settingDefs() {
        List<com.dogpound.railmap.settings.Setting> l = new ArrayList<>();
        l.add(com.dogpound.railmap.settings.Setting.text("Device", "name", "Name", "Shown in reports and chat, e.g. 'Hump yard scale'", "", 32));
        l.add(com.dogpound.railmap.settings.Setting.num("Device", "range", "Reads within", "How far from the rail it notices a vehicle", 5, 1, 12, 1, "half-blocks"));
        l.add(com.dogpound.railmap.settings.Setting.num("Device", "height", "Height tolerance", "Vehicles this far above or below still count (bridges, ramps)", 4, 1, 12, 1, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.num("Device", "clearSec", "Train has cleared after", "Empty rail for this long files the report", 2, 1, 15, 1, "s"));
        l.add(com.dogpound.railmap.settings.Setting.bool("Device", "skipLocos", "Ignore locomotives", "Only weigh / read the cars behind", false));
        l.add(com.dogpound.railmap.settings.Setting.choice("Units", "weight", "Weight", "", "tonnes", "tonnes", "short tons", "kg"));
        l.add(com.dogpound.railmap.settings.Setting.choice("Units", "speed", "Speed", "", "km/h", "km/h", "mph"));
        l.add(com.dogpound.railmap.settings.Setting.choice("Redstone", "output", "Comparator output", "What the device tells your redstone after each train",
                "Vehicle count", "Vehicle count", "Overload alarm (15 if any)", "Weight (1 level per N tonnes)", "Speed (1 level per 10 km/h)", "Pulse when a train has passed"));
        l.add(com.dogpound.railmap.settings.Setting.num("Redstone", "tPerLevel", "Tonnes per level", "For the Weight output", 100, 10, 1000, 10, "t"));
        l.add(com.dogpound.railmap.settings.Setting.num("Redstone", "pulse", "Pulse length", "For the Pulse output", 10, 2, 100, 2, "ticks"));
        l.add(com.dogpound.railmap.settings.Setting.bool("Chat", "announce", "Announce every train", "Nearby players get the report in chat", false));
        l.add(com.dogpound.railmap.settings.Setting.bool("Chat", "alarmChat", "Announce overloads", "Chat warning when a car is over its limit", true));
        l.add(com.dogpound.railmap.settings.Setting.num("Chat", "radius", "Announce radius", "", 32, 4, 256, 4, "blocks"));
        if (world != null) {
            l.add(com.dogpound.railmap.settings.Setting.info("Reports", "Trains seen", String.valueOf(trains)));
            l.add(com.dogpound.railmap.settings.Setting.info("Reports", "Size", Math.round(scale * 100) + "% (Signal Wrench scale screen)"));
            for (int i = 0; i < history.size(); i++) l.add(com.dogpound.railmap.settings.Setting.info("Reports", i == 0 ? "Last" : "#" + (i + 1), history.get(i)));
        }
        return l;
    }

    @Override
    public com.dogpound.railmap.settings.SettingsStore settings() {
        return cfg;
    }

    @Override
    public void onSettingsChanged(String key) {
        markDirty();
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
        t.setInteger("trains", trains);
        net.minecraft.nbt.NBTTagList h = new net.minecraft.nbt.NBTTagList();
        for (String r : history) h.appendTag(new net.minecraft.nbt.NBTTagString(r));
        t.setTag("history", h);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        report = t.getString("report");
        comparator = t.getInteger("comparator");
        scale = t.hasKey("scale") ? com.dogpound.railmap.signal.IScalable.clamp(t.getFloat("scale")) : 1f;
        trains = t.getInteger("trains");
        history.clear();
        net.minecraft.nbt.NBTTagList h = t.getTagList("history", 8);
        for (int i = 0; i < h.tagCount(); i++) history.add(h.getStringTagAt(i));
        cfg.read(t);
    }
}
