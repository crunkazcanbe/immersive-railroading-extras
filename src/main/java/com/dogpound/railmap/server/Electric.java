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

    /** W per FE/s: the grid's 1 FE/t = 2 kW, so 1 FE/s = 100 W */
    private static final double W_PER_FE_S = com.dogpound.railmap.grid.Elec.W_PER_FE_T / 20.0;

    private static void tick(World mc) {
        cam72cam.mod.world.World world = cam72cam.mod.world.World.get(mc);
        RailProps props = RailProps.get(mc);
        int dim = mc.provider.getDimension();
        long now = mc.getTotalWorldTime();
        com.dogpound.railmap.ohle.Ohle.tick(mc);                    // OHLE protection: trips, auto-reclose, fault isolation (#31)
        com.dogpound.railmap.grid.GridTicker.regenStart();
        java.util.Map<LocomotiveDiesel, com.dogpound.railmap.ohle.Ohle.Contact> contacts = new java.util.HashMap<>();
        java.util.Map<LocomotiveDiesel, Integer> regenOffered = new java.util.HashMap<>();
        java.util.List<LocomotiveDiesel> electric = new java.util.ArrayList<>();
        java.util.Map<LocomotiveDiesel, com.dogpound.railmap.grid.GridData.Net> regenNet = new java.util.HashMap<>();

        // pass 1: who is electric, blended braking, and what the braking trains give back
        for (LocomotiveDiesel loco : world.getEntities(LocomotiveDiesel.class)) {
            if (loco.isDead()) continue;
            RailProps.Props pr = props.of(loco.getUUID());
            if (!isElectric(loco, pr)) {
                pr.source = 0;
                loco.prideElectric = false;
                loco.prideTractionLimit = 1f;
                continue;
            }
            loco.prideElectric = true;
            electric.add(loco);
            // blended braking: the train brake brings in the electric brake first (as on real EMUs / electric locos),
            // so drivers, the autopilot and CBTC all regenerate; a driver's own dynamic-brake setting is left alone
            float tb = loco.getTrainBrakePos();
            if (tb > 0.01f && loco.getDynamicBrake() < tb) {
                loco.setDynamicBrake(tb);
                pr.blended = true;
            } else if (tb <= 0.01f && (pr.blended || Math.abs(loco.getThrottle()) > 0.01f && loco.getDynamicBrake() > 0)) {
                // brake released: take the blended electric brake off too (also after a restart, when the flag is gone:
                // nobody applies power against their own dynamic brake)
                loco.setDynamicBrake(0f);
                pr.blended = false;
            }
            double v = loco.getCurrentSpeed() == null ? 0 : Math.abs(loco.getCurrentSpeed().metersPerSecond());
            double brakeW = loco.getDynamicBrakeMultiplier() * loco.getDynamicBrakeNewton() * v * 0.85;   // motor-generator efficiency
            pr.kwRegen = (int) Math.round(brakeW / 1000);
            pr.regenTo = "";
            Vec3d p = loco.getPosition();
            BlockPos at = new BlockPos(p.x, p.y, p.z);
            boolean beam = beamAt(mc, at);
            boolean h2 = com.dogpound.railmap.traction.Traction.kind(loco) == com.dogpound.railmap.traction.Traction.H2;   // batch 5 #6: no pantograph
            // the pantograph: raise / lower, main breaker, which OHLE section, contact quality (#31, #32)
            com.dogpound.railmap.ohle.Ohle.Contact oc = beam || h2 ? null
                    : com.dogpound.railmap.ohle.Ohle.contact(mc, loco, at, Math.abs(loco.getThrottle()) > 0.01f || brakeW >= 2000, Math.abs(loco.getThrottle()), now);
            if (oc != null && !oc.underWire) oc = null;
            contacts.put(loco, oc);
            if (brakeW < 2000) continue;
            regenOffered.put(loco, pr.kwRegen);
            boolean ohl = oc != null;
            com.dogpound.railmap.grid.GridData.Net net = (ohl ? oc.collecting : !h2 && wired(mc, at))
                    ? com.dogpound.railmap.grid.GridTicker.offerRegen(mc, at, ohl, beam, Math.round(brakeW / W_PER_FE_S), ohl ? oc.feeders : null) : null;
            if (net != null) regenNet.put(loco, net);
            else if (com.dogpound.railmap.traction.Traction.regen(mc, loco, pr, brakeW)) pr.regenTo = "battery";   // batch 5 #6: BEMU / H2 traction battery
            else {
                pr.regenTo = "resistors";
                // no line to take it: the battery soaks up what it can
                pr.battery = Math.min(1f, pr.battery + (float) (brakeW / W_PER_FE_S) / Math.max(1, RailMapConfig.batteryFuelMb * 50f));
            }
        }

        // pass 2: trains under power draw what they need at their throttle, and pull as hard as the line lets them
        for (LocomotiveDiesel loco : electric) {
            RailProps.Props pr = props.of(loco.getUUID());
            Vec3d p = loco.getPosition();
            BlockPos at = new BlockPos(p.x, p.y, p.z);
            com.dogpound.railmap.ohle.Ohle.Contact oc = contacts.get(loco);
            boolean onWire = oc != null ? oc.collecting : wired(mc, at);
            int alt = com.dogpound.railmap.traction.Traction.kind(loco);          // batch 5 #6: battery-electric / hydrogen
            if (alt == com.dogpound.railmap.traction.Traction.H2) onWire = false;
            String note = oc == null ? "" : oc.note;
            if (oc == null && onWire && !beamAt(mc, at) && com.dogpound.railmap.weather.Weather.floodMm(mc, at) > 50) {   // #64: water over a live third rail
                onWire = false;
                note = "THIRD RAIL FLOODED - shorted to earth, no power";
                com.dogpound.railmap.weather.Weather.alarmOnce(mc, "flood3r:" + (at.getX() >> 6) + "," + (at.getZ() >> 6), true, com.dogpound.railmap.alarm.AlarmManager.Cat.FAULT,
                        com.dogpound.railmap.alarm.AlarmManager.Sev.MAJOR, "Third rail flooded near " + at.getX() + "," + at.getZ() + " - earth fault, section dead until the water drains", at);
            }
            float throttle = Math.abs(loco.getThrottle());
            double ratedW = Math.max(100_000, loco.getDefinition().getScriptedWatt(loco.gauge, loco));
            // traction motors + drive (~90 %) at this throttle, plus auxiliaries (compressors, lights, HVAC: ~2 %)
            double wantW = ratedW * throttle / 0.9 + ratedW * 0.02;
            double chargeW = alt == com.dogpound.railmap.traction.Traction.BEMU && onWire ? com.dogpound.railmap.traction.Traction.chargeW(loco, pr) : 0;   // OHLE charging section
            wantW += chargeW;
            long want = Math.max(1, Math.round(wantW / W_PER_FE_S));
            if (!RailMapConfig.powerNeedsEnergy) want = Math.min(want, RailMapConfig.fePerSecond);
            TilePowerPost sub = onWire ? liveSubstation(dim, at) : null;
            boolean beam = beamAt(mc, at);
            boolean ohl = oc != null;
            double frac;
            if (sub != null) frac = sub.draw((int) Math.min(Integer.MAX_VALUE, want)) ? 1 : 0;
            else if (!onWire) frac = -1;
            else {
                long got = com.dogpound.railmap.grid.GridTicker.supply(mc, at, ohl || !beam && overhead(mc, at), beam, want, ohl ? oc.feeders : null);
                frac = got < 0 ? -1 : (double) got / want;
            }
            if (!RailMapConfig.powerNeedsEnergy && frac > 0) frac = 1;
            float limit;
            if (alt != 0) {                                        // batch 5 #6: battery / fuel cell does its own sums
                limit = com.dogpound.railmap.traction.Traction.power(mc, loco, pr, alt, onWire, frac, wantW, chargeW, throttle);
                topUp(loco, 100);
                if (frac < 0) frac = 0;
            } else if (frac >= 0.999) {                                   // full power from the line
                limit = 1f;
                pr.battery = Math.min(1f, pr.battery + (float) (RailMapConfig.chargePerSecond / 100.0));
                pr.source = 1;
                topUp(loco, TOPUP_MB);
            } else if (frac > 0.05) {                              // a weak line: the battery fills the gap while it lasts
                double gap = 1 - frac;
                if (pr.battery > 0 && RailMapConfig.batteryFuelMb > 0) {
                    pr.battery = Math.max(0f, pr.battery - (float) (gap * throttle * 100 / RailMapConfig.batteryFuelMb));
                    limit = 1f;
                } else {
                    limit = (float) frac;
                }
                pr.source = 1;
                topUp(loco, TOPUP_MB);
            } else if (pr.battery > 0 && RailMapConfig.batteryFuelMb > 0) {   // off the wire / dead line: battery
                int added = topUp(loco, 100);
                pr.battery = Math.max(0f, pr.battery - added / (float) RailMapConfig.batteryFuelMb);
                limit = pr.battery > 0 ? 1f : 0f;
                pr.source = pr.battery > 0 ? (byte) 2 : (byte) 4;
                frac = 0;
            } else {
                limit = 0f;
                pr.source = 4;
                frac = 0;
            }
            if (ohl && frac > 0 && oc.loss > 0) limit *= 1 - oc.loss;                   // poor contact: the power dips
            if (com.dogpound.railmap.track.TrackHooks.tractionIsolated(mc, loco.getPosition().x, loco.getPosition().z)) { limit = 0f; frac = 0; note = "traction isolated - tunnel emergency"; }
            loco.prideTractionLimit = limit;
            pr.supplied = (float) Math.max(0, Math.min(1, frac));
            pr.kwDraw = pr.source == 1 ? (int) Math.round(wantW * Math.min(1, frac) / 1000) : 0;
            com.dogpound.railmap.ohle.OhleData.get(mc).pan(loco.getUUID()).note = note;
        }

        // where the regen went: other trains, the substation batteries, back up an AC line, or the resistors
        java.util.Map<com.dogpound.railmap.grid.GridData.Net, com.dogpound.railmap.grid.GridTicker.RegenResult> res =
                com.dogpound.railmap.grid.GridTicker.settleRegen(mc);
        for (java.util.Map.Entry<LocomotiveDiesel, com.dogpound.railmap.grid.GridData.Net> en : regenNet.entrySet()) {
            RailProps.Props pr = props.of(en.getKey().getUUID());
            com.dogpound.railmap.grid.GridTicker.RegenResult r = res.get(en.getValue());
            pr.regenTo = r == null ? "resistors" : r.mainSink();
            if (r != null && r.accepted() < 0.999) pr.kwRegen = (int) Math.round(pr.kwRegen * r.accepted());
        }
        // the energy ledger (#33): drawn, given back to the line, burnt in the resistors
        for (LocomotiveDiesel loco : electric) {
            RailProps.Props pr = props.of(loco.getUUID());
            int offered = regenOffered.getOrDefault(loco, 0);
            boolean burnt = offered > 0 && (pr.regenTo.isEmpty() || pr.regenTo.equals("resistors"));
            com.dogpound.railmap.ohle.EnergyDispatch.ledger(mc, loco.getUUID(), pr.kwDraw, burnt ? 0 : pr.kwRegen, burnt ? offered : Math.max(0, offered - pr.kwRegen));
        }
        props.markDirty();
    }

    private static boolean beamAt(World mc, BlockPos at) {
        for (int dy = -2; dy <= 0; dy++) if (prideBeam(mc, at.up(dy))) return true;
        return false;
    }

    /** Electric by override, else by name. */
    public static boolean isElectric(EntityRollingStock s, RailProps.Props pr) {
        if (pr.electric == RailProps.ON) return true;
        if (com.dogpound.railmap.traction.Traction.kind(s) != 0) return true;   // batch 5 #6: battery-electric / hydrogen units
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

    /** planning (#1/#48 digital twin): is this stop rail under contact wire / on a third rail? */
    public static boolean wiredAt(World mc, BlockPos at) { return wired(mc, at); }

    /** contact wire overhead (not just a third rail at track level) */
    private static boolean overhead(World mc, BlockPos at) {
        for (int dy = 2; dy <= 7; dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (mc.getBlockState(at.add(dx, dy, dz)).getBlock() instanceof BlockCatenary c && c.kind().line()) return true;
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

    /** Board readout: "" for non-electric, else e.g. "wire 2.4 MW · battery 87%" / "regen 1.1 MW → trains". */
    public static String status(World mc, EntityRollingStock s) {
        RailProps.Props pr = RailProps.get(mc).peek(s.getUUID());
        if (pr == null || !isElectric(s, pr)) return "";
        if (pr.kwRegen > 0 && !pr.regenTo.isEmpty())
            return (pr.regenTo.equals("resistors") ? "§6braking resistors " : "§aregen ") + power(pr.kwRegen) + (pr.regenTo.equals("resistors") ? "" : " → " + pr.regenTo)
                    + "§r · battery " + Math.round(pr.battery * 100) + "%";
        com.dogpound.railmap.ohle.OhleData.Pan pan = com.dogpound.railmap.ohle.OhleData.get(mc).pans.get(s.getUUID());
        String pn = pan == null || pan.note == null || pan.note.isEmpty() ? "" : " · §e" + pan.note + "§r";
        String src = switch (pr.source) {
            case 1 -> "wire " + power(pr.kwDraw) + (pr.supplied < 0.999 ? " §e(line gives " + Math.round(pr.supplied * 100) + "%)§r" : "");
            case 2 -> "battery";
            case 3 -> "charging";
            case 4 -> "§cNO POWER";
            default -> "idle";
        };
        return src + " · battery " + Math.round(pr.battery * 100) + "%" + pn;
    }

    private static String power(int kw) { return kw >= 1000 ? String.format(Locale.ROOT, "%.1f MW", kw / 1000.0) : kw + " kW"; }

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
