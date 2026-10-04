package gg.vape.mac.injector;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AgentLoadException;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import java.awt.event.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.util.concurrent.TimeoutException;

/** Runs separately on Java 17; target payload remains Java 8 compatible. */
public final class Injector {
    private static final Path ROOT=root();
    private static final Path ASSETS=assetDirectory();
    private static final Path DEFAULT_GAME=Paths.get(System.getProperty("user.home"),"Library/Application Support/minecraft/versions/1.8.9-Forge-OptiFine");
    static void configureJava2D(){
        if(System.getProperty("sun.java2d.opengl")==null)System.setProperty("sun.java2d.opengl","false");
    }
    private static final String[] UI_FONT_COVERAGE_TEXT={
        "OpenVape macOS · Forge 1.8.9",
        "● 灰色 · 未检测到候选进程",
        "● 绿色 · 已发现候选 Minecraft（需另行核对 Forge 与 Java 8；发现进程不代表兼容）",
        "● 黄色 · 等待实际心跳（尚无状态；需两个不同且新鲜的目标 time）",
        "● 活跃绿色 · 连续心跳正常 · TICK_OK · ticks6284 frames9449 · 报告：/Users/example/Library/Application Support/minecraft/reports/123.status.properties",
        "● 红色 · 无心跳，疑似卡住（目标报告 time 超过 20 秒或等待超时）；不能据此确认崩溃",
        "● 红色 · 连接等待超过 30 秒；操作线程仍运行，按钮保持禁用",
        "只读监测；绑定当前 PID 12345，不会执行 attach。选择状态报告：",
        "打开报告目录 打开配置目录 导入已保存配置 保存当前配置 选择游戏目录 监测已有报告 刷新 注入 停用 游戏目录 或 PID",
        "Minecraft Java · PID 12345 · 1.8.9",
        "状态监测 TICK_OK · MENU TICK_OK ticks6284 frames9449",
        "本次报告：/Users/example/Library/Application Support/minecraft/reports/123.status.properties"
    };
    static boolean configureUiFont(){
        if(!System.getProperty("os.name","").startsWith("Mac"))return false;
        return configureUiFont(Paths.get("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"));
    }
    static boolean configureUiFont(Path path){
        try{
            if(!Files.isRegularFile(path))return false;
            Font base=Font.createFont(Font.TRUETYPE_FONT,path.toFile());
            for(String sample:UI_FONT_COVERAGE_TEXT)if(base.canDisplayUpTo(sample)>=0)return false;
            UIDefaults defaults=UIManager.getDefaults();
            Enumeration<?> keys=defaults.keys();
            while(keys.hasMoreElements()){
                Object key=keys.nextElement();Object value=defaults.get(key);
                if(value instanceof Font){Font old=(Font)value;defaults.put(key,base.deriveFont(old.getStyle(),old.getSize2D()));}
            }
            return true;
        }catch(Exception unavailable){return false;}
    }
    private static Path root(){
        String override=System.getProperty("openvape.root");
        if(override!=null&&!override.isBlank())return Paths.get(override).toAbsolutePath().normalize();
        if(Boolean.getBoolean("openvape.portable"))return Paths.get(System.getProperty("user.home"),"Library/Application Support/OpenVape-macOS").toAbsolutePath().normalize();
        try{return Paths.get(Injector.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static Path assetDirectory(){
        if(Boolean.getBoolean("openvape.portable"))try{return Paths.get(Injector.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();}catch(Exception e){throw new IllegalStateException(e);}
        return ROOT.resolve("dist");
    }
    public static final class Target {
        final long pid;final String version;final Path gameDirectory;
        Target(long pid,String version){this(pid,version,null);}
        Target(long pid,String version,Path gameDirectory){this.pid=pid;this.version=version;this.gameDirectory=gameDirectory;}
        public String toString(){return "Minecraft Java · PID "+pid+(version.isEmpty()?"":" · "+version);}
    }
    static List<Target> targets(){
        List<Target> result=new ArrayList<>();
        ProcessHandle.allProcesses().forEach(p->{
            // Arguments can contain access tokens. Inspect locally; never log or display them.
            ProcessHandle.Info info=p.info();String[] args=info.arguments().orElse(new String[0]);
            boolean launch=false;String version="";Path gameDirectory=null;
            for(int i=0;i<args.length;i++){
                if(args[i].equals("net.minecraft.launchwrapper.Launch"))launch=true;
                if(args[i].equals("--version")&&i+1<args.length)version=args[i+1];
                if(args[i].equals("--gameDir")&&i+1<args.length)gameDirectory=Paths.get(args[i+1]).toAbsolutePath().normalize();
            }
            if(launch)result.add(new Target(p.pid(),version,gameDirectory));
        });
        result.sort(Comparator.comparingLong(t->t.pid));return result;
    }
    static Properties read(Path path)throws IOException{
        Properties p=new Properties();if(Files.exists(path))try(InputStream in=Files.newInputStream(path)){p.load(in);}return p;
    }
    // Pure health decision, also exercised by InjectorStatusTest.
    static String health(String state,String targetTime,long now,long monitorStarted,int distinctFreshHeartbeats,Boolean processAlive){
        if(Set.of("FAILED","RUNTIME_FAILED").contains(state))return "RED";
        if(Set.of("STOPPED","PROBE_OK").contains(state))return "GRAY";
        if(Boolean.FALSE.equals(processAlive))return "RED";
        long targetMillis=0;try{targetMillis=Long.parseLong(targetTime);}catch(Exception ignored){}
        boolean hasTime=targetMillis>0;
        if(hasTime&&now-targetMillis>=20_000L)return "RED";
        if(!hasTime&&now-monitorStarted>=20_000L)return "RED";
        if(Set.of("FRAME_OK","TICK_OK","RENDER_OK").contains(state)&&hasTime&&distinctFreshHeartbeats>=2)return "GREEN";
        return "YELLOW";
    }
    public static Path execute(String action,String pid,Path game,Consumer<String> output)throws Exception{
        Path reports=ROOT.resolve("reports");Files.createDirectories(reports);
        String id=System.currentTimeMillis()+"-"+UUID.randomUUID();Path request=reports.resolve(id+".request.properties"),report=reports.resolve(id+".status.properties");
        output.accept("本次报告："+report);
        if(!Set.of("inject","stop","probe").contains(action))throw new IllegalArgumentException("Unknown action");
        Long.parseLong(pid);Path agent=ASSETS.resolve("openvape-macos-agent.jar");
        if(!Files.isRegularFile(agent))throw new FileNotFoundException("Agent JAR missing: "+agent);
        Properties p=new Properties();p.setProperty("action",action);p.setProperty("gameDir",game.toAbsolutePath().toString());
        p.setProperty("report",report.toString());p.setProperty("log",reports.resolve(id+".log").toString());p.setProperty("logging","false");
        p.setProperty("config",ROOT.resolve("config/settings.json").toString());
        try(OutputStream out=Files.newOutputStream(request)){p.store(out,"OpenVape macOS request");}
        output.accept("连接 PID "+pid+"，操作："+action);
        VirtualMachine vm=null;
        try{
            vm=VirtualMachine.attach(pid);
            if(!"1.8".equals(vm.getSystemProperties().getProperty("java.specification.version"))){
                throw new IllegalArgumentException("目标需要使用 Java 8；没有加载载荷。");
            }
            try { vm.loadAgent(agent.toString(),request.toString()); }
            catch(AgentLoadException compatibility){
                // Some Java 8 VMs return old protocol "0" to Java 17's parser.
                // Accept only if this request's unique target-written report proves entry.
                if(!"0".equals(compatibility.getMessage())||read(report).getProperty("state","").isEmpty())throw compatibility;
                output.accept("目标已写入独立报告；继续检查实际运行状态。");
            }
        }finally{if(vm!=null)vm.detach();}
        long deadline=System.nanoTime()+30_000_000_000L;String previous="";
        while(System.nanoTime()<deadline){
            Properties state=read(report);String s=state.getProperty("state","");
            if(!s.equals(previous)){previous=s;if(!s.isEmpty())output.accept(s+": "+state.getProperty("detail",""));}
            if(Set.of("FAILED","RUNTIME_FAILED").contains(s))throw new IllegalStateException(state.getProperty("detail"));
            // FRAME_OK is an entry/heartbeat signal, not successful runtime acceptance.
            if(Set.of("PROBE_OK","STOPPED","ALREADY_ACTIVE","RENDER_OK","TICK_OK","FRAME_OK").contains(s)){output.accept("已收到状态信号；继续监测实际心跳。报告："+report);return report;}
            if(s.equals("READY")&&!action.equals("inject")){output.accept("报告："+report);return report;}
            Thread.sleep(150);
        }
        output.accept("等待事件超时。请检查状态文件；不能据此确认已正常运行。报告："+report);return report;
    }
    public static Path saveCurrentConfiguration(String pid,Path game,Consumer<String> output)throws Exception{
        Long.parseLong(pid);
        Path reports=ROOT.resolve("reports"),snapshots=ROOT.resolve("config/snapshots");
        Files.createDirectories(reports);Files.createDirectories(snapshots);
        String id=System.currentTimeMillis()+"-"+UUID.randomUUID();
        Path request=reports.resolve(id+".config-request.properties"),report=reports.resolve(id+".config-status.properties");
        Path snapshot=snapshots.resolve("live-"+id+".json"),agent=ASSETS.resolve("openvape-config-export.jar");
        if(!Files.isRegularFile(agent))throw new FileNotFoundException("Config exporter missing: "+agent);
        Properties p=new Properties();p.setProperty("gameDir",game.toAbsolutePath().toString());p.setProperty("output",snapshot.toString());p.setProperty("report",report.toString());
        try(OutputStream stream=Files.newOutputStream(request)){p.store(stream,"Local configuration export only");}
        output.accept("导出当前配置，PID "+pid+"；独立报告："+report);
        VirtualMachine vm=null;
        try{vm=VirtualMachine.attach(pid);
            if(!"1.8".equals(vm.getSystemProperties().getProperty("java.specification.version")))throw new IllegalArgumentException("目标需要 Java 8");
            try{vm.loadAgent(agent.toString(),request.toString());}
            catch(AgentLoadException compatibility){if(!"0".equals(compatibility.getMessage())||read(report).getProperty("state","").isEmpty())throw compatibility;}
        }finally{if(vm!=null)vm.detach();}
        long deadline=System.nanoTime()+30_000_000_000L;
        while(System.nanoTime()<deadline){
            Properties result=read(report);String state=result.getProperty("state","");
            if("FAILED".equals(state))throw new IOException("配置导出失败："+result.getProperty("detail","")+"；报告："+report);
            if("CONFIG_SAVED".equals(state)){
                byte[] data=Files.readAllBytes(snapshot);
                if(data.length<2)throw new IOException("Empty configuration snapshot; kept current settings unchanged");
                Path config=ROOT.resolve("config/settings.json");
                if(Files.exists(config))Files.copy(config,snapshots.resolve("previous-"+id+".json"));
                Path tmp=Files.createTempFile(config.getParent(),"settings-export-",".tmp");
                try{Files.write(tmp,data);try{Files.move(tmp,config,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException noAtomic){Files.move(tmp,config,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
                output.accept("CONFIG_SAVED：当前设置已导出，游戏保持运行。快照："+snapshot+"；下次加载配置："+config);return snapshot;
            }
            Thread.sleep(100);
        }
        throw new TimeoutException("等待客户端线程导出超过 30 秒；不重复执行。检查独立报告："+report);
    }
    static Path importConfiguration(Path source)throws Exception{
        if(Files.size(source)>16_777_216L)throw new IOException("配置文件超过 16 MiB");
        byte[] data=Files.readAllBytes(source);
        // Parse through bundled Gson without initializing the game payload.
        try(java.net.URLClassLoader parser=new java.net.URLClassLoader(new java.net.URL[]{ASSETS.resolve("openvape-macos-agent.jar").toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            Class<?> jsonParser=parser.loadClass("com.google.gson.JsonParser");
            Object element=jsonParser.getMethod("parseString",String.class).invoke(null,new String(data,java.nio.charset.StandardCharsets.UTF_8));
            Object object=element.getClass().getMethod("getAsJsonObject").invoke(element);
            Object profiles=object.getClass().getMethod("get",String.class).invoke(object,"profiles");
            if(profiles==null||!Boolean.TRUE.equals(profiles.getClass().getMethod("isJsonObject").invoke(profiles)))throw new IOException("缺少 profiles 配置档对象");
            boolean settings=Boolean.TRUE.equals(object.getClass().getMethod("has",String.class).invoke(object,"otherdata"))||Boolean.TRUE.equals(object.getClass().getMethod("has",String.class).invoke(object,"otherData"));
            if(!settings)throw new IOException("缺少客户端设置对象");
        }
        Path config=ROOT.resolve("config/settings.json");Files.createDirectories(config.getParent());
        if(Files.exists(config)){
            if(Arrays.equals(Files.readAllBytes(config),data))return config;
            Path snapshots=config.getParent().resolve("snapshots");Files.createDirectories(snapshots);Files.copy(config,snapshots.resolve("before-import-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".json"));
        }
        Path tmp=Files.createTempFile(config.getParent(),"settings-import-",".tmp");
        try{Files.write(tmp,data);try{Files.move(tmp,config,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,config,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
        return config;
    }
    private static void gui(){ CompactInjector.show(); }
    private static void writeDiagnostic(String pid,String action,Throwable failure,Consumer<String> output){
        try{Path dir=ROOT.resolve("reports");Files.createDirectories(dir);Path p=dir.resolve("injector-diagnostic-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".properties");Properties d=new Properties();d.setProperty("pid",pid);d.setProperty("action",action);d.setProperty("time",Long.toString(System.currentTimeMillis()));d.setProperty("exception",failure.toString());try(OutputStream os=Files.newOutputStream(p)){d.store(os,"Injector diagnostic; separate from agent report");}try(Writer w=Files.newBufferedWriter(p.resolveSibling(p.getFileName()+".log"))){failure.printStackTrace(new PrintWriter(w));}output.accept("注入器诊断报告："+p);
        }catch(Exception diagnosticFailure){output.accept("无法写入注入器诊断报告："+diagnosticFailure);}
    }
    private static void startReportMonitor(Path report,String pid,long token,JLabel status,Consumer<String> output,javax.swing.Timer[] monitor,long[] generation,long[] heartbeatAt,String[] lastHeartbeat){
        generation[0]=token;heartbeatAt[0]=System.currentTimeMillis();lastHeartbeat[0]="";
        java.util.concurrent.atomic.AtomicBoolean reading=new java.util.concurrent.atomic.AtomicBoolean(false);final long[] monitorStarted={System.currentTimeMillis()};final int[] distinctTimes={0};final String[] observedTime={""};
        javax.swing.Timer timer=new javax.swing.Timer(1000,null);monitor[0]=timer;timer.addActionListener(e->{if(generation[0]!=token||!reading.compareAndSet(false,true))return;new Thread(()->{Properties p=null;Exception err=null;Boolean alive=null;try{p=read(report);}catch(Exception x){err=x;}try{Optional<ProcessHandle> handle=ProcessHandle.of(Long.parseLong(pid));if(handle.isPresent())alive=handle.get().isAlive();}catch(Exception denied){/* Process visibility may be restricted; unknown is not exited. */}final Properties state=p;final Exception problem=err;final Boolean processAlive=alive;SwingUtilities.invokeLater(()->{reading.set(false);if(generation[0]!=token)return;if(problem!=null){status.setForeground(Color.RED);status.setText("● 红色 · 无法读取状态报告："+problem.getMessage());return;}String s=state.getProperty("state","");String detail=state.getProperty("detail","");String targetTime=state.getProperty("time","");String signature=targetTime+"|"+detail;
            boolean changed=!signature.equals(lastHeartbeat[0]);if(changed){lastHeartbeat[0]=signature;if(!targetTime.isEmpty()&&!targetTime.equals(observedTime[0])){observedTime[0]=targetTime;distinctTimes[0]++;}if(!detail.isEmpty())output.accept("状态监测 "+s+" · "+detail);}
            long now=System.currentTimeMillis();String health=health(s,targetTime,now,monitorStarted[0],distinctTimes[0],processAlive);
            if("RED".equals(health)){status.setForeground(Color.RED);if(Set.of("FAILED","RUNTIME_FAILED").contains(s))status.setText("● 红色 · 运行失败："+detail+" · 报告："+report);else if(Boolean.FALSE.equals(processAlive))status.setText("● 红色 · 目标进程已退出 · 报告："+report);else status.setText("● 红色 · 无心跳，疑似卡住（目标报告 time 超过 20 秒或等待超时）；不能据此确认崩溃 · 报告："+report);}
            else if("GREEN".equals(health)){status.setForeground(new Color(0,135,55));status.setText("● 活跃绿色 · 连续心跳正常 · "+detail+" · 报告："+report);}
            else if("GRAY".equals(health)){status.setForeground(Color.GRAY);status.setText("● 灰色 · "+s+" · 报告："+report);}
            else{status.setForeground(new Color(190,135,0));status.setText("● 黄色 · 等待实际心跳（"+(s.isEmpty()?"尚无状态":s)+"；需两个不同且新鲜的目标 time） · 报告："+report);}
        });},"OpenVape report read").start();});timer.start();
    }
    public static void main(String[] args)throws Exception{
        // The Java 17 macOS OpenGL Java2D pipeline can paint partial Swing text while
        // accessibility still exposes the complete string. Disable it before AWT init.
        // An explicit launcher setting remains available for controlled comparison.
        configureJava2D();
        if(args.length>0&&args[0].equals("diagnose")){
            System.out.println("ROOT="+ROOT);
            System.out.println("ASSETS="+ASSETS);
            System.out.println("JAVA="+System.getProperty("java.version")+" ARCH="+System.getProperty("os.arch"));
            if(!Files.isRegularFile(ASSETS.resolve("openvape-macos-agent.jar")))throw new FileNotFoundException("Agent path does not resolve");
            if(Boolean.getBoolean("openvape.portable")&&!Files.isRegularFile(ASSETS.resolve("openvape-config-export.jar")))throw new FileNotFoundException("Config export agent missing");
            if(ModuleLayer.boot().findModule("jdk.attach").isEmpty())throw new IllegalStateException("Attach module absent");
            System.out.println("PACKAGE_PATHS_OK");return;
        }
        if(args.length==0){
            // CLI commands must not initialize AppKit/AWT just to attach or diagnose.
            configureUiFont();
            SwingUtilities.invokeLater(()->{
                try{gui();}
                catch(Throwable error){JOptionPane.showMessageDialog(null,"E00","OpenVape",JOptionPane.ERROR_MESSAGE);}
            });return;
        }
        if(args[0].equals("list")){for(Target t:targets())System.out.println(t);return;}
        if(args[0].equals("save-config")){if(args.length<2)throw new IllegalArgumentException("Usage: save-config PID [game-directory]");saveCurrentConfiguration(args[1],args.length>2?Paths.get(args[2]):DEFAULT_GAME,System.out::println);return;}
        if(args.length<2)throw new IllegalArgumentException("Usage: inject|stop|probe PID [game-directory]");
        execute(args[0],args[1],args.length>2?Paths.get(args[2]):DEFAULT_GAME,System.out::println);
    }
}
