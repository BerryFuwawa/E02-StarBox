package com.e02.rootconsole;

public final class TestStatusEntryPublisher {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Host implements StatusEntryPublisher.Driver {
  boolean wanted=true,allowed=true,throwsPost,flipDuringPost;int posts,removes;boolean present;
  public boolean current(boolean enabled){return allowed&&wanted==enabled;}
  public void publish(boolean enabled)throws Exception{
   if(throwsPost)throw new Exception("fixture failure");
   if(enabled){posts++;present=true;}else{removes++;present=false;}
   if(flipDuringPost){wanted=!wanted;flipDuringPost=false;}
  }
  StatusEntryPublisher.Presence presence(){return present?StatusEntryPublisher.Presence.PRESENT:StatusEntryPublisher.Presence.ABSENT;}
 }
 public static void main(String[] args){
  Host host=new Host();StatusEntryPublisher p=new StatusEntryPublisher();
  check(p.refresh(true,false,host.presence(),host)==StatusEntryPublisher.Result.PUBLISHED&&host.posts==1,"initial own entry published");
  for(int i=0;i<20;i++)p.refresh(true,false,host.presence(),host);
  check(host.posts==1,"ordinary ticks do not churn healthy entry");
  host.present=false;check(p.refresh(true,false,host.presence(),host)==StatusEntryPublisher.Result.PUBLISHED&&host.posts==2,"removed own entry restored while preference remains enabled");
  check(p.refresh(true,true,host.presence(),host)==StatusEntryPublisher.Result.PUBLISHED&&host.posts==3,"natural restore can republish existing entry");
  host.throwsPost=true;check(p.refresh(true,true,host.presence(),host)==StatusEntryPublisher.Result.FAILED,"publication error returned");host.throwsPost=false;
  check(p.refresh(true,false,host.presence(),host)==StatusEntryPublisher.Result.PUBLISHED&&host.posts==4,"failed attempt not cached as success");
  host.wanted=false;check(p.refresh(false,false,host.presence(),host)==StatusEntryPublisher.Result.PUBLISHED&&host.removes==1&&!host.present,"user disable removes own entry");
  check(p.refresh(false,true,host.presence(),host)==StatusEntryPublisher.Result.UNCHANGED&&host.posts==4,"delayed restore cannot re-enable user-disabled entry");
  host.allowed=false;host.wanted=true;check(p.refresh(true,true,host.presence(),host)==StatusEntryPublisher.Result.CANCELLED&&host.posts==4,"exit policy suppresses delayed publication");
  host.allowed=true;host.flipDuringPost=true;check(p.refresh(true,true,host.presence(),host)==StatusEntryPublisher.Result.CANCELLED,"choice changed during publication does not commit stale success");
  check(p.refresh(false,false,host.presence(),host)==StatusEntryPublisher.Result.PUBLISHED&&!host.present,"next reconciliation clears stale own entry");
  Host unknown=new Host();StatusEntryPublisher q=new StatusEntryPublisher();q.refresh(true,false,StatusEntryPublisher.Presence.UNKNOWN,unknown);
  int posts=unknown.posts;check(q.refresh(true,false,StatusEntryPublisher.Presence.UNKNOWN,unknown)==StatusEntryPublisher.Result.UNCHANGED&&unknown.posts==posts,"unknown host read not treated as actual removal");
  unknown.present=false;q.refresh(true,false,unknown.presence(),unknown);check(unknown.posts==posts+1,"later actual absence triggers recovery");
  Host disabled=new Host();disabled.wanted=false;StatusEntryPublisher d=new StatusEntryPublisher();
  check(d.refresh(false,true,StatusEntryPublisher.Presence.ABSENT,disabled)==StatusEntryPublisher.Result.UNCHANGED&&disabled.removes==0,"initial disabled state does not publish hidden placeholder");
  disabled.present=true;check(d.refresh(false,false,disabled.presence(),disabled)==StatusEntryPublisher.Result.PUBLISHED&&disabled.removes==1,"disabled preference removes surviving old own entry");
  System.out.println("PASS "+checks+" own-entry publication recovery checks; physical Flyme icon display not exercised.");
 }
}
