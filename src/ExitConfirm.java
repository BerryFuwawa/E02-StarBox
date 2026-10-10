package com.e02.rootconsole;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** Clickable gray exit uses the same rounded visual language as the existing dialogs. */
final class ExitConfirm {
    static void style(Button button,boolean blocked) {
        button.setEnabled(true);
        button.setBackgroundTintList(null);
        button.setBackground(Design.feedback(button.getContext(),blocked?0xff414650:Design.DANGER,12));
        button.setTextColor(Design.TEXT);
        button.setContentDescription(blocked?"退出程序及后台，当前不可退出，点击查看已开启的选项":"退出程序及后台");
    }
    static Dialog show(Context context,boolean keepAlive,boolean autoStart,boolean parkedRemote,boolean wakeRecovery,Runnable settings) {
        Dialog dialog=new Dialog(context);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body=Design.column(context);body.setPadding(Design.dp(context,28),Design.dp(context,24),Design.dp(context,28),Design.dp(context,24));
        body.setBackground(Design.surface(context,Design.CARD,24));body.setClipToOutline(true);
        TextView title=Design.text(context,"暂时无法退出",26);title.setGravity(Gravity.CENTER);body.addView(title);
        TextView message=Design.text(context,ExitPolicy.message(keepAlive,autoStart,parkedRemote,wakeRecovery),20);message.setTextColor(Design.TEXT);body.addView(message);
        LinearLayout row=new LinearLayout(context);body.addView(row);
        Button cancel=Design.button(context,"取消",dialog::dismiss),goSettings=Design.button(context,"前往后台",()->{dialog.dismiss();settings.run();});
        goSettings.setBackground(Design.feedback(context,Design.ACCENT,12));goSettings.setTextColor(Design.BG);
        for(Button button:new Button[]{cancel,goSettings}) {LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,Design.dp(context,58),1);params.topMargin=Design.dp(context,18);if(row.getChildCount()>0)params.leftMargin=Design.dp(context,18);row.addView(button,params);}
        dialog.setContentView(body);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes=window.getAttributes();attributes.dimAmount=.58f;window.setAttributes(attributes);dialog.show();window.setLayout(Math.min(Design.dp(context,850),(int)(context.getResources().getDisplayMetrics().widthPixels*.88)),-2);
        return dialog;
    }
}
