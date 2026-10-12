package com.dogpound.railmap.safety;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/** {@code /irextras sig ...}: signalling batch 3 from chat (testing + inspection). Each part answers its own sub-commands. */
public final class SigCommand {
    private SigCommand() {}

    public static String usage() {
        StringBuilder b = new StringBuilder();
        for (String u : new String[]{SigA.usage(), SigB.usage(), SigC.usage(), SigM.usage()}) if (!u.isEmpty()) b.append(b.length() > 0 ? "|" : "").append(u);
        return "sig <" + b + ">";
    }

    public static void run(ICommandSender s, World w, String[] a) {
        if (a.length > 1 && (SigA.command(s, w, a) || SigB.command(s, w, a) || SigC.command(s, w, a) || SigM.command(s, w, a))) return;
        for (String u : new String[]{SigA.usage(), SigB.usage(), SigC.usage(), SigM.usage()})
            if (!u.isEmpty()) s.sendMessage(new TextComponentString("§7/irextras sig " + u));
    }
}
