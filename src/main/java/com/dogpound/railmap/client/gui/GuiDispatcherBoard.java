package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.TileRailDisplay;
import com.dogpound.railmap.client.ClientTrains;
import com.dogpound.railmap.client.HandheldMap;
import com.dogpound.railmap.client.render.MapRenderer;
import com.dogpound.railmap.graph.LogEntry;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.RailNode;
import com.dogpound.railmap.graph.StopNode;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.network.PacketAction;
import com.dogpound.railmap.network.PacketRescan;
import com.dogpound.railmap.server.TrainControl;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.graph.SignalNode;
import com.dogpound.railmap.network.PacketRailOps;
import com.dogpound.railmap.network.PacketRailState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The interactive map: pan/zoom, hover readouts, and the dispatcher's controls —
 * click a switch to throw it, click a stop or a piece of track to name a station, click a
 * train to open its card (and follow it), and a timetable panel of arrivals/departures.
 * <p>
 * Backed by a {@link TileRailDisplay} (board or panel wall) or, with {@code tile == null},
 * by the handheld map's last snapshot. Trains come from {@link ClientTrains} either way.
 */
public class GuiDispatcherBoard extends GuiScreen {
    private static final int MARGIN = 8, TOP = 22, BOTTOM = 34, PANEL_W = 150;
    private static final int BTN_RESCAN = 0, BTN_CENTER = 1, BTN_CLOSE = 2, BTN_TIMETABLE = 3, BTN_FOLLOW = 4,
            BTN_NAME_OK = 5, BTN_NAME_REMOVE = 6, BTN_NAME_CANCEL = 7, BTN_AUGMENT = 8,
            BTN_AUTO = 9, BTN_LINES = 10, BTN_ROUTE = 11,
            BTN_MODE_LINE = 30, BTN_MODE_SHUTTLE = 31, BTN_MODE_ONCALL = 32, BTN_LINE_CYCLE = 33,
            BTN_SPEED_DN = 34, BTN_SPEED_UP = 35, BTN_DWELL_DN = 36, BTN_DWELL_UP = 37,
            BTN_START = 38, BTN_OFF = 39, BTN_SEND = 40, BTN_BACK = 41,
            BTN_LINE_NEW = 50, BTN_LINE_UNDO = 51, BTN_LINE_SAVE = 52, BTN_LINE_DELETE = 53, BTN_LINE_CANCEL = 54;
    /** Existing lines in the Lines panel start here. */
    private static final int BTN_LINE_PICK = 60;
    /** Train controls start here; the offset from this id is the {@link TrainControl.Cmd} ordinal. */
    private static final int BTN_TRAIN = 20;

    private final TileRailDisplay tile;
    private final BlockPos origin;
    private final MapRenderer map = new MapRenderer();
    private boolean fitted;
    private boolean dragging, moved;
    private int downX, downY, lastMouseX, lastMouseY;
    private String hoverText = "";
    private MapRenderer.Pick lastPick = new MapRenderer.Pick();

    // Side panel state
    private enum Panel { NONE, TIMETABLE, TRAIN, NAME, AUTO, LINES }
    private Panel panel = Panel.NONE;
    private int selectedTrain = -1;
    private boolean follow;
    private BlockPos nameTarget;
    private String nameOld = "";
    private GuiTextField nameField;
    /** True when the thing being named is an IR loader/unloader we can also switch on. */
    private boolean augmentTarget;
    private int watchTicks;

    // Driverless panel
    private RailwayData.Mode autoMode = RailwayData.Mode.LINE;
    private int autoLine;
    private int autoKmh = 60, autoDwell = 20;
    private boolean sendPick;
    // CTC route mode: click the entrance signal, then the exit signal
    private boolean routeMode;
    private BlockPos routeStart;
    // Line editor
    private boolean editingLine;
    private GuiTextField lineField;
    private String lineName = "";
    private final List<Long> lineStations = new ArrayList<>();

    // viewport rectangle (screen px)
    private int vx, vy, vw, vh;

    public GuiDispatcherBoard(TileRailDisplay tile) {
        this.tile = tile;
        this.origin = tile != null ? tile.origin() : mc.player != null ? mc.player.getPosition() : BlockPos.ORIGIN;
        map.origin = origin;
        map.camX = origin.getX() + 0.5;
        map.camZ = origin.getZ() + 0.5;
    }

    public boolean isHandheld() {
        return tile == null;
    }

    private RailNetwork net() {
        return tile != null ? tile.getNetwork() : HandheldMap.network();
    }

    private BlockPos boardPos() {
        return tile != null ? tile.getPos() : null;
    }

    /** Handheld: a fresh snapshot arrived. */
    public void networkChanged() {
        if (!fitted) fitToNetwork();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        layout();
        if (!fitted) fitToNetwork();
    }

    private void layout() {
        int pw = panel == Panel.NONE ? 0 : PANEL_W + MARGIN;
        vx = MARGIN;
        vy = TOP;
        vw = width - 2 * MARGIN - pw;
        vh = height - TOP - BOTTOM;
        map.w = vw;
        map.h = vh;
        buttonList.clear();
        int by = height - BOTTOM + 8;
        buttonList.add(new GuiButton(BTN_CLOSE, width - MARGIN - 50, by, 50, 20, "Close"));
        buttonList.add(new GuiButton(BTN_RESCAN, width - MARGIN - 106, by, 52, 20, "Rescan"));
        buttonList.add(new GuiButton(BTN_CENTER, width - MARGIN - 160, by, 50, 20, "Center"));
        buttonList.add(new GuiButton(BTN_TIMETABLE, width - MARGIN - 232, by, 68, 20,
                panel == Panel.TIMETABLE ? "Map only" : "Timetable"));
        buttonList.add(new GuiButton(BTN_LINES, width - MARGIN - 290, by, 54, 20, "Lines"));
        buttonList.add(new GuiButton(BTN_ROUTE, width - MARGIN - 364, by, 70, 20, routeMode ? "Route: ON" : "Set route"));
        int px = width - MARGIN - PANEL_W;
        if (panel == Panel.TRAIN) {
            // The driving desk: two columns of short buttons under the readout.
            int by2 = vy + vh - 118;
            addCmd(px + 4, by2, 70, "Throttle +", TrainControl.Cmd.THROTTLE_UP);
            addCmd(px + 78, by2, 68, "Thr -", TrainControl.Cmd.THROTTLE_DOWN);
            addCmd(px + 4, by2 + 22, 70, "Brake +", TrainControl.Cmd.BRAKE_UP);
            addCmd(px + 78, by2 + 22, 68, "Brk -", TrainControl.Cmd.BRAKE_DOWN);
            addCmd(px + 4, by2 + 44, 45, "Fwd", TrainControl.Cmd.REVERSER_FORWARD);
            addCmd(px + 52, by2 + 44, 42, "N", TrainControl.Cmd.REVERSER_NEUTRAL);
            addCmd(px + 97, by2 + 44, 49, "Rev", TrainControl.Cmd.REVERSER_REVERSE);
            addCmd(px + 4, by2 + 66, 70, "Horn", TrainControl.Cmd.HORN);
            addCmd(px + 78, by2 + 66, 68, "Bell", TrainControl.Cmd.BELL);
            addCmd(px + 4, by2 + 88, PANEL_W - 8, "EMERGENCY STOP", TrainControl.Cmd.EMERGENCY_STOP);
            buttonList.add(new GuiButton(BTN_FOLLOW, px + 4, vy + vh - 24, PANEL_W - 8, 20,
                    follow ? "Following (stop)" : "Follow on map"));
            buttonList.add(new GuiButton(BTN_AUTO, px + 4, by2 - 24, PANEL_W - 8, 20,
                    ClientRailway.train(selectedTrain) != null ? "Driverless: ON" : "Make driverless..."));
        }
        if (panel == Panel.AUTO) layoutAuto(px);
        if (panel == Panel.LINES) layoutLines(px);
        else lineField = null;
        if (panel == Panel.NAME) {
            nameField = new GuiTextField(100, fontRenderer, px + 6, vy + 40, PANEL_W - 12, 16);
            nameField.setMaxStringLength(32);
            nameField.setText(nameOld);
            nameField.setFocused(true);
            buttonList.add(new GuiButton(BTN_NAME_OK, px + 4, vy + 64, PANEL_W - 8, 20, "Save"));
            buttonList.add(new GuiButton(BTN_NAME_REMOVE, px + 4, vy + 88, PANEL_W - 8, 20, "Remove station"));
            buttonList.add(new GuiButton(BTN_NAME_CANCEL, px + 4, vy + 112, PANEL_W - 8, 20, "Cancel"));
            if (augmentTarget) {
                buttonList.add(new GuiButton(BTN_AUGMENT, px + 4, vy + 136, PANEL_W - 8, 20, "Load / unload now"));
            }
        } else {
            nameField = null;
        }
    }

    private void layoutAuto(int px) {
        int w3 = (PANEL_W - 16) / 3;
        int y = vy + 62;
        buttonList.add(new GuiButton(BTN_MODE_LINE, px + 4, y, w3, 20, mark(autoMode == RailwayData.Mode.LINE, "Line")));
        buttonList.add(new GuiButton(BTN_MODE_SHUTTLE, px + 8 + w3, y, w3, 20, mark(autoMode == RailwayData.Mode.SHUTTLE, "Shuttle")));
        buttonList.add(new GuiButton(BTN_MODE_ONCALL, px + 12 + 2 * w3, y, w3, 20, mark(autoMode == RailwayData.Mode.ON_CALL, "On call")));
        y += 22;
        List<PacketRailState.LineInfo> lines = ClientRailway.get().lines;
        String lineLabel = autoMode == RailwayData.Mode.ON_CALL ? "Home: nearest station"
                : lines.isEmpty() ? "No lines yet (use Lines)" : "Line: " + lines.get(Math.floorMod(autoLine, lines.size())).name + " >";
        GuiButton lb = new GuiButton(BTN_LINE_CYCLE, px + 4, y, PANEL_W - 8, 20, fontRenderer.trimStringToWidth(lineLabel, PANEL_W - 16));
        lb.enabled = autoMode != RailwayData.Mode.ON_CALL && !lines.isEmpty();
        buttonList.add(lb);
        y += 24;
        buttonList.add(new GuiButton(BTN_SPEED_DN, px + 4, y, 24, 20, "-"));
        buttonList.add(new GuiButton(BTN_SPEED_UP, px + PANEL_W - 28, y, 24, 20, "+"));
        y += 22;
        buttonList.add(new GuiButton(BTN_DWELL_DN, px + 4, y, 24, 20, "-"));
        buttonList.add(new GuiButton(BTN_DWELL_UP, px + PANEL_W - 28, y, 24, 20, "+"));
        y += 24;
        buttonList.add(new GuiButton(BTN_START, px + 4, y, PANEL_W - 8, 20, "Start driverless"));
        y += 22;
        buttonList.add(new GuiButton(BTN_SEND, px + 4, y, PANEL_W - 8, 20, sendPick ? "Click a station on the map" : "Send to a station..."));
        y += 22;
        GuiButton off = new GuiButton(BTN_OFF, px + 4, y, PANEL_W - 8, 20, "Driverless off");
        off.enabled = ClientRailway.train(selectedTrain) != null;
        buttonList.add(off);
        buttonList.add(new GuiButton(BTN_BACK, px + 4, vy + vh - 24, PANEL_W - 8, 20, "Back to train"));
    }

    private void layoutLines(int px) {
        if (!editingLine) {
            lineField = null;
            List<PacketRailState.LineInfo> lines = ClientRailway.get().lines;
            int y = vy + 24;
            for (int i = 0; i < lines.size() && y + 22 < vy + vh - 50; i++, y += 22) {
                buttonList.add(new GuiButton(BTN_LINE_PICK + i, px + 4, y, PANEL_W - 8, 20,
                        fontRenderer.trimStringToWidth(lines.get(i).name + " (" + lines.get(i).stations.size() + ")", PANEL_W - 16)));
            }
            buttonList.add(new GuiButton(BTN_LINE_NEW, px + 4, vy + vh - 48, PANEL_W - 8, 20, "New line"));
            buttonList.add(new GuiButton(BTN_LINE_CANCEL, px + 4, vy + vh - 24, PANEL_W - 8, 20, "Close"));
            return;
        }
        lineField = new GuiTextField(101, fontRenderer, px + 6, vy + 36, PANEL_W - 12, 16);
        lineField.setMaxStringLength(32);
        lineField.setText(lineName);
        lineField.setFocused(lineName.isEmpty());
        int half = (PANEL_W - 12) / 2;
        buttonList.add(new GuiButton(BTN_LINE_UNDO, px + 4, vy + vh - 72, half, 20, "Undo stop"));
        buttonList.add(new GuiButton(BTN_LINE_DELETE, px + 8 + half, vy + vh - 72, half, 20, "Delete"));
        buttonList.add(new GuiButton(BTN_LINE_SAVE, px + 4, vy + vh - 48, PANEL_W - 8, 20, "Save line"));
        buttonList.add(new GuiButton(BTN_LINE_CANCEL, px + 4, vy + vh - 24, PANEL_W - 8, 20, "Cancel"));
    }

    private static String mark(boolean on, String label) {
        return on ? "> " + label : label;
    }

    private void addCmd(int x, int y, int w, String label, TrainControl.Cmd cmd) {
        buttonList.add(new GuiButton(BTN_TRAIN + cmd.ordinal(), x, y, w, 20, label));
    }

    private void setPanel(Panel p) {
        panel = p;
        layout();
    }

    /** Zoom/pan so the whole network fills the viewport; falls back to the origin if empty. */
    private void fitToNetwork() {
        RailNetwork net = net();
        map.fit(net, origin, 4);
        fitted = !net.isEmpty();
    }

    @Override
    public void updateScreen() {
        if (nameField != null) nameField.updateCursorCounter();
        if (lineField != null) lineField.updateCursorCounter();
        if (++watchTicks % 40 == 0) RailMap.NETWORK.sendToServer(PacketAction.watch());
        if (follow && selectedTrain >= 0) {
            TrainNode t = ClientTrains.byId(selectedTrain);
            if (t != null) {
                map.camX = t.x;
                map.camZ = t.z;
            }
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        switch (b.id) {
            case BTN_RESCAN -> RailMap.NETWORK.sendToServer(new PacketRescan(boardPos()));
            case BTN_CENTER -> { follow = false; fitted = false; fitToNetwork(); }
            case BTN_CLOSE -> mc.displayGuiScreen(null);
            case BTN_TIMETABLE -> setPanel(panel == Panel.TIMETABLE ? Panel.NONE : Panel.TIMETABLE);
            case BTN_FOLLOW -> { follow = !follow; layout(); }
            case BTN_NAME_OK -> { if (nameField != null) sendName(nameField.getText()); }
            case BTN_NAME_REMOVE -> sendName("");
            case BTN_NAME_CANCEL -> setPanel(Panel.NONE);
            case BTN_AUTO -> openAuto();
            case BTN_LINES -> { editingLine = false; setPanel(panel == Panel.LINES ? Panel.NONE : Panel.LINES); }
            case BTN_ROUTE -> { routeMode = !routeMode; routeStart = null; sendPick = false; layout(); }
            case BTN_MODE_LINE -> { autoMode = RailwayData.Mode.LINE; layout(); }
            case BTN_MODE_SHUTTLE -> { autoMode = RailwayData.Mode.SHUTTLE; layout(); }
            case BTN_MODE_ONCALL -> { autoMode = RailwayData.Mode.ON_CALL; layout(); }
            case BTN_LINE_CYCLE -> { autoLine++; layout(); }
            case BTN_SPEED_DN -> autoKmh = Math.max(10, autoKmh - 10);
            case BTN_SPEED_UP -> autoKmh = Math.min(200, autoKmh + 10);
            case BTN_DWELL_DN -> autoDwell = Math.max(5, autoDwell - 5);
            case BTN_DWELL_UP -> autoDwell = Math.min(300, autoDwell + 5);
            case BTN_START -> {
                List<PacketRailState.LineInfo> lines = ClientRailway.get().lines;
                String line = lines.isEmpty() || autoMode == RailwayData.Mode.ON_CALL ? ""
                        : lines.get(Math.floorMod(autoLine, lines.size())).name;
                RailMap.NETWORK.sendToServer(PacketRailOps.autopilot(boardPos(), selectedTrain, autoMode, line, 0, autoDwell, autoKmh));
                setPanel(Panel.TRAIN);
            }
            case BTN_OFF -> { RailMap.NETWORK.sendToServer(PacketRailOps.autopilotOff(boardPos(), selectedTrain)); setPanel(Panel.TRAIN); }
            case BTN_SEND -> { sendPick = !sendPick; routeMode = false; layout(); }
            case BTN_BACK -> { sendPick = false; setPanel(Panel.TRAIN); }
            case BTN_LINE_NEW -> { editingLine = true; lineName = ""; lineStations.clear(); layout(); }
            case BTN_LINE_UNDO -> { if (!lineStations.isEmpty()) lineStations.remove(lineStations.size() - 1); }
            case BTN_LINE_SAVE -> {
                if (lineField != null) lineName = lineField.getText().trim();
                RailMap.NETWORK.sendToServer(PacketRailOps.saveLine(boardPos(), lineName, lineStations));
                editingLine = false;
                layout();
            }
            case BTN_LINE_DELETE -> {
                if (!lineName.isEmpty()) RailMap.NETWORK.sendToServer(PacketRailOps.deleteLine(boardPos(), lineName));
                editingLine = false;
                layout();
            }
            case BTN_LINE_CANCEL -> { if (editingLine) { editingLine = false; layout(); } else setPanel(Panel.NONE); }
            case BTN_AUGMENT -> {
                if (nameTarget != null) RailMap.NETWORK.sendToServer(PacketAction.augment(boardPos(), nameTarget));
            }
            default -> {
                List<PacketRailState.LineInfo> lines = ClientRailway.get().lines;
                if (b.id >= BTN_LINE_PICK && b.id < BTN_LINE_PICK + lines.size()) {
                    PacketRailState.LineInfo li = lines.get(b.id - BTN_LINE_PICK);
                    editingLine = true;
                    lineName = li.name;
                    lineStations.clear();
                    lineStations.addAll(li.stations);
                    layout();
                    return;
                }
                if (b.id >= BTN_TRAIN && b.id < BTN_TRAIN + TrainControl.Cmd.values().length && selectedTrain >= 0) {
                    RailMap.NETWORK.sendToServer(PacketAction.train(boardPos(), selectedTrain,
                            TrainControl.Cmd.values()[b.id - BTN_TRAIN]));
                }
            }
        }
    }

    private void sendName(String name) {
        if (nameTarget != null) {
            RailMap.NETWORK.sendToServer(new PacketAction(PacketAction.SET_STATION, boardPos(), nameTarget, name));
        }
        setPanel(Panel.NONE);
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (lineField != null && lineField.isFocused()) {
            if (key == Keyboard.KEY_ESCAPE) { editingLine = false; layout(); return; }
            lineField.textboxKeyTyped(c, key);
            lineName = lineField.getText();
            return;
        }
        if (nameField != null && nameField.isFocused()) {
            if (key == Keyboard.KEY_RETURN) { sendName(nameField.getText()); return; }
            if (key == Keyboard.KEY_ESCAPE) { setPanel(Panel.NONE); return; }
            nameField.textboxKeyTyped(c, key);
            return;
        }
        if (key == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(c, key);
    }

    // ---- input -------------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (nameField != null) nameField.mouseClicked(mx, my, button);
        if (lineField != null) lineField.mouseClicked(mx, my, button);
        if (button == 0 && inViewport(mx, my)) {
            dragging = true;
            moved = false;
            downX = lastMouseX = mx;
            downY = lastMouseY = my;
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long held) {
        if (dragging) {
            if (Math.abs(mx - downX) + Math.abs(my - downY) > 3) moved = true;
            if (moved) {
                follow = false;
                map.camX -= (mx - lastMouseX) / map.zoom;
                map.camZ -= (my - lastMouseY) / map.zoom;
            }
            lastMouseX = mx;
            lastMouseY = my;
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        super.mouseReleased(mx, my, state);
        if (dragging && !moved && state == 0) click();
        dragging = false;
    }

    /** A click (not a drag) on whatever was under the cursor at the last frame. */
    private void click() {
        MapRenderer.Pick p = lastPick;
        if (routeMode) {
            if (p.signal == null || !"railmap".equals(p.signal.source)) return;
            if (routeStart == null) {
                for (PacketRailState.RouteInfo r : ClientRailway.get().routes) {
                    if (r.start.equals(p.signal.pos)) {
                        RailMap.NETWORK.sendToServer(PacketRailOps.cancelRoute(boardPos(), r.start));
                        return;
                    }
                }
                routeStart = p.signal.pos;
            } else {
                RailMap.NETWORK.sendToServer(PacketRailOps.route(boardPos(), routeStart, p.signal.pos));
                routeStart = null;
            }
            return;
        }
        if (sendPick) {
            if (p.stop != null && p.stop.named && selectedTrain >= 0) {
                RailMap.NETWORK.sendToServer(PacketRailOps.send(boardPos(), selectedTrain, p.stop.pos.toLong()));
                sendPick = false;
                setPanel(Panel.TRAIN);
            }
            return;
        }
        if (panel == Panel.LINES && editingLine) {
            if (p.stop != null && p.stop.named) {
                long k = p.stop.pos.toLong();
                if (lineStations.isEmpty() || lineStations.get(lineStations.size() - 1) != k) lineStations.add(k);
            }
            return;
        }
        if (p.train != null) {
            selectedTrain = p.train.id;
            setPanel(Panel.TRAIN);
        } else if (p.stop != null) {
            nameTarget = p.stop.pos;
            nameOld = p.stop.named ? p.stop.name : "";
            augmentTarget = p.stop.kind != StopNode.Kind.STATION;
            setPanel(Panel.NAME);
        } else if (p.node != null && p.node.kind == RailNode.Kind.SWITCH) {
            RailMap.NETWORK.sendToServer(new PacketAction(PacketAction.THROW_SWITCH, boardPos(), p.node.pos, ""));
        } else if (p.node != null) {
            nameTarget = p.node.pos;
            nameOld = "";
            augmentTarget = false;
            setPanel(Panel.NAME);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (!inViewport(mx, my)) return;
        // Zoom about the cursor so the block under it stays put.
        int lx = mx - vx, ly = my - vy;
        double wx = map.toWorldX(lx), wz = map.toWorldZ(ly);
        map.zoom = MapRenderer.clampZoom(map.zoom * (wheel > 0 ? 1.25 : 0.8));
        if (!follow) {
            map.camX = wx - (lx - map.cx()) / map.zoom;
            map.camZ = wz - (ly - map.cy()) / map.zoom;
        }
    }

    private boolean inViewport(int mx, int my) {
        return mx >= vx && mx < vx + vw && my >= vy && my < vy + vh;
    }

    // ---- drawing -----------------------------------------------------------------------

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        drawDefaultBackground();
        RailNetwork net = net();
        if (!fitted && !net.isEmpty()) fitToNetwork();
        List<TrainNode> trains = ClientTrains.get();

        drawRect(vx - 1, vy - 1, vx + vw + 1, vy + vh + 1, 0xFF3A3A3A);

        scissorOn();
        GlStateManager.pushMatrix();
        GlStateManager.translate(vx, vy, 0);
        boolean inView = inViewport(mx, my) && panel != Panel.NAME;
        lastPick = map.draw(net, trains, inView ? mx - vx : -1, inView ? my - vy : -1);
        drawOverlays(net);
        map.drawChrome();
        GlStateManager.popMatrix();
        scissorOff();
        hoverText = lastPick.text;

        drawHeader(net, trains);
        switch (panel) {
            case TIMETABLE -> drawTimetable(net);
            case TRAIN -> drawTrainCard(net);
            case NAME -> drawNamePanel();
            case AUTO -> drawAutoPanel();
            case LINES -> drawLinesPanel(net);
            default -> { }
        }
        String hint = routeMode
                ? (routeStart == null ? "ROUTE: click the signal the train starts at (click a lit route's signal to cancel it)"
                                      : "ROUTE: now click the signal it should run to")
                : sendPick ? "SEND: click a named station on the map"
                : panel == Panel.LINES && editingLine ? "LINE: click named stations in the order the train visits them"
                : hoverText.isEmpty()
                ? "drag to pan, scroll to zoom, click: switch = throw, track/stop = name station, train = card"
                : hoverText;
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(hint, width - 2 * MARGIN), MARGIN, height - BOTTOM + 14, 0xFFDDDDDD);
        super.drawScreen(mx, my, partialTicks);
        if (nameField != null) nameField.drawTextBox();
        if (lineField != null) lineField.drawTextBox();
    }

    // ---- driverless, lines, routes -------------------------------------------------------

    private void openAuto() {
        PacketRailState.TrainInfo t = ClientRailway.train(selectedTrain);
        if (t != null) {
            autoMode = RailwayData.Mode.byOrdinal(t.mode);
            if (autoMode == RailwayData.Mode.SEND) autoMode = RailwayData.Mode.LINE;
            autoKmh = t.maxKmh;
            autoDwell = t.dwell;
            List<PacketRailState.LineInfo> lines = ClientRailway.get().lines;
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).name.equals(t.line)) autoLine = i;
        }
        setPanel(Panel.AUTO);
    }

    private void drawAutoPanel() {
        panelFrame("Driverless");
        int px = panelX();
        PacketRailState.TrainInfo t = ClientRailway.train(selectedTrain);
        String status = t == null ? "A person is driving this train. Pick how it should run, then Start."
                : RailwayData.Mode.byOrdinal(t.mode).label + " — " + t.status;
        fontRenderer.drawSplitString(status, px + 6, vy + 24, PANEL_W - 12, t == null ? 0xFF8A94A0 : 0xFF7CF29A);
        int y = vy + 62 + 22 + 24;
        centre(px, y + 6, "Top speed " + autoKmh + " km/h");
        centre(px, y + 28, "Wait " + autoDwell + " s at stops");
    }

    private void centre(int px, int y, String s) {
        fontRenderer.drawString(s, px + (PANEL_W - fontRenderer.getStringWidth(s)) / 2, y, 0xFFE8ECF0);
    }

    private void drawLinesPanel(RailNetwork net) {
        panelFrame(editingLine ? "Edit line" : "Lines");
        int px = panelX();
        if (!editingLine) {
            if (ClientRailway.get().lines.isEmpty()) {
                fontRenderer.drawSplitString("A line is a list of stations a driverless train runs. Name your stations first, then press New line.",
                        px + 6, vy + 24, PANEL_W - 12, 0xFF8A94A0);
            }
            return;
        }
        fontRenderer.drawString("Name", px + 6, vy + 26, 0xFF8A94A0);
        int y = vy + 58;
        if (lineStations.isEmpty()) {
            fontRenderer.drawSplitString("Click named stations on the map, in order.", px + 6, y, PANEL_W - 12, 0xFF8A94A0);
        }
        for (int i = 0; i < lineStations.size() && y + 10 < vy + vh - 76; i++, y += 11) {
            String n = stationName(net, lineStations.get(i));
            fontRenderer.drawString(fontRenderer.trimStringToWidth((i + 1) + ". " + n, PANEL_W - 12), px + 6, y, 0xFFE8ECF0);
        }
    }

    private static String stationName(RailNetwork net, long key) {
        for (StopNode s : net.stops) if (s.named && s.pos.toLong() == key) return s.name;
        BlockPos p = BlockPos.fromLong(key);
        return p.getX() + "," + p.getZ();
    }

    /** Routes as bright lines, line stations as coloured rings, the route's first click as a halo. */
    private void drawOverlays(RailNetwork net) {
        PacketRailState st = ClientRailway.get();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GL11.glLineWidth(4f);
        for (PacketRailState.RouteInfo r : st.routes) {
            int c = r.entered ? 0xAA3A7A4A : 0xFF3BFF6E;
            for (double[] pts : r.points) {
                buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
                for (int i = 0; i + 2 < pts.length; i += 3) {
                    buf.pos(map.toScreenX(pts[i]), map.toScreenY(pts[i + 2]), 0)
                            .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
                }
                tess.draw();
            }
        }
        GL11.glLineWidth(1f);
        for (PacketRailState.LineInfo l : st.lines) {
            for (long k : l.stations) ring(k, 7, l.color | 0xFF000000);
        }
        if (panel == Panel.LINES && editingLine) {
            for (long k : lineStations) ring(k, 9, 0xFFFFFFFF);
        }
        if (routeStart != null) ring(routeStart.toLong(), 8, 0xFF3BFF6E);
        GlStateManager.enableTexture2D();
    }

    private void ring(long key, int r, int col) {
        BlockPos p = BlockPos.fromLong(key);
        double sx = map.toScreenX(p.getX() + 0.5), sy = map.toScreenY(p.getZ() + 0.5);
        map.rect(sx - r, sy - r, sx + r, sy - r + 2, col);
        map.rect(sx - r, sy + r - 2, sx + r, sy + r, col);
        map.rect(sx - r, sy - r, sx - r + 2, sy + r, col);
        map.rect(sx + r - 2, sy - r, sx + r, sy + r, col);
    }

    private void drawHeader(RailNetwork net, List<TrainNode> trains) {
        fontRenderer.drawStringWithShadow(tile == null ? "Rail Map" : "Dispatcher Board", MARGIN, 7, 0xFFFFFFFF);
        String status;
        if (net.isEmpty()) {
            status = "no data";
        } else {
            long age = Math.max(0, (mc.world.getTotalWorldTime() - net.timestamp) / 20);
            int leads = 0;
            for (TrainNode t : trains) if (t.lead) leads++;
            status = net.nodes.size() + " pieces, " + net.signals.size() + " signals, " + net.stops.size()
                    + " stops, " + leads + " trains" + (ClientTrains.live() ? "" : " (feed idle)")
                    + ", updated " + age + "s ago" + (net.truncated ? " (budget hit: more track exists)" : "");
        }
        fontRenderer.drawStringWithShadow(status, MARGIN + 100, 7, 0xFFA0A0A0);
    }

    private int panelX() {
        return width - MARGIN - PANEL_W;
    }

    private void panelFrame(String title) {
        int px = panelX();
        drawRect(px - 1, vy - 1, px + PANEL_W + 1, vy + vh + 1, 0xFF3A3A3A);
        drawRect(px, vy, px + PANEL_W, vy + vh, 0xFF14181C);
        fontRenderer.drawStringWithShadow(title, px + 6, vy + 6, 0xFFFFFFFF);
        drawRect(px + 4, vy + 17, px + PANEL_W - 4, vy + 18, 0xFF3A3A3A);
    }

    private void drawTimetable(RailNetwork net) {
        panelFrame("Timetable");
        int px = panelX();
        int y = vy + 24;
        if (net.log.isEmpty()) {
            fontRenderer.drawSplitString("No arrivals yet. Name a station (click a stop or a piece of track) and trains stopping there get logged.",
                    px + 6, y, PANEL_W - 12, 0xFF8A94A0);
            return;
        }
        for (int i = net.log.size() - 1; i >= 0 && y + 20 < vy + vh; i--) {
            LogEntry e = net.log.get(i);
            fontRenderer.drawString(LogEntry.clock(e.time), px + 6, y, 0xFF8A94A0);
            String line = fontRenderer.trimStringToWidth(e.train + (e.arrive ? " arr " : " dep ") + e.station, PANEL_W - 12);
            fontRenderer.drawString(line, px + 6, y + 10, e.arrive ? MapRenderer.COL_STATION : 0xFFFFC44D);
            y += 22;
        }
    }

    private void drawTrainCard(RailNetwork net) {
        TrainNode t = ClientTrains.byId(selectedTrain);
        panelFrame(t == null ? "Train" : fontRenderer.trimStringToWidth(t.displayName(), PANEL_W - 12));
        int px = panelX();
        int y = vy + 24;
        if (t == null) {
            fontRenderer.drawSplitString(ClientTrains.live() ? "Train left the loaded area." : "Waiting for train feed...", px + 6, y, PANEL_W - 12, 0xFF8A94A0);
            return;
        }
        y = card(px, y, "Type", MapRenderer.pretty(t.kind.name()));
        if (!t.tag.isEmpty()) y = card(px, y, "Model", t.name);
        y = card(px, y, "Speed", Math.round(Math.abs(t.speedKmh)) + " km/h" + (t.moving() ? (t.speedKmh < 0 ? " (reverse)" : "") : " (stopped)"));
        if (t.kind.isLoco()) {
            y = card(px, y, "Throttle", Math.round(t.throttle * 100) + "%");
            y = card(px, y, "Reverser", t.reverser > 0.05 ? "Forward" : t.reverser < -0.05 ? "Reverse" : "Neutral");
            y = card(px, y, "Train brake", Math.round(t.brake * 100) + "%");
        }
        if (t.consist > 1) y = card(px, y, "Consist", t.consist + " units");
        if (t.cargoPct >= 0) y = card(px, y, "Cargo", t.cargoPct + "% full");
        if (t.passengers > 0) y = card(px, y, "Passengers", String.valueOf(t.passengers));
        PacketRailState.TrainInfo auto = ClientRailway.train(t.id);
        if (auto != null) y = card(px, y, "DRIVERLESS", auto.status);
        StopNode at = net.nearestStop(t.x, t.y, t.z, 6, true);
        if (at != null) card(px, y, "At", at.name);
        else if (!t.heading.isEmpty()) card(px, y, "Heading to", t.heading);
    }

    private int card(int px, int y, String k, String v) {
        fontRenderer.drawString(k, px + 6, y, 0xFF8A94A0);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(v, PANEL_W - 12), px + 6, y + 10, 0xFFE8ECF0);
        return y + 22;
    }

    private void drawNamePanel() {
        panelFrame(nameOld.isEmpty() ? "New station" : "Rename station");
        int px = panelX();
        fontRenderer.drawString("Name", px + 6, vy + 28, 0xFF8A94A0);
        if (nameTarget != null) {
            fontRenderer.drawString("@ " + nameTarget.getX() + "," + nameTarget.getY() + "," + nameTarget.getZ(), px + 6, vy + 140, 0xFF8A94A0);
        }
        fontRenderer.drawSplitString("Trains stopping within 6 blocks get logged in the timetable.", px + 6, vy + 154, PANEL_W - 12, 0xFF8A94A0);
    }

    private void scissorOn() {
        ScaledResolution sr = new ScaledResolution(mc);
        int s = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(vx * s, mc.displayHeight - (vy + vh) * s, vw * s, vh * s);
    }

    private void scissorOff() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
