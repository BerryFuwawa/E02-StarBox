package com.e02.rootconsole;

/** Keep selected mappings intact while allowing the available channel to aid recovery. */
final class ChannelRecovery {
    static boolean webRoot(boolean requested,boolean available){return requested&&available;}
    static boolean mayStart(boolean webSelected,boolean webReady,boolean adbSelected,boolean adbReady){
        if(!webSelected&&!adbSelected)return false;
        if(webSelected&&!webReady)return false;
        return adbSelected&&adbReady||webSelected&&webReady;
    }
}
