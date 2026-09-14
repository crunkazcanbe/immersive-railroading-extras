package com.dogpound.railmap.client;

import com.dogpound.railmap.client.gui.GuiDispatcherBoard;
import com.dogpound.railmap.graph.RailNetwork;
import net.minecraft.client.Minecraft;

/** Client-side holder for the handheld Rail Map's last network; opens/refreshes the GUI on arrival. */
public final class HandheldMap {
    private static RailNetwork network = RailNetwork.EMPTY;

    private HandheldMap() {}

    public static RailNetwork network() {
        return network;
    }

    public static void accept(RailNetwork net) {
        network = net;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiDispatcherBoard g && g.isHandheld()) {
            g.networkChanged();
        } else if (mc.currentScreen == null) {
            mc.displayGuiScreen(new GuiDispatcherBoard(null));
        }
    }
}
