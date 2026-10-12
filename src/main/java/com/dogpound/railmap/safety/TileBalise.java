package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.Map;

/**
 * Balise group + LEU (her batch-3 list #12): the yellow transponder slab between the rails and its lineside encoder
 * cabinet. The LEU is programmed from its own panel with the PROGRAM key turned: speed limit (+ / -, or NONE), the
 * distance ahead it takes effect, FIXED / SWITCHED mode, and which direction of travel it talks to. SWITCHED reads the
 * wrench-linked signal: STOP = end of authority at the signal, caution = 40 km/h, clear = the programmed limit, and
 * sends the route set from that signal as route data. The telegram window shows exactly what a train reads now.
 */
public class TileBalise extends SafetyTile {
    private long signal;
    private String text = "";
    private int passes, silent;
    private long txUntil;

    @Override public String wiredName() { return "Balise BG" + bg(); }

    public int bg() { return Math.floorMod(pos.getX() * 73 + pos.getZ() * 19 + pos.getY(), 1000); }
    public int passes() { return passes; }
    public int dir() { return Math.min(2, Math.max(0, ctl.get("s_dir"))); }
    public int mode() { return Math.min(1, Math.max(0, ctl.get("s_mode"))); }
    public int kmh() { return Balises.SPEEDS[Math.floorMod(ctl.get("spd"), Balises.SPEEDS.length)]; }
    public int offset() { return Balises.OFFSETS[Math.min(Balises.OFFSETS.length - 1, Math.max(0, ctl.get("s_off")))]; }
    public String modeName() { return (mode() == 1 ? "SWITCHED" : "FIXED") + " · " + new String[]{"BOTH", "FACING", "TRAILING"}[dir()]; }
    public boolean healthy() { return powered() && !term.burnt; }

    EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getPropertyKeys().contains(com.dogpound.railmap.block.BlockStationDevice.FACING) ? s.getValue(com.dogpound.railmap.block.BlockStationDevice.FACING) : EnumFacing.NORTH;
    }

    BlockPos signalPos() { return signal == 0 ? null : BlockPos.fromLong(signal); }

    TileSignalMast mast() {
        BlockPos p = signalPos();
        return p != null && world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileSignalMast m ? m : null;
    }

    /** route data: SWITCHED = the route set from the linked signal; FIXED = the programmed text */
    String routeData() {
        if (mode() == 1) {
            TileSignalMast m = mast();
            if (m != null) for (Interlocking.Route r : Interlocking.routes(world))
                if (r.start.equals(m.getPos())) {
                    String exit = world.getTileEntity(r.end) instanceof TileSignalMast e ? SignalSighting.name(e) : r.end.getX() + "," + r.end.getZ();
                    return "RT>" + exit.toUpperCase(Locale.ROOT) + " " + Math.round(r.path.length) + "M " + r.path.switches.size() + "SW";
                }
        }
        return text;
    }

    /** what a train reads now (null = silent: no 12 V / burnt) */
    Balises.Telegram telegram() {
        if (!healthy()) return null;
        TileSignalMast m = mode() == 1 ? mast() : null;
        return Balises.build(kmh(), offset(), mode(), m == null ? null : m.shownAspect(), routeData());
    }

    public String telegramText() { Balises.Telegram t = telegram(); return t == null ? "SILENT" : t.show(); }

    void passed(boolean ok) {
        passes++;
        if (!ok) silent++;
        txUntil = world.getTotalWorldTime() + 40;
        markDirty();
    }

    /** once a second (Balises.tick) */
    void second() {
        String key = "balise:" + pos.toLong();
        AlarmManager am = AlarmManager.get(world);
        if (!healthy()) am.raise(world, key, AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR, "Balise BG" + bg() + " at " + pos.getX() + "," + pos.getZ() + " SILENT - " + (term.burnt ? "burnt out" : "no 12 V") + ": trains get no telegram", pos, true);
        else if (mode() == 1 && mast() == null) am.raise(world, key, AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MINOR, "Balise BG" + bg() + " SWITCHED but no signal linked to its LEU - sends end of authority", pos, true);
        else am.clear(key);
    }

    @Override
    public String gearState() { return !healthy() ? "SILENT - " + (term.burnt ? "burnt out" : "no 12 V") : modeName() + " · " + telegramText(); }
    @Override public int wiredLamp() { return healthy() ? 1 : 3; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("bg", bg()); m.put("telegram", telegramText()); m.put("passes", passes); m.put("mode", modeName()); }

    @Override
    protected void serverTick() {
        if (age % 10 != 0) return;
        boolean prog = ctl.get("k_prog") == 1;
        put("pwr", powered());
        put("tx", healthy() && world.getTotalWorldTime() < txUntil);
        put("fault", powered() && (term.burnt || mode() == 1 && mast() == null));
        put("prog", prog);
        TileSignalMast m = mast();
        put("sigstop", m != null && (m.shownAspect() == Aspect.DARK || m.shownAspect().isStop()));
        put("passes", passes);
        text("tel", telegramText());
        text("bg", "BG" + bg());
        text("kmh", kmh() < 0 ? "NONE" : kmh() + " KMH");
        text("rd", routeData().isEmpty() ? "-" : routeData().toUpperCase(Locale.ROOT));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        boolean prog = ctl.get("k_prog") == 1;
        switch (id) {
            case "lamptest": return lampTest();
            case "k_prog": {
                ctl.set("k_prog", prog ? 0 : 1);
                if (prog) SafetyData.get(world).history(world, pos, wiredName(), "balise", "programmed by " + who(who) + ": " + telegramText());
                return prog ? "§aLEU back to RUN - telegram " + telegramText() : "§6LEU in PROGRAM - set the telegram, then turn back to RUN";
            }
            case "spdup": case "spddn": case "s_off": case "s_mode": case "s_dir": {
                if (!prog) return "§cTurn the PROGRAM key first";
                if (id.equals("spdup")) ctl.set("spd", Math.min(Balises.SPEEDS.length - 1, ctl.get("spd") + 1));
                else if (id.equals("spddn")) ctl.set("spd", Math.max(0, ctl.get("spd") - 1));
                else if (id.equals("s_off")) ctl.set("s_off", (ctl.get("s_off") + 1) % Balises.OFFSETS.length);
                else if (id.equals("s_mode")) ctl.set("s_mode", 1 - mode());
                else ctl.set("s_dir", (dir() + 1) % 3);
                return "§eTelegram: " + telegramText() + " · " + modeName();
            }
            default: return "§7" + gearState();
        }
    }

    String setText(String t, String who) {
        text = t.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 \\-.:>$/%+]", "");
        if (text.length() > 24) text = text.substring(0, 24);
        markDirty();
        SafetyData.get(world).history(world, pos, wiredName(), "balise", "route data set to '" + text + "' by " + who);
        return "§aBG" + bg() + " route data: " + text;
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (!(world.getTileEntity(pick) instanceof TileSignalMast m)) return "§cPick the signal this balise's LEU reads";
        signal = pick.toLong();
        markDirty();
        return "§aLEU linked to signal " + SignalSighting.name(m) + " (set the mode to SWITCHED)";
    }

    @Override protected boolean keepTextUnpowered() { return false; }
    @Override protected void save(NBTTagCompound t) { t.setLong("sig", signal); t.setString("txt", text); t.setInteger("passes", passes); t.setInteger("silent", silent); }
    @Override protected void load(NBTTagCompound t) { signal = t.getLong("sig"); text = t.getString("txt"); passes = t.getInteger("passes"); silent = t.getInteger("silent"); }
}
