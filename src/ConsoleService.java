package com.e02.rootconsole;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;

/** Owns live sessions; configured recovery uses actual identities and never holds a wake lock. */
public class ConsoleService extends Service {
 private static final int STATUS_NOTIFICATION=23; // Separate from console 20, update 21 and FRP 22.
 public static volatile ConsoleServer server;
 public static volatile String url="",pin="",error="";
 public static volatile boolean root,bridge;
 private WindowManager windows;private Badge badge;private WindowManager.LayoutParams layout;
 private Handler handler=new Handler();private boolean capturing,stopping,started,hadRoot,statusPublished;private int statusVisibility=-1;
 private static Context sessionContext;private android.net.ConnectivityManager connectivity;private android.net.ConnectivityManager.NetworkCallback networkCallback;
 public static boolean active(Context c){boolean work=MainActivity.visible()||KeepAlivePresentation.overlay(RuntimeSettings.runningAllowed(c),RuntimeSettings.snapshot(c).keepAlive,c.getSharedPreferences("connection",0).getBoolean("overlay",true),Settings.canDrawOverlays(c))||server!=null||RemoteService.running||UpdateService.downloading||c.getSharedPreferences("connection",0).getBoolean("adbManaged",false)||PhoneCompanion.connected()||RuntimeRecovery.working()||RemoteStartController.preparing();return RuntimeSettings.runningAllowed(c)&&RecoveryPolicy.serviceWanted(RuntimeSettings.snapshot(c),work,RuntimeSettings.wakeRecovery(c));}
 private final Runnable tick=new Runnable(){public void run(){if(!active(ConsoleService.this)){stopSelf();return;}refreshStatusEntry();refreshBadge();PhoneCompanion.refresh();refreshSessionNetwork();RootAccess.refresh(ConsoleService.this);AdbMaintenance.refresh(ConsoleService.this);boolean ready=RootAccess.available();if(ready&&!hadRoot)RuntimeRecovery.request(ConsoleService.this);hadRoot=ready;handler.postDelayed(this,3000);}};
 public static synchronized void endSession(){if(sessionContext!=null&&!sessionContext.getSharedPreferences("connection",0).edit().putBoolean("webRequested",false).putBoolean("webRootRequested",false).commit())error="停止回传设置未保存，请重新打开后检查";closeSession();}
 private static synchronized void closeSession(){ConsoleServer old=server;server=null;url="";pin="";root=false;if(old!=null)old.close();}
 public static synchronized boolean restoreSession(Context c)throws java.io.IOException {
  if(!RuntimeSettings.runningAllowed(c))return false;
  if(server!=null||!c.getSharedPreferences("connection",0).getBoolean("webRequested",false))return true;
  boolean wanted=c.getSharedPreferences("connection",0).getBoolean("webRootRequested",false);
  if(wanted&&!RootAccess.available())return false;
  startSession(c,lanHost(c),wanted);return true;
 }
 public static int webPort(){ConsoleServer current=server;return current==null?8875:current.port();}
 public static synchronized ConsoleServer startSession(Context c,String lanHost,boolean allowRoot)throws java.io.IOException {
  sessionContext=c.getApplicationContext();if(server!=null)return server;if(!RiskNotice.accepted(c))throw new java.io.IOException("请先阅读并同意使用提示");if(!c.getSharedPreferences("connection",0).getBoolean("appActive",false))throw new java.io.IOException("星匣已退出，请重新打开");
  if(allowRoot&&!RootAccess.available())throw new java.io.IOException("Root 授权已失效，请重新授权");
  java.io.ByteArrayOutputStream page=new java.io.ByteArrayOutputStream();try(java.io.InputStream in=c.getAssets().open("console.html")){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)page.write(b,0,n);}
  String code=String.format(java.util.Locale.US,"%04d",new java.security.SecureRandom().nextInt(10000));
  ConsoleServer next=new ConsoleServer("127.0.0.1",8875,code,page.toByteArray(),allowRoot,RootAccess.runner(c));
  error="";try{next.updateLan(lanHost);}catch(java.io.IOException e){error="局域网入口暂不可用，内网穿透仍可使用";lanHost="";}
  pin=code;root=allowRoot;bridge=RootAccess.bridge();url="http://"+(lanHost==null||lanHost.isEmpty()?"127.0.0.1":lanHost)+":"+next.port()+"/";
  if(RemoteService.running)next.setRemoteOrigin(c.getSharedPreferences("remote",0).getString("origin","http://127.0.0.1:8875"));
  if(!c.getSharedPreferences("connection",0).edit().putBoolean("webRequested",true).putBoolean("webRootRequested",allowRoot).commit()){next.close();throw new java.io.IOException("无法保存回传设置，请检查存储后重试");}
  next.start();server=next;try{c.startForegroundService(new Intent(c,ConsoleService.class));}catch(RuntimeException error){closeSession();throw new java.io.IOException("系统暂未允许启动回传服务",error);}return next;
 }
 public static String lanHost(Context c){java.util.List<NetworkState.Address> all=NetworkState.list();String iface=c.getSharedPreferences("connection",0).getString("consoleInterface","");for(NetworkState.Address a:all)if(a.iface.equals(iface))return a.ip;return all.isEmpty()?"":all.get(0).ip;}
 private void refreshSessionNetwork(){ConsoleServer current=server;if(current==null)return;String host=lanHost(this);try{current.updateLan(host);url="http://"+(host.isEmpty()?"127.0.0.1":host)+":"+current.port()+"/";error=host.isEmpty()?"暂无局域网地址，可使用内网穿透访问":"";}catch(java.io.IOException e){error="局域网入口暂不可用，内网穿透仍可使用";}if(root&&!RootAccess.available()){current.disableRoot();root=false;}}
 public void onCreate(){super.onCreate();sessionContext=getApplicationContext();NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("e02-session","E02星匣",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
  startForeground(20,new Notification.Builder(this,"e02-session").setSmallIcon(getApplicationInfo().icon).setContentTitle("E02星匣 · 服务运行中").setContentText("点击返回星匣；各服务在对应页面单独停止").setContentIntent(open).setOngoing(true).build());
  windows=(WindowManager)getSystemService(WINDOW_SERVICE);
 }
 public int onStartCommand(Intent intent,int flags,int id){
  if(!RiskNotice.accepted(this)){stopSelf();return START_NOT_STICKY;}
  String action=intent==null?"restart":intent.getAction();
  if(intent==null&&!RecoveryPolicy.background(RuntimeSettings.snapshot(this),RuntimeSettings.wakeRecovery(this))){stopSelf();return START_NOT_STICKY;}
  boolean wake=RootWakeObserver.RESTORE.equals(action);
  if(wake&&!validWakeRequest(intent))return RecoveryPolicy.background(RuntimeSettings.snapshot(this),RuntimeSettings.wakeRecovery(this))?START_STICKY:START_NOT_STICKY;
  if("restore".equals(action)||intent==null||wake){
   boolean cold=intent!=null&&intent.getBooleanExtra("coldStart",false);
   if(!RuntimeSettings.restore(this,cold,wake)){stopSelf();return START_NOT_STICKY;}
   getSharedPreferences("connection",0).edit().putBoolean("appActive",true).apply();RootAccess.resetRecovery();RuntimeRecovery.request(this);if(cold)RemoteStartController.onApplicationOpen(this);
  }
  if(!active(this)){stopSelf();return START_NOT_STICKY;}
  if(!started){started=true;PhoneCompanion.start(this);observeNetwork();handler.post(tick);}
  if("center".equals(action)){android.util.DisplayMetrics m=new android.util.DisplayMetrics();windows.getDefaultDisplay().getMetrics(m);int x=Math.max(0,(m.widthPixels-dp(80))/2),y=Math.max(0,(m.heightPixels-dp(44))/2);getSharedPreferences("connection",0).edit().putInt("x",x).putInt("y",y).apply();if(layout!=null){layout.x=x;layout.y=y;if(badge!=null)try{windows.updateViewLayout(badge,layout);}catch(Exception ignored){}}}
  if("stop".equals(action))endSession();
  if("options".equals(action))RuntimeRecovery.request(this);
  refreshStatusEntry();refreshBadge();return RecoveryPolicy.background(RuntimeSettings.snapshot(this),RuntimeSettings.wakeRecovery(this))?START_STICKY:START_NOT_STICKY;
 }
 private boolean validWakeRequest(Intent intent){
  if(!WakeRecoveryOptions.available())return false;
  try{RootPermit.Ticket ticket=RootPermit.active(getFilesDir());return ticket!=null&&ticket.nonce.equals(intent.getStringExtra("wakeGeneration"))&&RootPermit.matches(ticket)&&RecoveryPolicy.start(RuntimeSettings.snapshot(this),RiskNotice.accepted(this),RuntimeSettings.healthy(this),RecoveryPolicy.Event.WAKE,RuntimeSettings.wakeRecovery(this));}catch(java.io.IOException invalid){return false;}
 }
 private void observeNetwork(){
  connectivity=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
  networkCallback=new android.net.ConnectivityManager.NetworkCallback(){public void onAvailable(android.net.Network n){RuntimeRecovery.request(ConsoleService.this);}public void onLost(android.net.Network n){RuntimeRecovery.request(ConsoleService.this);}public void onLinkPropertiesChanged(android.net.Network n,android.net.LinkProperties p){RuntimeRecovery.request(ConsoleService.this);}};
  try{if(connectivity!=null)connectivity.registerDefaultNetworkCallback(networkCallback);}catch(RuntimeException denied){networkCallback=null;}
 }

 private void refreshStatusEntry(){
  boolean enabled=StatusBarEntry.wanted(this);if(!StatusBarEntry.sync(this))enabled=false;int value=enabled?1:0;if(value==statusVisibility)return;statusVisibility=value;if(enabled)statusPublished=true;
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder notice=new Notification.Builder(this,"e02-session").setSmallIcon(getApplicationInfo().icon).setContentTitle("E02星匣 · 服务运行中").setContentText("点击返回星匣；各服务在对应页面单独停止").setContentIntent(open).setOngoing(true);
  // This Flyme host filters foreground-service notifications before status icons.
  // Keep notification 20 for the service, and use a separate app-owned status entry.
  NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(statusPublished){notice.addExtras(StatusBarEntry.extras(this,enabled));manager.notify(STATUS_NOTIFICATION,notice.build());if(!enabled)manager.cancel(STATUS_NOTIFICATION);}
 }
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
 private void refreshBadge(){boolean show=KeepAlivePresentation.overlay(active(this),RuntimeSettings.snapshot(this).keepAlive,getSharedPreferences("connection",0).getBoolean("overlay",true),Settings.canDrawOverlays(this));
  if(!show){removeBadge();return;}if(capturing||badge!=null)return;
  badge=new Badge(this);layout=new WindowManager.LayoutParams(dp(80),dp(44),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);layout.gravity=Gravity.TOP|Gravity.LEFT;layout.x=getSharedPreferences("connection",0).getInt("x",24);layout.y=getSharedPreferences("connection",0).getInt("y",180);
  badge.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP)));
  badge.setOnLongClickListener(v->{captureScreen();return true;});
  badge.setOnTouchListener(new View.OnTouchListener(){float sx,sy;int ox,oy;boolean moved,longPress;final Runnable hold=()->{if(!moved&&badge!=null){longPress=true;badge.performLongClick();}};public boolean onTouch(View v,android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){sx=e.getRawX();sy=e.getRawY();ox=layout.x;oy=layout.y;moved=false;longPress=false;handler.postDelayed(hold,ViewConfiguration.getLongPressTimeout());return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getRawX()-sx,dy=e.getRawY()-sy;if(Math.abs(dx)+Math.abs(dy)>dp(6)){moved=true;handler.removeCallbacks(hold);}if(!moved)return true;android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getMetrics(metrics);layout.x=Math.max(0,Math.min(metrics.widthPixels-layout.width,ox+(int)dx));layout.y=Math.max(0,Math.min(metrics.heightPixels-layout.height,oy+(int)dy));try{windows.updateViewLayout(badge,layout);}catch(Exception ignored){}return true;}if(e.getAction()==MotionEvent.ACTION_UP){handler.removeCallbacks(hold);if(!moved&&!longPress)v.performClick();else if(moved)getSharedPreferences("connection",0).edit().putInt("x",layout.x).putInt("y",layout.y).apply();return true;}if(e.getAction()==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(hold);return true;}return true;}});
  try{windows.addView(badge,layout);}catch(Exception e){error="悬浮窗显示失败："+UserMessages.explain(e);badge=null;}
 }
 private void removeBadge(){if(badge!=null){try{windows.removeView(badge);}catch(Exception ignored){}badge=null;}}
 private void captureScreen(){if(capturing||stopping)return;capturing=true;if(badge!=null)badge.setVisibility(View.INVISIBLE);handler.postDelayed(()->{if(stopping)return;new Thread(()->{CommandRunner runner=null;String message;try{String path=android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()+"/E02-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS",java.util.Locale.US).format(new java.util.Date())+".png";RootAccess.check(this,false);if(!RootAccess.available())throw new java.io.IOException("请先启用 Root 授权");runner=RootAccess.runner(this);String quoted="'"+path.replace("'","'\\''")+"'";String temporary="'"+(path+".part").replace("'","'\\''")+"'";String folder="'"+android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath().replace("'","'\\''")+"'";CommandRunner.Result r=runner.execute("mkdir -p "+folder+" && screencap -p "+temporary+" && chmod 644 "+temporary+" && test -s "+temporary+" && mv "+temporary+" "+quoted+"; status=$?; if [ $status -ne 0 ]; then rm -f "+temporary+"; fi; exit $status",true,15);if(r.exit==0&&!r.timedOut&&r.error.isEmpty()){try{android.media.MediaScannerConnection.scanFile(this,new String[]{path},new String[]{"image/png"},null);}catch(Exception ignored){}message="截图已保存到下载文件夹："+new java.io.File(path).getName();}else message="截图失败，请检查 Root 授权："+r.stderr+" "+r.error;}catch(Exception e){message="截图失败："+e.getMessage();}finally{if(runner!=null)runner.close();}final String result=message;handler.post(()->{capturing=false;if(!stopping){if(badge!=null)badge.setVisibility(View.VISIBLE);refreshBadge();android.widget.Toast.makeText(this,result,android.widget.Toast.LENGTH_LONG).show();}});},"e02-screenshot").start();},250);}
 public void onDestroy(){stopping=true;handler.removeCallbacksAndMessages(null);RuntimeRecovery.cancel();if(connectivity!=null&&networkCallback!=null)try{connectivity.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}removeBadge();PhoneCompanion.stop();if(RecoveryPolicy.restoreChannels(RuntimeSettings.snapshot(this),RiskNotice.accepted(this),RuntimeSettings.healthy(this),RuntimeSettings.wakeRecovery(this)))closeSession();else endSession();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(STATUS_NOTIFICATION);stopForeground(true);super.onDestroy();}
 public android.os.IBinder onBind(Intent i){return null;}
 private final class Badge extends View {
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Badge(Context c){super(c);setContentDescription("E02星匣保活窗口，轻点打开星匣，长按截图，拖动调整位置");}
  void fill(Canvas c,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);}
  protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/80f,getHeight()/44f);fill(c,Color.argb(96,20,32,36));c.drawRoundRect(1,1,79,43,8,8,p);
   fill(c,Color.argb(220,255,255,255));p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(13);c.drawText("E02星匣",40,18,p);p.setTypeface(Typeface.DEFAULT);p.setTextSize(12);c.drawText("保活窗口",40,35,p);c.restore();
  }
 }
}
