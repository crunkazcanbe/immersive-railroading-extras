package com.dogpound.railmap.weather;

import net.minecraft.world.World;

/** what a cell's culverts and running pumps take away, mm of water per 5 s */
final class Drainage {
    private Drainage() {}

    static double capacity(World w, WeatherData.Cell c, long now) {
        double cap = 0;
        for (TileCulvert t : TileCulvert.REG.all(w)) if (in(c, t.getPos())) cap += t.capacity();
        for (TilePumpStation t : TilePumpStation.REG.all(w)) if (in(c, t.getPos())) cap += t.pumping();
        return cap;
    }

    static boolean in(WeatherData.Cell c, net.minecraft.util.math.BlockPos p) { return p.getX() >> 6 == c.cx && p.getZ() >> 6 == c.cz; }
}
