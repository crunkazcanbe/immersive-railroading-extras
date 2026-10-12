package com.dogpound.railmap.api;

import com.dogpound.railmap.block.TileDataLink;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * ComputerCraft / CC:Tweaked for the versioned API (her ChatGPT list #100, optional integration order: OpenComputers
 * first, then ComputerCraft). A computer next to a Railroad Data Link sees a peripheral "irextras_api" whose methods
 * are the API's (dots become underscores: orders.list -> orders_list) plus call(name, ...). Built entirely through
 * reflection (java.lang.reflect.Proxy on dan200's interfaces) so IR Extras needs no ComputerCraft jar to build or run;
 * the calls hop to the server thread through ILuaContext.executeMainThreadTask, like CC's own peripherals.
 * The Data Link's 12 V, MODE (READ ONLY blocks control methods) and its "api" setting apply exactly as for OC.
 */
public final class CCBridge {
    private CCBridge() {}

    private static String state = "not started", detail = "";

    static String state() { return state; }
    static String detail() { return detail; }

    public static void init() {
        if (!Loader.isModLoaded("computercraft")) return;
        try {
            ClassLoader cl = CCBridge.class.getClassLoader();
            Class<?> api = Class.forName("dan200.computercraft.api.ComputerCraftAPI", true, cl);
            Class<?> provider = Class.forName("dan200.computercraft.api.peripheral.IPeripheralProvider", true, cl);
            Class<?> periph = Class.forName("dan200.computercraft.api.peripheral.IPeripheral", true, cl);
            Class<?> task = Class.forName("dan200.computercraft.api.lua.ILuaTask", true, cl);
            Class<?> lua = Class.forName("dan200.computercraft.api.lua.ILuaContext", true, cl);
            Method exec = lua.getMethod("executeMainThreadTask", task);
            Object prov = Proxy.newProxyInstance(cl, new Class<?>[]{provider}, (p, m, a) -> {
                if (m.getName().equals("getPeripheral") && a != null && a.length >= 2 && a[0] instanceof World w && a[1] instanceof BlockPos pos) {
                    TileEntity te = w.getTileEntity(pos);
                    return te instanceof TileDataLink link ? peripheral(cl, periph, task, exec, link) : null;
                }
                return basic(p, m, a);
            });
            api.getMethod("registerPeripheralProvider", provider).invoke(null, prov);
            state = "OK";
            detail = "peripheral 'irextras_api' on every Railroad Data Link (" + names().length + " methods)";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            state = "ERROR";
            detail = "ComputerCraft API did not link: " + e;
            com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] ComputerCraft bridge off: {}", e.toString());
        }
    }

    private static String[] names() {
        List<String> l = new ArrayList<>();
        l.add("call");
        for (RailApi.Method m : RailApi.methods()) l.add(m.name.replace('.', '_'));
        return l.toArray(new String[0]);
    }

    private static Object peripheral(ClassLoader cl, Class<?> periph, Class<?> task, Method exec, TileDataLink link) {
        String[] names = names();
        InvocationHandler h = (p, m, a) -> {
            switch (m.getName()) {
                case "getType": return "irextras_api";
                case "getMethodNames": return names.clone();
                case "attach": case "detach": return null;
                case "getTarget": return link;
                case "equals": return a != null && a.length == 1 && a[0] == p;
                case "callMethod": {
                    Object ctx = a[1];
                    int idx = (Integer) a[2];
                    Object[] args = a[3] == null ? new Object[0] : (Object[]) a[3];
                    String name;
                    if (idx == 0) {
                        if (args.length == 0) return new Object[]{null, "call(name, ...) needs a method name"};
                        name = String.valueOf(args[0]);
                        Object[] rest = new Object[args.length - 1];
                        System.arraycopy(args, 1, rest, 0, rest.length);
                        args = rest;
                    } else name = idx < names.length ? names[idx].replace('_', '.') : "";
                    final String fn = name;
                    final Object[] fa = args;
                    Object t = Proxy.newProxyInstance(cl, new Class<?>[]{task}, (tp, tm, ta) -> {
                        if (!tm.getName().equals("execute")) return basic(tp, tm, ta);
                        RailApi.Method rm = RailApi.method(fn);
                        String deny = link.apiDenied(rm != null && rm.control);
                        if (deny != null) return new Object[]{null, deny};
                        return new Object[]{RailApi.call(link.getWorld(), fn, fa, Audit.Src.COMPUTER, "computercraft@" + link.getPos().getX() + "," + link.getPos().getY() + "," + link.getPos().getZ())};
                    });
                    return exec.invoke(ctx, t);
                }
                default: return basic(p, m, a);
            }
        };
        return Proxy.newProxyInstance(cl, new Class<?>[]{periph}, h);
    }

    private static Object basic(Object p, Method m, Object[] a) {
        switch (m.getName()) {
            case "hashCode": return System.identityHashCode(p);
            case "equals": return a != null && a.length == 1 && a[0] == p;
            case "toString": return "IRExtras:" + m.getDeclaringClass().getSimpleName();
            default: return null;
        }
    }
}
