package com.dogpound.railmap.admin;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.ctl.CtlConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Operational permissions and approval workflows (her "next 48" list #43): REQUEST -> APPROVE -> EXECUTE, with dual
 * authorisation, approvals that expire unused, rejection, and an audit trail of every step (also in the API audit log).
 * <ul>
 *   <li>what needs approval: issuing a permit to work (CtlConfig.approvalsForPermits), taking off a lockout padlock
 *       (approvalsForPadlocks), and any device an administrator puts under approval control (/irextras ctl approve
 *       require x y z [dual]) - its operations through IR Extras' own gates (filter banks, microgrid resync, breakers
 *       closed by the controllers, chaos runs);</li>
 *   <li>trying the operation files the request by itself (or /irextras ctl approve request ...); someone ELSE approves it
 *       - at an Approval Panel (two different people turning the two keys within 10 s is the dual authorisation), on
 *       the console, or by command; the requester then does the operation, which consumes the approval;</li>
 *   <li>approvals expire after {@link CtlConfig#approvalMinutes}; requests nobody acts on expire after an hour.</li>
 * </ul>
 */
public final class Approvals extends WorldSavedData {
    private static final String NAME = "irextras_approvals";
    public enum State { REQUESTED, APPROVED, EXECUTED, REJECTED, EXPIRED, CANCELLED }

    public static final class Req {
        public int id;
        public String action = "", detail = "", requester = "";
        public long pos, created, approvedAt, expires;
        public boolean dual;
        public State state = State.REQUESTED;
        public final List<String> approvers = new ArrayList<>();
        public String closedBy = "";
        public int needed() { return dual ? 2 : 1; }
        public String where() { BlockPos b = BlockPos.fromLong(pos); return pos == 0 ? "-" : b.getX() + "," + b.getY() + "," + b.getZ(); }
    }

    public final List<Req> reqs = new ArrayList<>();
    /** devices under approval control: pos -> dual (true = two approvers) */
    public final Map<Long, Boolean> required = new LinkedHashMap<>();
    public final List<String> trail = new ArrayList<>();
    private int nextId = 1;

    public Approvals() { super(NAME); }
    public Approvals(String n) { super(n); }

    public static Approvals get(World w) {
        MapStorage st = w.getPerWorldStorage();
        Approvals a = (Approvals) st.getOrLoadData(Approvals.class, NAME);
        if (a == null) { a = new Approvals(); st.setData(NAME, a); }
        return a;
    }

    private void audit(World w, String who, Req r, String step, String result) {
        String line = Recorder.clock(w, w.getTotalWorldTime()) + " #" + r.id + " " + step + " by " + who + " - " + r.action + " @ " + r.where() + (result.isEmpty() ? "" : " (" + result + ")");
        trail.add(0, line);
        while (trail.size() > 200) trail.remove(trail.size() - 1);
        com.dogpound.railmap.api.Audit.log(w, com.dogpound.railmap.api.Audit.Src.PLAYER, who, r.pos == 0 ? null : BlockPos.fromLong(r.pos), "approval #" + r.id + " " + r.action, step, "", r.state.name(), result.isEmpty() ? "ok" : result);
        Recorder.event(w, "APPROVAL " + line);
        markDirty();
    }

    public Req byId(int id) { for (Req r : reqs) if (r.id == id) return r; return null; }

    public Req request(World w, String action, BlockPos at, String detail, String who, boolean dual) {
        for (Req r : reqs) if (r.state == State.REQUESTED && r.action.equals(action) && r.pos == (at == null ? 0 : at.toLong()) && r.requester.equals(who)) return r;
        Req r = new Req();
        r.id = nextId++;
        r.action = action; r.pos = at == null ? 0 : at.toLong(); r.detail = detail; r.requester = who; r.dual = dual;
        r.created = w.getTotalWorldTime();
        reqs.add(r);
        while (reqs.size() > 150) reqs.remove(0);
        audit(w, who, r, "REQUEST", detail);
        return r;
    }

    /** an approval by someone other than the requester; dual requests need two different approvers */
    public String approve(World w, int id, String who) {
        Req r = byId(id);
        if (r == null) return "§cNo request #" + id;
        if (r.state != State.REQUESTED) return "§7#" + id + " is " + r.state;
        if (r.requester.equals(who)) return "§cYou can't approve your own request (#" + id + ")";
        if (r.approvers.contains(who)) return "§7You already approved #" + id + (r.dual ? " - a SECOND person must approve it too" : "");
        r.approvers.add(who);
        if (r.approvers.size() >= r.needed()) {
            r.state = State.APPROVED;
            r.approvedAt = w.getTotalWorldTime();
            r.expires = r.approvedAt + CtlConfig.approvalMinutes * 1200L;
        }
        audit(w, who, r, r.state == State.APPROVED ? "APPROVE" : "APPROVE 1/2", "");
        return r.state == State.APPROVED ? "§aRequest #" + id + " APPROVED - " + r.requester + " may now do it (valid " + CtlConfig.approvalMinutes + " min)" : "§eFirst approval on #" + id + " - dual authorisation: a second person must approve";
    }

    public String reject(World w, int id, String who) {
        Req r = byId(id);
        if (r == null || r.state != State.REQUESTED && r.state != State.APPROVED) return "§cNothing to reject";
        r.state = State.REJECTED; r.closedBy = who;
        audit(w, who, r, "REJECT", "");
        return "§6Request #" + id + " rejected";
    }

    public String cancel(World w, int id, String who) {
        Req r = byId(id);
        if (r == null || r.state != State.REQUESTED && r.state != State.APPROVED) return "§cNothing to cancel";
        if (!r.requester.equals(who)) return "§cOnly the requester can cancel";
        r.state = State.CANCELLED; r.closedBy = who;
        audit(w, who, r, "CANCEL", "");
        return "§7Request #" + id + " cancelled";
    }

    public void tick(World w) {
        long now = w.getTotalWorldTime();
        for (Req r : reqs) {
            if (r.state == State.APPROVED && now > r.expires) { r.state = State.EXPIRED; audit(w, "system", r, "EXPIRE", "approval not used in time"); }
            else if (r.state == State.REQUESTED && now - r.created > 72000) { r.state = State.EXPIRED; audit(w, "system", r, "EXPIRE", "nobody approved it within an hour"); }
        }
    }

    /** does this operation need approval here? */
    public static boolean needs(World w, String action, BlockPos at) {
        if (action.equals("permit") && CtlConfig.approvalsForPermits) return true;
        if (action.equals("padlock") && CtlConfig.approvalsForPadlocks) return true;
        return at != null && get(w).required.containsKey(at.toLong());
    }

    /**
     * The gate: null = go ahead (no approval needed, or an approval was used up now - EXECUTED); otherwise the refusal,
     * and the request is filed automatically so the operator's attempt starts the workflow.
     */
    public static String blocked(World w, String action, BlockPos at, EntityPlayer who) {
        if (w == null || w.isRemote || !needs(w, action, at)) return null;
        Approvals a = get(w);
        a.tick(w);
        String name = who == null ? "console" : who.getName();
        long p = at == null ? 0 : at.toLong();
        for (Req r : a.reqs) {
            if (r.state != State.APPROVED || !r.action.equals(action) || r.pos != p) continue;
            if (!r.requester.equals(name) && who != null) continue;
            r.state = State.EXECUTED; r.closedBy = name;
            a.audit(w, name, r, "EXECUTE", "");
            return null;
        }
        boolean dual = action.equals("permit") || a.required.getOrDefault(p, false);
        Req r = a.request(w, action, at, action + " at " + (at == null ? "-" : at.getX() + "," + at.getY() + "," + at.getZ()), name, dual);
        return "§c⛔ Needs approval: request #" + r.id + " filed (" + r.approvers.size() + "/" + r.needed() + " approvals" + (dual ? ", DUAL authorisation" : "") + ") - approve at an Approval Panel, the console or /irextras ctl approve " + r.id;
    }

    public List<Req> open() { List<Req> l = new ArrayList<>(); for (Req r : reqs) if (r.state == State.REQUESTED || r.state == State.APPROVED) l.add(r); return l; }

    // ---- save ---------------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        reqs.clear(); required.clear(); trail.clear();
        nextId = Math.max(1, t.getInteger("next"));
        NBTTagList l = t.getTagList("r", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Req r = new Req();
            r.id = c.getInteger("id"); r.action = c.getString("a"); r.detail = c.getString("d"); r.requester = c.getString("by");
            r.pos = c.getLong("p"); r.created = c.getLong("c"); r.approvedAt = c.getLong("ap"); r.expires = c.getLong("x"); r.dual = c.getBoolean("dual"); r.closedBy = c.getString("cb");
            try { r.state = State.valueOf(c.getString("s")); } catch (RuntimeException e) { r.state = State.EXPIRED; }
            NBTTagList ap = c.getTagList("apv", 8);
            for (int k = 0; k < ap.tagCount(); k++) r.approvers.add(ap.getStringTagAt(k));
            reqs.add(r);
        }
        NBTTagCompound rq = t.getCompoundTag("req");
        for (String k : rq.getKeySet()) try { required.put(Long.parseLong(k), rq.getBoolean(k)); } catch (NumberFormatException ignored) { }
        NBTTagList tr = t.getTagList("trail", 8);
        for (int i = 0; i < tr.tagCount(); i++) trail.add(tr.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("next", nextId);
        NBTTagList l = new NBTTagList();
        for (Req r : reqs) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", r.id); c.setString("a", r.action); c.setString("d", r.detail); c.setString("by", r.requester);
            c.setLong("p", r.pos); c.setLong("c", r.created); c.setLong("ap", r.approvedAt); c.setLong("x", r.expires); c.setBoolean("dual", r.dual); c.setString("cb", r.closedBy);
            c.setString("s", r.state.name());
            NBTTagList ap = new NBTTagList();
            for (String s : r.approvers) ap.appendTag(new NBTTagString(s));
            c.setTag("apv", ap);
            l.appendTag(c);
        }
        t.setTag("r", l);
        NBTTagCompound rq = new NBTTagCompound();
        for (Map.Entry<Long, Boolean> e : required.entrySet()) rq.setBoolean(String.valueOf(e.getKey()), e.getValue());
        t.setTag("req", rq);
        NBTTagList tr = new NBTTagList();
        for (String s : trail) tr.appendTag(new NBTTagString(s));
        t.setTag("trail", tr);
        return t;
    }
}
