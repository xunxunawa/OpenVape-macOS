import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Equivalent package relocation for the two bytecode libraries used by the payload. */
public final class JarAssembler {
    static final Map<String,byte[]> entries=new TreeMap<String,byte[]>();
    static final Remapper remapper=new Remapper(){
        public String map(String name){
            if(name.startsWith("javassist/"))return "gg/vape/shaded/"+name;
            if(name.startsWith("org/objectweb/asm/"))return "gg/vape/shaded/"+name;
            return name;
        }
        public Object mapValue(Object value){
            if(value instanceof String){String s=(String)value;
                if(s.startsWith("javassist.")||s.startsWith("org.objectweb.asm."))return "gg.vape.shaded."+s;
            }
            return super.mapValue(value);
        }
    };
    static void add(String name,byte[] data){
        if(name.endsWith("/"))return;
        if(name.equals("module-info.class")||name.startsWith("META-INF/versions/")||name.equals("META-INF/MANIFEST.MF")||name.matches("META-INF/.*\\.(SF|RSA|DSA)"))return;
        if(name.endsWith(".class")){
            ClassReader r=new ClassReader(data);ClassWriter w=new ClassWriter(0);r.accept(new ClassRemapper(w,remapper),0);data=w.toByteArray();
            name=remapper.map(name.substring(0,name.length()-6))+".class";
        }
        if(!entries.containsKey(name))entries.put(name,data);
    }
    static void directory(Path root)throws IOException{
        if(!Files.exists(root))return;
        try(java.util.stream.Stream<Path> s=Files.walk(root)){
            for(Iterator<Path> it=s.filter(Files::isRegularFile).iterator();it.hasNext();){Path p=it.next();add(root.relativize(p).toString().replace(File.separatorChar,'/'),Files.readAllBytes(p));}
        }
    }
    public static void main(String[] args)throws Exception{
        directory(Paths.get(args[1]));directory(Paths.get(args[2]));
        for(int i=3;i<args.length;i++)try(JarFile j=new JarFile(args[i])){
            for(Enumeration<JarEntry> e=j.entries();e.hasMoreElements();){JarEntry entry=e.nextElement();if(entry.isDirectory())continue;
                try(InputStream in=j.getInputStream(entry)){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);add(entry.getName(),b.toByteArray());}
            }
        }
        Manifest manifest=new Manifest();Attributes a=manifest.getMainAttributes();a.putValue("Manifest-Version","1.0");a.putValue("Agent-Class","gg.vape.mac.bootstrap.MacAgent");a.putValue("Can-Redefine-Classes","true");a.putValue("Can-Retransform-Classes","true");a.putValue("Implementation-Title","OpenVape macOS source port");
        try(JarOutputStream out=new JarOutputStream(new FileOutputStream(args[0]),manifest)){
            for(Map.Entry<String,byte[]> e:entries.entrySet()){JarEntry entry=new JarEntry(e.getKey());entry.setTime(0L);out.putNextEntry(entry);out.write(e.getValue());out.closeEntry();}
        }
        System.out.println("Packaged entries: "+entries.size());
    }
}
