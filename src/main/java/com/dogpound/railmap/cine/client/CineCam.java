package com.dogpound.railmap.cine.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.cctv.client.CctvFeeds;
import com.dogpound.railmap.cctv.client.EntityCctvView;
import com.dogpound.railmap.cine.CineConfig;
import com.dogpound.railmap.cine.CineMath;
import com.dogpound.railmap.cine.CineTrains;
import com.dogpound.railmap.cine.TileDirectorDesk;
import cam72cam.immersiverailroading.entity.Locomotive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Photo mode / the cinematic train camera (batch 5 #10). F6 turns it on: your screen is drawn from an invisible camera
 * (the CCTV view entity) - your player stays exactly where it is, its movement keys and mouse clicks go to the camera
 * instead, and its head is put back each frame. Spectator-style, client-side only; on servers the free camera can't pass
 * through blocks (config) and never strays further than CineConfig.maxDistance.
 * <pre>
 *  1 FREE   2 CHASE   3 SIDE   4 CAB   5 HELI ORBIT   6 FLYBY   7 TRACKSIDE (auto-cut tripods)   8 PATH   9 REPLAY
 *  WASD / Space / Shift fly (Ctrl fast) · mouse looks (free) · wheel zooms · arrows: train (replay: < > ghost, ^ v seek)
 *  R add waypoint · P play / stop the path · inventory key: the photo-mode panel · F1 hide HUD · F2 screenshot · F6 leave
 * </pre>
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class CineCam {
    private CineCam() {}

    public static final KeyBinding KEY = new KeyBinding("key.irextras.photomode", Keyboard.KEY_F6, "key.categories.irextras");
    private static boolean keyRegistered;

    public static boolean active;
    public static int mode = CineMath.FREE;
    public static double x, y, z, fov = 70, zoom = 60;
    public static float yaw, pitch, roll;
    public static String train = "";
    public static String trainName = "";
    public static int letterbox, dof;
    public static double pathSecs = 12;
    public static final List<double[]> WAYPOINTS = new ArrayList<>();
    public static boolean pathPlaying;
    private static long pathStart;
    /** watching a director desk's program output */
    public static BlockPos desk;

    private static EntityCctvView view;
    private static float savedYaw, savedPitch;
    private static int savedThird;
    private static float inF, inS;
    private static boolean inUp, inDown, inFast;
    private static long lastNanos;
    static String message = "";
    static long messageAt;

    public static void registerKey() {
        if (keyRegistered) return;
        keyRegistered = true;
        ClientRegistry.registerKeyBinding(KEY);
    }

    // ---- on / off ------------------------------------------------------------------------------------------------------
    public static void toggle() { if (active) exit(); else enter(); }

    public static void enter() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!CineConfig.photoMode || mc.world == null || mc.player == null) return;
        Entity p = mc.player;
        x = p.posX; y = p.posY + p.getEyeHeight(); z = p.posZ;
        yaw = p.rotationYaw; pitch = p.rotationPitch; roll = 0;
        savedYaw = p.rotationYaw; savedPitch = p.rotationPitch;
        savedThird = mc.gameSettings.thirdPersonView;
        view = new EntityCctvView(mc.world);
        view.place(x, y, z, yaw, pitch);
        active = true;
        lastNanos = 0;
        if (train.isEmpty()) nearestTrain(0);
        say("PHOTO MODE - 1-9 views, E panel, F6 leave");
    }

    public static void exit() {
        Minecraft mc = Minecraft.getMinecraft();
        active = false;
        desk = null;
        pathPlaying = false;
        if (mc.player != null) {
            mc.setRenderViewEntity(mc.player);
            mc.player.rotationYaw = mc.player.prevRotationYaw = savedYaw;
            mc.player.rotationPitch = mc.player.prevRotationPitch = savedPitch;
        }
        mc.gameSettings.thirdPersonView = savedThird;
        view = null;
    }

    public static void watchDesk(BlockPos p) {
        if (!active) enter();
        if (!active) return;
        desk = p;
        mode = -1;
        say("WATCHING THE DIRECTOR DESK PROGRAM - F6 to leave");
    }

    public static void setMode(int m) {
        mode = m;
        desk = null;
        CinePoses.forget("photo");
        if (m == CineMath.REPLAY) CineReplay.request();
        if (m == CineMath.PATH && WAYPOINTS.size() >= 2) playPath();
        say(CineMath.name(m));
    }

    static void say(String s) { message = s; messageAt = System.currentTimeMillis(); }

    /** pick the next / previous locomotive by distance from the camera */
    public static void nearestTrain(int dir) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;
        List<Locomotive> near = CineTrains.near(mc.world, x, z, 512);
        if (near.isEmpty()) { train = ""; trainName = ""; say("NO TRAIN NEAR"); return; }
        int at = -1;
        for (int i = 0; i < near.size(); i++) if (near.get(i).getUUID().toString().equals(train)) at = i;
        at = dir == 0 ? 0 : Math.floorMod(at + dir, near.size());
        train = near.get(at).getUUID().toString();
        trainName = CineTrains.name(near.get(at));
        CinePoses.forget("photo");
        say("SUBJECT: " + trainName);
    }

    public static void addWaypoint() {
        WAYPOINTS.add(new double[]{x, y, z, yaw, pitch, zoom});
        say("WAYPOINT " + WAYPOINTS.size());
    }

    public static void playPath() {
        if (WAYPOINTS.size() < 2) { say("NEED 2+ WAYPOINTS (R)"); return; }
        mode = CineMath.PATH;
        pathPlaying = true;
        pathStart = System.nanoTime();
        say("PLAYING PATH " + Math.round(pathSecs) + " S");
    }

    // ---- the camera, every frame ------------------------------------------------------------------------------------------
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void frame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!active) return;
        if (mc.world == null || mc.player == null || !CineConfig.photoMode) { exit(); return; }
        if (view == null || view.world != mc.world) view = new EntityCctvView(mc.world);
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        // the mouse turned the player's head: give the turn to the camera and put the head back
        float dy = mc.player.rotationYaw - savedYaw, dp = mc.player.rotationPitch - savedPitch;
        mc.player.rotationYaw = mc.player.prevRotationYaw = savedYaw;
        mc.player.rotationPitch = mc.player.prevRotationPitch = savedPitch;
        float pt = e.renderTickTime;
        double[] p = null;
        if (desk != null) {
            if (mc.world.getTileEntity(desk) instanceof TileDirectorDesk d) {
                p = CineDeskView.sourcePose(d, d.program, "watch", pt);
                letterbox = d.letterbox();
            } else { say("DESK GONE"); desk = null; mode = CineMath.FREE; }
        } else if (mode == CineMath.FREE || mode == CineMath.PATH && !pathPlaying) {
            yaw += dy;
            pitch = Math.max(-90, Math.min(90, pitch + dp));
            fly(mc, dt);
            fov = zoom;
            roll = 0;
        } else if (mode == CineMath.PATH) {
            double t = (now - pathStart) / 1e9 / Math.max(1, pathSecs);
            double[] q = CineMath.path(WAYPOINTS, t);
            if (t >= 1) { pathPlaying = false; say("PATH DONE"); }
            if (q != null) p = new double[]{q[0], q[1], q[2], q[3], q[4], q[5], 0};
        } else {
            double[] subject;
            if (mode == CineMath.REPLAY) { CineReplay.advance(dt); subject = CineReplay.subject(); }
            else subject = CinePoses.live(train, pt);
            int src = mode == CineMath.REPLAY ? replayView : mode;
            p = CinePoses.pose("photo", src, subject, train, -1, zoom, pt);
            if (p == null && mode != CineMath.REPLAY) { yaw += dy; pitch = Math.max(-90, Math.min(90, pitch + dp)); fly(mc, dt); fov = zoom; }
        }
        if (p != null) { x = p[0]; y = p[1]; z = p[2]; yaw = (float) p[3]; pitch = (float) p[4]; fov = p[5]; roll = (float) p[6]; }
        clampToPlayer(mc);
        view.place(x, y, z, yaw, pitch);
        if (mc.getRenderViewEntity() != view && !CctvFeeds.rendering) mc.setRenderViewEntity(view);
        mc.gameSettings.thirdPersonView = 0;
    }

    /** the view used inside REPLAY: CHASE / ORBIT / FLYBY / SIDE / AUTO (tripods) */
    public static int replayView = CineMath.CHASE;

    static void fly(Minecraft mc, double dt) {
        double sp = CineConfig.flySpeed * (inFast ? 4 : 1) * dt;
        double yr = Math.toRadians(yaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr);
        // as Entity.moveRelative: strafe + is left
        double mx = (fx * inF + fz * inS) * sp, mz = (fz * inF - fx * inS) * sp, my = ((inUp ? 1 : 0) - (inDown ? 1 : 0)) * sp;
        boolean collide = CineConfig.collideOnServers && !mc.isSingleplayer();
        if (!collide || free(mc, x + mx, y, z)) x += mx;
        if (!collide || free(mc, x, y + my, z)) y += my;
        if (!collide || free(mc, x, y, z + mz)) z += mz;
    }

    static boolean free(Minecraft mc, double px, double py, double pz) {
        BlockPos b = new BlockPos(px, py, pz);
        return mc.world.getBlockState(b).getCollisionBoundingBox(mc.world, b) == null;
    }

    static void clampToPlayer(Minecraft mc) {
        double dx = x - mc.player.posX, dy = y - mc.player.posY, dz = z - mc.player.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz), max = CineConfig.maxDistance;
        if (d > max) { x = mc.player.posX + dx / d * max; y = mc.player.posY + dy / d * max; z = mc.player.posZ + dz / d * max; }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        registerKey();
        if (active && mode == CineMath.REPLAY) CineReplay.tick();
        Minecraft mc = Minecraft.getMinecraft();
        if (active && mc.currentScreen == null) {                              // no clicking blocks from the camera
            while (mc.gameSettings.keyBindAttack.isPressed()) { }
            while (mc.gameSettings.keyBindUseItem.isPressed()) { }
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }
    }

    /** the player's movement keys drive the camera; the player stands still */
    @SubscribeEvent
    public static void input(InputUpdateEvent e) {
        if (!active) return;
        net.minecraft.util.MovementInput m = e.getMovementInput();
        inF = m.moveForward; inS = m.moveStrafe; inUp = m.jump; inDown = m.sneak;
        inFast = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
        m.moveForward = 0; m.moveStrafe = 0; m.jump = false; m.sneak = false;
        m.forwardKeyDown = m.backKeyDown = m.leftKeyDown = m.rightKeyDown = false;
    }

    @SubscribeEvent
    public static void mouse(MouseEvent e) {
        if (!active || Minecraft.getMinecraft().currentScreen != null) return;
        if (e.getDwheel() != 0) {
            zoom = Math.max(5, Math.min(110, zoom - Math.signum(e.getDwheel()) * (zoom > 30 ? 5 : 2)));
            e.setCanceled(true);
        }
        if (e.getButton() >= 0 && e.isButtonstate()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void keys(InputEvent.KeyInputEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (KEY.isPressed()) { toggle(); return; }
        if (!active || !Keyboard.getEventKeyState()) return;
        int k = Keyboard.getEventKey();
        for (KeyBinding hb : mc.gameSettings.keyBindsHotbar) while (hb.isPressed()) { }
        if (k >= Keyboard.KEY_1 && k <= Keyboard.KEY_9) {
            int[] map = {CineMath.FREE, CineMath.CHASE, CineMath.SIDE, CineMath.CAB, CineMath.ORBIT, CineMath.FLYBY, CineMath.AUTO, CineMath.PATH, CineMath.REPLAY};
            setMode(map[k - Keyboard.KEY_1]);
        } else if (k == Keyboard.KEY_R) addWaypoint();
        else if (k == Keyboard.KEY_P) { if (pathPlaying) { pathPlaying = false; say("PATH STOPPED"); } else playPath(); }
        else if (k == Keyboard.KEY_RIGHT) { if (mode == CineMath.REPLAY) CineReplay.cycle(1); else nearestTrain(1); }
        else if (k == Keyboard.KEY_LEFT) { if (mode == CineMath.REPLAY) CineReplay.cycle(-1); else nearestTrain(-1); }
        else if (k == Keyboard.KEY_UP && mode == CineMath.REPLAY) CineReplay.seek(15);
        else if (k == Keyboard.KEY_DOWN && mode == CineMath.REPLAY) CineReplay.seek(-15);
        if (mc.gameSettings.keyBindInventory.isPressed()) {
            while (mc.gameSettings.keyBindInventory.isPressed()) { }
            mc.displayGuiScreen(new GuiPhotoMode());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fovEvent(EntityViewRenderEvent.FOVModifier e) {
        if (active && !CctvFeeds.rendering && Minecraft.getMinecraft().getRenderViewEntity() == view) e.setFOV((float) fov);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void camera(EntityViewRenderEvent.CameraSetup e) {
        if (active && !CctvFeeds.rendering && Minecraft.getMinecraft().getRenderViewEntity() == view) e.setRoll(roll);
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent e) { if (active && !CctvFeeds.rendering) e.setCanceled(true); }

    @SubscribeEvent
    public static void hud(RenderGameOverlayEvent.Pre e) {
        if (!active) return;
        if (e.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS || e.getType() == RenderGameOverlayEvent.ElementType.HOTBAR) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void hudText(RenderGameOverlayEvent.Post e) {
        if (!active || e.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        String head = desk != null ? "DIRECTOR PROGRAM" : CineMath.name(mode);
        String sub = mode == CineMath.REPLAY ? CineReplay.status() + " · " + CineMath.name(replayView) + " · " + CineReplay.name(CineReplay.follow)
                : trainName.isEmpty() ? "NO SUBJECT (arrows pick a train)" : "SUBJECT " + trainName;
        int bar = CineMath.bar(e.getResolution().getScaledWidth(), e.getResolution().getScaledHeight(), CineMath.LETTERBOX[letterbox]);
        int yy = bar + 4;
        mc.fontRenderer.drawStringWithShadow("§c● §f" + head + String.format(Locale.ROOT, " §7FOV %.0f", fov), 6, yy, 0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow("§7" + sub, 6, yy + 10, 0xFFFFFF);
        if (System.currentTimeMillis() - messageAt < 2500) mc.fontRenderer.drawStringWithShadow("§e" + message, 6, yy + 22, 0xFFFFFF);
        if (!WAYPOINTS.isEmpty()) mc.fontRenderer.drawStringWithShadow("§7path: " + WAYPOINTS.size() + " waypoints, " + Math.round(pathSecs) + " s" + (pathPlaying ? " §aPLAYING" : ""), 6, yy + 34, 0xFFFFFF);
    }

    public static void message(String s) { Minecraft.getMinecraft().player.sendStatusMessage(new TextComponentString(s), true); }
}
