package com.dogpound.railmap.interior.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.gui.PrideFrame;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.interior.PacketInterior;
import com.dogpound.railmap.interior.PacketInteriorState;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

import java.util.List;

/** The café till's menu: everything in the stock fridge, live, with its price; click a line to buy one (Pride coins). */
public class GuiCafeMenu extends net.minecraft.client.gui.GuiScreen {
    private final int entity;
    private final String car;
    private PrideFrame f;
    private int scroll;

    public GuiCafeMenu(int entity, String car) { this.entity = entity; this.car = car; }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        f = PrideFrame.sized(width, height, 520, 340);
        PacketInteriorState st = InteriorClient.state(entity);
        f.draw(this, "Café menu", car + (st != null && st.txt.containsKey("till") ? "  §7till " + st.txt.get("till") : ""));
        if (st == null) { fontRenderer.drawStringWithShadow("§7No signal from the till", f.cx, f.cy + 4, 0xFFFFFF); return; }
        if (!st.powered) { fontRenderer.drawStringWithShadow("§cThe till has no power (car power off)", f.cx, f.cy + 4, 0xFFFFFF); return; }
        List<Object[]> menu = st.menu;
        if (menu.isEmpty()) { fontRenderer.drawStringWithShadow("§7Sold out - the fridge is empty. Staff: stock it by clicking the fridge with food or drinks.", f.cx, f.cy + 4, 0xFFFFFF); return; }
        fontRenderer.drawStringWithShadow("§7Click a line to buy one. Paid from your Pride bank account; brewed drinks come from the espresso machine.", f.cx, f.cy + 2, 0xFFFFFF);
        int rh = 22, top = f.cy + 16, vis = (f.ch - 20) / rh;
        scroll = Math.max(0, Math.min(scroll, menu.size() - vis));
        for (int i = 0; i < Math.min(vis, menu.size() - scroll); i++) {
            Object[] m = menu.get(i + scroll);
            ItemStack s = (ItemStack) m[1];
            int y = top + i * rh;
            boolean over = mx >= f.cx && mx < f.cx + f.cw - 6 && my >= y && my < y + rh - 2;
            PrideFrame.tile(f.cx, y, f.cw - 6, rh - 2, 0xFFE0A040, over, false);
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(s, f.cx + 3, y + 2);
            RenderHelper.disableStandardItemLighting();
            String extra = s.getItem() instanceof ItemFood food ? "  §8+" + food.getHealAmount(s) + " food" : "";
            fontRenderer.drawStringWithShadow(s.getDisplayName() + extra, f.cx + 24, y + 6, 0xFFFFFF);
            String right = "§7" + s.getCount() + " left   §e" + Coins.fmt((Long) m[2]);
            fontRenderer.drawStringWithShadow(right, f.cx + f.cw - 14 - fontRenderer.getStringWidth(right), y + 6, 0xFFFFFF);
        }
        PrideFrame.scrollbar(f.cx + f.cw - 4, top, vis * rh, scroll, vis, menu.size());
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0) scroll += d > 0 ? -1 : 1;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws java.io.IOException {
        super.mouseClicked(mx, my, button);
        PacketInteriorState st = InteriorClient.state(entity);
        if (f == null || st == null || !st.powered) return;
        int rh = 22, top = f.cy + 16, vis = (f.ch - 20) / rh;
        for (int i = 0; i < Math.min(vis, st.menu.size() - scroll); i++) {
            int y = top + i * rh;
            if (mx >= f.cx && mx < f.cx + f.cw - 6 && my >= y && my < y + rh - 2) {
                RailMap.NETWORK.sendToServer(new PacketInterior(PacketInterior.BUY, entity, 0, -1, (Integer) st.menu.get(i + scroll)[0]));
                mc.player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 0.5f, 1.2f);
                return;
            }
        }
    }
}
