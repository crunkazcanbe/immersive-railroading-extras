package com.dogpound.railmap.server;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.fluid.FluidStack;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.block.TilePowerPost;
import com.dogpound.railmap.signal.BlockCatenary;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Locale;

/**
 * Electrification + battery trains (her railway list §10, §11). Once a second, for every
 * electric locomotive (IR diesel-type locos whose name says electric, or switched with
 * /irextras electric):
 * <ul>
 *   <li>under live contact wire / beside a live third rail: its fuel tank is kept topped up
 *       (it runs on the wire), the substation is billed FE if power is required, and the battery charges;</li>
 *   <li>off the wire: the battery stands in for fuel until it is flat.</li>
 * </ul>
 * A substation's breaker, redstone or a storm trip kills its whole section: an outage.
 */
public final class Electric {
    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    private static final int TOPUP_MB = 1000;

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR || !RailMapConfig.electrification) return;
        if (e.world.getTotalWorldTime() % 20 != 7) return;
        try {
            tick(e.world);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[IR Extras] electric tick failed: {}", ex.toString());
        }
    }

    private static void tick(World mc) {
        cam72cam.mod.world.World world = cam72cam.mod.world.World.get(mc);
        RailProps props = RailProps.get(mc);
        int dim = mc.provider.getDimension();
        for (LocomotiveDiesel loco : world.getEntities(LocomotiveDiesel.class)) {
            if (loco.isDead()) continue;
            RailProps.Props pr = props.of(loco.getUUID());
            if (!isElectric(loco, pr)) { pr.source = 0; continue; }
            Vec3d p = loco.getPosition();
            BlockPos at = new BlockPos(p.x, p.y, p.z);
            boolean onWire = wired(mc, at);
            TilePowerPost sub = onWire ? liveSubstation(dim, at) : null;
            float throttle = loco.getThrottle();
            int want = (int) (RailMapConfig.fePerSecond * (0.15 + throttle));
            boolean beam = false;
            for (int dy = -2; dy <= 0 && !beam; dy++) beam = prideBeam(mc, at.up(dy));
            long grid = onWire && sub == null ? com.dogpound.railmap.grid.GridTicker.supply(mc, at, !beam && overhead(mc, at), beam, want) : -1;
            if ((sub != null && sub.draw(want)) || grid >= want) {
                topUp(loco, TOPUP_MB);
                pr.battery = Math.min(1f, pr.battery + (float) (RailMapConfig.chargePerSecond / 100.0));
                pr.source = 1;
            } else if (pr.battery > 0 && RailMapConfig.batteryFuelMb > 0) {
                int added = topUp(loco, 100);
                pr.battery = Math.max(0f, pr.battery - added / (float) RailMapConfig.batteryFuelMb);
                pr.source = pr.battery > 0 ? (byte) 2 : (byte) 4;
            } else {
                pr.source = 4;
            }
        }
        props.markDirty();
    }

    /** Electric by override, else by name. */
    public static boolean isElectric(EntityRollingStock s, RailProps.Props pr) {
        if (pr.electric == RailProps.ON) return true;
        if (pr.electric == RailProps.OFF) return false;
        String n = ((s.getDefinition() == null ? "" : s.getDefinition().name()) + " " + s.getDefinitionID() + " " + (s.tag == null ? "" : s.tag) + " ").toLowerCase(Locale.ROOT);
        for (String k : RailMapConfig.electricNames) if (!k.isEmpty() && n.contains(k)) return true;
        return false;
    }

    /** Contact wire / portal overhead (up to 7 above), third rail (wire at track level within 2 blocks),
     *  or riding a Pride monorail beam / maglev guideway (the beam itself is the power rail). */
    private static boolean wired(World mc, BlockPos at) {
        for (int dy = -2; dy <= 0; dy++) if (prideBeam(mc, at.up(dy)) || prideThirdRail(mc, at.up(dy))) return true;
        for (int dy = -1; dy <= 7; dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    Block b = mc.getBlockState(at.add(dx, dy, dz)).getBlock();
                    if (b instanceof BlockCatenary c && c.kind() != BlockCatenary.Kind.MAST) return true;
                }
        return false;
    }

    /** contact wire overhead (not just a third rail at track level) */
    private static boolean overhead(World mc, BlockPos at) {
        for (int dy = 2; dy <= 7; dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (mc.getBlockState(at.add(dx, dy, dz)).getBlock() instanceof BlockCatenary c && c.kind() == BlockCatenary.Kind.WIRE) return true;
        return false;
    }

    /** IR track with our electrified third rail (subway / metro) */
    public static boolean prideThirdRail(World mc, BlockPos p) {
        if (mc.getTileEntity(p) == null) return false;
        try {
            cam72cam.immersiverailroading.tile.TileRailBase rb = cam72cam.mod.world.World.get(mc)
                    .getBlockEntity(new cam72cam.mod.math.Vec3i(p.getX(), p.getY(), p.getZ()), cam72cam.immersiverailroading.tile.TileRailBase.class);
            cam72cam.immersiverailroading.tile.TileRail r = rb == null ? null : rb.getParentTile();
            return r != null && r.info != null && r.info.settings.track.contains("/pride_thirdrail");
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** IR track whose style is one of ours (immersiverailroading:track/pride_*.json) */
    public static boolean prideBeam(World mc, BlockPos p) {
        if (mc.getTileEntity(p) == null) return false;
        try {
            cam72cam.immersiverailroading.tile.TileRailBase rb = cam72cam.mod.world.World.get(mc)
                    .getBlockEntity(new cam72cam.mod.math.Vec3i(p.getX(), p.getY(), p.getZ()), cam72cam.immersiverailroading.tile.TileRailBase.class);
            cam72cam.immersiverailroading.tile.TileRail r = rb == null ? null : rb.getParentTile();
            return r != null && r.info != null && r.info.settings.track.contains("/pride_") && !r.info.settings.track.contains("/pride_thirdrail");
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static TilePowerPost liveSubstation(int dim, BlockPos at) {
        double best = Double.MAX_VALUE;
        TilePowerPost found = null;
        for (TilePowerPost t : TilePowerPost.all(dim)) {
            if (t.kind() != TilePowerPost.Kind.SUBSTATION || !t.live()) continue;
            BlockPos s = t.where();
            double dx = s.getX() - at.getX(), dz = s.getZ() - at.getZ(), d = dx * dx + dz * dz;
            if (d <= (double) t.range() * t.range() && d <= best) { best = d; found = t; }
        }
        return found;
    }

    /** Add up to {@code mb} of diesel-type fuel to the loco's tank. Returns how much went in. */
    private static int topUp(LocomotiveDiesel loco, int mb) {
        FluidStack in = loco.theTank.getContents();
        Fluid fuel = in != null && in.getFluid() != null && in.getAmount() > 0 ? in.getFluid() : null;
        if (fuel == null) {
            for (String name : Config.ConfigBalance.dieselFuels.keySet()) {
                Fluid f = Fluid.getFluid(name);
                if (f != null && loco.theTank.allows(f)) { fuel = f; break; }
            }
        }
        if (fuel == null) return 0;
        return loco.theTank.fill(new FluidStack(fuel, mb), false);
    }

    /** Charging Station: charge parked electric locos within 6 blocks. */
    public static void chargeNear(World mc, BlockPos at, TilePowerPost charger) {
        if (!IR || !RailMapConfig.electrification) return;
        cam72cam.mod.world.World world = cam72cam.mod.world.World.get(mc);
        RailProps props = RailProps.get(mc);
        for (LocomotiveDiesel loco : world.getEntities(LocomotiveDiesel.class)) {
            Vec3d p = loco.getPosition();
            if (loco.isDead() || at.distanceSqToCenter(p.x, p.y, p.z) > (double) charger.reach() * charger.reach()) continue;
            if (loco.getCurrentSpeed() != null && Math.abs(loco.getCurrentSpeed().metric()) > Math.max(0.5, charger.settings().num("parked"))) continue;
            RailProps.Props pr = props.of(loco.getUUID());
            if (!isElectric(loco, pr) || pr.battery >= 1f || !charger.live()) continue;
            if (!charger.draw(RailMapConfig.fePerSecond)) continue;
            pr.battery = Math.min(1f, pr.battery + (float) (RailMapConfig.chargePerSecond * charger.rateMul() / 100.0));
            pr.source = 3;
            props.markDirty();
        }
    }

    /** Board readout: "" for non-electric, else e.g. "⚡ wire · 🔋 87%". */
    public static String status(World mc, EntityRollingStock s) {
        RailProps.Props pr = RailProps.get(mc).peek(s.getUUID());
        if (pr == null || !isElectric(s, pr)) return "";
        String src = switch (pr.source) {
            case 1 -> "wire";
            case 2 -> "battery";
            case 3 -> "charging";
            case 4 -> "§cNO POWER";
            default -> "idle";
        };
        return src + " · battery " + Math.round(pr.battery * 100) + "%";
    }

    /** Command: set electric on/off/auto for stock within reach. */
    public static int setNear(World mc, BlockPos at, int reach, byte mode) {
        if (!IR) return 0;
        cam72cam.mod.world.World world = cam72cam.mod.world.World.get(mc);
        RailProps props = RailProps.get(mc);
        int n = 0;
        for (LocomotiveDiesel loco : world.getEntities(LocomotiveDiesel.class)) {
            Vec3d p = loco.getPosition();
            if (loco.isDead() || at.distanceSqToCenter(p.x, p.y, p.z) > (double) reach * reach) continue;
            props.of(loco.getUUID()).electric = mode;
            n++;
        }
        props.markDirty();
        return n;
    }
}
