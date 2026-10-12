package com.dogpound.railmap.infra;

import com.dogpound.railmap.incident.GradedDrills;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** /irextras infra selftest - the batch-3 maths checked without a world (each line PASS / FAIL). */
public final class InfraSelfTest {
    private InfraSelfTest() {}

    public static List<String> run() {
        List<String> out = new ArrayList<>();
        int[] fails = {0};
        // #39 reactance method: single-fed, the fault resistance doesn't move the estimate
        double[] m = FaultLocator.measure(400, 25, 0, new Random(1));
        check(out, fails, "fault locator single-fed: 400 m with 25 Ω fault -> " + Math.round(m[3]) + " m", Math.abs(m[3] - 400) <= 400 * 0.05);
        double[] m2 = FaultLocator.measure(400, 25, 2, new Random(1));
        check(out, fails, "fault locator remote infeed pushes the estimate out (" + Math.round(m2[3]) + " m) and widens the band (±" + Math.round(m2[4]) + ")", m2[3] > m[3] && m2[4] > m[4]);
        check(out, fails, String.format(Locale.ROOT, "fault current ~%.1f kA at 400 m flashover (expect 1-6 kA)", FaultLocator.measure(400, 0.5, 0, new Random(2))[2]),
                FaultLocator.measure(400, 0.5, 0, new Random(2))[2] > 1 && FaultLocator.measure(400, 0.5, 0, new Random(2))[2] < 6);
        // #37 the nearer feeder carries more
        double zn = LoadSharing.impedance(10, 64), zf = LoadSharing.impedance(60, 64);
        double near = (1 / zn) / (1 / zn + 1 / zf);
        check(out, fails, String.format(Locale.ROOT, "load sharing: train 10 m from A, 60 m from B -> A carries %.0f%%", near * 100), near > 0.6 && near < 0.95);
        // #38 storage derating
        TileWaysideEss e = new TileWaysideEss();
        e.set(20, 1, 50);
        double full = e.limitKw(false);
        e.set(52, 1, 50);
        double hot = e.limitKw(false);
        e.set(0, 1, 50);
        double cold = e.limitKw(true);
        check(out, fails, String.format(Locale.ROOT, "ESS Li-ion: %.0f kW at 20 C, %.0f kW at 52 C (derated), charge %.0f kW at 0 C", full, hot, cold), full == 1200 && hot < full && hot > 0 && cold < full * 0.2);
        // #41 flaw classes parse back
        check(out, fails, "rail flaw class from its type text", RailFlaws.of(RailFlaws.Flaw.SQUAT.type()) == RailFlaws.Flaw.SQUAT && RailFlaws.of("rail head crack (RCF / transverse)") == null);
        // #47 grades
        check(out, fails, "drill grades A 96 / C 81 / F 40", GradedDrills.grade(96).equals("A") && GradedDrills.grade(81).equals("C") && GradedDrills.grade(40).equals("F"));
        // #36 APC
        check(out, fails, "APC: AUTO pantograph always opens; MANUAL opens only with on-board APC", NeutralSections.apc(false) && NeutralSections.apc(true) == InfraConfig.cabAutoVcb);
        out.add(0, fails[0] == 0 ? "§a§lINFRA SELF-TEST PASSED§r§a (" + out.size() + " checks)" : "§c§lINFRA SELF-TEST: " + fails[0] + " FAILED");
        return out;
    }

    private static void check(List<String> out, int[] fails, String what, boolean ok) {
        if (!ok) fails[0]++;
        out.add((ok ? "§aPASS §7" : "§cFAIL §7") + what);
    }
}
