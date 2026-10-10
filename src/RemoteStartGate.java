package com.e02.rootconsole;

/** Process-local ownership of pending starts. Manual stop invalidates pending workers. */
final class RemoteStartGate {
    private long generation;
    private boolean preparing,automaticClaimed;
    synchronized boolean claimAutomatic(){if(automaticClaimed)return false;automaticClaimed=true;return true;}
    synchronized long begin(){if(preparing)return -1;preparing=true;return ++generation;}
    synchronized boolean current(long ticket){return preparing&&ticket==generation;}
    synchronized boolean preparing(){return preparing;}
    synchronized void finish(long ticket){if(ticket==generation)preparing=false;}
    synchronized void cancel(){generation++;preparing=false;}
}
