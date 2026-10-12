package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.program.PacketProgram;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.program.Rule;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.util.*;

/**
 * The signal-box program editor, drawn as a side panel next to the map. The map screen owns the panel: it calls
 * draw/click/wheel/keyTyped and forwards map clicks via picked()/selectFromMap().
 * <p>
 * 2026-10-10 (her ChatGPT list #98): rules can belong to a ROUTINE (run by a CALL), use EXPRESSION / EVENT / TIMER
 * conditions, and SET VARIABLES, START TIMERS, CALL routines, SEND EVENTS and PRESS machine controls; the VARS tab shows
 * the program's variables, timers, events heard and the watchdog. Clicks now act on click only (they used to act on
 * hover) and the panel reads the server's copy of the program (PacketProgram.client) instead of an empty one.
 */
public final class ProgramPanel {
    private final BlockPos box;
    private ProgramData data = new ProgramData();

    private int tab = 0; // 0 PROGRAM, 1 CHANNELS, 2 VARS, 3 ALARMS
    private int scroll, editScroll, chScroll, varScroll, alarmScroll;

    // PROGRAM tab
    private boolean editing;
    private Rule edit;
    /** which text field is being typed into (name, group, condtxt, cond2txt, msg, elsemsg), null = none */
    private String textKind;
    private String textBuf = "";

    private enum PickField { COND, COND2, ACT, ACT2, ELSE, ELSE2 }
    private PickField pickField;
    private String pickHint = "";
    private long focus = -1;

    // CHANNELS tab
    private int renameIdx = -1;
    private String renameBuf = "";

    private int mx, my;
    private int px, py, pw, ph;
    private long lastRequest;

    /** this frame's clickable areas: x, y, w, h, and what a left (dir 1) / right (dir -1) click does */
    private final List<int[]> hitBox = new ArrayList<>();
    private final List<java.util.function.IntConsumer> hitDo = new ArrayList<>();

    public ProgramPanel(BlockPos box) {
        this.box = box;
        RailMap.NETWORK.sendToServer(PacketProgram.request(box));
    }

    private void hit(int x, int y, int w, int h, java.util.function.IntConsumer what) {
        hitBox.add(new int[]{x, y, w, h});
        hitDo.add(what);
    }

    private void button(int x, int y, int w, int h, String label, int color, java.util.function.IntConsumer what) {
        PrideFrame.button(x, y, w, h, label, color, mx, my);
        hit(x, y, w, h, what);
    }

    public void draw(FontRenderer fr, int x, int y, int w, int h, int mx, int my) {
        this.px = x; this.py = y; this.pw = w; this.ph = h;
        this.mx = mx; this.my = my;
        data = PacketProgram.client;
        if (System.currentTimeMillis() - lastRequest > 1000) { lastRequest = System.currentTimeMillis(); RailMap.NETWORK.sendToServer(PacketProgram.request(box)); }   // live channels, vars, timers, watchdog
        hitBox.clear();
        hitDo.clear();
        PrideFrame.sync();

        PrideFrame.gradient(x, y, x + w, y + h, PrideFrame.PANEL_TOP, PrideFrame.PANEL_BOTTOM);
        int sw = w / PrideFrame.RAINBOW.length;
        for (int i = 0; i < PrideFrame.RAINBOW.length; i++)
            Gui.drawRect(x + i * sw, y, i == PrideFrame.RAINBOW.length - 1 ? x + w : x + (i + 1) * sw, y + 3, PrideFrame.RAINBOW[i]);

        String[] tabs = {"PROGRAM", "CHANNELS", data.halted.isEmpty() ? "VARS" : "§cVARS !", "ALARMS (" + data.alarms.size() + ")"};
        int tabW = w / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int tx = x + i * tabW;
            int color = i == tab ? PrideFrame.TILE_ON : PrideFrame.BUTTON;
            boolean over = mx >= tx && my >= y + 3 && mx < tx + tabW && my < y + 23;
            if (over) color = PrideFrame.brighten(color);
            Gui.drawRect(tx, y + 3, tx + tabW, y + 23, color);
            if (i == tab) Gui.drawRect(tx, y + 22, tx + tabW, y + 23, PrideFrame.PINK);
            String label = fr.trimStringToWidth(tabs[i], tabW - 4);
            fr.drawStringWithShadow(label, tx + (tabW - fr.getStringWidth(label)) / 2f, y + 9, i == tab ? 0xFFFFFF : PrideFrame.DIM);
            final int k = i;
            hit(tx, y + 3, tabW, 20, d -> { tab = k; textKind = null; renameIdx = -1; });
        }

        int cy = y + 27, ch = h - 27;
        switch (tab) {
            case 0 -> drawProgram(fr, x, w, cy, ch);
            case 1 -> drawChannels(fr, x, w, cy, ch);
            case 2 -> drawVars(fr, x, w, cy, ch);
            default -> drawAlarms(fr, x, w, cy, ch);
        }
    }

    // ---- PROGRAM ---------------------------------------------------------------------------------------------------
    private void drawProgram(FontRenderer fr, int x, int w, int cy, int ch) {
        int ix = x + 6, iw = w - 12;
        if (editing) { drawEdit(fr, ix, cy, iw, ch); return; }
        String run = data.halted.isEmpty() ? "§aRUNNING§7  " + data.lastSteps + " steps, " + data.lastMicros + " µs a pass" : "§c§lHALTED§c by the watchdog: " + data.halted;
        fr.drawStringWithShadow(fr.trimStringToWidth(run, iw), ix, cy, 0xFFFFFF);
        cy += 11; ch -= 11;
        button(ix, cy, iw, 20, "+ New rule", PrideFrame.BUTTON, d -> startEdit(null));
        int listY = cy + 24, listH = ch - 24;
        if (focus >= 0) {
            button(ix, listY - 1, iw, 14, "show all", PrideFrame.BUTTON, d -> focus = -1);
            listY += 16; listH -= 16;
        }
        List<Rule> shown = visibleRules();
        int rowH = 22, total = shown.size() * rowH;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - listH)));
        if (listH <= 0) return;
        PrideFrame.clip(ix, listY, iw, listH);
        for (int i = 0; i < shown.size(); i++) {
            int ry = listY + i * rowH - scroll;
            if (ry + rowH < listY || ry > listY + listH) continue;
            Rule r = shown.get(i);
            boolean over = mx >= ix && my >= ry && mx < ix + iw && my < ry + rowH && my >= listY && my < listY + listH;
            Gui.drawRect(ix, ry, ix + iw, ry + rowH, over ? PrideFrame.TILE_HOVER : 0x00000000);
            int togX = ix + 4, togY = ry + 5;
            Gui.drawRect(togX, togY, togX + 12, togY + 12, r.enabled ? 0xFF3FBF5F : 0xFF555555);
            String label = (r.name.isEmpty() ? "#" + r.id : r.name) + (r.group.isEmpty() ? "" : "  §d[" + r.group + "]");
            fr.drawStringWithShadow(label, togX + 18, ry + 3, 0xFFFFFF);
            String sum = fr.trimStringToWidth(r.summary(), iw - 18 - 60);
            fr.drawStringWithShadow(sum, togX + 18, ry + 12, PrideFrame.DIM);
            int bx = ix + iw - 54;
            PrideFrame.button(bx, ry + 2, 16, 18, "▲", PrideFrame.BUTTON, mx, my);
            PrideFrame.button(bx + 18, ry + 2, 16, 18, "▼", PrideFrame.BUTTON, mx, my);
            PrideFrame.button(bx + 36, ry + 2, 16, 18, "✖", 0xFF8A3030, mx, my);
            if (ry < listY || ry + rowH > listY + listH) continue;          // half-hidden rows: look, don't click
            final Rule rr = r;
            hit(ix, ry, iw, rowH, d -> startEdit(rr));                       // later hits win: the small buttons come next
            hit(togX, togY, 12, 12, d -> { NBTTagCompound t = new NBTTagCompound(); t.setInteger("id", rr.id); send(PacketProgram.TOGGLE_RULE, t); });
            hit(bx, ry + 2, 16, 18, d -> sendMove(rr.id, -1));
            hit(bx + 18, ry + 2, 16, 18, d -> sendMove(rr.id, 1));
            hit(bx + 36, ry + 2, 16, 18, d -> sendDelete(rr.id));
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(ix + iw - 4, listY, listH, scroll, listH, total);
    }

    private void drawEdit(FontRenderer fr, int x, int y, int w, int h) {
        int rowH = 20;
        List<String[]> rows = buildEditRows();
        int listH = h - 40, total = rows.size() * rowH;
        editScroll = Math.max(0, Math.min(editScroll, Math.max(0, total - listH)));
        PrideFrame.clip(x, y, w, listH);
        int ry = y - editScroll;
        for (String[] row : rows) {
            String kind = row[2];
            if (ry + rowH >= y && ry <= y + listH) {
                if (kind.equals(textKind)) {
                    Gui.drawRect(x, ry, x + w, ry + rowH, 0xFF100C18);
                    Gui.drawRect(x, ry, x + 2, ry + rowH, PrideFrame.PINK);
                    fr.drawStringWithShadow(fr.trimStringToWidth(row[0] + ": " + textBuf + (System.currentTimeMillis() / 400 % 2 == 0 ? "§7_" : ""), w - 8), x + 6, ry + 6, 0xFFFFFF);
                } else {
                    String full = fr.trimStringToWidth(row[0] + (row[1].isEmpty() ? "" : "  " + row[1]), w - 6);
                    PrideFrame.button(x, ry, w, rowH, full, PrideFrame.BUTTON, mx, my);
                }
                if (ry >= y && ry + rowH <= y + listH) hit(x, ry, w, rowH, d -> handleEditRow(kind, d));
            }
            ry += rowH;
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 4, y, listH, editScroll, listH, total);
        int by = y + h - 34;
        button(x, by, w / 2 - 2, 24, "SAVE", 0xFF2F8F4F, d -> saveEdit());
        button(x + w / 2 + 2, by, w / 2 - 2, 24, "CANCEL", 0xFF8A3030, d -> { editing = false; edit = null; textKind = null; });
    }

    private List<String[]> buildEditRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Name", edit.name, "name"});
        rows.add(new String[]{"Routine", edit.group.isEmpty() ? "(main program - every pass)" : edit.group + " (runs when CALLed)", "group"});
        rows.add(new String[]{"WHEN", edit.cond.label, "cond"});
        condRows(rows, edit.cond, edit.condPos, edit.condChannel, edit.condText, "cond");
        rows.add(new String[]{"AND", edit.cond2 == null ? "none" : edit.cond2.label, "cond2"});
        if (edit.cond2 != null) condRows(rows, edit.cond2, edit.cond2Pos, edit.cond2Channel, edit.cond2Text, "cond2");
        rows.add(new String[]{"THEN", edit.act.label, "act"});
        actRows(rows, edit.act, edit.actPos, edit.actPos2, edit.actChannel, edit.text, "act", "msg");
        rows.add(new String[]{"ELSE", edit.elseAct.label, "elseact"});
        actRows(rows, edit.elseAct, edit.elsePos, edit.elsePos2, edit.elseChannel, edit.elseText, "else", "elsemsg");
        return rows;
    }

    private void condRows(List<String[]> rows, Rule.Cond c, long pos, int channel, String text, String k) {
        if (c.needsPos()) rows.add(new String[]{"Where", Rule.where(pos), k + "pos"});
        if (c.needsChannel()) rows.add(new String[]{"Channel", String.valueOf(channel), k + "ch"});
        if (c.needsRadius()) rows.add(new String[]{"Within", edit.radius + " blocks", k + "rad"});
        if (c.needsText()) rows.add(new String[]{c == Rule.Cond.EXPR ? "Expression" : c == Rule.Cond.EVENT ? "Event" : "Timer", text.isEmpty() ? "§8" + c.textHint() : text, k + "txt"});
    }

    private void actRows(List<String[]> rows, Rule.Act a, long pos, long pos2, int channel, String text, String k, String textKey) {
        if (a.needsPos()) rows.add(new String[]{a == Rule.Act.PRESS ? "Machine" : "Target", Rule.where(pos), k + "pos"});
        if (a.needsPos2()) rows.add(new String[]{"To signal", Rule.where(pos2), k + "pos2"});
        if (a.needsChannel()) rows.add(new String[]{"Channel", String.valueOf(channel), k + "ch"});
        if (a.needsText()) rows.add(new String[]{a.textLabel(), text, textKey});
    }

    private void handleEditRow(String kind, int dir) {
        if (textKind != null && !textKind.equals(kind)) commitText();
        int step = dir * (GuiScreen.isShiftKeyDown() ? 8 : 1);
        switch (kind) {
            case "name", "group", "condtxt", "cond2txt", "msg", "elsemsg" -> { if (kind.equals(textKind)) commitText(); else { textKind = kind; textBuf = textOf(kind); } }
            case "cond" -> { Rule.Cond[] v = Rule.Cond.values(); edit.cond = v[(edit.cond.ordinal() + dir + v.length) % v.length]; }
            case "cond2" -> {
                Rule.Cond[] v = Rule.Cond.values();
                int i = edit.cond2 == null ? (dir > 0 ? 0 : v.length - 1) : edit.cond2.ordinal() + dir;
                edit.cond2 = edit.cond2 != null && (i < 0 || i >= v.length) ? null : v[Math.floorMod(i, v.length)];
            }
            case "act" -> { Rule.Act[] v = Rule.Act.values(); edit.act = v[(edit.act.ordinal() + dir + v.length) % v.length]; }
            case "elseact" -> { Rule.Act[] v = Rule.Act.values(); edit.elseAct = v[(edit.elseAct.ordinal() + dir + v.length) % v.length]; }
            case "condpos" -> startPick(PickField.COND, "Click the place this condition watches");
            case "cond2pos" -> startPick(PickField.COND2, "Click the place this AND condition watches");
            case "actpos" -> startPick(PickField.ACT, edit.act == Rule.Act.PRESS ? "Click the machine whose control this presses" : "Click the target for this action");
            case "actpos2" -> startPick(PickField.ACT2, "Click the destination signal for this route");
            case "elsepos" -> startPick(PickField.ELSE, "Click the target for this else action");
            case "elsepos2" -> startPick(PickField.ELSE2, "Click the destination signal for this else route");
            case "condch" -> edit.condChannel = wrapChannel(edit.condChannel + step);
            case "cond2ch" -> edit.cond2Channel = wrapChannel(edit.cond2Channel + step);
            case "actch" -> edit.actChannel = wrapChannel(edit.actChannel + step);
            case "elsech" -> edit.elseChannel = wrapChannel(edit.elseChannel + step);
            case "condrad", "cond2rad" -> edit.radius = Math.max(4, Math.min(128, edit.radius + dir * 4));
            default -> { }
        }
    }

    private String textOf(String kind) {
        return switch (kind) {
            case "name" -> edit.name;
            case "group" -> edit.group;
            case "condtxt" -> edit.condText;
            case "cond2txt" -> edit.cond2Text;
            case "msg" -> edit.text;
            default -> edit.elseText;
        };
    }

    private void commitText() {
        if (textKind == null || edit == null) { textKind = null; return; }
        String v = textBuf.trim();
        switch (textKind) {
            case "name" -> edit.name = v;
            case "group" -> edit.group = v.replace(' ', '_');
            case "condtxt" -> edit.condText = v;
            case "cond2txt" -> edit.cond2Text = v;
            case "msg" -> edit.text = v;
            default -> edit.elseText = v;
        }
        textKind = null;
    }

    private int wrapChannel(int c) { return Math.floorMod(c - 1, 64) + 1; }

    private void startEdit(Rule src) {
        edit = new Rule();
        if (src == null) edit.id = 0;                                  // the server gives a new rule its id
        else edit = Rule.read(src.write());
        editing = true;
        editScroll = 0;
        textKind = null;
    }

    private void saveEdit() {
        commitText();
        send(PacketProgram.SAVE_RULE, edit.write());
        editing = false;
        edit = null;
    }

    private List<Rule> visibleRules() {
        if (focus < 0) return data.rules;
        List<Rule> out = new ArrayList<>();
        for (Rule r : data.rules)
            if (r.condPos == focus || r.cond2Pos == focus || r.actPos == focus || r.actPos2 == focus || r.elsePos == focus || r.elsePos2 == focus) out.add(r);
        return out;
    }

    private void send(byte op, NBTTagCompound t) { RailMap.NETWORK.sendToServer(PacketProgram.op(op, t, box)); }

    private void sendMove(int id, int dir) { NBTTagCompound t = new NBTTagCompound(); t.setInteger("id", id); t.setInteger("dir", dir); send(PacketProgram.MOVE_RULE, t); }

    private void sendDelete(int id) { NBTTagCompound t = new NBTTagCompound(); t.setInteger("id", id); send(PacketProgram.DELETE_RULE, t); }

    // ---- CHANNELS --------------------------------------------------------------------------------------------------
    private void drawChannels(FontRenderer fr, int x, int w, int cy, int ch) {
        int ix = x + 6, iw = w - 12, cols = 4, cellW = iw / cols, cellH = 24, total = 16 * cellH;
        chScroll = Math.max(0, Math.min(chScroll, Math.max(0, total - ch)));
        PrideFrame.clip(ix, cy, iw, ch);
        for (int i = 0; i < 64; i++) {
            int cx = ix + i % cols * cellW, cyy = cy + i / cols * cellH - chScroll;
            if (cyy + cellH < cy || cyy > cy + ch) continue;
            int n = i + 1;
            boolean on = data.ch(n);
            String name = data.channelName[n] == null ? "" : data.channelName[n];
            if (renameIdx == i) {
                Gui.drawRect(cx + 2, cyy + 2, cx + cellW - 2, cyy + cellH - 2, 0xFF100C18);
                Gui.drawRect(cx + 2, cyy + 2, cx + 4, cyy + cellH - 2, PrideFrame.PINK);
                fr.drawStringWithShadow(renameBuf + (System.currentTimeMillis() / 400 % 2 == 0 ? "§7_" : ""), cx + 8, cyy + 8, 0xFFFFFF);
            } else {
                if (mx >= cx && my >= cyy && mx < cx + cellW && my < cyy + cellH) Gui.drawRect(cx + 1, cyy + 1, cx + cellW - 1, cyy + cellH - 1, PrideFrame.TILE_HOVER);
                Gui.drawRect(cx + 4, cyy + 8, cx + 12, cyy + 16, on ? 0xFF3FBF5F : 0xFF444444);
                fr.drawStringWithShadow(fr.trimStringToWidth(n + " " + name, cellW - 18), cx + 16, cyy + 8, on ? 0xFFFFFF : PrideFrame.DIM);
            }
            if (cyy < cy || cyy + cellH > cy + ch) continue;
            final int k = i;
            hit(cx, cyy, cellW, cellH, d -> {
                if (renameIdx == k) { renameIdx = -1; return; }
                if (d < 0) { renameIdx = k; renameBuf = data.channelName[k + 1] == null ? "" : data.channelName[k + 1]; return; }
                NBTTagCompound t = new NBTTagCompound();
                t.setInteger("n", k + 1);
                t.setBoolean("v", !data.ch(k + 1));
                send(PacketProgram.CHANNEL, t);
            });
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(ix + iw - 4, cy, ch, chScroll, ch, total);
    }

    // ---- VARS: variables, timers, events, the watchdog --------------------------------------------------------------
    private void drawVars(FontRenderer fr, int x, int w, int cy, int ch) {
        int ix = x + 6, iw = w - 12;
        boolean halted = !data.halted.isEmpty();
        Gui.drawRect(ix, cy, ix + iw, cy + 22, halted ? 0xFF5A1414 : 0xFF143A1E);
        fr.drawStringWithShadow(fr.trimStringToWidth(halted ? "§lWATCHDOG TRIPPED§r - " + data.halted : "Watchdog OK - last pass " + data.lastSteps + " steps / "
                + data.lastMicros + " µs, worst " + data.worstMicros + " µs", iw - 8), ix + 4, cy + 7, 0xFFFFFF);
        cy += 25;
        button(ix, cy, iw / 2 - 2, 18, halted ? "RESET WATCHDOG" : "(running)", halted ? 0xFF8A6A10 : PrideFrame.BUTTON, d -> send(PacketProgram.RESET_WATCHDOG, new NBTTagCompound()));
        button(ix + iw / 2 + 2, cy, iw / 2 - 2, 18, "CLEAR VARIABLES + TIMERS", 0xFF8A3030, d -> send(PacketProgram.CLEAR_VARS, new NBTTagCompound()));
        cy += 22;
        List<String> lines = new ArrayList<>();
        lines.add("§b== Variables (" + data.vars.size() + ")");
        for (Map.Entry<String, Double> e : data.vars.entrySet()) lines.add("  " + e.getKey() + " = " + com.dogpound.railmap.api.Expr.fmt(e.getValue()));
        if (data.vars.isEmpty()) lines.add("§8  none yet - rules set them with 'Set variable', logic modules write them");
        lines.add("§b== Timers (" + data.timers.size() + ")");
        long now = net.minecraft.client.Minecraft.getMinecraft().world == null ? 0 : net.minecraft.client.Minecraft.getMinecraft().world.getTotalWorldTime();
        for (Map.Entry<String, long[]> e : data.timers.entrySet()) {
            long left = e.getValue()[0] - now;
            lines.add("  " + e.getKey() + "  " + (left <= 0 ? "§aDONE" : String.format(Locale.ROOT, "%.1f s left of %.0f", left / 20.0, e.getValue()[1] / 20.0)));
        }
        lines.add("§b== Events heard lately");
        for (String e : data.recentEvents) lines.add("  " + e);
        lines.add("§b== Routines");
        Set<String> groups = new TreeSet<>();
        for (Rule r : data.rules) if (!r.group.isEmpty()) groups.add(r.group);
        lines.add(groups.isEmpty() ? "§8  none - give rules a Routine name, run them with 'Run routine'" : "  " + String.join(", ", groups));
        int rowH = 11, listH = ch - 47, total = lines.size() * rowH;
        varScroll = Math.max(0, Math.min(varScroll, Math.max(0, total - listH)));
        PrideFrame.clip(ix, cy, iw, listH);
        for (int i = 0; i < lines.size(); i++) {
            int ry = cy + i * rowH - varScroll;
            if (ry + rowH < cy || ry > cy + listH) continue;
            fr.drawStringWithShadow(fr.trimStringToWidth(lines.get(i), iw - 6), ix + 2, ry, 0xE8ECF0);
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(ix + iw - 4, cy, listH, varScroll, listH, total);
    }

    // ---- ALARMS ----------------------------------------------------------------------------------------------------
    private void drawAlarms(FontRenderer fr, int x, int w, int cy, int ch) {
        int ix = x + 6, iw = w - 12;
        button(ix, cy, iw, 20, "Clear", 0xFF8A3030, d -> send(PacketProgram.CLEAR_ALARMS, new NBTTagCompound()));
        int listY = cy + 24, listH = ch - 24, rowH = 16, total = data.alarms.size() * rowH;
        alarmScroll = Math.max(0, Math.min(alarmScroll, Math.max(0, total - listH)));
        if (listH <= 0) return;
        PrideFrame.clip(ix, listY, iw, listH);
        for (int i = 0; i < data.alarms.size(); i++) {
            int ay = listY + i * rowH - alarmScroll;
            if (ay + rowH < listY || ay > listY + listH) continue;
            fr.drawStringWithShadow(fr.trimStringToWidth(data.alarms.get(i), iw - 4), ix + 2, ay + 2, 0xFFFF6B6B);
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(ix + iw - 4, listY, listH, alarmScroll, listH, total);
    }

    // ---- input -----------------------------------------------------------------------------------------------------
    public boolean click(int mx, int my, int button) {
        this.mx = mx;
        this.my = my;
        for (int i = hitBox.size() - 1; i >= 0; i--) {
            int[] b = hitBox.get(i);
            if (mx >= b[0] && my >= b[1] && mx < b[0] + b[2] && my < b[1] + b[3]) { hitDo.get(i).accept(button == 1 ? -1 : 1); return true; }
        }
        return mx >= px && my >= py && mx < px + pw && my < py + ph;
    }

    public void wheel(int dir) {
        switch (tab) {
            case 0 -> { if (editing) editScroll -= dir * 20; else scroll -= dir * 22; }
            case 1 -> chScroll -= dir * 24;
            case 2 -> varScroll -= dir * 11;
            default -> alarmScroll -= dir * 16;
        }
        // the draw clamps them
        scroll = Math.max(0, scroll); editScroll = Math.max(0, editScroll); chScroll = Math.max(0, chScroll); varScroll = Math.max(0, varScroll); alarmScroll = Math.max(0, alarmScroll);
    }

    public boolean keyTyped(char c, int key) {
        if (textKind != null) {
            if (key == Keyboard.KEY_RETURN) { commitText(); return true; }
            if (key == Keyboard.KEY_ESCAPE) { textKind = null; return true; }
            if (key == Keyboard.KEY_BACK) { if (!textBuf.isEmpty()) textBuf = textBuf.substring(0, textBuf.length() - 1); return true; }
            if (c >= 32 && c < 127 && textBuf.length() < 120) textBuf += c;
            return true;
        }
        if (renameIdx >= 0) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_ESCAPE) {
                if (key == Keyboard.KEY_RETURN) {
                    NBTTagCompound t = new NBTTagCompound();
                    t.setInteger("n", renameIdx + 1);
                    t.setString("name", renameBuf);
                    send(PacketProgram.CHANNEL_NAME, t);
                }
                renameIdx = -1;
                return true;
            }
            if (key == Keyboard.KEY_BACK) { if (!renameBuf.isEmpty()) renameBuf = renameBuf.substring(0, renameBuf.length() - 1); return true; }
            if (c >= 32 && c < 127 && renameBuf.length() < 24) renameBuf += c;
            return true;
        }
        return false;
    }

    public boolean wantsPick() { return pickField != null; }

    public String pickHint() { return pickHint; }

    public void picked(BlockPos p) {
        if (pickField == null || edit == null) { pickField = null; return; }
        long v = p.toLong();
        switch (pickField) {
            case COND -> edit.condPos = v;
            case COND2 -> edit.cond2Pos = v;
            case ACT -> edit.actPos = v;
            case ACT2 -> edit.actPos2 = v;
            case ELSE -> edit.elsePos = v;
            case ELSE2 -> edit.elsePos2 = v;
        }
        pickField = null;
        pickHint = "";
    }

    public void selectFromMap(BlockPos p) {
        focus = p.toLong();
        tab = 0;
        editing = false;
        edit = null;
    }

    public Set<Long> highlighted() {
        Set<Long> out = new HashSet<>();
        if (editing && edit != null)
            for (long k : new long[]{edit.condPos, edit.cond2Pos, edit.actPos, edit.actPos2, edit.elsePos, edit.elsePos2}) if (k != 0) out.add(k);
        return out;
    }

    private void startPick(PickField f, String hint) {
        pickField = f;
        pickHint = hint;
    }
}
