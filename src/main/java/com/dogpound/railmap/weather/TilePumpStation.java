package com.dogpound.railmap.weather;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.ohle.Reg;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;

/**
 * Drainage Pump Station (her ChatGPT list #64: "pump stations need power and maintenance"): a sump with two
 * submersible pumps (duty / standby) and a control kiosk. AUTO: the float switch starts the duty pump at 60 mm of
 * water in the formation, the standby joins at 150 mm (water at the rail head), both stop under 20 mm; duty alternates
 * every start. Each pump takes 3 mm / 5 s out of its cell and draws 15 kW (any mod's cable beside it, the OHLE aux
 * transformer, or a substation). Pumps wear: service due after 120 running minutes (efficiency falls), seized at 180
 * - trip and a MOTOR alarm. Service: MAINT key to ISOLATE, press SERVICE. Drowned if the kiosk floods (over 400 mm).
 */
public class TilePumpStation extends SafetyTile {
    static final Reg<TilePumpStation> REG = new Reg<>();
    static final String[] MODES = {"AUTO", "HAND", "OFF"};
    private final double[] mins = new double[2];
    private final boolean[] run = new boolean[2], fault = new boolean[2];
    private int duty;
    private double kwh;
    private String src = "";

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    // ---- drainage network monitoring (infra #43) ------------------------------------------------------------------------
    public static java.util.Collection<TilePumpStation> all(net.minecraft.world.World w) { return REG.all(w); }
    public boolean running(int i) { return run[i]; }
    public boolean faulted(int i) { return fault[i]; }
    public double minutes(int i) { return mins[i]; }
    public String mode() { return MODES[Math.max(0, Math.min(2, ctl.get("s_mode")))]; }
    public String source() { return src; }
    /** the flood sensor's FORCE PUMPS: run both pumps by hand (1) or back to the float switch (0) */
    public void force(boolean hand) { ctl.set("s_mode", hand ? 1 : 0); controlsChanged(); }
    /** a possession gang serviced both pumps (infra #45) */
    public void serviced(String who) {
        for (int i = 0; i < 2; i++) { mins[i] = 0; fault[i] = false; AlarmManager.get(world).clear("pump:" + pos.toLong() + ":" + i); }
        WeatherData.get(world).log(world, wiredName() + " serviced by " + who);
        markDirty();
    }

    /** mm / 5 s it is taking out now */
    double pumping() { double s = 0; for (int i = 0; i < 2; i++) if (run[i]) s += 3 * eff(i); return s; }
    private double eff(int i) { return mins[i] < 120 ? 1 : Math.max(0.4, 1 - (mins[i] - 120) / 100); }

    @Override public String wiredName() { return "Pump station " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return MODES[ctl.get("s_mode")] + " · P1 " + st(0) + " · P2 " + st(1) + (src.isEmpty() ? "" : " · " + src); }
    @Override public int wiredLamp() { return !powered() || fault[0] && fault[1] ? 3 : fault[0] || fault[1] || mins[0] > 120 || mins[1] > 120 ? 2 : 1; }
    private String st(int i) { return fault[i] ? "FAULT" : run[i] ? "RUN" : "stby" ; }

    @Override
    protected void serverTick() {
        if (age % 20 != 19) return;
        WeatherData.Cell c = Weather.cell(world, pos);
        int mode = ctl.get("s_mode");
        boolean iso = ctl.get("k_maint") == 1;
        boolean drowned = c.flood() > 400;
        boolean[] want = new boolean[2];
        if (powered() && !iso && !drowned) {
            if (mode == 1) { want[0] = true; want[1] = true; }
            else if (mode == 0) {
                boolean any = run[0] || run[1];
                boolean on1 = c.water > 60 || any && c.water > 20, on2 = c.water > 150 || run[0] && run[1] && c.water > 20;
                int a = duty, b = 1 - duty;
                if (!any && on1) { duty = 1 - duty; a = duty; b = 1 - duty; }
                want[a] = on1;
                want[b] = on2;
            }
        }
        double needW = 0;
        for (int i = 0; i < 2; i++) { if (fault[i]) want[i] = false; if (want[i]) needW += 15000; }
        String[] s = {""};
        double got = needW > 0 ? Aux.draw(world, pos, needW, s) : 0;
        src = needW > 0 ? s[0] : "";
        for (int i = 0; i < 2; i++) {
            run[i] = want[i] && got > 0.5;
            if (!run[i]) continue;
            mins[i] += 1 / 60.0;
            if (mins[i] >= 180 && !fault[i]) {
                fault[i] = true;
                run[i] = false;
                AlarmManager.get(world).raise(world, "pump:" + pos.toLong() + ":" + i, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, wiredName() + ": pump " + (i + 1) + " SEIZED - overdue service (" + Math.round(mins[i]) + " running minutes)", pos, true);
            }
        }
        if (drowned && (run[0] || run[1])) { run[0] = run[1] = false; }
        kwh += needW / 1000 * got / 3600;
        put("r1", run[0]); put("r2", run[1]); put("f1", fault[0]); put("f2", fault[1]);
        put("high", c.water > 150);
        put("supply", needW == 0 || got > 0.5);
        put("due", mins[0] >= 120 || mins[1] >= 120);
        put("drown", drowned);
        put("pwr", powered());
        put("level", (int) Math.min(100, c.water / 3));
        text("lvl", String.format(Locale.ROOT, "%.0f MM", c.water));
        text("h1", String.format(Locale.ROOT, "P1 %.0f MIN", mins[0]));
        text("h2", String.format(Locale.ROOT, "P2 %.0f MIN", mins[1]));
        text("src", src.isEmpty() ? MODES[mode] : src.toUpperCase(Locale.ROOT));
        markDirty();
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "s_mode": ctl.set("s_mode", (ctl.get("s_mode") + 1) % 3); return "§ePumps " + MODES[ctl.get("s_mode")];
            case "k_maint":
                if (who == null) return "§cThe maintenance key is at the kiosk";
                ctl.set("k_maint", 1 - ctl.get("k_maint"));
                return ctl.get("k_maint") == 1 ? "§ePumps ISOLATED for maintenance" : "§aPumps back in service";
            case "svc": {
                if (who == null) return "§cService is done at the pumps";
                if (ctl.get("k_maint") != 1) return "§cIsolate the pumps first (MAINT key)";
                for (int i = 0; i < 2; i++) { mins[i] = 0; fault[i] = false; AlarmManager.get(world).clear("pump:" + pos.toLong() + ":" + i); }
                WeatherData.get(world).log(world, wiredName() + " serviced by " + who.getName());
                com.dogpound.railmap.alarm.Recorder.event(world, wiredName() + ": pumps serviced by " + who.getName());
                return "§aBoth pumps serviced - bearings, seals and impellers done";
            }
            default: return "§7" + gearState();
        }
    }

    @Override protected void save(NBTTagCompound t) { t.setDouble("m1", mins[0]); t.setDouble("m2", mins[1]); t.setBoolean("f1", fault[0]); t.setBoolean("f2", fault[1]); t.setInteger("duty", duty); t.setDouble("kwh", kwh); }
    @Override protected void load(NBTTagCompound t) { mins[0] = t.getDouble("m1"); mins[1] = t.getDouble("m2"); fault[0] = t.getBoolean("f1"); fault[1] = t.getBoolean("f2"); duty = t.getInteger("duty"); kwh = t.getDouble("kwh"); }
}
