package com.dogpound.railmap.traction;

import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import java.util.Locale;

/**
 * Hydrogen Compressor (batch 5 #6): a three-stage diaphragm compressor skid. Hydrogen gas from the factory's
 * Electrolysis Plant (pi_hydrogen, piped into its suction buffer - any side) is compressed 30 -> 90 -> 250 -> 500 bar
 * into the Storage Tubes within 8 blocks: 72 kg/h at 75 kW (mains). Intercoolers keep the discharge under 150 °C - the
 * COOLING selector (AUTO / HIGH) runs their fans; over 160 °C it trips. AUTO key: starts on suction pressure, stops when
 * the storage is full. A gas detector in the enclosure, the station ESD and an E-STOP. Mains + 12 V.
 */
public class TileH2Compressor extends com.dogpound.railmap.depot.DepotTile implements H2Station.Part {
    static final float KG_S = 0.02f;
    private final FluidTank inlet = new FluidTank(16_000) {
        @Override public boolean canFillFluidType(FluidStack f) { return f != null && f.getFluid() != null && f.getFluid().getName().contains("hydrogen"); }
    };
    private boolean running, esd, trip;
    private float dischC = 20, lel, made;
    private String why = "";

    public TileH2Compressor() { super(new String[]{"spares"}, new String[]{"Diaphragms, valves and seal kits"}, new int[]{9}); inlet.setTileEntity(this); }

    @Override public String wiredName() { return "Hydrogen compressor"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return running ? 750 : 3; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "%s · suction %.0f bar · discharge %.0f °C · %.1f kg made%s", running ? "RUNNING" : trip ? "TRIPPED " + why : "stopped", suction(), dischC, made, esd ? " · ESD" : ""); }

    float suction() { return 30f * inlet.getFluidAmount() / inlet.getCapacity(); }

    @Override public void esdTrip(String w) { esd = true; running = false; controlsChanged(); }
    @Override public boolean esd() { return esd; }
    @Override public float lel() { return lel; }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "start" -> {
                String no = cantRun();
                if (no != null) return "§c" + no;
                running = true; return "§aCompressor running";
            }
            case "stop" -> { running = false; return "§eCompressor stopped"; }
            case "reset" -> {
                if (lel >= 10) return String.format(Locale.ROOT, "§cGas %.0f %% LEL in the enclosure", lel);
                if (dischC > 120) return String.format(Locale.ROOT, "§cDischarge still %.0f °C", dischC);
                esd = false; trip = false; why = ""; H2Station.reset(world, pos);
                return "§aCompressor reset";
            }
            default -> { return null; }
        }
    }

    String cantRun() {
        if (ctl.get("m_estop") == 1) return "E-STOP is in";
        if (esd) return "Station ESD - RESET first";
        if (trip) return "Tripped: " + why + " - RESET";
        if (!mainsOk()) return "No mains (" + mains.what + ")";
        if (H2Station.storage(world, pos, 8) == null) return "No Storage Tubes within 8 blocks";
        if (suction() < 2) return "No hydrogen at the suction - pipe it in from an Electrolysis Plant";
        return null;
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { if (ctl.get(id) == 0) { ctl.set(id, 1); H2Station.esd(world, pos, "E-STOP at the compressor"); } else ctl.set(id, 0); return ctl.get(id) == 1 ? "§cESD - station shut down" : "§aE-stop released (RESET each part)"; }
        return null;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        TileH2Storage st = H2Station.storage(world, pos, 8);
        boolean auto = ctl.get("k_auto") == 1;
        if (auto && !running && cantRun() == null && suction() > 6 && st != null && st.total() < TileH2Storage.CAP * 2.95f) running = true;
        if (running) {
            String no = !powered() ? "no 12 V" : cantRun();
            if (no != null && !no.startsWith("No hydrogen")) running = false;
            else if (suction() < 1) { if (auto) running = false; }
            else {
                float kg = Math.min(KG_S, inlet.getFluidAmount() / (float) H2Station.MB_PER_KG);
                float took = st.fill(kg);
                inlet.drain(Math.round(took * H2Station.MB_PER_KG), true);
                made += took;
                if (took < kg * 0.1f) { running = false; Recorder.event(world, "Hydrogen compressor: storage full - stopped"); }
                if (age % 80 == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.servo"), SoundCategory.BLOCKS, 0.5f, 0.6f);
            }
        }
        boolean high = Math.floorMod(ctl.get("s_cool"), 2) == 1;
        float target = running ? (high ? 95 : 135) : 20;
        dischC += (target - dischC) * 0.05f;
        if (dischC > 160 && !trip) { trip = true; running = false; why = "HIGH DISCHARGE TEMP"; Recorder.event(world, "Hydrogen compressor tripped: high discharge temperature"); }
        if (running && world.rand.nextInt(20000) == 0) lel += 25;          // a diaphragm weeps
        lel = Math.max(0, lel - lel * 0.1f);
        if (powered()) H2Station.detector(world, pos, "compressor", lel);
        float s = suction(), s3 = st == null ? 0 : st.highest();
        put("pwr", powered()); put("mainsl", mains.ok()); put("run", running); put("tripl", trip); put("esdl", esd); put("gas", lel >= 5); put("leakl", lel >= 20);
        put("fan", running || dischC > 60); put("autol", auto); put("stl", st != null); put("full", st != null && st.total() >= TileH2Storage.CAP * 2.95f);
        put("suctg", Math.round(s * 100 / 40)); put("st1g", running ? Math.round(Math.min(90, 3 * s) * 100 / 120) : 0); put("st2g", running ? Math.round(Math.min(250, 8.3f * s) * 100 / 300) : 0);
        put("st3g", Math.round(s3 * 100 / 550)); put("tempg", Math.round(dischC * 100 / 200)); put("lelg", Math.round(lel));
        text("kg", String.format(Locale.ROOT, "%.1f", made));
        text("line1", running ? String.format(Locale.ROOT, "RUN %.0f KG/H", KG_S * 3600) : trip ? why : esd ? "ESD TRIPPED" : "STOPPED");
        text("temp", String.format(Locale.ROOT, "%.0f", dischC));
    }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ? (T) inlet : super.getCapability(c, f); }

    /** test hook: put hydrogen at the suction (mB) */
    public int feed(int mb) { net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid("pi_hydrogen"); return f == null ? 0 : inlet.fill(new FluidStack(f, mb), true); }

    @Override protected void saveExtra(NBTTagCompound t) { t.setTag("in", inlet.writeToNBT(new NBTTagCompound())); t.setBoolean("run", running); t.setBoolean("esd", esd); t.setBoolean("trip", trip); t.setFloat("dc", dischC); t.setFloat("lel", lel); t.setFloat("made", made); t.setString("why", why); }
    @Override protected void loadExtra(NBTTagCompound t) { inlet.readFromNBT(t.getCompoundTag("in")); running = t.getBoolean("run"); esd = t.getBoolean("esd"); trip = t.getBoolean("trip"); dischC = t.hasKey("dc") ? t.getFloat("dc") : 20; lel = t.getFloat("lel"); made = t.getFloat("made"); why = t.getString("why"); }
}
