package com.dogpound.railmap.cine.client;

import com.dogpound.railmap.cine.CineMath;
import com.dogpound.railmap.client.gui.PrideButton;
import com.dogpound.railmap.client.gui.PrideFrame;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.Locale;

/**
 * The photo-mode panel (inventory key while in photo mode): every camera setting as a button - views, subject train,
 * zoom, depth of field, letterbox, HUD, the waypoint path (add / undo / clear / length / play / save + load slots), the
 * replay transport (load, seek, play / pause, speed, ghost, view) and the clean screenshot.
 */
public class GuiPhotoMode extends GuiScreen {
    static final int[] VIEWS = {CineMath.FREE, CineMath.CHASE, CineMath.SIDE, CineMath.CAB, CineMath.ORBIT, CineMath.FLYBY, CineMath.AUTO, CineMath.PATH, CineMath.REPLAY};
    static final int[] REPLAY_VIEWS = {CineMath.CHASE, CineMath.SIDE, CineMath.ORBIT, CineMath.FLYBY, CineMath.AUTO};
    static final float[] SPEEDS = {0.5f, 1, 2, 4, 8};
    private PrideFrame f;
    private int slot = 1;
    private String note = "";

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void initGui() {
        f = PrideFrame.sized(width, height, 470, 300);
        buttonList.clear();
        int x = f.cx, y = f.cy + 12, bw = (f.cw - 8 * 3) / 9, bh = 16;
        for (int i = 0; i < VIEWS.length; i++) buttonList.add(new PrideButton(100 + i, x + i * (bw + 3), y, bw, bh, short_(VIEWS[i])));
        y += 32;
        int w3 = 44;
        buttonList.add(new PrideButton(1, x, y, w3, bh, "< TRAIN"));
        buttonList.add(new PrideButton(2, x + w3 + 3, y, w3, bh, "TRAIN >"));
        buttonList.add(new PrideButton(3, x + 2 * (w3 + 3), y, w3, bh, "ZOOM -"));
        buttonList.add(new PrideButton(4, x + 3 * (w3 + 3), y, w3, bh, "ZOOM +"));
        buttonList.add(new PrideButton(5, x + 4 * (w3 + 3), y, 70, bh, "DOF " + CineFx.DOF[CineCam.dof]));
        buttonList.add(new PrideButton(6, x + 4 * (w3 + 3) + 73, y, 74, bh, "BARS " + CineMath.LETTERBOX_NAME[CineCam.letterbox]));
        buttonList.add(new PrideButton(7, x + 4 * (w3 + 3) + 150, y, 60, bh, mc.gameSettings.hideGUI ? "HUD OFF" : "HUD ON"));
        y += 32;
        buttonList.add(new PrideButton(10, x, y, 60, bh, "+ WAYPOINT"));
        buttonList.add(new PrideButton(11, x + 63, y, 40, bh, "UNDO"));
        buttonList.add(new PrideButton(12, x + 106, y, 40, bh, "CLEAR"));
        buttonList.add(new PrideButton(13, x + 149, y, 20, bh, "-"));
        buttonList.add(new PrideButton(14, x + 222, y, 20, bh, "+"));
        buttonList.add(new PrideButton(15, x + 245, y, 50, bh, CineCam.pathPlaying ? "STOP" : "PLAY"));
        buttonList.add(new PrideButton(16, x + 298, y, 30, bh, "S" + slot));
        buttonList.add(new PrideButton(17, x + 331, y, 40, bh, "SAVE"));
        buttonList.add(new PrideButton(18, x + 374, y, 40, bh, "LOAD"));
        y += 32;
        buttonList.add(new PrideButton(20, x, y, 50, bh, "LOAD REC"));
        buttonList.add(new PrideButton(21, x + 53, y, 34, bh, "<< 30"));
        buttonList.add(new PrideButton(22, x + 90, y, 46, bh, CineReplay.paused ? "PLAY" : "PAUSE"));
        buttonList.add(new PrideButton(23, x + 139, y, 34, bh, "30 >>"));
        buttonList.add(new PrideButton(24, x + 176, y, 40, bh, String.format(Locale.ROOT, "x%s", CineReplay.speed < 1 ? "0.5" : String.valueOf((int) CineReplay.speed))));
        buttonList.add(new PrideButton(25, x + 219, y, 40, bh, "< GHOST"));
        buttonList.add(new PrideButton(26, x + 262, y, 40, bh, "GHOST >"));
        buttonList.add(new PrideButton(27, x + 305, y, 80, bh, CineMath.name(CineCam.replayView)));
        y += 40;
        buttonList.add(new PrideButton(30, x, y, 110, 20, "SCREENSHOT"));
        buttonList.add(new PrideButton(31, x + 116, y, 110, 20, "LEAVE PHOTO MODE"));
        buttonList.add(new PrideButton(32, x + 232, y, 80, 20, "BACK"));
    }

    static String short_(int v) {
        return switch (v) {
            case CineMath.FREE -> "FREE"; case CineMath.CHASE -> "CHASE"; case CineMath.SIDE -> "SIDE"; case CineMath.CAB -> "CAB"; case CineMath.ORBIT -> "HELI";
            case CineMath.FLYBY -> "FLYBY"; case CineMath.AUTO -> "TRACK"; case CineMath.PATH -> "PATH"; default -> "REPLAY";
        };
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        int id = b.id;
        if (id >= 100) { CineCam.setMode(VIEWS[id - 100]); initGui(); return; }
        switch (id) {
            case 1 -> CineCam.nearestTrain(-1);
            case 2 -> CineCam.nearestTrain(1);
            case 3 -> CineCam.zoom = Math.min(110, CineCam.zoom + 5);
            case 4 -> CineCam.zoom = Math.max(5, CineCam.zoom - 5);
            case 5 -> CineCam.dof = (CineCam.dof + 1) % CineFx.DOF.length;
            case 6 -> CineCam.letterbox = (CineCam.letterbox + 1) % CineMath.LETTERBOX.length;
            case 7 -> mc.gameSettings.hideGUI = !mc.gameSettings.hideGUI;
            case 10 -> CineCam.addWaypoint();
            case 11 -> { if (!CineCam.WAYPOINTS.isEmpty()) CineCam.WAYPOINTS.remove(CineCam.WAYPOINTS.size() - 1); }
            case 12 -> CineCam.WAYPOINTS.clear();
            case 13 -> CineCam.pathSecs = Math.max(2, CineCam.pathSecs - 2);
            case 14 -> CineCam.pathSecs = Math.min(300, CineCam.pathSecs + 2);
            case 15 -> { if (CineCam.pathPlaying) CineCam.pathPlaying = false; else { CineCam.playPath(); mc.displayGuiScreen(null); return; } }
            case 16 -> slot = slot % 9 + 1;
            case 17 -> note = CinePathStore.save(slot);
            case 18 -> note = CinePathStore.load(slot);
            case 20 -> { CineCam.setMode(CineMath.REPLAY); CineReplay.paused = false; }
            case 21 -> CineReplay.seek(-30);
            case 22 -> CineReplay.paused = !CineReplay.paused;
            case 23 -> CineReplay.seek(30);
            case 24 -> { int i = 0; for (int k = 0; k < SPEEDS.length; k++) if (SPEEDS[k] == CineReplay.speed) i = k; CineReplay.speed = SPEEDS[(i + 1) % SPEEDS.length]; }
            case 25 -> CineReplay.cycle(-1);
            case 26 -> CineReplay.cycle(1);
            case 27 -> { int i = 0; for (int k = 0; k < REPLAY_VIEWS.length; k++) if (REPLAY_VIEWS[k] == CineCam.replayView) i = k; CineCam.replayView = REPLAY_VIEWS[(i + 1) % REPLAY_VIEWS.length]; }
            case 30 -> { mc.displayGuiScreen(null); CineFx.screenshot(); return; }
            case 31 -> { mc.displayGuiScreen(null); CineCam.exit(); return; }
            case 32 -> { mc.displayGuiScreen(null); return; }
            default -> { }
        }
        initGui();
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        f.draw(this, "PHOTO MODE", CineCam.desk != null ? "DIRECTOR PROGRAM" : CineMath.name(CineCam.mode));
        int x = f.cx, y = f.cy;
        fontRenderer.drawStringWithShadow("§7VIEW §8(keys 1-9)", x, y + 2, 0xFFFFFF);
        fontRenderer.drawStringWithShadow("§7SUBJECT §f" + (CineCam.trainName.isEmpty() ? "none" : CineCam.trainName) + String.format(Locale.ROOT, "  §7ZOOM §f%.0f°", CineCam.zoom), x, y + 34, 0xFFFFFF);
        fontRenderer.drawStringWithShadow("§7PATH §f" + CineCam.WAYPOINTS.size() + " waypoints §7(R adds one, P plays)" + "  " + note, x, y + 66, 0xFFFFFF);
        fontRenderer.drawString(String.format(Locale.ROOT, "%.0f s", CineCam.pathSecs), x + 177, y + 81, 0xFFFFFF);
        fontRenderer.drawStringWithShadow("§7REPLAY §f" + CineReplay.status() + (CineReplay.follow >= 0 ? " §7· §f" + CineReplay.name(CineReplay.follow) : ""), x, y + 98, 0xFFFFFF);
        fontRenderer.drawStringWithShadow("§8WASD/Space/Shift fly, Ctrl fast, wheel zoom, arrows pick a train (replay: ghost / seek), F1 HUD, F2 screenshot, F6 leave", x, f.y + f.h - 14, 0xFFFFFF);
        super.drawScreen(mx, my, pt);
    }
}
