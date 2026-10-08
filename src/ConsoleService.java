package com.e02.rootconsole;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;

/** Owns the live session independently of Activity visibility. Never auto-restores a root session. */
public class ConsoleService extends Service {
 public static volatile ConsoleServer server;
 public static volatile String url="",pin="",error="";
 public static volatile boolean root,bridge;
 private WindowManager windows;private Badge badge;private WindowManager.LayoutParams layout;
 private PowerManager.WakeLock awake;private Handler handler=new Handler();private boolean capturing,stopping;
 public static boolean active(Context c){return RiskNotice.accepted(c)&&(c.getSharedPreferences("connection",0).getBoolean("appActive",false)||server!=null||RemoteService.running||UpdateService.downloading||c.getSharedPreferences("connection",0).getBoolean("adbManaged",false));}
 private final Runnable tick=new Runnable(){public void run(){if(!active(ConsoleService.this)){stopSelf();return;}refreshBadge();PhoneCompanion.refresh();refreshSessionNetwork();RootAccess.refresh(ConsoleService.this);AdbMaintenance.refresh(ConsoleService.this);handler.postDelayed(this,1500);}};
 public static synchronized void endSession(){ConsoleServer old=server;server=null;url="";pin="";root=false;if(old!=null)old.close();}
 public static int webPort(){ConsoleServer current=server;return current==null?8875:current.port();}
 public static synchronized ConsoleServer startSession(Context c,String lanHost,boolean allowRoot)throws java.io.IOException {
  if(server!=null)return server;if(!RiskNotice.accepted(c))throw new java.io.IOException("请先阅读并同意使用提示");if(!c.getSharedPreferences("connection",0).getBoolean("appActive",false))throw new java.io.IOException("星匣已退出，请重新打开");
  if(allowRoot&&!RootAccess.available())throw new java.io.IOException("Root 授权已失效，请重新授权");
  java.io.ByteArrayOutputStream page=new java.io.ByteArrayOutputStream();try(java.io.InputStream in=c.getAssets().open("console.html")){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)page.write(b,0,n);}
  String code=String.format(java.util.Locale.US,"%04d",new java.security.SecureRandom().nextInt(10000));
  ConsoleServer next=new ConsoleServer("127.0.0.1",8875,code,page.toByteArray(),allowRoot,RootAccess.runner(c));
  error="";try{next.updateLan(lanHost);}catch(java.io.IOException e){error="局域网入口暂不可用，内网穿透仍可使用";lanHost="";}
  pin=code;root=allowRoot;bridge=RootAccess.bridge();url="http://"+(lanHost==null||lanHost.isEmpty()?"127.0.0.1":lanHost)+":"+next.port()+"/";
  if(RemoteService.running)next.setRemoteOrigin(c.getSharedPreferences("remote",0).getString("origin","http://127.0.0.1:8875"));
  next.start();server=next;c.startForegroundService(new Intent(c,ConsoleService.class));return next;
 }
 public static String lanHost(Context c){java.util.List<NetworkState.Address> all=NetworkState.list();String iface=c.getSharedPreferences("connection",0).getString("consoleInterface","");for(NetworkState.Address a:all)if(a.iface.equals(iface))return a.ip;return all.isEmpty()?"":all.get(0).ip;}
 private void refreshSessionNetwork(){ConsoleServer current=server;if(current==null)return;String host=lanHost(this);try{current.updateLan(host);url="http://"+(host.isEmpty()?"127.0.0.1":host)+":"+current.port()+"/";error=host.isEmpty()?"暂无局域网地址，可使用内网穿透访问":"";}catch(java.io.IOException e){error="局域网入口暂不可用，内网穿透仍可使用";}if(root&&!RootAccess.available()){current.disableRoot();root=false;}}
 public void onCreate(){super.onCreate();NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("e02-session","E02星匣",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ConsoleService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT);
  startForeground(20,new Notification.Builder(this,"e02-session").setSmallIcon(getApplicationInfo().icon).setContentTitle("E02星匣 · 服务运行中").setContentText("点击返回星匣；各服务在对应页面单独停止").setContentIntent(open).setOngoing(true).build());
  if(!active(this)){stopSelf();return;}
  awake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"E02:ConsoleSession");awake.acquire();PhoneCompanion.start(this);windows=(WindowManager)getSystemService(WINDOW_SERVICE);handler.post(tick);
 }
 public int onStartCommand(Intent intent,int flags,int id){if(!RiskNotice.accepted(this)){stopSelf();return START_NOT_STICKY;}if(intent!=null&&"center".equals(intent.getAction())){android.util.DisplayMetrics m=new android.util.DisplayMetrics();windows.getDefaultDisplay().getMetrics(m);int x=Math.max(0,(m.widthPixels-dp(80))/2),y=Math.max(0,(m.heightPixels-dp(44))/2);getSharedPreferences("connection",0).edit().putInt("x",x).putInt("y",y).apply();if(layout!=null){layout.x=x;layout.y=y;if(badge!=null)try{windows.updateViewLayout(badge,layout);}catch(Exception ignored){}}}if(intent!=null&&"stop".equals(intent.getAction())){endSession();if(!active(this))stopSelf();return START_NOT_STICKY;}if(!active(this)){stopSelf();return START_NOT_STICKY;}refreshBadge();return START_NOT_STICKY;}
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
 private void refreshBadge(){boolean show=active(this)&&getSharedPreferences("connection",0).getBoolean("overlay",true)&&Settings.canDrawOverlays(this);
  if(!show){removeBadge();return;}if(capturing||badge!=null)return;
  badge=new Badge(this);layout=new WindowManager.LayoutParams(dp(80),dp(44),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);layout.gravity=Gravity.TOP|Gravity.LEFT;layout.x=getSharedPreferences("connection",0).getInt("x",24);layout.y=getSharedPreferences("connection",0).getInt("y",180);
  badge.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP)));
  badge.setOnLongClickListener(v->{captureScreen();return true;});
  badge.setOnTouchListener(new View.OnTouchListener(){float sx,sy;int ox,oy;boolean moved,longPress;final Runnable hold=()->{if(!moved&&badge!=null){longPress=true;badge.performLongClick();}};public boolean onTouch(View v,android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){sx=e.getRawX();sy=e.getRawY();ox=layout.x;oy=layout.y;moved=false;longPress=false;handler.postDelayed(hold,ViewConfiguration.getLongPressTimeout());return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getRawX()-sx,dy=e.getRawY()-sy;if(Math.abs(dx)+Math.abs(dy)>dp(6)){moved=true;handler.removeCallbacks(hold);}if(!moved)return true;android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getMetrics(metrics);layout.x=Math.max(0,Math.min(metrics.widthPixels-layout.width,ox+(int)dx));layout.y=Math.max(0,Math.min(metrics.heightPixels-layout.height,oy+(int)dy));try{windows.updateViewLayout(badge,layout);}catch(Exception ignored){}return true;}if(e.getAction()==MotionEvent.ACTION_UP){handler.removeCallbacks(hold);if(!moved&&!longPress)v.performClick();else if(moved)getSharedPreferences("connection",0).edit().putInt("x",layout.x).putInt("y",layout.y).apply();return true;}if(e.getAction()==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(hold);return true;}return true;}});
  try{windows.addView(badge,layout);}catch(Exception e){error="悬浮窗显示失败："+UserMessages.explain(e);badge=null;}
 }
 private void removeBadge(){if(badge!=null){try{windows.removeView(badge);}catch(Exception ignored){}badge=null;}}
 private void captureScreen(){if(capturing||stopping)return;capturing=true;if(badge!=null)badge.setVisibility(View.INVISIBLE);handler.postDelayed(()->{if(stopping)return;new Thread(()->{CommandRunner runner=null;String message;try{String path=android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()+"/E02-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS",java.util.Locale.US).format(new java.util.Date())+".png";if(bridge||getSharedPreferences("connection",0).getBoolean("bridgeMode",false)){byte[] secret=new byte[32];try(java.io.DataInputStream in=new java.io.DataInputStream(new java.io.FileInputStream(new java.io.File(getFilesDir(),"bridge-token")))){in.readFully(secret);}runner=new BridgeRunner(new String(secret,"US-ASCII"),false);}else runner=new CommandRunner("/system/bin/sh","su");String quoted="'"+path.replace("'","'\\''")+"'";String temporary="'"+(path+".part").replace("'","'\\''")+"'";String folder="'"+android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath().replace("'","'\\''")+"'";CommandRunner.Result r=runner.execute("mkdir -p "+folder+" && screencap -p "+temporary+" && chmod 644 "+temporary+" && test -s "+temporary+" && mv "+temporary+" "+quoted+"; status=$?; if [ $status -ne 0 ]; then rm -f "+temporary+"; fi; exit $status",true,15);if(r.exit==0&&!r.timedOut&&r.error.isEmpty()){try{android.media.MediaScannerConnection.scanFile(this,new String[]{path},new String[]{"image/png"},null);}catch(Exception ignored){}message="截图已保存到下载文件夹："+new java.io.File(path).getName();}else message="截图失败，请检查 EVCC Root 授权："+r.stderr+" "+r.error;}catch(Exception e){message="截图失败："+e.getMessage();}finally{if(runner!=null)runner.close();}final String result=message;handler.post(()->{capturing=false;if(!stopping){if(badge!=null)badge.setVisibility(View.VISIBLE);refreshBadge();android.widget.Toast.makeText(this,result,android.widget.Toast.LENGTH_LONG).show();}});},"e02-screenshot").start();},250);}
 public void onDestroy(){stopping=true;handler.removeCallbacksAndMessages(null);removeBadge();PhoneCompanion.stop();endSession();if(awake!=null&&awake.isHeld())awake.release();stopForeground(true);super.onDestroy();}
 public android.os.IBinder onBind(Intent i){return null;}
 private final class Badge extends View {
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Badge(Context c){super(c);setContentDescription("E02星匣保活窗口，轻点打开星匣，长按截图，拖动调整位置");}
  void fill(Canvas c,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);}
  protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/80f,getHeight()/44f);fill(c,Color.argb(96,20,32,36));c.drawRoundRect(1,1,79,43,8,8,p);
   fill(c,Color.argb(220,255,255,255));p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(13);c.drawText("E02星匣",40,18,p);p.setTypeface(Typeface.DEFAULT);p.setTextSize(12);c.drawText("保活窗口",40,35,p);c.restore();
  }
 }
}
