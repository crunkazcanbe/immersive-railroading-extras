package com.dogpound.railmap.biz;

import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * The Insurance & Claims Desk (her "next 48" #33). A loss adjuster's roll-top desk: the policy screen shows the selected
 * company's cover, excess, premium a day and its maintenance compliance (from the depot's exam and brake-test records),
 * the claims list and the selected claim with the adjuster's evidence notes. COVER and EXCESS pick a policy, QUOTE prices
 * it, SIGN takes it out (directors only). ASSESS settles the selected filed claim now. The EVIDENCE drawer is real
 * storage and really counts: every document filed in it (paper, books, waybills - an audit report from the Regulator's
 * stamp, a recorder print-out) adds to the evidence score of its company's claims. ENDORSE hands over an insurance
 * certificate. Policy files cupboard and two drawers are storage too; the shelf shows what stands on it.
 */
public class TileInsuranceDesk extends BizDesk {
    public static final String[] STORE_IDS = {"evid", "pol", "dl", "dr"};
    public static final String[] STORE_NAMES = {"Evidence Drawer", "Policy Files Cupboard", "Desk Drawer (left)", "Desk Drawer (right)"};
    public static final int[] STORE_SIZES = {9, 27, 9, 9};
    private static final double[][] SPOTS = {{20.35, 7.0, 13.4}, {22.85, 7.0, 13.4}, {25.35, 7.0, 13.4}, {27.85, 7.0, 13.4}};
    @Override public double[][] shelfSpots() { return SPOTS; }
    private int cover = 1, excess = 1, clSel;

    public TileInsuranceDesk() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, 4); }

    /** documents filed in the evidence drawer */
    public int docs() {
        IInventory inv = storage(0);
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            String id = s.getItem().getRegistryName() == null ? "" : s.getItem().getRegistryName().getPath();
            if (s.getItem() == Items.PAPER || s.getItem() == Items.WRITTEN_BOOK || s.getItem() == Items.WRITABLE_BOOK || s.getItem() == Items.FILLED_MAP || id.contains("waybill") || id.contains("ticket"))
                n += s.hasDisplayName() || s.getItem() != Items.PAPER ? 1 : 0;   // blank paper isn't evidence; a named report is
        }
        return n;
    }

    private List<Insurance.Claim> claims(Company c) {
        List<Insurance.Claim> l = new ArrayList<>();
        if (c != null) for (Insurance.Claim k : biz().insurance.claims) if (k.company == c.id) l.add(k);
        Collections.reverse(l);
        return l;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        BizData d = biz();
        Company c = company();
        switch (id) {
            case "prev": case "next": clSel = 0; return cycleCompany(id.equals("next") ? 1 : -1);
            case "cover": cover = (cover + 1) % Insurance.Cover.values().length; return "§7Cover: " + Insurance.Cover.values()[cover].name().replace('_', ' ');
            case "excess": excess = (excess + 1) % Insurance.EXCESS.length; return "§7Excess: " + Coins.fmt(Insurance.EXCESS[excess]);
            case "quote": {
                if (c == null) return "§7No company selected";
                long q = d.insurance.quote(world, d, c, Insurance.Cover.values()[cover], excess);
                Insurance.Policy p = d.insurance.policy(c.id);
                return "§bQuote for " + c.mark + ": " + Insurance.Cover.values()[cover].name().replace('_', ' ') + ", excess " + Coins.fmt(Insurance.EXCESS[excess]) + " - " + Coins.fmt(q) + " a day"
                        + String.format(Locale.ROOT, " §7(compliance %.0f%%: %d of %d units overdue, %d failed brake tests)", p.compliance, p.overdue, p.units, p.brakeFails);
            }
            case "cprev": clSel--; return "";
            case "cnext": clSel++; return "";
            case "assess": {
                List<Insurance.Claim> l = claims(c);
                if (l.isEmpty()) return "§7No claims for " + (c == null ? "-" : c.mark);
                Insurance.Claim k = l.get(Math.floorMod(clSel, l.size()));
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.7f, 1.0f);
                return d.insurance.settle(world, d, k);
            }
            case "endorse": {
                if (c == null || who == null) return "";
                Insurance.Policy p = d.insurance.policy(c.id);
                if (p.cover == Insurance.Cover.NONE) return "§7" + c.mark + " holds no policy";
                ItemStack cert = new ItemStack(Items.PAPER);
                cert.setStackDisplayName("Certificate of Insurance " + c.mark);
                NBTTagList lore = new NBTTagList();
                lore.appendTag(new NBTTagString("§7" + c.name + " - " + p.cover.name().replace('_', ' ')));
                lore.appendTag(new NBTTagString("§7Excess " + Coins.fmt(Insurance.EXCESS[p.excess]) + ", premium " + Coins.fmt(p.premiumDay) + "/day"));
                lore.appendTag(new NBTTagString("§8" + com.dogpound.railmap.depot.DepotClock.stamp(world)));
                cert.getOrCreateSubCompound("display").setTag("Lore", lore);
                if (!who.inventory.addItemStackToInventory(cert)) who.dropItem(cert, false);
                return "§aCertificate of insurance endorsed and handed over";
            }
            default: break;
        }
        if (!(who instanceof EntityPlayerMP p)) return null;
        if (id.equals("sign")) {
            Company m = mine(p, true);
            if (m == null) return "§cA policy is signed by a company's owner or director";
            selCo = m.id;
            return d.insurance.take(world, d, m, Insurance.Cover.values()[cover], excess);
        }
        return null;
    }

    @Override
    protected void screens() {
        BizData d = biz();
        Company c = company();
        Insurance.Policy p = c == null ? null : d.insurance.policy(c.id);
        Company owner = placer == null ? null : com.dogpound.railmap.economy.CompanyData.get(world).of(placer);
        if (owner != null && age % 100 == 3) d.insurance.policy(owner.id).evidenceDocs = docs();
        if (c != null && p != null && age % 200 == 3) d.insurance.survey(world, c, p);
        t("co", c == null ? "NO COMPANIES" : c.mark + " " + cut(c.name.toUpperCase(Locale.ROOT), 20));
        t("cover", p == null ? "" : p.cover.name().replace('_', ' ') + "  EXCESS " + Coins.fmt(Insurance.EXCESS[p.excess]));
        t("prem", p == null ? "" : "PREMIUM " + Coins.fmt(p.premiumDay) + "/DAY  PAID " + Coins.fmt(p.paidTotal));
        t("comp", p == null ? "" : String.format(Locale.ROOT, "COMPLIANCE %.0f%%  %d/%d OVERDUE", p.compliance, p.overdue, p.units));
        t("sel", "QUOTE: " + Insurance.Cover.values()[cover].name().replace('_', ' ') + " " + Coins.fmt(Insurance.EXCESS[excess]));
        List<Insurance.Claim> l = claims(c);
        int top = Math.max(0, Math.min(Math.floorMod(clSel, Math.max(1, l.size())) - 1, l.size() - 4));
        int filed = 0;
        for (Insurance.Claim k : l) if (k.state.equals("FILED")) filed++;
        for (int i = 0; i < 4; i++) {
            int k = top + i;
            Insurance.Claim x = k < l.size() ? l.get(k) : null;
            boolean on = x != null && k == Math.floorMod(clSel, l.size());
            t("cl" + (i + 1), x == null ? "" : (on ? ">" : " ") + "C" + x.id + " " + x.state + " " + Coins.fmt(x.paid) + " " + cut(x.what.toUpperCase(Locale.ROOT), 16));
        }
        Insurance.Claim s = l.isEmpty() ? null : l.get(Math.floorMod(clSel, l.size()));
        t("m1", s == null ? "NO CLAIMS ON FILE" : "C" + s.id + " " + cut(s.what.toUpperCase(Locale.ROOT), 28));
        t("m2", s == null ? "" : "VALUE " + Coins.fmt(s.value) + "  LIAB " + Coins.fmt(s.liability));
        t("m3", s == null ? "" : "EVIDENCE " + s.evidence + "/100  PAID " + Coins.fmt(s.paid));
        t("m4", s == null ? "" : cut(s.notes.toUpperCase(Locale.ROOT), 36));
        t("docs", docs() + " DOCS FILED");
        p("pw", term.powered());
        p("ins", p != null && p.cover != Insurance.Cover.NONE);
        p("lapse", c != null && p != null && p.cover == Insurance.Cover.NONE && !c.lines.isEmpty());
        p("neg", p != null && p.overdue > 0 && p.brakeFails > 0);
        p("filed", filed > 0);
        v("compg", p == null ? 0 : (int) Math.round(p.compliance));
    }

    @Override public String wiredName() { return "Insurance Desk"; }
    @Override public String gearState() { Company c = company(); return c == null ? "no companies" : c.mark + " " + biz().insurance.policy(c.id).cover.name(); }
    @Override protected void save(NBTTagCompound t) { t.setInteger("cover", cover); t.setInteger("excess", excess); t.setInteger("cl", clSel); }
    @Override protected void load(NBTTagCompound t) { cover = t.hasKey("cover") ? t.getInteger("cover") : 1; excess = t.hasKey("excess") ? t.getInteger("excess") : 1; clSel = t.getInteger("cl"); }
}
