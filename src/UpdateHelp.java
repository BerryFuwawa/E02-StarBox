package com.e02.rootconsole;

/** The last bracketed line in legacy notes remains editable by the publisher. */
public final class UpdateHelp {
 public static final String FALLBACK="【安装失败可以尝试使用EVCC更新或Github自行下载安装包更新。】";
 public static final String RELEASES=UpdatePolicy.REPO+"/releases/latest";
 public static final String METHODS="EVCC：在应用商城搜索“E02星匣”，下载安装更新。\n\nGitHub：打开官方发布页，下载安装包后覆盖安装。请使用官方原签名安装包。";
 private UpdateHelp(){}
 private static String clean(String value){return value==null?"":value.replace("\r\n","\n").replace('\r','\n').trim();}
 public static String footer(String notes){String text=clean(notes);int start=text.lastIndexOf('\n')+1;String last=text.substring(start).trim();return last.startsWith("【")&&last.endsWith("】")?last:FALLBACK;}
 public static String notes(String value){
  String text=clean(value),last=footer(text);StringBuilder body=new StringBuilder();
  for(String line:text.split("\n")){String trimmed=line.trim();if(trimmed.equals(FALLBACK)||trimmed.equals(last))continue;if(body.length()>0)body.append('\n');body.append(line);}
  String main=body.toString().trim();return main.isEmpty()?last:main+"\n\n"+last;
 }
 public static String message(String status,String notes){String first=clean(status);return first.isEmpty()?notes(notes):first+"\n\n"+notes(notes);}
 public static String installationFailure(String reason,String notes){String error=clean(reason);if(error.isEmpty())error="未收到明确的安装结果，请核对当前版本后重试。";return message("失败原因："+error+"\n\n"+METHODS,footer(notes));}
}
