package com.e02.rootconsole;

/** Plain messages for normal UI; command output and diagnostic logs retain details. */
public final class UserMessages {
 private UserMessages(){}
 public static String explain(Throwable error){return explain(error==null?null:error.getMessage());}
 public static String explain(String detail){
  if(detail==null||detail.trim().isEmpty())return "操作未完成，请重试";
  String lower=detail.toLowerCase(java.util.Locale.ROOT);
  if(lower.contains("connection refused")||lower.contains("failed to connect"))return "无法连接，请确认对应服务已启动";
  if(lower.contains("timed out")||lower.contains("timeout"))return "连接超时，请检查网络后重试";
  if(lower.contains("unknownhost")||lower.contains("unable to resolve")||lower.contains("name or service not known"))return "无法找到服务器，请检查地址和网络";
  if(lower.contains("permission denied")||lower.contains("eacces"))return "权限不足，请检查授权后重试";
  if(lower.contains("invalid bridge output length"))return "Root 返回结果异常，请重新授权后重试";
  if(lower.contains("no such file")||lower.contains("enoent"))return "所需文件不存在，请重新选择文件或重试";
  if(lower.contains("ssl")||lower.contains("certificate"))return "无法建立安全连接，请检查设备时间或更换连接线路";
  if(lower.contains("invalid")&&lower.contains("uri"))return "地址格式不正确，请检查后重试";
  // Application-authored Chinese validation messages already describe the action.
  if(detail.matches("(?s).*[\\u4e00-\\u9fff].*")&&!detail.contains("Exception")&&!detail.contains("/data/")&&!detail.contains("/system/"))return detail;
  return "操作未完成，请检查配置或查看诊断信息";
 }
}
