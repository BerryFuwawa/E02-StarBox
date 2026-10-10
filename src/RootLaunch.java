package com.e02.rootconsole;
/** A saved finite grant must never become unlimited. Managed launches carry a revocable generation. */
public final class RootLaunch {
 public static void validate(String[] args){if(args==null)throw new IllegalArgumentException("授权命令已更新，请重新复制提权命令");if(args.length==1)return;if(args.length==2&&"0".equals(args[1]))return;if(args.length==3&&"--managed".equals(args[1])&&args[2]!=null&&args[2].matches("[0-9a-f]{32}"))return;throw new IllegalArgumentException("授权命令已更新，请重新复制提权命令");}
 static String generation(String[] args){validate(args);return args.length==3?args[2]:null;}
}
