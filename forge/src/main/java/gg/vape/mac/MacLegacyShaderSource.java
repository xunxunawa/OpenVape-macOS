package gg.vape.mac;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GLSL 1.20 is the legacy compatibility profile used by Minecraft 1.8.9 on macOS. */
public final class MacLegacyShaderSource {
    private static final Pattern UNIFORM = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*uniform\\s+\\w+\\s+(\\w+)");
    private MacLegacyShaderSource() {}
    public static boolean needsLegacy(String source) { return source.contains("#version 430 compatibility"); }
    public static Map<Integer,String> uniforms(String source) {
        Map<Integer,String> result = new LinkedHashMap<Integer,String>();
        Matcher m = UNIFORM.matcher(source);
        while (m.find()) result.put(Integer.parseInt(m.group(1)), m.group(2));
        return result;
    }
    public static String convert(String source, boolean fragment) {
        if (!needsLegacy(source)) return source;
        source = source.replace("#version 430 compatibility", "#version 120");
        source = source.replaceAll("layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)\\s*", "");
        source = source.replaceAll("precision\\s+\\w+\\s+float\\s*;", "");
        if (fragment) {
            Matcher output = Pattern.compile("\\bout\\s+vec4\\s+(\\w+)\\s*;").matcher(source);
            String name = output.find() ? output.group(1) : null;
            source = source.replaceAll("\\bout\\s+vec4\\s+\\w+\\s*;", "");
            if (name != null) source = source.replaceAll("\\b" + Pattern.quote(name) + "\\b", "gl_FragColor");
            source = source.replaceAll("\\bin\\s+(vec[234]|float)\\s+", "varying $1 ");
        } else source = source.replaceAll("\\bout\\s+(vec[234]|float)\\s+", "varying $1 ");
        return source.replaceAll("\\btexture\\s*\\(", "texture2D(");
    }
}
