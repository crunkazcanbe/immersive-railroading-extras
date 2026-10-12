package com.dogpound.railmap.biz;

import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * The Office of Rail Regulation (her "next 48" #34) - with the capacity register (#12). The audit board shows the
 * selected company's last audit: nine checks, each with a PASS / FAIL lamp and what was found, the score, and its
 * corrective actions with their deadlines. AUDIT runs one now; ACCEPT and APPEAL answer the selected action; the
 * STAMP prints the audit report as a paper document (file it in an Insurance Desk's evidence drawer).
 * The capacity register: pick a corridor / terminal (RES) and a window (WIN), see paths sold and bids, and BID a path
 * for your company at the PRICE shown, recurring or for a season (SEASON). Auctions close daily (CLOSE runs one now,
 * for server operators). Audit files cabinet and two drawers are storage; the binder shelf shows what stands on it.
 */
public class TileRegulatorOffice extends BizDesk {
    public static final String[] STORE_IDS = {"files", "dl", "dr"};
    public static final String[] STORE_NAMES = {"Audit Files Cabinet", "Desk Drawer", "Binder Cupboard"};
    public static final int[] STORE_SIZES = {27, 9, 9};
    private static final double[][] SPOTS = {{20.35, 7.0, 13.4}, {22.85, 7.0, 13.4}, {25.35, 7.0, 13.4}, {27.85, 7.0, 13.4}};
    @Override public double[][] shelfSpots() { return SPOTS; }
    private int caSel, resSel, win = 2, season = -1;
    private long bidPx = 300;

    public TileRegulatorOffice() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, 4); }

    private List<Regulator.Ca> cas(Company c) {
        List<Regulator.Ca> l = new ArrayList<>();
        if (c != null) for (Regulator.Ca x : biz().regulator.cas) if (x.company == c.id && !x.state.equals("CLOSED")) l.add(x);
        return l;
    }

    /** corridors (lines) and terminals (named stations) on the register */
    private List<String> resources() {
        List<String> l = new ArrayList<>();
        for (String n : com.dogpound.railmap.auto.RailwayData.get(world).lines().keySet()) l.add("C:" + n);
        for (String n : com.dogpound.railmap.server.StationData.get(world).names().values()) if (!l.contains("T:" + n)) l.add("T:" + n);
        for (String k : biz().capacity.resources.keySet()) if (!l.contains(k)) l.add(k);
        return l;
    }

    private String res() { List<String> l = resources(); return l.isEmpty() ? null : l.get(Math.floorMod(resSel, l.size())); }

    @Override
    protected String press(String id, EntityPlayer who) {
        Company c = company();
        BizData d = biz();
        switch (id) {
            case "prev": case "next": caSel = 0; return cycleCompany(id.equals("next") ? 1 : -1);
            case "caprev": caSel--; return "";
            case "canext": caSel++; return "";
            case "resprev": resSel--; return "§7Register: " + res();
            case "resnext": resSel++; return "§7Register: " + res();
            case "winprev": case "winnext": win = Math.floorMod(win + (id.equals("winnext") ? 1 : -1), Capacity.WINDOWS.length); return "§7Window: " + Capacity.WINDOWS[win];
            case "season": season = season >= 3 ? -1 : season + 1; return "§7Term: " + (season < 0 ? "RECURRING (7-day timetable)" : Capacity.SEASONS[season] + " season");
            case "pxup": case "pxdn": bidPx = Math.max(50, bidPx + (id.equals("pxup") ? 50 : -50)); return "§7Bid " + Coins.fmt(bidPx) + " a path a day";
            case "audit": {
                if (c == null) return "§7No company to audit";
                if (!BizConfig.regulator) return "§7The regulator is switched off on this server";
                Regulator.Audit a = d.regulator.audit(world, d, c);
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.7f, 1.0f);
                return "§bAudit of " + c.name + ": " + a.score + "/100";
            }
            case "stamp": {
                if (c == null || who == null) return "";
                Regulator.Audit a = d.regulator.last.get(c.id);
                if (a == null) return "§7No audit on file for " + c.mark + " - press AUDIT";
                ItemStack paper = new ItemStack(Items.PAPER);
                paper.setStackDisplayName("Audit Report " + c.mark + " " + a.score + "/100");
                NBTTagList lore = new NBTTagList();
                for (int i = 0; i < Regulator.CHECKS.length; i++) lore.appendTag(new NBTTagString((a.pass[i] ? "§aPASS " : "§cFAIL ") + Regulator.CHECKS[i] + " §7" + a.detail[i]));
                paper.getOrCreateSubCompound("display").setTag("Lore", lore);
                if (!who.inventory.addItemStackToInventory(paper)) who.dropItem(paper, false);
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.paper"), SoundCategory.BLOCKS, 0.6f, 1.0f);
                return "§aAudit report stamped and handed over";
            }
            case "close": {
                if (who != null && !who.canUseCommand(2, "irextras")) return "§cOnly a server operator closes an auction early";
                return "§bAuction closed: " + d.capacity.close(world, d) + " bids decided";
            }
            default: break;
        }
        if (!(who instanceof EntityPlayerMP p)) return null;
        switch (id) {
            case "accept": case "appeal": {
                Company m = mine(p, true);
                List<Regulator.Ca> l = cas(m);
                if (m == null || l.isEmpty()) return "§7No open corrective actions for your company";
                Regulator.Ca x = l.get(Math.floorMod(caSel, l.size()));
                return id.equals("accept") ? d.regulator.accept(world, m, x.id) : d.regulator.appeal(world, m, x.id);
            }
            case "bid": {
                Company m = mine(p, true);
                if (m == null) return "§cA company's owner or director bids for capacity";
                String r = res();
                if (r == null) return "§7No lines or stations on the register yet";
                return d.capacity.bid(world, m, r, win, 1, bidPx, season);
            }
            default: return null;
        }
    }

    @Override
    protected void screens() {
        BizData d = biz();
        Company c = company();
        Regulator.Audit a = c == null ? null : d.regulator.last.get(c.id);
        t("co", c == null ? "NO COMPANIES" : c.mark + " " + cut(c.name.toUpperCase(Locale.ROOT), 18) + (a == null ? " - NOT AUDITED" : " SCORE " + a.score));
        for (int i = 0; i < Regulator.CHECKS.length; i++) {
            p("a" + i + "l", a != null && a.pass[i]);
            p("a" + i + "f", a != null && !a.pass[i]);
            t("a" + i + "d", a == null ? "" : cut(a.detail[i].toUpperCase(Locale.ROOT), 26));
        }
        List<Regulator.Ca> l = cas(c);
        int top = Math.max(0, Math.min(Math.floorMod(caSel, Math.max(1, l.size())) - 1, l.size() - 4));
        long now = world.getTotalWorldTime();
        boolean over = false;
        for (int i = 0; i < 4; i++) {
            int k = top + i;
            Regulator.Ca x = k < l.size() ? l.get(k) : null;
            if (x != null && x.state.equals("OVERDUE")) over = true;
            boolean on = x != null && k == Math.floorMod(caSel, l.size());
            t("ca" + (i + 1), x == null ? "" : (on ? ">" : " ") + "CA" + x.id + " " + Regulator.CHECKS[x.check] + " " + x.state
                    + (x.state.equals("OVERDUE") ? "" : " " + Math.max(0, (x.deadline - now) / 1000) + "H"));
        }
        // capacity register
        String r = res();
        Capacity.Resource rs = r == null ? null : d.capacity.resources.get(r);
        int paths = rs == null ? BizConfig.defaultPaths : rs.paths, season = Capacity.season(world);
        t("cr1", r == null ? "NO LINES" : cut(r.toUpperCase(Locale.ROOT), 22) + " " + Capacity.WINDOWS[win]);
        t("cr2", r == null ? "" : "SOLD " + d.capacity.pathsHeld(r, win, season, -1) + "/" + paths + " PATHS  RES " + Coins.fmt(rs == null ? 200 : rs.reserve));
        int bids = 0;
        for (Capacity.Bid b : d.capacity.bids) if (b.res.equals(r) && b.window == win && b.state.equals("OPEN")) bids++;
        t("cr3", bids + " SEALED BIDS  NOW " + Capacity.SEASONS[season]);
        t("cr4", "MY BID " + Coins.fmt(bidPx) + " " + (this.season < 0 ? "RECURRING" : Capacity.SEASONS[this.season]));
        t("cr5", d.capacity.decisions.isEmpty() ? "" : cut(d.capacity.decisions.get(0).toUpperCase(Locale.ROOT), 34));
        p("pw", term.powered());
        p("due", c != null && (a == null || now - a.when >= BizConfig.auditEveryDays * BizData.DAY));
        p("over", over);
        p("clean", a != null && a.score == 100);
        p("auct", bids > 0);
        p("off", !BizConfig.regulator);
        v("scoreg", a == null ? 0 : a.score);
    }

    @Override public String wiredName() { return "Regulator Office"; }
    @Override public String gearState() { Company c = company(); return c == null ? "no companies" : c.mark + " last score " + biz().regulator.lastScore(c.id); }
    @Override protected void save(NBTTagCompound t) { t.setInteger("ca", caSel); t.setInteger("res", resSel); t.setInteger("win", win); t.setInteger("season", season); t.setLong("px", bidPx); }
    @Override protected void load(NBTTagCompound t) { caSel = t.getInteger("ca"); resSel = t.getInteger("res"); win = t.hasKey("win") ? t.getInteger("win") : 2; season = t.hasKey("season") ? t.getInteger("season") : -1; bidPx = t.hasKey("px") ? t.getLong("px") : 300; }
}
