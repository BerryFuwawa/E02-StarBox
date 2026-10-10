package com.e02.rootconsole;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.UUID;
import org.json.JSONObject;

/** One update task and one immutable UI snapshot; no platform installer intents. */
public class UpdateService extends Service {
 public enum Phase {IDLE,CHECKING,LATEST,FOUND,CHECK_FAILED,TESTING,DOWNLOADING,DOWNLOAD_FAILED,READY,INSTALLING,INSTALL_FAILED,COMPLETED,CANCELLED}
 public static final class State {
  public final Phase phase;public final String message,route;public final UpdateRelease release;public final long bytes;
  State(Phase p,String m,UpdateRelease r,String route,long n){phase=p;message=m;release=r;this.route=route;bytes=n;}
  public boolean working(){return phase==Phase.CHECKING||phase==Phase.TESTING||phase==Phase.DOWNLOADING||phase==Phase.INSTALLING;}
  public boolean ready(){return phase==Phase.READY||phase==Phase.INSTALL_FAILED;}
  public String notice(){switch(phase){case FOUND:return "发现更新 - "+release.version+" - 点击查看";case CHECK_FAILED:return "检测更新失败，点击查看";case DOWNLOADING:return message+" - 点击查看";case READY:return "下载完成 - "+release.version+" - 点击安装";case DOWNLOAD_FAILED:return "下载失败，点击查看";case INSTALLING:return "正在安装 - "+release.version;case INSTALL_FAILED:return "安装失败，点击查看";default:return "";}}
 }
 private static volatile State current=new State(Phase.IDLE,"尚未检查更新",null,"",0);
 public static volatile boolean busy,downloading;private static volatile boolean cancelled;private static volatile UpdateHttp transport;private static volatile Context app;private static volatile String mirror="";private static volatile boolean watching;private static boolean initialized;
 private static final int NOTIFICATION=21;
 public static State state(){return current;}
 private static synchronized void publish(Phase p,String m,UpdateRelease r,String route,long bytes){current=new State(p,m,r,route,bytes);busy=current.working();downloading=p==Phase.DOWNLOADING;}
 public static String installedName(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "版本未知";}}
 private static long installed(Context c)throws Exception{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode();}
 public static synchronized void initialize(Context context){app=context.getApplicationContext();if(initialized)return;initialized=true;
  for(String name:new String[]{"update.apk","update.part"}){File old=new File(app.getFilesDir(),"updates/"+name);if(old.isFile())old.delete();}
  File task=new File(UpdateTask.folder(app),"task.json"),status=new File(UpdateTask.folder(app),"status.json");
  if(task.isFile()&&status.isFile())try{JSONObject t=UpdateTask.read(task),s=UpdateTask.read(status);UpdateRelease r=new UpdateRelease(t.getJSONObject("release"));if(!t.getString("id").equals(s.getString("id")))return;Phase p=phase(s.getString("phase"));if(installed(app)>=r.code){publish(Phase.COMPLETED,"当前版本 "+installedName(app),null,"",0);if(p!=Phase.COMPLETED&&!(p==Phase.INSTALLING&&System.currentTimeMillis()-s.getLong("time")<240000))recover(t);return;}publish(p,s.getString("message"),r,t.getString("route"),s.optLong("bytes",0));if(p==Phase.DOWNLOADING||p==Phase.INSTALLING)watch(t);else if(p==Phase.READY||p==Phase.INSTALL_FAILED)publish(p,s.getString("message"),r,t.getString("route"),r.size);}catch(Exception ignored){publish(Phase.IDLE,"更新任务需重新检查",null,"",0);}
 }
 private static Phase phase(String value){return Phase.valueOf(value.toUpperCase(java.util.Locale.US));}
 private static synchronized boolean begin(Phase p,String message){if(busy||current.ready())return false;cancelled=false;mirror="";publish(p,message,current.release,current.route,0);return true;}
 public static void check(Context context,boolean manual){initialize(context);if(!RiskNotice.accepted(context)||!begin(Phase.CHECKING,"正在检查更新…"))return;
  String route=context.getSharedPreferences("updates",0).getString("proxy","");new Thread(()->{UpdateHttp http=new UpdateHttp();transport=http;try(Closeable deadline=http.deadline(20000)){UpdateRelease r=new UpdateRelease(new JSONObject(new String(http.manifest(route),"UTF-8")));if(cancelled)throw new IOException("已取消检查");publish(r.code>installed(app)?Phase.FOUND:Phase.LATEST,r.code>installed(app)?"发现新版本 "+r.version:"已是最新版",r,route,0);}catch(Exception e){publish(cancelled?Phase.CANCELLED:Phase.CHECK_FAILED,cancelled?"已取消检查":"检查失败："+UserMessages.explain(e),null,route,0);}finally{http.close();transport=null;}},"starbox-update-check").start();
 }
 public static void testMirrors(Context context){initialize(context);if(!RiskNotice.accepted(context)||!begin(Phase.TESTING,"正在测试镜像站…"))return;
  new Thread(()->{String found=MirrorSearch.first(UpdatePolicy.ROUTES,new MirrorSearch.Probe(){public boolean cancelled(){return UpdateService.cancelled;}public void testing(int i,String route){publish(Phase.TESTING,"正在测试 "+i+"/5："+route.replace("https://",""),null,route,0);}public void verify(String route)throws Exception{UpdateHttp http=new UpdateHttp();transport=http;try(Closeable deadline=http.deadline(10000)){UpdateRelease r=new UpdateRelease(new JSONObject(new String(http.manifest(route),"UTF-8")));http.probe(route,r.url);}finally{http.close();transport=null;}}});if(!found.isEmpty()){mirror=found;publish(Phase.IDLE,"镜像站可用："+found.replace("https://",""),null,found,0);}else publish(cancelled?Phase.CANCELLED:Phase.CHECK_FAILED,cancelled?"已取消网络测试":"暂未找到可用镜像站，请稍后重试或填写自定义线路",null,"",0);},"starbox-mirror-test").start();
 }
 public static synchronized String takeMirror(){String result=mirror;mirror="";return result;}
 public static void cancel(){State s=current;if(s.phase==Phase.INSTALLING)return;cancelled=true;mirror="";UpdateHttp http=transport;if(http!=null)http.close();if(app!=null&&s.phase==Phase.DOWNLOADING)try{File f=new File(UpdateTask.folder(app),"cancel");try(FileOutputStream out=new FileOutputStream(f)){out.write(1);}}catch(IOException ignored){} }
 public static void stopForRevocation(){cancel();}
 public static void download(Context context){initialize(context);State s=current;if(s.release==null||busy||s.ready())return;context.startForegroundService(new Intent(context,UpdateService.class).setAction("download"));}
 public static void install(Context context){initialize(context);if(!current.ready()||busy)return;context.startForegroundService(new Intent(context,UpdateService.class).setAction("install"));}
 private static void recover(JSONObject task){new Thread(()->{try{RootAccess.check(app,false);if(RootAccess.available())launch("recover");}catch(Exception ignored){}},"update-cleanup").start();}
 private static void launch(String operation)throws Exception{
  CommandRunner runner=RootAccess.updateRunner(app,operation);CommandRunner.Result result;try{result=runner.execute("",true,10);}finally{runner.close();}if(result.exit!=0||result.timedOut||!result.error.isEmpty())throw new IOException(result.error.isEmpty()?"无法完成更新任务":result.error);
 }
 private static synchronized void watch(JSONObject task){if(watching)return;watching=true;new Thread(()->{long start=System.currentTimeMillis(),seen=0;try{UpdateRelease r=new UpdateRelease(task.getJSONObject("release"));String route=task.getString("route");File f=new File(UpdateTask.folder(app),"status.json");
  while(System.currentTimeMillis()-start<660000){if(f.isFile())try{JSONObject value=UpdateTask.read(f);if(value.getString("id").equals(task.getString("id"))){Phase p=phase(value.getString("phase"));long stamp=value.getLong("time");if(stamp!=seen){seen=stamp;publish(p,value.getString("message"),p==Phase.COMPLETED?null:r,route,value.optLong("bytes",0));}if(p!=Phase.DOWNLOADING&&p!=Phase.INSTALLING)return;if(System.currentTimeMillis()-stamp>(p==Phase.INSTALLING?240000:60000))throw new IOException("更新任务已停止，请重试");}}catch(org.json.JSONException ignored){}if(seen==0&&System.currentTimeMillis()-start>10000)throw new IOException("更新任务未能启动，请重新检测 Root 后重试");Thread.sleep(500);}
  throw new IOException("更新等待超时，请重试");
 }catch(Exception e){State s=current;publish(s.phase==Phase.INSTALLING?Phase.INSTALL_FAILED:Phase.DOWNLOAD_FAILED,UserMessages.explain(e),s.release,s.route,s.bytes);}finally{watching=false;}},"starbox-update-status").start();}
 private void execute(String op){State before=current;new Thread(()->{try{RootAccess.check(this,false);if(!RootAccess.available())throw new IOException("请先完成 Root 提权，再下载或安装更新");if(cancelled)throw new IOException("已取消下载");File folder=UpdateTask.folder(this);if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建更新任务");File cancel=new File(folder,"cancel");if(cancel.exists()&&!cancel.delete())throw new IOException("无法准备更新任务");JSONObject task;
   if(op.equals("download")){task=new JSONObject().put("id",UUID.randomUUID().toString().replace("-","")).put("route",before.route).put("release",before.release.json());UpdateTask.write(new File(folder,"task.json"),task,android.os.Process.myUid());File status=new File(folder,"status.json");if(status.exists())status.delete();}
   else {task=UpdateTask.read(new File(folder,"task.json"));File status=new File(folder,"status.json");if(status.exists())status.delete();}
   if(cancelled)throw new IOException("已取消下载");watch(task);launch(op);
  }catch(Exception e){publish(op.equals("install")?Phase.INSTALL_FAILED:Phase.DOWNLOAD_FAILED,UserMessages.explain(e),before.release,before.route,before.bytes);stopForeground(true);stopSelf();}},"starbox-update-launch").start();}
 public void onCreate(){super.onCreate();initialize(this);NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);manager.createNotificationChannel(new NotificationChannel("starbox-update","星匣版本更新",NotificationManager.IMPORTANCE_LOW));startForeground(NOTIFICATION,notification());}
 private Notification notification(){State s=current;Notification.Builder b=new Notification.Builder(this,"starbox-update").setSmallIcon(getApplicationInfo().icon).setContentTitle("E02星匣").setContentText(s.message).setOngoing(s.working()).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT));if(s.phase==Phase.DOWNLOADING)b.addAction(0,"取消下载",PendingIntent.getService(this,0,new Intent(this,UpdateService.class).setAction("cancel"),PendingIntent.FLAG_UPDATE_CURRENT));return b.build();}
 private final Handler handler=new Handler();private final Runnable tick=new Runnable(){public void run(){if(!busy){stopForeground(true);stopSelf();return;}((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION,notification());handler.postDelayed(this,1000);}};
 public int onStartCommand(Intent intent,int flags,int id){if(!RiskNotice.accepted(this)){stopSelf();return START_NOT_STICKY;}String op=intent==null?"":intent.getAction();if("cancel".equals(op)){cancel();return START_NOT_STICKY;}State s=current;if(s.working()||s.release==null||!("download".equals(op)||"install".equals(op))){stopSelf();return START_NOT_STICKY;}cancelled=false;publish(op.equals("install")?Phase.INSTALLING:Phase.DOWNLOADING,op.equals("install")?"准备安装…":"准备下载…",s.release,s.route,0);execute(op);handler.removeCallbacks(tick);handler.postDelayed(tick,1000);return START_NOT_STICKY;}
 public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}public IBinder onBind(Intent intent){return null;}
}
