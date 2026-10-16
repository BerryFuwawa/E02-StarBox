package com.e02.rootconsole;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** Own-service consent with a separate, default-off option for native ADB preparation. */
final class RootConsent {
    static Dialog show(Activity activity,java.util.function.Consumer<Boolean> enable) {
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body=Design.column(activity);body.setPadding(Design.dp(activity,28),Design.dp(activity,24),Design.dp(activity,28),Design.dp(activity,24));
        body.setBackground(Design.surface(activity,Design.CARD,24));body.setClipToOutline(true);
        TextView title=Design.text(activity,"启用星匣独立授权？",26);title.setGravity(Gravity.CENTER);body.addView(title);
        body.addView(Design.text(activity,"本机 Root ADB 可用时，星匣可以建立自己的 Root 服务。确认后，星匣可在已允许的运行条件下自动恢复授权；本机条件不可用时会提示原因。",20));
        CheckBox prepare=new CheckBox(activity);prepare.setText("自动准备本机 ADB，用于恢复 Root 授权");prepare.setTextSize(20);prepare.setTextColor(Design.TEXT);prepare.setChecked(RootAdbRecovery.selected(activity));body.addView(prepare);
        body.addView(Design.text(activity,"此选项默认关闭。启用时需先完成一次 Root 授权，并授予系统设置权限；后续可能开启本机 ADB、请求原生 ADB Root，调试连接可能短暂中断。不勾选时保持原有方式。",19));
        body.addView(Design.text(activity,"结束提权后不会自动重新获取，需要再次确认启用。退出程序及后台会停止当前服务。请仅在安全驻车时启用。",19));
        LinearLayout actions=new LinearLayout(activity);body.addView(actions);
        Button cancel=Design.button(activity,"取消",dialog::dismiss),confirm=Design.button(activity,"启用",()->{dialog.dismiss();enable.accept(prepare.isChecked());});
        confirm.setBackgroundTintList(null);confirm.setBackground(Design.feedback(activity,Design.ACCENT,12));confirm.setTextColor(Design.BG);
        for(Button button:new Button[]{cancel,confirm}) {LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,Design.dp(activity,58),1);params.topMargin=Design.dp(activity,18);if(actions.getChildCount()>0)params.leftMargin=Design.dp(activity,18);actions.addView(button,params);}
        dialog.setContentView(body);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes=window.getAttributes();attributes.dimAmount=.58f;window.setAttributes(attributes);
        dialog.show();window.setLayout(Math.min(Design.dp(activity,1000),(int)(activity.getResources().getDisplayMetrics().widthPixels*.88)),-2);return dialog;
    }
}
