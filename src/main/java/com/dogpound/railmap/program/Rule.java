package com.dogpound.railmap.program;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * One line of a signal-box program: WHEN condition [AND condition] THEN action [ELSE action].
 * Plain cards, no code: every field is a choice, a number or a place picked on the map.
 */
public final class Rule {
    public enum Cond {
        ALWAYS("Always"), CHANNEL_ON("Channel is ON"), CHANNEL_OFF("Channel is OFF"),
        OCCUPIED("Track circuit occupied"), CLEAR("Track circuit clear"),
        TRAIN_NEAR("A train is near"), TRAIN_STOPPED("A train is stopped near"),
        SIGNAL_RED("Signal shows Stop"), SIGNAL_PROCEED("Signal shows a proceed"),
        SWITCH_STRAIGHT("Switch is Straight"), SWITCH_TURN("Switch is Turn"),
        REDSTONE("Redstone is powered at"), DAYTIME("It is daytime"), NIGHT("It is night"),
        ROUTE_SET("A route is set from signal"),
        // her ChatGPT list #98: variables, timers, event subscriptions, any mod's trains
        EXPR("Expression is true"), EVENT("Event happened"), TIMER_DONE("Timer ran out"), VEHICLE_NEAR("Any vehicle near (all mods)");
        public final String label;
        Cond(String l) { label = l; }
        public boolean needsPos() { return this != ALWAYS && this != CHANNEL_ON && this != CHANNEL_OFF && this != DAYTIME && this != NIGHT && !needsText(); }
        public boolean needsChannel() { return this == CHANNEL_ON || this == CHANNEL_OFF; }
        public boolean needsRadius() { return this == TRAIN_NEAR || this == TRAIN_STOPPED || this == VEHICLE_NEAR; }
        public boolean needsText() { return this == EXPR || this == EVENT || this == TIMER_DONE; }
        public String textHint() { return this == EXPR ? "e.g. trains >= 3 && ch5, @x,y,z.kw > 500" : this == EVENT ? "e.g. alarm, arrive:Harbor, channel:5:on, logic:*" : "timer name"; }
    }

    public enum Act {
        NOTHING("Do nothing"), SIGNAL_STOP("Hold signal at Stop"), SIGNAL_RESTRICTING("Signal: Restricting"),
        SIGNAL_APPROACH("Signal: Approach"), SIGNAL_CLEAR("Signal: Clear"), SIGNAL_AUTO("Give signal back to the track"),
        SWITCH_STRAIGHT("Throw switch Straight"), SWITCH_TURN("Throw switch Turn"), SWITCH_AUTO("Switch back to automatic"),
        CHANNEL_ON("Turn channel ON"), CHANNEL_OFF("Turn channel OFF"),
        ROUTE("Set route from signal to signal"), CANCEL_ROUTE("Cancel route from signal"),
        ANNOUNCE("Announce in chat"), ALARM("Raise an alarm"),
        // her ChatGPT list #98: variables, timers, reusable routines, events, machines
        SET_VAR("Set variable (x = expr)"), TIMER_START("Start timer (name seconds)"), TIMER_STOP("Stop timer"),
        CALL("Run routine"), EMIT("Send event"), PRESS("Press a machine control");
        public final String label;
        Act(String l) { label = l; }
        public boolean needsPos() { return this.name().startsWith("SIGNAL") || this.name().startsWith("SWITCH") || this == ROUTE || this == CANCEL_ROUTE || this == ANNOUNCE || this == PRESS; }
        public boolean needsPos2() { return this == ROUTE; }
        public boolean needsChannel() { return this == CHANNEL_ON || this == CHANNEL_OFF; }
        public boolean needsText() { return this == ANNOUNCE || this == ALARM || this.ordinal() >= SET_VAR.ordinal(); }
        public String textLabel() { return this == SET_VAR ? "x = expr" : this == TIMER_START ? "Timer + secs" : this == TIMER_STOP ? "Timer" : this == CALL ? "Routine" : this == EMIT ? "Event" : this == PRESS ? "Control id" : "Message"; }
    }

    public int id;
    public String name = "";
    public boolean enabled = true;
    public Cond cond = Cond.ALWAYS, cond2 = null;
    public long condPos, cond2Pos;
    public int condChannel = 1, cond2Channel = 1, radius = 16;
    public Act act = Act.NOTHING, elseAct = Act.NOTHING;
    public long actPos, actPos2, elsePos, elsePos2;
    public int actChannel = 1, elseChannel = 1;
    public String text = "", elseText = "";
    /** the routine this rule belongs to ("" = the main program, run every pass; named = only when a CALL runs it) */
    public String group = "";
    /** expression / event name / timer name for the text conditions (#98) */
    public String condText = "", cond2Text = "";
    /** last evaluated result (server), so edge-only actions fire once per change */
    public transient int last = -1;
    // ---- next-48 #41: the visual automation designer - priority, error handling, history ----------------------------
    /** higher runs first in its pass (rules of equal priority keep their order) */
    public int priority;
    /** ON ERROR: what to do when this rule's condition or action fails (ALARM / EMIT / CALL / SET_VAR / ANNOUNCE...) */
    public Act errAct = Act.NOTHING;
    public String errText = "";
    /** the last firings: "tick|THEN|result" / "tick|ELSE|..." / "tick|ERROR|why", newest first (kept in the save) */
    public final java.util.List<String> history = new java.util.ArrayList<>();
    public int fired, errors;

    public void remember(long tick, String what) {
        history.add(0, tick + "|" + what);
        while (history.size() > 8) history.remove(history.size() - 1);
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("id", id); t.setString("name", name); t.setBoolean("on", enabled);
        t.setString("c", cond.name()); if (cond2 != null) t.setString("c2", cond2.name());
        t.setLong("cp", condPos); t.setLong("c2p", cond2Pos); t.setInteger("cc", condChannel); t.setInteger("c2c", cond2Channel); t.setInteger("r", radius);
        t.setString("a", act.name()); t.setString("e", elseAct.name());
        t.setLong("ap", actPos); t.setLong("ap2", actPos2); t.setLong("ep", elsePos); t.setLong("ep2", elsePos2);
        t.setInteger("ac", actChannel); t.setInteger("ec", elseChannel); t.setString("at", text); t.setString("et", elseText);
        t.setString("g", group); t.setString("ct", condText); t.setString("c2t", cond2Text);
        if (priority != 0) t.setInteger("pr", priority);
        if (errAct != Act.NOTHING) { t.setString("ea", errAct.name()); t.setString("et", errText); }
        if (!history.isEmpty()) { net.minecraft.nbt.NBTTagList h = new net.minecraft.nbt.NBTTagList(); for (String x : history) h.appendTag(new net.minecraft.nbt.NBTTagString(x)); t.setTag("h", h); }
        t.setInteger("nf", fired); t.setInteger("ne", errors);
        return t;
    }

    public static Rule read(NBTTagCompound t) {
        Rule r = new Rule();
        r.id = t.getInteger("id"); r.name = t.getString("name"); r.enabled = t.getBoolean("on");
        r.cond = cond(t.getString("c"), Cond.ALWAYS); r.cond2 = t.hasKey("c2") ? cond(t.getString("c2"), null) : null;
        r.condPos = t.getLong("cp"); r.cond2Pos = t.getLong("c2p"); r.condChannel = t.getInteger("cc"); r.cond2Channel = t.getInteger("c2c");
        r.radius = t.hasKey("r") ? t.getInteger("r") : 16;
        r.act = act(t.getString("a")); r.elseAct = act(t.getString("e"));
        r.actPos = t.getLong("ap"); r.actPos2 = t.getLong("ap2"); r.elsePos = t.getLong("ep"); r.elsePos2 = t.getLong("ep2");
        r.actChannel = t.getInteger("ac"); r.elseChannel = t.getInteger("ec"); r.text = t.getString("at"); r.elseText = t.getString("et");
        r.group = t.getString("g"); r.condText = t.getString("ct"); r.cond2Text = t.getString("c2t");
        r.priority = t.getInteger("pr");
        r.errAct = t.hasKey("ea") ? act(t.getString("ea")) : Act.NOTHING; r.errText = t.getString("et");
        net.minecraft.nbt.NBTTagList h = t.getTagList("h", 8);
        for (int i = 0; i < h.tagCount(); i++) r.history.add(h.getStringTagAt(i));
        r.fired = t.getInteger("nf"); r.errors = t.getInteger("ne");
        return r;
    }

    private static Cond cond(String s, Cond d) { try { return Cond.valueOf(s); } catch (RuntimeException e) { return d; } }
    private static Act act(String s) { try { return Act.valueOf(s); } catch (RuntimeException e) { return Act.NOTHING; } }

    public static String where(long p) {
        if (p == 0) return "(pick on map)";
        BlockPos b = BlockPos.fromLong(p);
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    /** one-line summary for the rule list */
    public String summary() {
        StringBuilder s = new StringBuilder(group.isEmpty() ? "" : "[" + group + "] ").append("WHEN ").append(cond.needsText() ? cond.label.split(" ")[0] + " " + condText : cond.label);
        if (cond.needsChannel()) s.append(" ").append(condChannel);
        if (cond2 != null) s.append(" AND ").append(cond2.needsText() ? cond2Text : cond2.label);
        s.append(" THEN ").append(act.label);
        if (act.needsChannel()) s.append(" ").append(actChannel);
        if (act.ordinal() >= Act.SET_VAR.ordinal()) s.append(" ").append(text);
        if (elseAct != Act.NOTHING) s.append(" ELSE ").append(elseAct.label);
        if (errAct != Act.NOTHING) s.append(" ON ERROR ").append(errAct.label);
        if (priority != 0) s.append(" (P").append(priority).append(")");
        return s.toString();
    }
}
