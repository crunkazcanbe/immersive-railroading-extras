package com.dogpound.railmap.client.render;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.client.CabDisplay;
import com.dogpound.railmap.network.PacketCab;
import com.dogpound.railmap.network.PacketCabKey;
import com.dogpound.railmap.sim.CabControls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.*;

/**
 * The cab desk (requested feature). Drawn in the cab of every
 * IR locomotive whose model has no driving controls of its own (and, as a switch / gauge / screen panel, in the ones
 * that do - config cabDeskMode), at the front of the loco's passenger area, facing the driver.
 * <p>
 * Console (sloped desk top): automatic brake (REL / LAP / SVC / EMG), independent brake, dynamic brake, the alerter
 * mushroom, sander, throttle (notches IDLE-8, or the steam regulator), reverser (F-N-R, or steam cut-off), and the
 * switch panel: headlights OFF / DIM / BRIGHT, cab light, bell, horn, engine start / stop or pantograph AUTO / UP / DOWN,
 * isolate key, fault reset. Hood (instrument panel): duplex main reservoir / brake pipe, brake cylinder, speedometer
 * with the ATP target bug, the driver's display screen (CBTC / ATP target speed, distance to go, planning, systems),
 * load ammeter, engine rpm / boiler pressure / line volts, fuel or water glass, the warning lamps, the radio handset
 * and the horn / whistle cord.
 * <p>
 * Right-click a control (or scroll up) to move it on / forward, left-click (or scroll down) back. Hold right-click on
 * the horn button or cord to sound it. Everything goes to the server (sim.CabControls) as real IR / IR Extras actions;
 * every handle is drawn from the synced loco state, so everyone in the cab sees the same desk.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class CabDeskRenderer {
    private CabDeskRenderer() {}

    private static final boolean IR = Loader.isModLoaded("immersiverailroading");

    // control kinds
    private static final int LEVER = 0, KNOB = 1, TOGGLE = 2, BUTTON = 3, MUSHROOM = 4, KEY = 5, HANDSET = 6, CORD = 7;
    // which locos: 0 all, 1 diesel (not electric), 2 electric, 3 steam, 4 not steam
    private static final int ANY = 0, DIESEL = 1, ELECTRIC = 2, STEAM = 3, NOT_STEAM = 4;
    // surfaces: 0 console (sloped desk top), 1 hood (instrument panel)
    private static final double[][] SURF = {
            // centre X, Y, Z (desk metres: X right, Y up from the cab floor, Z toward the driver), tilt from vertical (deg), half u, half v
            {0, 0.88, -0.20, 62, 0.78, 0.20},
            {0, 1.17, -0.46, 12, 0.78, 0.19},
    };

    private static final class Ctl {
        final String id, label;
        final int surf, kind, need;
        final double u, v, w, h;
        final boolean drive;
        Ctl(String id, int surf, double u, double v, double w, double h, int kind, String label, int need, boolean drive) {
            this.id = id; this.surf = surf; this.u = u; this.v = v; this.w = w; this.h = h; this.kind = kind; this.label = label; this.need = need; this.drive = drive;
        }
    }

    private static final List<Ctl> CTLS = Arrays.asList(
            // ---- console: brake stand (driver's left)
            new Ctl("abrk", 0, -0.62, 0.0, 0.14, 0.36, LEVER, "AUTO BRAKE", ANY, true),
            new Ctl("ibrk", 0, -0.45, 0.0, 0.12, 0.36, LEVER, "IND BRAKE", ANY, true),
            new Ctl("dyn", 0, -0.29, 0.0, 0.12, 0.36, LEVER, "DYN BRAKE", NOT_STEAM, true),
            // ---- console: centre
            new Ctl("ack", 0, -0.12, 0.08, 0.11, 0.11, MUSHROOM, "ALERTER", ANY, false),
            new Ctl("sand", 0, -0.12, -0.10, 0.08, 0.08, BUTTON, "SAND", ANY, false),
            // ---- console: power stand
            new Ctl("thr", 0, 0.06, 0.0, 0.14, 0.36, LEVER, "THROTTLE", ANY, true),
            new Ctl("rev", 0, 0.24, 0.0, 0.12, 0.36, LEVER, "REVERSER", ANY, true),
            // ---- console: switch panel
            new Ctl("hl", 0, 0.45, 0.13, 0.09, 0.08, KNOB, "HEADLT", ANY, false),
            new Ctl("cabl", 0, 0.58, 0.13, 0.09, 0.08, TOGGLE, "CAB LT", ANY, false),
            new Ctl("bell", 0, 0.71, 0.13, 0.09, 0.08, TOGGLE, "BELL", ANY, false),
            new Ctl("horn", 0, 0.45, 0.03, 0.09, 0.08, BUTTON, "HORN", ANY, false),
            new Ctl("eng", 0, 0.58, 0.03, 0.09, 0.08, BUTTON, "ENGINE", DIESEL, false),
            new Ctl("pan", 0, 0.58, 0.03, 0.09, 0.08, KNOB, "PANTO", ELECTRIC, false),
            new Ctl("iso", 0, 0.71, 0.03, 0.09, 0.08, KEY, "ISOLATE", ANY, false),
            new Ctl("flt", 0, 0.45, -0.07, 0.09, 0.08, BUTTON, "FLT RST", NOT_STEAM, false),
            // ---- hood
            new Ctl("radio", 1, 0.72, 0.0, 0.10, 0.30, HANDSET, "RADIO", ANY, false),
            new Ctl("cord", 1, -0.735, 0.02, 0.06, 0.30, CORD, "HORN CORD", ANY, false)
    );

    // ================================================================================================ placement

    /** where one loco's desk is this frame */
    private static final class Desk {
        Locomotive l;
        double x, y, z, yaw, pitch, floor, anchor, s;
        boolean flip, full, steam, elec, diesel;
    }

    private static final Map<String, Double> HALF_LEN = new HashMap<>();
    private static final Map<String, Boolean> MODEL_CAB = new HashMap<>();

    /** half-length of the passenger area (IR keeps it private: found from isAtFront) */
    private static double halfLen(Locomotive l) {
        String k = l.getDefinitionID() + "@" + l.gauge.value();
        Double h = HALF_LEN.get(k);
        if (h != null) return h;
        EntityRollingStockDefinition def = l.getDefinition();
        double gs = l.gauge.scale();
        double pcz = def.passengerCenter.z * gs, lo = 0, hi = 60;
        for (int i = 0; i < 30; i++) {
            double mid = (lo + hi) / 2;
            if (def.isAtFront(l.gauge, new cam72cam.mod.math.Vec3d(0, 0, pcz + mid))) hi = mid; else lo = mid;
        }
        HALF_LEN.put(k, hi);
        return hi;
    }

    /** the loco's model has its own throttle / brake / reverser (IR's interactive cab) */
    private static boolean modelCab(Locomotive l) {
        return MODEL_CAB.computeIfAbsent(l.getDefinitionID(), k -> {
            try {
                for (ModelComponentType t : new ModelComponentType[]{ModelComponentType.THROTTLE_X, ModelComponentType.THROTTLE_BRAKE_X,
                        ModelComponentType.THROTTLE_DYN_BRAKE_X, ModelComponentType.TRAIN_BRAKE_X, ModelComponentType.REVERSER_X})
                    if (!l.getDefinition().getModel().getControls(t).isEmpty()) return true;
            } catch (RuntimeException e) { return false; }
            return false;
        });
    }

    private static Desk desk(Locomotive l, float pt) {
        if (RailMapConfig.cabDeskMode == 0 || !RailMapConfig.cabControls || l.getDefinition() == null || l.getDefinition().passengerCenter == null) return null;
        Desk d = new Desk();
        d.l = l;
        d.full = RailMapConfig.cabDeskMode == 2 || !modelCab(l);
        Entity e = l.internal;
        d.x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt;
        d.y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt;
        d.z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
        d.yaw = l.getRotationYaw();
        d.pitch = l.getRotationPitch();
        double gs = l.gauge.scale();
        d.s = Math.max(0.6, Math.min(1.2, gs));
        EntityRollingStockDefinition def = l.getDefinition();
        boolean sit = def.shouldSit != null ? def.shouldSit : l.gauge.shouldSit();
        d.floor = def.passengerCenter.y * gs - (sit ? 0.45 : 0) + RailMapConfig.cabDeskYOffset;
        double hl = halfLen(l), pcz = def.passengerCenter.z * gs;
        d.flip = RailMapConfig.cabDeskFlip;
        double back = (0.67 + RailMapConfig.cabDeskZOffset) * d.s;
        d.anchor = d.flip ? pcz - hl + back : pcz + hl - back;
        d.steam = l instanceof LocomotiveSteam;
        d.elec = !d.steam && l.getControlPosition(CabControls.ELEC) > 0.5f;
        d.diesel = l instanceof LocomotiveDiesel && !d.elec;
        return d;
    }

    private static boolean shown(Desk d, Ctl c) {
        if (c.drive && !d.full) return false;
        switch (c.need) {
            case DIESEL: return d.diesel;
            case ELECTRIC: return d.elec;
            case STEAM: return d.steam;
            case NOT_STEAM: return !d.steam;
            default: return true;
        }
    }

    // ---- math: world <-> desk <-> surface (same chain as the GL transform)

    private static double[] ry(double[] p, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[]{c * p[0] + s * p[2], p[1], -s * p[0] + c * p[2]};
    }

    private static double[] rx(double[] p, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[]{p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c};
    }

    /** a world vector (point when {@code point}) into desk metres */
    private static double[] toDesk(Desk d, double[] w, boolean point) {
        double[] q = point ? new double[]{w[0] - d.x, w[1] - d.y, w[2] - d.z} : w.clone();
        q = ry(q, d.yaw);
        q = rx(q, d.pitch);
        if (point) { q[1] -= d.floor; q[2] -= d.anchor; }
        if (!d.flip) { q[0] = -q[0]; q[2] = -q[2]; }
        return new double[]{q[0] / d.s, q[1] / d.s, q[2] / d.s};
    }

    private static double[] toSurf(int s, double[] p, boolean point) {
        double[] q = point ? new double[]{p[0] - SURF[s][0], p[1] - SURF[s][1], p[2] - SURF[s][2]} : p.clone();
        return rx(q, SURF[s][3]);
    }

    // ================================================================================================ picking + input

    private static int hoverEntity = -1;
    private static Ctl hover;
    private static boolean hornHeld;
    private static final Map<String, Long> PRESSED = new HashMap<>();

    private static boolean onTrain(Locomotive l, EntityPlayer p) {
        List<EntityCoupleableRollingStock> cars = new ArrayList<>(l.getTrain(false));
        if (cars.isEmpty()) cars.add(l);
        for (EntityCoupleableRollingStock s : cars)
            for (cam72cam.mod.entity.Entity e : s.getPassengers()) if (e.internal == p) return true;
        return false;
    }

    private static List<Locomotive> near(Minecraft mc, double r) {
        List<Locomotive> out = new ArrayList<>();
        for (Locomotive l : cam72cam.mod.world.World.get(mc.world).getEntities(Locomotive.class)) {
            if (l.isDead()) continue;
            if (l.internal.getDistanceSq(mc.player) < r * r) out.add(l);
        }
        return out;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (!IR || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        hover = null;
        hoverEntity = -1;
        if (mc.world == null || mc.player == null || mc.currentScreen != null || mc.player.getRidingEntity() == null) { hornHeld = false; return; }
        try {
            pick(mc);
        } catch (LinkageError | RuntimeException ex) {
            return;
        }
        if (hornHeld) {
            if (!org.lwjgl.input.Mouse.isButtonDown(1) || hover == null || !(hover.id.equals("horn") || hover.id.equals("cord"))) hornHeld = false;
            else if (mc.world.getTotalWorldTime() % 4 == 0) send("horn");
        }
    }

    private static void pick(Minecraft mc) {
        EntityPlayer p = mc.player;
        double[] eye = {p.posX, p.posY + p.getEyeHeight(), p.posZ};
        net.minecraft.util.math.Vec3d lv = p.getLookVec();
        double[] look = {lv.x, lv.y, lv.z};
        double best = 3.0;
        for (Locomotive l : near(mc, 12)) {
            if (!onTrain(l, p)) continue;
            Desk d = desk(l, 1f);
            if (d == null) continue;
            double[] o = toDesk(d, eye, true), dir = toDesk(d, look, false);
            double dl = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]) * d.s;   // desk metres per world metre
            for (int s = 0; s < SURF.length; s++) {
                double[] so = toSurf(s, o, true), sd = toSurf(s, dir, false);
                if (sd[2] >= -1e-6) continue;                                  // looking away from the panel
                double t = -so[2] / sd[2];
                double worldT = t * dl;
                if (t < 0 || worldT > best) continue;
                double u = so[0] + sd[0] * t, v = so[1] + sd[1] * t;
                for (Ctl c : CTLS) {
                    if (c.surf != s || !shown(d, c)) continue;
                    if (Math.abs(u - c.u) <= c.w / 2 && Math.abs(v - c.v) <= c.h / 2) {
                        best = worldT;
                        hover = c;
                        hoverEntity = l.internal.getEntityId();
                    }
                }
            }
        }
    }

    private static void send(String action) {
        if (hoverEntity < 0) return;
        RailMap.NETWORK.sendToServer(new PacketCabKey("d:" + action + "@" + hoverEntity));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(MouseEvent e) {
        if (!IR || hover == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;
        int dir = 0;
        if (e.getDwheel() != 0) dir = e.getDwheel() > 0 ? 1 : -1;
        else if (e.isButtonstate() && e.getButton() == 1) dir = 1;
        else if (e.isButtonstate() && e.getButton() == 0) dir = -1;
        else if (e.getButton() == 0 || e.getButton() == 1) { e.setCanceled(true); return; }   // swallow the release too
        if (dir == 0) return;
        e.setCanceled(true);
        Ctl c = hover;
        PRESSED.put(hoverEntity + c.id, mc.world.getTotalWorldTime());
        switch (c.kind) {
            case LEVER:
                send(c.id + (dir > 0 ? "+" : "-"));
                break;
            case CORD:
            case BUTTON:
                if (c.id.equals("horn") || c.id.equals("cord")) {
                    if (e.getDwheel() != 0) return;
                    send("horn");
                    hornHeld = dir > 0;
                    break;
                }
                // fallthrough
            default:
                if (e.getDwheel() != 0) return;                                // switches only on a click
                send(c.id);
        }
    }

    // ================================================================================================ drawing

    private static final int STEEL = 0x3A3F44, DARK = 0x1E2124, PANEL = 0x2C3136, CREAM = 0xEDE6D0, BLACK = 0x111111,
            RED = 0xC62828, AMBER = 0xFFB300, GREEN = 0x43A047, BLUE = 0x4FC3F7, WHITE = 0xF2F2F2, YELLOW = 0xFDD835, WOOD = 0x6D4C33;

    private static int light;

    @SubscribeEvent
    public static void render(RenderWorldLastEvent e) {
        if (!IR || RailMapConfig.cabDeskMode == 0 || !RailMapConfig.cabControls) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null || mc.player == null) return;
        Entity v = mc.getRenderViewEntity();
        float pt = e.getPartialTicks();
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt, cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt, cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
        try {
            for (Locomotive l : near(mc, 24)) {
                Desk d = desk(l, pt);
                if (d == null) continue;
                double dist2 = (d.x - cx) * (d.x - cx) + (d.y - cy) * (d.y - cy) + (d.z - cz) * (d.z - cz);
                boolean close = dist2 < 8 * 8;
                BlockPos bp = new BlockPos(d.x, d.y + 1.5, d.z);
                light = mc.world.getCombinedLight(bp, 0);
                if (l.getControlPosition(CabControls.LIGHT) > 0.5f) light = Math.max(light & 0xFFFF, 200) | (light & 0xFFFF0000);   // cab light on
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(d.x - cx, d.y - cy, d.z - cz);
                    GlStateManager.rotate((float) -d.yaw, 0, 1, 0);
                    GlStateManager.rotate((float) -d.pitch, 1, 0, 0);
                    GlStateManager.translate(0, d.floor, d.anchor);
                    if (!d.flip) GlStateManager.rotate(180f, 0, 1, 0);
                    GlStateManager.scale(d.s, d.s, d.s);
                    GlStateManager.disableLighting();
                    GlStateManager.disableCull();
                    GlStateManager.color(1, 1, 1, 1);
                    lit();
                    drawDesk(mc, d, close, mc.world.getTotalWorldTime(), pt);
                } finally {
                    GlStateManager.enableCull();
                    GlStateManager.popMatrix();
                }
            }
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.debug("[irextras] cab desk render: {}", ex.toString());
        }
        Prims.end();
        GlStateManager.enableLighting();
    }

    private static void lit() { OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16); }
    private static void bright() { OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f); }

    private static void drawDesk(Minecraft mc, Desk d, boolean close, long now, float pt) {
        Locomotive l = d.l;
        Prims.begin();
        // ---- the desk body (steel, a kick plate, the hood behind)
        Prims.box(-0.80, 0, -0.62, 0.80, 0.78, -0.02, STEEL);
        Prims.box(-0.80, 0, -0.04, 0.80, 0.10, 0.0, DARK);
        Prims.box(-0.82, 0.76, -0.64, 0.82, 0.80, 0.02, DARK);        // console lip
        // ---- console (sloped)
        surface(0);
        Prims.box(-0.78, -0.20, -0.03, 0.78, 0.20, 0, PANEL);
        Prims.box(-0.72, -0.19, 0, -0.20, 0.19, 0.002, 0x24282C);        // brake stand plate
        Prims.box(-0.03, -0.19, 0, 0.33, 0.19, 0.002, 0x24282C);         // power stand plate
        Prims.box(0.39, -0.12, 0, 0.77, 0.19, 0.002, 0x24282C);          // switch panel plate
        for (Ctl c : CTLS) if (c.surf == 0) drawCtl(mc, d, c, close, now);
        GlStateManager.popMatrix();
        // ---- hood (instrument panel)
        surface(1);
        Prims.box(-0.78, -0.19, -0.10, 0.78, 0.19, 0, PANEL);
        Prims.box(-0.80, 0.17, -0.12, 0.80, 0.21, 0.06, DARK);           // glare shield
        gauges(mc, d, close, now, pt);
        screen(mc, d, close, now);
        lamps(mc, d, close, now);
        for (Ctl c : CTLS) if (c.surf == 1) drawCtl(mc, d, c, close, now);
        GlStateManager.popMatrix();
        Prims.end();
        lit();
    }

    /** push the surface frame (u right, v up the panel, w out toward the driver) */
    private static void surface(int s) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(SURF[s][0], SURF[s][1], SURF[s][2]);
        GlStateManager.rotate((float) -SURF[s][3], 1, 0, 0);
    }

    // ---- controls

    private static void drawCtl(Minecraft mc, Desk d, Ctl c, boolean close, long now) {
        if (!shown(d, c)) {
            if (c.drive && !d.full) Prims.box(c.u - c.w / 2, c.v - c.h / 2, 0, c.u + c.w / 2, c.v + c.h / 2, 0.004, 0x2A2E33);   // blanking plate
            return;
        }
        Locomotive l = d.l;
        boolean hov = hover == c && hoverEntity == l.internal.getEntityId();
        Long pr = PRESSED.get(l.internal.getEntityId() + c.id);
        boolean pressed = pr != null && now - pr < 6;
        String state = "";
        switch (c.kind) {
            case LEVER: {
                float t; String[] marks;
                switch (c.id) {
                    case "thr":
                        t = l.getThrottle();
                        marks = d.steam ? new String[]{"SHUT", "", "", "", "OPEN"} : new String[]{"IDLE", "2", "4", "6", "8"};
                        state = d.steam ? "REGULATOR " + Math.round(t * 100) + "%" : t <= 0.001f ? "IDLE" : "NOTCH " + Math.round(t * CabControls.NOTCHES);
                        break;
                    case "rev":
                        t = (l.getReverser() + 1) / 2;
                        marks = new String[]{"R", "", "N", "", "F"};
                        state = d.steam ? (Math.abs(l.getReverser()) < 0.05f ? "MID GEAR" : (l.getReverser() > 0 ? "FWD " : "REV ") + Math.round(Math.abs(l.getReverser()) * 100) + "%")
                                : l.getReverser() > 0.5f ? "FORWARD" : l.getReverser() < -0.5f ? "REVERSE" : "NEUTRAL";
                        break;
                    case "abrk": {
                        float h = l.getControlPosition(CabControls.AUTOBRAKE);
                        t = h == CabControls.REL ? 0 : h == CabControls.SVC ? 0.66f : h == CabControls.EMG ? 1 : 0.33f;
                        marks = new String[]{"REL", "LAP", "", "SVC", "EMG"};
                        state = new String[]{"RELEASE", "LAP", "SERVICE", "EMERGENCY"}[t == 0 ? 0 : t == 1 ? 3 : t > 0.5f ? 2 : 1] + " " + Math.round(l.getTrainBrakePos() * 100) + "%";
                        break;
                    }
                    case "ibrk":
                        t = l.getIndependentBrake();
                        marks = new String[]{"REL", "", "", "", "FULL"};
                        state = Math.round(t * 100) + "%";
                        break;
                    default:
                        t = l instanceof LocomotiveDiesel dd ? dd.getDynamicBrake() : 0;
                        marks = new String[]{"OFF", "", "", "", "MAX"};
                        state = t <= 0 ? "OFF" : "NOTCH " + Math.round(t * 8);
                }
                lever(mc, c, t, marks, c.id.equals("abrk") ? RED : c.id.equals("thr") ? BLACK : c.id.equals("rev") ? 0x5D4037 : 0x37474F, close);
                break;
            }
            case KNOB: {
                float v = l.getControlPosition(c.id.equals("hl") ? CabControls.HEADLIGHT : CabControls.PANTO);
                double ang;
                String[] lab;
                if (c.id.equals("hl")) { ang = -45 + 90 * v; lab = new String[]{"OFF", "DIM", "BRT"}; state = v < 0.25f ? "OFF" : v < 0.75f ? "DIM" : "BRIGHT"; }
                else { ang = v < 0.25f ? 0 : v > 0.75f ? 45 : -45; lab = new String[]{"DN", "AUTO", "UP"}; state = v < 0.25f ? "AUTO" : v > 0.75f ? "UP" : "DOWN";
                    float ps = l.getControlPosition(CabControls.PANST); state += ps > 0.75f ? " - VCB CLOSED" : ps > 0.25f ? " - RAISING" : " - LOWERED"; }
                Prims.disc(c.u, c.v, 0.003, 0.03, BLACK, 1);
                GlStateManager.pushMatrix();
                GlStateManager.translate(c.u, c.v, 0);
                GlStateManager.rotate((float) -ang, 0, 0, 1);
                Prims.box(-0.008, -0.026, 0.003, 0.008, 0.026, 0.02, 0x263238);
                Prims.box(-0.002, 0.012, 0.02, 0.002, 0.026, 0.021, WHITE);
                GlStateManager.popMatrix();
                if (close) { text(lab[0], c.u - 0.034, c.v + 0.025, 0.004, 0.009, WHITE, true); text(lab[1], c.u, c.v + 0.036, 0.004, 0.009, WHITE, true); text(lab[2], c.u + 0.034, c.v + 0.025, 0.004, 0.009, WHITE, true); }
                break;
            }
            case TOGGLE: {
                boolean on = l.getControlPosition(c.id.equals("cabl") ? CabControls.LIGHT : CabControls.BELL) > 0.5f;
                state = on ? "ON" : "OFF";
                Prims.box(c.u - 0.012, c.v - 0.018, 0.002, c.u + 0.012, c.v + 0.018, 0.008, 0x9E9E9E);
                GlStateManager.pushMatrix();
                GlStateManager.translate(c.u, c.v, 0.008);
                GlStateManager.rotate(on ? -25f : 25f, 1, 0, 0);
                Prims.box(-0.003, -0.003, 0, 0.003, 0.003, 0.03, 0xCFD8DC);
                GlStateManager.popMatrix();
                if (close) { text("ON", c.u, c.v + 0.03, 0.004, 0.008, WHITE, true); text("OFF", c.u, c.v - 0.03, 0.004, 0.008, WHITE, true); }
                break;
            }
            case BUTTON: {
                int col = c.id.equals("horn") ? AMBER : c.id.equals("sand") ? 0xA1887F : c.id.equals("eng") ? GREEN : 0x90A4AE;
                boolean active = pressed || c.id.equals("horn") && l.getHornTime() > 0 || c.id.equals("sand") && l.getControlPosition(CabControls.SANDL) > 0.5f;
                if (c.id.equals("eng") && l instanceof LocomotiveDiesel dd) { active |= dd.isTurnedOn(); state = dd.isTurnedOn() ? (dd.isRunning() ? "RUNNING" : "STARTING") : "STOPPED"; }
                if (c.id.equals("flt")) { boolean f = l.getControlPosition(CabControls.FLT) > 0.5f; state = f ? "FAULT - PRESS TO RESET" : "NO FAULT"; active |= f && now % 20 < 10; col = f ? RED : col; }
                Prims.box(c.u - 0.022, c.v - 0.022, 0, c.u + 0.022, c.v + 0.022, 0.006, BLACK);
                if (active) bright();
                Prims.disc(c.u, c.v, pressed ? 0.008 : 0.016, 0.016, active ? col : Prims.shade(col, 0.55f), 1);
                Prims.box(c.u - 0.015, c.v - 0.015, 0.006, c.u + 0.015, c.v + 0.015, pressed ? 0.008 : 0.015, active ? col : Prims.shade(col, 0.55f));
                lit();
                break;
            }
            case MUSHROOM: {
                float a = l.getControlPosition(CabControls.ALERT);
                boolean lit = a > 0.75f || a > 0.25f && now % 10 < 6;
                state = a > 0.75f ? "PENALTY - STOP, THEN ACKNOWLEDGE" : a > 0.25f ? "ALERT - ACKNOWLEDGE NOW" : "QUIET";
                Prims.box(c.u - 0.01, c.v - 0.01, 0, c.u + 0.01, c.v + 0.01, 0.02, 0x424242);
                if (lit) { bright(); Prims.glow(c.u, c.v, 0.04, 0.07, YELLOW); }
                Prims.disc(c.u, c.v, pressed ? 0.024 : 0.032, 0.042, lit ? YELLOW : 0xB59A00, 1);
                Prims.box(c.u - 0.03, c.v - 0.03, 0.02, c.u + 0.03, c.v + 0.03, pressed ? 0.024 : 0.031, lit ? YELLOW : 0xB59A00);
                lit();
                break;
            }
            case KEY: {
                boolean iso = l.getControlPosition(CabControls.ISOLATE) > 0.5f;
                state = iso ? "ISOLATED - NO TRACTION" : "RUN";
                Prims.disc(c.u, c.v, 0.003, 0.02, 0xBDBDBD, 1);
                GlStateManager.pushMatrix();
                GlStateManager.translate(c.u, c.v, 0.003);
                GlStateManager.rotate(iso ? 90f : 0f, 0, 0, 1);
                Prims.box(-0.003, -0.012, 0, 0.003, 0.012, 0.012, 0xFFD54F);
                Prims.box(-0.01, -0.006, 0.012, 0.01, 0.006, 0.016, 0xFFD54F);
                GlStateManager.popMatrix();
                if (close) { text("RUN", c.u, c.v + 0.03, 0.004, 0.008, WHITE, true); text("ISOL", c.u + 0.033, c.v, 0.004, 0.008, iso ? AMBER : WHITE, true); }
                break;
            }
            case HANDSET: {
                float r = l.getControlPosition(CabControls.RADIO);
                boolean blink = r > 0.75f && now % 16 < 8;
                state = r > 0.75f ? "INSTRUCTION WAITING - LIFT TO ACKNOWLEDGE" : r > 0.25f ? "CALL PLACED" : "IDLE - LIFT TO CALL THE SIGNALLER";
                Prims.box(c.u - 0.045, c.v - 0.15, 0, c.u + 0.045, c.v + 0.15, 0.02, 0x263238);            // cradle
                double lift = pressed ? 0.03 : 0;
                Prims.box(c.u - 0.025, c.v - 0.13, 0.02 + lift, c.u + 0.025, c.v + 0.13, 0.04 + lift, BLACK); // handset
                Prims.box(c.u - 0.03, c.v + 0.08, 0.04 + lift, c.u + 0.03, c.v + 0.13, 0.055 + lift, BLACK);
                Prims.box(c.u - 0.03, c.v - 0.13, 0.04 + lift, c.u + 0.03, c.v - 0.08, 0.055 + lift, BLACK);
                bright();
                if (blink || r > 0.25f && r < 0.75f) Prims.glow(c.u, c.v - 0.17, 0.03, 0.03, blink ? AMBER : GREEN);
                Prims.disc(c.u, c.v - 0.17, 0.021, 0.008, blink ? AMBER : r > 0.25f ? GREEN : 0x33402F, 1);
                lit();
                if (close) text("RADIO", c.u, c.v + 0.165, 0.022, 0.009, WHITE, true);
                break;
            }
            case CORD: {
                boolean pull = l.getHornTime() > 0;
                double dv = pull ? -0.05 : 0;
                state = pull ? (d.steam ? "WHISTLE" : "HORN") + " SOUNDING" : "HOLD RIGHT-CLICK TO SOUND";
                Prims.box(c.u - 0.002, c.v + dv - 0.1, 0.07, c.u + 0.002, c.v + 0.6, 0.074, 0xBCAAA4);      // cord up to the roof
                Prims.box(c.u - 0.012, c.v + dv - 0.15, 0.062, c.u + 0.012, c.v + dv - 0.1, 0.082, WOOD);   // handle
                break;
            }
            default: break;
        }
        if (close && !c.label.isEmpty() && c.kind != HANDSET && c.kind != CORD)
            text(c.label, c.u, c.v - c.h / 2 - 0.004 + (c.kind == LEVER ? 0.0 : -0.006), 0.004, c.kind == LEVER ? 0.012 : 0.0085, 0xCFD8DC, true);
        if (hov) {
            bright();
            Prims.box(c.u - c.w / 2, c.v - c.h / 2, 0.001, c.u + c.w / 2, c.v - c.h / 2 + 0.003, 0.005, 0x80DEEA);
            Prims.box(c.u - c.w / 2, c.v + c.h / 2 - 0.003, 0.001, c.u + c.w / 2, c.v + c.h / 2, 0.005, 0x80DEEA);
            Prims.box(c.u - c.w / 2, c.v - c.h / 2, 0.001, c.u - c.w / 2 + 0.003, c.v + c.h / 2, 0.005, 0x80DEEA);
            Prims.box(c.u + c.w / 2 - 0.003, c.v - c.h / 2, 0.001, c.u + c.w / 2, c.v + c.h / 2, 0.005, 0x80DEEA);
            String hint = c.kind == LEVER ? "  [R-click / scroll up: on · L-click / down: off]" : c.id.equals("horn") || c.id.equals("cord") ? "  [hold R-click]" : "  [click]";
            text(c.label + ": " + state, c.u, c.v + c.h / 2 + 0.03, 0.09, 0.016, 0xFFFFFF, true);
            text(hint, c.u, c.v + c.h / 2 + 0.012, 0.09, 0.009, 0xB0BEC5, true);
            lit();
        }
    }

    /** a desk lever in its quadrant: t 0 (driver end) .. 1 (pushed away) */
    private static void lever(Minecraft mc, Ctl c, float t, String[] marks, int knob, boolean close) {
        double len = 0.14;
        Prims.box(c.u - 0.016, c.v - 0.15, 0, c.u + 0.016, c.v + 0.15, 0.006, 0x1A1A1A);        // the gate
        Prims.box(c.u - 0.004, c.v - 0.13, 0.006, c.u + 0.004, c.v + 0.13, 0.007, 0x000000);       // slot
        for (int i = 0; i < marks.length; i++) {                                                  // detent marks along the gate
            double phi = Math.toRadians(35 - 70.0 * i / (marks.length - 1));
            double mv = c.v - Math.sin(phi) * len * 0.9;
            Prims.box(c.u + 0.018, mv - 0.001, 0.006, c.u + 0.03, mv + 0.001, 0.007, WHITE);
            if (close && !marks[i].isEmpty()) text(marks[i], c.u + 0.034, mv, 0.007, 0.008, WHITE, false);
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(c.u, c.v, 0.004);
        GlStateManager.rotate(35f - 70f * t, 1, 0, 0);
        Prims.box(-0.006, -0.006, 0, 0.006, 0.006, len, 0x9E9E9E);
        Prims.box(-0.02, -0.014, len, 0.02, 0.014, len + 0.026, knob);
        GlStateManager.popMatrix();
    }

    // ---- instruments

    private static void gauges(Minecraft mc, Desk d, boolean close, long now, float pt) {
        Locomotive l = d.l;
        // duplex: main reservoir (red) + brake pipe (black), bar
        float mr = l.getMainAirReservoir() * 10;
        float tb = l.getTrainBrakePos();
        float bp = l.getEmergency() || tb >= 0.99f ? 0 : Math.min(mr, 5.0f - 1.5f * tb);
        dial(mc, -0.62, 0.07, 0.085, 0, 12, new float[]{mr, bp}, new int[]{RED, BLACK}, "MR / BP", "bar", 6, 10.5f, close, -1);
        // brake cylinder
        dial(mc, -0.62, -0.11, 0.07, 0, 6, new float[]{l.getBrakePressure() * 5}, new int[]{BLACK}, "BRK CYL", "bar", 6, 99, close, -1);
        // speedometer + ATP target bug
        double maxKmh = 60;
        try { maxKmh = Math.max(60, Math.ceil(l.getDefinition().getMaxSpeed(l.gauge).metric() * 1.2 / 20) * 20); } catch (RuntimeException ignored) {}
        float spd = (float) (l.getCurrentSpeed() == null ? 0 : Math.abs(l.getCurrentSpeed().metric()));
        PacketCab cab = mine(l) ? CabDisplay.current() : null;
        float bug = cab != null && cab.target >= 0 ? cab.target : -1;
        dial(mc, -0.36, -0.01, 0.135, 0, (float) maxKmh, new float[]{spd}, new int[]{BLACK}, "SPEED", "km/h", (int) Math.round(maxKmh / 20), 999, close, bug);
        // load ammeter: traction effort (+) / dynamic brake (-)
        float te = 0, dyn = l instanceof LocomotiveDiesel dd ? dd.getDynamicBrake() : 0;
        try { te = l.getCurrentTractiveEffort(); } catch (RuntimeException ignored) {}
        if (d.steam) dial(mc, 0.30, 0.07, 0.068, 0, 100, new float[]{l instanceof LocomotiveSteam st ? st.getChestPressurePercent() * 100 : 0}, new int[]{BLACK}, "STEAM CHEST", "%", 5, 999, close, -1);
        else dial(mc, 0.30, 0.07, 0.068, -100, 100, new float[]{te * 100 - dyn * 100}, new int[]{BLACK}, dyn > 0 ? "DYN BRAKE" : "LOAD", "%", 4, 85, close, -1);
        // engine rpm / boiler pressure / line volts
        if (d.steam && l instanceof LocomotiveSteam st) {
            float max = Math.max(50, st.getMaxBoilerPSI());
            dial(mc, 0.30, -0.11, 0.068, 0, max * 1.2f, new float[]{st.getBoilerPressure()}, new int[]{BLACK}, "BOILER", "psi", 6, max, close, -1);
        } else if (d.elec) {
            float kv = l.getControlPosition(CabControls.PANST) > 0.75f ? 25 : 0;
            dial(mc, 0.30, -0.11, 0.068, 0, 30, new float[]{kv}, new int[]{BLACK}, "LINE", "kV", 6, 27.5f, close, -1);
        } else if (l instanceof LocomotiveDiesel dd) {
            float rpm = l.getControlPosition(CabControls.RPM) * 2000;
            if (rpm <= 0 && dd.isRunning()) rpm = 300 + dd.getRelativeRPM() * 600;
            dial(mc, 0.30, -0.11, 0.068, 0, 1500, new float[]{rpm}, new int[]{BLACK}, "ENGINE", "rpm", 5, 1100, close, -1);
        }
        // fuel tank / water glass
        float pct = l.getPercentLiquidFull() / 100f;
        if (d.steam || d.diesel) {
            double u = 0.425, v0 = -0.15, v1 = 0.15;
            Prims.box(u - 0.025, v0 - 0.012, 0, u + 0.025, v1 + 0.012, 0.01, d.steam ? 0x8D6E63 : BLACK);
            Prims.box(u - 0.012, v0, 0.01, u + 0.012, v1, 0.012, 0x0D1A12);
            bright();
            int col = d.steam ? 0x81D4FA : pct < 0.15f ? RED : AMBER;
            Prims.box(u - 0.011, v0, 0.0125, u + 0.011, v0 + (v1 - v0) * Math.max(0, Math.min(1, pct)), 0.0135, col);
            if (d.steam) Prims.box(u - 0.016, v0 + (v1 - v0) * 0.33 - 0.001, 0.012, u + 0.016, v0 + (v1 - v0) * 0.33 + 0.001, 0.014, RED);   // lowest safe water
            lit();
            if (close) {
                text(d.steam ? "WATER" : "FUEL", u, v1 + 0.024, 0.012, 0.009, WHITE, true);
                text(Math.round(pct * 100) + "%", u, v0 - 0.024, 0.012, 0.009, WHITE, true);
            }
        }
        // batch 5 #6: battery-electric SOC glass / hydrogen unit's buffer + H2 glasses (written by traction.Traction)
        float tract = l.getControlPosition(com.dogpound.railmap.traction.Traction.TRACT);
        if (tract > 0.25f) {
            boolean h2 = tract > 0.75f;
            float[] lv = h2 ? new float[]{l.getControlPosition(com.dogpound.railmap.traction.Traction.SOC), l.getControlPosition(com.dogpound.railmap.traction.Traction.H2LVL)}
                    : new float[]{l.getControlPosition(com.dogpound.railmap.traction.Traction.SOC)};
            String[] nm = h2 ? new String[]{"BUFFER", "H2"} : new String[]{"SOC"};
            for (int i = 0; i < lv.length; i++) {
                double u = 0.425 + i * 0.065, v0 = -0.15, v1 = 0.15;
                float x = Math.max(0, Math.min(1, lv[i]));
                Prims.box(u - 0.025, v0 - 0.012, 0, u + 0.025, v1 + 0.012, 0.01, BLACK);
                Prims.box(u - 0.012, v0, 0.01, u + 0.012, v1, 0.012, 0x0D1A12);
                bright();
                int col = i == 1 ? 0x81D4FA : x < 0.15f ? RED : x < 0.3f ? AMBER : 0x4CDB6A;
                Prims.box(u - 0.011, v0, 0.0125, u + 0.011, v0 + (v1 - v0) * x, 0.0135, col);
                Prims.box(u - 0.016, v0 + (v1 - v0) * 0.2 - 0.001, 0.012, u + 0.016, v0 + (v1 - v0) * 0.2 + 0.001, 0.014, RED);
                lit();
                if (close) {
                    text(nm[i], u, v1 + 0.024, 0.012, 0.009, WHITE, true);
                    text(Math.round(x * 100) + "%", u, v0 - 0.024, 0.012, 0.009, WHITE, true);
                }
            }
        }
    }

    /** a round gauge: 270° scale from min to max, needles in their colours, an optional target bug, red from redFrom */
    private static void dial(Minecraft mc, double u, double v, double r, float min, float max, float[] vals, int[] cols, String name, String unit,
                             int majors, float redFrom, boolean close, float bug) {
        Prims.disc(u, v, 0.002, r * 1.13, BLACK, 1);
        Prims.disc(u, v, 0.004, r, CREAM, 1);
        for (int i = 0; i <= majors * 2; i++) {
            float val = min + (max - min) * i / (majors * 2f);
            double ang = 225 - 270.0 * i / (majors * 2);
            boolean major = i % 2 == 0;
            GlStateManager.pushMatrix();
            GlStateManager.translate(u, v, 0.005);
            GlStateManager.rotate((float) (ang - 90), 0, 0, 1);
            Prims.box(-0.0012, r * (major ? 0.78 : 0.86), 0, 0.0012, r * 0.96, 0.0008, val >= redFrom ? RED : 0x222222);
            GlStateManager.popMatrix();
            if (major && close) {
                double a = Math.toRadians(ang);
                String n = Math.abs(max - min) >= 100 ? String.valueOf(Math.round(val)) : (Math.round(val * 10) % 10 == 0 ? String.valueOf(Math.round(val)) : String.format(Locale.ROOT, "%.1f", val));
                text(n, u + Math.cos(a) * r * 0.6, v + Math.sin(a) * r * 0.6, 0.006, r * 0.14, 0x222222, true);
            }
        }
        if (close) {
            text(name, u, v - r * 0.42, 0.006, r * 0.13, 0x333333, true);
            text(unit, u, v + r * 0.32, 0.006, r * 0.11, 0x555555, true);
        }
        if (bug >= 0) {                                                       // ATP target speed bug on the rim
            double ang = 225 - 270.0 * Math.max(0, Math.min(1, (bug - min) / (max - min)));
            GlStateManager.pushMatrix();
            GlStateManager.translate(u, v, 0.006);
            GlStateManager.rotate((float) (ang - 90), 0, 0, 1);
            bright();
            Prims.box(-0.006, r * 0.97, 0, 0.006, r * 1.1, 0.002, AMBER);
            lit();
            GlStateManager.popMatrix();
        }
        for (int i = vals.length - 1; i >= 0; i--) {
            double f = Math.max(-0.02, Math.min(1.02, (vals[i] - min) / (max - min)));
            double ang = 225 - 270.0 * f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(u, v, 0.008 + i * 0.002);
            GlStateManager.rotate((float) (ang - 90), 0, 0, 1);
            Prims.box(-0.0025, -r * 0.15, 0, 0.0025, r * 0.88, 0.0015, cols[i]);
            GlStateManager.popMatrix();
        }
        Prims.disc(u, v, 0.013, r * 0.08, 0x333333, 1);
    }

    private static boolean mine(Locomotive l) {
        EntityPlayer p = Minecraft.getMinecraft().player;
        return p != null && onTrain(l, p);
    }

    /** the driver's display: speed, ATP target / limit, distance to go, the planning area and the systems line */
    private static void screen(Minecraft mc, Desk d, boolean close, long now) {
        double u0 = -0.185, u1 = 0.17, v0 = -0.15, v1 = 0.13;
        Prims.box(u0 - 0.015, v0 - 0.015, 0, u1 + 0.015, v1 + 0.015, 0.012, 0x212121);
        bright();
        Prims.box(u0, v0, 0.012, u1, v1, 0.013, 0x06121C);
        if (!close) { lit(); return; }
        Locomotive l = d.l;
        PacketCab p = mine(l) ? CabDisplay.current() : null;
        float spd = (float) (l.getCurrentSpeed() == null ? 0 : Math.abs(l.getCurrentSpeed().metric()));
        double z = 0.0135;
        text(p != null && !p.mode.isEmpty() ? p.mode : "NO ATP DATA", u0 + 0.008, v1 - 0.012, z, 0.011, p != null ? BLUE : 0x607D8B, false);
        int sc = p == null || p.target < 0 ? WHITE : spd > p.target + 5 ? 0xFF5252 : spd > p.target ? AMBER : WHITE;
        text(String.valueOf(Math.round(spd)), u0 + 0.10, v1 - 0.07, z, 0.045, sc, true);
        text("km/h", u0 + 0.10, v1 - 0.105, z, 0.01, 0x90A4AE, true);
        if (p != null) {
            if (p.target >= 0) text("TGT " + Math.round(p.target), u1 - 0.008 - 0.075, v1 - 0.035, z, 0.013, AMBER, false);
            if (p.limit > 0 && p.limit < 999) text("LIM " + Math.round(p.limit), u1 - 0.008 - 0.075, v1 - 0.055, z, 0.011, WHITE, false);
            // distance to go: a bar (0-1000 m, log) on the left edge
            if (p.distanceToGo >= 0) {
                double f = Math.min(1, Math.log10(1 + p.distanceToGo) / 3.0);
                Prims.box(u0 + 0.006, v0 + 0.03, 0.0135, u0 + 0.018, v0 + 0.03 + (v1 - v0 - 0.06) * f, 0.014, p.distanceToGo < 100 ? 0xFF5252 : 0xB0BEC5);
                text(Math.round(p.distanceToGo) + "m", u0 + 0.022, v0 + 0.03, z, 0.009, WHITE, false);
            }
            if (!p.limitedBy.isEmpty()) text(clip(p.limitedBy, 30), u0 + 0.022, v0 + 0.015, z, 0.008, 0x90A4AE, false);
            // planning area: the next three things on the route
            double pv = v1 - 0.085;
            for (int i = 0; i < Math.min(3, p.planD.length); i++) {
                String what = p.planKind[i] == 1 ? "STOP" : p.planKind[i] == 2 ? "STN" : p.planKind[i] == 3 ? "NEUTRAL" : Math.round(p.planKmh[i]) + "";
                text(Math.round(p.planD[i]) + "m " + what, u1 - 0.083, pv - i * 0.018, z, 0.009, p.planKind[i] == 1 ? 0xFF8A80 : p.planKind[i] == 3 ? 0xE070F0 : 0xC5E1A5, false);
            }
            if (!p.nextStop.isEmpty()) text("NEXT " + clip(p.nextStop, 18) + (p.stopM >= 0 ? " " + Math.round(p.stopM) + "m" : ""), u0 + 0.022, v0 + 0.045, z, 0.009, 0xFFF59D, false);
            if (!p.advice.isEmpty()) text(clip(p.advice, 34), u0 + 0.022, v0 + 0.06, z, 0.008, 0x80CBC4, false);
            if (!p.fault.isEmpty() && now % 20 < 14) text("FLT " + clip(p.fault, 28), u0 + 0.022, v0 + 0.075, z, 0.008, 0xFF5252, false);
        }
        // systems line along the bottom: notch / brake / reverser
        String notch = d.steam ? "REG " + Math.round(l.getThrottle() * 100) + "%" : l.getThrottle() <= 0.001f ? "IDLE" : "N" + Math.round(l.getThrottle() * CabControls.NOTCHES);
        String rev = l.getReverser() > 0.05f ? "F" : l.getReverser() < -0.05f ? "R" : "N";
        text(notch + "  " + rev + "  BRK " + Math.round(l.getTrainBrakePos() * 100) + "%  IND " + Math.round(l.getIndependentBrake() * 100) + "%",
                u0 + 0.008, v0 + 0.004 + 0.002, z, 0.009, 0xB0BEC5, false);
        lit();
    }

    private static final String[] LAMP = {"VIG", "PEN", "AWS", "SAND", "SLIP", "FLT", "ISOL", "PAN", "HL", "BELL"};

    private static void lamps(Minecraft mc, Desk d, boolean close, long now) {
        Locomotive l = d.l;
        float a = l.getControlPosition(CabControls.ALERT);
        boolean[] on = {
                a > 0.25f && a < 0.75f && now % 10 < 6, a > 0.75f, l.getControlPosition(CabControls.AWS) > 0.5f,
                l.getControlPosition(CabControls.SANDL) > 0.5f, l.getControlPosition(CabControls.SLIP) > 0.5f, l.getControlPosition(CabControls.FLT) > 0.5f,
                l.getControlPosition(CabControls.ISOLATE) > 0.5f, l.getControlPosition(CabControls.PANST) > 0.75f || l.getControlPosition(CabControls.PANST) > 0.25f && now % 10 < 5,
                l.getControlPosition(CabControls.HEADLIGHT) > 0.25f, l.getControlPosition(CabControls.BELL) > 0.5f || l.getBell() > 0};
        int[] col = {YELLOW, RED, YELLOW, 0xE0C080, AMBER, RED, AMBER, GREEN, WHITE, AMBER};
        for (int i = 0; i < LAMP.length; i++) {
            if (i == 7 && !d.elec) continue;
            double u = 0.52 + (i % 2) * 0.085, v = 0.145 - (i / 2) * 0.07;
            Prims.disc(u, v, 0.002, 0.017, BLACK, 1);
            if (on[i]) { bright(); Prims.glow(u, v, 0.006, 0.03, col[i]); }
            Prims.disc(u, v, 0.005, 0.012, on[i] ? col[i] : Prims.shade(col[i], 0.25f), 1);
            lit();
            if (close) text(LAMP[i], u, v - 0.026, 0.004, 0.0085, 0xCFD8DC, true);
        }
    }

    private static String clip(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "."; }

    /** text on the current surface (u, v centre-left or centre) at height h metres, facing the driver */
    private static void text(String s, double u, double v, double w, double h, int rgb, boolean centre) {
        FontRenderer f = Minecraft.getMinecraft().fontRenderer;
        Prims.end();
        GlStateManager.pushMatrix();
        GlStateManager.translate(u, v, w + 0.0015);
        double sc = h / 8.0;
        GlStateManager.scale(sc, -sc, sc);
        GlStateManager.depthMask(false);
        f.drawString(s, centre ? -f.getStringWidth(s) / 2 : 0, -4, rgb);
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        Prims.begin();
    }
}
