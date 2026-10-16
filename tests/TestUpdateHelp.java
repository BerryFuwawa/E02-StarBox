package com.e02.rootconsole;
public final class TestUpdateHelp {
 private static int checks;
 private static void check(boolean value){checks++;if(!value)throw new AssertionError("Update help check "+checks);}
 private static int occurrences(String value,String part){return (value.length()-value.replace(part,"").length())/part.length();}
 public static void main(String[] args){
  String hint=UpdateHelp.FALLBACK;
  check(UpdateHelp.notes(null).equals(hint));check(UpdateHelp.notes("").equals(hint));
  check(UpdateHelp.notes("更新内容").equals("更新内容\n\n"+hint));
  String duplicate=hint+"\r\n更新内容\r\n"+hint+"\r\n";
  String result=UpdateHelp.notes(duplicate);check(result.endsWith(hint));check(occurrences(result,hint)==1);check(result.startsWith("更新内容"));
  check(UpdateHelp.notes(result).equals(result));
  String custom="【安装帮助已更新，请通过官方发布页获取安装包。】";
  String online="在线说明\n\n"+custom;
  check(UpdateHelp.footer(online).equals(custom));check(UpdateHelp.notes(online).equals(online));
  check(UpdateHelp.message("下载状态",online).equals("下载状态\n\n"+online));
  check(!UpdateHelp.notes(hint+"\n内容\n"+custom).contains(hint));
  check(UpdateHelp.installationFailure("Root 授权已失效",online).startsWith("失败原因：Root 授权已失效"));
  check(UpdateHelp.installationFailure("Root 授权已失效",online).endsWith(custom));
  check(UpdateHelp.installationFailure("",null).contains("未收到明确的安装结果"));
  check(UpdateHelp.RELEASES.equals("https://github.com/BerryFuwawa/E02-StarBox/releases/latest"));
  System.out.println("Update help: "+checks+" checks passed");
 }
}
