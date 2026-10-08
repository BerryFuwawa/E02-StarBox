package com.e02.rootconsole;
import org.json.JSONObject;

/** Immutable metadata shared by checking, probing, downloading and installing. */
public final class UpdateRelease {
 public final String version,url,sha,notes;public final long code,size;
 public UpdateRelease(JSONObject j)throws Exception{
  version=j.getString("version");code=j.getLong("versionCode");url=j.getString("apk");sha=j.getString("sha256").toLowerCase(java.util.Locale.US);size=j.getLong("size");notes=j.optString("notes","");
  UpdatePolicy.url("",url);
  if(!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||code<1||!sha.matches("[a-f0-9]{64}")||size<1024||size>50L*1024*1024||notes.length()>8192)throw new java.io.IOException("版本文件格式无效");
 }
 public JSONObject json()throws Exception{return new JSONObject().put("version",version).put("versionCode",code).put("apk",url).put("sha256",sha).put("size",size).put("notes",notes);}
}
