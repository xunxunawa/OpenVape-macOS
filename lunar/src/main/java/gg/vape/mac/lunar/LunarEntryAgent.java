package gg.vape.mac.lunar;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.*;
import java.util.Properties;

/** System-loader entry; payload and hooks share the actual Genesis game loader. */
public final class LunarEntryAgent {
    public static void agentmain(String requestFile, Instrumentation inst) throws Exception {
        Properties request = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get(requestFile))) { request.load(in); }
        Class<?> mc = null;
        for (Class<?> c : inst.getAllLoadedClasses()) if (c.getName().equals("net.minecraft.client.Minecraft")) { mc = c; break; }
        if (mc == null) throw new IllegalStateException("LUNAR_NOT_READY");
        ClassLoader loader = mc.getClassLoader();
        if (loader == null || !loader.getClass().getName().startsWith("com.moonsworth.lunar.genesis."))
            throw new IllegalStateException("UNSUPPORTED_LUNAR_LOADER");
        Class<?> forge = Class.forName("net.minecraftforge.common.ForgeVersion", false, loader);
        if (!"1.8.9".equals(String.valueOf(forge.getField("mcVersion").get(null))))
            throw new IllegalStateException("LUNAR_FORGE_189_REQUIRED");
        Object game = mc.getMethod("func_71410_x").invoke(null);
        File actual = (File) mc.getField("field_71412_D").get(game);
        if (!actual.getCanonicalFile().equals(new File(request.getProperty("gameDir")).getCanonicalFile()))
            throw new IllegalArgumentException("GAME_DIRECTORY_MISMATCH");
        request.setProperty("runtime", "lunar189");
        try (OutputStream out = Files.newOutputStream(Paths.get(requestFile))) { request.store(out, "Verified Lunar request"); }
        URL agent = LunarEntryAgent.class.getProtectionDomain().getCodeSource().getLocation();
        loader.getClass().getMethod("addURL", URL.class).invoke(loader, agent);
        Class<?> bridge = Class.forName("gg.vape.mac.bootstrap.MacAgent", true, loader);
        if (bridge.getClassLoader() != loader) throw new IllegalStateException("LUNAR_BRIDGE_LOADER_MISMATCH");
        try { bridge.getMethod("agentmain", String.class, Instrumentation.class).invoke(null, requestFile, inst); }
        catch (InvocationTargetException e) { throw new IllegalStateException("LUNAR_BOOTSTRAP_FAILED", e.getCause()); }
    }
}
