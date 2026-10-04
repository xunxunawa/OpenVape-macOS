package gg.vape.mac;

import gg.vape.input.*;
import gg.vape.mac.bootstrap.MacAgent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.objectweb.asm.*;

/** Observes the actual LWJGL queue without consuming an extra event or polling away short clicks. */
public final class MacInput {
    private static boolean active, focused;
    public static long keyboardEvents, mouseEvents, frames;
    public static void install() throws Exception {
        hook(Keyboard.class,"next","()Z","keyboardNext","(Z)Z");
        hook(Mouse.class,"next","()Z","mouseNext","(Z)Z");
        hook(Display.class,"update","(Z)V","frameUpdated","()V");
        MacAgent.inputCallbacks(MacInput.class); active=true;
        InputEventDispatcher.getInstance().setWindowHandle(0L);
    }
    public static void resume() throws Exception { keyboardEvents=0;mouseEvents=0;frames=0;active=true;MacAgent.inputCallbacks(MacInput.class); }
    public static void stop(){active=false;MacAgent.disableInput();}
    private static void hook(Class<?> target,final String name,final String desc,final String callback,final String callbackDesc){
        ClassReader reader=new ClassReader(MacAgent.bytes(target));final ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        final boolean[] found={false};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            public MethodVisitor visitMethod(int access,String n,String d,String sig,String[] exceptions){
                MethodVisitor parent=super.visitMethod(access,n,d,sig,exceptions);
                if(!n.equals(name)||!d.equals(desc))return parent;found[0]=true;
                return new MethodVisitor(Opcodes.ASM9,parent){public void visitInsn(int opcode){
                    if(opcode==(callbackDesc.equals("()V")?Opcodes.RETURN:Opcodes.IRETURN))
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/vape/mac/bootstrap/MacAgent",callback,callbackDesc,false);
                    super.visitInsn(opcode);
                }};
            }
        },0);
        if(!found[0])throw new IllegalStateException("LWJGL method unavailable: "+target.getName()+"."+name+desc);
        if(MacAgent.redefine(target,writer.toByteArray())!=0)throw new IllegalStateException("Cannot install LWJGL input bridge: "+target.getName());
    }
    public static boolean keyboardEvent(){
        if(!active)return false;keyboardEvents++;
        int legacy=Keyboard.getEventKey(),vk=KeyboardCodeUtil.convertLegacyKeyCode(legacy);
        boolean pressed=Keyboard.getEventKeyState(),cancel=false;
        if(vk!=0)cancel=InputEventDispatcher.getInstance().dispatch(pressed?256:257,vk,(long)legacy<<16);
        char character=Keyboard.getEventCharacter();
        if(pressed&&character!=0)cancel=InputEventDispatcher.getInstance().dispatch(258,character,0)||cancel;
        return cancel;
    }
    public static boolean mouseEvent(){
        if(!active)return false;mouseEvents++;
        long coordinates=(Mouse.getEventX()&65535L)|(((Display.getHeight()-1-Mouse.getEventY())&65535L)<<16);
        InputEventDispatcher.getInstance().dispatch(512,0,coordinates);
        int button=Mouse.getEventButton();boolean down=Mouse.getEventButtonState(),cancel=false;
        if(button>=0&&button<3){int code=button==0?513:button==1?516:519;cancel=InputEventDispatcher.getInstance().dispatch(down?code:code+1,0,coordinates);}
        else if(button==3||button==4)cancel=InputEventDispatcher.getInstance().dispatch(down?523:524,button==3?131072L:65536L,coordinates);
        int wheel=Mouse.getEventDWheel();if(wheel!=0)cancel=InputEventDispatcher.getInstance().dispatch(522,(long)(wheel&65535)<<16,coordinates)||cancel;
        return cancel;
    }
    public static boolean frame(){
        if(!active)return false;frames++;
        boolean now=Display.isActive();if(now!=focused){focused=now;InputEventDispatcher.getInstance().dispatch(now?7:8,0,0);}
        gg.vape.event.impl.EventRenderWorldPassExecutorDrain.EXECUTOR.runPending();
        MacLifecycle.frameObserved();return false;
    }
}
