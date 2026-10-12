package com.dogpound.railmap.economy;

import com.dogpound.railmap.block.TileContainerTerminal;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The economy rows the Control Center's COMPANY, MARKET and PASSENGERS tabs show, sent in PacketRailState. Each row is
 * tagged by its first field: "co" company, "px" price, "ps" station passengers, "pl" line passengers, "ct" container.
 */
public final class EconSync {
    private EconSync() {}

    private static String f0(double v) { return String.format(Locale.ROOT, "%.0f", v); }

    public static List<String[]> rows(World w) {
        List<String[]> out = new ArrayList<>();
        PassengerData pd = PassengerData.get(w);
        for (Company c : CompanyData.get(w).all()) {
            int cars = 0;
            for (UUID t : c.trains) cars += pd.cars(t);
            out.add(new String[]{"co", String.valueOf(c.id), c.name, c.mark, String.valueOf(c.members.size()), Coins.fmt(c.operating), Coins.fmt(c.maintenance),
                    Coins.fmt(c.profit(c.month)), Coins.fmt(c.profit(c.lastMonth)), String.valueOf(c.reputation), Coins.fmt(c.sharePrice(cars)),
                    String.valueOf(c.trains.size()), String.valueOf(c.lines.size()), String.valueOf(c.stations.size()), Coins.fmt(c.loan), String.valueOf(c.applicants.size()),
                    String.valueOf(c.colour), String.valueOf(c.profit(c.month))});
        }
        for (MarketData.Price p : MarketData.get(w).all()) {
            StringBuilder h = new StringBuilder();
            for (int i = 23; i >= 0; i--) h.append(i == 23 ? "" : ",").append(i == 0 ? p.price : p.hist(i - 1));
            out.add(new String[]{"px", p.key, p.name(), Coins.fmt(p.price), Coins.fmt(p.base), String.valueOf(p.change24()), Coins.fmt(p.bid()), Coins.fmt(p.ask()),
                    f0(p.supply), f0(p.demand), String.valueOf(p.volume), h.toString()});
        }
        for (PassengerData.Stn s : pd.stations()) {
            if (out.size() > 220) break;
            out.add(new String[]{"ps", s.name, String.valueOf(pd.zoneOf(s.key)), String.valueOf(s.pop), f0(s.waitingTotal()), f0(s.boardedDay), f0(s.alightedDay),
                    f0(s.sat), f0(s.punct), f0(s.crowd), f0(s.wait), f0(s.fac), f0(s.clean), f0(s.complaintsDay), Coins.fmt(s.revenueDay), f0(s.headway),
                    f0(s.leftDay), f0(s.walkedDay), f0(s.evadersDay), s.topReason(), f0(s.transfersDay)});
        }
        CompanyData cd = CompanyData.get(w);
        for (PassengerData.LineStat l : pd.lines()) {
            Company c = cd.ownerOfLine(l.name);
            out.add(new String[]{"pl", l.name, f0(l.ridersDay), f0(l.ridersYesterday), f0(l.sat), f0(l.punct), f0(l.load), Coins.fmt(l.revenueDay), Coins.fmt(l.refundsDay),
                    c == null ? "-" : c.mark, c != null && c.pso.contains(l.name) ? "PSO" : "", f0(l.delayedDay), f0(l.leftDay), Coins.fmt(l.costDay)});
        }
        // "in": every industry endpoint (any mod's plant, depot stores, fuel point, construction site) with what it makes,
        // what it is short of and what it draws from the grid (her list section 13: FREIGHT tab, PLANTS view)
        java.util.Map<Long, com.dogpound.railmap.industry.IndustryLink.Load> loads = new java.util.HashMap<>();
        for (com.dogpound.railmap.industry.IndustryLink.Load l : com.dogpound.railmap.industry.IndustryLink.loads(w)) loads.put(l.pos.toLong(), l);
        for (TileEntity te : com.dogpound.railmap.industry.IndustryLink.all(w)) {
            if (out.size() > 280) break;
            com.dogpound.railmap.industry.IndustryLink.Endpoint e = (com.dogpound.railmap.industry.IndustryLink.Endpoint) te;
            StringBuilder mk = new StringBuilder(), wn = new StringBuilder();
            for (net.minecraft.item.ItemStack s : e.makes()) if (!s.isEmpty()) mk.append(mk.length() > 0 ? ", " : "").append(s.getCount()).append(' ').append(s.getDisplayName());
            for (net.minecraftforge.fluids.FluidStack f : e.fluidsMade()) if (f != null) mk.append(mk.length() > 0 ? ", " : "").append(f.amount).append(" L ").append(f.getLocalizedName());
            for (net.minecraft.item.ItemStack s : e.wants()) if (!s.isEmpty()) wn.append(wn.length() > 0 ? ", " : "").append(s.getCount()).append(' ').append(s.getDisplayName());
            for (net.minecraftforge.fluids.FluidStack f : e.fluidsWanted()) if (f != null) wn.append(wn.length() > 0 ? ", " : "").append(f.amount).append(" L ").append(f.getLocalizedName());
            com.dogpound.railmap.industry.IndustryLink.Load ld = loads.remove(te.getPos().toLong());
            long ready = e.readyAt(), now = w.getTotalWorldTime();
            out.add(new String[]{"in", e.endpointName(), e.endpointState(), mk.toString(), wn.toString(), ld == null ? "" : f0(ld.kw), ld == null ? "" : ld.supply,
                    te.getPos().getX() + " " + te.getPos().getY() + " " + te.getPos().getZ(), ready > now ? String.valueOf((ready - now) / 20) : ""});
        }
        for (com.dogpound.railmap.industry.IndustryLink.Load ld : loads.values())                   // loads that aren't endpoints
            out.add(new String[]{"in", ld.name, ld.state, "", "", f0(ld.kw), ld.supply, ld.pos.getX() + " " + ld.pos.getY() + " " + ld.pos.getZ(), ""});
        for (TileEntity te : new ArrayList<>(w.loadedTileEntityList)) {
            if (!(te instanceof TileContainerTerminal t) || te.isInvalid()) continue;
            for (String[] b : t.boxes()) {
                if (out.size() > 300) break;
                out.add(new String[]{"ct", b[0], b[1], b[2], b[3], b[4], b[5], te.getPos().getX() + " " + te.getPos().getY() + " " + te.getPos().getZ()});
            }
        }
        return out;
    }
}
