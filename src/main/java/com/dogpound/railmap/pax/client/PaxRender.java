package com.dogpound.railmap.pax.client;

import com.dogpound.railmap.pax.EntityCrew;
import com.dogpound.railmap.pax.EntityPax;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Draws visible passengers: a player-shaped model wearing one of our own skins, painted in code (skin tone, hair and
 * style, face, outfit: shirt / striped / rainbow / suit and tie / hoodie / dress / long coat, trousers or shorts,
 * shoes, sometimes a cap) - 48 looks x wide or slim arms - plus what they carry, modelled and flat-coloured: wheeled
 * suitcase, briefcase, backpack, shoulder bag, or a wheelchair. Children are drawn small. No name tags. Looking at
 * someone (up to 16 blocks) shows a small tooltip: where they're going and what they're doing.
 */
public class PaxRender extends Render<EntityPax> {
    private final PoseModel wide = new PoseModel(false), slim = new PoseModel(true);
    private final ModelBase acc = new ModelBase() { };
    private final ModelRenderer suitcase, briefcase, backpack, bag, strapF, strapB, chairFrame, chairSeat, wheel, wheel45;
    // crew kit (batch 5 #7 #9): peaked cap, guard's right-away paddle + whistle, inspector's ticket machine + ticket
    private final ModelRenderer capCrown, capBand, capVisor, capBadge, paddlePole, paddleDisc, paddleRim, whistle, machine, machineScreen, ticket;

    /** the player model with the crew's working arm poses: 1 right-away (arm up), 2 whistle, 3 checking a ticket, 5 a mug of tea */
    static final class PoseModel extends ModelPlayer {
        int pose;
        PoseModel(boolean slimArms) { super(0f, slimArms); }

        @Override
        public void setRotationAngles(float a, float b, float c, float yaw, float pitch, float sc, Entity e) {
            super.setRotationAngles(a, b, c, yaw, pitch, sc, e);
            switch (pose) {
                case 1 -> { bipedRightArm.rotateAngleX = -2.95f; bipedRightArm.rotateAngleY = 0f; bipedRightArm.rotateAngleZ = -0.1f; }
                case 2 -> { bipedRightArm.rotateAngleX = -1.95f; bipedRightArm.rotateAngleY = -0.6f; bipedRightArm.rotateAngleZ = 0f; }
                case 3 -> { bipedRightArm.rotateAngleX = -1.05f; bipedRightArm.rotateAngleY = -0.25f; bipedLeftArm.rotateAngleX = -0.85f; bipedLeftArm.rotateAngleY = 0.35f; }
                case 5 -> { bipedRightArm.rotateAngleX = -1.35f; bipedRightArm.rotateAngleY = -0.35f; }
                default -> { }
            }
            copyModelAngles(bipedRightArm, bipedRightArmwear);
            copyModelAngles(bipedLeftArm, bipedLeftArmwear);
        }
    }

    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityPax.class, PaxRender::new);
        MinecraftForge.EVENT_BUS.register(PaxRender.class);
    }

    public PaxRender(RenderManager rm) {
        super(rm);
        shadowSize = 0.4f;
        acc.textureWidth = 64; acc.textureHeight = 64;
        suitcase = part(); suitcase.addBox(-2.5f, 11f, -5f, 3, 8, 10); suitcase.addBox(-1.5f, 10f, -1.5f, 1, 1, 3); suitcase.addBox(-2.5f, 19f, 3f, 3, 1, 1);
        briefcase = part(); briefcase.addBox(-2f, 11f, -4f, 2, 6, 8); briefcase.addBox(-1.5f, 10f, -1f, 1, 1, 2);
        backpack = part(); backpack.addBox(-3.5f, 1f, 2f, 7, 9, 3); backpack.addBox(-2.5f, 6f, 5f, 5, 3, 1); backpack.addBox(-3f, 0f, -2.6f, 1, 6, 1); backpack.addBox(2f, 0f, -2.6f, 1, 6, 1);
        bag = part(); bag.addBox(4f, 8f, -2.5f, 2, 5, 5);
        strapF = part(); strapF.setRotationPoint(-3f, 0f, 0f); strapF.rotateAngleZ = -0.66f; strapF.addBox(-0.5f, 0f, -2.6f, 1, 12, 1);
        strapB = part(); strapB.setRotationPoint(-3f, 0f, 0f); strapB.rotateAngleZ = -0.66f; strapB.addBox(-0.5f, 0f, 1.8f, 1, 12, 1);
        chairFrame = part();
        chairFrame.addBox(-4.5f, 2f, 3f, 1, 1, 3); chairFrame.addBox(3.5f, 2f, 3f, 1, 1, 3);            // push handles
        chairFrame.addBox(-5.5f, 9f, -3f, 1, 1, 6); chairFrame.addBox(4.5f, 9f, -3f, 1, 1, 6);          // armrests
        chairFrame.addBox(-5.5f, 10f, -4f, 1, 5, 1); chairFrame.addBox(4.5f, 10f, -4f, 1, 5, 1);
        chairFrame.addBox(-3.5f, 15f, -11f, 1, 6, 1); chairFrame.addBox(2.5f, 15f, -11f, 1, 6, 1);      // footrest legs
        chairFrame.addBox(-3.5f, 21f, -12f, 7, 1, 3);                                                   // footplate
        chairFrame.addBox(-4.5f, 21f, -10f, 1, 3, 3); chairFrame.addBox(3.5f, 21f, -10f, 1, 3, 3);      // casters
        chairFrame.addBox(-4.5f, 15f, -4f, 9, 1, 1);
        chairSeat = part(); chairSeat.addBox(-4.5f, 14f, -4f, 9, 1, 8); chairSeat.addBox(-4.5f, 3f, 3f, 9, 11, 1);
        wheel = part(); wheel.setRotationPoint(0f, 17f, 1f);
        wheel45 = part(); wheel45.setRotationPoint(0f, 17f, 1f); wheel45.rotateAngleX = (float) (Math.PI / 4);
        for (ModelRenderer r : new ModelRenderer[]{wheel, wheel45})
            for (float x : new float[]{-7f, 6f}) { r.addBox(x, -7f, -3f, 1, 14, 6); r.addBox(x, -3f, -7f, 1, 6, 14); }
        capCrown = part(); capCrown.addBox(-4.5f, -10f, -4.5f, 9, 2, 9);
        capBand = part(); capBand.addBox(-4.6f, -8.4f, -4.6f, 9, 1, 9);
        capVisor = part(); capVisor.addBox(-4f, -8f, -7.2f, 8, 1, 3);
        capBadge = part(); capBadge.addBox(-1f, -9.6f, -4.9f, 2, 2, 1);
        paddlePole = part(); paddlePole.addBox(-0.5f, 9f, -1.5f, 1, 7, 1);
        paddleDisc = part(); paddleDisc.addBox(-0.5f, 15f, -4f, 1, 5, 6);
        paddleRim = part(); paddleRim.addBox(-0.6f, 16f, -3f, 1, 3, 4);
        whistle = part(); whistle.addBox(-0.5f, -1.5f, -5.5f, 1, 1, 2);
        machine = part(); machine.addBox(-2.5f, 9f, -3.5f, 3, 2, 5);
        machineScreen = part(); machineScreen.addBox(-2.6f, 10.6f, -3f, 3, 1, 3);
        ticket = part(); ticket.addBox(0f, 10f, -3f, 1, 1, 3);
    }

    private ModelRenderer part() { return new ModelRenderer(acc, 0, 0); }

    @Override
    protected ResourceLocation getEntityTexture(EntityPax e) { return Skins.get(e.skin(), e.slim(), e instanceof EntityCrew c ? c.role() : 0); }

    @Override
    public void doRender(EntityPax e, double x, double y, double z, float entityYaw, float pt) {
        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        GlStateManager.translate(x, y, z);
        float yaw = e.prevRotationYaw + MathHelper.wrapDegrees(e.rotationYaw - e.prevRotationYaw) * pt;
        GlStateManager.rotate(180f - yaw, 0f, 1f, 0f);
        float s = e.kid() ? 0.6f : 0.9375f;
        GlStateManager.scale(s, s, s);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(-1f, -1f, 1f);
        GlStateManager.translate(0f, -1.501f, 0f);
        PoseModel m = e.slim() ? slim : wide;
        EntityCrew crew = e instanceof EntityCrew c ? c : null;
        m.pose = crew == null ? 0 : crew.action();
        float amt = Math.min(1f, e.prevLimbSwingAmount + (e.limbSwingAmount - e.prevLimbSwingAmount) * pt);
        float swing = e.limbSwing - e.limbSwingAmount * (1f - pt);
        float head = e.prevHead + (e.head - e.prevHead) * pt;
        m.isRiding = e.sitting();
        m.isSneak = false; m.isChild = false; m.swingProgress = 0;
        m.rightArmPose = m.leftArmPose = ModelBiped.ArmPose.EMPTY;
        bindEntityTexture(e);
        GlStateManager.color(1f, 1f, 1f, 1f);
        m.render(e, swing, amt, e.ticksExisted + pt, head, 0f, 0.0625f);
        // what they carry: modelled, flat-coloured
        GlStateManager.disableTexture2D();
        int c = Skins.CLOTH[e.accColour() % Skins.CLOTH.length];
        float sc = 0.0625f;
        if (e.wheelchair()) {
            colour(0x3A3D42); chairFrame.render(sc);
            colour(c); chairSeat.render(sc);
            colour(0x18181A); wheel.render(sc); wheel45.render(sc);
        }
        switch (e.accessory()) {
            case EntityPax.ACC_SUITCASE, EntityPax.ACC_BRIEFCASE -> {
                if (!e.sitting()) {
                    GlStateManager.pushMatrix();
                    m.bipedRightArm.postRender(sc);
                    colour(e.accessory() == EntityPax.ACC_BRIEFCASE ? 0x3B2616 : c);
                    (e.accessory() == EntityPax.ACC_BRIEFCASE ? briefcase : suitcase).render(sc);
                    GlStateManager.popMatrix();
                }
            }
            case EntityPax.ACC_BACKPACK -> { GlStateManager.pushMatrix(); m.bipedBody.postRender(sc); colour(c); backpack.render(sc); GlStateManager.popMatrix(); }
            case EntityPax.ACC_SHOULDER -> {
                GlStateManager.pushMatrix(); m.bipedBody.postRender(sc);
                colour(0x2B1D12); strapF.render(sc); strapB.render(sc);
                colour(c); bag.render(sc);
                GlStateManager.popMatrix();
            }
            default -> { }
        }
        if (crew != null) crewKit(m, crew, sc);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableRescaleNormal();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    /** what the crew wear and carry: guards and inspectors a peaked cap; the guard's paddle and whistle; the inspector's machine */
    private void crewKit(PoseModel m, EntityCrew e, float sc) {
        int role = e.role(), pose = e.action();
        if (role == 2 || role == 3) {
            GlStateManager.pushMatrix();
            m.bipedHead.postRender(sc);
            colour(role == 2 ? 0x1C2740 : 0x221C2C); capCrown.render(sc);
            colour(role == 2 ? 0xF2F2F2 : 0xB02020); capBand.render(sc);
            colour(0x121212); capVisor.render(sc);
            colour(0xD8B040); capBadge.render(sc);
            if (role == 2 && pose == 2) { colour(0xC8CCD0); whistle.render(sc); }
            GlStateManager.popMatrix();
        }
        if (role == 2 && pose == 1) {
            GlStateManager.pushMatrix();
            m.bipedRightArm.postRender(sc);
            colour(0x202020); paddlePole.render(sc);
            colour(0xF4F4F4); paddleDisc.render(sc);
            colour(0x18A040); paddleRim.render(sc);
            GlStateManager.popMatrix();
        }
        if (role == 3) {
            GlStateManager.pushMatrix();
            m.bipedRightArm.postRender(sc);
            colour(0x2A2C30); machine.render(sc);
            colour(0x60C8F0); machineScreen.render(sc);
            GlStateManager.popMatrix();
            if (pose == 3) {
                GlStateManager.pushMatrix();
                m.bipedLeftArm.postRender(sc);
                colour(0xF2EEDC); ticket.render(sc);
                GlStateManager.popMatrix();
            }
        }
    }

    private static void colour(int rgb) { GlStateManager.color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1f); }

    // ---- the tooltip ----------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void overlay(RenderGameOverlayEvent.Post ev) {
        if (ev.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null || mc.world == null || mc.gameSettings.hideGUI) return;
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;
        float pt = ev.getPartialTicks();
        Vec3d eye = view.getPositionEyes(pt), look = view.getLook(pt);
        double reach = 16;
        RayTraceResult block = mc.objectMouseOver;
        if (block != null && block.typeOfHit == RayTraceResult.Type.BLOCK) reach = Math.min(reach, block.hitVec.distanceTo(eye));
        Vec3d end = eye.add(look.x * reach, look.y * reach, look.z * reach);
        EntityPax best = null;
        double bd = reach;
        for (EntityPax e : mc.world.getEntitiesWithinAABB(EntityPax.class, view.getEntityBoundingBox().grow(reach))) {
            AxisAlignedBB bb = e.getEntityBoundingBox().grow(0.1);
            RayTraceResult hit = bb.calculateIntercept(eye, end);
            if (hit == null) continue;
            double d = hit.hitVec.distanceTo(eye);
            if (d < bd) { bd = d; best = e; }
        }
        if (best == null) return;
        boolean crew = best instanceof EntityCrew;
        String l1 = crew ? "§b" + best.destName() : (best.kid() ? "Child - " : "") + "§f→ " + best.destName();
        String l2 = crew ? best.note() : EntityPax.STATES[Math.min(EntityPax.STATES.length - 1, best.state())] + (best.note().isEmpty() ? "" : " · " + best.note()) + (best.wheelchair() ? " · step-free" : "");
        ScaledResolution sr = ev.getResolution();
        FontRenderer fr = mc.fontRenderer;
        int cx = sr.getScaledWidth() / 2, y = sr.getScaledHeight() / 2 + 12;
        int w = Math.max(fr.getStringWidth(l1), fr.getStringWidth(l2)) + 8;
        Gui.drawRect(cx - w / 2, y, cx + w / 2, y + 22, 0x90000000);
        fr.drawStringWithShadow(l1, cx - fr.getStringWidth(l1) / 2f, y + 2, 0xFFFFFF);
        fr.drawStringWithShadow(l2, cx - fr.getStringWidth(l2) / 2f, y + 12, 0xB0B0B0);
    }

    // ---- our own skins, painted in code -------------------------------------------------------------------------------
    static final class Skins {
        static final int[] TONE = {0xF5D6C6, 0xEBC4A6, 0xD9A47E, 0xC08A5F, 0x9C6644, 0x7A4A2E, 0x5C3620, 0x45281A};
        static final int[] HAIR = {0x1A1410, 0x3B2414, 0x6B4423, 0x8C3B1E, 0xD9B66A, 0x9E9E9E, 0xE8E0D0, 0xC2457A, 0x3F6FB5};
        static final int[] CLOTH = {0x1F2A44, 0xA32A2A, 0x2E5E3A, 0xC9A227, 0x6FA8DC, 0xE8E8E8, 0x222222, 0x7A7A7A, 0x6A3D8F, 0xE38FB0, 0xD9772B, 0x2A8C8C, 0xCDB891, 0x6E1F2E, 0x6B6B2E, 0x4A6FA5};
        static final int[] TROUSERS = {0x3A5A8C, 0x27364F, 0x1E1E1E, 0x5A5A5A, 0xB59F6E, 0x5E4027};
        static final int[] SHOES = {0x151515, 0x4A2E1A, 0xE6E6E6, 0x7A1F1F, 0x2B2B3A};
        static final int[] PRIDE = {0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982};
        private static final Map<Integer, ResourceLocation> CACHE = new HashMap<>();

        static ResourceLocation get(int idx, boolean slim, int role) {
            int k = (idx * 2 + (slim ? 1 : 0)) * 4 + (role & 3);
            ResourceLocation r = CACHE.get(k);
            if (r == null) {
                r = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("irextras_pax_" + k, new DynamicTexture(paint(idx, slim, role & 3)));
                CACHE.put(k, r);
            }
            return r;
        }

        interface Paint { int at(int face, int px, int py); }          // face: 0 top 1 bottom 2 right 3 front 4 left 5 back; 0 = leave clear

        /** role 0 = a passenger's own clothes; 1 driver, 2 guard, 3 ticket inspector = uniform (same face, hair, build) */
        static BufferedImage paint(int idx, boolean slim, int role) {
            Random r = new Random(idx * 7919L + 13);
            BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            int skin = TONE[r.nextInt(TONE.length)];
            int hair = r.nextInt(14) == 0 ? HAIR[7 + r.nextInt(2)] : HAIR[r.nextInt(7)];
            int hairStyle = r.nextInt(4);                    // 0 short, 1 long, 2 cropped, 3 tied back
            int outfit = r.nextInt(7);                       // 0 shirt, 1 striped, 2 suit, 3 hoodie, 4 dress, 5 coat, 6 rainbow tee
            int top = CLOTH[r.nextInt(CLOTH.length)], top2 = CLOTH[r.nextInt(CLOTH.length)];
            int legs = TROUSERS[r.nextInt(TROUSERS.length)], shoes = SHOES[r.nextInt(SHOES.length)];
            int eyes = new int[]{0x3B2A1A, 0x2F5C8C, 0x3F6B3A, 0x5A4A2A}[r.nextInt(4)];
            boolean shorts0 = outfit != 2 && outfit != 5 && r.nextInt(6) == 0, longSleeve = outfit == 2 || outfit == 3 || outfit == 5 || r.nextBoolean();
            boolean cap = r.nextInt(7) == 0 && role == 0;
            int capCol = CLOTH[r.nextInt(CLOTH.length)];
            if (role > 0) { outfit = 2; longSleeve = true; }                            // crew: uniform (below)
            final boolean shorts = shorts0 && role == 0;
            final int sleeve = longSleeve ? 10 : 4;
            if (outfit == 2) { top = r.nextBoolean() ? 0x23232B : 0x2E3A55; legs = top; shoes = 0x151515; }
            int tie = 0x8C1F1F, cuff = 0xF2F2F2;
            if (role > 0) {                                                             // uniform: shirt + tie, trousers, black shoes
                if (role == 1) { top = 0xDDE6EE; legs = 0x1F2A44; tie = 0x1F2A44; cuff = 0xDDE6EE; }
                else if (role == 2) { top = 0x1C2740; legs = 0x1C2740; tie = 0x1C2740; }
                else { top = 0x2A2235; legs = 0x1A1A1E; tie = 0xB02020; cuff = 0xB02020; }
            }
            final int fTie = tie, fCuff = cuff, fRole = role;
            final int fTop = top, fTop2 = top2, fLegs = legs, fShoes = shoes, fSkin = skin, fHair = hair, fOutfit = outfit;
            int aw = slim ? 3 : 4;
            // head
            box(img, r, 0, 0, 8, 8, 8, (f, x, y) -> {
                if (f == 0) return hairStyle == 2 ? shade(fHair, 0.8) : fHair;
                if (f == 1) return fSkin;
                if (f == 3) {
                    if (y == 0 || (y == 1 && hairStyle != 2)) return fHair;
                    if (hairStyle == 1 && (x == 0 || x == 7) && y < 6) return fHair;
                    if (y == 3 && (x == 1 || x == 2 || x == 5 || x == 6)) return shade(fHair, 0.7);
                    if (y == 4 && (x == 1 || x == 6)) return 0xF4F4F4;
                    if (y == 4 && (x == 2 || x == 5)) return eyes;
                    if (y == 5 && (x == 3 || x == 4)) return shade(fSkin, 0.88);
                    if (y == 6 && (x == 3 || x == 4)) return blend(fSkin, 0xA03040, 0.45);
                    return fSkin;
                }
                if (f == 5) return y < (hairStyle == 1 || hairStyle == 3 ? 8 : 5) ? fHair : fSkin;
                if (y < (hairStyle == 1 ? 7 : hairStyle == 2 ? 2 : 3)) return fHair;          // sides
                return y == 4 && x == 4 ? shade(fSkin, 0.85) : fSkin;                         // ear
            });
            if (cap) box(img, r, 32, 0, 8, 8, 8, (f, x, y) -> f == 0 ? capCol : f == 1 ? 0 : y < 2 || (f == 3 && y == 2) ? capCol : 0);
            else if (hairStyle == 3) box(img, r, 32, 0, 8, 8, 8, (f, x, y) -> f == 5 && y >= 1 && y <= 3 && x >= 3 && x <= 4 ? shade(fHair, 0.9) : 0);
            // body
            box(img, r, 16, 16, 8, 12, 4, (f, x, y) -> {
                if (f == 1) return fOutfit == 4 ? fTop : fLegs;
                switch (fOutfit) {
                    case 1: return y % 3 == 0 ? fTop2 : fTop;
                    case 2:
                        if (f == 3 && (x == 3 || x == 4) && y >= 1 && y <= 8) return y == 1 ? 0xF2F2F2 : fTie;
                        if (f == 3 && (x == 2 || x == 5) && y <= 6) return fRole == 1 ? shade(fTop, 0.9) : 0xF2F2F2;
                        if (fRole == 3 && f == 3 && x == 1 && y >= 1 && y <= 6) return PRIDE[y - 1];      // inspector's rainbow lanyard
                        if (fRole == 3 && f == 3 && x == 1 && y == 7) return 0xF4F4F4;                    // warrant card
                        return fTop;
                    case 3:
                        if (f == 3 && y >= 7 && y <= 9 && x >= 2 && x <= 5) return shade(fTop, 0.82);
                        if (f == 3 && (x == 3 || x == 4) && y >= 1 && y <= 3) return 0xEEEEEE;
                        return fTop;
                    case 6: return f == 3 || f == 5 ? (y >= 1 && y <= 6 ? PRIDE[y - 1] : 0xF4F4F4) : 0xF4F4F4;
                    default:
                        if (fOutfit != 4 && y == 11) return 0x3A2414;                       // belt
                        return fTop;
                }
            });
            // arms
            Paint arm = (f, x, y) -> {
                if (f == 1) return fSkin;
                if (f == 0) return y < 0 ? fSkin : fOutfit == 6 ? 0xF4F4F4 : fTop;
                int sl = fOutfit == 4 ? 2 : sleeve;
                if (y < sl) return fOutfit == 6 ? 0xF4F4F4 : fOutfit == 1 && y % 3 == 0 ? fTop2 : fTop;
                if (fOutfit == 2 && y == sl) return fCuff;
                return fSkin;
            };
            box(img, r, 40, 16, aw, 12, 4, arm);
            box(img, r, 32, 48, aw, 12, 4, arm);
            // legs
            Paint leg = (f, x, y) -> {
                if (f == 1) return shade(fShoes, 0.7);
                if (f == 0) return fOutfit == 4 ? fTop : fLegs;
                if (y >= 10) return fShoes;
                if (fOutfit == 4) return y < 5 ? fTop : shade(fSkin, 0.9);
                if (shorts && y >= 5) return fSkin;
                return fLegs;
            };
            box(img, r, 0, 16, 4, 12, 4, leg);
            box(img, r, 16, 48, 4, 12, 4, leg);
            if (role == 1 || role == 2) {                                               // hi-vis vest with reflective bands + rainbow lanyard
                int vest = role == 1 ? 0xFF7A1A : 0xE8F020;
                box(img, r, 16, 32, 8, 12, 4, (f, x, y) -> {
                    if (f <= 1 || y > 10) return 0;
                    if (f == 3 && (x == 3 || x == 4) && y < 4) return 0;                   // the V at the neck shows the tie
                    if (y == 6 || y == 8) return 0xD8DCE0;
                    if (f == 3 && x == 2 && y >= 1 && y <= 5) return PRIDE[y];
                    return vest;
                });
            }
            if (outfit == 5) {                                                          // long coat over everything
                int coat = shade(CLOTH[r.nextInt(CLOTH.length)], 0.85);
                box(img, r, 16, 32, 8, 12, 4, (f, x, y) -> f == 3 && (x == 3 || x == 4) ? 0 : f == 1 ? 0 : coat);
                box(img, r, 40, 32, aw, 12, 4, (f, x, y) -> f == 1 ? 0 : y < 9 ? coat : 0);
                box(img, r, 48, 48, aw, 12, 4, (f, x, y) -> f == 1 ? 0 : y < 9 ? coat : 0);
                box(img, r, 0, 32, 4, 12, 4, (f, x, y) -> f <= 1 ? 0 : y < 5 ? coat : 0);
                box(img, r, 0, 48, 4, 12, 4, (f, x, y) -> f <= 1 ? 0 : y < 5 ? coat : 0);
            }
            return img;
        }

        /** paint the six faces of one box of the skin layout */
        static void box(BufferedImage img, Random r, int u, int v, int w, int h, int d, Paint p) {
            face(img, r, u + d, v, w, d, 0, p);
            face(img, r, u + d + w, v, w, d, 1, p);
            face(img, r, u, v + d, d, h, 2, p);
            face(img, r, u + d, v + d, w, h, 3, p);
            face(img, r, u + d + w, v + d, d, h, 4, p);
            face(img, r, u + d + w + d, v + d, w, h, 5, p);
        }

        private static void face(BufferedImage img, Random r, int u, int v, int w, int h, int f, Paint p) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int c = p.at(f, x, y);
                    if (c == 0 || u + x >= 64 || v + y >= 64) continue;
                    img.setRGB(u + x, v + y, 0xFF000000 | shade(c, 0.92 + r.nextDouble() * 0.08));   // a little cloth texture
                }
        }

        static int shade(int c, double f) {
            int rr = (int) Math.min(255, (c >> 16 & 255) * f), g = (int) Math.min(255, (c >> 8 & 255) * f), b = (int) Math.min(255, (c & 255) * f);
            return rr << 16 | g << 8 | b;
        }

        static int blend(int a, int b, double t) {
            int rr = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t), g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
            return rr << 16 | g << 8 | bl;
        }
    }
}
