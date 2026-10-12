package com.dogpound.railmap.cctv;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

import java.util.List;

/**
 * The server's own picture of what a camera sees: a software voxel ray-cast through the loaded chunks (block map
 * colours, face shading, real sky / block light, distance haze) plus entity silhouettes. It is what the NVR records,
 * what motion detection and tamper detection look at, and the fallback picture for a camera your client can't render
 * (its chunk isn't loaded on your side). Pixels are RGB555 with bit 15 = "hot" (a living thing: thermal cameras).
 */
public final class Snap {
    private Snap() {}

    public static final class Frame {
        public long t;
        public int w, h;
        public short[] px;
        /** pixels whose ray hit something within 1.2 blocks (lens covered) */
        public int near;
    }

    /** view basis: forward, right, up for MC yaw / pitch (pitch + = looking down) */
    public static double[][] basis(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double[] f = {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
        double[] r = {-Math.cos(y), 0, -Math.sin(y)};
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
        return new double[][]{f, r, u};
    }

    public static short pack(int rgb, boolean hot) {
        int r = (rgb >> 19) & 31, g = (rgb >> 11) & 31, b = (rgb >> 3) & 31;
        return (short) ((hot ? 0x8000 : 0) | (r << 10) | (g << 5) | b);
    }

    public static int rgb(short p) {
        int r = (p >> 10) & 31, g = (p >> 5) & 31, b = p & 31;
        return ((r << 3) | (r >> 2)) << 16 | ((g << 3) | (g >> 2)) << 8 | ((b << 3) | (b >> 2));
    }

    public static boolean hot(short p) { return (p & 0x8000) != 0; }

    public static int luma(short p) { int c = rgb(p); return (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10; }

    /** masks "x0,y0,x1,y1;..." in 0..1000 of the picture: is (u, v) 0..1 inside one */
    public static boolean masked(int[][] masks, double u, double v) {
        for (int[] m : masks) if (u * 1000 >= m[0] && u * 1000 <= m[2] && v * 1000 >= m[1] && v * 1000 <= m[3]) return true;
        return false;
    }

    public static int[][] parseMasks(String s) {
        if (s == null || s.isEmpty()) return new int[0][];
        String[] a = s.split(";");
        java.util.List<int[]> o = new java.util.ArrayList<>();
        for (String m : a) {
            String[] p = m.split(",");
            if (p.length != 4) continue;
            try {
                int x0 = Integer.parseInt(p[0].trim()), y0 = Integer.parseInt(p[1].trim()), x1 = Integer.parseInt(p[2].trim()), y1 = Integer.parseInt(p[3].trim());
                o.add(new int[]{Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1)});
            } catch (NumberFormatException ignored) { }
            if (o.size() >= 4) break;
        }
        return o.toArray(new int[0][]);
    }

    private static boolean solid(IBlockState s) {
        Material m = s.getMaterial();
        return m != Material.AIR && (m.blocksMovement() || m.isLiquid() || m == Material.LEAVES || m == Material.SNOW) && m != Material.GLASS;
    }

    /** ray-cast one frame from (x, y, z) looking yaw / pitch with a vertical fov (deg); ownPos is skipped */
    public static Frame cast(World w, double x, double y, double z, float yaw, float pitch, float fov, BlockPos ownPos, int[][] masks) {
        int W = CctvConfig.snapW, H = CctvConfig.snapH, R = CctvConfig.snapRange;
        Frame fr = new Frame();
        fr.t = w.getTotalWorldTime();
        fr.w = W; fr.h = H;
        fr.px = new short[W * H];
        double[] dist = new double[W * H];
        double[][] b = basis(yaw, pitch);
        double[] f = b[0], r = b[1], u = b[2];
        double th = Math.tan(Math.toRadians(Math.min(fov, 150) / 2)), asp = W / (double) H;
        int sub = w.getSkylightSubtracted();
        float sun = 1f - sub / 11f;
        int sky = skyColour(sun);
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++) {
                double sx = (2 * (i + 0.5) / W - 1) * th * asp, sy = (1 - 2 * (j + 0.5) / H) * th;
                double dx = f[0] + r[0] * sx + u[0] * sy, dy = f[1] + r[1] * sx + u[1] * sy, dz = f[2] + r[2] * sx + u[2] * sy;
                double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= n; dy /= n; dz /= n;
                int k = j * W + i;
                dist[k] = R;
                int c = sky;
                // Amanatides-Woo voxel walk
                int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
                int stx = dx > 0 ? 1 : -1, sty = dy > 0 ? 1 : -1, stz = dz > 0 ? 1 : -1;
                double tdx = Math.abs(1 / dx), tdy = Math.abs(1 / dy), tdz = Math.abs(1 / dz);
                double tmx = (dx > 0 ? bx + 1 - x : x - bx) * tdx, tmy = (dy > 0 ? by + 1 - y : y - by) * tdy, tmz = (dz > 0 ? bz + 1 - z : z - bz) * tdz;
                int face = 1;
                double t = 0;
                BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
                int px = bx, py = by, pz = bz;
                while (t < R) {
                    px = bx; py = by; pz = bz;
                    if (tmx < tmy && tmx < tmz) { bx += stx; t = tmx; tmx += tdx; face = 0; }
                    else if (tmy < tmz) { by += sty; t = tmy; tmy += tdy; face = 1; }
                    else { bz += stz; t = tmz; tmz += tdz; face = 2; }
                    if (by < 0 || by > 255) break;
                    mp.setPos(bx, by, bz);
                    if (ownPos != null && mp.equals(ownPos)) continue;
                    if (!w.isBlockLoaded(mp)) break;
                    IBlockState s = w.getBlockState(mp);
                    if (!solid(s)) continue;
                    MapColor mc = s.getMapColor(w, mp);
                    int col = mc == null || mc.colorValue == 0 ? 0x808080 : mc.colorValue;
                    double shade = face == 1 ? (sty < 0 ? 1.0 : 0.55) : face == 0 ? 0.8 : 0.9;
                    BlockPos lp = new BlockPos(px, py, pz);
                    int sl = Math.max(0, w.getLightFor(EnumSkyBlock.SKY, lp) - sub), bl = w.getLightFor(EnumSkyBlock.BLOCK, lp);
                    double light = 0.12 + 0.88 * Math.max(sl, bl) / 15.0;
                    double haze = Math.min(1, t / R);
                    haze = haze * haze;
                    c = mix(scale(col, shade * light), sky, haze);
                    dist[k] = t;
                    break;
                }
                if (dist[k] < 1.2) fr.near++;
                fr.px[k] = pack(c, false);
            }
        }
        // entities: silhouettes (their box projected), in front of the blocks they stand before
        List<Entity> es = w.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(x - R, y - R, z - R, x + R, y + R, z + R),
                e -> !(e instanceof EntityItem) && !(e instanceof EntityXPOrb) && e.isEntityAlive());
        for (Entity e : es) {
            AxisAlignedBB bb = e.getEntityBoundingBox();
            if (bb.getAverageEdgeLength() > 12) bb = bb.shrink(0.1);
            int minI = W, maxI = -1, minJ = H, maxJ = -1;
            double depth = Double.MAX_VALUE;
            boolean behind = false;
            for (int q = 0; q < 8; q++) {
                double cx = ((q & 1) == 0 ? bb.minX : bb.maxX) - x, cy = ((q & 2) == 0 ? bb.minY : bb.maxY) - y, cz = ((q & 4) == 0 ? bb.minZ : bb.maxZ) - z;
                double fz = cx * f[0] + cy * f[1] + cz * f[2];
                if (fz < 0.2) { behind = true; break; }
                double sx = (cx * r[0] + cy * r[1] + cz * r[2]) / fz, sy = (cx * u[0] + cy * u[1] + cz * u[2]) / fz;
                int ii = (int) Math.floor((sx / (th * asp) + 1) / 2 * W), jj = (int) Math.floor((1 - sy / th) / 2 * H);
                minI = Math.min(minI, ii); maxI = Math.max(maxI, ii); minJ = Math.min(minJ, jj); maxJ = Math.max(maxJ, jj);
                depth = Math.min(depth, fz);
            }
            if (behind || depth > R) continue;
            boolean live = e instanceof EntityLivingBase;
            int col = e instanceof EntityPlayer ? 0x3A5AC8 : e instanceof IMob ? 0x6A9A3A : live ? 0x9A7048 : 0x5A5E66;
            int sl = Math.max(0, w.getLightFor(EnumSkyBlock.SKY, e.getPosition()) - sub), bl = w.getLightFor(EnumSkyBlock.BLOCK, e.getPosition());
            int lc = scale(col, 0.15 + 0.85 * Math.max(sl, bl) / 15.0);
            for (int j = Math.max(0, minJ); j <= Math.min(H - 1, maxJ); j++)
                for (int i = Math.max(0, minI); i <= Math.min(W - 1, maxI); i++) {
                    int k = j * W + i;
                    if (dist[k] > depth) { fr.px[k] = pack(lc, live); dist[k] = depth; }
                }
        }
        for (int j = 0; j < H; j++)
            for (int i = 0; i < W; i++)
                if (masked(masks, (i + 0.5) / W, (j + 0.5) / H)) fr.px[j * W + i] = 0;
        return fr;
    }

    static int skyColour(float sun) {
        return mix(0x0A0E1C, 0x8AB4F0, Math.max(0, Math.min(1, sun)));
    }

    static int scale(int c, double k) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * k), g = (int) Math.min(255, ((c >> 8) & 255) * k), b = (int) Math.min(255, (c & 255) * k);
        return r << 16 | g << 8 | b;
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
