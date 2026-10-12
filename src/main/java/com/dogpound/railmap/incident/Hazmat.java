package com.dogpound.railmap.incident;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.mod.fluid.Fluid;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Dangerous goods (her ChatGPT list #69): what a car carries, as its placard (UN number + hazard class), and the
 * response rules for it. Tank cars read their fluid, box cars their cargo. Fuel, chemicals and gas each get their own
 * rules - cordon size, what to fight it with, whether to evacuate, how long the clean-up takes before the line reopens.
 * Response rules only: nothing here sets fire to or blows up anything (the IR fork's own wreck spills do what they do).
 */
public final class Hazmat {
    private Hazmat() {}

    /** 0 none, 1 flammable liquid, 2 chemical (corrosive / toxic), 3 gas, 4 radioactive, 5 explosive, 6 other (class 4 / 9) */
    public static final String[] KIND = {"ORDINARY", "FLAMMABLE", "CHEMICAL", "GAS", "RADIOACTIVE", "EXPLOSIVE", "OTHER DG"};
    /** cordon radius (blocks) by kind */
    public static final int[] CORDON = {0, 32, 64, 96, 64, 128, 24};
    public static final String[] RULE = {
            "standard response: fire service, recovery crew",
            "FOAM, no water jets; isolate traction power; 32 m cordon; no ignition sources",
            "HAZMAT team in gas-tight suits; 64 m cordon; evacuate downwind; contain run-off, no water",
            "isolate power, no ignition sources; 96 m cordon; evacuate; let it vent unless leak can be stopped",
            "specialist team, monitor dose; 64 m cordon; evacuate; nobody eats or drinks on site",
            "do NOT fight a fire reaching the load; 128 m cordon; evacuate everyone",
            "keep dry / cool; 24 m cordon; specialist advice"};

    public static final class Placard {
        public int kind;
        public String un = "", cls = "", name = "";
        public String line() { return kind == 0 ? "" : "UN" + un + " CL " + cls + " " + name; }
    }

    public static Placard of(EntityRollingStock s) {
        Placard p = new Placard();
        try {
            if (s instanceof FreightTank t && t.getLiquidAmount() > 0) {
                Fluid f = t.getLiquid();
                if (f != null) fluid(p, f.ident == null ? "" : f.ident.toLowerCase(Locale.ROOT));
            } else if (s instanceof Freight f && f.cargoItems != null) {
                for (int i = 0; i < f.cargoItems.getSlotCount(); i++) {
                    ItemStack st = f.cargoItems.get(i).internal;
                    if (st == null || st.isEmpty() || st.getItem().getRegistryName() == null) continue;
                    Placard q = new Placard();
                    item(q, st.getItem().getRegistryName().getPath().toLowerCase(Locale.ROOT));
                    if (worse(q.kind, p.kind)) p = q;
                }
            }
        } catch (RuntimeException | LinkageError ignored) { }
        return p;
    }

    /** ranks: explosive > radioactive > gas > chemical > flammable > other > none */
    public static boolean worse(int a, int b) { return rank(a) > rank(b); }
    private static int rank(int k) { return new int[]{0, 3, 4, 5, 6, 7, 2}[k]; }

    static void fluid(Placard p, String id) {
        if (has(id, "plantoil", "plant_oil", "seedoil", "seed_oil", "vegetable", "honey", "milk", "water")) return;    // not dangerous goods
        if (has(id, "gasoline", "petrol")) set(p, 1, "1203", "3", "GASOLINE");
        else if (has(id, "diesel", "biodiesel")) set(p, 1, "1202", "3", "DIESEL");
        else if (has(id, "crude", "oil")) set(p, 1, "1267", "3", "CRUDE OIL");
        else if (has(id, "ethanol", "alcohol", "bioethanol")) set(p, 1, "1170", "3", "ETHANOL");
        else if (has(id, "kerosene", "jet", "fuel", "naphtha", "creosote", "benzene", "lubricant")) set(p, 1, "1993", "3", "FLAMMABLE LIQ");
        else if (has(id, "sulfuric", "sulphuric")) set(p, 2, "1830", "8", "SULFURIC ACID");
        else if (has(id, "hydrochloric")) set(p, 2, "1789", "8", "HYDROCHLORIC ACID");
        else if (has(id, "acid")) set(p, 2, "1760", "8", "CORROSIVE LIQ");
        else if (has(id, "ammonia")) set(p, 3, "1005", "2.3", "AMMONIA");
        else if (has(id, "chlorine")) set(p, 3, "1017", "2.3", "CHLORINE");
        else if (has(id, "lye", "caustic", "sodium_hydroxide", "bleach", "toxic", "poison", "mercury", "chemical")) set(p, 2, "2922", "8", "TOXIC CORROSIVE");
        else if (has(id, "propane", "lpg", "butane")) set(p, 3, "1075", "2.1", "LPG");
        else if (has(id, "hydrogen")) set(p, 3, "1049", "2.1", "HYDROGEN");
        else if (has(id, "methane", "natural_gas", "naturalgas", "biogas")) set(p, 3, "1971", "2.1", "NATURAL GAS");
        else if (has(id, "oxygen")) set(p, 3, "1072", "2.2", "OXYGEN");
        else if (has(id, "uranium", "plutonium", "radioactive", "nuclear", "waste")) set(p, 4, "2912", "7", "RADIOACTIVE");
        else if (has(id, "lava", "molten", "pyrotheum")) set(p, 6, "3257", "9", "ELEVATED TEMP");
    }

    static void item(Placard p, String id) {
        if (has(id, "tnt", "gunpowder", "dynamite", "explosive", "nitro", "c4")) set(p, 5, "0081", "1.1D", "EXPLOSIVE");
        else if (has(id, "uranium", "plutonium", "radioactive", "yellowcake", "thorium")) set(p, 4, "2912", "7", "RADIOACTIVE");
        else if (has(id, "coal", "charcoal", "coke")) set(p, 6, "1361", "4.2", "COAL");
        else if (has(id, "sulfur", "sulphur")) set(p, 6, "1350", "4.1", "SULFUR");
        else if (has(id, "blaze", "fire_charge", "firework")) set(p, 5, "0336", "1.4G", "FIREWORKS");
    }

    private static boolean has(String id, String... w) {
        for (String s : w) if (id.contains(s)) return true;
        return false;
    }

    private static void set(Placard p, int k, String un, String cls, String name) { p.kind = k; p.un = un; p.cls = cls; p.name = name; }

    /** clean-up seconds before the line reopens, by kind */
    public static int cleanup(int kind) {
        int[] c = IncidentConfig.cleanupSeconds;
        int i = kind == 0 ? 0 : kind == 1 ? 1 : kind == 2 ? 2 : kind == 3 ? 3 : kind == 4 ? 4 : kind == 5 ? 4 : 0;
        return c == null || i >= c.length ? 60 : Math.max(0, c[i]);
    }

    /** self-check (/irextras incident selftest): placards for known cargo; returns failures */
    public static List<String> selfTest() {
        List<String> bad = new java.util.ArrayList<>();
        String[][] fl = {{"diesel", "1202"}, {"gasoline", "1203"}, {"oil", "1267"}, {"sulfuricacid", "1830"}, {"ammonia", "1005"}, {"lpg", "1075"}, {"water", ""}, {"milk", ""}};
        for (String[] c : fl) { Placard p = new Placard(); fluid(p, c[0]); if (!p.un.equals(c[1])) bad.add("fluid " + c[0] + " -> '" + p.un + "' want '" + c[1] + "'"); }
        String[][] it = {{"tnt", "0081"}, {"coal", "1361"}, {"iron_ingot", ""}};
        for (String[] c : it) { Placard p = new Placard(); item(p, c[0]); if (!p.un.equals(c[1])) bad.add("item " + c[0] + " -> '" + p.un + "' want '" + c[1] + "'"); }
        if (!worse(5, 1) || worse(0, 6) || !worse(3, 2)) bad.add("ranking");
        return bad;
    }

    public static boolean evacuates(int kind) { return kind == 2 || kind == 3 || kind == 4 || kind == 5; }
}
