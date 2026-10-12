package com.dogpound.railmap.mail;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.interior.Interior;
import com.dogpound.railmap.interior.InteriorData;
import com.dogpound.railmap.interior.InteriorServer;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * The TPO - travelling post office car (batch 5 #8): an IR passenger car fitted with the TPO interior (Interior.Layout
 * TPO: sorting frames with pigeonholes, sorting table, mail-bag racks, a late-posting box and the sorters' desk) or any
 * car whose name says mail / post / TPO / RPO. A driverless train with one sorts loose letters en route (Mail.trains).
 * Here: the car's live state for the interior (lit pigeonholes per destination, bags on the rack, the desk screen),
 * the desk's buttons, and the late-posting box (letters dropped in it are franked LATE and sorted on the train).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class MailTpo {
    private MailTpo() {}

    /** the TPO interior's frame holes (24 per frame, up to 4 frames) */
    public static final int HOLES_PER_FRAME = 24;

    public static boolean byName(String n) { return n.matches(".*(\\btpo\\b|\\brpo\\b|mail|post office|postal|royal post|sorting van).*"); }

    public static boolean isTpo(World w, EntityRollingStock s) {
        try {
            InteriorData.Car c = InteriorData.get(w).peek(s.getUUID());
            if (InteriorServer.layoutOf(s, c) == Interior.Layout.TPO) return true;
            return byName((s.getDefinition().name() + " " + s.getDefinitionID()).toLowerCase(Locale.ROOT));
        } catch (RuntimeException | LinkageError e) { return false; }
    }

    /** the driverless train this car runs in (its head loco), or null */
    static UUID trainOf(World w, EntityRollingStock s) {
        try {
            EntityRollingStock h = com.dogpound.railmap.signs.SignServer.head(s);
            return h != null && RailwayData.get(w).trains().containsKey(h.getUUID()) ? h.getUUID() : null;
        } catch (RuntimeException | LinkageError e) { return null; }
    }

    /** destination towns of this train's line in name order: the frame's pigeonholes */
    static List<String> holeTowns(World w, UUID train) {
        List<String> o = new ArrayList<>();
        RailwayData rd = RailwayData.get(w);
        RailwayData.AutoTrain a = train == null ? null : rd.trains().get(train);
        RailwayData.Line l = a == null ? null : rd.line(a.line);
        if (l == null) return o;
        for (long k : l.stations) { String n = Autopilot.nameOf(com.dogpound.railmap.server.StationData.get(w), k); if (!o.contains(n)) o.add(n); }
        Collections.sort(o);
        return o;
    }

    /** InteriorServer: the TPO's lamps + screen for the client */
    public static void state(World w, EntityRollingStock s, Map<String, Integer> v, Map<String, String> t) {
        UUID tr = trainOf(w, s);
        MailData d = MailData.get(w);
        long now = w.getTotalWorldTime();
        InteriorData.Car c = InteriorData.get(w).peek(s.getUUID());
        boolean sorting = c != null && c.is("tposort");
        int loose = 0, sorted = 0, bags = 0;
        List<String> towns = holeTowns(w, tr);
        Map<String, Integer> next = new TreeMap<>();
        if (tr != null) {
            for (MailData.Mail m : d.mail.values()) {
                if (!m.loose || !tr.equals(m.train)) continue;
                boolean done = sorting && now >= m.sortAt;
                if (done) { sorted++; int h = towns.indexOf(m.destName); if (h >= 0) v.put("tpoh" + Math.min(h, 4 * HOLES_PER_FRAME - 1), 1); }
                else loose++;
                next.merge(m.destName, 1, Integer::sum);
            }
            for (MailData.Bag b : d.bags.values()) if (b.state == 1 && tr.equals(b.train)) v.put("tpob" + Math.min(7, bags++), 1);
        }
        if (loose > 0) v.put("tpotray", 1);
        if (sorting) v.put("tposort", 1);
        if (tr != null) v.put("tpoon", 1);
        StringBuilder b = new StringBuilder();
        b.append(tr == null ? "TPO - NOT IN SERVICE" : "TPO " + (sorting ? "SORTING" : "SORTERS STOOD DOWN")).append('\n');
        b.append(String.format(Locale.ROOT, "LOOSE %d  SORTED %d  BAGS %d", loose, sorted, bags)).append('\n');
        int i = 0;
        StringBuilder l3 = new StringBuilder();
        for (Map.Entry<String, Integer> e : next.entrySet()) { if (i++ >= 3) break; l3.append(StnUtil.up(StnUtil.cut(e.getKey(), 8))).append(' ').append(e.getValue()).append("  "); }
        b.append(l3.length() == 0 ? "NO LETTERS ON BOARD" : l3.toString().trim()).append('\n');
        Autopilot.Run r = tr == null ? null : Autopilot.run(tr);
        b.append(r == null || r.nextStopName == null || r.nextStopName.isEmpty() ? "NEXT DROP -" : "NEXT DROP " + StnUtil.up(r.nextStopName));
        t.put("tposcr", b.toString());
        for (int f = 0; f < 4; f++) {
            int a = f * HOLES_PER_FRAME;
            t.put("tpof" + f, a < towns.size() ? StnUtil.up(StnUtil.cut(towns.get(a), 6)) + "-" + StnUtil.up(StnUtil.cut(towns.get(Math.min(towns.size(), a + HOLES_PER_FRAME) - 1), 6)) : "FRAME " + (f + 1));
        }
    }

    /** the sorters' desk buttons (InteriorServer's crew panel hands them over); null = not ours */
    public static String panel(EntityPlayer p, EntityRollingStock s, InteriorData.Car c, String btn) {
        World w = p.world;
        switch (btn) {
            case "tposort" -> {
                boolean on = c.toggle("tposort");
                w.playSound(null, p.getPosition(), com.dogpound.railmap.ModSounds.KEY, SoundCategory.BLOCKS, 0.5f, 1f);
                return on ? "§aSorters on duty: loose letters are sorted en route (" + MailConfig.tpoSortSeconds + " s each)" : "§eSorters stood down - loose letters wait in the tray";
            }
            case "tpoprint" -> {
                UUID tr = trainOf(w, s);
                List<String> out = new ArrayList<>();
                out.add("TPO MANIFEST");
                MailData d = MailData.get(w);
                if (tr != null) {
                    for (MailData.Bag b : d.bags.values()) if (b.state == 1 && tr.equals(b.train)) out.add("BAG " + b.seal + " " + b.fromName + ">" + b.toName + " (" + b.items.size() + ")");
                    for (MailData.Mail m : d.mail.values()) if (m.loose && tr.equals(m.train)) out.add("#" + m.id + " LOOSE for " + m.destName + " (" + m.to + ")");
                }
                if (out.size() == 1) out.add("Nothing on board");
                StnUtil.print(w, p.getPosition(), p.getHorizontalFacing().getOpposite(), "TPO manifest", "TPO", out);
                return "§aTPO manifest printed";
            }
            default -> { return null; }
        }
    }

    // ---- the late-posting box --------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.getTotalWorldTime() % 40 != 21) return;
        try { latePosting(e.world); } catch (RuntimeException | LinkageError ex) { RailMap.LOG.debug("TPO late posting: {}", ex.toString()); }
    }

    static void latePosting(World w) {
        InteriorData idata = InteriorData.get(w);
        MailData d = MailData.get(w);
        long now = w.getTotalWorldTime();
        for (InteriorData.Car c : new ArrayList<>(idata.cars.values())) {
            if (c.layout != Interior.Layout.TPO.ordinal()) continue;
            EntityRollingStock s = InteriorServer.byUuid(w, c.id);
            if (s == null) continue;
            Interior.Plan plan = Interior.plan(s, Interior.Layout.TPO);
            if (plan == null) continue;
            Interior.Part box = null;
            for (Interior.Part pt : plan.parts) if (pt.name.equals("Late posting box")) box = pt;
            InteriorData.Store st = box == null ? null : c.store(box);
            UUID tr = trainOf(w, s);
            if (st == null || tr == null) continue;
            Autopilot.Run r = Autopilot.run(tr);
            Map.Entry<Long, String> at = r == null ? null : r.dwelling ? new AbstractMap.SimpleEntry<>(r.dwellStation, Autopilot.nameOf(com.dogpound.railmap.server.StationData.get(w), r.dwellStation)) : Mail.stationNamed(w, r.nextStopName);
            if (at == null) continue;
            RailwayData.Line ln = RailwayData.get(w).line(RailwayData.get(w).trains().get(tr).line);
            for (int i = 0; i < st.getSizeInventory(); i++) {
                ItemStack it = st.getStackInSlot(i);
                if (!ItemMail.isMail(it) || ItemMail.to(it).isEmpty() || ItemMail.mailId(it) != 0) continue;
                MailData.Mail m = d.newMail(now);
                m.kind = ItemMail.is(it, ItemMail.PARCEL) ? 1 : 0;
                m.from = ItemMail.from(it); m.to = ItemMail.to(it);
                m.fromKey = at.getKey(); m.fromName = at.getValue();
                String how = Mail.resolve(w, m, at.getKey(), at.getValue());
                ItemStack cp = it.copy();
                ItemMail.tag(cp).setInteger("mid", m.id);
                ItemMail.tag(cp).setString("pm", "TPO " + StnUtil.up(at.getValue()) + " " + Recorder.clock(w, now) + " LATE FEE");
                m.stack = cp;
                st.setInventorySlotContents(i, ItemStack.EMPTY);
                if (ln != null && ln.stations.contains(m.destKey) && m.destKey != at.getKey()) {
                    m.state = MailData.ON_TRAIN; m.loose = true; m.train = tr; m.trainName = RailwayData.get(w).trains().get(tr).label;
                    m.sortAt = now + 20L * MailConfig.tpoSortSeconds;
                    d.track(m, now, "late-posted on the TPO near " + at.getValue() + " - " + how);
                } else {                                                            // not on this line: put off at the next sorting office
                    m.state = MailData.POSTED; m.office = Mail.officeOf(w, at.getKey());
                    d.track(m, now, "late-posted on the TPO, put off for " + at.getValue() + " sorting office (not on this line) - " + how);
                }
                d.note(Recorder.clock(w, now) + " late posting on the TPO: #" + m.id + " for " + m.to);
            }
        }
    }
}
