package com.e02.rootconsole;
import java.io.*;
import java.security.*;

/** Hashes the launch APK once: a current UI cannot mistake an old loaded daemon for its own build. */
final class RootServiceVersion {
 static final String PREFIX="registered-v5:";
 private static String cachedPath="",cachedHash="";private static long cachedLength,cachedModified;
 static synchronized String hash(File apk)throws IOException {
  String path=apk.getCanonicalPath();if(!apk.isFile()||apk.length()<1)throw new IOException("Missing service APK");
  if(path.equals(cachedPath)&&apk.length()==cachedLength&&apk.lastModified()==cachedModified)return cachedHash;
  try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(apk)){byte[] bytes=new byte[16384];int count;while((count=input.read(bytes))!=-1)digest.update(bytes,0,count);}
   StringBuilder value=new StringBuilder();for(byte b:digest.digest())value.append(String.format(java.util.Locale.US,"%02x",b&255));
   cachedPath=path;cachedLength=apk.length();cachedModified=apk.lastModified();cachedHash=value.toString();return cachedHash;
  }catch(NoSuchAlgorithmException impossible){throw new IOException(impossible);}
 }
 static boolean matches(String status,File apk){try{return (PREFIX+hash(apk)).equals(status);}catch(IOException invalid){return false;}}
}
