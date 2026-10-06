package com.e02.rootconsole;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Solid native surfaces: no blur, web rendering, or animation loops. */
public final class Design {
 public static final int BG=0xff14181e,CARD=0xff202630,LINE=0xff354050,TEXT=0xffe8edf4,MUTED=0xffa5b2c4,ACCENT=0xffa8d2e9,DANGER=0xffbb394d;
 public static final int GREEN=0xff86b89b,RED=0xffd58b93,GREEN_BUTTON=0xff527966,RED_BUTTON=0xff985760;
 public static void actionColor(Button b,boolean start){b.setBackground(surface(b.getContext(),start?GREEN_BUTTON:RED_BUTTON,12));b.setTextColor(TEXT);}
 public static class StatusText extends TextView {
  private final Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG);private int state;
  public StatusText(Context c,String label,int size){super(c);setText(label);setTextSize(size);setTextColor(TEXT);if(size>=25)setTypeface(Typeface.DEFAULT_BOLD);setPadding(0,dp(c,5),dp(c,28),dp(c,5));}
  public void state(boolean enabled){state=enabled?1:0;setContentDescription(getText()+" · "+(enabled?"已启用":"已关闭"));invalidate();}
  protected void onDraw(Canvas c){super.onDraw(c);dot.setColor(state==1?GREEN:RED);float x=Math.min(getWidth()-dp(getContext(),11),getPaddingLeft()+getPaint().measureText(getText().toString())+dp(getContext(),18));c.drawCircle(x,getHeight()/2f,dp(getContext(),6),dot);}
 }
 public static int dp(Context c,int n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
 public static GradientDrawable surface(Context c,int color,int radius){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(c,radius));b.setStroke(dp(c,1),LINE);return b;}
 public static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
 public static void card(LinearLayout l){Context c=l.getContext();l.setPadding(dp(c,22),dp(c,18),dp(c,22),dp(c,18));l.setBackground(surface(c,CARD,14));}
 public static TextView text(Context c,String value,int size){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(size>=25?TEXT:MUTED);if(size>=25)t.setTypeface(Typeface.DEFAULT_BOLD);t.setPadding(0,dp(c,5),0,dp(c,5));return t;}
 public static Button button(Context c,String value,Runnable action){Button b=new Button(c);b.setText(value);b.setAllCaps(false);b.setTextSize(20);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(TEXT);b.setBackground(surface(c,0xff303b4a,12));b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(c,12),0,dp(c,12),0);b.setOnClickListener(v->action.run());return b;}
 public static void addButton(LinearLayout l,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(l.getContext(),54));p.setMargins(0,dp(l.getContext(),6),0,dp(l.getContext(),6));l.addView(b,p);}
 public static void input(EditText e,String label,boolean secret){Context c=e.getContext();e.setTextSize(20);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));e.setBackground(surface(c,0xff171d26,9));e.setMinHeight(dp(c,52));if(secret)e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);PhoneCompanion.field(e,label);}
 public static void toggle(CompoundButton c){c.setTextSize(20);c.setTextColor(TEXT);c.setMinHeight(dp(c.getContext(),46));c.setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,MUTED}));}
 public static ScrollView scroll(Context c,LinearLayout body){ScrollView s=new ScrollView(c);s.setFillViewport(false);s.setClipToPadding(false);s.addView(body,new ScrollView.LayoutParams(-1,-2));return s;}
 public static android.app.AlertDialog show(android.app.AlertDialog.Builder builder){android.app.AlertDialog d=builder.create();d.setOnShowListener(v->dialog(d));d.show();return d;}
 /** Clip the whole dialog, rather than painting square backgrounds over its corners. */
 public static void dialog(android.app.AlertDialog d){
  Context c=d.getContext();Window w=d.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(Math.min(dp(c,1040),(int)(c.getResources().getDisplayMetrics().widthPixels*.88)),-2);styleDialogTree(w.getDecorView());
  View content=w.findViewById(android.R.id.content);if(content!=null){content.setBackground(surface(c,CARD,24));content.setClipToOutline(true);content.setElevation(dp(c,8));content.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,12));}
  w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.dimAmount=.58f;w.setAttributes(a);
  for(int id:new int[]{android.app.AlertDialog.BUTTON_POSITIVE,android.app.AlertDialog.BUTTON_NEGATIVE,android.app.AlertDialog.BUTTON_NEUTRAL}){Button b=d.getButton(id);if(b==null||b.getVisibility()!=View.VISIBLE)continue;b.setAllCaps(false);b.setTextSize(20);b.setTypeface(Typeface.DEFAULT_BOLD);b.setGravity(Gravity.CENTER);b.setTextColor(id==android.app.AlertDialog.BUTTON_POSITIVE?BG:TEXT);b.setBackground(surface(c,id==android.app.AlertDialog.BUTTON_POSITIVE?ACCENT:0xff303b4a,12));b.setBackgroundTintList(null);b.setMinHeight(dp(c,56));b.setMinimumHeight(dp(c,56));b.setMinWidth(dp(c,180));b.setPadding(dp(c,20),dp(c,8),dp(c,20),dp(c,8));ViewGroup.LayoutParams p=b.getLayoutParams();if(p instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)p;m.setMargins(dp(c,6),dp(c,8),dp(c,6),dp(c,8));b.setLayoutParams(m);}if(b.getParent() instanceof LinearLayout)((LinearLayout)b.getParent()).setGravity(Gravity.CENTER);}
 }
 private static boolean frameworkView(View v){try{return v.getId()!=View.NO_ID&&"android".equals(v.getResources().getResourcePackageName(v.getId()));}catch(android.content.res.Resources.NotFoundException e){return false;}}
 private static void styleDialogTree(View v){if(v instanceof ViewGroup){if(frameworkView(v)||!(v.getBackground() instanceof GradientDrawable))v.setBackgroundColor(Color.TRANSPARENT);ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)styleDialogTree(g.getChildAt(i));}else if(frameworkView(v)&&v instanceof TextView&&!(v instanceof EditText)&&!(v instanceof CompoundButton)){TextView t=(TextView)v;t.setTextColor(TEXT);t.setTextSize(v instanceof Button?20:22);}}
 public static class AdbAction extends CheckBox {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private int indicator=-1;public void state(int value){indicator=value;invalidate();}
  public AdbAction(Context c){super(c);setButtonDrawable(null);setTextColor(TEXT);setTextSize(18);setMinHeight(dp(c,62));setGravity(Gravity.CENTER_VERTICAL);setPadding(0,0,dp(c,122),0);}
  protected void onDraw(Canvas canvas){super.onDraw(canvas);paint.setColor(indicator<0?MUTED:indicator==1?GREEN:RED);canvas.drawCircle(getWidth()-dp(getContext(),108),getHeight()/2f,dp(getContext(),6),paint);float right=getWidth(),left=right-dp(getContext(),88),top=(getHeight()-dp(getContext(),44))/2f;paint.setColor(isEnabled()?isChecked()?0xff3a4658:ACCENT:0xff2b3441);canvas.drawRoundRect(left,top,right,top+dp(getContext(),44),dp(getContext(),8),dp(getContext(),8),paint);paint.setColor(isEnabled()&&!isChecked()?BG:isEnabled()?TEXT:MUTED);paint.setTextSize(dp(getContext(),19));paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(isEnabled()?isChecked()?"关闭":"开启":"待检测",(left+right)/2,top+dp(getContext(),28),paint);}
 }
}
