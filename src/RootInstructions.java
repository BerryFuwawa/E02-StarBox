package com.e02.rootconsole;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

/** Both verified authorization routes, readable without a provider selection step. */
public final class RootInstructions {
 public static void show(Context c){
  Dialog dialog=new Dialog(c);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
  LinearLayout body=Design.column(c);body.setPadding(dp(c,30),dp(c,24),dp(c,30),dp(c,24));body.setBackground(Design.surface(c,Design.CARD,24));body.setClipToOutline(true);
  TextView title=Design.text(c,"提权命令已复制",27);title.setGravity(Gravity.CENTER);body.addView(title);
  TextView subtitle=Design.text(c,"任选一种方式，完成后返回星匣检测 Root",20);subtitle.setGravity(Gravity.CENTER);body.addView(subtitle);
  LinearLayout columns=new LinearLayout(c);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(c,18);body.addView(columns,cp);
  addColumn(columns,"EVCC",new String[]{"打开 EVCC → 终端。","粘贴到自定义命令【命令】输入框。","点击【Root 执行】。","返回 E02星匣，点击【重新检测 Root】。"});
  addColumn(columns,"应用管家",new String[]{"打开应用管家 → 执行命令。","点击【粘贴】，将命令填入输入框。","点击【本机执行】。","返回 E02星匣，点击【重新检测 Root】。"});
  TextView hint=Design.text(c,"授权工具需已具有 Root 权限；复制命令不会自动完成提权。",18);hint.setGravity(Gravity.CENTER);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(c,14);body.addView(hint,hp);
  Button understood=Design.button(c,"明白",dialog::dismiss);understood.setTextSize(22);understood.setTextColor(Design.BG);understood.setBackground(Design.surface(c,Design.ACCENT,12));
  LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(c,240),dp(c,58));bp.gravity=Gravity.CENTER_HORIZONTAL;bp.topMargin=dp(c,16);body.addView(understood,bp);
  dialog.setContentView(body);Window w=dialog.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=w.getAttributes();attrs.dimAmount=.58f;w.setAttributes(attrs);
  dialog.show();w.setLayout(Math.min(dp(c,1240),(int)(c.getResources().getDisplayMetrics().widthPixels*.88)),-2);
 }
 private static void addColumn(LinearLayout columns,String title,String[] steps){
  Context c=columns.getContext();LinearLayout card=Design.column(c);card.setPadding(dp(c,22),dp(c,16),dp(c,22),dp(c,16));card.setBackground(Design.surface(c,0xff171d26,14));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(columns.getChildCount()>0)lp.leftMargin=dp(c,20);columns.addView(card,lp);
  TextView heading=Design.text(c,title,25);heading.setTextColor(Design.ACCENT);card.addView(heading);
  for(int i=0;i<steps.length;i++){
   LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.TOP);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(c,12);card.addView(row,rp);
   TextView number=Design.text(c,String.valueOf(i+1),18);number.setPadding(0,0,0,0);number.setGravity(Gravity.CENTER);number.setTypeface(Typeface.DEFAULT_BOLD);number.setTextColor(Design.ACCENT);number.setBackground(Design.surface(c,0xff2c3b49,8));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(c,34),dp(c,34));np.topMargin=dp(c,4);row.addView(number,np);
   TextView step=Design.text(c,steps[i],21);step.setTextColor(Design.TEXT);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=dp(c,14);row.addView(step,sp);
  }
 }
 private static int dp(Context c,int n){return Design.dp(c,n);}
}
