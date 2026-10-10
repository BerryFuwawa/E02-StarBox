package com.e02.rootconsole;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

/** First-use confirmation. No background connection starts until it is accepted. */
public final class RiskNotice {
 private static final int VERSION=1;
 private static final int BG=0xff481c25, BORDER=0xffa55b6a, TEXT=0xfff3e9ea, ROSE=0xfff2abb5;
 public static boolean accepted(android.content.Context c){return c.getSharedPreferences("risk_notice",0).getInt("acceptedVersion",0)>=VERSION;}
 public static Dialog show(Activity a,Runnable agree,Runnable decline){
  Dialog d=new Dialog(a);d.requestWindowFeature(Window.FEATURE_NO_TITLE);d.setCancelable(false);d.setCanceledOnTouchOutside(false);
  LinearLayout body=Design.column(a);body.setPadding(dp(a,30),dp(a,20),dp(a,30),dp(a,22));body.setBackground(surface(a,BG,BORDER,24));body.setClipToOutline(true);
  TextView title=text(a,"⚠  免责声明与风险告知",34,TEXT,true);body.addView(title);
  body.addView(text(a,"请在使用前仔细阅读",20,TEXT,true));
  ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);LinearLayout content=Design.column(a);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
  LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(a,8);sp.bottomMargin=dp(a,14);body.addView(scroll,sp);
  TextView free=text(a,"E02星匣是免费、开源的第三方车机工具，不对外出售，也不收取软件使用费用。项目方未授权任何个人或机构销售本软件。",20,BG,true);
  inset(a,content,free,0xffd7a0a5,0xffe5b6bc,14);
  add(a,content,text(a,"E02星匣是第三方车机工具，部分功能涉及高权限操作、车机设置修改及自定义命令执行。不同车型、系统版本和操作方式可能产生不同结果，无法保证所有功能均兼容或稳定运行。",20,TEXT,false),12);
  add(a,content,text(a,"使用本软件可能带来以下风险：",20,TEXT,true),8);
  String[] risks={"车机异常、数据丢失、功能失效或无法正常启动。","系统严重损坏、车机“变砖”，需要专业维修，甚至更换设备。","导航、仪表、车辆设置等相关功能受到影响，可能影响行车安全。","影响厂家售后服务或质保权益，具体以厂家政策及实际情况为准。","开启远程连接或高权限功能后，因配置不当造成未授权访问或误操作。"};
  for(String risk:risks)add(a,content,text(a,"•  "+risk,20,TEXT,false),0);
  TextView parking=text(a,"请仅在车辆停稳、安全驻车 🅿️时操作。",20,BG,false);SpannableString emphasis=new SpannableString(parking.getText());String phrase="车辆停稳、安全驻车";int start=emphasis.toString().indexOf(phrase);emphasis.setSpan(new StyleSpan(Typeface.BOLD),start,start+phrase.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);parking.setText(emphasis);
  inset(a,content,parking,0xffddd4cc,0xffeee5de,12);
  add(a,content,text(a,"使用前做好必要备份，不执行来源不明或不了解用途的命令；发现异常时立即停止操作。",20,TEXT,false),10);
  add(a,content,text(a,"请根据自身能力评估风险。",21,TEXT,true),10);
  add(a,content,text(a,"点击下方同意按钮，表示你已阅读并理解上述风险，自愿使用本软件。",21,ROSE,true),5);
  android.view.View line=new android.view.View(a);line.setBackgroundColor(BORDER);body.addView(line,new LinearLayout.LayoutParams(-1,dp(a,1)));
  LinearLayout row=new LinearLayout(a);row.setBaselineAligned(false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(a,16);body.addView(row,rp);
  Button no=Design.button(a,"不同意并退出",()->{d.dismiss();decline.run();});no.setBackground(Design.feedback(a,BG,12,BORDER));no.setTextColor(TEXT);row.addView(no,new LinearLayout.LayoutParams(0,dp(a,58),1));
  Button yes=Design.button(a,"我已阅读并同意",()->{if(!a.getSharedPreferences("risk_notice",0).edit().putInt("acceptedVersion",VERSION).commit()){Toast.makeText(a,"确认状态保存失败，请重试",Toast.LENGTH_LONG).show();return;}d.dismiss();agree.run();});yes.setBackground(Design.feedback(a,0xffbc7e88,12,0xffd49ca5));yes.setTextColor(TEXT);LinearLayout.LayoutParams yp=new LinearLayout.LayoutParams(0,dp(a,58),1);yp.leftMargin=dp(a,18);row.addView(yes,yp);
  d.setContentView(body);Window w=d.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=w.getAttributes();attrs.dimAmount=.66f;w.setAttributes(attrs);d.show();
  android.graphics.Rect visible=new android.graphics.Rect();a.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);int width=visible.width()>0?visible.width():a.getResources().getDisplayMetrics().widthPixels;int height=visible.height()>0?visible.height():a.getResources().getDisplayMetrics().heightPixels;
  w.setLayout(Math.min(dp(a,1440),(int)(width*.94f)),Math.min(dp(a,860),(int)(height*.82f)));
  return d;
 }
 private static int dp(android.content.Context c,int n){return Design.dp(c,n);}
 private static GradientDrawable surface(android.content.Context c,int color,int border,int radius){GradientDrawable b=Design.surface(c,color,radius);b.setStroke(dp(c,1),border);return b;}
 private static TextView text(android.content.Context c,String value,int size,int color,boolean bold){TextView t=Design.text(c,value,size);t.setTextColor(color);t.setPadding(0,0,0,0);t.setIncludeFontPadding(false);t.setLineSpacing(0,1f);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
 private static void add(Activity a,LinearLayout parent,TextView t,int gap){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(a,gap);parent.addView(t,p);}
 private static void inset(Activity a,LinearLayout parent,TextView t,int color,int border,int gap){t.setPadding(dp(a,18),dp(a,11),dp(a,18),dp(a,11));t.setBackground(surface(a,color,border,12));add(a,parent,t,gap);}
}
