package com.e02.rootconsole;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Single-writer daemon log, separate from app logs; seven days, 32 KiB, codes only. */
final class WakeDiagnosticLog {
 static final long RETENTION=7L*24*60*60*1000;static final int LIMIT=32768;
 static synchronized void record(File directory,String stage,String result,long wall,long elapsed,long uptime){Path temporary=null;try{
  if(!stage.matches("[a-zA-Z0-9_.-]{1,64}")||!result.matches("[a-zA-Z0-9_.-]{1,96}"))return;
  File file=new File(directory,"wake-observer-events");StringBuilder output=new StringBuilder();
  if(file.isFile()&&file.length()<=LIMIT*2)for(String line:new String(Files.readAllBytes(file.toPath()),StandardCharsets.US_ASCII).split("\n")){
   try{long time=Long.parseLong(line.split(" ",2)[0]);if(time>wall-RETENTION&&time<=wall)output.append(line).append('\n');}catch(NumberFormatException ignored){}
  }
  output.append(wall).append(' ').append(elapsed).append(' ').append(uptime).append(' ').append(stage).append(' ').append(result).append('\n');
  byte[] bytes=output.toString().getBytes(StandardCharsets.US_ASCII);int start=Math.max(0,bytes.length-LIMIT);if(start>0){while(start<bytes.length&&bytes[start]!='\n')start++;start++;}
  temporary=Files.createTempFile(directory.toPath(),"wake-log-",".tmp");try(FileOutputStream stream=new FileOutputStream(temporary.toFile())){stream.write(bytes,start,bytes.length-start);stream.getFD().sync();}
  Files.move(temporary,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
 }catch(IOException|RuntimeException ignored){}finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}}
 static synchronized String read(File directory,long now){try{
  File file=new File(directory,"wake-observer-events");if(!file.isFile()||file.length()>LIMIT*2)return "";
  StringBuilder output=new StringBuilder();java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.CHINA);
  for(String line:new String(Files.readAllBytes(file.toPath()),StandardCharsets.US_ASCII).split("\n")){
   String[] values=line.split(" ",5);if(values.length!=5)continue;try{long at=Long.parseLong(values[0]);if(at<=now-RETENTION||at>now)continue;
    String stage=values[3],result=values[4],message;
    if(stage.equals("signal"))message=result.equals("default-display-on")?"收到中控亮屏通知":result.equals("default-display-recheck")?"收到中控状态变化，核对星匣后台是否运行":result.equals("default-display-reconnected")?"中控状态监听已恢复，核对星匣后台是否运行":"收到车辆自然唤醒通知";
    else if(stage.equals("display"))message=result.equals("off")?"中控已熄屏，停止本轮恢复":result.equals("endpoint-lost")?"中控状态服务连接已断开":"暂时无法读取中控状态，未确认恢复";
    else if(stage.equals("rebind"))message=result.equals("ready")?"已重新连接中控状态服务":result.equals("waiting-service")?"等待中控状态服务重新就绪":result.equals("registration-uncertain")?"无法确认旧监听已释放，未重复登记":result.equals("registration-unavailable")?"中控状态监听尚未重新建立":result.equals("permission-or-options-denied")?"恢复已停用或授权失效，未重新连接":"本轮中控状态监听恢复未完成";
    else if(stage.equals("register"))message=result.equals("ready")?"恢复通知监听已建立":"恢复通知监听未就绪";
    else if(stage.equals("auxiliary"))message=result.equals("PRESENT")?"辅助保活授权仍有效":result.equals("REPAIRED")?"已修复星匣辅助保活授权":result.equals("MASTER_OFF")?"系统无障碍总开关关闭，未修改":result.equals("DENIED")?"未满足辅助保活恢复条件":"辅助保活授权修复未完成";
    else if(stage.equals("service"))message=result.equals("receipt-confirmed")?"后台已确认收到恢复请求，连接状态仍需核对":result.equals("already-running")?"星匣后台仍在运行，未重复启动":result.equals("liveness-unknown")?"暂时无法确认星匣后台状态，未操作":result.equals("request-accepted")?"已发送后台恢复请求":"后台恢复请求尚未确认";
    else message=result.equals("coalesced")?"已合并重复的唤醒通知":result.equals("waiting-interactive")?"中控已亮屏，等待系统唤醒就绪":"当前恢复条件未满足，未继续操作";
    output.append(format.format(new java.util.Date(at))).append("  唤醒恢复 · ").append(message).append('\n');
   }catch(NumberFormatException ignored){}
  }return output.toString();
 }catch(IOException|RuntimeException unreadable){return "";}}
}
