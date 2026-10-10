package com.e02.rootconsole;
import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.List;
/** Readable car-sized selections without changing system density. */
public class UiAdapter extends ArrayAdapter<String> {
 private final boolean warning;
 public UiAdapter(Context c,List<String> values){this(c,values,false);}
 public UiAdapter(Context c,List<String> values,boolean warning){super(c,android.R.layout.simple_spinner_item,values);this.warning=warning;setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);}
 public UiAdapter(Context c,String[] values){this(c,java.util.Arrays.asList(values));}
 private View size(View v){if(v instanceof TextView){((TextView)v).setTextSize(20);if(warning)((TextView)v).setTextColor(Design.YELLOW);v.setMinimumHeight((int)(44*getContext().getResources().getDisplayMetrics().density));}return v;}
 public View getView(int position,View convert,ViewGroup parent){return size(super.getView(position,convert,parent));}
 public View getDropDownView(int position,View convert,ViewGroup parent){return size(super.getDropDownView(position,convert,parent));}
}
