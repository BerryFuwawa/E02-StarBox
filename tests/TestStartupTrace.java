package com.e02.rootconsole;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class TestStartupTrace {
 public static void main(String[] args)throws Exception{
  Path directory=Files.createTempDirectory("e02-startup-trace-");
  try{
   for(int i=0;i<45;i++)StartupTrace.record(directory.toFile(),"received", "BOOT_"+i);
   List<String> lines=Files.readAllLines(directory.resolve("startup-events"),StandardCharsets.UTF_8);
   if(lines.size()!=20||!lines.get(0).endsWith("BOOT_25")||!lines.get(19).endsWith("BOOT_44"))throw new AssertionError("bounded event history");
   StartupTrace.record(directory.toFile(),"line\nbreak",new String(new char[500]).replace('\0','x'));
   lines=Files.readAllLines(directory.resolve("startup-events"),StandardCharsets.UTF_8);
   String last=lines.get(19);if(!last.contains("line_break ")||last.split(" ")[2].length()!=120)throw new AssertionError("single-line bounded fields");
   Files.write(directory.resolve("startup-events"),new byte[18000]);
   StartupTrace.record(directory.toFile(),"recovery","READY");
   if(Files.readAllLines(directory.resolve("startup-events")).size()!=1)throw new AssertionError("oversized history discarded");
   StartupTrace.record(directory.resolve("missing").toFile(),"received","BOOT");
   try(java.util.stream.Stream<Path> files=Files.list(directory)){if(files.anyMatch(p->p.toString().endsWith(".tmp")))throw new AssertionError("temporary artifact left behind");}
   System.out.println("PASS startup diagnostics: bounded history, field sanitation, oversize recovery, unavailable storage and temporary cleanup.");
  }finally{Files.deleteIfExists(directory.resolve("startup-events"));Files.deleteIfExists(directory.resolve("app-events.log"));Files.delete(directory);}
 }
}
