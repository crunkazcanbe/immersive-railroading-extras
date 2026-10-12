package com.dogpound.railmap.ride.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.ride.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;

/** The client's copy of the lines (from PacketRide), the cars' smooth motion along them, and the models. */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class RideClient {
    private RideClient() {}

    public static final Map<Integer, RideData.Line> LINES = new HashMap<>();
    public static int dim = Integer.MIN_VALUE;

    /** the baked moving parts (item variants of the spool) */
    public static final String[] PARTS = {"ride_cabin_gondola", "ride_cabin_tram", "ride_car_funicular", "ropeway_pylon_head", "ropeway_bullwheel",
            "ropeway_station_frame", "ropeway_mach_wheel", "ropeway_mach_rotor", "ropeway_tension_weight"};
    private static final Map<String, IBakedModel> BAKED = new HashMap<>();

    public static void sync(NBTTagCompound t) {
        dim = t.getInteger("dim");
        PacketRide.apply(t, LINES);
    }

    public static RideData.Line line(int id) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.world.provider.getDimension() != dim) return null;
        return LINES.get(id);
    }

    public static IBakedModel baked(String name) {
        return BAKED.computeIfAbsent(name, n -> Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getModelManager()
                .getModel(new ModelResourceLocation(RailMap.MODID + ":" + n, "inventory")));
    }

    /** client tick of a car: run its own coordinate forward at its speed, pulled gently to the server's, and place it on the rope */
    public static void carTick(EntityRideCar c) {
        RideData.Line l = line(c.line());
        double server = c.synced();
        double v = c.speed();
        if (l == null || l.geom() == null) { if (!Double.isNaN(c.coord)) c.setPosition(c.posX, c.posY, c.posZ); return; }
        RideGeom g = l.geom();
        boolean loop = !l.shuttle();
        if (Double.isNaN(c.coord)) c.coord = server;
        else {
            double sign = l.shuttle() && c.idx() == 1 ? -1 : 1;
            c.coord += sign * v * 0.05;
            double err = loop ? wrapErr(g, server - c.coord) : server - c.coord;
            if (Math.abs(err) > 3) c.coord = server; else c.coord += err * 0.25;
            if (loop) c.coord = g.wrap(c.coord);
            else c.coord = Math.max(0, Math.min(g.length, c.coord));
        }
        double[] p = loop ? g.loopPose(c.coord) : g.carPose(c.coord, c.idx() == 0 ? 1 : -1, (c.idx() == 0 ? 1 : -1) * l.dir);
        c.setPosition(p[0], p[1], p[2]);
        float yaw = (float) p[3];
        while (yaw - c.prevRotationYaw > 180) yaw -= 360;
        while (yaw - c.prevRotationYaw < -180) yaw += 360;
        c.rotationYaw = yaw;
        c.rotationPitch = l.kind == RideGeom.FUNICULAR ? (float) p[4] : 0;
        // the cabin swings on its hanger: kicked by speed changes, damped, nudged by the wind
        c.prevSwing = c.swing;
        if (l.kind != RideGeom.FUNICULAR) {
            double acc = (v - c.lastV) / 0.05;
            c.lastV = v;
            c.swingV += (float) (-c.swing * 0.06 - acc * 0.9 - c.swingV * 0.04 + Math.sin(c.ticksExisted * 0.07 + c.getEntityId()) * l.wind * 0.004);
            c.swing += c.swingV;
            c.swing = Math.max(-14, Math.min(14, c.swing));
        }
    }

    static double wrapErr(RideGeom g, double e) {
        double L = g.loop;
        e %= L;
        if (e > L / 2) e -= L;
        if (e < -L / 2) e += L;
        return e;
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        RenderingRegistry.registerEntityRenderingHandler(EntityRideCar.class, RenderRideCar::new);
        for (Block b : RideRegistry.BLOCKS)
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b), 0, new ModelResourceLocation(b.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(RideRegistry.spool, 0, new ModelResourceLocation(RailMap.MODID + ":rope_spool", "inventory"));
        ResourceLocation[] v = new ResourceLocation[PARTS.length + 1];
        v[0] = new ResourceLocation(RailMap.MODID, "rope_spool");
        for (int i = 0; i < PARTS.length; i++) v[i + 1] = new ResourceLocation(RailMap.MODID, PARTS[i]);
        ModelBakery.registerItemVariants(RideRegistry.spool, v);
        BAKED.clear();
    }
}
