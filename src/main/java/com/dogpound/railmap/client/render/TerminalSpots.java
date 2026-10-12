package com.dogpound.railmap.client.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Where a piece of gear's 12 V terminal box actually is, read from its Blockbench model ("terminal box" elements that
 * tools/bbkit.py terminal_12v() writes) and turned with the blockstate's y rotation - so a wire can run to the real
 * terminal instead of stopping at the block's edge. Results in pixels (0..16 = the block, may run past it on wide
 * gear): {x, yBottom, z} at the middle of the box's floor. Cached per blockstate.
 */
final class TerminalSpots {
    private TerminalSpots() {}

    private static final Map<IBlockState, List<double[]>> CACHE = new HashMap<>();

    static List<double[]> of(IBlockState s) {
        return CACHE.computeIfAbsent(s, TerminalSpots::load);
    }

    private static List<double[]> load(IBlockState s) {
        List<double[]> out = new ArrayList<>();
        ResourceLocation id = s.getBlock().getRegistryName();
        if (id == null) return out;
        try {
            JsonObject bs = read(new ResourceLocation(id.getNamespace(), "blockstates/" + id.getPath() + ".json"));
            JsonObject vars = bs == null ? null : bs.getAsJsonObject("variants");
            if (vars == null) return out;
            JsonElement v = vars.get(variantKey(s));
            if (v == null) v = vars.entrySet().iterator().next().getValue();
            if (v.isJsonArray()) v = v.getAsJsonArray().get(0);
            JsonObject vo = v.getAsJsonObject();
            ResourceLocation m = new ResourceLocation(vo.get("model").getAsString());
            int rot = vo.has("y") ? vo.get("y").getAsInt() : 0;
            JsonObject model = read(new ResourceLocation(m.getNamespace(), "models/block/" + m.getPath() + ".json"));
            if (model == null || !model.has("elements")) return out;
            for (JsonElement e : model.getAsJsonArray("elements")) {
                JsonObject o = e.getAsJsonObject();
                if (!o.has("__comment") || !"terminal box".equals(o.get("__comment").getAsString())) continue;
                double[] f = xyz(o, "from"), t = xyz(o, "to");
                double x = (f[0] + t[0]) / 2, z = (f[2] + t[2]) / 2;
                double[] r = turn(x, z, rot);
                out.add(new double[]{r[0], Math.min(f[1], t[1]), r[1]});
            }
        } catch (Exception ignored) {
            // no model / odd JSON: the wire falls back to stopping at the block's edge
        }
        return out;
    }

    /** "facing=east,lit=false" - the vanilla variant key: properties sorted by name */
    private static String variantKey(IBlockState s) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<IProperty<?>, Comparable<?>> e : s.getProperties().entrySet()) parts.add(e.getKey().getName() + "=" + name(e.getKey(), e.getValue()));
        Collections.sort(parts);
        return parts.isEmpty() ? "normal" : String.join(",", parts);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String name(IProperty p, Comparable v) { return p.getName(v); }

    /** the model faces north; the blockstate turns it clockwise (seen from above) by y degrees about the block's centre */
    static double[] turn(double x, double z, int y) {
        switch (((y % 360) + 360) % 360) {
            case 90: return new double[]{16 - z, x};
            case 180: return new double[]{16 - x, 16 - z};
            case 270: return new double[]{z, 16 - x};
            default: return new double[]{x, z};
        }
    }

    private static double[] xyz(JsonObject o, String k) {
        com.google.gson.JsonArray a = o.getAsJsonArray(k);
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    private static JsonObject read(ResourceLocation rl) {
        try (InputStreamReader r = new InputStreamReader(Minecraft.getMinecraft().getResourceManager().getResource(rl).getInputStream(), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(r).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
