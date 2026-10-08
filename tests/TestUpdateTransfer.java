package com.e02.rootconsole;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
public final class TestUpdateTransfer {
 static int checks;static void require(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static String hash(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));return s.toString();}
 static UpdateTransfer.Progress progress(boolean cancel){return new UpdateTransfer.Progress(){public boolean cancelled(){return cancel;}public void received(long bytes){}};}
 public static void main(String[] args)throws Exception{
  Path folder=Files.createTempDirectory("starbox-update-test-");byte[] data=new byte[65537];new Random(42).nextBytes(data);String sha=hash(data);
  try{File part=folder.resolve("success.part").toFile(),apk=folder.resolve("success.apk").toFile();UpdateTransfer.download(new ByteArrayInputStream(data),part,apk,data.length,sha,progress(false));require(apk.length()==data.length&&!part.exists(),"verified download is finalized without a partial copy");UpdateTransfer.verify(apk,data.length,sha);checks++;
   for(String failure:new String[]{"short","oversize","hash","cancel"}){part=folder.resolve(failure+".part").toFile();apk=folder.resolve(failure+".apk").toFile();long size=failure.equals("short")?data.length+1:failure.equals("oversize")?data.length-1:data.length;try{UpdateTransfer.download(new ByteArrayInputStream(data),part,apk,size,failure.equals("hash")?sha.replace('a','b').replace('c','d'):sha,progress(failure.equals("cancel")));throw new AssertionError("must reject "+failure);}catch(IOException expected){require(!part.exists()&&!apk.exists(),"failed "+failure+" leaves no installable file");}}
   part=folder.resolve("owned.part").toFile();Files.write(part.toPath(),new byte[]{7});apk=folder.resolve("occupied.apk").toFile();Files.write(apk.toPath(),new byte[]{8});try{UpdateTransfer.download(new ByteArrayInputStream(data),part,apk,data.length,sha,progress(false));throw new AssertionError("must not overwrite existing APK");}catch(IOException expected){require(Arrays.equals(Files.readAllBytes(apk.toPath()),new byte[]{8}),"existing APK not overwritten");require(Arrays.equals(Files.readAllBytes(part.toPath()),new byte[]{7}),"preexisting partial file not deleted");}
   byte[] altered=Files.readAllBytes(folder.resolve("success.apk"));altered[0]^=1;Files.write(folder.resolve("success.apk"),altered);try{UpdateTransfer.verify(folder.resolve("success.apk").toFile(),data.length,sha);throw new AssertionError("must reject a modified download");}catch(IOException expected){checks++;}
   System.out.println("Update streaming verification: "+checks+" checks passed");
  }finally{try(java.util.stream.Stream<Path> files=Files.list(folder)){files.forEach(path->{try{Files.delete(path);}catch(IOException ignored){}});}Files.delete(folder);}
 }
}
