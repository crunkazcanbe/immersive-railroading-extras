package com.dogpound.railmap.freight6;

import com.dogpound.railmap.freight.CargoRules;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;

/**
 * The grain elevator complex's shared sums (batch 6 #26): which bins belong to a plant and in what order, moisture of
 * grain coming off the field, weights. 1 item of grain = 50 kg.
 */
public final class Grain {
    private Grain() {}

    public static final double T_PER_ITEM = 0.05;
    public static final int MAX_BINS = 6;
    /** safe storage moisture, % wet basis */
    public static final float SAFE = 15f;

    public static boolean isGrain(ItemStack s) { return !s.isEmpty() && CargoRules.cargo(s) == CargoRules.Cargo.GRAIN; }

    /** the plant's bins, BIN 1..6 in a fixed order (west to east, then north to south) so every machine numbers them the same */
    public static List<TileGrainSilo> bins(World w, BlockPos at) {
        List<TileGrainSilo> l = StnUtil.near(w, TileGrainSilo.class, at, 24);
        l.sort(Comparator.comparingInt((TileGrainSilo s) -> s.getPos().getX()).thenComparingInt(s -> s.getPos().getZ()).thenComparingInt(s -> s.getPos().getY()));
        return l.size() > MAX_BINS ? l.subList(0, MAX_BINS) : l;
    }

    public static TileGrainSilo bin(World w, BlockPos at, int n) {
        List<TileGrainSilo> l = bins(w, at);
        return n >= 0 && n < l.size() ? l.get(n) : null;
    }

    /** moisture of grain arriving off the field: wetter in rainy biomes and when it is raining, 11-24 % */
    public static float fieldMoisture(World w, BlockPos p) {
        float rain = w.getBiome(p).getRainfall();
        float m = 12.5f + rain * 5f + (w.isRainingAt(p.up(2)) || w.isRaining() ? 3.5f : 0f);
        return Math.max(11f, Math.min(24f, m));
    }

    /** mix `addN` items of `addM` % into `n` items of `m` % */
    public static float mix(float m, long n, float addM, long addN) { return n + addN <= 0 ? addM : (float) ((m * n + addM * addN) / (double) (n + addN)); }

    public static String t(long items) { return String.format(java.util.Locale.ROOT, "%.1f", items * T_PER_ITEM); }

    public static String name(ItemStack s) { return s == null || s.isEmpty() ? "EMPTY" : s.getDisplayName(); }
}
