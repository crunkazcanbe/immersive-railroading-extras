package com.dogpound.railmap.factory.machine;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/**
 * /irfactory form|unform|press|status <x y z> [control]: operate a plant from chat or a command block (ops).
 * /irfactory selftest: checks the old Pride Industry ids still resolve. The old /prideindustry name stays as an alias.
 */
public class CommandIndustry extends CommandBase {
    @Override public String getName() { return "irfactory"; }
    @Override public java.util.List<String> getAliases() { return java.util.Collections.singletonList("prideindustry"); }
    @Override public String getUsage(ICommandSender s) { return "/irfactory <form|unform|press|status> <x> <y> <z> [control]  |  /irfactory selftest"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] a) throws CommandException {
        if (a.length == 1 && a[0].equals("selftest")) {
            java.util.List<String> bad = com.dogpound.railmap.factory.Factory.selfTest();
            sender.sendMessage(new TextComponentString(bad.isEmpty() ? "\u00a7aFactory self-test PASS: every old prideindustry id resolves under irextras"
                    : "\u00a7cFactory self-test FAIL: " + String.join(", ", bad)));
            return;
        }
        if (a.length < 4) throw new WrongUsageException(getUsage(sender));
        BlockPos p = parseBlockPos(sender, a, 1, false);
        if (!(sender.getEntityWorld().getTileEntity(p) instanceof TileMachine m)) throw new CommandException("No factory controller at " + p);
        String r;
        switch (a[0]) {
            case "form": r = m.tryForm(); break;
            case "unform": r = m.unform("by command"); break;
            case "press":
                if (a.length < 5) throw new WrongUsageException(getUsage(sender));
                r = m.press(a[4], null); break;
            default: r = m.status();
        }
        sender.sendMessage(new TextComponentString(r));
    }
}
