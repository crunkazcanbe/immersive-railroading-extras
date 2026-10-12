package com.dogpound.railmap.cctv;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /irextras cctv list | cam N | nvr | events | marks N | snap N | ptz N pan tilt [zoom] | preset N P | selftest
 */
public final class CctvCommand {
    private CctvCommand() {}

    public static final String USAGE = "cctv list|cam N|nvr|events|marks N|snap N|ptz N pan tilt [zoom]|preset N P|selftest";

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("cctv")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        CctvData d = CctvData.get(w);
        try {
            switch (sub) {
                case "list" -> {
                    say(s, "§b" + d.cams.size() + " cameras:");
                    for (CctvData.Cam c : d.cams.values())
                        say(s, String.format(Locale.ROOT, " CAM %d %s [%s] %s%s net %s %s dim %d", c.no, c.name, c.kind().label, c.streams() ? "§a" : "§c", CctvData.STATUS[c.status],
                                c.net == 0 ? "-" : Long.toHexString(c.net), c.rec ? "§cREC§r" : "", c.dim));
                }
                case "cam" -> {
                    int n = Integer.parseInt(a[2]);
                    CctvData.Cam c = d.cam(n);
                    if (c == null) { say(s, "No CAM " + n); return true; }
                    say(s, String.format(Locale.ROOT, "CAM %d %s %s: %s (%s) eye %.1f %.1f %.1f yaw %.0f pitch %.0f fov %.1f ir %s gain %.1f fx %d masks '%s'",
                            n, c.name, c.kind().label, CctvData.STATUS[c.status], c.what, c.x, c.y, c.z, c.yaw, c.pitch, c.fov, c.ir, c.gain, c.fx, c.masks));
                    TileCamera t = Cctv.camTile(w, n);
                    if (t != null) say(s, "  tile: 12 V " + t.term().status() + "§r, PTZ pan " + t.pan + "/" + t.panT + " tilt " + t.tilt + "/" + t.tiltT + " zoom " + t.zoom
                            + ", zones " + Long.toHexString(t.zones) + " sens " + t.sens + ", NVR " + t.nvr + ", ring " + Store.used(w, n) + "/" + Store.slots(w, n));
                }
                case "nvr" -> {
                    for (TileNvr n : Cctv.NVRS) if (n.getWorld() == w && !n.isInvalid())
                        say(s, "NVR " + n.getPos() + ": " + n.gearState() + ", net " + Long.toHexString(n.net()) + ", capacity " + n.capacity() + " frames, " + n.perCamera() + "/cam");
                }
                case "events" -> { int i = 0; for (String[] e : d.events) { if (i++ >= 15) break; say(s, " t" + e[0] + " CAM " + e[1] + " " + e[2] + " " + e[3]); } }
                case "marks" -> { int n = Integer.parseInt(a[2]); for (CctvData.Mark m : d.marks) if (m.cam == n) say(s, " t" + m.t + " " + m.text); }
                case "snap" -> {
                    int n = Integer.parseInt(a[2]);
                    TileCamera t = Cctv.camTile(w, n);
                    if (t == null) { say(s, "CAM " + n + " isn't loaded here"); return true; }
                    t.capture(w.getTotalWorldTime());
                    Snap.Frame f = Cctv.LATEST.get(n);
                    if (f == null) { say(s, "CAM " + n + " is not running: " + t.gearState()); return true; }
                    int hot = 0; long lum = 0;
                    for (short p : f.px) { if (Snap.hot(p)) hot++; lum += Snap.luma(p); }
                    say(s, "CAM " + n + " frame " + f.w + "x" + f.h + ": mean luma " + lum / f.px.length + ", hot px " + hot + ", near px " + f.near);
                }
                case "ptz" -> {
                    TileCamera t = Cctv.camTile(w, Integer.parseInt(a[2]));
                    if (t == null) { say(s, "Not loaded"); return true; }
                    String r = t.ptz(Float.parseFloat(a[3]) - t.panT, Float.parseFloat(a[4]) - t.tiltT, a.length > 5 ? Float.parseFloat(a[5]) : 0, 2);
                    say(s, r == null ? "§aMoving" : r);
                }
                case "preset" -> {
                    TileCamera t = Cctv.camTile(w, Integer.parseInt(a[2]));
                    say(s, t == null ? "Not loaded" : t.preset(Integer.parseInt(a[3]), false));
                }
                case "selftest" -> selftest(w, s);
                default -> say(s, "/irextras " + USAGE);
            }
        } catch (RuntimeException e) {
            say(s, "§c" + e + " - /irextras " + USAGE);
        }
        return true;
    }

    private static void selftest(World w, ICommandSender s) {
        List<String> fail = new ArrayList<>();
        int pass = 0;
        // pixel packing
        short p = Snap.pack(0xFF8040, true);
        int back = Snap.rgb(p);
        if (Snap.hot(p) && Math.abs(((back >> 16) & 255) - 255) < 9 && Math.abs(((back >> 8) & 255) - 128) < 9 && Math.abs((back & 255) - 64) < 9) pass++; else fail.add("RGB555 pack/unpack " + Integer.toHexString(back));
        // view basis: yaw 0 looks +Z, yaw 90 looks -X, pitch 90 looks down; orthogonal
        double[][] b0 = Snap.basis(0, 0), b90 = Snap.basis(90, 0), bd = Snap.basis(0, 90);
        if (b0[0][2] > 0.99 && b90[0][0] < -0.99 && bd[0][1] < -0.99 && Math.abs(dot(b0[0], b0[1])) < 1e-9 && Math.abs(dot(b0[0], b0[2])) < 1e-9 && b0[2][1] > 0.99) pass++; else fail.add("camera basis");
        // privacy masks
        int[][] m = Snap.parseMasks("100,100,500,500;900,0,800,100;bad");
        if (m.length == 2 && Snap.masked(m, 0.3, 0.3) && !Snap.masked(m, 0.7, 0.7) && Snap.masked(m, 0.85, 0.05)) pass++; else fail.add("privacy masks");
        // ray-cast where the player stands: a frame of the right size, masked corner black
        if (s instanceof EntityPlayer pl) {
            Snap.Frame f = Snap.cast(w, pl.posX, pl.posY + pl.getEyeHeight(), pl.posZ, pl.rotationYaw, pl.rotationPitch, 60, null, Snap.parseMasks("0,0,200,200"));
            if (f.px.length == CctvConfig.snapW * CctvConfig.snapH && f.px[0] == 0) pass++; else fail.add("ray-cast frame");
        }
        // recording ring: write 3 frames into a 2-slot ring, read back the newest and the overwritten order
        int test = 999_999;
        Store.format(w, test, 2);
        Snap.Frame a = frame(100, (short) 1), bb = frame(200, (short) 2), c = frame(300, (short) 3);
        boolean wr = Store.write(w, test, a) && Store.write(w, test, bb) && Store.write(w, test, c);
        Snap.Frame r = Store.read(w, test, 250), r2 = Store.read(w, test, 50);
        long[] range = Store.range(w, test);
        if (wr && r != null && r.t == 200 && r.px[0] == 2 && r2 != null && r2.t == 200 && range != null && range[0] == 200 && range[1] == 300 && Store.full(w, test)) pass++;
        else fail.add("NVR ring buffer");
        Store.format(w, test, 0);
        // train cameras stream over LTE: every network sees them
        CctvData.Cam probe = new CctvData.Cam();
        probe.type = CamType.TRAIN.ordinal();
        if (Cctv.canSee(probe, 0)) pass++; else fail.add("train cameras visible on every network");
        say(s, (fail.isEmpty() ? "§aCCTV self-test PASS " : "§cCCTV self-test: " + fail.size() + " FAILED ") + pass + " ok" + (fail.isEmpty() ? "" : " - " + String.join(", ", fail)));
    }

    private static Snap.Frame frame(long t, short v) {
        Snap.Frame f = new Snap.Frame();
        f.t = t; f.w = CctvConfig.snapW; f.h = CctvConfig.snapH; f.px = new short[f.w * f.h];
        java.util.Arrays.fill(f.px, v);
        return f;
    }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }

    static BlockPos at(ICommandSender s) { return s.getPosition(); }
}
