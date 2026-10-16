package com.e02.rootconsole;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public final class TestAppLog {
 public static void main(String[] args)throws Exception{
  Path directory=Files.createTempDirectory("e02-app-log-");Path log=directory.resolve("app-events.log");long now=System.currentTimeMillis();
  try{
   String data=(now-AppLog.RETENTION-1)+"\t提权\texpired\n"+(now-1000)+"\t启动\tretained\n"+(now+60000)+"\t启动\tfuture\ninvalid\n";
   Files.write(log,data.getBytes(StandardCharsets.UTF_8));String value=AppLog.retained(directory.toFile(),now);
   if(value.contains("expired")||value.contains("future")||value.contains("invalid")||!value.contains("retained"))throw new AssertionError("retention boundary");
   AppLog.record(directory.toFile(),"提权\n", "已复制\n下一行\t内容");value=AppLog.read(directory.toFile());
   if(value.contains("expired")||!value.contains("已复制 下一行 内容")||value.split("\n").length!=2)throw new AssertionError("copyable sanitized log");
   Files.write(log,new byte[530000]);AppLog.record(directory.toFile(),"程序","恢复");if(!AppLog.read(directory.toFile()).contains("恢复"))throw new AssertionError("oversize recovery");
   StringBuilder large=new StringBuilder();for(int i=0;i<1800;i++)large.append(now-1).append("\t启动\t").append(new String(new char[200]).replace('\0','x')).append('\n');Files.write(log,large.toString().getBytes(StandardCharsets.UTF_8));AppLog.record(directory.toFile(),"程序","最新");if(Files.size(log)>256*1024||!AppLog.read(directory.toFile()).contains("最新"))throw new AssertionError("size bound");
   Files.write(log,((now-AppLog.RETENTION-1000)+"\t启动\t过期\n").getBytes(StandardCharsets.UTF_8));if(!"暂无日志".equals(AppLog.read(directory.toFile()))||Files.size(log)!=0)throw new AssertionError("read prunes disk");
   AppLog.record(directory.resolve("missing").toFile(),"程序","不可写");if(!AppLog.read(directory.resolve("missing").toFile()).contains("无法读取"))throw new AssertionError("unavailable storage");
   for(String[] event:new String[][]{{"WAKE","收到亮屏恢复通知"},{"COLD_START","收到开机通知"},{"REPLACED","收到应用更新通知"},{"UNKNOWN","收到后台恢复通知"}}){
    Files.deleteIfExists(log);AppLog.startup(directory.toFile(),"received",event[0]);value=AppLog.read(directory.toFile());
    if(!value.contains(event[1])||(!"REPLACED".equals(event[0])&&value.contains("收到应用更新通知")))throw new AssertionError("startup event cause: "+event[0]);
   }
   try(java.util.stream.Stream<Path> files=Files.list(directory)){if(files.anyMatch(p->p.toString().endsWith(".tmp")))throw new AssertionError("temporary files");}
   System.out.println("PASS app log: seven-day retention, future/malformed records, copying sanitation, bounded storage, read pruning, unavailable storage, exact boot/replaced/wake/unknown cause and atomic cleanup.");
  }finally{Files.deleteIfExists(log);Files.delete(directory);}
 }
}
