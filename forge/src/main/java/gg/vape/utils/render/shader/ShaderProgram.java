package gg.vape.utils.render.shader;

import gg.vape.ui.click.component.GuiComponent;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public class ShaderProgram {
    private static int currentProgramId;
    int previousProgramId;
    int geometryShaderId = 0;
    int vertexShaderId = 0;
    boolean linked;
    int programId;
    private final java.util.Map<Integer,Integer> uniformSlots = new java.util.HashMap<Integer,Integer>();
    int fragmentShaderId = 0;
    private static GuiComponent[] legacyComponents;

    public void compileAndLink(String vertexShaderSource, String fragmentShaderSource, String geometryShaderSource) {
        java.util.Map<Integer,String> names = gg.vape.mac.MacLegacyShaderSource.uniforms(vertexShaderSource);
        names.putAll(gg.vape.mac.MacLegacyShaderSource.uniforms(fragmentShaderSource));
        vertexShaderSource = gg.vape.mac.MacLegacyShaderSource.convert(vertexShaderSource, false);
        fragmentShaderSource = gg.vape.mac.MacLegacyShaderSource.convert(fragmentShaderSource, true);
        if (this.programId != 0) GL20.glDeleteProgram(this.programId);
        this.programId = GL20.glCreateProgram();
        this.vertexShaderId = GL20.glCreateShader((int)35633);
        this.fragmentShaderId = GL20.glCreateShader((int)35632);
        if (geometryShaderSource != null) {
            this.geometryShaderId = GL20.glCreateShader((int)36313);
        }
        GL20.glShaderSource((int)this.vertexShaderId, (CharSequence)vertexShaderSource);
        GL20.glShaderSource((int)this.fragmentShaderId, (CharSequence)fragmentShaderSource);
        if (geometryShaderSource != null) {
            GL20.glShaderSource((int)this.geometryShaderId, (CharSequence)geometryShaderSource);
        }
        GL20.glCompileShader((int)this.vertexShaderId);
        GL20.glCompileShader((int)this.fragmentShaderId);
        if (geometryShaderSource != null) {
            GL20.glCompileShader((int)this.geometryShaderId);
        }
        GL20.glAttachShader((int)this.programId, (int)this.vertexShaderId);
        GL20.glAttachShader((int)this.programId, (int)this.fragmentShaderId);
        if (geometryShaderSource != null) {
            GL20.glAttachShader((int)this.programId, (int)this.geometryShaderId);
        }
        GL20.glLinkProgram((int)this.programId);
        IntBuffer linkStatusBuffer = BufferUtils.createIntBuffer((int)1);
        gg.vape.wrapper.impl.GL20.x(this.programId, 35714, linkStatusBuffer);
        this.linked = linkStatusBuffer.get(0) == 1;
        uniformSlots.clear();
        if (this.linked) {
            for (java.util.Map.Entry<Integer,String> entry : names.entrySet())
                uniformSlots.put(entry.getKey(), GL20.glGetUniformLocation(this.programId, entry.getValue()));
        } else {
            gg.vape.mac.bootstrap.MacAgent.log("Shader failed " + getClass().getSimpleName()
                + " vertex=" + GL20.glGetShaderInfoLog(this.vertexShaderId, 4096)
                + " fragment=" + GL20.glGetShaderInfoLog(this.fragmentShaderId, 4096)
                + " link=" + GL20.glGetProgramInfoLog(this.programId, 4096));
        }
        GL20.glDeleteShader(this.vertexShaderId);
        GL20.glDeleteShader(this.fragmentShaderId);
        if (this.geometryShaderId != 0) GL20.glDeleteShader(this.geometryShaderId);
    }

    public ShaderProgram(String vertexShaderSource, String fragmentShaderSource, String geometryShaderSource) {
        compileAndLink(vertexShaderSource, fragmentShaderSource, geometryShaderSource);
    }

    private int uniformLocation(int slot) {
        Integer location = uniformSlots.get(slot);
        return location == null ? -1 : location;
    }
    protected void uniform1f(int slot, float x) { if (linked) GL20.glUniform1f(uniformLocation(slot), x); }
    protected void uniform1i(int slot, int x) { if (linked) GL20.glUniform1i(uniformLocation(slot), x); }
    protected void uniform2f(int slot, float x, float y) { if (linked) GL20.glUniform2f(uniformLocation(slot), x, y); }
    protected void uniform3f(int slot, float x, float y, float z) { if (linked) GL20.glUniform3f(uniformLocation(slot), x, y, z); }
    protected void uniform4f(int slot, float x, float y, float z, float w) { if (linked) GL20.glUniform4f(uniformLocation(slot), x, y, z, w); }

    public static GuiComponent[] getLegacyComponents() {
        return legacyComponents;
    }

    public static int getCurrentProgramId() {
        // Minecraft and other mods can bind programs without updating our cache.
        currentProgramId = GL11.glGetInteger(35725);
        return currentProgramId;
    }

    public boolean isLinked() {
        return this.linked;
    }

    public int getProgramId() {
        return this.programId;
    }

    public void restorePreviousProgram() {
        ShaderProgram.useProgram(this.previousProgramId);
    }


    public static void setCurrentProgramId(int programId) {
        currentProgramId = programId;
    }

    public void compileAndLink(String vertexShaderSource, String fragmentShaderSource) {
        this.compileAndLink(vertexShaderSource, fragmentShaderSource, null);
    }

    public static void useProgram(int programId) {
        GL20.glUseProgram((int)programId);
        ShaderProgram.setCurrentProgramId(programId);
    }

    public ShaderProgram(String vertexShaderSource, String fragmentShaderSource) {
        this(vertexShaderSource, fragmentShaderSource, null);
    }

    static {
        ShaderProgram.setLegacyComponents(new GuiComponent[4]);
        currentProgramId = -1;
    }

    public boolean bind() {
        if (!this.linked) {
            return false;
        }
        this.previousProgramId = ShaderProgram.getCurrentProgramId();
        ShaderProgram.useProgram(this.getProgramId());
        return true;
    }

    public static void setLegacyComponents(GuiComponent[] components) {
        legacyComponents = components;
    }
}

