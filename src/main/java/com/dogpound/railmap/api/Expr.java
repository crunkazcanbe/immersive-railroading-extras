package com.dogpound.railmap.api;

import com.dogpound.railmap.program.ProgramData;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * The little expression language the signal-box program, the scenario objectives and the logic modules share
 * (her ChatGPT list #98 / #105). Deliberately tiny and safe: no loops, no calls, nothing that can run away.
 * <pre>
 * condition :=  sum [op sum] { (&& | ||) sum [op sum] }      op = &lt; &lt;= &gt; &gt;= = == !=      (no op = "is not 0")
 * sum       :=  operand { (+ - * / %) operand }               (left to right)
 * operand   :=  number | variable | ch12 | t:name | done:name | @x,y,z.key | hour | minute | day | alarms | trains
 *               | vehicles | deps | ontime | delay | freight | boarded | waiting
 * assignment:=  variable = sum
 * </pre>
 * Variables live in the program memory (ProgramData) and start at 0. @x,y,z.key reads the telemetry API (#97), e.g.
 * {@code @120,64,-40.kw > 500}. t:name = seconds left on a program timer, done:name = 1 once it ran out.
 */
public final class Expr {
    private Expr() {}

    /** true / false for a condition; a bad expression is false and its error lands in err[0] */
    public static boolean test(World w, String s, String[] err) {
        try {
            String[] ors = s.split("\\|\\|");
            for (String or : ors) {
                boolean all = true;
                for (String and : or.split("&&")) if (!compare(w, and.trim())) { all = false; break; }
                if (all) return true;
            }
            return false;
        } catch (RuntimeException e) {
            if (err != null) err[0] = e.getMessage();
            return false;
        }
    }

    private static final String[] OPS = {"<=", ">=", "!=", "==", "<", ">", "="};

    private static boolean compare(World w, String s) {
        for (String op : OPS) {
            int i = s.indexOf(op);
            if (i <= 0) continue;
            double a = sum(w, s.substring(0, i)), b = sum(w, s.substring(i + op.length()));
            switch (op) {
                case "<=": return a <= b + 1e-9;
                case ">=": return a >= b - 1e-9;
                case "!=": return Math.abs(a - b) > 1e-9;
                case "<": return a < b;
                case ">": return a > b;
                default: return Math.abs(a - b) <= 1e-9;
            }
        }
        return Math.abs(sum(w, s)) > 1e-9;
    }

    /** a sum, left to right: operand { op operand }. Tokens: @telemetry runs to the next space; a leading - is a sign */
    public static double sum(World w, String s) {
        s = s.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("empty expression");
        double acc = 0;
        char op = '+';
        int i = 0, n = s.length();
        boolean wantOperand = true;
        while (i < n) {
            char c = s.charAt(i);
            if (c == ' ') { i++; continue; }
            if (wantOperand) {
                int j = i;
                if (c == '@') { while (j < n && s.charAt(j) != ' ') j++; }
                else { if (c == '-') j++; while (j < n && s.charAt(j) != ' ' && "+-*/%".indexOf(s.charAt(j)) < 0) j++; }
                double v = operand(w, s.substring(i, j));
                switch (op) {
                    case '+': acc += v; break;
                    case '-': acc -= v; break;
                    case '*': acc *= v; break;
                    case '/': acc = v == 0 ? 0 : acc / v; break;
                    default: acc = v == 0 ? 0 : acc % v;
                }
                wantOperand = false;
                i = j;
            } else {
                if ("+-*/%".indexOf(c) < 0) throw new IllegalArgumentException("expected + - * / % at '" + s.substring(i) + "'");
                op = c;
                wantOperand = true;
                i++;
            }
        }
        if (wantOperand) throw new IllegalArgumentException("expression ends with an operator");
        return acc;
    }

    public static double operand(World w, String t) {
        if (t.isEmpty()) throw new IllegalArgumentException("missing value");
        char c0 = t.charAt(0);
        if (Character.isDigit(c0) || c0 == '.' || c0 == '-' && t.length() > 1 && (Character.isDigit(t.charAt(1)) || t.charAt(1) == '.')) {
            try { return Double.parseDouble(t); } catch (NumberFormatException e) { throw new IllegalArgumentException("not a number: " + t); }
        }
        ProgramData pd = ProgramData.get(w);
        String l = t.toLowerCase(Locale.ROOT);
        if (c0 == '@') return Telemetry.value(w, t.substring(1));
        if (l.startsWith("t:")) return pd.timerLeft(w, t.substring(2).trim());
        if (l.startsWith("done:")) return pd.timerDone(w, t.substring(5).trim()) ? 1 : 0;
        if (l.matches("ch\\d{1,2}")) return pd.ch(Integer.parseInt(l.substring(2))) ? 1 : 0;
        switch (l) {
            case "hour": return ((w.getWorldTime() + 6000) % 24000) / 1000;
            case "minute": return ((w.getWorldTime() + 6000) % 1000) * 60 / 1000;
            case "day": return w.getWorldTime() / 24000;
            case "alarms": { com.dogpound.railmap.alarm.AlarmManager a = com.dogpound.railmap.alarm.AlarmManager.get(w); long now = w.getTotalWorldTime(); return a.count(1, now) + a.count(2, now); }
            case "trains": return com.dogpound.railmap.auto.RailwayData.get(w).trains().size();
            case "vehicles": return Transport.vehicles(w).size();
            case "deps": return kpi(w, "dep");
            case "ontime": { double d = kpi(w, "dep"); return d == 0 ? 100 : kpi(w, "ontime") / d * 100; }
            case "delay": { double d = kpi(w, "dep"); return d == 0 ? 0 : kpi(w, "delay") / d; }
            case "freight": return kpi(w, "freight");
            case "boarded": { double s = 0; for (com.dogpound.railmap.economy.PassengerData.Stn st : com.dogpound.railmap.economy.PassengerData.get(w).stations()) s += st.boardedDay; return s; }
            case "waiting": { double s = 0; for (com.dogpound.railmap.economy.PassengerData.Stn st : com.dogpound.railmap.economy.PassengerData.get(w).stations()) s += st.waitingTotal(); return s; }
            default:
        }
        if (!t.matches("[A-Za-z_][A-Za-z0-9_.]*")) throw new IllegalArgumentException("bad name: " + t);
        return pd.var(t);
    }

    private static double kpi(World w, String k) {
        return com.dogpound.railmap.incident.Analytics.v(com.dogpound.railmap.incident.Analytics.get(w).today(w), k);
    }

    /** "name = sum": sets the program variable, returns the new value */
    public static double assign(World w, String s) {
        int i = s.indexOf('=');
        if (i <= 0) throw new IllegalArgumentException("expected name = value");
        String name = s.substring(0, i).trim();
        if (!name.matches("[A-Za-z_][A-Za-z0-9_.]*")) throw new IllegalArgumentException("bad variable name: " + name);
        double v = sum(w, s.substring(i + 1));
        ProgramData.get(w).setVar(name, v);
        return v;
    }

    public static String fmt(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e12 ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.3f", v);
    }
}
