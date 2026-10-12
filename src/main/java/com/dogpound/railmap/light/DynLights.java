package com.dogpound.railmap.light;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Bridge to a dynamic-lights mod, by reflection so IR Extras needs none to run: Celeritas Dynamic Lights (a LambDynamic
 * Lights port) gets a light handler for EntityTrainLight that returns its `lum`. Without such a mod moving train light
 * stays off (IR's own headlight light needs OptiFine's dynamic lights).
 */
public final class DynLights {
    private DynLights() {}

    private static boolean tried, ready;

    public static synchronized boolean available() {
        if (tried) return ready;
        tried = true;
        if (!Loader.isModLoaded("celeritasdynamiclights")) return false;
        try {
            Class<?> h = Class.forName("dev.lambdaurora.lambdynlights.api.DynamicLightHandler");
            Class<?> hs = Class.forName("dev.lambdaurora.lambdynlights.api.DynamicLightHandlers");
            Object handler = Proxy.newProxyInstance(h.getClassLoader(), new Class<?>[]{h}, (proxy, m, args) -> switch (m.getName()) {
                case "getLuminance" -> args != null && args[0] instanceof EntityTrainLight l ? Math.max(0, Math.min(15, l.lum)) : 0;
                case "isWaterSensitive" -> false;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> args != null && proxy == args[0];
                case "toString" -> "IR Extras train light";
                default -> null;
            });
            Method reg = hs.getMethod("registerEntityDynamicLightHandler", Class.class, h);
            reg.invoke(null, EntityTrainLight.class, handler);
            ready = true;
            RailMap.LOG.info("[IR Extras] night lighting: train lights through Celeritas Dynamic Lights");
        } catch (Throwable t) {
            RailMap.LOG.warn("[IR Extras] night lighting: Celeritas Dynamic Lights API not usable ({}) - trains cast no light", t.toString());
        }
        return ready;
    }
}
