package com.e02.rootconsole;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
/** Grants the installer read-only access to exactly the verified update file. */
public class ApkProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 private File file(Uri uri)throws FileNotFoundException{if(!"/update.apk".equals(uri.getPath()))throw new FileNotFoundException();File f=new File(getContext().getFilesDir(),"updates/update.apk");if(!f.isFile())throw new FileNotFoundException();return f;}
 public String getType(Uri uri){return "application/vnd.android.package-archive";}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
 public Cursor query(Uri u,String[] projection,String selection,String[] args,String sort){try{File f=file(u);String[] names=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(names);Object[] row=new Object[names.length];for(int i=0;i<names.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(names[i])?"E02-StarBox-update.apk":OpenableColumns.SIZE.equals(names[i])?f.length():null;c.addRow(row);return c;}catch(Exception e){return null;}}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
