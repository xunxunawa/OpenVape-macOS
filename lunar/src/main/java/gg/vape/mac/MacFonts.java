package gg.vape.mac;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Mac font lookup; bundled fonts provide a portable fallback. */
public final class MacFonts {
    private static final List<Path> fonts=new ArrayList<Path>();
    private static boolean scanned;
    public static synchronized byte[] bytes(String requested){
        if(!scanned){
            scanned=true;
            Path[] roots={Paths.get(System.getProperty("user.home"),"Library/Fonts"),Paths.get("/Library/Fonts"),Paths.get("/System/Library/Fonts")};
            for(Path root:roots)if(Files.isDirectory(root))try(java.util.stream.Stream<Path> paths=Files.walk(root,3)){
                paths.filter(Files::isRegularFile).filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".ttf")).sorted().forEach(fonts::add);
            }catch(IOException ignored){}
        }
        String wanted=requested.toLowerCase(Locale.ROOT).replace(".ttf","").replace(" ","");
        if(wanted.equals("arialbd"))wanted="arialbold";
        for(Path font:fonts){String name=font.getFileName().toString().toLowerCase(Locale.ROOT).replace(".ttf","").replace(" ","");
            if(name.equals(wanted))try{return Files.readAllBytes(font);}catch(IOException ignored){}
        }
        byte[] fallback=MacPlatformBridge.resource("resources/"+(wanted.contains("bold")||wanted.endsWith("bd")?"proximabd.ttf":"proxima.ttf"));
        if(fallback==null||fallback.length==0)throw new IllegalStateException("Bundled fallback font missing");
        return fallback;
    }
}
