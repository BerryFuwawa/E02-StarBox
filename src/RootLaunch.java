package com.e02.rootconsole;
/** Legacy zero is safe to accept; a saved finite grant must never become unlimited. */
public final class RootLaunch {
 public static void validate(String[] args){if(args.length==1)return;if(args.length==2&&"0".equals(args[1]))return;throw new IllegalArgumentException("授权命令已更新，请重新复制提权命令");}
}
