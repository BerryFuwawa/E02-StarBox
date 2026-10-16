package com.e02.rootconsole;

import android.content.Context;
import java.io.IOException;

/** Bound fixed command adapter. Only a user action may change the OEM setting. */
final class ParkingPower {
    static final class Reading {
        final int raw,value,always,off,gear;final float battery;final boolean restorable;
        Reading(String line)throws IOException {
            try{String[] p=line.trim().split(" ");if(p.length!=8||!"E02_POWER_V1".equals(p[0])||!p[7].matches("[01]"))throw new IllegalArgumentException();raw=Integer.parseInt(p[1]);value=Integer.parseInt(p[2]);always=Integer.parseInt(p[3]);off=Integer.parseInt(p[4]);gear=Integer.parseInt(p[5]);battery=Float.parseFloat(p[6]);restorable="1".equals(p[7]);}
            catch(IllegalArgumentException error){throw new IOException("原厂供电状态暂不可用",error);}
        }
        boolean online(){return value==always;}
    }
    static Reading read(Context context)throws IOException{return invoke(context,"read");}
    static Reading apply(Context context,Reading expected,int target)throws IOException {
        if(expected.gear!=4)throw new IOException("请挂 P 档并安全驻车后操作");
        return invoke(context,"apply "+expected.raw+" "+target);
    }
    private static Reading invoke(Context context,String operation)throws IOException {
        String apk=context.getApplicationInfo().sourceDir;
        if(apk==null||!apk.matches("/data/app/[A-Za-z0-9._=/+-]+/base\\.apk"))throw new IOException("无法读取当前安装位置");
        CommandRunner runner=RootAccess.runner(context);
        try {
            CommandRunner.Result result=runner.execute("CLASSPATH='"+apk+"' /system/bin/timeout 8 /system/bin/app_process /system/bin com.e02.rootconsole.NativeParkingPower "+operation,true,12);
            if(!result.error.isEmpty())throw new IOException(result.error);
            if(result.timedOut)throw new IOException("供电检测超时，请稍后重新检查");
            if(result.exit!=0)throw new IOException("无法确认原厂供电设置，请检查 Root 授权、P 档及车型支持情况");
            for(String line:result.stdout.split("\n"))if(line.startsWith("E02_POWER_V1 "))return new Reading(line);
            throw new IOException("未取得有效的原厂供电状态");
        }finally{runner.close();}
    }
}
