package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.network.PacketTicketBuy;
import com.dogpound.railmap.network.PacketTicketMenu;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * The ticket machine's touch screen. Drawn by hand rather than with vanilla buttons, so it
 * reads as a kiosk: a steel bezel, a transit-blue screen, big touch tiles for destinations,
 * One Way / Round Trip, the fare, what you have, and a green PRINT TICKET.
 */
public class GuiTicketMachine extends GuiScreen {
    private static final int W = 340, H = 220;
    private static final int STEEL = 0xFF3A3F46, STEEL_HI = 0xFF5A6068, STEEL_LO = 0xFF22262B;
    private static final int SCREEN_TOP = 0xFF0D3B78, SCREEN_BOT = 0xFF06214A;
    private static final int TILE = 0xFF11509C, TILE_HOVER = 0xFF1A66C2, TILE_SEL = 0xFFFFC23A;
    private static final int WHITE = 0xFFF5F7FA, DIM = 0xFF9DB6D8, GOLD = 0xFFFFC23A;
    private static final int GO = 0xFF1FA85A, GO_HI = 0xFF27C76B, NO = 0xFF5B6470, RED = 0xFFFF6B5E;
    private static final int ROW_H = 20, ROWS = 7;

    private PacketTicketMenu menu;
    private int selected = -1;
    private boolean round;
    private int scroll;
    private int left, top;

    public GuiTicketMachine(PacketTicketMenu menu) {
        this.menu = menu;
    }

    /** A fresh menu from the server (after buying): keep the selection if it still exists. */
    public void update(PacketTicketMenu m) {
        long sel = selected >= 0 && selected < menu.dests.size() ? menu.dests.get(selected).key : Long.MIN_VALUE;
        menu = m;
        selected = -1;
        for (int i = 0; i < m.dests.size(); i++) if (m.dests.get(i).key == sel) selected = i;
    }

    @Override
    public void initGui() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        drawDefaultBackground();
        // Bezel with a bevel, then the glass.
        drawRect(left - 8, top - 8, left + W + 8, top + H + 8, STEEL_LO);
        drawRect(left - 7, top - 7, left + W + 7, top + H + 7, STEEL);
        drawRect(left - 7, top - 7, left + W + 7, top - 5, STEEL_HI);
        drawRect(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF000000);
        drawGradientRect(left, top, left + W, top + H, SCREEN_TOP, SCREEN_BOT);

        // Header.
        drawRect(left, top, left + W, top + 18, 0xFF072A5C);
        drawRect(left, top + 18, left + W, top + 19, GOLD);
        fontRenderer.drawString("RAIL TICKETS", left + 6, top + 5, WHITE);
        String from = menu.station.isEmpty() ? "NOT AT A STATION" : "FROM  " + menu.station.toUpperCase();
        fontRenderer.drawString(from, left + W - fontRenderer.getStringWidth(from) - 6, top + 5, GOLD);

        if (menu.station.isEmpty()) {
            center("Name a station near this machine on the Dispatcher Board.", top + 90, WHITE);
            center("Click a piece of track on the map, type a name, Save.", top + 104, DIM);
            super.drawScreen(mx, my, partialTicks);
            return;
        }

        // Destination list.
        int lx = left + 6, ly = top + 26, lw = 176;
        fontRenderer.drawString("CHOOSE YOUR DESTINATION", lx, ly, DIM);
        ly += 11;
        if (menu.dests.isEmpty()) {
            fontRenderer.drawString("No other stations yet", lx, ly + 6, WHITE);
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = i + scroll;
            if (idx >= menu.dests.size()) break;
            PacketTicketMenu.Dest d = menu.dests.get(idx);
            int y = ly + i * (ROW_H + 2);
            boolean hover = in(mx, my, lx, y, lw, ROW_H);
            boolean sel = idx == selected;
            drawRect(lx, y, lx + lw, y + ROW_H, sel ? TILE_SEL : hover ? TILE_HOVER : TILE);
            int text = sel ? 0xFF1B1300 : WHITE;
            fontRenderer.drawString(trim(d.name, lw - 50), lx + 5, y + 3, text);
            fontRenderer.drawString(Math.round(d.distance) + " m", lx + 5, y + 11, sel ? 0xFF4A3A10 : DIM);
            String fare = d.oneWay == 0 ? "FREE" : String.valueOf(d.oneWay);
            fontRenderer.drawString(fare, lx + lw - fontRenderer.getStringWidth(fare) - 5, y + 6, sel ? 0xFF1B1300 : GOLD);
        }
        if (menu.dests.size() > ROWS) {
            int track = ROWS * (ROW_H + 2) - 2;
            int knob = Math.max(12, track * ROWS / menu.dests.size());
            int pos = (track - knob) * scroll / Math.max(1, menu.dests.size() - ROWS);
            drawRect(lx + lw + 2, ly, lx + lw + 5, ly + track, 0xFF0A2346);
            drawRect(lx + lw + 2, ly + pos, lx + lw + 5, ly + pos + knob, DIM);
        }

        // Right panel: the trip.
        int px = left + 192, pw = W - 198, py = top + 26;
        fontRenderer.drawString("YOUR TRIP", px, py, DIM);
        py += 12;
        PacketTicketMenu.Dest d = selected >= 0 && selected < menu.dests.size() ? menu.dests.get(selected) : null;
        fontRenderer.drawString(trim(menu.station, pw), px, py, WHITE);
        fontRenderer.drawString("to", px + 2, py + 10, GOLD);
        fontRenderer.drawString(d == null ? "— pick a station —" : trim(d.name, pw), px, py + 20, d == null ? DIM : WHITE);
        py += 36;

        toggle(px, py, pw / 2 - 2, "ONE WAY", !round, mx, my);
        toggle(px + pw / 2 + 2, py, pw / 2 - 2, "ROUND TRIP", round, mx, my);
        py += 24;

        int price = d == null ? 0 : round ? d.round : d.oneWay;
        boolean free = menu.fareItem.isEmpty();
        String fareLine = d == null ? "" : free ? "FARE: FREE" : "FARE: " + price + " " + menu.fareItem;
        fontRenderer.drawString(trim(fareLine, pw), px, py, GOLD);
        if (!free) fontRenderer.drawString(trim("YOU HAVE: " + menu.wallet, pw), px, py + 10, DIM);
        py += 26;

        boolean can = d != null && (free || menu.wallet >= price);
        boolean hover = can && in(mx, my, px, py, pw, 26);
        drawRect(px, py, px + pw, py + 26, can ? hover ? GO_HI : GO : NO);
        drawRect(px, py + 24, px + pw, py + 26, 0x40000000);
        String print = d != null && !can ? "NOT ENOUGH" : "PRINT TICKET";
        fontRenderer.drawString(print, px + (pw - fontRenderer.getStringWidth(print)) / 2, py + 9, WHITE);

        // Footer: status, waiting, how to ride.
        int fy = top + H - 30;
        drawRect(left, fy - 3, left + W, fy - 2, 0x3300A0FF);
        String msg = menu.message.isEmpty()
                ? "To ride: hold your ticket and right-click this machine."
                : menu.message;
        int msgColor = menu.message.startsWith("Not") || menu.message.startsWith("Pick") ? RED : WHITE;
        fontRenderer.drawString(trim(msg, W - 12), left + 6, fy + 2, msgColor);
        String info = menu.waiting + " waiting here · " + menu.trains + " driverless train" + (menu.trains == 1 ? "" : "s");
        fontRenderer.drawString(info, left + 6, fy + 14, DIM);

        super.drawScreen(mx, my, partialTicks);
    }

    private void toggle(int x, int y, int w, String label, boolean on, int mx, int my) {
        boolean hover = in(mx, my, x, y, w, 18);
        drawRect(x, y, x + w, y + 18, on ? GOLD : hover ? TILE_HOVER : TILE);
        int c = on ? 0xFF1B1300 : WHITE;
        fontRenderer.drawString(label, x + (w - fontRenderer.getStringWidth(label)) / 2, y + 5, c);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (button != 0 || menu.station.isEmpty()) return;
        int lx = left + 6, ly = top + 37, lw = 176;
        for (int i = 0; i < ROWS; i++) {
            int idx = i + scroll;
            if (idx >= menu.dests.size()) break;
            if (in(mx, my, lx, ly + i * (ROW_H + 2), lw, ROW_H)) {
                selected = idx;
                click();
                return;
            }
        }
        int px = left + 192, pw = W - 198;
        int ty = top + 26 + 12 + 36;
        if (in(mx, my, px, ty, pw / 2 - 2, 18)) { round = false; click(); return; }
        if (in(mx, my, px + pw / 2 + 2, ty, pw / 2 - 2, 18)) { round = true; click(); return; }
        int by = ty + 24 + 26;
        if (in(mx, my, px, by, pw, 26) && selected >= 0 && selected < menu.dests.size()) {
            RailMap.NETWORK.sendToServer(new PacketTicketBuy(menu.machine, menu.dests.get(selected).key, round));
            click();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int max = Math.max(0, menu.dests.size() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll + (wheel > 0 ? -1 : 1)));
    }

    private void click() {
        mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.4f));
    }

    private void center(String s, int y, int color) {
        fontRenderer.drawString(s, left + (W - fontRenderer.getStringWidth(s)) / 2, y, color);
    }

    private String trim(String s, int w) {
        if (fontRenderer.getStringWidth(s) <= w) return s;
        return fontRenderer.trimStringToWidth(s, w - fontRenderer.getStringWidth("…")) + "…";
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
