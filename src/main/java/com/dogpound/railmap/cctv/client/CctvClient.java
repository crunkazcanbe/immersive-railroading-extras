package com.dogpound.railmap.cctv.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.cctv.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The client's CCTV state: the camera directory from the server, and what to put on a screen for a camera number -
 * the live framebuffer (camera in a chunk your client has, or a loaded train), else the server's snapshot (requested
 * every few seconds), else NO SIGNAL with the reason. Playback frames and timelines too.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class CctvClient {
    private CctvClient() {}

    public static final TreeMap<Integer, CctvData.Cam> DIR = new TreeMap<>();

    /** what a screen draws for one camera */
    public static final class Pic {
        public int tex = -1, look, fx, size = 1;
        public boolean flip, fisheye, live, hotAlpha;
        public float gain = 1;
        public String msg = "";
        public int[][] masks = new int[0][];
        public long t;
        public CctvData.Cam cam;
    }

    private static final class Snapshot { DynamicTexture tex; int w, h; long t, asked; }
    private static final Map<Integer, Snapshot> SNAPS = new HashMap<>(), PB = new HashMap<>();
    /** timelines: cam -> {lo, hi}, and the bookmarks text */
    public static final Map<Integer, long[]> RANGES = new HashMap<>();
    public static final Map<Integer, String> MARKS = new HashMap<>();
    private static final Map<Integer, String> NOTE = new HashMap<>();

    public static void dir(PacketCctvDir m) {
        DIR.clear();
        for (CctvData.Cam c : m.cams) DIR.put(c.no, c);
    }

    public static void frame(PacketCctvFrame m) {
        if (m.kind == 2) { RANGES.put(m.cam, new long[]{m.lo, m.hi}); MARKS.put(m.cam, m.info); return; }
        if (m.kind == 0) NOTE.put(m.cam, m.info);
        if (m.w <= 0 || m.h <= 0) return;
        Map<Integer, Snapshot> into = m.kind == 1 ? PB : SNAPS;
        Snapshot s = into.get(m.cam);
        if (s == null || s.w != m.w || s.h != m.h) {
            if (s != null && s.tex != null) s.tex.deleteGlTexture();
            s = new Snapshot();
            s.w = m.w; s.h = m.h;
            s.tex = new DynamicTexture(m.w, m.h);
            into.put(m.cam, s);
        }
        int[] d = s.tex.getTextureData();
        for (int i = 0; i < m.px.length && i < d.length; i++) d[i] = (Snap.hot(m.px[i]) ? 0xFF000000 : 0x00000000) | Snap.rgb(m.px[i]);
        s.tex.updateDynamicTexture();
        s.t = m.t;
    }

    static boolean canSee(CctvData.Cam c, long net) { return Cctv.canSee(c, net); }

    /** where a camera is right now for a live render: {x, y, z, yaw, pitch, fov}, or null if this client can't render it */
    static double[] livePose(int no, float pt) {
        if (no <= -1000) return com.dogpound.railmap.cine.client.CineDeskView.feedPose(no, pt);   // director desk preview / program monitors (batch 5 #10)
        CctvData.Cam c = DIR.get(no);
        Minecraft mc = Minecraft.getMinecraft();
        if (c == null || mc.world == null || c.dim != mc.world.provider.getDimension()) return null;
        if (!c.train.isEmpty()) {
            double[] p = TrainCams.pose(mc.world, c.train, pt);
            return p == null ? null : new double[]{p[0], p[1], p[2], p[3], p[4], c.fov};
        }
        BlockPos at = BlockPos.fromLong(c.pos);
        if (!mc.world.isBlockLoaded(at) || !(mc.world.getTileEntity(at) instanceof TileCamera t)) return null;
        return t.pose(true);
    }

    /** the picture for camera `no` on a screen of network `net` */
    public static Pic pic(int no, long net) { return pic(no, net, false); }

    /** direct = the configurator plugged into the camera itself (no network needed) */
    public static Pic pic(int no, long net, boolean direct) { return pic(no, net, direct, true); }

    /** a small thumbnail: the server snapshot only (never costs a live render) */
    public static Pic thumb(int no) { return pic(no, 0, true, false); }

    private static Pic pic(int no, long net, boolean direct, boolean allowLive) {
        Pic p = new Pic();
        CctvData.Cam c = DIR.get(no);
        p.cam = c;
        if (no <= 0 || c == null) { p.msg = "NO CAMERA"; return p; }
        if (!direct && !canSee(c, net)) { p.msg = "NOT ON THIS NETWORK"; return p; }
        if (!c.streams() && !(direct && c.status == CctvData.NO_NET)) { p.msg = CctvData.STATUS[c.status]; return p; }
        CamType k = c.kind();
        p.look = k.look(c.ir);
        p.gain = c.gain;
        p.fx = c.fx;
        p.fisheye = k.fisheye();
        p.masks = Snap.parseMasks(c.masks);
        if (allowLive && livePose(no, 1f) != null && CctvFeeds.usable()) {
            int tex = CctvFeeds.texture(no, p.look);
            if (tex >= 0) { p.tex = tex; p.flip = true; p.live = true; p.hotAlpha = p.look == 2; p.size = CctvConfig.liveRes; return p; }
            if (!CctvFeeds.rendering) { p.msg = "CONNECTING"; }
        }
        Snapshot s = SNAPS.get(no);
        long now = System.currentTimeMillis();
        if (s == null || now - s.asked > CctvConfig.snapshotSeconds * 1000L) {
            if (s == null) { s = new Snapshot(); SNAPS.put(no, s); }
            s.asked = now;
            RailMap.NETWORK.sendToServer(new PacketCctvOp(PacketCctvOp.SNAP, no, 0, 0, ""));
        }
        if (s.tex == null) { if (p.msg.isEmpty()) p.msg = NOTE.getOrDefault(no, "").isEmpty() ? "NO SIGNAL - OUT OF RANGE" : "NO SIGNAL - " + NOTE.get(no); return p; }
        p.msg = "";
        p.tex = s.tex.getGlTextureId();
        p.hotAlpha = true;
        p.size = s.w;
        p.t = s.t;
        return p;
    }

    /** a recorded frame for playback near time t */
    public static Pic playback(int no, long t) {
        Pic p = new Pic();
        CctvData.Cam c = DIR.get(no);
        p.cam = c;
        if (c != null) { CamType k = c.kind(); p.look = k.look(c.ir); p.fisheye = k.fisheye(); p.gain = c.gain; }
        Snapshot s = PB.get(no);
        long now = System.currentTimeMillis();
        long step = CctvConfig.recordSeconds * 20L;
        if (s == null || Math.abs(s.t - t) > step && now - s.asked > 150) {
            if (s == null) { s = new Snapshot(); PB.put(no, s); }
            s.asked = now;
            RailMap.NETWORK.sendToServer(new PacketCctvOp(PacketCctvOp.PB_FRAME, no, 0, t, ""));
        }
        if (s.tex == null) { p.msg = "NO RECORDING"; return p; }
        p.tex = s.tex.getGlTextureId();
        p.hotAlpha = true;
        p.size = s.w;
        p.t = s.t;
        return p;
    }

    public static void askRange(int cam) { RailMap.NETWORK.sendToServer(new PacketCctvOp(PacketCctvOp.RANGE, cam, 0, 0, "")); }

    // ---- GUIs ----------------------------------------------------------------------------------------------------------
    public static void openCamera(BlockPos pos) { Minecraft.getMinecraft().displayGuiScreen(new GuiCamera(pos)); }
    public static void openMonitor(BlockPos pos) { Minecraft.getMinecraft().displayGuiScreen(new GuiMonitor(pos)); }

    // ---- models ---------------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        for (net.minecraft.block.Block b : CctvRegistry.BLOCKS)
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b), 0, new ModelResourceLocation(b.getRegistryName(), "inventory"));
        // the PTZ's moving parts are baked as extra variants of its item, drawn turned by CctvScreens
        Item ptz = Item.getItemFromBlock(CctvRegistry.ptz);
        ModelBakery.registerItemVariants(ptz, new ResourceLocation(RailMap.MODID, "cctv_ptz"), new ResourceLocation(RailMap.MODID, "cctv_ptz_pan"), new ResourceLocation(RailMap.MODID, "cctv_ptz_head"));
        for (int i = 0; i < ItemDrive.IDS.length; i++)
            ModelLoader.setCustomModelResourceLocation(CctvRegistry.drive, i, new ModelResourceLocation(RailMap.MODID + ":cctv_drive_" + ItemDrive.IDS[i], "inventory"));
        ModelLoader.setCustomModelResourceLocation(CctvRegistry.trainCamera, 0, new ModelResourceLocation(CctvRegistry.trainCamera.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(CctvRegistry.configurator, 0, new ModelResourceLocation(CctvRegistry.configurator.getRegistryName(), "inventory"));
    }
}
