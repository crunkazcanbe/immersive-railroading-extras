package com.dogpound.railmap.interior.client;

import cam72cam.immersiverailroading.entity.CarPassenger;
import cam72cam.immersiverailroading.entity.EntityRidableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.interior.Interior;
import com.dogpound.railmap.interior.InteriorConfig;
import com.dogpound.railmap.interior.InteriorRegistry;
import com.dogpound.railmap.interior.InteriorServer;
import com.dogpound.railmap.interior.ItemFittingKit;
import com.dogpound.railmap.interior.PacketInterior;
import com.dogpound.railmap.interior.PacketInteriorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client side of walk-in interiors: draws each fitted car's furniture, controls, live screens and the items on its
 * racks / tables / counter / fridge in the car's own (riding) frame; ray-casts the crosshair into it (hover label on
 * the action bar, outline) and turns a right-click into a server click, the café menu or the fitting-kit screen.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class InteriorClient {
    private InteriorClient() {}

    static final Map<Integer, PacketInteriorState> STATE = new HashMap<>();
    /** what the crosshair is on: entity id, part, control (-1 = the part), distance */
    static int hEnt = -1, hPart = -1, hBtn = -1;
    private static String lastLabel = "";
    private static final FloatBuffer SHEAR = BufferUtils.createFloatBuffer(16);

    public static void receive(PacketInteriorState m) {
        m.at = System.currentTimeMillis();
        STATE.put(m.entity, m);
        if (InteriorServer.CLIENT_LAYOUT == null) InteriorServer.CLIENT_LAYOUT = s -> { PacketInteriorState st = STATE.get(s.internal.getEntityId()); return st == null ? 0 : st.layout; };
    }

    public static PacketInteriorState state(int entity) { return STATE.get(entity); }

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        if (InteriorRegistry.kit != null) ModelLoader.setCustomModelResourceLocation(InteriorRegistry.kit, 0, new ModelResourceLocation(InteriorRegistry.kit.getRegistryName(), "inventory"));
    }

    static List<EntityRollingStock> stock() {
        Minecraft mc = Minecraft.getMinecraft();
        try { return cam72cam.mod.world.World.get(mc.world).getEntities(EntityRollingStock.class); } catch (RuntimeException | LinkageError e) { return new ArrayList<>(); }
    }

    // ---- hover --------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) { STATE.clear(); hEnt = -1; return; }
        long now = System.currentTimeMillis();
        for (Iterator<PacketInteriorState> it = STATE.values().iterator(); it.hasNext(); ) if (now - it.next().at > 12000) it.remove();
        hEnt = hPart = hBtn = -1;
        if (STATE.isEmpty() || mc.currentScreen != null) return;
        try { pick(mc); } catch (RuntimeException | LinkageError ex) { hEnt = -1; }
        String label = hEnt < 0 ? "" : label(hEnt, hPart, hBtn);
        if (!label.equals(lastLabel)) {
            lastLabel = label;
            if (!label.isEmpty()) mc.ingameGUI.setOverlayMessage(label, false);
        }
    }

    private static void pick(Minecraft mc) {
        Entity p = mc.player;
        Vec3d eye = p.getPositionEyes(1f), look = p.getLook(1f);
        double reach = mc.playerController == null ? 4.5 : mc.playerController.getBlockReachDistance();
        RayTraceResult vanilla = mc.objectMouseOver;
        if (vanilla != null && vanilla.typeOfHit == RayTraceResult.Type.BLOCK) reach = Math.min(reach, vanilla.hitVec.distanceTo(eye));
        double best = reach;
        for (EntityRollingStock s : stock()) {
            PacketInteriorState st = STATE.get(s.internal.getEntityId());
            if (st == null || st.layout == 0) continue;
            cam72cam.mod.math.Vec3d c = s.getPosition();
            double half = s.getDefinition().getLength(s.gauge) / 2 + reach;
            if (p.getDistanceSq(c.x, c.y, c.z) > half * half) continue;
            Interior.Plan plan = Interior.plan(s, Interior.Layout.of(st.layout));
            if (plan == null) continue;
            double[] o = Interior.toLocal(s, eye.x, eye.y, eye.z), d = Interior.dirLocal(s, look.x, look.y, look.z);
            double[] hit = Interior.pick(plan, o, d, best);
            if (hit == null) continue;
            best = hit[2];
            hEnt = s.internal.getEntityId(); hPart = (int) hit[0]; hBtn = (int) hit[1];
        }
    }

    private static String label(int ent, int part, int btn) {
        EntityRollingStock s = byId(ent);
        PacketInteriorState st = STATE.get(ent);
        if (s == null || st == null) return "";
        Interior.Plan plan = Interior.plan(s, Interior.Layout.of(st.layout));
        Interior.Part p = plan == null ? null : plan.part(part);
        if (p == null) return "";
        String head = "§b" + p.name;
        if (btn >= 0 && btn < p.btns.size()) return head + " §7» §f" + p.btns.get(btn).label;
        return head + switch (p.kind) {
            case SEAT, BERTH -> taken(s, p) ? " §c(taken)" : " §7» §fclick to " + (p.kind == Interior.Kind.BERTH ? "lie down / sleep" : "sit");
            case RACK, TABLE -> " §7» §fclick with an item to put it there, empty hand to take, sneak = all";
            case FRIDGE -> " §7» §fclick with an item to stock it; staff: open";
            case TILL -> " §7» §fclick for the menu";
            case COUNTER -> " §7» §fdisplay case (staff)";
            case TOILET -> " §7» §fFLUSH button / cord";
            case SINK -> " §7» §fwash hands; bottle / bucket fills, water bucket refills the tank";
            case KETTLE -> " §7» §fon / off";
            case PASSCOMM -> " §7» §cPULL IN EMERGENCY";
            default -> "";
        };
    }

    static boolean taken(EntityRollingStock s, Interior.Part p) {
        if (!(s instanceof EntityRidableRollingStock r) || p.sit == null) return false;
        try {
            for (String k : r.prideSeated().keySet()) {
                cam72cam.mod.math.Vec3d o = cam72cam.immersiverailroading.entity.EntityRidableRollingStock.prideSeatOffset(k);
                if (o != null && Math.abs(o.x - p.sit[0]) < 0.05 && Math.abs(o.z - p.sit[2]) < 0.05 && Math.abs(o.y - p.sit[1]) < 0.3) return true;
            }
        } catch (LinkageError ignored) { }
        return false;
    }

    static EntityRollingStock byId(int id) {
        for (EntityRollingStock s : stock()) if (s.internal.getEntityId() == id) return s;
        return null;
    }

    // ---- clicks -------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void mouse(MouseEvent e) {
        if (e.getButton() != 1 || !e.isButtonstate()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.currentScreen != null) return;
        if (mc.player.getHeldItemMainhand().getItem() instanceof ItemFittingKit) {
            EntityRollingStock car = nearCar(mc);
            if (car == null) { mc.player.sendStatusMessage(new TextComponentString("§7Stand in or beside an IR passenger car to fit it out"), true); e.setCanceled(true); return; }
            PacketInteriorState st = STATE.get(car.internal.getEntityId());
            mc.displayGuiScreen(new GuiFitting(car.internal.getEntityId(), name(car), st == null ? 0 : st.layout, InteriorServer.fittable(car)));
            e.setCanceled(true);
            return;
        }
        if (hEnt < 0) return;
        e.setCanceled(true);
        EntityRollingStock s = byId(hEnt);
        PacketInteriorState st = STATE.get(hEnt);
        if (s != null && st != null) {
            Interior.Plan plan = Interior.plan(s, Interior.Layout.of(st.layout));
            Interior.Part p = plan == null ? null : plan.part(hPart);
            if (p != null && p.kind == Interior.Kind.TILL && !(hBtn >= 0 && p.btns.get(hBtn).id.equals("cashup"))) {
                mc.displayGuiScreen(new GuiCafeMenu(hEnt, name(s)));
                return;
            }
        }
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        RailMap.NETWORK.sendToServer(new PacketInterior(PacketInterior.CLICK, hEnt, hPart, hBtn, 0));
    }

    static String name(EntityRollingStock s) { try { return s.getDefinition().name(); } catch (RuntimeException e) { return "car"; } }

    /** the passenger car the player is in, else the nearest one within 6 blocks of its body */
    static EntityRollingStock nearCar(Minecraft mc) {
        EntityRollingStock best = null;
        double bd = Double.MAX_VALUE;
        for (EntityRollingStock s : stock()) {
            if (!(s instanceof CarPassenger) || s.getDefinition() == null) continue;
            double[] lo = Interior.toLocal(s, mc.player.posX, mc.player.posY, mc.player.posZ);
            double half = s.getDefinition().getLength(s.gauge) / 2, hw = s.getDefinition().getWidth(s.gauge) / 2;
            double dz = Math.max(0, Math.abs(lo[2]) - half), dx = Math.max(0, Math.abs(lo[0]) - hw);
            double d = dz * dz + dx * dx;
            if (d > 36 || lo[1] < -3 || lo[1] > 6) continue;
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void render(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || STATE.isEmpty()) return;
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;
        float pt = e.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt, vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt, vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        List<Object[]> cars = new ArrayList<>();
        double r2 = (double) InteriorConfig.renderRange * InteriorConfig.renderRange;
        for (EntityRollingStock s : stock()) {
            PacketInteriorState st = STATE.get(s.internal.getEntityId());
            if (st == null || st.layout == 0) continue;
            Entity in = s.internal;
            double d = (in.posX - vx) * (in.posX - vx) + (in.posY - vy) * (in.posY - vy) + (in.posZ - vz) * (in.posZ - vz);
            if (d > r2) continue;
            cars.add(new Object[]{s, st, d});
        }
        if (cars.isEmpty()) return;
        cars.sort((a, b) -> Double.compare((Double) a[2], (Double) b[2]));
        GlStateManager.pushMatrix();
        try {
            for (int i = 0; i < Math.min(cars.size(), InteriorConfig.maxCars); i++) {
                EntityRollingStock s = (EntityRollingStock) cars.get(i)[0];
                PacketInteriorState st = (PacketInteriorState) cars.get(i)[1];
                Interior.Plan plan = Interior.plan(s, Interior.Layout.of(st.layout));
                if (plan == null) continue;
                Entity in = s.internal;
                double x = in.lastTickPosX + (in.posX - in.lastTickPosX) * pt, y = in.lastTickPosY + (in.posY - in.lastTickPosY) * pt, z = in.lastTickPosZ + (in.posZ - in.lastTickPosZ) * pt;
                GlStateManager.pushMatrix();
                GlStateManager.translate(x - vx, y - vy, z - vz);
                GlStateManager.rotate(-s.getRotationYaw(), 0, 1, 0);
                shear(s.getRotationPitch());
                drawCar(mc, s, st, plan, (Double) cars.get(i)[2], new BlockPos(x, y + 1.5, z));
                GlStateManager.popMatrix();
            }
        } catch (RuntimeException | LinkageError ex) {
            RailMap.LOG.debug("[IR Extras] interior render: {}", ex.toString());
        } finally {
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    /** IR lifts riders by sin(pitch) * z on a grade: do the same so the furniture stays under them */
    private static void shear(float pitch) {
        if (Math.abs(pitch) < 0.01f) return;
        SHEAR.clear();
        float[] m = {1, 0, 0, 0, 0, 1, 0, 0, 0, (float) Math.sin(Math.toRadians(pitch)), 1, 0, 0, 0, 0, 1};
        SHEAR.put(m).flip();
        GlStateManager.multMatrix(SHEAR);
    }

    private static void drawCar(Minecraft mc, EntityRollingStock s, PacketInteriorState st, Interior.Plan plan, double dist2, BlockPos at) {
        float bright;
        if (st.lit) bright = 1f;
        else {
            int l = mc.world.getCombinedLight(at, 0);
            float sky = ((l >> 20) & 15) * mc.world.getSunBrightness(1f), block = (l >> 4) & 15;
            bright = Math.max(0.14f, Math.max(sky, block) / 15f);
        }
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.enableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (Interior.Part p : plan.parts) {
            for (int i = 0; i < p.boxes.size(); i++) {
                double[] bx = p.boxes.get(i);
                String g = p.glow.get(i);
                if (g != null) box(b, bx, (int) bx[6], on(st, g) ? 1f : bright * 0.35f, false);
                else box(b, bx, (int) bx[6], bright, true);
            }
            for (Interior.Btn c : p.btns) {
                boolean on = c.state != null && on(st, c.state);
                int col = c.color;
                switch (c.type) {
                    case Interior.LAMP -> box(b, c.box, col, on ? 1f : bright * 0.3f, !on);
                    case Interior.PUSH -> box(b, c.box, on ? brighter(col) : col, on ? 1f : bright, !on);
                    case Interior.KEY -> { box(b, c.box, col, bright, true); keyMark(b, c, st); }
                    case Interior.HANDLE -> box(b, c.box, col, Math.max(bright, 0.6f), true);
                    default -> box(b, c.box, col, st.powered ? 1f : bright * 0.5f, false);
                }
            }
        }
        t.draw();
        // hover outline
        if (s.internal.getEntityId() == hEnt) {
            Interior.Part hp = plan.part(hPart);
            if (hp != null) {
                double[] h = hBtn >= 0 && hBtn < hp.btns.size() ? hp.btns.get(hBtn).box : hp.hit;
                if (h != null) {
                    GlStateManager.glLineWidth(2f);
                    RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(h[0], h[1], h[2], h[3], h[4], h[5]).grow(0.006), 1f, 0.85f, 0.2f, 0.9f);
                }
            }
        }
        // screens + digits text (only when powered; near enough to read)
        GlStateManager.enableTexture2D();
        if (st.powered && dist2 < 28 * 28) {
            FontRenderer f = mc.fontRenderer;
            for (Interior.Part p : plan.parts) for (Interior.Btn c : p.btns) {
                if (c.type != Interior.SCREEN && c.type != Interior.DIGITS) continue;
                String txt = st.txt.get(c.state);
                if (txt == null || txt.isEmpty()) continue;
                text(f, c, txt, colourFor(c.state, txt, c.type));
            }
        }
        // items on racks / tables / counter / in the fridge
        if (!st.items.isEmpty() && dist2 < (double) InteriorConfig.itemRange * InteriorConfig.itemRange) {
            RenderHelper.enableStandardItemLighting();
            GlStateManager.enableLighting();
            int l = st.lit ? 0xF000F0 : mc.world.getCombinedLight(at, 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
            float sc = (float) (0.3 * plan.g);
            for (Map.Entry<Integer, List<ItemStack>> e : st.items.entrySet()) {
                Interior.Part p = plan.part(e.getKey());
                if (p == null) continue;
                for (int i = 0; i < e.getValue().size() && i < p.spots.size(); i++) {
                    double[] sp = p.spots.get(i);
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(sp[0], sp[1] + sc * 0.5, sp[2]);
                    GlStateManager.rotate(p.face == 2 ? -90 : p.face == 3 ? 90 : p.face == 1 ? 180 : 0, 0, 1, 0);
                    GlStateManager.scale(sc, sc, sc);
                    mc.getRenderItem().renderItem(e.getValue().get(i), ItemCameraTransforms.TransformType.FIXED);
                    GlStateManager.popMatrix();
                }
            }
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
        }
    }

    static boolean on(PacketInteriorState st, String key) { Integer v = st.vals.get(key); return v != null && v > 0; }

    private static int colourFor(String key, String txt, int type) {
        if ("wcsign".equals(key)) return txt.startsWith("VACANT") ? 0xFF40E060 : 0xFFFF4030;
        if (key != null && key.startsWith("seat")) return txt.endsWith("FREE") ? 0xFF40E060 : 0xFFFFB030;
        if ("crew".equals(key)) return 0xFF7FFFD0;
        return type == Interior.DIGITS ? 0xFFFF8030 : 0xFFF5F0E0;
    }

    private static int brighter(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 70), g = Math.min(255, ((c >> 8) & 255) + 70), bl = Math.min(255, (c & 255) + 70);
        return (r << 16) | (g << 8) | bl;
    }

    /** a box with simple directional shading (top bright, sides dimmer) */
    private static void box(BufferBuilder b, double[] x, int rgb, float k, boolean shade) {
        float r = ((rgb >> 16) & 255) / 255f * k, g = ((rgb >> 8) & 255) / 255f * k, bl = (rgb & 255) / 255f * k;
        double x0 = x[0], y0 = x[1], z0 = x[2], x1 = x[3], y1 = x[4], z1 = x[5];
        float top = 1f, bot = shade ? 0.55f : 1f, zs = shade ? 0.85f : 1f, xs = shade ? 0.7f : 1f;
        quad(b, r * top, g * top, bl * top, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(b, r * bot, g * bot, bl * bot, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(b, r * zs, g * zs, bl * zs, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(b, r * zs, g * zs, bl * zs, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(b, r * xs, g * xs, bl * xs, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(b, r * xs, g * xs, bl * xs, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(BufferBuilder b, float r, float g, float bl, double... v) {
        for (int i = 0; i < 12; i += 3) b.pos(v[i], v[i + 1], v[i + 2]).color(Math.min(1, r), Math.min(1, g), Math.min(1, bl), 1f).endVertex();
    }

    /** the key / selector's pointer: a white bar whose angle shows the position (off / on / mode) */
    private static void keyMark(BufferBuilder b, Interior.Btn c, PacketInteriorState st) {
        Integer v = c.state == null ? null : st.vals.get(c.state);
        int pos = v == null ? 0 : v;
        double[] x = c.box;
        double cx = (x[0] + x[3]) / 2, cy = (x[1] + x[4]) / 2, cz = (x[2] + x[5]) / 2;
        double s = Math.min(Math.min(x[3] - x[0], x[4] - x[1]), x[5] - x[2]) < 0.001 ? 0.01 : 0.008;
        double len = Math.max(x[3] - x[0], Math.max(x[4] - x[1], x[5] - x[2])) * 0.35;
        double ang = Math.toRadians(-45 + pos * 45);
        double dx = Math.sin(ang) * len, dy = Math.cos(ang) * len;
        boolean flatZ = c.face == 0 || c.face == 1, flatX = c.face == 2 || c.face == 3;
        double out = c.face == 0 ? x[5] : c.face == 1 ? x[2] : c.face == 2 ? x[3] : c.face == 3 ? x[0] : x[1];
        double[] mk = flatZ ? new double[]{cx + dx - s, cy + dy - s, out - s, cx + dx + s, cy + dy + s, out + s}
                : flatX ? new double[]{out - s, cy + dy - s, cz + dx - s, out + s, cy + dy + s, cz + dx + s}
                : new double[]{cx + dx - s, out - s, cz + dy - s, cx + dx + s, out + s, cz + dy + s};
        box(b, mk, 0xFFFFFF, 1f, false);
    }

    /** lines of text filling a screen control, read from the side it faces */
    private static void text(FontRenderer f, Interior.Btn c, String txt, int argb) {
        double[] x = c.box;
        double w, h;
        GlStateManager.pushMatrix();
        switch (c.face) {
            case 0 -> { GlStateManager.translate(x[0], x[4], x[5] + 0.002); w = x[3] - x[0]; h = x[4] - x[1]; }
            case 1 -> { GlStateManager.translate(x[3], x[4], x[2] - 0.002); GlStateManager.rotate(180, 0, 1, 0); w = x[3] - x[0]; h = x[4] - x[1]; }
            case 2 -> { GlStateManager.translate(x[3] + 0.002, x[4], x[5]); GlStateManager.rotate(90, 0, 1, 0); w = x[5] - x[2]; h = x[4] - x[1]; }
            case 3 -> { GlStateManager.translate(x[0] - 0.002, x[4], x[2]); GlStateManager.rotate(-90, 0, 1, 0); w = x[5] - x[2]; h = x[4] - x[1]; }
            default -> { GlStateManager.translate(x[0], x[1] - 0.002, x[5]); GlStateManager.rotate(90, 1, 0, 0); w = x[3] - x[0]; h = x[5] - x[2]; }
        }
        String[] lines = txt.split("\n");
        int n = Math.max(c.lines, Math.min(lines.length, 8));
        float lh = (float) (h / n), k = lh / 10f;
        GlStateManager.scale(k, -k, k);
        int maxW = (int) (w / k) - 2;
        for (int i = 0; i < Math.min(n, lines.length); i++) {
            String ln = lines[i];
            if (f.getStringWidth(ln) > maxW) {
                // long line: scroll it like an LED board
                String loop = ln + "    ";
                int off = (int) ((System.currentTimeMillis() / 180) % loop.length());
                ln = f.trimStringToWidth((loop + loop).substring(off), maxW);
            }
            int tw = f.getStringWidth(ln);
            f.drawString(ln, Math.max(1, (maxW - tw) / 2 + 1), i * 10 + 1, i == 0 && n > 1 ? argb : (argb & 0xFF000000) | 0xE8E8E8, false);
        }
        GlStateManager.popMatrix();
    }

}
