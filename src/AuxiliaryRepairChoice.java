package com.e02.rootconsole;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Separate durable consent; an older accessibility choice never implies automatic repair. */
final class AuxiliaryRepairChoice {
 static boolean enabled(File directory)throws IOException {
  File file=new File(directory,"auxiliary-repair-choice");if(!file.exists())return false;
  if(!file.isFile()||file.length()>32)throw new IOException("Invalid auxiliary choice");
  String value=new String(Files.readAllBytes(file.toPath()),StandardCharsets.US_ASCII);
  if(value.equals("E02-AUX-REPAIR-1:1\n"))return true;if(value.equals("E02-AUX-REPAIR-1:0\n"))return false;
  throw new IOException("Invalid auxiliary choice");
 }
 static boolean write(File directory,boolean enabled){Path temporary=null;try{
  temporary=Files.createTempFile(directory.toPath(),"aux-choice-",".tmp");
  try(FileOutputStream out=new FileOutputStream(temporary.toFile())){out.write(("E02-AUX-REPAIR-1:"+(enabled?"1":"0")+"\n").getBytes(StandardCharsets.US_ASCII));out.getFD().sync();}
  Files.move(temporary,new File(directory,"auxiliary-repair-choice").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return true;
 }catch(IOException|RuntimeException failed){return false;}finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}}
}
