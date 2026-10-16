package com.e02.rootconsole;

import android.content.Context;
import android.os.*;
import java.lang.reflect.*;

/** Fixed OEM parking-comfort property only. No wake locks, low-battery or sleep-policy writes. */
public final class NativeParkingPower {
    private final ClassLoader loader;private final Class<?> ids,mappingType,apiType;
    private final Object wrapper,api;private final Class<?> wrapperType;
    private NativeParkingPower()throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT!=28||!"P145".equals(Build.MODEL))throw new SecurityException("Unsupported vehicle");
        if(Looper.myLooper()==null)Looper.prepareMainLooper();
        Class<?> thread=Class.forName("android.app.ActivityThread");Object instance=thread.getMethod("systemMain").invoke(null);
        Context system=(Context)thread.getMethod("getSystemContext").invoke(instance);
        Context context=system.createPackageContext("com.flyme.auto.settings",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        loader=context.getClassLoader();ids=loader.loadClass("com.flyme.auto.api.AutoFuncId");
        wrapper=loader.loadClass("com.ecarx.xui.adaptapi.car.Car").getMethod("createWrapper",Context.class).invoke(null,context);
        wrapperType=loader.loadClass("com.ecarx.xui.adaptapi.car.IWrapper");mappingType=loader.loadClass("com.ecarx.xui.adaptapi.car.IWrapper$IPropertyId");
        Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"car_service");
        Object car=loader.loadClass("android.car.ICar$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        Object propertyBinder=loader.loadClass("android.car.ICar").getMethod("getCarService",String.class).invoke(car,"property");
        apiType=loader.loadClass("android.car.hardware.property.ICarProperty");
        api=loader.loadClass("android.car.hardware.property.ICarProperty$Stub").getMethod("asInterface",IBinder.class).invoke(null,propertyBinder);
    }
    private int id(String name)throws Exception{return ids.getField("id").getInt(ids.getField(name).get(null));}
    private Object mapping(String name)throws Exception {
        Object value=ids.getField(name).get(null);
        Object result=wrapperType.getMethod("getWrappedPropertyId",int.class,int.class).invoke(wrapper,ids.getField("idType").getInt(value),ids.getField("id").getInt(value));
        if(result==null)throw new IllegalStateException("Mapping unavailable");return result;
    }
    private int property(Object mapping)throws Exception{return (Integer)mappingType.getMethod("getPropertyId").invoke(mapping);}
    private Number read(Object mapping)throws Exception {
        Object value=apiType.getMethod("getProperty",int.class,int.class).invoke(api,property(mapping),0);
        if(value==null||(Integer)value.getClass().getMethod("getStatus").invoke(value)!=0)throw new IllegalStateException("Property unavailable");
        Object raw=value.getClass().getMethod("getValue").invoke(value);
        if(!(raw instanceof Number))throw new IllegalStateException("Unexpected property type");return (Number)raw;
    }
    private int adapt(Object mapping,int raw)throws Exception{return (Integer)mappingType.getMethod("getPropertyAdaptValue",int.class).invoke(mapping,raw);}
    private int gear()throws Exception {
        // CURRENT_GEAR is a sensor-style AutoFuncId with no wrapper mapping on P145.
        // Its verified native property returns the same gear flags (P=4), status checked.
        int property=id("CURRENT_GEAR");if(property!=289408001)throw new SecurityException("Unexpected gear property");
        Object value=apiType.getMethod("getProperty",int.class,int.class).invoke(api,property,0);
        if(value==null||(Integer)value.getClass().getMethod("getStatus").invoke(value)!=0)throw new IllegalStateException("Gear unavailable");
        Object raw=value.getClass().getMethod("getValue").invoke(value);if(!(raw instanceof Number))throw new IllegalStateException("Invalid gear");return ((Number)raw).intValue();
    }
    private boolean restorable(int target)throws Exception {
        for(Field field:ids.getFields())if(field.getName().startsWith("PARKING_COMFORT_MODE_TIMER_")&&!field.getName().endsWith("COUNTDOWN")&&id(field.getName())==target)return true;
        return false;
    }
    private String report()throws Exception {
        Object timer=mapping("SETTING_FUNC_PCM_TIMER");if(property(timer)!=557871196)throw new SecurityException("Unexpected timer property");
        int raw=read(timer).intValue(),gear=-1;float battery=-1;
        try{gear=gear();}catch(Exception unavailable){}
        try{battery=read(mapping("CURRENT_BATTLE_SOC")).floatValue();if(battery<0||battery>100)battery=-1;}catch(Exception unavailable){}
        return "E02_POWER_V1 "+raw+" "+adapt(timer,raw)+" "+id("PARKING_COMFORT_MODE_TIMER_ALWAYS_ON")+" "+id("PARKING_COMFORT_MODE_TIMER_0")+" "+gear+" "+battery+" "+(restorable(adapt(timer,raw))?1:0);
    }
    private void compareAndSet(int expected,int target)throws Exception {
        Object timer=mapping("SETTING_FUNC_PCM_TIMER");
        if(property(timer)!=557871196||read(timer).intValue()!=expected)throw new IllegalStateException("Setting changed");
        if(gear()!=id("VALUE_CURRENT_GEAR_PARK"))throw new SecurityException("Park required");
        if(!restorable(target))throw new SecurityException("Unsupported timer value");
        if(adapt(timer,expected)==target)return;
        Integer raw=(Integer)mappingType.getMethod("getPropertyValue",int.class).invoke(timer,target);
        if(raw==null||adapt(timer,raw)!=target)throw new SecurityException("Invalid timer mapping");
        // Recheck immediately before the one OEM property write.
        if(read(timer).intValue()!=expected||gear()!=id("VALUE_CURRENT_GEAR_PARK"))throw new IllegalStateException("Vehicle state changed");
        Class<?> valueType=loader.loadClass("android.car.hardware.CarPropertyValue");
        Object value=valueType.getConstructor(int.class,int.class,Object.class).newInstance(property(timer),0,raw);
        apiType.getMethod("setProperty",valueType).invoke(api,value);
        for(int i=0;i<10;i++){if(adapt(timer,read(timer).intValue())==target)return;Thread.sleep(150);}
        throw new IllegalStateException("Readback failed");
    }
    public static void main(String[] args) {
        try {
            NativeParkingPower power=new NativeParkingPower();
            if(args.length==3&&"apply".equals(args[0]))power.compareAndSet(Integer.parseInt(args[1]),Integer.parseInt(args[2]));
            else if(args.length!=1||!"read".equals(args[0]))throw new IllegalArgumentException("Unsupported operation");
            System.out.println(power.report());System.exit(0);
        }catch(Throwable error){System.out.println("E02_POWER_ERROR "+error.getClass().getSimpleName());System.exit(2);}
    }
}
