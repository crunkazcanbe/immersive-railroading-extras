package com.dogpound.railmap.industry;

/**
 * What each industry takes in and turns out (her Trainz/Run8 list: "industries with supply chains").
 * Goods are plain Minecraft items so any mod's pipes and every IR freight car can carry them.
 * Chains: Coal Mine -> Power Station / Steel Mill, Iron Mine -> Steel Mill, Logging Camp -> Sawmill,
 * Farm -> Flour Mill, Quarry -> Cement Works, and everything finished -> Town Market.
 */
public enum IndustryKind {
    COAL_MINE("coal_mine", "Digs coal for power stations and steel mills", 30, 0, 0,
            new String[0], new String[]{"minecraft:coal:0:6"}),
    IRON_MINE("iron_mine", "Iron ore for the steel mill", 30, 0, 0,
            new String[0], new String[]{"minecraft:iron_ore:0:4"}),
    QUARRY("quarry", "Crushed stone for the cement works", 30, 0, 0,
            new String[0], new String[]{"minecraft:gravel:0:6", "minecraft:cobblestone:0:6"}),
    LOGGING_CAMP("logging_camp", "Timber for the sawmill", 30, 0, 0,
            new String[0], new String[]{"minecraft:log:0:6"}),
    FARM("farm", "Grain for the flour mill (grows faster in daylight)", 30, 0, 0,
            new String[0], new String[]{"minecraft:wheat:0:9"}),
    SAWMILL("sawmill", "Logs in, planks out. Needs power", 20, 4000, 0,
            new String[]{"minecraft:log:0:3"}, new String[]{"minecraft:planks:0:12"}),
    STEEL_MILL("steel_mill", "Iron ore + coal in, iron ingots out. Needs power", 20, 8000, 0,
            new String[]{"minecraft:iron_ore:0:2", "minecraft:coal:0:1"}, new String[]{"minecraft:iron_ingot:0:2"}),
    FLOUR_MILL("flour_mill", "Wheat in, bread out. Needs power", 20, 2000, 0,
            new String[]{"minecraft:wheat:0:3"}, new String[]{"minecraft:bread:0:1"}),
    CEMENT_WORKS("cement_works", "Gravel in, concrete powder out. Needs power", 20, 3000, 0,
            new String[]{"minecraft:gravel:0:4"}, new String[]{"minecraft:concrete_powder:8:4"}),
    POWER_STATION("power_station", "Burns coal delivered by train; pushes power into the grid or any mod's cables", 10, 0, 40000,
            new String[]{"minecraft:coal:0:1"}, new String[0]),
    TOWN_MARKET("town_market", "Buys bread, planks, iron and concrete. A well-supplied town grows", 30, 0, 0,
            new String[]{"minecraft:bread:0:2", "minecraft:planks:0:4", "minecraft:iron_ingot:0:1", "minecraft:concrete_powder:8:2"}, new String[0]);

    public final String id, tip;
    /** seconds per production cycle */
    public final int cycle;
    /** FE a factory needs per cycle (0 = none) */
    public final int needsFE;
    /** FE a power station makes per cycle */
    public final int makesFE;
    public final String[] in, out;

    IndustryKind(String id, String tip, int cycle, int needsFE, int makesFE, String[] in, String[] out) {
        this.id = id; this.tip = tip; this.cycle = cycle; this.needsFE = needsFE; this.makesFE = makesFE; this.in = in; this.out = out;
    }

    public boolean producer() { return in.length == 0; }
    /** the town takes whichever goods it is offered, not all of them at once */
    public boolean anyInput() { return this == TOWN_MARKET; }
}
