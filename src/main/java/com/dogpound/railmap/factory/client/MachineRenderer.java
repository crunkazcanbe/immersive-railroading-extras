package com.dogpound.railmap.factory.client;

import com.dogpound.railmap.factory.Factory;
import com.dogpound.railmap.factory.block.BlockController;
import com.dogpound.railmap.factory.machine.MachineType;
import com.dogpound.railmap.factory.machine.TileMachine;
import com.dogpound.railmap.client.render.GridPanelRenderer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws a commissioned plant: its big Blockbench model (OBJ, in the controller's own north-facing space: the
 * controller block is 0..1 on every axis, the machine extends to +x on the viewer's left, +z behind), the moving parts
 * (flywheels, jaw, trommel drum, chute), and the live control panel on the controller.
 */
public class MachineRenderer extends TileEntitySpecialRenderer<TileMachine> {

    /** a moving part: its own OBJ, how it moves, about which pivot (model blocks) */
    static final class Part {
        final String suffix; final char kind; final double px, py, pz; final float speed; final int axis;
        Part(String suffix, char kind, int axis, double px, double py, double pz, float speed) {
            this.suffix = suffix; this.kind = kind; this.axis = axis; this.px = px; this.py = py; this.pz = pz; this.speed = speed;
        }
    }

    /** S = spin, W = swing back and forth, L = slide down while working (chutes) */
    static final Map<MachineType, Part[]> PARTS = new EnumMap<>(MachineType.class);
    static {
        PARTS.put(MachineType.JAW_CRUSHER, new Part[]{new Part("flywheel", 'S', 0, 0.5, 1.6, 1.5, 9f), new Part("jaw", 'W', 0, 0.5, 2.4, 1.6, 6f)});
        PARTS.put(MachineType.WASHING_PLANT, new Part[]{new Part("drum", 'S', 2, 0.5, 1.6, 2.0, 4f)});
        PARTS.put(MachineType.FLOTATION, new Part[]{new Part("paddles", 'S', 0, 0.5, 39.5 / 16, 18.5 / 16, 3f)});
        PARTS.put(MachineType.SMELTER, new Part[0]);
        PARTS.put(MachineType.ASSEMBLY_LINE, new Part[]{new Part("press", 'B', 1, 0.5, 0, 1.5, 0.35f),
                new Part("robot1", 'W', 1, 1.55, 0, 2.5, 50f), new Part("robot2", 'W', 1, -0.55, 0, 3.5, 50f)});
        PARTS.put(MachineType.BULK_LOADER, new Part[]{new Part("chute", 'L', 1, 0, 0, 0, 1.6f)});
        PARTS.put(MachineType.UNLOADER, new Part[0]);
        PARTS.put(MachineType.BOREHOLE_PUMP, new Part[]{new Part("shaft", 'S', 2, 0.5, 0.5, 3.0, 14f)});
        PARTS.put(MachineType.WATER_TOWER, new Part[0]);
        PARTS.put(MachineType.OVERHEAD_LOADER, new Part[0]);
        PARTS.put(MachineType.CAR_DUMPER, new Part[]{new Part("cradle", 'R', 2, -0.5, 2.0, 0, 1f)});
        PARTS.put(MachineType.TANK_LOADER, new Part[]{new Part("arm", 'A', 2, 0, 47 / 16.0, 0, -70f)});
        PARTS.put(MachineType.TANK_UNLOADER, new Part[]{new Part("arm", 'A', 2, 2 / 16.0, 9 / 16.0, 0, -60f)});
        PARTS.put(MachineType.COKE_OVEN, new Part[0]);
        PARTS.put(MachineType.BLAST_FURNACE, new Part[0]);
        PARTS.put(MachineType.REFINERY, new Part[0]);
        PARTS.put(MachineType.ROLLING_MILL, new Part[]{new Part("rolltop", 'S', 0, 0, 17 / 16.0, 40 / 16.0, 9f), new Part("rollbot", 'S', 0, 0, 12 / 16.0, 40 / 16.0, -9f)});
        PARTS.put(MachineType.WIRE_DRAWER, new Part[]{new Part("capstan", 'S', 1, 8 / 16.0, 0, 42 / 16.0, 6f)});
        PARTS.put(MachineType.OIL_PRESS, new Part[]{new Part("screw", 'S', 0, 0, 14 / 16.0, 24 / 16.0, 5f)});
        PARTS.put(MachineType.SAWMILL, new Part[]{new Part("blade", 'S', 0, 0, 20 / 16.0, 35 / 16.0, 28f)});
        PARTS.put(MachineType.ARC_FURNACE, new Part[]{});
        PARTS.put(MachineType.ELECTROLYSER, new Part[]{});
        PARTS.put(MachineType.WELDING_CELL, new Part[]{new Part("arm", 'W', 1, 23 / 16.0, 14 / 16.0, 37 / 16.0, 70f)});   // the robot sweeps the torch along the seam
        PARTS.put(MachineType.WINDMILL, new Part[]{new Part("sails", 'S', 2, 8 / 16.0, 85 / 16.0, -7 / 16.0, -2.5f)});
        PARTS.put(MachineType.CRATE_PACKER, new Part[]{new Part("press", 'B', 1, 0, 0, 0, 0.3f)});
        PARTS.put(MachineType.TIE_PLANT, new Part[]{new Part("door", 'A', 1, 23.5 / 16.0, 0, 76.5 / 16.0, -100f)});   // the cylinder door swings open between charges
    }

    /** baked OBJ models (static body + moving parts), keyed "<id>" and "<id>_<part>" */
    public static final class Models {
        static final Map<String, IBakedModel> BAKED = new HashMap<>();
        static final Map<String, IModel> RAW = new HashMap<>();

        /** each machine's built-in belt (from its OBJ "belt" part): min xyz, max xyz, and which way it runs (+1/-1 along its long axis) */
        static final Map<String, double[]> BELTS = new HashMap<>();

        private static void load() {
            RAW.clear();
            BELTS.clear();
            for (MachineType t : MachineType.values()) belt(t.id);
            for (MachineType t : MachineType.values()) {
                loadOne(t.id);
                for (Part p : PARTS.get(t)) loadOne(t.id + "_" + p.suffix);
            }
        }

        private static void belt(String id) {
            ResourceLocation rl = new ResourceLocation(Factory.MODID, "models/block/machines/" + id + ".obj");
            double[] b = null, head = null;
            String obj = "";
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                    Minecraft.getMinecraft().getResourceManager().getResource(rl).getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.startsWith("o ")) { obj = line.substring(2).trim(); continue; }
                    if (!line.startsWith("v ")) continue;
                    String[] v = line.trim().split("\\s+");
                    double x = Double.parseDouble(v[1]), y = Double.parseDouble(v[2]), z = Double.parseDouble(v[3]);
                    if (obj.equals("belt")) {
                        if (b == null) b = new double[]{x, y, z, x, y, z, 1};
                        b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.min(b[2], z);
                        b[3] = Math.max(b[3], x); b[4] = Math.max(b[4], y); b[5] = Math.max(b[5], z);
                    } else if (obj.equals("head_pulley")) {
                        if (head == null) head = new double[]{0, 0, 0, 0};
                        head[0] += x; head[2] += z; head[3]++;
                    }
                }
            } catch (Exception e) { return; }
            if (b == null) return;
            if (head != null) {                                                     // runs towards its head pulley
                boolean alongZ = b[5] - b[2] >= b[3] - b[0];
                double hc = alongZ ? head[2] / head[3] : head[0] / head[3], mid = alongZ ? (b[2] + b[5]) / 2 : (b[0] + b[3]) / 2;
                b[6] = hc < mid ? -1 : 1;
            }
            BELTS.put(id, b);
        }

        private static void loadOne(String name) {
            ResourceLocation rl = new ResourceLocation(Factory.MODID, "block/machines/" + name + ".obj");
            try { RAW.put(name, ModelLoaderRegistry.getModel(rl).process(com.google.common.collect.ImmutableMap.of("flip-v", "true"))); }
            catch (Exception e) { Factory.LOG.warn("No model {} ({})", rl, e.toString()); }
        }

        @SubscribeEvent
        public static void stitch(TextureStitchEvent.Pre e) {
            load();
            for (IModel m : RAW.values()) for (ResourceLocation tex : m.getTextures()) e.getMap().registerSprite(tex);
            e.getMap().registerSprite(BeltDraw.STRIP);
        }

        @SubscribeEvent
        public static void bake(ModelBakeEvent e) {
            BAKED.clear();
            for (Map.Entry<String, IModel> m : RAW.entrySet())
                BAKED.put(m.getKey(), m.getValue().bake(TRSRTransformation.identity(), DefaultVertexFormats.ITEM,
                        loc -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(loc.toString())));
        }
    }

    @Override
    public void render(TileMachine te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (!te.formed() || te.getWorld() == null || te.type() == null) return;
        IBlockState st = te.getWorld().getBlockState(te.getPos());
        if (!(st.getBlock() instanceof BlockController)) return;
        EnumFacing f = st.getValue(BlockController.FACING);
        MachineType t = te.type();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.shadeModel(org.lwjgl.opengl.GL11.GL_SMOOTH);
        int light = te.getWorld().getCombinedLight(te.getPos().offset(f), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16);
        float bright = Math.max(0.35f, Math.min(1f, ((light >> 16 & 0xFFFF) / 240f) * 0.75f + ((light & 0xFFFF) / 240f) * 0.6f));

        DIM = te.burnt ? 0.3f : 1f;                                                // burnt out: the whole plant charred black
        draw(Models.BAKED.get(t.id), bright);
        float time = te.getWorld().getTotalWorldTime() + pt;
        boolean on = te.pv("anim") == 1;
        if (on) te.phase = time;
        for (Part p : PARTS.get(t)) {
            if (te.burnt && t == MachineType.WINDMILL) continue;                    // the storm tore the sails off
            IBakedModel m = Models.BAKED.get(t.id + "_" + p.suffix);
            if (m == null) continue;
            GlStateManager.pushMatrix();
            float ph = on ? time : 0;
            switch (p.kind) {
                case 'S': {
                    GlStateManager.translate(p.px, p.py, p.pz);
                    GlStateManager.rotate(ph * p.speed, p.axis == 0 ? 1 : 0, p.axis == 1 ? 1 : 0, p.axis == 2 ? 1 : 0);
                    GlStateManager.translate(-p.px, -p.py, -p.pz);
                    break;
                }
                case 'W': {
                    float a = (float) Math.sin(ph * 0.35) * p.speed * 0.5f;
                    GlStateManager.translate(p.px, p.py, p.pz);
                    GlStateManager.rotate(a, p.axis == 0 ? 1 : 0, p.axis == 1 ? 1 : 0, p.axis == 2 ? 1 : 0);
                    GlStateManager.translate(-p.px, -p.py, -p.pz);
                    break;
                }
                case 'R': {   // turned to the angle the machine reports (dumper cradle), eased
                    float[] a = te.anim().computeIfAbsent(p.suffix, k -> new float[]{te.pv("angle")});
                    a[0] += Math.max(-2f, Math.min(2f, te.pv("angle") - a[0]));
                    GlStateManager.translate(p.px, p.py, p.pz);
                    GlStateManager.rotate(a[0], p.axis == 0 ? 1 : 0, p.axis == 1 ? 1 : 0, p.axis == 2 ? 1 : 0);
                    GlStateManager.translate(-p.px, -p.py, -p.pz);
                    break;
                }
                case 'A': {   // an arm that swings between raised (speed = its raised angle) and lowered
                    float[] a = te.anim().computeIfAbsent(p.suffix, k -> new float[]{p.speed});
                    float target = te.pv("arm") == 1 ? 0 : p.speed;
                    a[0] += Math.max(-1.2f, Math.min(1.2f, target - a[0]));
                    GlStateManager.translate(p.px, p.py, p.pz);
                    GlStateManager.rotate(a[0], p.axis == 0 ? 1 : 0, p.axis == 1 ? 1 : 0, p.axis == 2 ? 1 : 0);
                    GlStateManager.translate(-p.px, -p.py, -p.pz);
                    break;
                }
                case 'B': {   // press head: comes down hard, lifts slowly
                    double c = (ph * 0.12) % (Math.PI * 2);
                    float dy = (float) (c < 1.0 ? -p.speed * Math.sin(c * Math.PI / 2) : -p.speed * Math.max(0, 1 - (c - 1.0) / (Math.PI * 2 - 1.0)));
                    GlStateManager.translate(0, dy, 0);
                    break;
                }
                default: {   // 'L': the chute telescopes down while the gate is open
                    float[] a = te.anim().computeIfAbsent("chute", k -> new float[]{0});
                    float target = te.pv("lgate") == 1 ? p.speed : 0;
                    a[0] += Math.max(-0.05f, Math.min(0.05f, target - a[0]));
                    GlStateManager.translate(0, -a[0], 0);
                }
            }
            draw(m, bright);
            GlStateManager.popMatrix();
        }
        double[] belt = Models.BELTS.get(t.id);
        if (belt != null) {                                                         // its built-in belt runs while it works
            boolean alongZ = belt[5] - belt[2] >= belt[3] - belt[0];
            double top = belt[4] + 0.004, run = te.phase * 0.05, half = (alongZ ? belt[3] - belt[0] : belt[5] - belt[2]) / 2 - 0.01;
            double c = alongZ ? (belt[0] + belt[3]) / 2 : (belt[2] + belt[5]) / 2;
            double lo = (alongZ ? belt[2] : belt[0]) + 0.01, hi = (alongZ ? belt[5] : belt[3]) - 0.01;
            if (belt[6] < 0) { double k = lo; lo = hi; hi = k; }
            double[] a = alongZ ? new double[]{c, top, lo} : new double[]{lo, top, c}, b = alongZ ? new double[]{c, top, hi} : new double[]{hi, top, c};
            net.minecraft.client.renderer.BufferBuilder bb = BeltDraw.begin(light);
            BeltDraw.straight(bb, a, b, half, run, 255);
            BeltDraw.end();
        }
        if (t == MachineType.OVERHEAD_LOADER) {                                     // the telescoping chute down through the floor
            float[] c = te.anim().computeIfAbsent("chute", k -> new float[]{te.pv("chute")});
            c[0] += Math.max(-1.5f, Math.min(1.5f, te.pv("chute") - c[0]));
            if (c[0] > 0.5f) {
                GlStateManager.pushMatrix();
                GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
                com.dogpound.railmap.client.render.Prims.begin();
                double top = 3, bottom = 3 - c[0];
                for (int i = 0; top > bottom + 0.01; i++) {
                    double seg = Math.max(bottom, top - 16), w = 4.6 - i * 0.35;
                    com.dogpound.railmap.client.render.Prims.box(8 - w, seg, 24 - w, 8 + w, top, 24 + w, i % 2 == 0 ? 0x8C9298 : 0x7A8086);
                    com.dogpound.railmap.client.render.Prims.box(8 - w - 0.4, top - 1, 24 - w - 0.4, 8 + w + 0.4, top, 24 + w + 0.4, 0x3A3F44);   // the telescoping joint
                    top = seg;
                }
                com.dogpound.railmap.client.render.Prims.box(8 - 3.4, bottom - 1.5, 24 - 3.4, 8 + 3.4, bottom, 24 + 3.4, 0x2A2A2E);   // rubber skirt
                com.dogpound.railmap.client.render.Prims.end();
                GlStateManager.popMatrix();
            }
        }
        if (t == MachineType.ASSEMBLY_LINE) {                                       // the work pieces at their stations
            RenderHelper.enableStandardItemLighting();
            for (int i = 0; i < 5; i++) {
                int r = te.pv("st" + i) - 1;
                if (r < 0 || r >= com.dogpound.railmap.factory.machine.AssemblyRecipes.ALL.size()) continue;
                net.minecraft.item.ItemStack piece = com.dogpound.railmap.factory.machine.AssemblyRecipes.ALL.get(r).result();
                if (piece.isEmpty()) continue;
                GlStateManager.pushMatrix();
                GlStateManager.translate(0.5, 0.62, 1.5 + i);
                GlStateManager.scale(0.5f, 0.5f, 0.5f);
                Minecraft.getMinecraft().getRenderItem().renderItem(piece, net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIXED);
                GlStateManager.popMatrix();
            }
        }
        RenderHelper.enableStandardItemLighting();
        GlStateManager.popMatrix();

        renderFluids(te, t, x, y, z, turns, light);
        GridPanelRenderer.render(te, t.panel(), f, te.getWorld(), te.getPos(), x, y, z, pt);
    }

    // ---- live fluids ------------------------------------------------------------------------------------------
    private void renderFluids(TileMachine te, MachineType t, double x, double y, double z, int turns, int light) {
        String ore = te.ptext("fore");
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        switch (t) {
            case SMELTER: {
                net.minecraftforge.fluids.FluidStack fs = fluidOf(te.ptext("fluid"), ore);
                if (fs == null) break;
                int metal = te.pv("metal");
                if (metal > 0) {                                                       // the melt in the crucible
                    double top = 31 + 3.6 * Math.min(1, metal / 4000.0);
                    box(fs, true, 4, top - 0.4, 22, 12, top, 38, 240);
                    box(fs, true, -1, top - 0.4, 26, 17, top, 34, 240);
                }
                if (te.pv("pour") == 1) {                                              // spout -> launder -> into the moulds
                    box(fs, false, -6, 33.9, 21, 2, 34.4, 23, 240);
                    box(fs, false, -14, 25.6, 11, -6, 26.2, 23, 240);
                    box(fs, false, -10.8, 8.5, 12.2, -9.2, 25.8, 13.8, 240);
                    box(fs, true, -12.5, 8.4, 11, -4.5, 9.0, 14, 240);
                }
                break;
            }
            case WASHING_PLANT: {
                if (ore.isEmpty()) break;
                net.minecraftforge.fluids.FluidStack fs = com.dogpound.railmap.factory.fluid.ModFluids.tagged(com.dogpound.railmap.factory.fluid.ModFluids.SLURRY, ore, 1000);
                int lvl = te.pv("slurry");
                if (lvl > 0) box(fs, true, 3.1, 0, 54.1, 12.9, 6 + 6.6 * lvl / 100.0, 61.9, light >> 16 & 0xFFFF);
                if (te.pv("anim") == 1) box(fs, false, 5.5, 8, 56.5, 10.5, 16, 58.5, light >> 16 & 0xFFFF);
                break;
            }
            case FLOTATION: {
                if (ore.isEmpty() || te.pv("anim") != 1) break;
                net.minecraftforge.fluids.FluidStack fs = com.dogpound.railmap.factory.fluid.ModFluids.tagged(com.dogpound.railmap.factory.fluid.ModFluids.FROTH, ore, 1000);
                box(fs, true, -12, 37.02, 16, 5, 37.6, 44, light >> 16 & 0xFFFF);
                box(fs, true, 11, 37.02, 16, 28, 37.6, 44, light >> 16 & 0xFFFF);
                box(fs, false, -14, 32.6, 11, 30, 33.4, 14, light >> 16 & 0xFFFF);
                break;
            }
            case BLAST_FURNACE: {                                                     // the tap: molten iron down the runner
                if (te.pv("anim") != 1) break;
                net.minecraftforge.fluids.FluidStack hot = new net.minecraftforge.fluids.FluidStack(com.dogpound.railmap.factory.fluid.ModFluids.MOLTEN, 1000);
                box(hot, true, 6, 2.9, -7.6, 10, 3.3, -0.2, 240);
                box(hot, false, 7, 2, -0.4, 9, 5.5, 1.2, 240);
                break;
            }
            case ARC_FURNACE: {                                                       // white-hot round the electrode ports, steel at the spout
                if (te.pv("anim") != 1) break;
                net.minecraftforge.fluids.FluidStack hot = new net.minecraftforge.fluids.FluidStack(com.dogpound.railmap.factory.fluid.ModFluids.MOLTEN, 1000);
                double fl = 0.3 + 0.25 * Math.sin(te.getWorld().getTotalWorldTime() * 1.7);
                box(hot, true, -2.5, 48, 23.5, 18.5, 48.4 + fl, 29.5, 240);
                box(hot, true, -21, 15, 23.2, -13.5, 15.4, 26.8, 240);
                break;
            }
            case COKE_OVEN: {                                                         // the oven doors glowing round the edges
                if (te.pv("anim") != 1) break;
                net.minecraftforge.fluids.FluidStack hot = new net.minecraftforge.fluids.FluidStack(com.dogpound.railmap.factory.fluid.ModFluids.MOLTEN, 1000);
                for (int dx : new int[]{-13, -1, 11, 23}) { box(hot, true, dx, 36, -0.15, dx + 6, 36.5, 0.9, 240); box(hot, true, dx, 3.5, -0.15, dx + 6, 4, 0.9, 240); }
                break;
            }
            case REFINERY: {                                                          // the flare burning off at the top
                if (te.pv("anim") != 1) break;
                net.minecraftforge.fluids.FluidStack hot = new net.minecraftforge.fluids.FluidStack(com.dogpound.railmap.factory.fluid.ModFluids.MOLTEN, 1000);
                double fl = 4 + 2 * Math.sin(te.getWorld().getTotalWorldTime() * 0.7);
                box(hot, false, 26, 122, 44, 30, 122 + fl, 48, 240);
                break;
            }
            case BOREHOLE_PUMP: {                                                     // water rushing up the sight glass
                if (te.pv("anim") != 1) break;
                net.minecraftforge.fluids.FluidStack w = new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000);
                box(w, false, 28.8, 16, 22.8, 31.2, 27, 25.2, light >> 16 & 0xFFFF);
                break;
            }
            case WATER_TOWER: {
                net.minecraftforge.fluids.FluidStack w = new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000);
                int lvl = te.pv("level");
                if (lvl > 0) box(w, true, 6.2, 52, -2.2, 9.8, 52 + 54 * lvl / 100.0, -0.7, light >> 16 & 0xFFFF);   // level gauge
                if (te.pv("lover") == 1 && te.pv("level") >= 99) box(w, false, 28.8, 47, -1.2, 31.2, 103, 0.2, light >> 16 & 0xFFFF); // spilling over
                break;
            }
            default: break;
        }
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static net.minecraftforge.fluids.FluidStack fluidOf(String name, String ore) {
        net.minecraftforge.fluids.Fluid fl = name.isEmpty() ? null : net.minecraftforge.fluids.FluidRegistry.getFluid(name);
        if (fl == null) return null;
        return fl == com.dogpound.railmap.factory.fluid.ModFluids.MOLTEN ? com.dogpound.railmap.factory.fluid.ModFluids.tagged(fl, ore, 1000) : new net.minecraftforge.fluids.FluidStack(fl, 1000);
    }

    /** a box of fluid (model pixels): still texture on top for pools, flowing texture down the sides for streams */
    private static void box(net.minecraftforge.fluids.FluidStack fs, boolean pool, double x0, double y0, double z0, double x1, double y1, double z1, int sky) {
        net.minecraft.client.renderer.texture.TextureMap map = Minecraft.getMinecraft().getTextureMapBlocks();
        net.minecraft.client.renderer.texture.TextureAtlasSprite still = map.getAtlasSprite(fs.getFluid().getStill(fs).toString());
        net.minecraft.client.renderer.texture.TextureAtlasSprite flow = map.getAtlasSprite(fs.getFluid().getFlowing(fs).toString());
        int c = fs.getFluid() == net.minecraftforge.fluids.FluidRegistry.WATER ? 0xD03F76E4 : fs.getFluid().getColor(fs);
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255, a = Math.max(160, c >>> 24);
        boolean glow = fs.getFluid().getLuminosity(fs) > 7;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, glow ? 240f : Math.max(60f, sky));
        net.minecraft.client.renderer.Tessellator tes = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder bb = tes.getBuffer();
        bb.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        // top
        quad(bb, still, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a);
        net.minecraft.client.renderer.texture.TextureAtlasSprite side = pool ? still : flow;
        quad(bb, side, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, r, g, b, a);   // north
        quad(bb, side, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, r, g, b, a);   // south
        quad(bb, side, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, r, g, b, a);   // west
        quad(bb, side, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, r, g, b, a);   // east
        tes.draw();
    }

    private static void quad(net.minecraft.client.renderer.BufferBuilder bb, net.minecraft.client.renderer.texture.TextureAtlasSprite sp,
                             double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, double dx, double dy, double dz,
                             int r, int g, int b, int a) {
        double u0 = sp.getMinU(), v0 = sp.getMinV(), u1 = sp.getInterpolatedU(8), v1 = sp.getInterpolatedV(8);
        bb.pos(ax, ay, az).tex(u0, v0).color(r, g, b, a).endVertex();
        bb.pos(bx, by, bz).tex(u1, v0).color(r, g, b, a).endVertex();
        bb.pos(cx, cy, cz).tex(u1, v1).color(r, g, b, a).endVertex();
        bb.pos(dx, dy, dz).tex(u0, v1).color(r, g, b, a).endVertex();
    }

    /** brightness multiplier (the colour arguments only reach tinted faces, so a burnt plant is darkened through this) */
    private static float DIM = 1f;

    private static void draw(IBakedModel m, float bright) {
        if (m == null) return;
        Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelRenderer().renderModelBrightnessColor(m, bright * DIM, 1f, 1f, 1f);
    }

    @Override public boolean isGlobalRenderer(TileMachine te) { return true; }
}
