package gg.vape.mac.rescue;

import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/** Read-only export of the currently active profile from an already loaded client. */
public final class ConfigExportAgent {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private ConfigExportAgent() { }

    public static void agentmain(String argument, Instrumentation instrumentation) {
        Path report = null;
        try {
            Properties request = new Properties();
            try (java.io.InputStream in = Files.newInputStream(Paths.get(argument))) { request.load(in); }
            Path gameDir = requiredPath(request, "gameDir");
            Path output = requiredPath(request, "output");
            report = requiredPath(request, "report");
            Class<?> lifecycle = loaded(instrumentation, "gg.vape.mac.MacLifecycle");
            Class<?> minecraftType = loaded(instrumentation, "net.minecraft.client.Minecraft");
            Class<?> vapeType = loaded(instrumentation, "gg.vape.Vape");
            requireInitialized(lifecycle);
            requireInitialized(minecraftType);
            requireInitialized(vapeType);
            ClassLoader loader = lifecycle.getClassLoader();
            if (minecraftType.getClassLoader() != loader || vapeType.getClassLoader() != loader)
                throw new IllegalStateException("Loaded OpenVape/Minecraft classes do not share the lifecycle classloader");
            Method getGame = lifecycle.getDeclaredMethod("game");
            getGame.setAccessible(true);
            final Object game = getGame.invoke(null);
            if (game == null || !minecraftType.isInstance(game)) throw new IllegalStateException("Lifecycle game instance is unavailable");
            Path actualGameDir = gameDirectory(game, minecraftType);
            if (!gameDir.equals(actualGameDir)) throw new IllegalArgumentException("Requested gameDir does not match the active Minecraft directory");
            Method schedule = method(minecraftType, "addScheduledTask", "func_152344_a");
            schedule.setAccessible(true);
            final Path outputPath = output;
            final Path reportPath = report;
            // Also proves agent entry before Java17 parses Java8's old loadAgent response.
            writeReport(reportPath, "SCHEDULED", null, "Awaiting client-thread configuration snapshot");
            schedule.invoke(game, new Runnable() {
                public void run() {
                    try {
                        Object vape = staticField(vapeType, "INSTANCE");
                        if (vape == null) throw new IllegalStateException("Vape.INSTANCE is null");
                        Object profiles = call(vape, "getProfilesManager");
                        Object profile = call(profiles, "getActiveProfile");
                        if (profile == null) throw new IllegalStateException("No active profile");
                        call(profile, "captureCurrentState");
                        Object sync = call(vape, "getSyncThread");
                        Object payload = call(sync, "buildSettingsPayload", Boolean.FALSE);
                        if (payload == null) throw new IllegalStateException("Settings payload is null");
                        Path parent = outputPath.toAbsolutePath().normalize().getParent();
                        if (parent == null) throw new IllegalArgumentException("Output must have a parent directory");
                        Files.createDirectories(parent);
                        try (OutputStream out = Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                            out.write(payload.toString().getBytes(UTF8));
                        }
                        writeReport(reportPath, "CONFIG_SAVED", outputPath.toAbsolutePath().normalize().toString(), null);
                    } catch (Throwable failure) {
                        writeReportQuietly(reportPath, "FAILED", null, failure.toString());
                    }
                }
            });
        } catch (Throwable failure) {
            writeReportQuietly(report, "FAILED", null, failure.toString());
        }
    }

    private static Path requiredPath(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.trim().length() == 0) throw new IllegalArgumentException("Missing request property: " + key);
        return Paths.get(value).toAbsolutePath().normalize();
    }

    private static Class<?> loaded(Instrumentation instrumentation, String name) {
        for (Class<?> type : instrumentation.getAllLoadedClasses()) if (name.equals(type.getName())) return type;
        throw new IllegalStateException("Required class is not already loaded: " + name);
    }

    private static void requireInitialized(Class<?> type) throws Exception {
        // Fail closed: reflective reads/invocations of static members can initialize a loaded class.
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe", false, null);
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        Method check = unsafeType.getMethod("shouldBeInitialized", Class.class);
        if (Boolean.TRUE.equals(check.invoke(unsafe, type)))
            throw new IllegalStateException("Refusing to initialize class that is loaded but not initialized: " + type.getName());
    }

    private static Path gameDirectory(Object game, Class<?> type) throws Exception {
        for (String name : new String[] {"mcDataDir", "field_71412_D"}) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(game);
                if (value instanceof java.io.File) return ((java.io.File)value).toPath().toAbsolutePath().normalize();
                if (value instanceof Path) return ((Path)value).toAbsolutePath().normalize();
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException("Minecraft data directory field mcDataDir/field_71412_D not found");
    }

    private static Method method(Class<?> type, String... names) throws NoSuchMethodException {
        for (String name : names) try { return type.getDeclaredMethod(name, Runnable.class); } catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException("Minecraft client scheduler method not found");
    }

    private static Object staticField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = args[i] instanceof Boolean ? Boolean.TYPE : args[i].getClass();
        Method method = target.getClass().getMethod(name, types); method.setAccessible(true); return method.invoke(target, args);
    }

    private static void writeReportQuietly(Path path, String state, String output, String detail) {
        if (path == null) return;
        try { writeReport(path, state, output, detail); } catch (Throwable ignored) { }
    }

    private static void writeReport(Path report, String state, String output, String detail) throws Exception {
        Path absolute = report.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IllegalArgumentException("Report must have a parent directory");
        Files.createDirectories(parent);
        Properties result = new Properties();
        result.setProperty("state", state);
        result.setProperty("time", Long.toString(System.currentTimeMillis()));
        if (output != null) result.setProperty("path", output);
        if (detail != null) result.setProperty("detail", detail);
        Path temp = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING)) { result.store(out, "OpenVape config export"); }
            try { Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
