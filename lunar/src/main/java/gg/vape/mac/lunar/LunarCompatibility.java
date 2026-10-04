package gg.vape.mac.lunar;

import gg.vape.mac.bootstrap.MacAgent;
import java.lang.reflect.*;
import java.util.*;

/** Owned, reversible setting leases for the explicitly verified Lunar build. */
public final class LunarCompatibility {
    private static final String CLIENT = "com.moonsworth.lunar.client.IOHOHIHHHHORRCCRHCRCHCRIOIHRHH";
    private static final List<Lease> leases = new ArrayList<Lease>();
    private static final class Lease {
        final Object option; final Method get, set; final boolean original;
        Lease(Object option) throws Exception {
            this.option=option;get=option.getClass().getMethod("get");
            set=option.getClass().getMethod("ROOCCIHRCCHORHCCOIIRIHHHIIHHHO",Object.class,boolean.class);
            Object value=get.invoke(option);
            if(!(value instanceof Boolean))throw new IllegalStateException("LUNAR_SETTING_NOT_BOOLEAN");
            original=((Boolean)value).booleanValue();
        }
        void disable() throws Exception { if(original)set.invoke(option,Boolean.FALSE,false); }
        void restore() throws Exception {
            if(original && Boolean.FALSE.equals(get.invoke(option)))set.invoke(option,Boolean.TRUE,false);
        }
    }
    public static void acquire(ClassLoader loader) throws Exception {
        if(!MacAgent.isLunarRuntime()||!leases.isEmpty())return;
        Class<?> type=Class.forName(CLIENT,false,loader);
        Object client=type.getMethod("CCCHRRHIOIIOICCIRHRIRCRRHIIIRR").invoke(null);
        Object manager=type.getMethod("OOICIIHROCCHRICCHCCHOROIHHRCIO").invoke(client);
        Object settings=manager.getClass().getMethod("OIRORICRIIICOHIOIRIICIIRICRHRH").invoke(manager);
        // Resolve both leases before changing either setting.
        Lease hud=new Lease(settings.getClass().getMethod("CCOCCHHOIIIHRHCIRHIOOCCROOHCHC").invoke(settings));
        Lease fps=new Lease(settings.getClass().getMethod("CCICOOHRCHOICROCCCHIIHOCRIIRCC").invoke(settings));
        leases.add(hud);leases.add(fps);
        try {
            hud.disable();fps.disable();
            MacAgent.log("Lunar settings leased: hudCaching="+hud.original+" -> false; shouldLimitUnfocusedFps="+fps.original+" -> false");
        } catch(Exception failure) {
            try { release(); } catch(Exception rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
    public static void release() throws Exception {
        Exception error=null;
        for(Lease lease:leases)try{lease.restore();}catch(Exception e){if(error==null)error=e;else error.addSuppressed(e);}
        if(error!=null)throw error;
        leases.clear();MacAgent.log("Lunar performance setting leases released");
    }
}
