package com.e02.rootconsole;
import android.content.Context;
import org.json.JSONObject;
import java.io.*;

/** Private task metadata only. APK bytes are stored exclusively in Downloads. */
public final class UpdateTask {
 public static File folder(Context c){return new File(c.getFilesDir(),"update-task");}
 public static JSONObject read(File file)throws Exception{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>131072)throw new IOException("更新任务信息异常");}return new JSONObject(out.toString("UTF-8"));}}
 public static void write(File file,JSONObject value,int owner)throws Exception{File temp=new File(file.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(value.toString().getBytes("UTF-8"));out.getFD().sync();}if(android.os.Process.myUid()==0){android.system.Os.chown(temp.getPath(),owner,owner);android.system.Os.chmod(temp.getPath(),0600);}if(!temp.renameTo(file))throw new IOException("无法保存更新结果");}
 public static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
}
