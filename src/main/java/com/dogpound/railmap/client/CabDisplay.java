package com.dogpound.railmap.client;

import com.dogpound.railmap.network.PacketCab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The driver's display in the cab (moving block / train protection), drawn like a real CBTC / ETCS screen: the mode
 * bar on top, a big speed readout, a speed bar with the permitted-speed hook, and on the left the distance-to-go
 * column that drains as the train closes on its limit of authority. Shown while the server keeps sending it (riding a
 * protected or driverless train); fades out a second after it stops.
 */
public final class CabDisplay {
    private CabDisplay() {}

    private static PacketCab cur;
    private static long gotAt;
    private static int lastAids;

    /** the driver's acknowledge key (vigilance, AWS, SPAD / penalty reset) */
    public static net.minecraft.client.settings.KeyBinding ACK;

    /** opens the full driver's display (client.GuiDmi) with its clickable soft keys */
    public static net.minecraft.client.settings.KeyBinding DMI;

    /** client pre-init: register the cab keys */
    public static void initKeys() {
        ACK = new net.minecraft.client.settings.KeyBinding("key.irextras.cab_ack", org.lwjgl.input.Keyboard.KEY_BACKSLASH, "key.categories.irextras");
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(ACK);
        DMI = new net.minecraft.client.settings.KeyBinding("key.irextras.cab_dmi", org.lwjgl.input.Keyboard.KEY_RBRACKET, "key.categories.irextras");
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(DMI);
        StockTint.init();
    }

    /** the last display data while it is fresh (under 2 s old), else null */
    public static PacketCab current() {
        Minecraft mc = Minecraft.getMinecraft();
        if (cur == null || mc.world == null) return null;
        long age = mc.world.getTotalWorldTime() - gotAt;
        return age < 0 || age > 40 ? null : cur;
    }

    public static void accept(PacketCab p) {
        int was = cur == null ? 0 : cur.aids;
        cur = p;
        gotAt = Minecraft.getMinecraft().world == null ? 0 : Minecraft.getMinecraft().world.getTotalWorldTime();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && (p.aids & com.dogpound.railmap.auto.DriverAids.AWS_BELL) != 0 && (was & com.dogpound.railmap.auto.DriverAids.AWS_BELL) == 0)
            mc.player.playSound(com.dogpound.railmap.ModSounds.BEEP_OK, 1f, 1.0f);
        if (mc.player != null && (p.aids & com.dogpound.railmap.auto.DriverAids.SPAD) != 0 && (was & com.dogpound.railmap.auto.DriverAids.SPAD) == 0)
            mc.player.playSound(com.dogpound.railmap.ModSounds.ALARM, 1f, 1f);
        lastAids = p.aids;
    }

    /** cab sounds while the server keeps the warnings up, and the acknowledge key */
    @SubscribeEvent
    public static void clientTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent e) {
        if (e.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;
        if (ACK != null && ACK.isPressed() && mc.player.getRidingEntity() != null)
            com.dogpound.railmap.RailMap.NETWORK.sendToServer(new com.dogpound.railmap.network.PacketCabAck());
        if (DMI != null && DMI.isPressed() && mc.player.getRidingEntity() != null && mc.currentScreen == null)
            mc.displayGuiScreen(new com.dogpound.railmap.client.gui.GuiDmi());
        if (cur == null || mc.player.getRidingEntity() == null) return;
        long age = mc.world.getTotalWorldTime() - gotAt;
        if (age < 0 || age > 25) return;
        long t = mc.world.getTotalWorldTime();
        int a = cur.aids;
        if ((a & com.dogpound.railmap.auto.DriverAids.CAB_SIG) != 0 && t % 6 == 0)            // batch-3 #13 cab signal alerter
            mc.player.playSound(com.dogpound.railmap.ModSounds.ALARM_HORN, 0.7f, 1.2f);
        else if ((a & com.dogpound.railmap.auto.DriverAids.AWS_HORN) != 0 && t % 4 == 0)
            mc.player.playSound(com.dogpound.railmap.ModSounds.BUZZER, 0.9f, 1.0f);
        else if ((a & com.dogpound.railmap.auto.DriverAids.VIG_ALERT) != 0 && t % 10 == 0)
            mc.player.playSound(com.dogpound.railmap.ModSounds.BEEP_OK, 0.8f, 1.0f);
        else if ((a & com.dogpound.railmap.auto.DriverAids.PENALTIES) != 0 && t % 20 == 0)
            mc.player.playSound(com.dogpound.railmap.ModSounds.BEEP_ERROR, 0.7f, 1.0f);
    }

    private static String ackKey() {
        return ACK == null ? "\\" : ACK.getDisplayName();
    }

    @SubscribeEvent
    public static void draw(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.ALL || cur == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || mc.gameSettings.hideGUI || mc.player.getRidingEntity() == null) return;
        if (mc.currentScreen instanceof com.dogpound.railmap.client.gui.GuiDmi) return;
        long age = mc.world.getTotalWorldTime() - gotAt;
        if (age > 40 || age < 0) return;
        float alpha = age > 20 ? 1 - (age - 20) / 20f : 1;
        PacketCab p = cur;
        FontRenderer f = mc.fontRenderer;
        ScaledResolution sr = new ScaledResolution(mc);
        int w = 156, h = 104;
        int x = sr.getScaledWidth() - w - 6, y = sr.getScaledHeight() - h - 46;
        int a8 = (int) (alpha * 230) << 24;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        // bezel + screen
        Gui.drawRect(x - 2, y - 2, x + w + 2, y + h + 2, a8 | 0x2A2E33);
        Gui.drawRect(x, y, x + w, y + h, a8 | 0x0A1018);

        // mode bar
        boolean cbtc = p.mode.startsWith("CBTC");
        int modeCol = cbtc ? 0x1E88E5 : 0x6D4C41;
        Gui.drawRect(x, y, x + w, y + 11, a8 | modeCol);
        f.drawString(p.mode.isEmpty() ? "ATP" : p.mode, x + 3, y + 2, 0xFFFFFF | a8);
        String lim = p.limit < 998 ? "Line " + Math.round(p.limit) : "";
        f.drawString(lim, x + w - 3 - f.getStringWidth(lim), y + 2, 0xCFE8FF | a8);

        // distance-to-go column (0..1000 m, the last 200 m stretched so the final approach is easy to read)
        int cx = x + 4, cy = y + 15, ch = h - 19, cw = 10;
        Gui.drawRect(cx, cy, cx + cw, cy + ch, a8 | 0x1B2733);
        if (p.distanceToGo >= 0) {
            float d = p.distanceToGo;
            float frac = d <= 200 ? d / 200f * 0.6f : Math.min(1f, 0.6f + (d - 200) / 800f * 0.4f);
            int fill = (int) (ch * frac);
            int col = d < 50 ? 0xE53935 : d < 200 ? 0xFFB300 : 0x43A047;
            Gui.drawRect(cx, cy + ch - fill, cx + cw, cy + ch, a8 | col);
            for (int m : new int[]{50, 100, 200, 500}) {
                float fr = m <= 200 ? m / 200f * 0.6f : 0.6f + (m - 200) / 800f * 0.4f;
                int ty = cy + ch - (int) (ch * fr);
                Gui.drawRect(cx + cw, ty, cx + cw + 3, ty + 1, a8 | 0x90A4AE);
            }
        } else {
            f.drawString("∞", cx + 2, cy + ch / 2 - 4, 0x43A047 | a8);
        }

        // big speed
        int sx = x + 20;
        String sp = String.valueOf(Math.round(Math.abs(p.speed)));
        GlStateManager.pushMatrix();
        GlStateManager.translate(sx, y + 15, 0);
        GlStateManager.scale(2.4f, 2.4f, 1);
        boolean over = p.speed > p.target + 3;
        boolean near = !over && p.speed > p.target - 5;
        f.drawString(sp, 0, 0, (over ? 0xFF5252 : near ? 0xFFD54F : 0xFFFFFF) | a8);
        GlStateManager.popMatrix();
        f.drawString("km/h", sx + (int) (f.getStringWidth(sp) * 2.4f) + 3, y + 26, 0x90A4AE | a8);

        // permitted / target
        String tg = p.target < 998 ? String.valueOf(Math.round(p.target)) : "--";
        f.drawString("Permitted", x + w - 3 - f.getStringWidth("Permitted"), y + 15, 0x90A4AE | a8);
        f.drawString(tg, x + w - 3 - f.getStringWidth(tg), y + 25, (p.target < 5 ? 0xFF5252 : 0xFFB300) | a8);

        // speed bar: 0 .. scale, current fill, permitted hook
        float scale = Math.max(40, Math.max(Math.abs(p.speed), Math.min(p.limit < 998 ? p.limit : 120, 250)) * 1.1f);
        int bx = sx, by = y + 42, bw = x + w - 4 - sx, bh = 6;
        Gui.drawRect(bx, by, bx + bw, by + bh, a8 | 0x1B2733);
        int fillW = (int) (bw * Math.min(1, Math.abs(p.speed) / scale));
        Gui.drawRect(bx, by, bx + fillW, by + bh, a8 | (over ? 0xE53935 : near ? 0xFFB300 : 0x43A047));
        if (p.target < 998) {
            int tx = bx + (int) (bw * Math.min(1, p.target / scale));
            Gui.drawRect(tx - 1, by - 3, tx + 1, by + bh + 3, a8 | 0xFFB300);
        }

        // text lines
        int ty = y + 53;
        if (p.distanceToGo >= 0) {
            f.drawString(String.format("Go %d m", Math.round(p.distanceToGo)), sx, ty, 0xFFFFFF | a8);
            ty += 10;
            String why = p.limitedBy + (p.gap >= 0 ? String.format(" (%d m)", Math.round(p.gap)) : "");
            f.drawString(f.trimStringToWidth(why, w - 22), sx, ty, 0xB0BEC5 | a8);
        } else {
            f.drawString("Line clear ahead", sx, ty, 0x81C784 | a8);
            ty += 10;
        }
        ty += 10;
        if (!p.nextStop.isEmpty()) {
            String ns = p.stopM >= 0 ? String.format("Next: %s %s", p.nextStop, p.stopM >= 1000 ? String.format("%.1f km", p.stopM / 1000) : Math.round(p.stopM) + " m") : "Next: " + p.nextStop;
            f.drawString(f.trimStringToWidth(ns, w - 22), sx, ty, 0x80CBC4 | a8);
            ty += 10;
        }
        if (!p.note.isEmpty()) f.drawString(f.trimStringToWidth(p.note, w - 22), sx, ty, 0xFF8A65 | a8);
        String hint = "[" + (DMI == null ? "]" : DMI.getDisplayName()) + "] DMI";
        f.drawString(hint, x + w - 3 - f.getStringWidth(hint), y + h - 9, 0x546E7A | a8);
        GlStateManager.popMatrix();
        systems(mc, f, sr, p, x - 6, y + h, a8, alpha);
        banners(mc, f, sr, p, a8);
    }

    // ---- the train-systems panel: lamps, advisory, engine, consist (sim.CabData) ----------------------------------------

    private static final String[] LAMPS = {"SLIP", "SLIDE", "WSP", "SAND", "TC", "FLT", "PWR", "OVLD", "AESS", "BATT", "HOT", "LOW SAND"};
    private static final int[] LAMP_BITS = {com.dogpound.railmap.sim.CabData.SLIP, com.dogpound.railmap.sim.CabData.SLIDE, com.dogpound.railmap.sim.CabData.WSP,
            com.dogpound.railmap.sim.CabData.SAND, com.dogpound.railmap.sim.CabData.TC, com.dogpound.railmap.sim.CabData.FAULT, com.dogpound.railmap.sim.CabData.DERATE,
            com.dogpound.railmap.sim.CabData.OVERLOAD, com.dogpound.railmap.sim.CabData.AESS, com.dogpound.railmap.sim.CabData.LOW_BATT, com.dogpound.railmap.sim.CabData.HOT,
            com.dogpound.railmap.sim.CabData.SAND_LOW};
    private static final int[] LAMP_COL = {0xFFB300, 0xFFB300, 0x4FC3F7, 0xE0C080, 0x66BB6A, 0xFF5252, 0xFFB300, 0xFF5252, 0x90CAF9, 0xFF7043, 0xFF5252, 0xFFB300};

    private static void systems(Minecraft mc, FontRenderer f, ScaledResolution sr, PacketCab p, int right, int bottom, int a8, float alpha) {
        boolean any = !p.eng1.isEmpty() || !p.phys.isEmpty() || !p.adh.isEmpty() || !p.advice.isEmpty();
        if (!any && p.aids == 0) return;
        int w = 170;
        int lines = (p.advice.isEmpty() ? 0 : 1) + (p.eng1.isEmpty() ? 0 : 2) + (p.phys.isEmpty() ? 0 : 3) + (p.adh.isEmpty() ? 0 : 1) + (p.fault.isEmpty() ? 0 : 1);
        int h = 13 + 22 + lines * 8 + 4;
        int x = right - w, y = bottom - h;
        Gui.drawRect(x - 2, y - 2, x + w + 2, y + h + 2, a8 | 0x2A2E33);
        Gui.drawRect(x, y, x + w, y + h, a8 | 0x0A1018);
        Gui.drawRect(x, y, x + w, y + 11, a8 | 0x37474F);
        f.drawString("TRAIN SYSTEMS", x + 3, y + 2, 0xFFFFFF | a8);
        // AWS sunflower: black = clear, black/yellow = a restrictive aspect was acknowledged
        int sx = x + w - 12, sy = y + 1;
        Gui.drawRect(sx, sy, sx + 9, sy + 9, a8 | 0x101010);
        if ((p.aids & com.dogpound.railmap.auto.DriverAids.AWS_SUNFLOWER) != 0)
            for (int i = 0; i < 9; i += 2) Gui.drawRect(sx + i, sy, sx + i + 1, sy + 9, a8 | 0xFFD600);
        f.drawString("AWS", sx - 20, y + 2, 0x90A4AE | a8);
        // lamp row (two rows of six)
        boolean blink = (mc.world.getTotalWorldTime() / 6) % 2 == 0;
        for (int i = 0; i < LAMPS.length; i++) {
            int lx = x + 2 + (i % 6) * 28, ly = y + 13 + (i / 6) * 11;
            boolean lit = (p.aids & LAMP_BITS[i]) != 0;
            if (i == 4) lit = (p.aids & com.dogpound.railmap.sim.CabData.TC) != 0;
            boolean flash = lit && (i == 0 || i == 1 || i == 7);
            int col = lit && (!flash || blink) ? LAMP_COL[i] : 0x263238;
            Gui.drawRect(lx, ly, lx + 26, ly + 9, a8 | col);
            GlStateManager.pushMatrix();
            GlStateManager.translate(lx + 13, ly + 2, 0);
            GlStateManager.scale(0.6f, 0.6f, 1);
            String t = LAMPS[i];
            f.drawString(t, -f.getStringWidth(t) / 2, 0, (lit ? 0x101010 : 0x607D8B) | a8);
            GlStateManager.popMatrix();
        }
        int ty = y + 36;
        if (!p.advice.isEmpty()) {
            int col = p.advice.startsWith("BRAKE") ? 0xFF8A65 : p.advice.startsWith("COAST") ? 0x4DD0E1 : p.advice.startsWith("POWER") ? 0x81C784 : 0xECEFF1;
            f.drawString(f.trimStringToWidth("DAS: " + p.advice, w - 4), x + 3, ty, col | a8);
            ty += 8;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 3, ty, 0);
        GlStateManager.scale(0.75f, 0.75f, 1);
        int ly = 0, tw = (int) ((w - 6) / 0.75f);
        for (String s : new String[]{p.eng1, p.eng2, p.adh}) {
            if (s.isEmpty()) continue;
            f.drawString(f.trimStringToWidth(s, tw), 0, ly, 0xCFD8DC | a8);
            ly += 11;
        }
        if (!p.phys.isEmpty()) {
            f.drawString(f.trimStringToWidth(p.phys, tw), 0, ly, 0xB0BEC5 | a8);
            ly += 11;
            // drawbar gauge: buff (compression) to the left, draft (tension) to the right, +-2.5 MN, limits at 1 MN
            int gw = tw - 20, gx = 10, mid = gx + gw / 2;
            Gui.drawRect(gx, ly + 2, gx + gw, ly + 8, a8 | 0x1B2733);
            float scale = 2.5e6f;
            int tr = (int) (gw / 2f * Math.min(1f, p.drawbar / scale)), cl = (int) (gw / 2f * Math.min(1f, p.buff / scale));
            Gui.drawRect(mid, ly + 2, mid + tr, ly + 8, a8 | (p.drawbar > 1e6f ? 0xE53935 : 0x43A047));
            Gui.drawRect(mid - cl, ly + 2, mid, ly + 8, a8 | (p.buff > 1.4e6f ? 0xE53935 : 0x1E88E5));
            for (float m : new float[]{-1e6f, 1e6f}) { int tx = mid + (int) (gw / 2f * m / scale); Gui.drawRect(tx, ly, tx + 1, ly + 10, a8 | 0xFFB300); }
            Gui.drawRect(mid, ly, mid + 1, ly + 10, a8 | 0xFFFFFF);
            f.drawString("B", 0, ly + 1, 0x64B5F6 | a8);
            f.drawString("D", gx + gw + 3, ly + 1, 0x81C784 | a8);
            ly += 13;
        }
        if (!p.fault.isEmpty()) f.drawString(f.trimStringToWidth(p.fault, tw), 0, ly, 0xFF5252 | a8);
        GlStateManager.popMatrix();
    }

    /** big flashing warnings across the middle of the screen */
    private static void banners(Minecraft mc, FontRenderer f, ScaledResolution sr, PacketCab p, int a8) {
        String msg = null;
        int col = 0xFFD600;
        int a = p.aids;
        if ((a & com.dogpound.railmap.auto.DriverAids.SPAD) != 0) { msg = "SPAD - SIGNAL PASSED AT DANGER - stop, then press " + ackKey(); col = 0xFF1744; }
        else if ((a & com.dogpound.railmap.auto.DriverAids.TRIPPED) != 0) { msg = "TRAIN STOP TRIPPED - EMERGENCY BRAKE - stop, then press " + ackKey() + " to reset the trip valve"; col = 0xFF1744; }
        else if ((a & com.dogpound.railmap.sim.CabData.PARTED) != 0) { msg = "TRAIN PARTED - EMERGENCY BRAKE"; col = 0xFF1744; }
        else if ((a & com.dogpound.railmap.auto.DriverAids.VIG_PENALTY) != 0) { msg = "VIGILANCE PENALTY BRAKE - stop, then press " + ackKey(); col = 0xFF5252; }
        else if ((a & com.dogpound.railmap.auto.DriverAids.CAB_PENALTY) != 0) { msg = "CAB SIGNAL BRAKE DEMAND - stop, then press " + ackKey(); col = 0xFF5252; }
        else if ((a & com.dogpound.railmap.auto.DriverAids.AWS_PENALTY) != 0) { msg = "AWS BRAKE DEMAND - stop, then press " + ackKey(); col = 0xFF5252; }
        else if ((a & com.dogpound.railmap.auto.DriverAids.AWS_HORN) != 0) { msg = "AWS WARNING - press " + ackKey(); col = 0xFFD600; }
        else if ((a & com.dogpound.railmap.auto.DriverAids.CAB_SIG) != 0) { msg = "CAB SIGNAL - press " + ackKey(); col = 0xFFD600; }
        else if ((a & com.dogpound.railmap.auto.DriverAids.VIG_ALERT) != 0) { msg = "VIGILANCE - press " + ackKey(); col = 0xFFD600; }
        if (msg == null) return;
        if ((mc.world.getTotalWorldTime() / 5) % 2 == 1) return;
        int x = sr.getScaledWidth() / 2, y = sr.getScaledHeight() / 3;
        int tw = f.getStringWidth(msg);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(1.5f, 1.5f, 1);
        Gui.drawRect(-tw / 2 - 4, -3, tw / 2 + 4, 11, a8 | 0x101010);
        f.drawString(msg, -tw / 2, 0, col | a8);
        GlStateManager.popMatrix();
    }
}
