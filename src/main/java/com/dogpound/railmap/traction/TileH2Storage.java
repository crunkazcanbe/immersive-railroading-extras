package com.dogpound.railmap.traction;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;

/**
 * Hydrogen Storage Tubes (batch 5 #6): a rack of three cascade banks of composite tubes - LOW 200 bar, MEDIUM 350 bar,
 * HIGH 500 bar, 60 kg each. The compressor fills HIGH first, then MEDIUM, then LOW (priority panel); the dispenser draws
 * LOW first and steps up the cascade as the train's tank pressure rises. Each bank has its own pressure gauge and a hand
 * isolation valve (works without power); the gas detector and the ESD solenoid valves need 12 V - no 12 V = the
 * solenoids fail SHUT (nothing flows). Leaks: a bank over 95 % has a small chance of a leaking fitting each minute.
 */
public class TileH2Storage extends com.dogpound.railmap.depot.DepotTile implements H2Station.Part {
    static final String[] BANK = {"LOW", "MED", "HIGH"};
    static final float[] RATED = {200, 350, 500};
    static final float CAP = 60;
    private final float[] kg = new float[3];
    private boolean esd;
    private float lel, leak;

    public TileH2Storage() { super(new String[]{"cert"}, new String[]{"Tube inspection certificates"}, new int[]{9}); }

    @Override public String wiredName() { return "Hydrogen storage"; }
    @Override public String gearState() { return !powered() ? "NO 12 V - solenoids shut" : String.format(Locale.ROOT, "L %.0f / M %.0f / H %.0f bar · %.0f kg%s", bar(0), bar(1), bar(2), total(), esd ? " · ESD" : ""); }

    public float bar(int b) { return RATED[b] * kg[b] / CAP; }
    public float total() { return kg[0] + kg[1] + kg[2]; }
    boolean open(int b) { return powered() && !esd && ctl.get("m_v" + (b + 1)) == 0; }    // hand valves: 0 = open (as delivered)

    /** the compressor: priority fill HIGH -> MED -> LOW; returns kg taken */
    public float fill(float in) {
        float took = 0;
        for (int b = 2; b >= 0 && in > 0; b--) { if (!open(b)) continue; float t = Math.min(in, CAP - kg[b]); kg[b] += t; in -= t; took += t; }
        if (took > 0) markDirty();
        return took;
    }

    /** the dispenser: cascade from LOW up, only from a bank above the train's tank pressure; returns kg given */
    public float draw(float want, float tankBar) {
        float got = 0;
        for (int b = 0; b < 3 && want > 0; b++) {
            if (!open(b) || bar(b) <= tankBar + 20) continue;
            float t = Math.min(want, kg[b] - CAP * (tankBar + 20) / RATED[b]);
            if (t <= 0) continue;
            kg[b] -= t; want -= t; got += t;
        }
        if (got > 0) markDirty();
        return got;
    }
    public float highest() { float m = 0; for (int b = 0; b < 3; b++) if (open(b)) m = Math.max(m, bar(b)); return m; }
    /** which bank the dispenser is on now (-1 none) */
    public int bankFor(float tankBar) { for (int b = 0; b < 3; b++) if (open(b) && bar(b) > tankBar + 20) return b; return -1; }

    @Override public void esdTrip(String why) { esd = true; controlsChanged(); }
    @Override public boolean esd() { return esd; }
    @Override public float lel() { return lel; }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "reset" -> {
                if (lel >= 10) return String.format(Locale.ROOT, "§cGas still %.0f %% LEL - find the leak first", lel);
                esd = false; H2Station.reset(world, pos);
                return "§aESD reset - bank solenoids open";
            }
            case "fix" -> {
                if (leak <= 0) return "§7No leak on the bank fittings";
                if (who == null || !(who.getHeldItemMainhand().getItem() instanceof com.dogpound.railmap.grid.ItemScrewdriver)) return "§7Hold a screwdriver (the fitting spanner) to remake the leaking joint";
                leak = 0; return "§aLeaking fitting remade - wait for the gas to clear, then RESET";
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { if (ctl.get(id) == 0) { ctl.set(id, 1); H2Station.esd(world, pos, "E-STOP at the storage"); } else ctl.set(id, 0); return ctl.get(id) == 1 ? "§cESD - station shut down" : "§aE-stop released (RESET each part)"; }
        if (id.startsWith("m_v")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); return "§e" + BANK[id.charAt(3) - '1'] + " bank hand valve " + (ctl.get(id) == 1 ? "SHUT" : "OPEN"); }
        return null;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        for (int b = 0; b < 3; b++) if (kg[b] > CAP * 0.95f && leak == 0 && world.rand.nextInt(6000) == 0) {
            leak = 0.5f + world.rand.nextFloat();
            com.dogpound.railmap.alarm.Recorder.event(world, "Hydrogen storage: fitting leak on the " + BANK[b] + " bank");
        }
        if (leak > 0) for (int b = 0; b < 3; b++) kg[b] = Math.max(0, kg[b] - 0.002f * leak);
        lel = Math.max(0, lel + leak * 2.5f - lel * 0.08f);
        if (powered()) H2Station.detector(world, pos, "storage", lel);
        put("pwr", powered()); put("esdl", esd); put("leakl", lel >= 20); put("gas", lel >= 5);
        for (int b = 0; b < 3; b++) { put("b" + (b + 1) + "g", Math.round(bar(b) * 100 / 550)); put("o" + (b + 1), open(b)); }
        put("lelg", Math.round(lel));
        text("kg", String.format(Locale.ROOT, "%.1f", total()));
        text("line1", String.format(Locale.ROOT, "L %3.0f M %3.0f H %3.0f BAR", bar(0), bar(1), bar(2)));
        text("lel", String.format(Locale.ROOT, "%.0f", lel));
    }

    @Override protected void saveExtra(NBTTagCompound t) { for (int b = 0; b < 3; b++) t.setFloat("kg" + b, kg[b]); t.setBoolean("esd", esd); t.setFloat("lel", lel); t.setFloat("leak", leak); }
    @Override protected void loadExtra(NBTTagCompound t) { for (int b = 0; b < 3; b++) kg[b] = t.getFloat("kg" + b); esd = t.getBoolean("esd"); lel = t.getFloat("lel"); leak = t.getFloat("leak"); }
}
