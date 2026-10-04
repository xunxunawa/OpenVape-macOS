package gg.vape.mac;

import gg.vape.Vape;
import gg.vape.account.AccountInfo;
import gg.vape.mac.bootstrap.MacAgent;
import gg.vape.module.Mod;
import gg.vape.module.none.ClientSettings;
import gg.vape.runtime.NativeBridge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class MacLifecycle {
    private static Object game;
    private static Path config;
    private static boolean starting,initialized,stopped;
    private static long ticks,renders,lastReport,initializationMs,lastFrameNanos,maxFrameGapMs;
    private static final Set<Mod> previouslyEnabled=new HashSet<Mod>();
    public static Object game(){return game;}
    public static Path localDirectory(){
        if(config==null)throw new IllegalStateException("Port data directory not initialized");
        return config.getParent().getParent();
    }
    public static boolean isStartingOnClientThread(){return starting;}
    public static synchronized void apply(Object mc,Properties request)throws Throwable{
        game=mc;config=Paths.get(request.getProperty("config"));
        if("stop".equals(request.getProperty("action"))){stop();MacAgent.status("STOPPED","Game classes restored; loaded Java classes remain until game exit");return;}
        if(initialized&&!stopped){MacAgent.status("ALREADY_ACTIVE","Existing OpenVape runtime; duplicate initialization prevented");return;}
        if(initialized){
            gg.vape.mac.lunar.LunarCompatibility.acquire(mc.getClass().getClassLoader());
            ticks=0;renders=0;lastReport=0;MacAgent.reapply();MacInput.resume();stopped=false;
            for(Mod mod:previouslyEnabled)mod.setEnabled(true);previouslyEnabled.clear();
            MacAgent.status("READY",summary());return;
        }
        if(Vape.INSTANCE!=null)throw new IllegalStateException("Another OpenVape payload is already loaded; restart the game before changing builds");
        starting=true;
        long initializationStart = System.nanoTime();
        MacAgent.log("Client-thread initialization begins");
        try {
            // start() now calls the normal recovered initialization but propagates errors.
            gg.vape.mac.lunar.LunarCompatibility.acquire(mc.getClass().getClassLoader());
            NativeBridge.start();
            if(Vape.INSTANCE==null||Vape.INSTANCE.getModManager()==null)throw new IllegalStateException("Module manager was not initialized");
            if(!ClientSettings.framesInitialized)throw new IllegalStateException("GUI frames were not initialized");
            if(MacAgent.failedCount()!=0)throw new IllegalStateException("Class redefinition failed; inspect the log before retrying in a fresh game process");
            MacInput.install();initializationMs=(System.nanoTime()-initializationStart)/1000000L;
            MacAgent.log("Client-thread initialization completed in " + initializationMs + " ms");initialized=true;stopped=false;MacAgent.status("READY",summary());
        }catch(Throwable t){
            MacInput.stop();MacPlatformBridge.releaseSynthetic();
            try{MacAgent.restore();}catch(Throwable rollback){t.addSuppressed(rollback);}
            try{gg.vape.mac.lunar.LunarCompatibility.release();}catch(Throwable rollback){t.addSuppressed(rollback);}
            throw t;
        }finally{starting=false;}
    }
    private static void stop()throws Exception{
        if(!initialized||stopped)return;
        // Capture enabled states before disabling modules for shutdown.
        saveCurrentConfig();
        MacInput.stop();MacPlatformBridge.releaseSynthetic();
        for(Mod mod:Vape.INSTANCE.getModManager().getAllModules())if(mod.isEnabled()){previouslyEnabled.add(mod);mod.setEnabled(false);}
        if(ClientSettings.INSTANCE!=null)ClientSettings.INSTANCE.setInputEnabled(true);
        MacAgent.restore();gg.vape.mac.lunar.LunarCompatibility.release();stopped=true;
    }
    public static void ticked(){if(initialized&&!stopped)ticks++;}
    public static void rendered(){if(initialized&&!stopped)renders++;}
    public static void frameObserved(){
        if(!initialized||stopped)return;long frameNanos=System.nanoTime();
        if(lastFrameNanos!=0)maxFrameGapMs=Math.max(maxFrameGapMs,(frameNanos-lastFrameNanos)/1000000L);
        lastFrameNanos=frameNanos;long now=System.currentTimeMillis();
        if(now-lastReport>=1000){lastReport=now;MacAgent.status(renders>0?"RENDER_OK":ticks>0?"TICK_OK":"FRAME_OK",summary());}
    }
    public static String summary(){
        String mode="UNKNOWN";
        try{
            Object world=MacAgent.field(game.getClass(),new String[]{"theWorld","field_71441_e"}).get(game);
            mode=world==null?"MENU":Boolean.TRUE.equals(MacAgent.method(game.getClass(),new String[]{"isSingleplayer","func_71356_B"}).invoke(game))?"SINGLEPLAYER":"MULTIPLAYER";
        }catch(Exception ignored){}
        int count=Vape.INSTANCE==null||Vape.INSTANCE.getModManager()==null?0:Vape.INSTANCE.getModManager().getAllModules().size();
        return "mode="+mode+"; modules="+count+"; ticks="+ticks+"; renderEvents="+renders+"; frames="+MacInput.frames+"; keyboardEvents="+MacInput.keyboardEvents+"; mouseEvents="+MacInput.mouseEvents+"; mappingFailures="+MacAgent.failedCount()+"; listenerFailures="+MacDiagnostics.failures()+"; initializationMs="+initializationMs+"; maxFrameGapMs="+maxFrameGapMs;
    }
    public static String readConfig(String fallback){
        if(config==null||!Files.exists(config))return fallback;
        try{return new String(Files.readAllBytes(config),StandardCharsets.UTF_8);}catch(IOException e){throw new IllegalStateException("Cannot read local config",e);}
    }
    public static synchronized void saveConfig(String json){
        if(config==null)throw new IllegalStateException("Local config path missing");
        try{Files.createDirectories(config.getParent());byte[] data=json.getBytes(StandardCharsets.UTF_8);
            if(Files.exists(config)){
                if(Arrays.equals(Files.readAllBytes(config),data))return;
                Path history=config.getParent().resolve("history");Files.createDirectories(history);
                Files.copy(config,history.resolve("settings-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".json"));
            }
            Path tmp=Files.createTempFile(config.getParent(),"settings-",".tmp");
            try{Files.write(tmp,data);try{Files.move(tmp,config,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,config,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
        }catch(IOException e){throw new IllegalStateException("Cannot save local config",e);}
    }
    public static void saveCurrentConfig(){
        if(Vape.INSTANCE==null||Vape.INSTANCE.getProfilesManager()==null)throw new IllegalStateException("No active configuration to save");
        gg.vape.config.Profile active=Vape.INSTANCE.getProfilesManager().getActiveProfile();
        if(active==null)throw new IllegalStateException("No active profile to capture");
        active.captureCurrentState();
        saveConfig(Vape.INSTANCE.getSyncThread().buildSettingsPayload(false).toString());
    }
    public static java.util.concurrent.CompletableFuture<Void> saveCurrentConfigAsync(){
        final java.util.concurrent.CompletableFuture<Void> result=new java.util.concurrent.CompletableFuture<Void>();
        try{
            MacAgent.method(game.getClass(),new String[]{"addScheduledTask","func_152344_a"},Runnable.class).invoke(game,new Runnable(){public void run(){
                try{saveCurrentConfig();result.complete(null);}catch(Throwable error){result.completeExceptionally(error);}
            }});
        }catch(Throwable error){result.completeExceptionally(error);}
        return result;
    }
}
