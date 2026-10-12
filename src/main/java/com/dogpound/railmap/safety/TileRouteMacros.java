package com.dogpound.railmap.safety;

import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Route Macro Panel (her ChatGPT batch-3 list #19): the signaller's one-button route setting. Eight illuminated macro
 * buttons (BANK selector: macros 1-8 / 9-16) each with its name window and three lamps - white SET (every route its
 * last run set still stands), amber RUNNING (flashing while a SEQ macro works through its routes), red FAILED. Press a
 * button to run that macro (it becomes the SELECTED one); MODE writes SEQ / ALL to the selected macro; CANCEL takes
 * back the routes the selected macro set. Recording: key to REC, press a button (its old routes are cleared), set the
 * routes the normal way, press it again (or key back to OFF). Steps by hand: Signal Wrench - pick the entrance signal,
 * click ENTRANCE; pick the exit signal, click EXIT. CLEAR (key at REC) empties the selected macro. The ROUTES SET dial
 * counts every route the interlocking holds. 12 V: with none the panel is dark and dead (the key still turns).
 */
public class TileRouteMacros extends SafetyTile {
    private int selected = 1;
    private long pendingStart;

    private int bank() { return Math.min(1, Math.max(0, ctl.get("s_bank"))); }

    @Override public String wiredName() { return "Route macro panel"; }

    @Override public String gearState() {
        Macros d = Macros.get(world);
        return !powered() ? "DEAD - no 12 V" : "selected " + d.label(selected) + " · " + d.status(selected) + (d.recording >= 0 ? " · REC " + d.label(d.recording) : "");
    }

    @Override public int wiredLamp() { Macros d = Macros.get(world); return !powered() ? 3 : d.state(world, selected) == 3 ? 3 : d.recording >= 0 ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { Macros d = Macros.get(world); m.put("selected", selected); m.put("state", d.state(world, selected)); m.put("recording", d.recording); }

    @Override
    protected void serverTick() {
        if (age % 10 != 4) return;
        Macros d = Macros.get(world);
        for (int i = 1; i <= 8; i++) {
            int n = bank() * 8 + i;
            int st = d.state(world, n);
            put("l" + i, st == 2);
            put("r" + i, st == 1 || d.recording == n);
            put("f" + i, st == 3);
            Macros.Macro m = d.macro(n);
            text("n" + i, n + " " + (m.name.isEmpty() ? (m.steps.isEmpty() ? "-" : m.steps.size() + " RTE") : m.name));
        }
        Macros.Macro sm = d.macro(selected);
        put("s_mode", sm.mode);
        put("pwr", powered());
        put("rec", d.recording >= 0);
        put("busy", !d.running.isEmpty());
        put("flt", d.state(world, selected) == 3);
        put("nroute", sm.steps.size());
        put("g_routes", Interlocking.routes(world).size());
        text("sel", "SEL " + selected + " " + d.label(selected));
        text("stat", d.status(selected).isEmpty() ? (sm.steps.isEmpty() ? "EMPTY - KEY REC TO RECORD" : "READY " + (sm.mode == Macros.ALL ? "ALL" : "SEQ")) : d.status(selected));
        int step = d.stepOf(selected);
        text("step", d.recording == selected ? "REC " + sm.steps.size() : step > 0 ? "STEP " + step + "/" + sm.steps.size() : pendingStart != 0 ? "ENT " + Macros.sig(world, BlockPos.fromLong(pendingStart)) : "");
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        Macros d = Macros.get(world);
        String me = who(who);
        if (id.equals("k_rec")) {
            int v = 1 - ctl.get("k_rec");
            ctl.set("k_rec", v);
            if (v == 0 && d.recording >= 0) return d.stopRecording(world);
            return v == 1 ? "§cRecord key at REC - press a macro button to record into it" : "§7Record key OFF";
        }
        if (id.equals("s_bank")) { ctl.set("s_bank", 1 - bank()); return "§eBank " + (bank() == 0 ? "1-8" : "9-16"); }
        if (!powered()) return "§cNo 12 V - the panel is dead";
        if (id.length() == 2 && id.charAt(0) == 'm' && Character.isDigit(id.charAt(1))) {
            int n = bank() * 8 + (id.charAt(1) - '0');
            selected = n;
            markDirty();
            if (ctl.get("k_rec") == 1) return d.record(world, n, me);
            String r = d.play(world, n, me);
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.RELAY, SoundCategory.BLOCKS, 0.6f, 1.0f);
            return r;
        }
        switch (id) {
            case "s_mode": {
                Macros.Macro m = d.macro(selected);
                m.mode = 1 - m.mode;
                d.markDirty();
                ctl.set("s_mode", m.mode);
                return "§e" + d.label(selected) + ": " + (m.mode == Macros.ALL ? "ALL - every route at once or none" : "SEQ - one route at a time, retried 30 s");
            }
            case "cancel": return d.cancel(world, selected, me);
            case "clear":
                if (ctl.get("k_rec") != 1) return "§cCLEAR needs the record key at REC";
                return d.clear(world, selected, me);
            case "ent": case "ext": return "§7Signal Wrench: pick the " + (id.equals("ent") ? "entrance" : "exit") + " signal, then right-click " + (id.equals("ent") ? "ENTRANCE" : "EXIT") + " (adds a step to " + d.label(selected) + ")";
            default: return "§7" + gearState();
        }
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (!control.equals("ent") && !control.equals("ext")) return null;
        if (!(world.getTileEntity(pick) instanceof TileSignalMast m)) return "§cPick a signal with the wrench";
        if (!powered()) return "§cNo 12 V - the panel is dead";
        if (control.equals("ent")) {
            pendingStart = pick.toLong();
            markDirty();
            return "§eEntrance " + SignalSighting.name(m) + " - now pick the exit signal and click EXIT";
        }
        if (pendingStart == 0) return "§cClick ENTRANCE with the entrance signal first";
        String r = Macros.get(world).addStep(world, selected, BlockPos.fromLong(pendingStart), pick);
        pendingStart = 0;
        markDirty();
        return r;
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("sel", selected); t.setLong("pend", pendingStart); }
    @Override protected void load(NBTTagCompound t) { selected = Math.max(1, Math.min(Macros.N, t.hasKey("sel") ? t.getInteger("sel") : 1)); pendingStart = t.getLong("pend"); }
}
