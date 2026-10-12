package com.dogpound.railmap.cabinet.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.cabinet.Cabinet;
import com.dogpound.railmap.client.render.Prims;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.controls.Layout;
import com.dogpound.railmap.controls.Surface;
import com.dogpound.railmap.grid.Panel;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;

/**
 * Swings the cabinet doors (their own models, written by tools/cabkit.py, so they are not baked into the block) about
 * their hinges over ~10 ticks, and draws the fuse carriers in their holders: fitted (blue indicator), blown (black,
 * scorched) or pulled out (empty holder). The door and fuse states come from the server (Cabinet), so every player
 * sees the same thing.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class CabinetRenderer {
    private CabinetRenderer() {}

    private static final Map<String, IModel> RAW = new HashMap<>();
    private static final Map<String, IBakedModel> BAKED = new HashMap<>();
    /** door swing per (pos, door): {open fraction 0..1, last frame time} */
    private static final Map<String, float[]> SWING = new HashMap<>();

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre e) {
        RAW.clear();
        for (Block b : ForgeRegistries.BLOCKS) {
            ResourceLocation n = b.getRegistryName();
            if (n == null || !RailMap.MODID.equals(n.getNamespace())) continue;
            Cabinet.Spec s = Cabinet.of(n.getPath());
            if (s == null) continue;
            for (Cabinet.Door d : s.doors) {
                try {
                    IModel m = ModelLoaderRegistry.getModel(new ResourceLocation(RailMap.MODID, "block/" + d.model));
                    RAW.put(d.model, m);
                    for (ResourceLocation tex : m.getTextures()) e.getMap().registerSprite(tex);
                } catch (Exception ex) { RailMap.LOG.warn("[IR Extras] no cabinet door model {}: {}", d.model, ex.toString()); }
            }
        }
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent e) {
        BAKED.clear();
        for (Map.Entry<String, IModel> m : RAW.entrySet())
            BAKED.put(m.getKey(), m.getValue().bake(TRSRTransformation.identity(), DefaultVertexFormats.ITEM,
                    loc -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(loc.toString())));
    }

    @SubscribeEvent
    public static void render(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null || BAKED.isEmpty()) return;
        Entity v = mc.getRenderViewEntity();
        float pt = e.getPartialTicks();
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt, cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt, cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
        float now = mc.world.getTotalWorldTime() + pt;
        for (TileEntity t : mc.world.loadedTileEntityList) {
            if (!(t instanceof IControlled m) || t.isInvalid()) continue;
            BlockPos p = t.getPos();
            double x = p.getX() - cx, y = p.getY() - cy, z = p.getZ() - cz;
            if (x * x + y * y + z * z > 96 * 96) continue;
            Cabinet.Spec s = Cabinet.of(t);
            if (s == null) continue;
            EnumFacing f = m.controlFacing();
            int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
            int light = mc.world.getCombinedLight(p.offset(f), 0);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 0.5, y, z + 0.5);
            GlStateManager.rotate(-90f * turns, 0, 1, 0);
            GlStateManager.translate(-0.5, 0, -0.5);
            doors(m, s, p, now, light);
            if (x * x + y * y + z * z < 24 * 24) fuses(m, light);
            GlStateManager.popMatrix();
        }
    }

    private static void doors(IControlled m, Cabinet.Spec s, BlockPos p, float now, int light) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16);
        GlStateManager.color(1f, 1f, 1f, 1f);
        for (Cabinet.Door d : s.doors) {
            IBakedModel model = BAKED.get(d.model);
            if (model == null) continue;
            float[] a = SWING.computeIfAbsent(p.toLong() + d.id, k -> new float[]{Cabinet.get(m, d.id) == 1 ? 1 : 0, now});
            float target = Cabinet.get(m, d.id) == 1 ? 1 : 0, dt = Math.max(0, Math.min(5, now - a[1]));
            a[1] = now;
            a[0] += Math.max(-0.1f * dt, Math.min(0.1f * dt, target - a[0]));       // a full swing in 10 ticks
            float k = a[0] * a[0] * (3 - 2 * a[0]);                                   // ease in and out
            GlStateManager.pushMatrix();
            GlStateManager.translate(d.hx / 16, 0, d.hz / 16);
            GlStateManager.rotate((float) (d.angle * k), 0, 1, 0);
            GlStateManager.translate(-d.hx / 16, 0, -d.hz / 16);
            Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelRenderer().renderModelBrightnessColor(model, 1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
    }

    /** the fuse carriers, in the same frame the controls renderer uses for that surface */
    private static void fuses(IControlled m, int light) {
        Layout l = m.layout();
        GlStateManager.pushMatrix();
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16);
        Prims.begin();
        for (Surface s : l.surfaces) {
            boolean any = false;
            for (Panel.C c : s.panel.cs) if (c.id.startsWith(Cabinet.FUSE)) { any = true; break; }
            if (!any) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate(8, 8, 8);
            if (s.top()) GlStateManager.rotate(90f, 1, 0, 0);
            else if (s.yaw() != 0) GlStateManager.rotate((float) s.yaw(), 0, 1, 0);
            GlStateManager.translate(-8, -8, -8);
            if (s.tilt != 0) {
                GlStateManager.translate(0, s.pivot, s.panel.face);
                GlStateManager.rotate((float) s.tilt, 1, 0, 0);
                GlStateManager.translate(0, -s.pivot, -s.panel.face);
            }
            double face = s.panel.face;
            for (Panel.C c : s.panel.cs) {
                if (!c.id.startsWith(Cabinet.FUSE)) continue;
                int st = Cabinet.get(m, c.id);
                double w = c.w * 0.62, h = c.h * 0.9, u = c.u, v = c.v;
                if (st == 1) {                                                         // pulled: the empty holder's contacts
                    box(face, u - w / 2, v + h / 2 - 0.2, u + w / 2, v + h / 2 - 0.05, 0.6, 0.75, 0xB8B0A0);
                    box(face, u - w / 2, v - h / 2 + 0.05, u + w / 2, v - h / 2 + 0.2, 0.6, 0.75, 0xB8B0A0);
                    continue;
                }
                box(face, u - w / 2, v - h / 2, u + w / 2, v + h / 2, 0.0, 0.75, 0x2A2A2E);              // carrier
                box(face, u - w * 0.35, v - h * 0.3, u + w * 0.35, v + h * 0.3, 0.75, 0.9, st == 2 ? 0x1A1410 : 0xE6E0CC);   // fuse body through the window
                box(face, u - w * 0.12, v - 0.12, u + w * 0.12, v + 0.12, 0.9, 0.95, st == 2 ? 0x101010 : 0x2A72C8);       // indicator
                box(face, u - w * 0.3, v + h / 2 - 0.25, u + w * 0.3, v + h / 2, 0.75, 1.2, 0x202024);   // pull grip
            }
            GlStateManager.popMatrix();
        }
        Prims.end();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    /** a box on a surface: u0..u1 × v0..v1, from depth d0 to d1 in front of the face */
    private static void box(double face, double u0, double v0, double u1, double v1, double d0, double d1, int rgb) {
        Prims.box(16 - u1, v0, face - d1, 16 - u0, v1, face - d0, rgb);
    }
}
