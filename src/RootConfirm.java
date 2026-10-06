package com.e02.rootconsole;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

public final class RootConfirm {
 public static void show(Context c,Runnable end){
  Dialog d=new Dialog(c);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
  LinearLayout body=Design.column(c);body.setPadding(Design.dp(c,28),Design.dp(c,24),Design.dp(c,28),Design.dp(c,24));body.setBackground(Design.surface(c,Design.CARD,24));body.setClipToOutline(true);
  TextView title=Design.text(c,"结束提权",26);title.setGravity(Gravity.CENTER);body.addView(title);
  TextView question=Design.text(c,"你真的现在就要结束提权吗？",22);question.setTextColor(Design.TEXT);question.setGravity(Gravity.CENTER);body.addView(question);
  TextView hint=Design.text(c,"结束后，需要重新授权才能使用 Root 功能。",19);hint.setGravity(Gravity.CENTER);body.addView(hint);
  LinearLayout row=new LinearLayout(c);body.addView(row);
  Button cancel=Design.button(c,"取消",d::dismiss),confirm=Design.button(c,"结束提权",()->{d.dismiss();end.run();});confirm.setBackground(Design.surface(c,Design.DANGER,12));
  for(Button b:new Button[]{cancel,confirm}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Design.dp(c,58),1);p.topMargin=Design.dp(c,18);if(row.getChildCount()>0)p.leftMargin=Design.dp(c,18);row.addView(b,p);}
  d.setContentView(body);Window w=d.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=w.getAttributes();p.dimAmount=.58f;w.setAttributes(p);d.show();w.setLayout(Math.min(Design.dp(c,720),(int)(c.getResources().getDisplayMetrics().widthPixels*.88)),-2);
 }
}
