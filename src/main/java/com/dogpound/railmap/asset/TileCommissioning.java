package com.dogpound.railmap.asset;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.DepotClock;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.depot.Diagnostics;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Commissioning Test Set (her 48-next list #10, #19, #45): a wheeled test cart with a touchscreen-style plan display,
 * test lead drawers and an injection set. SCAN finds the assets within the RADIUS and their connections; PLAN proposes
 * each asset's acceptance tests; RUN works through them on the real gear (the cart's screen follows along); SIGN OFF
 * (with the AUTHORISED key turned) commissions the selected asset when every one of its tests passed; PRINT gives its
 * certificate (or the connection diagram on the LINKS page). The BENCH page: wrench-pick a protection IED onto the
 * TARGET window, INJECT runs the secondary injection test (expected vs actual through CT, VT, relay, trip wiring and
 * breaker) and files its certificate. 12 V for the electronics; no mains needed (it runs off its own battery).
 */
public class TileCommissioning extends DepotTile {
    private static final int[] RADII = {16, 32, 64};
    static final String[] PAGES = {"ASSETS", "TESTS", "LINKS", "BENCH", "CERTS"};
    private final List<Commissioning.Conn> conns = new ArrayList<>();
    private final List<Commissioning.Test> tests = new ArrayList<>();
    private final List<String> assets = new ArrayList<>();
    private int sel, runAt = -1, runT, scroll;
    private long target;
    private String benchLast = "";
    private boolean lastPass;

    public TileCommissioning() {
        super(new String[]{"leads", "certs"}, new String[]{"Test leads + clamps drawer", "Certificates tray"}, new int[]{9, 9});
    }

    @Override public String wiredName() { return "Commissioning test set"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : assets.size() + " assets · " + tests.size() + " tests" + (runAt >= 0 ? " · running " + (runAt + 1) + "/" + tests.size() : ""); }
    @Override public void gearInfo(Map<String, Object> m) { m.put("assets", assets.size()); m.put("links", conns.size()); m.put("tests", tests.size()); m.put("running", runAt >= 0); }

    int radius() { return RADII[Math.floorMod(ctl.get("s_rad"), 3)]; }
    boolean all() { return ctl.get("s_scope") == 1; }
    int page() { return Math.floorMod(ctl.get("s_page"), PAGES.length); }
    AssetData.Asset selected() { return assets.isEmpty() ? null : AssetData.get(world).byId(assets.get(Math.floorMod(sel, assets.size()))); }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop") && ctl.get("m_estop") == 0 && runAt >= 0) { runAt = -1; Recorder.event(world, "COMMISSIONING: test run stopped by E-STOP"); }
        return null;
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        GridData.Node n = GridData.get(world).node(pick);
        if (n == null || n.kind != GridKind.IED) return "§cPick a PROTECTION IED (grid) with the Signal Wrench for the injection set";
        target = pick.toLong();
        controlsChanged();
        return "§aInjection set connected to the IED at " + pick.getX() + "," + pick.getY() + "," + pick.getZ();
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "scan" -> {
                conns.clear();
                conns.addAll(Commissioning.discover(world, pos, radius()));
                assets.clear();
                for (AssetData.Asset a : Commissioning.inRange(world, pos, radius(), all())) assets.add(a.id);
                tests.clear(); sel = 0; runAt = -1;
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CTL_TOGGLE, SoundCategory.BLOCKS, 0.4f, 1.0f);
                int nv = 0; for (Commissioning.Conn c : conns) if (c.verified) nv++;
                return String.format(Locale.ROOT, "§bScan %d m: %d asset(s)%s, %d connection(s) discovered, %d already verified", radius(), assets.size(), all() ? "" : " needing commissioning", conns.size(), nv);
            }
            case "plan" -> {
                if (assets.isEmpty()) return "§7SCAN first";
                tests.clear();
                for (String id2 : assets) { AssetData.Asset a = AssetData.get(world).byId(id2); if (a != null) tests.addAll(Commissioning.plan(world, a, conns)); }
                for (String id2 : assets) { AssetData.Asset a = AssetData.get(world).byId(id2); if (a != null && a.comm == AssetData.UNCOMM) { a.comm = AssetData.TESTING; a.note(world, "acceptance tests planned"); } }
                AssetData.get(world).markDirty();
                return "§b" + tests.size() + " acceptance tests proposed for " + assets.size() + " asset(s) - RUN to carry them out";
            }
            case "run" -> {
                if (tests.isEmpty()) return "§7PLAN first";
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (runAt >= 0) return "§7Already running";
                for (Commissioning.Test t : tests) if (t.state != 2) { t.state = 0; t.detail = ""; }
                runAt = next(-1); runT = 0;
                Recorder.event(world, "COMMISSIONING: test run started (" + tests.size() + " tests) by " + who(who));
                return runAt < 0 ? "§aEvery test has already passed" : "§6Running the acceptance tests...";
            }
            case "abort" -> { if (runAt < 0) return "§7Nothing running"; runAt = -1; return "§6Test run stopped"; }
            case "prev" -> { sel--; scroll = 0; return null; }
            case "next" -> { sel++; scroll = 0; return null; }
            case "sign" -> {
                if (ctl.get("k_auth") == 0) return "§cTurn the AUTHORISED PERSON key to sign off";
                AssetData.Asset a = selected();
                if (a == null) return "§7Nothing selected";
                String r = Commissioning.sign(world, a, tests, conns, who(who));
                if (r.startsWith("§a")) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.8f, 1.0f);
                if (r.startsWith("§a")) com.dogpound.railmap.api.QuestEvents.fire(world, who, pos, "commissioned", "asset", a.id, "kind", a.kind.name());
                return r;
            }
            case "print" -> { print(who); return "§7Printing..."; }
            case "inject" -> {
                if (target == 0) return "§cNo IED on the injection set - Signal Wrench: pick the IED, then click TARGET";
                Object[] r = com.dogpound.railmap.grid.Bench.test(world, BlockPos.fromLong(target));
                @SuppressWarnings("unchecked") List<String> l = (List<String>) r[1];
                lastPass = (Boolean) r[0];
                benchLast = l.isEmpty() ? "" : l.get(l.size() - 1);
                AssetData.Cert c = AssetData.get(world).addCert(world, "IED " + BlockPos.fromLong(target).getX() + "," + BlockPos.fromLong(target).getZ(), "SECONDARY INJECTION", lastPass, l, who(who));
                if (who != null) for (String s : l) who.sendMessage(new net.minecraft.util.text.TextComponentString((s.startsWith("FAIL") ? "§c" : s.startsWith("PASS") ? "§a" : "§7") + s));
                Recorder.event(world, "BENCH: secondary injection " + (lastPass ? "PASSED" : "FAILED") + " (certificate #" + c.id + ")");
                return (lastPass ? "§aINJECTION PASSED" : "§cINJECTION FAILED") + " - certificate #" + c.id;
            }
            default -> { return null; }
        }
    }

    private int next(int from) { for (int i = from + 1; i < tests.size(); i++) if (tests.get(i).state != 2) return i; return -1; }

    @Override
    protected void work() {
        if (runAt >= 0 && powered()) {
            Commissioning.Test t = tests.get(runAt);
            if (Commissioning.step(world, t, runT++)) {
                if (t.state == 3) { AssetData.Asset a = AssetData.get(world).byId(t.asset); if (a != null) Commissioning.failed(world, a, t); }
                world.playSound(null, pos, t.state == 2 ? com.dogpound.railmap.ModSounds.BEEP_OK : com.dogpound.railmap.ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.3f, t.state == 2 ? 1.8f : 0.7f);
                runAt = next(runAt);
                runT = 0;
                if (runAt < 0) {
                    int p = 0, f = 0; for (Commissioning.Test x : tests) { if (x.state == 2) p++; if (x.state == 3) f++; }
                    Recorder.event(world, "COMMISSIONING: test run finished - " + p + " pass, " + f + " fail");
                }
            }
        }
        if (age % 5 == 0) screens();
    }

    private void screens() {
        int nnew = 0, nv = 0, np = 0, nf = 0;
        AssetData d = AssetData.get(world);
        for (String id : assets) { AssetData.Asset a = d.byId(id); if (a != null && a.comm != AssetData.COMMISSIONED) nnew++; }
        for (Commissioning.Conn c : conns) if (c.verified) nv++;
        for (Commissioning.Test t : tests) { if (t.state == 2) np++; if (t.state == 3) nf++; }
        put("pwr", true);
        put("lscan", !conns.isEmpty() || !assets.isEmpty());
        put("lrun", runAt >= 0);
        put("lpass", !tests.isEmpty() && nf == 0 && np == tests.size());
        put("lfail", nf > 0);
        put("lwarn", nnew > 0);
        put("lauth", ctl.get("k_auth") == 1);
        put("ltgt", target != 0);
        put("nass", assets.size()); put("nnew", nnew); put("nconn", conns.size()); put("nver", nv);
        put("ntest", tests.size()); put("npass", np); put("nfail", nf);
        put("prog", tests.isEmpty() ? 0 : (np + nf) * 100 / tests.size());
        text("tgt", target == 0 ? "NO IED" : "IED " + BlockPos.fromLong(target).getX() + "," + BlockPos.fromLong(target).getZ());
        text("title", PAGES[page()] + " · R " + radius() + "M" + (all() ? " ALL" : " NEW"));
        String[] l = new String[6];
        AssetData.Asset a = selected();
        switch (page()) {
            case 0 -> {
                if (a == null) { l[0] = assets.isEmpty() && conns.isEmpty() ? "PRESS SCAN" : "NOTHING TO COMMISSION"; break; }
                l[0] = cut(a.id + " " + a.kind.label.toUpperCase(Locale.ROOT), 30);
                l[1] = cut(a.name.toUpperCase(Locale.ROOT), 30);
                l[2] = AssetData.COMM[a.comm] + (a.commBy.isEmpty() ? "" : " " + cut(a.commBy.toUpperCase(Locale.ROOT), 12));
                l[3] = String.format(Locale.ROOT, "COND %d%% LIFE %d%%", Math.round(a.cond), Math.round(a.lifeUsed() * 100));
                int mine = 0, ok = 0; for (Commissioning.Test t : tests) if (t.asset.equals(a.id)) { mine++; if (t.state == 2) ok++; }
                l[4] = mine == 0 ? "NO TESTS PLANNED" : ok + "/" + mine + " TESTS PASSED";
                l[5] = (Math.floorMod(sel, Math.max(1, assets.size())) + 1) + "/" + assets.size() + (ok == mine && mine > 0 ? " READY TO SIGN" : "");
            }
            case 1 -> {
                List<Commissioning.Test> mine = new ArrayList<>();
                for (Commissioning.Test t : tests) if (a == null || t.asset.equals(a.id)) mine.add(t);
                l[0] = a == null ? "ALL TESTS" : cut(a.id + " TESTS", 30);
                for (int i = 0; i < 5 && i < mine.size(); i++) { Commissioning.Test t = mine.get(i); l[i + 1] = cut((t.state == 2 ? "OK " : t.state == 3 ? "XX " : t.state == 1 ? ">> " : ".. ") + t.name.toUpperCase(Locale.ROOT), 30); }
                if (mine.isEmpty()) l[1] = "PRESS PLAN";
            }
            case 2 -> {
                l[0] = conns.size() + " LINKS, " + nv + " VERIFIED";
                int i = 1;
                for (Commissioning.Conn c : conns) { if (i > 5) break; if (a != null && c.pa != a.pos && c.pb != a.pos && conns.size() > 5) continue; l[i++] = cut((c.verified ? "V " : "D ") + c.kind + " " + c.b.toUpperCase(Locale.ROOT), 30); }
            }
            case 3 -> {
                l[0] = target == 0 ? "NO IED - WRENCH TARGET" : "IED " + BlockPos.fromLong(target).getX() + "," + BlockPos.fromLong(target).getZ();
                int bf = 0; for (com.dogpound.railmap.safety.FaultLab.Kind k : new com.dogpound.railmap.safety.FaultLab.Kind[]{com.dogpound.railmap.safety.FaultLab.Kind.CT_ERROR, com.dogpound.railmap.safety.FaultLab.Kind.VT_FAIL,
                        com.dogpound.railmap.safety.FaultLab.Kind.TRIP_WIRING, com.dogpound.railmap.safety.FaultLab.Kind.BREAKER_STUCK, com.dogpound.railmap.safety.FaultLab.Kind.RELAY_SETTING}) bf += com.dogpound.railmap.safety.FaultLab.benchAt(k).size();
                l[1] = bf == 0 ? "NO BENCH FAULTS IN" : bf + " BENCH FAULT(S) IN (FAULT LAB)";
                l[2] = benchLast.isEmpty() ? "PRESS INJECT" : cut(benchLast.toUpperCase(Locale.ROOT), 30);
                l[3] = "EXPECTED VS ACTUAL:";
                l[4] = "CT VT RELAY WIRING BREAKER";
            }
            default -> {
                List<AssetData.Cert> cs = AssetData.get(world).certs;
                l[0] = cs.size() + " CERTIFICATES";
                for (int i = 0; i < 5 && i < cs.size(); i++) { AssetData.Cert c = cs.get(i); l[i + 1] = cut("#" + c.id + " " + (c.pass ? "PASS " : "FAIL ") + c.kind + " " + c.scope, 30).toUpperCase(Locale.ROOT); }
            }
        }
        for (int i = 0; i < 6; i++) text("d" + (i + 1), l[i] == null ? "" : l[i]);
        Commissioning.Test cur = runAt >= 0 ? tests.get(runAt) : null;
        text("status", cur != null ? cut(">> " + cur.asset + " " + cur.name.toUpperCase(Locale.ROOT), 34) : ctl.get("m_estop") == 1 ? "E-STOP IN" : "READY");
    }

    private void print(EntityPlayer who) {
        List<String> l = new ArrayList<>();
        AssetData.Asset a = selected();
        if (page() == 2) {
            l.add("CONNECTION DIAGRAM " + DepotClock.stamp(world));
            l.add("R " + radius() + " m round " + pos.getX() + "," + pos.getZ());
            for (Commissioning.Conn c : conns) l.add((c.verified ? "[V] " : "[D] ") + c.kind + ": " + c.a + " -> " + c.b + " (" + c.how + ")");
        } else if (page() == 3 || a == null) {
            AssetData.Cert c = AssetData.get(world).certs.isEmpty() ? null : AssetData.get(world).certs.get(0);
            if (c == null) l.add("No certificates yet");
            else { l.add("CERTIFICATE #" + c.id + " " + c.kind); l.add(c.scope + " · " + (c.pass ? "PASS" : "FAIL") + " · " + c.by); l.addAll(c.lines); }
        } else {
            AssetData.Cert c = null;
            for (AssetData.Cert x : AssetData.get(world).certs) if (x.scope.equals(a.id)) { c = x; break; }
            l.add((c == null ? "TEST SHEET " : "CERTIFICATE #" + c.id + " ") + a.id + " " + AssetData.COMM[a.comm]);
            if (c != null) l.addAll(c.lines);
            for (Commissioning.Test t : tests) if (t.asset.equals(a.id)) l.add(Commissioning.TSTATE[t.state] + "  " + t.name + (t.detail.isEmpty() ? "" : " - " + t.detail));
        }
        ItemStack book = Diagnostics.book(l.get(0), "Commissioning", l);
        if (!com.dogpound.railmap.depot.Stores.put(store(1), book)) {
            EnumFacing f = controlFacing();
            world.spawnEntity(new EntityItem(world, pos.getX() + 0.5 + f.getXOffset() * 0.7, pos.getY() + 1.1, pos.getZ() + 0.5 + f.getZOffset() * 0.7, book));
        }
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.2f, 1.0f);
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setLong("target", target); t.setInteger("sel", sel); t.setString("bench", benchLast); }
    @Override protected void loadExtra(NBTTagCompound t) { target = t.getLong("target"); sel = t.getInteger("sel"); benchLast = t.getString("bench"); }
}
