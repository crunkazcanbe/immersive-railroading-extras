package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.grid.PacketCableSpec;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumHand;

import java.util.Locale;

/** Choose the conductor a Power Cable stack lays: material, cross-section, cores, insulation (live ratings shown). */
public class GuiCableSpec extends GuiScreen {
    private final EnumHand hand;
    private int mat, size, cores, ins;

    public GuiCableSpec(EnumHand hand, Elec.Spec s) {
        this.hand = hand;
        mat = s.mat.ordinal(); cores = s.cores.ordinal(); ins = s.ins.ordinal();
        for (int i = 0; i < Elec.SIZES.length; i++) if (Elec.SIZES[i] == s.mm2) size = i;
    }

    private Elec.Spec spec() {
        return new Elec.Spec(Elec.Material.values()[mat], Elec.SIZES[size], Elec.Cores.values()[cores], Elec.Insulation.values()[ins]);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int x = width / 2 - 110, y = height / 2 - 60;
        buttonList.add(new GuiButton(0, x, y, 220, 20, ""));
        buttonList.add(new GuiButton(1, x, y + 24, 220, 20, ""));
        buttonList.add(new GuiButton(2, x, y + 48, 220, 20, ""));
        buttonList.add(new GuiButton(3, x, y + 72, 220, 20, ""));
        buttonList.add(new GuiButton(4, x + 60, y + 104, 100, 20, "Done"));
        labels();
    }

    private void labels() {
        Elec.Spec s = spec();
        buttonList.get(0).displayString = "Material: " + s.mat.label;
        buttonList.get(1).displayString = "Size: " + Elec.SIZES[size] + " mm²  (click: bigger, shift: smaller)";
        buttonList.get(2).displayString = "Cores: " + s.cores.label;
        buttonList.get(3).displayString = "Insulation: " + s.ins.label;
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        boolean back = isShiftKeyDown();
        switch (b.id) {
            case 0: mat = (mat + (back ? Elec.Material.values().length - 1 : 1)) % Elec.Material.values().length; break;
            case 1: size = (size + (back ? Elec.SIZES.length - 1 : 1)) % Elec.SIZES.length; break;
            case 2: cores = (cores + (back ? Elec.Cores.values().length - 1 : 1)) % Elec.Cores.values().length; break;
            case 3: ins = (ins + (back ? Elec.Insulation.values().length - 1 : 1)) % Elec.Insulation.values().length; break;
            case 4: RailMap.NETWORK.sendToServer(new PacketCableSpec(hand, spec())); mc.displayGuiScreen(null); return;
        }
        labels();
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        Elec.Spec s = spec();
        int y = height / 2 - 96;
        drawCenteredString(fontRenderer, "§lPower Cable - choose the conductor", width / 2, y, 0xF5A9B8);
        drawCenteredString(fontRenderer, String.format(Locale.ROOT, "Rated §e%.0f A§r · §e%.3f mΩ/m§r · insulation to §e%s§r · max §e%d °C",
                s.ampacity(), s.ohmsPerM(20) * 1000, Elec.si(s.ins.maxVolts, "V"), s.ins.maxTemp), width / 2, y + 14, 0xDDDDDD);
        super.drawScreen(mx, my, pt);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
