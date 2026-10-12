package com.dogpound.railmap.newrail6.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.newrail6.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Batch 6 #16-20 client: models, the ferry and garden train renderers, the tarpaulined vehicles on motorail wagons. */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class NR6Client {
    private NR6Client() {}

    /** baked moving parts (item variants of the garden train set) */
    public static final String[] PARTS = {"train_ferry_ship", "train_ferry_door", "garden_loco", "garden_truck", "garden_car", "motorail_tarp"};
    private static final Map<String, IBakedModel> BAKED = new HashMap<>();
    /** wagon UUID -> the vehicle chained on it */
    public static final Map<UUID, String> LOADS = new HashMap<>();

    public static void loads(NBTTagCompound t) {
        LOADS.clear();
        NBTTagList l = t.getTagList("l", 10);
        for (int i = 0; i < l.tagCount(); i++) { NBTTagCompound c = l.getCompoundTagAt(i); UUID u = c.getUniqueId("u"); if (u != null) LOADS.put(u, c.getString("n")); }
    }

    public static IBakedModel baked(String name) {
        return BAKED.computeIfAbsent(name, n -> Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getModelManager()
                .getModel(new ModelResourceLocation(RailMap.MODID + ":" + n, "inventory")));
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        RenderingRegistry.registerEntityRenderingHandler(EntityTrainFerry.class, RenderTrainFerry::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityGardenTrain.class, RenderGardenTrain::new);
        for (Block b : NR6Registry.BLOCKS)
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b), 0, new ModelResourceLocation(b.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(NR6Registry.gardenSet, 0, new ModelResourceLocation(RailMap.MODID + ":garden_train_set", "inventory"));
        ResourceLocation[] v = new ResourceLocation[PARTS.length + 1];
        v[0] = new ResourceLocation(RailMap.MODID, "garden_train_set");
        for (int i = 0; i < PARTS.length; i++) v[i + 1] = new ResourceLocation(RailMap.MODID, PARTS[i]);
        ModelBakery.registerItemVariants(NR6Registry.gardenSet, v);
        BAKED.clear();
    }

    /** draw a baked part at the current GL origin (block atlas, its own light) */
    static void draw(String part, int light) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
        BlockModelRenderer r = mc.getBlockRendererDispatcher().getBlockModelRenderer();
        r.renderModelBrightnessColor(baked(part), 1f, 1f, 1f, 1f);
    }

    /** a tarpaulin over each vehicle riding a motorail wagon */
    @SubscribeEvent
    public static void tarps(RenderWorldLastEvent e) {
        if (LOADS.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null) return;
        Entity v = mc.getRenderViewEntity();
        float pt = e.getPartialTicks();
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt, cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt, cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.color(1, 1, 1, 1);
        for (Entity w : mc.world.loadedEntityList) {
            if (!LOADS.containsKey(w.getUniqueID())) continue;
            double x = w.lastTickPosX + (w.posX - w.lastTickPosX) * pt - cx, y = w.lastTickPosY + (w.posY - w.lastTickPosY) * pt - cy, z = w.lastTickPosZ + (w.posZ - w.lastTickPosZ) * pt - cz;
            if (x * x + y * y + z * z > 160 * 160) continue;
            float yaw = w.prevRotationYaw + net.minecraft.util.math.MathHelper.wrapDegrees(w.rotationYaw - w.prevRotationYaw) * pt;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y + 1.05, z);
            GlStateManager.rotate(180f - yaw, 0, 1, 0);
            GlStateManager.scale(2f, 2f, 2f);
            GlStateManager.translate(-0.5, 0, -0.5);
            draw("motorail_tarp", w.getBrightnessForRender());
            GlStateManager.popMatrix();
        }
        RenderHelper.enableStandardItemLighting();
    }
}
