package com.e02.rootconsole;

import android.app.Service;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.service.notification.StatusBarNotification;
import android.view.*;
import android.widget.*;
import com.flyme.auto.plugin.systemui.StatusBarPlugin;

/** Runs inside the host: only native UI and an explicit user-click launch, never Root or app statics. */
public final class StarboxStatusBarPlugin extends Service implements StatusBarPlugin {
    static final int ENTRY_ID=0x453032;
    private Context pluginContext,hostContext;private DialogCallback callback;private View view;
    @Override public int getID(){return ENTRY_ID;}
    @Override public int getVersion(){return 1;}
    @Override public ComponentName getComponentName(){return new ComponentName("com.e02.rootconsole",getClass().getName());}
    @Override public void onCreate(Context systemContext,Context context){hostContext=systemContext;pluginContext=context;view=null;}
    @Override public String getDialogTitle(){return "E02星匣";}
    private int dp(int n){return Math.round(pluginContext.getResources().getDisplayMetrics().density*n);}
    @Override public int getDialogWidth(){return pluginContext==null?560:Math.min(dp(560),Math.max(dp(280),pluginContext.getResources().getDisplayMetrics().widthPixels-dp(64)));}
    @Override public int getDialogHeight(){return pluginContext==null?310:dp(310);}
    @Override public View getDialogView(){
        if(view!=null)return view;if(pluginContext==null)return null;
        LinearLayout panel=new LinearLayout(pluginContext);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(24),dp(20),dp(24),dp(24));
        GradientDrawable background=new GradientDrawable();background.setColor(0xff202630);background.setCornerRadius(dp(20));background.setStroke(dp(1),0xff354050);panel.setBackground(background);panel.setClipToOutline(true);
        TextView title=new TextView(pluginContext);title.setText("星匣快捷入口");title.setTextSize(23);title.setTextColor(0xffe8edf4);title.setTypeface(Typeface.DEFAULT_BOLD);panel.addView(title);
        TextView hint=new TextView(pluginContext);hint.setText("打开星匣，查看 ADB、网页回传和远程连接。");hint.setTextSize(18);hint.setTextColor(0xffa5b2c4);hint.setPadding(0,dp(12),0,dp(20));panel.addView(hint);
        Button open=new Button(pluginContext);open.setText("打开 E02星匣");open.setAllCaps(false);open.setTextSize(20);open.setSingleLine(true);open.setGravity(Gravity.CENTER);open.setPadding(0,0,0,0);open.setIncludeFontPadding(false);open.setTypeface(Typeface.DEFAULT_BOLD);open.setTextColor(0xff14181e);open.setBackgroundTintList(null);open.setMinimumHeight(0);open.setMinHeight(0);
        GradientDrawable button=new GradientDrawable();button.setColor(0xffa8d2e9);button.setCornerRadius(dp(12));open.setBackground(button);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(270),dp(56));params.gravity=Gravity.CENTER_HORIZONTAL;panel.addView(open,params);
        open.setOnClickListener(v->{final Context launchContext=hostContext==null?pluginContext:hostContext;if(callback!=null)callback.dismissDialog();new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{try{launchContext.startActivity(new Intent().setComponent(new ComponentName("com.e02.rootconsole","com.e02.rootconsole.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}catch(RuntimeException unavailable){Toast.makeText(launchContext,"暂时无法打开星匣，请从应用列表打开",Toast.LENGTH_LONG).show();}},200);});
        view=panel;return panel;
    }
    @Override public void setDialogCallback(DialogCallback value){callback=value;}
    @Override public void onDialogDismissed(){}
    @Override public void onDialogShowed(){}
    @Override public void onStatusIconPosted(StatusBarNotification notification){}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){stopSelf();return START_NOT_STICKY;}
    @Override public void onDestroy(){view=null;callback=null;pluginContext=null;hostContext=null;super.onDestroy();}
}
