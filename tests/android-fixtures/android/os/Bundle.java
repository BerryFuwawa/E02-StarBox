package android.os;
import java.util.*;
public final class Bundle {
 public final Map<String,Object> values=new HashMap<>();
 public void putBoolean(String k,boolean v){values.put(k,v);}public void putInt(String k,int v){values.put(k,v);}
 public void putString(String k,String v){values.put(k,v);}public void putParcelable(String k,Object v){values.put(k,v);}
}
