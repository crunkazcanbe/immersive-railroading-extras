package com.dogpound.railmap.sim;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotTicker;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.network.PacketAppearance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.*;

/**
 * Vehicle condition and appearance (her ChatGPT list item 110). Every two seconds each vehicle's look is worked out
 * from real state and sent to the players near it, who see it tinted on the IR model (the fork's PrideTint):
 * <ul>
 *   <li><b>dirt</b>: the depot's exterior cleanliness (it falls with every km, faster in the rain; the Train Wash Plant
 *       brings it back) - grime brown over the paint;</li>
 *   <li><b>weathering</b>: the paint fades and yellows over the km since the last repaint (config weatheringKm);</li>
 *   <li><b>body damage</b>: a derailment dents it (35 %), on its side (70 %), wrecked (100 %) - darkened, scorched
 *       panels until it is RESTORED at a Formation &amp; Livery Desk;</li>
 *   <li><b>livery</b>: the builder's paint, or the operating company's colour (the economy's company colour), applied
 *       by a repaint at the desk (which also resets the weathering).</li>
 * </ul>
 */
public final class Appearance {
    private Appearance() {}

    static final float[] WRECK_DAMAGE = {0, 35, 70, 100};

    static void tick(World mc, SimData data, long now) {
        if (!SimConfig.appearance || now % 40 != 17) return;
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        CompanyData cd = CompanyData.get(mc);
        Map<UUID, Integer> trainColour = new HashMap<>();
        List<int[]> out = new ArrayList<>();          // {entity id, rgb}
        List<Vec3d> where = new ArrayList<>();
        for (EntityMoveableRollingStock s : w.getEntities(EntityMoveableRollingStock.class)) {
            if (s.isDead()) continue;
            SimData.Car c = data.peek(s.getUUID());
            if (s.wreck > 0 && s.wreck < WRECK_DAMAGE.length) {
                if (c == null) c = data.of(s.getUUID());
                if (c.body < WRECK_DAMAGE[s.wreck]) { c.body = WRECK_DAMAGE[s.wreck]; data.markDirty(); }
            }
            int col = -1;
            if (c != null && c.livery == 1) col = colour(cd, s, trainColour);
            int rgb = rgb(tint(DepotTicker.live(s.getUUID()), c, col));
            if (rgb == 0xFFFFFF) continue;
            out.add(new int[]{s.getId(), rgb});
            where.add(s.getPosition());
        }
        for (EntityPlayer p : mc.playerEntities) {
            if (!(p instanceof EntityPlayerMP mp)) continue;
            PacketAppearance pk = new PacketAppearance();
            for (int i = 0; i < out.size(); i++) {
                Vec3d v = where.get(i);
                double dx = v.x - p.posX, dz = v.z - p.posZ;
                if (dx * dx + dz * dz < 192 * 192 && pk.ids.size() < 2000) { pk.ids.add(out.get(i)[0]); pk.rgb.add(out.get(i)[1]); }
            }
            RailMap.NETWORK.sendTo(pk, mp);
        }
    }

    /** the operating company's colour for this vehicle's train, or -1 */
    private static int colour(CompanyData cd, EntityMoveableRollingStock s, Map<UUID, Integer> cache) {
        Integer k = cache.get(s.getUUID());
        if (k != null) return k;
        int col = -1;
        List<EntityCoupleableRollingStock> train = s instanceof EntityCoupleableRollingStock cs ? cs.getTrain(false) : Collections.emptyList();
        for (EntityCoupleableRollingStock x : train) {
            Company c = cd.ownerOfTrain(x.getUUID(), "");
            if (x instanceof Locomotive && c != null) { col = c.colour; break; }
        }
        if (col < 0) { Company c = cd.ownerOfTrain(s.getUUID(), ""); if (c != null) col = c.colour; }
        for (EntityCoupleableRollingStock x : train) cache.put(x.getUUID(), col);
        cache.put(s.getUUID(), col);
        return col;
    }

    /** dirt 0..1 (100 % exterior = clean) */
    public static float dirt(FleetData.Unit u) { return u == null ? 0 : Math.max(0, Math.min(1, (100 - u.exterior) / 100f)); }

    /** paint weathering 0..1 */
    public static float weather(FleetData.Unit u, SimData.Car c) {
        if (u == null) return 0;
        double since = c == null || c.paintKm < 0 ? u.km : u.km - c.paintKm;
        return (float) Math.max(0, Math.min(1, since / SimConfig.weatheringKm));
    }

    /** the colour multiplier for a vehicle {r, g, b} */
    public static float[] tint(FleetData.Unit u, SimData.Car c, int liveryRgb) {
        float r = 1, g = 1, b = 1;
        if (liveryRgb >= 0) {
            r = 0.55f + 0.45f * ((liveryRgb >> 16) & 255) / 255f;
            g = 0.55f + 0.45f * ((liveryRgb >> 8) & 255) / 255f;
            b = 0.55f + 0.45f * (liveryRgb & 255) / 255f;
        }
        float wth = weather(u, c);
        r *= 1 - 0.08f * wth; g *= 1 - 0.11f * wth; b *= 1 - 0.20f * wth;            // faded, yellowed paint
        float d = 0.7f * dirt(u);
        r += (0.58f * r - r) * d; g += (0.52f * g - g) * d; b += (0.44f * b - b) * d;   // brake dust and road grime
        float bd = c == null ? 0 : 0.6f * c.body / 100f;
        r += (0.40f * r - r) * bd; g += (0.35f * g - g) * bd; b += (0.31f * b - b) * bd; // scorched, dented panels
        return new float[]{r, g, b};
    }

    static int rgb(float[] t) {
        int r = Math.round(Math.max(0, Math.min(1, t[0])) * 255), g = Math.round(Math.max(0, Math.min(1, t[1])) * 255), b = Math.round(Math.max(0, Math.min(1, t[2])) * 255);
        if (r >= 254 && g >= 254 && b >= 254) return 0xFFFFFF;
        return (r << 16) | (g << 8) | b;
    }

    /** "dirt 34 % · paint 12 % weathered · body 35 % damaged · company livery" */
    public static String line(World mc, EntityMoveableRollingStock s) {
        SimData.Car c = SimData.get(mc).peek(s.getUUID());
        FleetData.Unit u = DepotTicker.live(s.getUUID());
        return String.format(Locale.ROOT, "dirt %d %% · paint %d %% weathered · body %d %% damaged · %s livery", Math.round(dirt(u) * 100), Math.round(weather(u, c) * 100),
                Math.round(c == null ? 0 : c.body), c != null && c.livery == 1 ? "company" : "builder's");
    }
}
