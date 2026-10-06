package com.e02.rootconsole;
import android.content.Context;
import android.graphics.Rect;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

/** A non-focusable popup keeps the real field available to the phone assistant. */
public final class NumberPad {
 private static PopupWindow current;
 public static boolean dismiss(){if(current==null)return false;PopupWindow p=current;current=null;p.dismiss();return true;}
 public static void attach(EditText field){field.setShowSoftInputOnFocus(false);field.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN)field.setShowSoftInputOnFocus(false);if(event.getAction()==MotionEvent.ACTION_UP)field.post(()->show(field));return false;});}
 private static void replace(EditText e,String value){int a=e.getSelectionStart(),b=e.getSelectionEnd();if(a<0||b<0)a=b=e.length();int start=Math.min(a,b),end=Math.max(a,b);e.getText().replace(start,end,value);e.setSelection(start+value.length());}
 private static void backspace(EditText e){int a=e.getSelectionStart(),b=e.getSelectionEnd();if(a<0||b<0)a=b=e.length();if(a!=b)replace(e,"");else if(a>0){e.getText().delete(a-1,a);e.setSelection(a-1);}}
 private static void show(EditText e){dismiss();e.setShowSoftInputOnFocus(false);e.requestFocus();Context c=e.getContext();InputMethodManager ime=(InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE);ime.hideSoftInputFromWindow(e.getWindowToken(),0);
  int height=Design.dp(c,360);Rect visible=new Rect();e.getWindowVisibleDisplayFrame(visible);int[] loc=new int[2];e.getLocationOnScreen(loc);int needed=loc[1]+e.getHeight()+height+Design.dp(c,8)-visible.bottom;
  if(needed>0){ViewParent parent=e.getParent();while(parent!=null&&!(parent instanceof ScrollView))parent=parent.getParent();if(parent instanceof ScrollView)((ScrollView)parent).smoothScrollBy(0,needed);}
  e.postDelayed(()->{if(!e.isShown()||!e.hasFocus())return;render(e);},180);
 }
 private static void render(EditText e){dismiss();Context c=e.getContext();LinearLayout body=Design.column(c);body.setPadding(Design.dp(c,12),Design.dp(c,12),Design.dp(c,12),Design.dp(c,12));android.graphics.drawable.GradientDrawable base=Design.surface(c,0xff354659,16);base.setStroke(Design.dp(c,1),0xff70839a);body.setBackground(base);
  String[][] keys={{"1","2","3","← 退格"},{"4","5","6","清空"},{"7","8","9","."},{"车机输入法输入","0","完成"}};
  for(String[] row:keys){LinearLayout line=new LinearLayout(c);line.setBaselineAligned(false);line.setClipChildren(false);line.setGravity(Gravity.CENTER_VERTICAL);body.addView(line);for(String key:row){Button b=Design.button(c,key,()->{if(key.equals("← 退格"))backspace(e);else if(key.equals("清空"))e.setText("");else if(key.equals("完成"))dismiss();else if(key.equals("车机输入法输入")){dismiss();e.setShowSoftInputOnFocus(true);e.requestFocus();e.postDelayed(()->((InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(e,InputMethodManager.SHOW_IMPLICIT),120);}else replace(e,key);});b.setTextSize(key.equals("车机输入法输入")?14:20);if(key.equals("车机输入法输入")){b.setBackground(Design.surface(c,Design.ACCENT,12));b.setTextColor(Design.BG);}else if(key.equals("清空"))b.setBackground(Design.surface(c,0xffa56c72,12));else if(key.equals("完成"))b.setBackground(Design.surface(c,0xff6f8d7a,12));((android.graphics.drawable.GradientDrawable)b.getBackground()).setStroke(Design.dp(c,1),0xff8293a8);b.setElevation(Design.dp(c,2));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Design.dp(c,58),1);p.setMargins(Design.dp(c,3),Design.dp(c,3),Design.dp(c,3),Design.dp(c,3));line.addView(b,p);}}
  TextView tip=Design.text(c,"可以使用手机助手填写或导入",16);tip.setGravity(Gravity.CENTER);body.addView(tip);
  int width=Math.min(Design.dp(c,470),c.getResources().getDisplayMetrics().widthPixels-Design.dp(c,32));PopupWindow popup=new PopupWindow(body,width,-2,false);popup.setClippingEnabled(false);popup.setAttachedInDecor(false);popup.setBackgroundDrawable(Design.surface(c,0xff354659,16));popup.setOutsideTouchable(true);popup.setElevation(Design.dp(c,12));current=popup;popup.setOnDismissListener(()->{if(current==popup)current=null;});popup.showAsDropDown(e,0,Design.dp(c,6));
 }
}
