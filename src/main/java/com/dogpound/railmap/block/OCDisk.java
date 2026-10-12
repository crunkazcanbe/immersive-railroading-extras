package com.dogpound.railmap.block;

import net.minecraft.item.EnumDyeColor;

/**
 * The "Pride Control" OpenComputers loot disk: the IR Extras dashboard program (map, trains, power, alarms).
 * Only ever touched when OpenComputers is installed. In game: put it in a disk drive, run `install`, then `pridectl`.
 */
public final class OCDisk {
    private OCDisk() {}

    public static void register() {
        li.cil.oc.api.Items.registerFloppy("Pride Control", EnumDyeColor.PINK,
                () -> li.cil.oc.api.FileSystem.fromClass(OCDisk.class, "irextras", "opencomputers/pridectl"), true);
    }
}
