package com.dogpound.railmap.economy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

/**
 * The economy's permission nodes, asked through Forge's PermissionAPI - which PridePerms answers (her list #46:
 * "integrate with your permissions mod rather than creating a competing permission system"). Company roles still
 * decide who may do what inside a company; these decide who may use the economy at all on a server.
 */
public final class Perms {
    private Perms() {}

    public static final String FOUND = "irextras.economy.company.found", MANAGE = "irextras.economy.company.manage",
            TRADE = "irextras.economy.exchange.trade", CRANE = "irextras.economy.terminal.operate", ADMIN = "irextras.economy.admin";
    private static boolean registered;

    private static void register() {
        if (registered) return;
        registered = true;
        PermissionAPI.registerNode(FOUND, DefaultPermissionLevel.ALL, "Found a railway company (Company HQ / /irextras company create)");
        PermissionAPI.registerNode(MANAGE, DefaultPermissionLevel.ALL, "Work a railway company's books at a Company HQ (roles still apply)");
        PermissionAPI.registerNode(TRADE, DefaultPermissionLevel.ALL, "Buy and sell at a Commodity Exchange");
        PermissionAPI.registerNode(CRANE, DefaultPermissionLevel.ALL, "Operate a Container Terminal's crane and gate");
        PermissionAPI.registerNode(ADMIN, DefaultPermissionLevel.OP, "Set the fare scheme and commodity prices, manage any company");
    }

    /** null player (console / remote button) = allowed; the machine's own rules decide */
    public static boolean has(EntityPlayer p, String node) {
        if (p == null) return true;
        register();
        try { return PermissionAPI.hasPermission(p, node); } catch (RuntimeException e) { return true; }
    }

    public static String no(String what) { return "§cYou don't have permission to " + what + " on this server"; }
}
