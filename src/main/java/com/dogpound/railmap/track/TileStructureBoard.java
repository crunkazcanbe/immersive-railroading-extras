package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Structure asset board (her ChatGPT list #15): the cast plate + condition box fixed at a bridge, viaduct, tunnel,
 * culvert, retaining wall or cutting. Wrench its two ends onto the rails (START / END) and it is an asset:
 * <ul>
 *   <li>load rating (max axle load, route availability style) - every axle that crosses is weighed; overweight axles
 *       alarm and damage the structure, and a train carrying one is REFUSED before it gets there (stopped, alarm);</li>
 *   <li>clearance gauge measured from the real blocks (lowest roof, narrowest wall over the rails) and checked against
 *       each vehicle's height and width - out-of-gauge stock is stopped short and alarmed;</li>
 *   <li>condition: corrosion / spalling / scour with time (worse for steel, worse over water), overload damage;
 *       examinations due every N days (overdue = alarm, then a 40 km/h restriction), repair by work order with
 *       materials (steel: iron, concrete: concrete / stone, masonry: bricks / stone bricks);</li>
 *   <li>speed restriction from its condition (or one set by the engineer) that drivers and driverless trains obey;</li>
 *   <li>hazards: flood water over the rails, rockfall / landslide (blocks or falling blocks on the line) - signals
 *       into the structure are held at danger until it is clear.</li>
 * </ul>
 * Inspection trains (IR stock tagged INSPECT / TRC) passing through count as an examination.
 */
public class TileStructureBoard extends TrackGear {
    public static final String[] KINDS = {"BRIDGE", "VIADUCT", "TUNNEL", "CULVERT", "RET WALL", "CUTTING"};
    public static final String[] MATS = {"STEEL", "CONCRETE", "MASONRY"};
    public static final double[] RA = {16, 20, 22.5, 25, 30, 35};
    public static final int[] SPD = {0, 120, 80, 60, 40, 20};

    private long a, b;                       // ends (rail positions)
    private double clearH, clearW;           // measured, m (0 = open / not measured)
    private float cond = 100, seenCond = 100;
    private long examAt, woAt;
    private boolean wo, heavy, oog;
    private String hazard = "", last = "", worstAxle = "";
    private int overloads, crossings;
    private long lastRefuse;
    private final Set<UUID> inSpan = new HashSet<>();
    private final Map<UUID, Double> lastD = new HashMap<>();

    public String id() { return KINDS[kind()].charAt(0) + "" + Math.floorMod(pos.getX() * 13 + pos.getZ() * 7, 1000); }
    private int kind() { return Math.min(5, Math.max(0, ctl.get("s_kind"))); }
    private int mat() { return Math.min(2, Math.max(0, ctl.get("s_mat"))); }
    private double rating() { return RA[Math.min(5, Math.max(0, ctl.get("s_ra")))]; }
    private boolean linked() { return a != 0 && b != 0; }
    private boolean tunnel() { return kind() == 2; }
    @Override public String wiredName() { return KINDS[kind()].toLowerCase(Locale.ROOT) + " " + id(); }

    private long dueTicks() { return TrackConfig.structureInspectDays * 24000L; }
    private boolean overdue() { return examAt > 0 && world.getTotalWorldTime() - examAt > dueTicks(); }
    private boolean wayOverdue() { return examAt > 0 && world.getTotalWorldTime() - examAt > dueTicks() * 3 / 2; }

    /** this structure's restriction (km/h), 0 = none */
    public int limit() {
        int lim = SPD[Math.min(5, Math.max(0, ctl.get("s_spd")))];
        int c = cond < 10 ? 10 : cond < 20 ? 20 : cond < 40 ? 40 : cond < 60 ? 60 : 0;
        if (c > 0 && (lim == 0 || c < lim)) lim = c;
        if (wayOverdue() && (lim == 0 || lim > 40)) lim = 40;
        return lim;
    }

    public String limitWhy() {
        if (cond < 60) return KINDS[kind()].toLowerCase(Locale.ROOT) + " " + id() + " in poor condition (" + Math.round(cond) + "%)";
        if (wayOverdue()) return KINDS[kind()].toLowerCase(Locale.ROOT) + " " + id() + " examination overdue";
        return KINDS[kind()].toLowerCase(Locale.ROOT) + " " + id() + " speed limit";
    }

    private AxisAlignedBB span() {
        BlockPos p = BlockPos.fromLong(a), q = BlockPos.fromLong(b);
        return new AxisAlignedBB(Math.min(p.getX(), q.getX()) - 2, Math.min(p.getY(), q.getY()) - 2, Math.min(p.getZ(), q.getZ()) - 2,
                Math.max(p.getX(), q.getX()) + 3, Math.max(p.getY(), q.getY()) + 6, Math.max(p.getZ(), q.getZ()) + 3);
    }

    public int limitAt(BlockPos piece) {
        if (!linked()) return 0;
        int l = limit();
        return l > 0 && span().grow(1).contains(new net.minecraft.util.math.Vec3d(piece.getX() + 0.5, piece.getY() + 0.5, piece.getZ() + 0.5)) ? l : 0;
    }

    @Override public String gearState() {
        if (!linked()) return "ends not set (wrench a rail onto START and END)";
        return "condition " + Math.round(cond) + "% · RA " + rating() + " t" + (limit() > 0 ? " · TSR " + limit() : "") + (hazard.isEmpty() ? "" : " · " + hazard) + (overdue() ? " · EXAM OVERDUE" : "");
    }
    @Override public int wiredLamp() { return !powered() || !hazard.isEmpty() || cond < 20 ? 3 : limit() > 0 || overdue() ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("kind", KINDS[kind()]); m.put("condition", Math.round(cond)); m.put("axleRatingT", rating()); m.put("clearanceH", clearH); m.put("hazard", hazard); m.put("overloads", overloads); m.put("crossings", crossings); }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        TileRail r = Rails.pieceAt(world, pick);
        if (r == null) return "§cPick a RAIL with the Signal Wrench (one end of the structure)";
        if (pick.distanceSq(pos) > 128 * 128) return "§cThat rail is too far from the board (128 blocks)";
        boolean end = control.equals("d_end");
        if (end) b = pick.toLong(); else a = pick.toLong();
        if (a != 0 && b == 0) b = a;
        if (b != 0 && a == 0) a = b;
        markDirty();
        measure();
        return "§a" + (end ? "END" : "START") + " of " + wiredName() + " set at " + Rails.xz(pick) + (linked() && a != b ? " - span " + Math.round(Math.sqrt(BlockPos.fromLong(a).distanceSq(BlockPos.fromLong(b)))) + " m, clearance measured" : " - now wrench the other end onto END");
    }

    /** measure the clearance gauge from the real blocks along the span */
    private void measure() {
        if (!linked()) return;
        BlockPos p = BlockPos.fromLong(a), q = BlockPos.fromLong(b);
        int n = (int) Math.max(1, Math.ceil(Math.sqrt(p.distanceSq(q))));
        double minH = 0, minW = 0;
        double dx = (q.getX() - p.getX()) / (double) n, dz = (q.getZ() - p.getZ()) / (double) n;
        double len = Math.sqrt(dx * dx + dz * dz);
        double px = len == 0 ? 1 : -dz / len, pz = len == 0 ? 0 : dx / len;     // perpendicular
        for (int i = 0; i <= n; i++) {
            int x = (int) Math.floor(p.getX() + 0.5 + dx * i), z = (int) Math.floor(p.getZ() + 0.5 + dz * i);
            int y = (int) Math.round(p.getY() + (q.getY() - p.getY()) * (i / (double) n));
            for (int h = 1; h <= 12; h++) {
                BlockPos c = new BlockPos(x, y + h, z);
                if (solid(c)) { double hh = h - 0.2; if (minH == 0 || hh < minH) minH = hh; break; }
            }
            for (int side = -1; side <= 1; side += 2) {
                for (int w = 1; w <= 7; w++) {
                    BlockPos c = new BlockPos((int) Math.floor(x + 0.5 + px * w * side), y + 2, (int) Math.floor(z + 0.5 + pz * w * side));
                    if (solid(c)) { double ww = w - 0.5; if (minW == 0 || ww < minW) minW = ww; break; }
                }
            }
        }
        clearH = minH;
        clearW = minW;
        markDirty();
    }

    private boolean solid(BlockPos c) {
        IBlockState s = world.getBlockState(c);
        return s.getMaterial().blocksMovement() && Rails.pieceAt(world, c) == null && !(world.getTileEntity(c) instanceof TrackGear) && !(world.getTileEntity(c) instanceof PlainTile);
    }

    @Override
    protected void tickGear() {
        if (age % 20 != 0) return;
        long now = world.getTotalWorldTime();
        if (examAt == 0) examAt = now;                                   // a new asset is examined as built
        if (!linked()) { live(); return; }
        if (age % 6000 == 0) measure();
        // decay (per second of a day: rate per day / 1200)
        if (TrackConfig.structureDecay) {
            double perDay = new double[]{0.35, 0.15, 0.2}[mat()];
            if (water()) perDay *= kind() <= 1 || kind() == 3 ? 1.8 : 1.2;   // scour / wet
            if (world.isRaining()) perDay *= 1.3;
            cond = (float) Math.max(0, cond - perDay / 1200.0);
        }
        hazard = hazard();
        trains(now);
        String k = "struct:" + pos.toLong();
        Alarms.state(world, k + ":hz", !hazard.isEmpty(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.CRITICAL, KINDS[kind()] + " " + id() + ": " + hazard + " - signals held", pos);
        Alarms.state(world, k + ":exam", overdue(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, KINDS[kind()] + " " + id() + ": examination overdue" + (wayOverdue() ? " - 40 km/h imposed" : ""), pos);
        Alarms.state(world, k + ":cond", cond < 40, AlarmManager.Cat.GENERAL, cond < 20 ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR, KINDS[kind()] + " " + id() + ": condition " + Math.round(cond) + "% - TSR " + limit() + " km/h, raise a work order", pos);
        live();
    }

    private boolean water() {
        BlockPos p = BlockPos.fromLong(a), q = BlockPos.fromLong(b);
        BlockPos mid = new BlockPos((p.getX() + q.getX()) / 2, Math.min(p.getY(), q.getY()), (p.getZ() + q.getZ()) / 2);
        for (int d = 1; d < 24; d++) { IBlockState s = world.getBlockState(mid.down(d)); if (s.getMaterial() == Material.WATER) return true; if (s.getMaterial().blocksMovement()) return false; }
        return false;
    }

    /** flood water or fallen rock on the line through the structure */
    private String hazard() {
        BlockPos p = BlockPos.fromLong(a), q = BlockPos.fromLong(b);
        int n = (int) Math.max(1, Math.ceil(Math.sqrt(p.distanceSq(q))));
        for (int i = 0; i <= n; i += 1) {
            int x = (int) Math.floor(p.getX() + 0.5 + (q.getX() - p.getX()) * (i / (double) n));
            int z = (int) Math.floor(p.getZ() + 0.5 + (q.getZ() - p.getZ()) * (i / (double) n));
            int y = (int) Math.round(p.getY() + (q.getY() - p.getY()) * (i / (double) n));
            BlockPos at = new BlockPos(x, y, z), up = at.up();
            if (!world.isBlockLoaded(at)) continue;
            IBlockState s0 = world.getBlockState(at), s1 = world.getBlockState(up);
            if (s0.getMaterial() == Material.WATER || s1.getMaterial() == Material.WATER) return "FLOODED at " + Rails.xz(at);
            if (s1.getMaterial().blocksMovement() && Rails.pieceAt(world, up) == null) {
                Material m = s1.getMaterial();
                if (m == Material.ROCK || m == Material.GROUND || m == Material.SAND || m == Material.CLAY || m == Material.GRASS) return (tunnel() || kind() == 5 ? "ROCKFALL" : "LANDSLIP") + " on the line at " + Rails.xz(at);
                return "OBSTRUCTION (" + s1.getBlock().getLocalizedName() + ") on the line at " + Rails.xz(at);
            }
        }
        if (!world.getEntitiesWithinAABB(EntityFallingBlock.class, span().grow(2, 8, 2)).isEmpty()) return "ROCKFALL in progress";
        return "";
    }

    /** weigh and gauge everything in and approaching the span */
    private void trains(long now) {
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        AxisAlignedBB sp = span();
        double cx = (sp.minX + sp.maxX) / 2, cz = (sp.minZ + sp.maxZ) / 2, half = Math.max(sp.maxX - sp.minX, sp.maxZ - sp.minZ) / 2;
        heavy = oog = false;
        Set<UUID> now_in = new HashSet<>();
        for (EntityRollingStock s : umc.getEntities(EntityRollingStock.class)) {
            if (s.isDead()) continue;
            Vec3d p = s.getPosition();
            double d = Math.sqrt((p.x - cx) * (p.x - cx) + (p.z - cz) * (p.z - cz));
            if (d > half + 96 || Math.abs(p.y - sp.minY) > 24) continue;
            boolean inside = sp.contains(new net.minecraft.util.math.Vec3d(p.x, p.y + 0.5, p.z));
            double axle = Rails.tonnes(s) / Rails.axles(s);
            double hgt = 4.3, wid = 3.0;
            try { hgt = s.getDefinition().getHeight(s.gauge); wid = s.getDefinition().getWidth(s.gauge); } catch (RuntimeException ignored) { }
            boolean over = axle > rating() + 0.05;
            boolean outGauge = clearH > 0 && hgt > clearH || clearW > 0 && wid / 2 > clearW;
            if (inside) {
                now_in.add(s.getUUID());
                if (!inSpan.contains(s.getUUID())) {                     // a vehicle entering: weigh it
                    crossings++;
                    last = String.format(Locale.ROOT, "%.1fT AXLE %s", axle, over ? "OVER" : "OK");
                    if (over) {
                        overloads++;
                        worstAxle = String.format(Locale.ROOT, "%.1f t", axle);
                        if (TrackConfig.structureDecay) cond = (float) Math.max(0, cond - (axle / rating() - 1) * 6);
                        AlarmManager.get(world).raise(world, "struct:" + pos.toLong() + ":ow", AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, "OVERWEIGHT on " + KINDS[kind()] + " " + id() + ": " + Rails.name(s) + String.format(Locale.ROOT, " %.1f t axle > RA %.1f t", axle, rating()), pos, false);
                        Recorder.event(world, "OVERWEIGHT " + Rails.name(s) + String.format(Locale.ROOT, " %.1f t/axle on ", axle) + KINDS[kind()] + " " + id());
                    }
                    if (outGauge) {
                        AlarmManager.get(world).raise(world, "struct:" + pos.toLong() + ":oog", AlarmManager.Cat.INCIDENT, AlarmManager.Sev.CRITICAL, "OUT OF GAUGE in " + KINDS[kind()] + " " + id() + ": " + Rails.name(s) + String.format(Locale.ROOT, " %.1f x %.1f m vs %.1f x %.1f m", hgt, wid, clearH, clearW * 2), pos, false);
                        if (TrackConfig.structureDecay) cond = Math.max(0, cond - 3);
                    }
                    if (s.tag != null && (s.tag.toUpperCase(Locale.ROOT).contains("INSPECT") || s.tag.toUpperCase(Locale.ROOT).contains("TRC"))) exam("inspection train " + Rails.name(s));
                }
            } else if ((over || outGauge) && s instanceof EntityMoveableRollingStock m) {
                Double was = lastD.put(s.getUUID(), d);
                boolean approaching = was != null && d < was - 0.05 && d < half + 80;
                if (approaching) {
                    if (over) heavy = true; else oog = true;
                    refuse(s, over ? String.format(Locale.ROOT, "%.1f t axle > RA %.1f t", axle, rating()) : String.format(Locale.ROOT, "%.1f m high / %.1f m wide - clearance %.1f x %.1f m", hgt, wid, clearH, clearW * 2));
                }
            }
        }
        inSpan.clear();
        inSpan.addAll(now_in);
        if (lastD.size() > 200) lastD.clear();
    }

    /** a train that may not cross: ROUTE NOT APPROVED - cut its power and brake it, tell the crew and the signaller */
    private void refuse(EntityRollingStock car, String why) {
        String key = "struct:" + pos.toLong() + ":refuse";
        boolean announce = world.getTotalWorldTime() - lastRefuse > 600;
        if (announce) { lastRefuse = world.getTotalWorldTime(); AlarmManager.get(world).raise(world, key, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, "ROUTE NOT APPROVED over " + KINDS[kind()] + " " + id() + ": " + Rails.name(car) + " " + why, pos, false); }
        if (!TrackConfig.structuresStopTrains) return;
        List<EntityCoupleableRollingStock> train = car instanceof EntityCoupleableRollingStock c ? c.getTrain() : Collections.emptyList();
        boolean told = false;
        for (EntityCoupleableRollingStock s : train) {
            if (!(s instanceof Locomotive l)) continue;
            if (l.getThrottle() > 0) l.setThrottle(0);
            l.setTrainBrake(1f);
            if (!told && announce) { TrackCondition.tell(world, l.getPosition(), "§c✖ ROUTE NOT APPROVED over " + KINDS[kind()].toLowerCase(Locale.ROOT) + " " + id() + " - " + Rails.name(car) + ": " + why + ". Stop and take another route."); told = true; }
        }
    }

    private void exam(String by) {
        examAt = world.getTotalWorldTime();
        seenCond = cond;
        Recorder.event(world, "STRUCTURE EXAM " + KINDS[kind()] + " " + id() + " by " + by + ": condition " + Math.round(cond) + "%" + (hazard.isEmpty() ? "" : ", " + hazard));
        markDirty();
    }

    private void live() {
        long now = world.getTotalWorldTime();
        int lim = limit();
        put("ok", linked() && lim == 0 && hazard.isEmpty() && cond >= 60);
        put("restr", lim > 0);
        put("closed", !hazard.isEmpty() || cond < 10);
        put("overdue", overdue());
        put("heavy", heavy);
        put("oog", oog);
        put("flood", hazard.startsWith("FLOOD"));
        put("rock", !hazard.isEmpty() && !hazard.startsWith("FLOOD"));
        put("insp", !overdue());
        put("wo", wo);
        put("pwr", powered());
        put("g_cond", Math.round(cond));
        text("name", KINDS[kind()] + " " + id() + " " + MATS[mat()]);
        text("cond", "COND " + Math.round(cond) + "%");
        text("ra", String.format(Locale.ROOT, "RA %.1f T", rating()));
        text("clr", clearH <= 0 ? "CLR OPEN" : String.format(Locale.ROOT, "CLR %.1fX%.1f M", clearH, clearW * 2));
        long left = examAt == 0 ? dueTicks() : dueTicks() - (now - examAt);
        text("due", left >= 0 ? "EXAM " + (left / 24000) + " D" : "OVERDUE " + (-left / 24000) + " D");
        text("spd", lim > 0 ? "TSR " + lim : "LINE SPEED");
        text("last", last.isEmpty() ? "NO TRAFFIC" : last);
        text("d_start", a == 0 ? "START --" : "S " + Rails.xz(BlockPos.fromLong(a)));
        text("d_end", b == 0 ? "END --" : "E " + Rails.xz(BlockPos.fromLong(b)));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "p_meas": measure(); return "§eClearance measured: " + (clearH <= 0 ? "open above" : String.format(Locale.ROOT, "%.1f m high", clearH)) + (clearW <= 0 ? ", open sides" : String.format(Locale.ROOT, ", %.1f m wide", clearW * 2));
            case "p_insp": {
                if (!linked()) return "§7Set the structure's ends first (Signal Wrench onto START / END)";
                if (who != null && who.getDistanceSq(pos) > 64) return "§7Stand at the structure to examine it";
                exam(who(who));
                Material defect = cond < 40 ? Material.ROCK : null;
                return "§aExamined " + wiredName() + ": condition " + Math.round(cond) + "% (" + (cond >= 80 ? "good" : cond >= 60 ? "fair - monitor" : cond >= 40 ? "poor - repair soon" : "SEVERE - restriction, repair now") + ")"
                        + (defect != null ? " §c" + new String[]{"heavy corrosion, section loss at the bearings", "spalling, exposed rebar", "missing mortar, bulging, loose stones"}[mat()] : "") + (hazard.isEmpty() ? "" : " §c" + hazard);
            }
            case "p_wo": {
                if (wo) return "§7Work order already open (since " + (world.getTotalWorldTime() - woAt) / 1200 + " min) - REPAIR with materials in hand";
                wo = true;
                woAt = world.getTotalWorldTime();
                AlarmManager.get(world).raise(world, "struct:" + pos.toLong() + ":wo", AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "WORK ORDER: repair " + wiredName() + " (condition " + Math.round(cond) + "%)", pos, true);
                Recorder.event(world, "WORK ORDER raised for " + wiredName() + " by " + who(who));
                return "§eWork order raised - repair needs " + need();
            }
            case "p_rep": {
                if (!wo) return "§7Raise a work order first (WORK ORDER)";
                if (who == null) return "§7Repairs are done on site";
                if (!take(who)) return "§cYou need " + need() + " in your inventory";
                cond = Math.min(100, cond + 20);
                if (cond >= 90) { wo = false; AlarmManager.get(world).clear("struct:" + pos.toLong() + ":wo"); Recorder.event(world, "WORK ORDER closed: " + wiredName() + " repaired to " + Math.round(cond) + "%"); }
                markDirty();
                return "§aRepaired: condition " + Math.round(cond) + "%" + (wo ? " - more to do" : " - work order closed");
            }
            default: return null;
        }
    }

    private String need() { return new String[]{"4 iron ingots (new steelwork + paint)", "4 concrete or stone (patch repairs)", "4 bricks or stone bricks (repointing, rebuilding)"}[mat()]; }

    private boolean take(EntityPlayer p) {
        if (p.capabilities.isCreativeMode) return true;
        int got = 0;
        for (ItemStack st : p.inventory.mainInventory) if (fits(st)) got += st.getCount();
        if (got < 4) return false;
        int left = 4;
        for (ItemStack st : p.inventory.mainInventory) if (left > 0 && fits(st)) { int t = Math.min(left, st.getCount()); st.shrink(t); left -= t; }
        return true;
    }

    private boolean fits(ItemStack st) {
        if (st.isEmpty()) return false;
        Item i = st.getItem();
        return switch (mat()) {
            case 0 -> i == Items.IRON_INGOT;
            case 1 -> i == Item.getItemFromBlock(Blocks.CONCRETE) || i == Item.getItemFromBlock(Blocks.STONE);
            default -> i == Item.getItemFromBlock(Blocks.BRICK_BLOCK) || i == Item.getItemFromBlock(Blocks.STONEBRICK) || i == Items.BRICK;
        };
    }

    /** SafetyHolds: a hazard (or a closed structure) holds every signal into it */
    void holds(Map<Long, String> hold, List<Object[]> zones) {
        if (!linked() || hazard.isEmpty() && cond >= 10) return;
        AxisAlignedBB sp = span();
        double r = Math.max(sp.maxX - sp.minX, sp.maxZ - sp.minZ) / 2 + 8;
        zones.add(new Object[]{(sp.minX + sp.maxX) / 2, (sp.minZ + sp.maxZ) / 2, r, KINDS[kind()].toLowerCase(Locale.ROOT) + " " + id() + ": " + (hazard.isEmpty() ? "structure closed (condition " + Math.round(cond) + "%)" : hazard)});
    }

    @Override
    protected String[] assetRow() {
        return new String[]{"ST", id(), KINDS[kind()], MATS[mat()], Math.round(cond) + "%", String.format(Locale.ROOT, "%.1f", rating()), clearH <= 0 ? "open" : String.format(Locale.ROOT, "%.1fx%.1f", clearH, clearW * 2),
                limit() > 0 ? String.valueOf(limit()) : "-", overdue() ? "OVERDUE" : "in date", hazard.isEmpty() ? "-" : hazard, overloads + "/" + crossings, wo ? "OPEN" : "-", xyz()};
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setLong("a", a); t.setLong("b", b); t.setDouble("cH", clearH); t.setDouble("cW", clearW); t.setFloat("cond", cond); t.setFloat("seen", seenCond);
        t.setLong("exam", examAt); t.setLong("woAt", woAt); t.setBoolean("wo", wo); t.setInteger("ovl", overloads); t.setInteger("xs", crossings); t.setString("last", last);
    }

    @Override
    protected void load(NBTTagCompound t) {
        a = t.getLong("a"); b = t.getLong("b"); clearH = t.getDouble("cH"); clearW = t.getDouble("cW"); cond = t.hasKey("cond") ? t.getFloat("cond") : 100; seenCond = t.getFloat("seen");
        examAt = t.getLong("exam"); woAt = t.getLong("woAt"); wo = t.getBoolean("wo"); overloads = t.getInteger("ovl"); crossings = t.getInteger("xs"); last = t.getString("last");
    }
}
