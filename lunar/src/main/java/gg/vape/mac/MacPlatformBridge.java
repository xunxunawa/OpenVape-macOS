package gg.vape.mac;

import gg.vape.input.KeyboardCodeUtil;
import gg.vape.input.InputEventDispatcher;
import gg.vape.mac.bootstrap.MacAgent;
import java.io.*;
import java.lang.reflect.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/** Implements Win32-facing contracts with client-local Java/LWJGL behavior. */
public final class MacPlatformBridge {
    private static final int[] VK_TO_KEY=new int[256];
    private static final boolean[] SYNTHETIC=new boolean[5];
    static {
        for(int key=1;key<256;key++){int vk=KeyboardCodeUtil.convertLegacyKeyCode(key);if(vk>0&&vk<256&&VK_TO_KEY[vk]==0)VK_TO_KEY[vk]=key;}
    }
    public static int legacyKey(int virtualKey){return virtualKey>=0&&virtualKey<256?VK_TO_KEY[virtualKey]:0;}
    public static short keyState(int vk) {
        if(!Display.isCreated()||!Display.isActive()) return 0;
        boolean down;
        if(vk==1||vk==2||vk==4||vk==5||vk==6){int b=vk==1?0:vk==2?1:vk==4?2:vk==5?3:4;down=Mouse.isCreated()&&b<Mouse.getButtonCount()&&(Mouse.isButtonDown(b)||SYNTHETIC[b]);}
        else if(vk==16)down=Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)||Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        else if(vk==17)down=Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)||Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        else if(vk==18)down=Keyboard.isKeyDown(Keyboard.KEY_LMENU)||Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        else {int key=legacyKey(vk);down=key!=0&&Keyboard.isCreated()&&Keyboard.isKeyDown(key);}
        return (short)(down?0x100:0);
    }
    public static int mapKey(int code,int mode){
        if(mode==0)return legacyKey(code);
        if(mode==3||mode==1)return code>=0&&code<256?KeyboardCodeUtil.convertLegacyKeyCode(code):0;
        if(mode==2)return code>=32&&code<=126?code:code==8||code==9||code==13||code==27?code:0;
        throw new IllegalArgumentException("Unsupported key mapping mode "+mode);
    }
    public static String keyName(long data){String s=Keyboard.getKeyName((int)((data>>>16)&255));return s==null?"Unknown":s;}
    public static void mouseMessage(int mask,int message){
        int button=message==513||message==514?0:message==516||message==517?1:message==519||message==520?2:mask==1?0:mask==2?1:2;
        boolean pressed=message==513||message==516||message==519;
        SYNTHETIC[button]=pressed;
        InputEventDispatcher.getInstance().getMouseState().setButtonState(button,pressed);
        try{
            Object mc=MacLifecycle.game(); Object settings=MacAgent.field(mc.getClass(),new String[]{"gameSettings","field_71474_y"}).get(mc);
            String[] names=button==0?new String[]{"keyBindAttack","field_74312_F"}:button==1?new String[]{"keyBindUseItem","field_74313_G"}:new String[]{"keyBindPickBlock","field_74322_I"};
            Object binding=MacAgent.field(settings.getClass(),names).get(settings);
            int key=(Integer)MacAgent.method(binding.getClass(),new String[]{"getKeyCode","func_151463_i"}).invoke(binding);
            MacAgent.method(binding.getClass(),new String[]{"setKeyBindState","func_74510_a"},int.class,boolean.class).invoke(null,key,pressed);
            if(pressed)MacAgent.method(binding.getClass(),new String[]{"onTick","func_74507_a"},int.class).invoke(null,key);
        }catch(Exception e){throw new IllegalStateException("Minecraft mouse binding failed",e);}
    }
    public static void releaseSynthetic(){for(int b=0;b<3;b++)if(SYNTHETIC[b])mouseMessage(b==0?1:b==1?2:16,b==0?514:b==1?517:520);}
    public static byte[] resource(String name){
        if(name==null)return null;while(name.startsWith("/"))name=name.substring(1);
        try(InputStream in=MacPlatformBridge.class.getClassLoader().getResourceAsStream(name)){
            if(in==null)return null;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Resource read failed: "+name,e);}
    }
    public static Object invoke(Method method,Object target,Object[] args){
        try{method.setAccessible(true);return method.invoke(target,args);}
        catch(InvocationTargetException e){return MacPlatformBridge.<RuntimeException,Object>throwUnchecked(e.getCause());}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    @SuppressWarnings("unchecked")private static <T extends Throwable,R>R throwUnchecked(Throwable t)throws T{throw(T)t;}
}
