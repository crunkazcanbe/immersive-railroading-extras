package com.dogpound.railmap.biz;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

/**
 * The world + business layer's furniture (Port Berth, Stock Exchange Board, Regulator Office, Insurance Desk, Town Hall
 * Planning Board): office furniture like the Company HQ - real storages behind the drawer and cupboard fronts, a shelf
 * that shows what stands on it, a 12 V terminal for the screens and buttons - with a company selector shared by all.
 */
public abstract class BizDesk extends TileFurniture {
    protected int selCo = -1;                         // the company the desk is looking at (-1 = the placer's)
    protected boolean ch;

    protected BizDesk(String[] ids, String[] names, int[] sizes, int shelf) { super(ids, names, sizes, shelf); }

    protected BizData biz() { return BizData.get(world); }

    /** the company on the screens: the one selected, else the placer's, else the first */
    protected Company company() {
        CompanyData cd = CompanyData.get(world);
        Company c = selCo >= 0 ? cd.byId(selCo) : null;
        if (c == null && placer != null) c = cd.of(placer);
        if (c == null && !cd.all().isEmpty()) c = cd.all().get(0);
        return c;
    }

    protected String cycleCompany(int dir) {
        List<Company> all = CompanyData.get(world).all();
        if (all.isEmpty()) return "§7No railway companies yet - found one at a Company HQ";
        Company c = company();
        int i = c == null ? 0 : all.indexOf(c);
        selCo = all.get(Math.floorMod(i + dir, all.size())).id;
        return "§7Company: §f" + company().name + " (" + company().mark + ")";
    }

    /** the presser's own company (and whether they may sign for it) */
    protected static Company mine(EntityPlayer p, boolean finance) {
        if (p == null) return null;
        Company c = CompanyData.get(p.world).of(p.getUniqueID());
        return c != null && (!finance || c.can(p.getUniqueID(), r -> r.finance)) ? c : null;
    }

    protected void t(String id, String s) { ch |= StationControls.text(ctl, id, s == null ? "" : s); }
    protected void p(String id, boolean on) { ch |= StationControls.put(ctl, id, on); }
    protected void v(String id, int val) { ch |= StationControls.put(ctl, id, val); }

    @Override
    protected final boolean refresh() {
        ch = false;
        if (world != null && !world.isRemote) screens();
        return ch;
    }

    /** fill the screens and lamps with t / p / v */
    protected abstract void screens();

    @Override protected void writeExtra(NBTTagCompound t) { t.setInteger("selCo", selCo); save(t); }
    @Override protected void readExtra(NBTTagCompound t) { selCo = t.hasKey("selCo") ? t.getInteger("selCo") : -1; load(t); }
    protected void save(NBTTagCompound t) {}
    protected void load(NBTTagCompound t) {}

    protected static String spark(double[] v) {
        String bars = "_.,-~=*^";
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double x : v) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
        StringBuilder b = new StringBuilder();
        for (int i = v.length - 1; i >= 0; i--) b.append(bars.charAt(hi - lo < 1e-9 ? 3 : (int) ((v[i] - lo) * 7 / (hi - lo))));
        return b.toString();
    }
}
