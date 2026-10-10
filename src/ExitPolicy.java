package com.e02.rootconsole;

/** Only live background keepalive blocks exit. Boot preference survives an explicit exit. */
final class ExitPolicy {
    static boolean blocked(boolean keepAlive,boolean autoStart,boolean parkedRemote) { return keepAlive; }
    static String message(boolean keepAlive,boolean autoStart,boolean parkedRemote) {
        StringBuilder value=new StringBuilder("以下选项已开启：\n");
        if(keepAlive)value.append("• 星匣后台保活\n");
        return value.append("\n请先在【后台】中关闭以上功能，再退出程序及后台。").toString();
    }
}
