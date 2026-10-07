package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.program.PacketProgram;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.program.Rule;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The signal-box program editor, drawn as a side panel next to the map.
 * The map screen owns the panel: it calls draw/click/wheel/keyTyped and forwards
 * map clicks via picked()/selectFromMap().
 */
public final class ProgramPanel {
    private final BlockPos box;
    private final ProgramData data = new ProgramData();

    private int tab = 0; // 0 PROGRAM, 1 CHANNELS, 2 ALARMS
    private int scroll = 0;

    // PROGRAM tab
    private boolean editing = false;
    private Rule edit;
    private int editScroll = 0;
    private boolean nameEditing = false;
    private String nameBuf = "";
    private boolean msgEditing = false;
    private String msgBuf = "";
    private boolean elseMsgEditing = false;
    private String elseMsgBuf = "";

    // Picking
    private enum PickField { COND, COND2, ACT, ACT2, ELSE, ELSE2 }
    private PickField pickField = null;
    private String pickHint = "";

    // Focus (map click while not picking)
    private long focus = -1;

    // CHANNELS tab
    private int chScroll = 0;
    private int renameIdx = -1;
    private String renameBuf = "";

    // ALARMS tab
    private int alarmScroll = 0;

    private int mx, my;

    public ProgramPanel(BlockPos box) {
        this.box = box;
        RailMap.NETWORK.sendToServer(PacketProgram.request(box));
    }

    public void draw(FontRenderer fr, int x, int y, int w, int h, int mx, int my) {
        this.px = x; this.py = y; this.pw = w; this.ph = h;
        this.mx = mx;
        this.my = my;
        PrideFrame.sync();

        // Panel background
        PrideFrame.gradient(x, y, x + w, y + h, PrideFrame.PANEL_TOP, PrideFrame.PANEL_BOTTOM);
        // Rainbow bar
        int sw = w / PrideFrame.RAINBOW.length;
        for (int i = 0; i < PrideFrame.RAINBOW.length; i++)
            Gui.drawRect(x + i * sw, y, i == PrideFrame.RAINBOW.length - 1 ? x + w : x + (i + 1) * sw, y + 3, PrideFrame.RAINBOW[i]);

        // Tabs
        int tabW = w / 3;
        String[] tabs = {"PROGRAM", "CHANNELS", "ALARMS (" + data.alarms.size() + ")"};
        for (int i = 0; i < 3; i++) {
            int tx = x + i * tabW;
            int color = i == tab ? PrideFrame.TILE_ON : PrideFrame.BUTTON;
            boolean over = mx >= tx && my >= y + 3 && mx < tx + tabW && my < y + 23;
            if (over) color = PrideFrame.brighten(color);
            Gui.drawRect(tx, y + 3, tx + tabW, y + 23, color);
            if (i == tab) Gui.drawRect(tx, y + 22, tx + tabW, y + 23, PrideFrame.PINK);
            String label = tabs[i];
            int lw = fr.getStringWidth(label);
            fr.drawStringWithShadow(label, tx + (tabW - lw) / 2f, y + 9, i == tab ? 0xFFFFFF : PrideFrame.DIM);
        }

        int contentY = y + 27;
        int contentH = h - 27;

        if (tab == 0) drawProgram(fr, x, y, w, contentY, contentH);
        else if (tab == 1) drawChannels(fr, x, y, w, contentY, contentH);
        else drawAlarms(fr, x, y, w, contentY, contentH);
    }

    private void drawProgram(FontRenderer fr, int x, int y, int w, int cy, int ch) {
        int pad = 6;
        int ix = x + pad, iw = w - pad * 2;

        if (!editing) {
            // + New rule button
            if (PrideFrame.button(ix, cy, iw, 20, "+ New rule", PrideFrame.BUTTON, mx, my)) {
                startEdit(null);
            }
            int listY = cy + 24;
            int listH = ch - 24;

            List<Rule> shown = visibleRules();
            int rowH = 22;
            int total = shown.size() * rowH;
            int maxScroll = Math.max(0, total - listH);
            scroll = Math.max(0, Math.min(scroll, maxScroll));

            if (focus >= 0) {
                if (PrideFrame.button(ix, listY - 1, iw, 14, "show all", PrideFrame.BUTTON, mx, my)) {
                    focus = -1;
                }
                listY += 16;
                listH -= 16;
            }

            if (listH > 0) {
                PrideFrame.clip(ix, listY, iw, listH);
                for (int i = 0; i < shown.size(); i++) {
                    int ry = listY + i * rowH - scroll;
                    if (ry + rowH < listY || ry > listY + listH) continue;
                    Rule r = shown.get(i);
                    boolean over = mx >= ix && my >= ry && mx < ix + iw && my < ry + rowH;
                    Gui.drawRect(ix, ry, ix + iw, ry + rowH, over ? PrideFrame.TILE_HOVER : 0x00000000);

                    // ON/OFF toggle
                    int togX = ix + 4, togY = ry + 5, togS = 12;
                    Gui.drawRect(togX, togY, togX + togS, togY + togS, r.enabled ? 0xFF3FBF5F : 0xFF555555);
                    if (over && mx >= togX && my >= togY && mx < togX + togS && my < togY + togS) {
                        // handled in click
                    }

                    // Name / id
                    String label = r.name.isEmpty() ? ("#" + r.id) : r.name;
                    fr.drawStringWithShadow(label, togX + togS + 6, ry + 6, 0xFFFFFF);

                    // Summary in dim
                    String sum = r.summary();
                    int sumX = togX + togS + 6;
                    int sumW = iw - (sumX - ix) - 60;
                    while (sum.length() > 0 && fr.getStringWidth(sum) > sumW) sum = sum.substring(0, sum.length() - 1);
                    fr.drawStringWithShadow(sum, sumX, ry + 14, PrideFrame.DIM);

                    // ▲ ▼ ✖ buttons
                    int bx = ix + iw - 54;
                    if (PrideFrame.button(bx, ry + 2, 16, 18, "▲", PrideFrame.BUTTON, mx, my)) {
                        sendMove(r.id, -1);
                    }
                    if (PrideFrame.button(bx + 18, ry + 2, 16, 18, "▼", PrideFrame.BUTTON, mx, my)) {
                        sendMove(r.id, 1);
                    }
                    if (PrideFrame.button(bx + 36, ry + 2, 16, 18, "✖", 0xFF8A3030, mx, my)) {
                        sendDelete(r.id);
                    }
                }
                PrideFrame.unclip();
            }
            PrideFrame.scrollbar(ix + iw - 4, listY, listH, scroll, listH, total);
        } else {
            drawEdit(fr, ix, cy, iw, ch);
        }
    }

    private void drawEdit(FontRenderer fr, int x, int y, int w, int h) {
        int rowH = 20;
        List<String[]> rows = buildEditRows(fr);
        int total = rows.size() * rowH + 44;
        int maxScroll = Math.max(0, total - h);
        editScroll = Math.max(0, Math.min(editScroll, maxScroll));

        PrideFrame.clip(x, y, w, h);
        int ry = y - editScroll;
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (ry + rowH < y || ry > y + h) { ry += rowH; continue; }
            String label = row[0];
            String value = row[1];
            String kind = row[2];

            if (kind.equals("name") && nameEditing) {
                Gui.drawRect(x, ry, x + w, ry + rowH, 0xFF100C18);
                Gui.drawRect(x, ry, x + 2, ry + rowH, PrideFrame.PINK);
                String txt = nameBuf + (System.currentTimeMillis() / 400 % 2 == 0 ? "§7_" : "");
                fr.drawStringWithShadow(txt, x + 6, ry + 6, 0xFFFFFF);
            } else if (kind.equals("msg") && msgEditing) {
                Gui.drawRect(x, ry, x + w, ry + rowH, 0xFF100C18);
                Gui.drawRect(x, ry, x + 2, ry + rowH, PrideFrame.PINK);
                String txt = msgBuf + (System.currentTimeMillis() / 400 % 2 == 0 ? "§7_" : "");
                fr.drawStringWithShadow(txt, x + 6, ry + 6, 0xFFFFFF);
            } else if (kind.equals("elsemsg") && elseMsgEditing) {
                Gui.drawRect(x, ry, x + w, ry + rowH, 0xFF100C18);
                Gui.drawRect(x, ry, x + 2, ry + rowH, PrideFrame.PINK);
                String txt = elseMsgBuf + (System.currentTimeMillis() / 400 % 2 == 0 ? "§7_" : "");
                fr.drawStringWithShadow(txt, x + 6, ry + 6, 0xFFFFFF);
            } else {
                int color = kind.equals("save") ? 0xFF2F8F4F : kind.equals("cancel") ? 0xFF8A3030 : PrideFrame.BUTTON;
                String full = label + (value.isEmpty() ? "" : "  " + value);
                if (PrideFrame.button(x, ry, w, rowH, full, color, mx, my)) {
                    handleEditRow(kind, i, 1);
                }
                // right click handled in click()
            }
            ry += rowH;
        }

        // Bottom buttons
        int by = y + h - 36;
        if (PrideFrame.button(x, by, w / 2 - 2, 24, "SAVE", 0xFF2F8F4F, mx, my)) {
            saveEdit();
        }
        if (PrideFrame.button(x + w / 2 + 2, by, w / 2 - 2, 24, "CANCEL", 0xFF8A3030, mx, my)) {
            editing = false;
            edit = null;
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(x + w - 4, y, h, editScroll, h, total);
    }

    private List<String[]> buildEditRows(FontRenderer fr) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Name", nameEditing ? nameBuf : (edit.name.isEmpty() ? "" : edit.name), "name"});
        rows.add(new String[]{"WHEN", edit.cond.label, "cond"});
        if (edit.cond.needsPos()) rows.add(new String[]{"Where", Rule.where(edit.condPos), "condpos"});
        if (edit.cond.needsChannel()) rows.add(new String[]{"Channel", String.valueOf(edit.condChannel), "condch"});
        if (edit.cond.needsRadius()) rows.add(new String[]{"Within", edit.radius + " blocks", "condrad"});

        rows.add(new String[]{"AND", edit.cond2 == null ? "none" : edit.cond2.label, "cond2"});
        if (edit.cond2 != null) {
            if (edit.cond2.needsPos()) rows.add(new String[]{"Where", Rule.where(edit.cond2Pos), "cond2pos"});
            if (edit.cond2.needsChannel()) rows.add(new String[]{"Channel", String.valueOf(edit.cond2Channel), "cond2ch"});
            if (edit.cond2.needsRadius()) rows.add(new String[]{"Within", edit.radius + " blocks", "cond2rad"});
        }

        rows.add(new String[]{"THEN", edit.act.label, "act"});
        if (edit.act.needsPos()) rows.add(new String[]{"Target", Rule.where(edit.actPos), "actpos"});
        if (edit.act.needsPos2()) rows.add(new String[]{"To signal", Rule.where(edit.actPos2), "actpos2"});
        if (edit.act.needsChannel()) rows.add(new String[]{"Channel", String.valueOf(edit.actChannel), "actch"});
        if (edit.act.needsText()) rows.add(new String[]{"Message", msgEditing ? msgBuf : edit.text, "msg"});

        rows.add(new String[]{"ELSE", edit.elseAct.label, "elseact"});
        if (edit.elseAct.needsPos()) rows.add(new String[]{"Target", Rule.where(edit.elsePos), "elsepos"});
        if (edit.elseAct.needsPos2()) rows.add(new String[]{"To signal", Rule.where(edit.elsePos2), "elsepos2"});
        if (edit.elseAct.needsChannel()) rows.add(new String[]{"Channel", String.valueOf(edit.elseChannel), "elsech"});
        if (edit.elseAct.needsText()) rows.add(new String[]{"Message", elseMsgEditing ? elseMsgBuf : edit.elseText, "elsemsg"});

        return rows;
    }

    private void handleEditRow(String kind, int idx, int dir) {
        switch (kind) {
            case "name":
                nameEditing = true;
                nameBuf = edit.name;
                break;
            case "cond":
                cycleCond(edit, dir, "cond");
                break;
            case "condpos":
                startPick(PickField.COND, "Click the place this condition watches");
                break;
            case "condch":
                edit.condChannel = wrapChannel(edit.condChannel + dir * (GuiScreen.isShiftKeyDown() ? 8 : 1));
                break;
            case "condrad":
                edit.radius = Math.max(4, Math.min(128, edit.radius + dir * 4));
                break;
            case "cond2":
                cycleCond2(edit, dir);
                break;
            case "cond2pos":
                startPick(PickField.COND2, "Click the place this AND condition watches");
                break;
            case "cond2ch":
                edit.cond2Channel = wrapChannel(edit.cond2Channel + dir * (GuiScreen.isShiftKeyDown() ? 8 : 1));
                break;
            case "cond2rad":
                edit.radius = Math.max(4, Math.min(128, edit.radius + dir * 4));
                break;
            case "act":
                cycleAct(edit, dir, "act");
                break;
            case "actpos":
                startPick(PickField.ACT, "Click the target for this action");
                break;
            case "actpos2":
                startPick(PickField.ACT2, "Click the destination signal for this route");
                break;
            case "actch":
                edit.actChannel = wrapChannel(edit.actChannel + dir * (GuiScreen.isShiftKeyDown() ? 8 : 1));
                break;
            case "msg":
                msgEditing = true;
                msgBuf = edit.text;
                break;
            case "elseact":
                cycleAct(edit, dir, "else");
                break;
            case "elsepos":
                startPick(PickField.ELSE, "Click the target for this else action");
                break;
            case "elsepos2":
                startPick(PickField.ELSE2, "Click the destination signal for this else route");
                break;
            case "elsech":
                edit.elseChannel = wrapChannel(edit.elseChannel + dir * (GuiScreen.isShiftKeyDown() ? 8 : 1));
                break;
            case "elsemsg":
                elseMsgEditing = true;
                elseMsgBuf = edit.elseText;
                break;
        }
    }

    private void cycleCond(Rule r, int dir, String which) {
        Rule.Cond[] vals = Rule.Cond.values();
        int i = r.cond.ordinal();
        i = (i + dir + vals.length) % vals.length;
        r.cond = vals[i];
    }

    private void cycleCond2(Rule r, int dir) {
        Rule.Cond[] vals = Rule.Cond.values();
        if (r.cond2 == null) {
            r.cond2 = vals[dir > 0 ? 0 : vals.length - 1];
        } else {
            int i = r.cond2.ordinal();
            i += dir;
            if (i < 0) r.cond2 = null;
            else if (i >= vals.length) r.cond2 = null;
            else r.cond2 = vals[i];
        }
    }

    private void cycleAct(Rule r, int dir, String which) {
        Rule.Act[] vals = Rule.Act.values();
        Rule.Act cur = which.equals("act") ? r.act : r.elseAct;
        int i = cur.ordinal();
        i = (i + dir + vals.length) % vals.length;
        Rule.Act next = vals[i];
        if (which.equals("act")) r.act = next; else r.elseAct = next;
    }

    private int wrapChannel(int c) {
        c = (c - 1) % 64;
        if (c < 0) c += 64;
        return c + 1;
    }

    private void startEdit(Rule src) {
        if (src == null) {
            edit = new Rule();
            edit.id = nextId();
            edit.name = "";
            edit.enabled = true;
            edit.cond = Rule.Cond.ALWAYS;
            edit.cond2 = null;
            edit.act = Rule.Act.NOTHING;
            edit.elseAct = Rule.Act.NOTHING;
            edit.condPos = 0; edit.cond2Pos = 0;
            edit.actPos = 0; edit.actPos2 = 0; edit.elsePos = 0; edit.elsePos2 = 0;
            edit.condChannel = 1; edit.cond2Channel = 1; edit.actChannel = 1; edit.elseChannel = 1;
            edit.radius = 16;
            edit.text = ""; edit.elseText = "";
        } else {
            edit = copyRule(src);
        }
        editing = true;
        editScroll = 0;
        nameEditing = false;
        msgEditing = false;
        elseMsgEditing = false;
    }

    private Rule copyRule(Rule r) {
        Rule c = new Rule();
        c.id = r.id; c.name = r.name; c.enabled = r.enabled;
        c.cond = r.cond; c.cond2 = r.cond2;
        c.condPos = r.condPos; c.cond2Pos = r.cond2Pos;
        c.condChannel = r.condChannel; c.cond2Channel = r.cond2Channel; c.radius = r.radius;
        c.act = r.act; c.elseAct = r.elseAct;
        c.actPos = r.actPos; c.actPos2 = r.actPos2; c.elsePos = r.elsePos; c.elsePos2 = r.elsePos2;
        c.actChannel = r.actChannel; c.elseChannel = r.elseChannel;
        c.text = r.text; c.elseText = r.elseText;
        return c;
    }

    private int nextId() {
        int max = 0;
        for (Rule r : data.rules) if (r.id > max) max = r.id;
        return max + 1;
    }

    private void saveEdit() {
        if (nameEditing) { edit.name = nameBuf; nameEditing = false; }
        if (msgEditing) { edit.text = msgBuf; msgEditing = false; }
        if (elseMsgEditing) { edit.elseText = elseMsgBuf; elseMsgEditing = false; }
        RailMap.NETWORK.sendToServer(PacketProgram.op(PacketProgram.SAVE_RULE, edit.write(), box));
        editing = false;
        edit = null;
    }

    private List<Rule> visibleRules() {
        if (focus < 0) return data.rules;
        List<Rule> out = new ArrayList<>();
        for (Rule r : data.rules) {
            if (r.condPos == focus || r.cond2Pos == focus || r.actPos == focus ||
                r.actPos2 == focus || r.elsePos == focus || r.elsePos2 == focus) {
                out.add(r);
            }
        }
        return out;
    }

    private void sendMove(int id, int dir) {
        net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
        t.setInteger("id", id);
        t.setInteger("dir", dir);
        RailMap.NETWORK.sendToServer(PacketProgram.op(PacketProgram.MOVE_RULE, t, box));
    }

    private void sendDelete(int id) {
        net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
        t.setInteger("id", id);
        RailMap.NETWORK.sendToServer(PacketProgram.op(PacketProgram.DELETE_RULE, t, box));
    }

    private void drawChannels(FontRenderer fr, int x, int y, int w, int cy, int ch) {
        int pad = 6;
        int ix = x + pad, iw = w - pad * 2;
        int cols = 4;
        int cellW = iw / cols;
        int cellH = 24;
        int rows = 64 / cols;
        int total = rows * cellH;
        int maxScroll = Math.max(0, total - ch);
        chScroll = Math.max(0, Math.min(chScroll, maxScroll));

        PrideFrame.clip(ix, cy, iw, ch);
        for (int i = 0; i < 64; i++) {
            int col = i % cols, row = i / cols;
            int cx = ix + col * cellW;
            int cyy = cy + row * cellH - chScroll;
            if (cyy + cellH < cy || cyy > cy + ch) continue;

            boolean on = i < data.channel.length && data.channel[i];
            String name = i < data.channelName.length ? data.channelName[i] : "";

            if (renameIdx == i) {
                Gui.drawRect(cx + 2, cyy + 2, cx + cellW - 2, cyy + cellH - 2, 0xFF100C18);
                Gui.drawRect(cx + 2, cyy + 2, cx + 4, cyy + cellH - 2, PrideFrame.PINK);
                String txt = renameBuf + (System.currentTimeMillis() / 400 % 2 == 0 ? "§7_" : "");
                fr.drawStringWithShadow(txt, cx + 8, cyy + 8, 0xFFFFFF);
            } else {
                boolean over = mx >= cx && my >= cyy && mx < cx + cellW && my < cyy + cellH;
                if (over) Gui.drawRect(cx + 1, cyy + 1, cx + cellW - 1, cyy + cellH - 1, PrideFrame.TILE_HOVER);
                // Lamp
                int lampX = cx + 4, lampY = cyy + 8, lampS = 8;
                Gui.drawRect(lampX, lampY, lampX + lampS, lampY + lampS, on ? 0xFF3FBF5F : 0xFF444444);
                // Label
                String label = (i + 1) + " " + name;
                if (fr.getStringWidth(label) > cellW - 18) {
                    while (label.length() > 0 && fr.getStringWidth(label) > cellW - 18) label = label.substring(0, label.length() - 1);
                }
                fr.drawStringWithShadow(label, lampX + lampS + 4, cyy + 8, on ? 0xFFFFFF : PrideFrame.DIM);
            }
        }
        PrideFrame.unclip();
        PrideFrame.scrollbar(ix + iw - 4, cy, ch, chScroll, ch, total);
    }

    private void drawAlarms(FontRenderer fr, int x, int y, int w, int cy, int ch) {
        int pad = 6;
        int ix = x + pad, iw = w - pad * 2;

        if (PrideFrame.button(ix, cy, iw, 20, "Clear", 0xFF8A3030, mx, my)) {
            net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
            RailMap.NETWORK.sendToServer(PacketProgram.op(PacketProgram.CLEAR_ALARMS, t, box));
        }

        int listY = cy + 24;
        int listH = ch - 24;
        int rowH = 16;
        int total = data.alarms.size() * rowH;
        int maxScroll = Math.max(0, total - listH);
        alarmScroll = Math.max(0, Math.min(alarmScroll, maxScroll));

        if (listH > 0) {
            PrideFrame.clip(ix, listY, iw, listH);
            List<String> alarms = data.alarms;
            for (int i = 0; i < alarms.size(); i++) {
                int ay = listY + i * rowH - alarmScroll;
                if (ay + rowH < listY || ay > listY + listH) continue;
                String a = alarms.get(i);
                if (fr.getStringWidth(a) > iw - 4) {
                    while (a.length() > 0 && fr.getStringWidth(a) > iw - 4) a = a.substring(0, a.length() - 1);
                }
                fr.drawStringWithShadow(a, ix + 2, ay + 2, 0xFFFF6B6B);
            }
            PrideFrame.unclip();
        }
        PrideFrame.scrollbar(ix + iw - 4, listY, listH, alarmScroll, listH, total);
    }

    public boolean click(int mx, int my, int button) {
        this.mx = mx;
        this.my = my;

        if (tab == 0 && !editing) {
            int pad = 6;
            int ix = x0() + pad, iw = w0() - pad * 2;
            int cy = y0() + 27;
            int listY = cy + 24;

            List<Rule> shown = visibleRules();
            int rowH = 22;
            int total = shown.size() * rowH;
            int maxScroll = Math.max(0, total - (h0() - 24));
            scroll = Math.max(0, Math.min(scroll, maxScroll));

            if (focus >= 0) {
                if (mx >= ix && my >= listY - 1 && mx < ix + iw && my < listY + 13) {
                    focus = -1;
                    return true;
                }
                listY += 16;
            }

            for (int i = 0; i < shown.size(); i++) {
                int ry = listY + i * rowH - scroll;
                if (my < ry || my >= ry + rowH) continue;
                if (mx < ix || mx >= ix + iw) continue;
                Rule r = shown.get(i);

                // Toggle box
                int togX = ix + 4, togY = ry + 5, togS = 12;
                if (mx >= togX && my >= togY && mx < togX + togS && my < togY + togS) {
                    net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
                    t.setInteger("id", r.id);
                    RailMap.NETWORK.sendToServer(PacketProgram.op(PacketProgram.TOGGLE_RULE, t, box));
                    return true;
                }

                // ▲ ▼ ✖
                int bx = ix + iw - 54;
                if (mx >= bx && my >= ry + 2 && mx < bx + 16 && my < ry + 20) {
                    sendMove(r.id, -1);
                    return true;
                }
                if (mx >= bx + 18 && my >= ry + 2 && mx < bx + 34 && my < ry + 20) {
                    sendMove(r.id, 1);
                    return true;
                }
                if (mx >= bx + 36 && my >= ry + 2 && mx < bx + 52 && my < ry + 20) {
                    sendDelete(r.id);
                    return true;
                }

                // Open edit
                startEdit(r);
                return true;
            }
        }

        if (tab == 0 && editing) {
            int pad = 6;
            int ix = x0() + pad, iw = w0() - pad * 2;
            int cy = y0() + 27;
            int rowH = 20;
            List<String[]> rows = buildEditRows(fr());
            int ry = cy - editScroll;
            for (int i = 0; i < rows.size(); i++) {
                String kind = rows.get(i)[2];
                if (my >= ry && my < ry + rowH && mx >= ix && mx < ix + iw) {
                    if (kind.equals("name") && nameEditing) { nameEditing = false; edit.name = nameBuf; return true; }
                    if (kind.equals("msg") && msgEditing) { msgEditing = false; edit.text = msgBuf; return true; }
                    if (kind.equals("elsemsg") && elseMsgEditing) { elseMsgEditing = false; edit.elseText = elseMsgBuf; return true; }
                    handleEditRow(kind, i, button == 1 ? -1 : 1);
                    return true;
                }
                ry += rowH;
            }
        }

        if (tab == 1) {
            int pad = 6;
            int ix = x0() + pad, iw = w0() - pad * 2;
            int cy = y0() + 27;
            int cols = 4;
            int cellW = iw / cols;
            int cellH = 24;
            for (int i = 0; i < 64; i++) {
                int col = i % cols, row = i / cols;
                int cx = ix + col * cellW;
                int cyy = cy + row * cellH - chScroll;
                if (my < cyy || my >= cyy + cellH) continue;
                if (mx < cx || mx >= cx + cellW) continue;
                if (renameIdx == i) {
                    renameIdx = -1;
                    return true;
                }
                if (button == 1) {
                    renameIdx = i;
                    renameBuf = i < data.channelName.length ? data.channelName[i] : "";
                    return true;
                }
                net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
                t.setInteger("n", i + 1);
                t.setBoolean("v", !(i < data.channel.length && data.channel[i]));
                RailMap.NETWORK.sendToServer(PacketProgram.op(PacketProgram.CHANNEL, t, box));
                return true;
            }
        }

        return false;
    }

    public void wheel(int dir) {
        if (tab == 0 && !editing) {
            int pad = 6;
            int iw = w0() - pad * 2;
            int cy = y0() + 27;
            int listY = cy + 24;
            int listH = h0() - 24;
            List<Rule> shown = visibleRules();
            int total = shown.size() * 22;
            int maxScroll = Math.max(0, total - listH);
            scroll = Math.max(0, Math.min(scroll - dir * 22, maxScroll));
        } else if (tab == 0 && editing) {
            int pad = 6;
            int iw = w0() - pad * 2;
            int cy = y0() + 27;
            int h = h0();
            List<String[]> rows = buildEditRows(fr());
            int total = rows.size() * 20 + 44;
            int maxScroll = Math.max(0, total - h);
            editScroll = Math.max(0, Math.min(editScroll - dir * 20, maxScroll));
        } else if (tab == 1) {
            int total = 16 * 24;
            int maxScroll = Math.max(0, total - h0());
            chScroll = Math.max(0, Math.min(chScroll - dir * 24, maxScroll));
        } else if (tab == 2) {
            int total = data.alarms.size() * 16;
            int maxScroll = Math.max(0, total - (h0() - 24));
            alarmScroll = Math.max(0, Math.min(alarmScroll - dir * 16, maxScroll));
        }
    }

    public boolean keyTyped(char c, int key) {
        if (nameEditing) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_ESCAPE) {
                if (key == Keyboard.KEY_RETURN) edit.name = nameBuf;
                nameEditing = false;
                return true;
            }
            if (key == Keyboard.KEY_BACK) {
                if (nameBuf.length() > 0) nameBuf = nameBuf.substring(0, nameBuf.length() - 1);
                return true;
            }
            if (c >= 32 && c < 127) {
                nameBuf += c;
                return true;
            }
            return true;
        }
        if (msgEditing) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_ESCAPE) {
                if (key == Keyboard.KEY_RETURN) edit.text = msgBuf;
                msgEditing = false;
                return true;
            }
            if (key == Keyboard.KEY_BACK) {
                if (msgBuf.length() > 0) msgBuf = msgBuf.substring(0, msgBuf.length() - 1);
                return true;
            }
            if (c >= 32 && c < 127) {
                msgBuf += c;
                return true;
            }
            return true;
        }
        if (elseMsgEditing) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_ESCAPE) {
                if (key == Keyboard.KEY_RETURN) edit.elseText = elseMsgBuf;
                elseMsgEditing = false;
                return true;
            }
            if (key == Keyboard.KEY_BACK) {
                if (elseMsgBuf.length() > 0) elseMsgBuf = elseMsgBuf.substring(0, elseMsgBuf.length() - 1);
                return true;
            }
            if (c >= 32 && c < 127) {
                elseMsgBuf += c;
                return true;
            }
            return true;
        }
        if (renameIdx >= 0) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_ESCAPE) {
                if (key == Keyboard.KEY_RETURN) {
                    net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
                    t.setInteger("n", renameIdx + 1);
                    t.setString("name", renameBuf);
                    RailMap.NETWORK.sendToServer(PacketProgram.op(PacketProgram.CHANNEL_NAME, t, box));
                }
                renameIdx = -1;
                return true;
            }
            if (key == Keyboard.KEY_BACK) {
                if (renameBuf.length() > 0) renameBuf = renameBuf.substring(0, renameBuf.length() - 1);
                return true;
            }
            if (c >= 32 && c < 127) {
                renameBuf += c;
                return true;
            }
            return true;
        }
        return false;
    }

    public boolean wantsPick() {
        return pickField != null;
    }

    public String pickHint() {
        return pickHint;
    }

    public void picked(BlockPos p) {
        if (pickField == null) return;
        long v = p.toLong();
        switch (pickField) {
            case COND: edit.condPos = v; break;
            case COND2: edit.cond2Pos = v; break;
            case ACT: edit.actPos = v; break;
            case ACT2: edit.actPos2 = v; break;
            case ELSE: edit.elsePos = v; break;
            case ELSE2: edit.elsePos2 = v; break;
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
        if (editing && edit != null) {
            if (edit.condPos != 0) out.add(edit.condPos);
            if (edit.cond2Pos != 0) out.add(edit.cond2Pos);
            if (edit.actPos != 0) out.add(edit.actPos);
            if (edit.actPos2 != 0) out.add(edit.actPos2);
            if (edit.elsePos != 0) out.add(edit.elsePos);
            if (edit.elsePos2 != 0) out.add(edit.elsePos2);
        }
        return out;
    }

    private void startPick(PickField f, String hint) {
        pickField = f;
        pickHint = hint;
    }

    // Cached panel geometry (set during draw)
    private int px, py, pw, ph;

    private int x0() { return px; }
    private int y0() { return py; }
    private int w0() { return pw; }
    private int h0() { return ph; }

    private FontRenderer fr() {
        return net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
    }
}
