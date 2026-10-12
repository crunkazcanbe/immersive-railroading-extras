package com.dogpound.railmap.cctv.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.cctv.*;
import com.dogpound.railmap.client.gui.PrideFrame;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.util.Locale;

/**
 * The CCTV configurator screen (IPC tester plugged into a camera): name, aim with the live picture, field of view,
 * privacy masks (drag boxes on the picture), motion zones (click the 8 x 6 grid) + sensitivity, motion on / off, IR
 * mode, focus, iris, and "motion here -> PTZ camera N to preset P". Every change goes to the server at once (it owns the
 * camera's configuration) and the picture follows.
 */
public class GuiCamera extends CctvGui {
    private final BlockPos pos;
    private GuiTextField name, trig;
    private int mode;                 // 0 view, 1 masks, 2 zones
    private int dragX = -1, dragY;
    private int px, py, pw, ph;

    public GuiCamera(BlockPos pos) { this.pos = pos; }

    private TileCamera cam() { return mc.world.getTileEntity(pos) instanceof TileCamera t ? t : null; }

    private void send(String cfg) { RailMap.NETWORK.sendToServer(new PacketCctvOp(PacketCctvOp.CONFIG, 0, pos.toLong(), 0, cfg)); }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        TileCamera c = cam();
        name = new GuiTextField(1, fontRenderer, 0, 0, 150, 14);
        name.setMaxStringLength(24);
        name.setText(c == null ? "" : c.name);
        trig = new GuiTextField(2, fontRenderer, 0, 0, 60, 14);
        trig.setMaxStringLength(7);
        trig.setText(c == null || c.trigCam == 0 ? "" : c.trigCam + "," + c.trigPreset);
    }

    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float pt) {
        mx = mouseX; my = mouseY;
        startHits();
        TileCamera c = cam();
        PrideFrame f = PrideFrame.sized(width, height, 760, 430);
        f.draw(this, "CCTV CAMERA SET-UP", c == null ? "camera gone" : "CAM " + c.no + " - " + c.type().label);
        if (c == null) { super.drawScreen(mouseX, mouseY, pt); return; }
        pw = Math.min(f.cw * 58 / 100, (f.ch - 30) * 4 / 3);
        ph = pw * 3 / 4;
        px = f.cx; py = f.cy + 2;
        CctvClient.Pic p = CctvClient.pic(c.no, 0, true);                                   // the tester plugs straight into the camera
        picture(p, px, py, pw, ph, false);
        int[][] masks = Snap.parseMasks(c.masks);
        for (int[] m : masks) {
            int x0 = px + m[0] * pw / 1000, y0 = py + m[1] * ph / 1000, x1 = px + m[2] * pw / 1000, y1 = py + m[3] * ph / 1000;
            Gui.drawRect(x0, y0, x1, y1, mode == 1 ? 0xA0000000 : 0xFF000000);
            if (mode == 1) { Gui.drawRect(x0, y0, x1, y0 + 1, 0xFFFF4040); Gui.drawRect(x0, y1 - 1, x1, y1, 0xFFFF4040); }
        }
        if (mode == 2) for (int cy = 0; cy < 6; cy++) for (int cx = 0; cx < 8; cx++) {
            int x0 = px + cx * pw / 8, y0 = py + cy * ph / 6, x1 = px + (cx + 1) * pw / 8, y1 = py + (cy + 1) * ph / 6;
            boolean on = (c.zones >> (cy * 8 + cx) & 1) != 0;
            Gui.drawRect(x0, y0, x1, y1, on ? 0x3020FF40 : 0x00000000);
            Gui.drawRect(x0, y0, x1, y0 + 1, 0x60FFFFFF); Gui.drawRect(x0, y0, x0 + 1, y1, 0x60FFFFFF);
            final int bit = cy * 8 + cx;
            area(x0, y0, x1 - x0, y1 - y0, () -> send("zones=" + (c.zones ^ (1L << bit))));
        }
        if (dragX >= 0) Gui.drawRect(Math.min(dragX, mx), Math.min(dragY, my), Math.max(dragX, mx), Math.max(dragY, my), 0x80FF0000);
        String st = CctvData.STATUS[c.status] + (c.what.isEmpty() ? "" : " - " + c.what);
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(st, pw), px, py + ph + 4, c.running() ? 0x7CF29A : 0xFF6060);
        fontRenderer.drawStringWithShadow(p.live ? "LIVE " + CctvConfig.liveRes + "px" : p.tex >= 0 ? "SNAPSHOT (server)" : "", px, py + ph + 15, 0x8A94A0);
        // ---- right column
        int x = px + pw + 10, y = f.cy + 2, w = f.x + f.w - 10 - x, bw = (w - 6) / 3;
        fontRenderer.drawStringWithShadow("NAME", x, y + 3, 0x5BCEFA);
        name.x = x + 36; name.y = y; name.width = w - 36 - 44;
        name.drawTextBox();
        btn(x + w - 40, y, 40, 14, "SET", 0xFF2A6A3A, () -> send("name=" + name.getText()));
        y += 20;
        boolean ptz = c.type() == CamType.PTZ;
        String aim = ptz ? String.format(Locale.ROOT, "PAN %.0f  TILT %.0f  ZOOM %.1fx", c.pan, c.tilt, c.zoom) : String.format(Locale.ROOT, "AIM yaw %+.0f  pitch %+.0f", c.yawOff, c.pitchOff);
        fontRenderer.drawStringWithShadow(aim, x, y, 0xE8ECF0);
        y += 12;
        if (ptz) {
            btn(x, y, bw, 14, "< PAN", 0xFF2A2238, () -> ptzMove(c, -10, 0)); btn(x + bw + 3, y, bw, 14, "TILT UP", 0xFF2A2238, () -> ptzMove(c, 0, -5)); btn(x + 2 * (bw + 3), y, bw, 14, "PAN >", 0xFF2A2238, () -> ptzMove(c, 10, 0));
            y += 17;
            btn(x, y, bw, 14, "ZOOM -", 0xFF2A2238, () -> ptzZoom(c, 0.5f)); btn(x + bw + 3, y, bw, 14, "TILT DN", 0xFF2A2238, () -> ptzMove(c, 0, 5)); btn(x + 2 * (bw + 3), y, bw, 14, "ZOOM +", 0xFF2A2238, () -> ptzZoom(c, 2f));
        } else {
            btn(x, y, bw, 14, "< LEFT", 0xFF2A2238, () -> send("yaw=" + (c.yawOff - 5))); btn(x + bw + 3, y, bw, 14, "UP", 0xFF2A2238, () -> send("pitch=" + (c.pitchOff - 3))); btn(x + 2 * (bw + 3), y, bw, 14, "RIGHT >", 0xFF2A2238, () -> send("yaw=" + (c.yawOff + 5)));
            y += 17;
            btn(x, y, bw, 14, "CENTRE", 0xFF2A2238, () -> send("yaw=0;pitch=0")); btn(x + bw + 3, y, bw, 14, "DOWN", 0xFF2A2238, () -> send("pitch=" + (c.pitchOff + 3)));
        }
        y += 20;
        float fov = c.fovSet > 0 ? c.fovSet : c.type().fov;
        fontRenderer.drawStringWithShadow(String.format(Locale.ROOT, "FIELD OF VIEW %.0f°", fov), x, y + 3, 0xE8ECF0);
        btn(x + w - 92, y, 28, 14, "-", 0xFF2A2238, () -> send("fov=" + Math.max(10, fov - 5)));
        btn(x + w - 62, y, 28, 14, "+", 0xFF2A2238, () -> send("fov=" + Math.min(150, fov + 5)));
        btn(x + w - 32, y, 32, 14, "STD", 0xFF2A2238, () -> send("fov=0"));
        y += 20;
        btn(x, y, bw, 14, mode == 1 ? "§eMASKS" : "MASKS", mode == 1 ? 0xFF6A3FA0 : 0xFF2A2238, () -> mode = mode == 1 ? 0 : 1);
        btn(x + bw + 3, y, bw, 14, "CLEAR", 0xFF5A2A2A, () -> send("masks="));
        btn(x + 2 * (bw + 3), y, bw, 14, mode == 2 ? "§eZONES" : "ZONES", mode == 2 ? 0xFF6A3FA0 : 0xFF2A2238, () -> mode = mode == 2 ? 0 : 2);
        y += 17;
        fontRenderer.drawStringWithShadow(mode == 1 ? "Drag boxes on the picture (max 4)" : mode == 2 ? "Click cells: green = watched for motion" : masks.length + " privacy mask(s)", x, y + 2, 0x8A94A0);
        y += 14;
        fontRenderer.drawStringWithShadow("MOTION " + (c.motionOn ? "ON" : "OFF") + "  SENS " + c.sens, x, y + 3, 0xE8ECF0);
        btn(x + w - 92, y, 28, 14, "-", 0xFF2A2238, () -> send("sens=" + (c.sens - 1)));
        btn(x + w - 62, y, 28, 14, "+", 0xFF2A2238, () -> send("sens=" + (c.sens + 1)));
        btn(x + w - 32, y, 32, 14, c.motionOn ? "OFF" : "ON", 0xFF2A2238, () -> send("motion=" + (c.motionOn ? 0 : 1)));
        y += 18;
        btn(x, y, bw, 14, "IR " + new String[]{"AUTO", "ON", "OFF"}[c.irMode], 0xFF5A2020, () -> send("ir=" + (c.irMode + 1) % 3));
        btn(x + bw + 3, y, bw, 14, "FOCUS " + new String[]{"AUTO", "NEAR", "MID", "FAR"}[c.focus], 0xFF2A2238, () -> send("focus=" + (c.focus + 1) % 4));
        btn(x + 2 * (bw + 3), y, bw, 14, "IRIS " + new String[]{"AUTO", "F1.4", "F2.8", "F5.6", "F11"}[c.iris], 0xFF2A2238, () -> send("iris=" + (c.iris + 1) % 5));
        y += 20;
        fontRenderer.drawStringWithShadow("ON MOTION: PTZ CAM,PRESET", x, y + 3, 0xE8ECF0);
        trig.x = x + w - 60 - 34; trig.y = y; trig.drawTextBox();
        btn(x + w - 30, y, 30, 14, "SET", 0xFF2A6A3A, () -> send("trig=" + (trig.getText().isEmpty() ? "0,0" : trig.getText())));
        y += 22;
        btn(x, y, w, 16, "DONE", 0xFF2A6A3A, () -> mc.displayGuiScreen(null));
        super.drawScreen(mouseX, mouseY, pt);
    }

    private void ptzMove(TileCamera c, float dp, float dt) { send("ptz=" + dp + "," + dt); }
    private void ptzZoom(TileCamera c, float k) { send("zoomto=" + Math.max(1, Math.min(30, c.zoomT * k))); }

    @Override
    protected void mouseClicked(int x, int y, int b) throws java.io.IOException {
        name.mouseClicked(x, y, b);
        trig.mouseClicked(x, y, b);
        if (mode == 1 && x >= px && y >= py && x < px + pw && y < py + ph) { dragX = x; dragY = y; return; }
        super.mouseClicked(x, y, b);
    }

    @Override
    protected void mouseReleased(int x, int y, int state) {
        super.mouseReleased(x, y, state);
        if (dragX < 0) return;
        TileCamera c = cam();
        int x0 = clamp((Math.min(dragX, x) - px) * 1000 / pw), y0 = clamp((Math.min(dragY, y) - py) * 1000 / ph), x1 = clamp((Math.max(dragX, x) - px) * 1000 / pw), y1 = clamp((Math.max(dragY, y) - py) * 1000 / ph);
        dragX = -1;
        if (c == null || x1 - x0 < 10 || y1 - y0 < 10) return;
        String m = c.masks.isEmpty() ? "" : c.masks + ";";
        send("masks=" + m + x0 + "," + y0 + "," + x1 + "," + y1);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(1000, v)); }

    @Override
    protected void keyTyped(char ch, int key) throws java.io.IOException {
        if (name.isFocused()) { if (key == Keyboard.KEY_RETURN) send("name=" + name.getText()); else name.textboxKeyTyped(ch, key); return; }
        if (trig.isFocused()) { trig.textboxKeyTyped(ch, key); return; }
        super.keyTyped(ch, key);
    }
}
