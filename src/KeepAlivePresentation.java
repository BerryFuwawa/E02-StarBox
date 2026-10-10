package com.e02.rootconsole;

/** Child selections stay saved while the background-keepalive master is off. */
final class KeepAlivePresentation {
    static boolean statusBar(boolean running,boolean master,boolean selected){return running&&master&&selected;}
    static boolean overlay(boolean running,boolean master,boolean selected,boolean permitted){return running&&master&&selected&&permitted;}
}
