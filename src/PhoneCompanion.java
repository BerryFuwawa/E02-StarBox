package com.e02.rootconsole;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Lifecycle belongs to ConsoleService; Activity and input references are weak. */
public final class PhoneCompanion {
 private static volatile PhoneServer server;
 private static volatile String error="";
 private static Context context;
 private static WeakReference<EditText> focus=new WeakReference<>(null);
 private static WeakReference<Activity> activity=new WeakReference<>(null);
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static ClipboardManager clipboard;
 private static ClipboardManager.OnPrimaryClipChangedListener listener;
 private static final String TAG="Starbox:";
 public static synchronized void start(Context c){if(!RiskNotice.accepted(c)||server!=null)return;context=c.getApplicationContext();try{ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getAssets().open("phone.html")){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}PhoneServer fresh=null;IOException last=null;/* 8876 is reserved for the loopback-only EVCC Root bridge, even before authorization. */for(int port=8877;port<8887;port++)try{fresh=new PhoneServer(port,out.toByteArray(),PhoneCompanion::send);break;}catch(IOException e){last=e;}if(fresh==null)throw last;server=fresh;refresh();fresh.start();error="";clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);listener=()->{if(context==null||!context.getSharedPreferences("phone",0).getBoolean("crossClipboard",false))return;try{ClipData clip=clipboard.getPrimaryClip();if(clip==null||clip.getItemCount()==0||String.valueOf(clip.getDescription().getLabel()).startsWith(TAG))return;CharSequence value=clip.getItemAt(0).coerceToText(context);if(value!=null&&value.length()<=65536&&server!=null)server.copied(value.toString(),true);}catch(Exception ignored){}};clipboard.addPrimaryClipChangedListener(listener);}catch(Exception e){error="手机助手启动失败："+UserMessages.explain(e);}}
 public static void attach(Activity a){activity=new WeakReference<>(a);}
 public static void detach(Activity a){if(activity.get()==a){activity.clear();focus.clear();if(server!=null)server.focus(null);}}
 public static void field(EditText e,String label){e.setTag(TAG+label);e.setOnFocusChangeListener((v,on)->{if(on){focus=new WeakReference<>((EditText)v);if(server!=null)server.focus(label);}else if(focus.get()==v){focus.clear();if(server!=null)server.focus(null);}});}
 private static boolean usable(EditText e){Activity a=activity.get();return a!=null&&!a.isFinishing()&&e!=null&&e.isShown()&&e.isEnabled()&&e.hasFocus()&&e.hasWindowFocus()&&e.getKeyListener()!=null&&e.getTag() instanceof String&&((String)e.getTag()).startsWith(TAG);}
 private static String send(String text,boolean toFocus,String expectedFocus)throws Exception {CountDownLatch done=new CountDownLatch(1);String[] answer=new String[1];Exception[] failure=new Exception[1];java.util.concurrent.atomic.AtomicBoolean expired=new java.util.concurrent.atomic.AtomicBoolean();main.post(()->{try{if(expired.get()||context==null)throw new IOException("连接已结束，请重试");if(toFocus){EditText e=focus.get();if(!usable(e))throw new IOException("请在星匣中点选一个可编辑输入框");if(!e.getTag().toString().substring(TAG.length()).equals(expectedFocus))throw new IOException("当前输入框已切换，请确认后重新发送");e.setText(text);e.setSelection(e.getText().length());answer[0]="已填写 "+e.getTag().toString().substring(TAG.length())+" · "+text.length()+" 字符";}else {clipboard.setPrimaryClip(ClipData.newPlainText(TAG+"phone",text));answer[0]="已发送到车机剪贴板 · "+text.length()+" 字符";}if(context!=null)Toast.makeText(context,answer[0],Toast.LENGTH_SHORT).show();}catch(Exception e){failure[0]=e;}finally{done.countDown();}});if(!done.await(5,TimeUnit.SECONDS)){expired.set(true);throw new IOException("车机响应超时，请重试");}if(failure[0]!=null)throw failure[0];return answer[0];}
 public static void copy(Context c,String label,String text,boolean sensitive){ClipboardManager manager=(ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);manager.setPrimaryClip(ClipData.newPlainText(TAG+label,text));PhoneServer current=server;if(current!=null)current.copied(text,sensitive);}
 public static void refresh(){PhoneServer s=server;if(s==null)return;List<String> hosts=new ArrayList<>();hosts.add("127.0.0.1");for(NetworkState.Address a:NetworkState.list())hosts.add(a.ip);s.setHosts(hosts);EditText e=focus.get();Activity a=activity.get();if(!usable(e)&&a!=null&&a.getCurrentFocus() instanceof EditText){EditText current=(EditText)a.getCurrentFocus();if(usable(current)){e=current;focus=new WeakReference<>(current);}}s.focus(usable(e)?e.getTag().toString().substring(TAG.length()):null);}
 public static String url(String host){PhoneServer s=server;return s==null?"":"http://"+host+":"+s.port()+"/";}
 public static String pin(){PhoneServer s=server;return s==null?"----":s.pin();}
 public static boolean connected(){PhoneServer s=server;return s!=null&&s.paired();}
 public static String status(){PhoneServer s=server;return s==null?(error.isEmpty()?"未启动":error):s.paired()?"已连接":"未连接";}
 public static void unpair(){PhoneServer s=server;if(s!=null)s.resetPairing();}
 public static synchronized void stop(){PhoneServer s=server;server=null;if(s!=null)s.close();if(clipboard!=null&&listener!=null)clipboard.removePrimaryClipChangedListener(listener);listener=null;clipboard=null;context=null;focus.clear();activity.clear();}
}
