package com.dogpound.railmap.controls;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.grid.Panel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A block's live controls, written by the model generator next to its model (assets/irextras/controls/<name>.json), so
 * what you see on the model is exactly what works. Loaded once per name on either side (the server hit-tests clicks).
 */
public final class Layout {
    public final List<Surface> surfaces;
    /** every control on one panel, for the console's WIRED page / Control Buttons */
    public final Panel all;
    private final Map<String, Panel.C> byId = new HashMap<>();

    private Layout(List<Surface> surfaces, String title) {
        this.surfaces = surfaces;
        all = new Panel(title, 0, 0, 16, 16, 0, 0);
        for (Surface s : surfaces) for (Panel.C c : s.panel.cs) if (!c.id.isEmpty() && !byId.containsKey(c.id)) { byId.put(c.id, c); if (c.t != Panel.T.ZONE) all.cs.add(c); }   // storage zones aren't remote controls   // the same reading shown twice is listed once
    }

    public Panel.C control(String id) { return byId.get(id); }
    public boolean isEmpty() { return surfaces.isEmpty(); }

    public static final Layout NONE = new Layout(Collections.emptyList(), "");
    private static final Map<String, Layout> CACHE = new HashMap<>();

    public static synchronized Layout of(String name) {
        return CACHE.computeIfAbsent(name, Layout::load);
    }

    private static Layout load(String name) {
        try (InputStream in = Layout.class.getResourceAsStream("/assets/" + RailMap.MODID + "/controls/" + name + ".json")) {
            if (in == null) return NONE;
            JsonObject o = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Surface> out = new ArrayList<>();
            for (JsonElement se : o.getAsJsonArray("surfaces")) {
                JsonObject s = se.getAsJsonObject();
                Panel p = new Panel(str(s, "title", ""), 0, 0, 16, 16, s.get("face").getAsDouble(), s.has("plate") ? Integer.parseInt(s.get("plate").getAsString(), 16) : 0x404448);
                for (JsonElement ce : s.getAsJsonArray("controls")) {
                    JsonObject c = ce.getAsJsonObject();
                    String[] pos = new String[0];
                    if (c.has("pos")) { JsonArray a = c.getAsJsonArray("pos"); pos = new String[a.size()]; for (int i = 0; i < pos.length; i++) pos[i] = a.get(i).getAsString(); }
                    Panel.C k = new Panel.C(str(c, "id", ""), Panel.T.valueOf(c.get("t").getAsString().toUpperCase(Locale.ROOT)),
                            c.get("u").getAsDouble(), c.get("v").getAsDouble(), c.get("w").getAsDouble(), c.get("h").getAsDouble(),
                            Integer.parseInt(str(c, "col", "202020"), 16), str(c, "label", ""), pos);
                    if (c.has("fmt")) k.fmt = c.get("fmt").getAsString();
                    if (c.has("scale")) k.scale = c.get("scale").getAsDouble();
                    if (c.has("max")) k.max = c.get("max").getAsInt();
                    if (c.has("blink")) k.blink = c.get("blink").getAsBoolean();
                    p.cs.add(k);
                }
                out.add(new Surface(str(s, "side", "north"), s.has("tilt") ? s.get("tilt").getAsDouble() : 0,
                        s.has("pivot") ? s.get("pivot").getAsDouble() : 0, s.has("plate"), p));
            }
            return new Layout(out, str(o, "title", name));
        } catch (Exception ex) {
            RailMap.LOG.warn("[IR Extras] bad controls layout {}: {}", name, ex.toString());
            return NONE;
        }
    }

    private static String str(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }
}
