package com.dogpound.railmap.client.gui;

import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.registry.CarFreightDefinition;
import cam72cam.immersiverailroading.registry.CarPassengerDefinition;
import cam72cam.immersiverailroading.registry.CarTankDefinition;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.LocomotiveDefinition;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.vehicle.Formation;
import com.dogpound.railmap.vehicle.PacketFormation;
import com.dogpound.railmap.vehicle.TileFormationDesk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;

import java.util.*;

/**
 * The Formation Editor on the Formation &amp; Livery Desk's monitor (her ChatGPT list item 107). Left: every vehicle
 * Immersive Railroading knows (filter by typing), click one to add it to the end. Middle: the formation, car by car -
 * pick one to move it, turn it round, change its paint variant, make it the lead cab or take it out. Right: the
 * figures and checks (from IR's definitions, against the desk's siding length and ruling grade) and SAVE. Along the
 * bottom the train drawn to scale, side on.
 */
public class GuiFormation extends GuiScreen {
    private final TileFormationDesk desk;
    private Formation f;
    private int slot, selCar = -1, catScroll, carScroll;
    private GuiTextField filter, name;
    private List<EntityRollingStockDefinition> catalogue = new ArrayList<>();
    private PrideFrame fr;
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> acts = new ArrayList<>();
    private static final int ROW = 11;

    public GuiFormation(TileFormationDesk desk) {
        this.desk = desk;
        slot = desk.sel();
        Formation c = desk.current();
        f = c == null ? new Formation() : Formation.read(c.write());
        if (c == null) slot = desk.library.size();
    }

    public static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.world != null && mc.world.getTileEntity(pos) instanceof TileFormationDesk d) mc.displayGuiScreen(new GuiFormation(d));
        });
    }

    @Override
    public void initGui() {
        fr = PrideFrame.fit(width, height);
        int colW = (fr.cw - 16) / 3;
        filter = new GuiTextField(1, fontRenderer, fr.cx + 2, fr.cy + 2, colW - 4, 12);
        filter.setMaxStringLength(40);
        name = new GuiTextField(2, fontRenderer, fr.cx + 2 * colW + 18, fr.cy + 2, colW - 6, 12);
        name.setMaxStringLength(24);
        name.setText(f.name);
        refilter();
    }

    private void refilter() {
        String q = filter == null ? "" : filter.getText().toLowerCase(Locale.ROOT);
        catalogue = new ArrayList<>();
        for (EntityRollingStockDefinition d : DefinitionManager.getDefinitions())
            if (q.isEmpty() || Formation.shortName(d).toLowerCase(Locale.ROOT).contains(q) || d.defID.toLowerCase(Locale.ROOT).contains(q)) catalogue.add(d);
        catalogue.sort(Comparator.comparing((EntityRollingStockDefinition d) -> type(d)).thenComparing(Formation::shortName));
        catScroll = 0;
    }

    static String type(EntityRollingStockDefinition d) {
        if (d instanceof LocomotiveDefinition l) return l.isCabCar() ? "CAB" : "LOCO";
        if (d instanceof CarPassengerDefinition) return "PASS";
        if (d instanceof CarTankDefinition) return "TANK";
        if (d instanceof CarFreightDefinition) return "FRT";
        return "CAR";
    }

    static int colour(EntityRollingStockDefinition d) {
        switch (type(d)) {
            case "LOCO": return 0xFFE0A030;
            case "CAB": return 0xFFD0D040;
            case "PASS": return 0xFF5BCEFA;
            case "TANK": return 0xFF909090;
            default: return 0xFF8A6A50;
        }
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    private boolean btn(int x, int y, int w, int h, String label, int col, int mx, int my, Runnable r) {
        boolean over = PrideFrame.button(x, y, w, h, label, col, mx, my);
        hits.add(new int[]{x, y, w, h});
        acts.add(r);
        return over;
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear();
        acts.clear();
        fr = PrideFrame.fit(width, height);
        f.name = name.getText().isEmpty() ? "FORMATION" : name.getText().toUpperCase(Locale.ROOT);
        fr.draw(this, "Formation Editor", "slot " + (slot + 1) + "/" + TileFormationDesk.MAX + (desk.library.size() > slot ? "" : " (new)"));
        int colW = (fr.cw - 16) / 3, x0 = fr.cx, x1 = fr.cx + colW + 8, x2 = fr.cx + 2 * (colW + 8);
        int listTop = fr.cy + 18, listH = fr.ch - 18 - 64;
        int rows = listH / ROW;
        // ---- catalogue
        filter.drawTextBox();
        PrideFrame.card(x0, listTop, colW, listH, PrideFrame.BLUE);
        catScroll = Math.max(0, Math.min(catScroll, Math.max(0, catalogue.size() - rows)));
        for (int i = 0; i < rows && catScroll + i < catalogue.size(); i++) {
            EntityRollingStockDefinition d = catalogue.get(catScroll + i);
            int ry = listTop + 2 + i * ROW;
            boolean over = mx >= x0 && mx < x0 + colW && my >= ry && my < ry + ROW;
            if (over) Gui.drawRect(x0 + 1, ry, x0 + colW - 4, ry + ROW, PrideFrame.TILE_HOVER);
            Gui.drawRect(x0 + 3, ry + 2, x0 + 6, ry + ROW - 2, colour(d));
            fontRenderer.drawString(type(d), x0 + 9, ry + 2, 0xFF8A8499);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(Formation.shortName(d), colW - 46), x0 + 38, ry + 2, 0xFFFFFFFF);
            hits.add(new int[]{x0, ry, colW - 4, ROW});
            acts.add(() -> { if (f.cars.size() < 48) { f.cars.add(new Formation.Car(d.defID, false, "")); selCar = f.cars.size() - 1; } });
        }
        PrideFrame.scrollbar(x0 + colW - 4, listTop + 2, listH - 4, catScroll, rows, catalogue.size());
        // ---- the formation
        fontRenderer.drawStringWithShadow("§lFORMATION §r§7(" + f.cars.size() + " vehicles)", x1, fr.cy + 4, 0xFFFFFF);
        PrideFrame.card(x1, listTop, colW, listH, PrideFrame.PINK);
        carScroll = Math.max(0, Math.min(carScroll, Math.max(0, f.cars.size() - rows)));
        for (int i = 0; i < rows && carScroll + i < f.cars.size(); i++) {
            int k = carScroll + i;
            Formation.Car c = f.cars.get(k);
            EntityRollingStockDefinition d = c.definition();
            int ry = listTop + 2 + i * ROW;
            if (k == selCar) Gui.drawRect(x1 + 1, ry, x1 + colW - 4, ry + ROW, PrideFrame.TILE_ON);
            else if (mx >= x1 && mx < x1 + colW && my >= ry && my < ry + ROW) Gui.drawRect(x1 + 1, ry, x1 + colW - 4, ry + ROW, PrideFrame.TILE_HOVER);
            String t = (k + 1) + (c.flip ? " < " : " > ") + Formation.shortName(d) + (c.texture.isEmpty() ? "" : " [" + texName(d, c.texture) + "]");
            fontRenderer.drawString(fontRenderer.trimStringToWidth(t, colW - 22), x1 + 4, ry + 2, d == null ? 0xFFFF5050 : 0xFFFFFFFF);
            if (k == f.lead) fontRenderer.drawString("§eLEAD", x1 + colW - 30, ry + 2, 0xFFFFD040);
            hits.add(new int[]{x1, ry, colW - 4, ROW});
            acts.add(() -> selCar = k);
        }
        PrideFrame.scrollbar(x1 + colW - 4, listTop + 2, listH - 4, carScroll, rows, f.cars.size());
        int by = listTop + listH + 4, bw = (colW - 10) / 6;
        boolean has = selCar >= 0 && selCar < f.cars.size();
        int c0 = has ? PrideFrame.BUTTON : 0xFF1A1622;
        btn(x1, by, bw, 14, "UP", c0, mx, my, () -> move(-1));
        btn(x1 + (bw + 2), by, bw, 14, "DOWN", c0, mx, my, () -> move(1));
        btn(x1 + 2 * (bw + 2), by, bw, 14, "FLIP", c0, mx, my, () -> { if (ok()) f.cars.get(selCar).flip = !f.cars.get(selCar).flip; });
        btn(x1 + 3 * (bw + 2), by, bw, 14, "PAINT", c0, mx, my, this::cycleTexture);
        btn(x1 + 4 * (bw + 2), by, bw, 14, "LEAD", c0, mx, my, () -> { if (ok()) f.lead = selCar; });
        btn(x1 + 5 * (bw + 2), by, bw, 14, "DEL", has ? 0xFF7A1E1E : 0xFF1A1622, mx, my, () -> {
            if (!ok()) return;
            f.cars.remove(selCar);
            if (f.lead >= f.cars.size()) f.lead = Math.max(0, f.cars.size() - 1);
            selCar = Math.min(selCar, f.cars.size() - 1);
        });
        // ---- figures and checks
        name.drawTextBox();
        Formation.Stats s = f.stats(desk.maxLen(), desk.grade());
        int ty = fr.cy + 20;
        String[] lines = {
                String.format(Locale.ROOT, "Length  %.0f m %s", s.lengthM, desk.maxLen() > 0 ? "(siding " + Math.round(desk.maxLen()) + " m)" : ""),
                String.format(Locale.ROOT, "Mass    %.0f t", s.tonnes),
                String.format(Locale.ROOT, "Braked  %.0f %% of mass", s.brakedPct),
                String.format(Locale.ROOT, "Power   %.0f kW  (%.1f kW/t)", s.kw, s.kwPerT),
                String.format(Locale.ROOT, "Start TE %.0f kN", s.teKn),
                String.format(Locale.ROOT, "Starts on %.1f %% (ruling %.1f %%)", s.startGradePct, desk.grade()),
                String.format(Locale.ROOT, "Top speed %.0f km/h", s.maxKmh),
                String.format(Locale.ROOT, "%d units: %d powered, %d cab cars", s.locos, s.powered, s.cabs),
                String.format(Locale.ROOT, "Seats   %d", s.seats)};
        for (String l : lines) { fontRenderer.drawString(l, x2, ty, 0xFFDDE3EA); ty += 10; }
        ty += 3;
        for (String p : s.problems) { if (ty > listTop + listH - 30) break; fontRenderer.drawString(fontRenderer.trimStringToWidth("✖ " + p, colW), x2, ty, 0xFFFF6060); ty += 10; }
        for (String n : s.notes) { if (ty > listTop + listH - 30) break; fontRenderer.drawString(fontRenderer.trimStringToWidth("• " + n, colW), x2, ty, 0xFFFFD040); ty += 10; }
        if (s.problems.isEmpty() && !f.cars.isEmpty()) { fontRenderer.drawString("✔ CHECKS OK", x2, ty, 0xFF50E070); }
        int hw = (colW - 6) / 3;
        btn(x2, listTop + listH - 32, colW, 14, "Livery: " + (f.livery == 1 ? "COMPANY COLOURS" : "BUILDER'S PAINT"), PrideFrame.BUTTON, mx, my, () -> f.livery = 1 - f.livery);
        btn(x2, by - 16, hw, 14, "◀ SLOT", PrideFrame.BUTTON, mx, my, () -> slot(-1));
        btn(x2 + hw + 3, by - 16, hw, 14, "SLOT ▶", PrideFrame.BUTTON, mx, my, () -> slot(1));
        btn(x2 + 2 * (hw + 3), by - 16, hw, 14, "CLEAR", PrideFrame.BUTTON, mx, my, () -> { f.cars.clear(); f.lead = 0; selCar = -1; });
        btn(x2, by, colW, 14, "SAVE TO DESK", 0xFF1F7A3A, mx, my, () -> {
            f.name = name.getText().isEmpty() ? "FORMATION" : name.getText().toUpperCase(Locale.ROOT);
            RailMap.NETWORK.sendToServer(new PacketFormation(desk.getPos(), "save", slot, f.write()));
        });
        // ---- the train drawn side on, to scale
        int dy = fr.cy + fr.ch - 40, dw = fr.cw;
        PrideFrame.card(fr.cx, dy, dw, 38, PrideFrame.BLUE);
        Gui.drawRect(fr.cx + 2, dy + 30, fr.cx + dw - 2, dy + 31, 0xFF707070);
        double total = Math.max(1, s.lengthM);
        double k = Math.min(4.0, (dw - 8) / total);
        double xx = fr.cx + 4;
        Gauge g = Gauge.standard();
        for (int i = 0; i < f.cars.size(); i++) {
            EntityRollingStockDefinition d = f.cars.get(i).definition();
            double len = d == null ? 10 : d.getLength(g);
            int a = (int) xx, b = (int) (xx + len * k) - 1;
            int top = d instanceof LocomotiveDefinition ? dy + 12 : dy + 15;
            Gui.drawRect(a, top, Math.max(a + 1, b), dy + 28, d == null ? 0xFFFF4040 : colour(d));
            if (i == f.lead) Gui.drawRect(a, top - 3, Math.max(a + 1, b), top - 1, 0xFFFFD040);
            if (i == selCar) Gui.drawRect(a, dy + 32, Math.max(a + 1, b), dy + 34, 0xFFFFFFFF);
            boolean flip = f.cars.get(i).flip;
            Gui.drawRect(flip ? a : b - 2, top + 2, flip ? a + 2 : b, top + 5, 0xFF202020);   // which end is the "front"
            xx += len * k;
        }
        fontRenderer.drawString(String.format(Locale.ROOT, "%.0f m", s.lengthM), fr.cx + dw - 40, dy + 3, 0xFF8A8499);
        super.drawScreen(mx, my, pt);
    }

    private boolean ok() { return selCar >= 0 && selCar < f.cars.size(); }

    private void move(int d) {
        if (!ok()) return;
        int j = selCar + d;
        if (j < 0 || j >= f.cars.size()) return;
        Collections.swap(f.cars, selCar, j);
        if (f.lead == selCar) f.lead = j; else if (f.lead == j) f.lead = selCar;
        selCar = j;
    }

    private void cycleTexture() {
        if (!ok()) return;
        Formation.Car c = f.cars.get(selCar);
        EntityRollingStockDefinition d = c.definition();
        if (d == null || d.textureNames == null || d.textureNames.isEmpty()) return;
        List<String> keys = new ArrayList<>(d.textureNames.keySet());
        int i = keys.indexOf(c.texture);
        c.texture = i + 1 >= keys.size() ? "" : keys.get(i + 1);
    }

    private static String texName(EntityRollingStockDefinition d, String t) {
        if (d == null || d.textureNames == null) return t;
        return d.textureNames.getOrDefault(t, t);
    }

    private void slot(int d) {
        int n = Math.min(TileFormationDesk.MAX, desk.library.size() + 1);
        slot = Math.floorMod(slot + d, n);
        f = slot < desk.library.size() ? Formation.read(desk.library.get(slot).write()) : new Formation();
        name.setText(f.name);
        selCar = -1;
    }

    @Override
    protected void mouseClicked(int mx, int my, int b) throws java.io.IOException {
        super.mouseClicked(mx, my, b);
        filter.mouseClicked(mx, my, b);
        name.mouseClicked(mx, my, b);
        if (b != 0) return;
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] r = hits.get(i);
            if (mx >= r[0] && my >= r[1] && mx < r[0] + r[2] && my < r[1] + r[3]) {
                acts.get(i).run();
                mc.player.playSound(com.dogpound.railmap.sound.PackSounds.ev("ui.click"), 0.5f, 1.0f);
                return;
            }
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int w = Mouse.getEventDWheel();
        if (w == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int colW = (fr.cw - 16) / 3;
        int step = w > 0 ? -3 : 3;
        if (mx < fr.cx + colW + 4) catScroll += step; else carScroll += step;
    }

    @Override
    protected void keyTyped(char c, int code) throws java.io.IOException {
        if (filter.textboxKeyTyped(c, code)) { refilter(); return; }
        if (name.textboxKeyTyped(c, code)) return;
        super.keyTyped(c, code);
    }

    @Override public void updateScreen() { filter.updateCursorCounter(); name.updateCursorCounter(); }
}
