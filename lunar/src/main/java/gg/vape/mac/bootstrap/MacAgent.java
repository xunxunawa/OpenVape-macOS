package gg.vape.mac.bootstrap;

import java.io.*;
import java.lang.instrument.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Shared JVM bootstrap. Never imports payload, game, ASM, LWJGL or Forge classes. */
public final class MacAgent {
    private static Instrumentation instrumentation;
    private static final Map<Class<?>,byte[]> originals = new LinkedHashMap<Class<?>,byte[]>();
    private static final Map<Class<?>,byte[]> patches = new LinkedHashMap<Class<?>,byte[]>();
    private static volatile Method keyboard, mouse, frame;
    private static Path report, log;
    private static boolean logging=true;
    private static volatile boolean lunarActive;
    public static boolean isLunarRuntime(){return lunarActive;}
    public static boolean lunarKeyDown(boolean down,int key){return lunarActive && key==54 && keyboard!=null ? false : down;}
    private static int failures;
    private static int mappingFailures;
    public static Instrumentation instrumentation() { return instrumentation; }
    public static int patchedCount() { synchronized(originals) { return patches.size(); } }
    public static int failedCount() { return failures+mappingFailures; }
    public static void mappingFailure(String task,int code){mappingFailures++;log("Mapping failed: "+task+" code="+code);}
    public static void agentmain(String requestFile, Instrumentation inst) throws Exception {
        instrumentation = inst;
        final Properties request = new Properties();
        try(InputStream in=Files.newInputStream(Paths.get(requestFile))) { request.load(in); }
        report=Paths.get(request.getProperty("report")); log=Paths.get(request.getProperty("log"));
        logging=!"false".equals(request.getProperty("logging"));
        try {
            if (!inst.isRedefineClassesSupported() || !inst.isRetransformClassesSupported())
                throw new IllegalStateException("JVM does not support class redefinition and retransformation");
            if ("probe".equals(request.getProperty("action"))) { status("PROBE_OK", "Real Java agent entry reached"); return; }
            Class<?> gameType=null;
            for(Class<?> c:inst.getAllLoadedClasses()) if(c.getName().equals("net.minecraft.client.Minecraft")) { gameType=c;break; }
            if(gameType==null) throw new IllegalStateException("Wait for Forge 1.8.9 to reach its main menu");
            final ClassLoader loader=gameType.getClassLoader();
            lunarActive="lunar189".equals(request.getProperty("runtime")) && loader.getClass().getName().startsWith("com.moonsworth.lunar.genesis.");
            if(!lunarActive && !loader.getClass().getName().equals("net.minecraft.launchwrapper.LaunchClassLoader")) throw new IllegalStateException("Unsupported client class loader");
            Class<?> forgeVersion=Class.forName("net.minecraftforge.common.ForgeVersion",true,loader);
            if(!"1.8.9".equals(String.valueOf(forgeVersion.getField("mcVersion").get(null)))) throw new IllegalStateException("This payload targets Forge 1.8.9 only");
            final Object game=method(gameType,new String[]{"getMinecraft","func_71410_x"}).invoke(null);
            File actual=(File)field(gameType,new String[]{"mcDataDir","field_71412_D"}).get(game);
            if(!actual.getCanonicalFile().equals(new File(request.getProperty("gameDir")).getCanonicalFile())) throw new IllegalArgumentException("Game directory does not match selected instance");
            if(!lunarActive){
                loader.getClass().getMethod("addClassLoaderExclusion",String.class).invoke(loader,"gg.vape.mac.bootstrap.");
                loader.getClass().getMethod("addURL",URL.class).invoke(loader,MacAgent.class.getProtectionDomain().getCodeSource().getLocation());
            }
            status("SCHEDULED","Awaiting Minecraft client thread");
            Runnable work=new Runnable(){public void run(){
                ClassLoader previous=Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(loader);
                    Class<?> lifecycle=Class.forName("gg.vape.mac.MacLifecycle",true,loader);
                    lifecycle.getMethod("apply",Object.class,Properties.class).invoke(null,game,request);
                } catch(Throwable t) {
                    status("FAILED",describe(t)); log(describe(t));
                    try { disableInput(); restore(); } catch(Throwable rollback) { log("Rollback failed: "+describe(rollback)); }
                } finally { Thread.currentThread().setContextClassLoader(previous); }
            }};
            method(gameType,new String[]{"addScheduledTask","func_152344_a"},Runnable.class).invoke(game,work);
        } catch(Throwable t) { status("FAILED",describe(t)); throw new IllegalStateException(describe(t),t); }
    }
    public static byte[] bytes(final Class<?> target) {
        if(target==null || !instrumentation.isModifiableClass(target)) throw new IllegalArgumentException("Unmodifiable class: "+target);
        synchronized(originals) {
            final AtomicReference<byte[]> capture=new AtomicReference<byte[]>();
            ClassFileTransformer transformer=new ClassFileTransformer(){
                public byte[] transform(ClassLoader l,String n,Class<?> c,ProtectionDomain d,byte[] b){if(c==target) capture.set(b.clone());return null;}
            };
            instrumentation.addTransformer(transformer,true);
            try { instrumentation.retransformClasses(target); }
            catch(Exception e) { throw new IllegalStateException("Cannot read loaded class "+target.getName(),e); }
            finally { instrumentation.removeTransformer(transformer); }
            if(capture.get()==null) throw new IllegalStateException("No bytecode captured: "+target.getName());
            return capture.get();
        }
    }
    public static int redefine(Class<?> target,byte[] data) {
        synchronized(originals) {
            try {
                if(!originals.containsKey(target)) originals.put(target,bytes(target));
                instrumentation.redefineClasses(new ClassDefinition(target,data)); patches.put(target,data.clone());return 0;
            } catch(Throwable t) { failures++;log("Redefine failed "+target.getName()+": "+describe(t)); return 113; }
        }
    }
    public static void restore() throws Exception { applyDefinitions(originals); }
    public static void reapply() throws Exception { applyDefinitions(patches); }
    private static void applyDefinitions(Map<Class<?>,byte[]> map) throws Exception {
        synchronized(originals) {
            List<ClassDefinition> defs=new ArrayList<ClassDefinition>();
            for(Map.Entry<Class<?>,byte[]> e:map.entrySet()) defs.add(new ClassDefinition(e.getKey(),e.getValue()));
            if(!defs.isEmpty()) instrumentation.redefineClasses(defs.toArray(new ClassDefinition[0]));
        }
    }
    public static void inputCallbacks(Class<?> input) throws Exception {
        keyboard=input.getMethod("keyboardEvent");mouse=input.getMethod("mouseEvent");frame=input.getMethod("frame");
    }
    public static void disableInput() { keyboard=null;mouse=null;frame=null; }
    public static boolean keyboardNext(boolean result) { return !result ? false : !dispatch(keyboard); }
    public static boolean mouseNext(boolean result) { return !result ? false : !dispatch(mouse); }
    public static void frameUpdated() { dispatch(frame); }
    private static boolean dispatch(Method callback) {
        if(callback==null) return false;
        try { return Boolean.TRUE.equals(callback.invoke(null)); }
        catch(Throwable t) { disableInput();status("RUNTIME_FAILED",describe(t));log(describe(t));return false; }
    }
    public static void status(String state,String detail) {
        if(report==null) return;
        Properties p=new Properties();p.setProperty("state",state);p.setProperty("detail",detail);p.setProperty("time",String.valueOf(System.currentTimeMillis()));
        p.setProperty("patchedClasses",String.valueOf(patchedCount()));p.setProperty("failedRedefinitions",String.valueOf(failures));
        p.setProperty("mappingFailures",String.valueOf(mappingFailures));
        try {
            Files.createDirectories(report.getParent());Path tmp=Files.createTempFile(report.getParent(),"status-",".tmp");
            try {try(OutputStream out=Files.newOutputStream(tmp)){p.store(out,"OpenVape macOS");}
                try {Files.move(tmp,report,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException e){Files.move(tmp,report,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(tmp);}
        } catch(IOException e) { System.err.println(e); }
    }
    public static synchronized void log(String message) {
        if(!logging)return;
        if(log==null){System.out.println("[OpenVape macOS] "+message);return;}
        try {Files.write(log,(new Date()+" "+message+"\n").getBytes("UTF-8"),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(IOException e){System.err.println(e);}
    }
    public static Field field(Class<?> c,String[] names) throws NoSuchFieldException {
        for(Class<?> t=c;t!=null;t=t.getSuperclass()) for(String name:names) try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(Arrays.toString(names));
    }
    public static Method method(Class<?> c,String[] names,Class<?>... params) throws NoSuchMethodException {
        for(String name:names)try{Method m=c.getMethod(name,params);m.setAccessible(true);return m;}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(Arrays.toString(names));
    }
    public static String describe(Throwable t) {
        while(t instanceof InvocationTargetException && t.getCause()!=null)t=t.getCause();
        StringWriter s=new StringWriter();t.printStackTrace(new PrintWriter(s));return s.toString();
    }
}
