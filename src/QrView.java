package com.e02.rootconsole;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.google.zxing.qrcode.encoder.*;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
/** Encodes a local URL on-device; no external QR service receives connection details. */
public final class QrView extends View {
 private final Paint p=new Paint();private ByteMatrix matrix;private String lastUrl;
 public QrView(Context c,String url){super(c);setContentDescription("手机助手连接二维码");setUrl(url);}
 public void setUrl(String url){if(url.equals(lastUrl))return;lastUrl=url;try{matrix=Encoder.encode(url,ErrorCorrectionLevel.M).getMatrix();}catch(Exception e){matrix=null;}invalidate();}
 protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.WHITE);if(matrix==null)return;int units=matrix.getWidth()+8;int pixel=Math.max(1,Math.min(getWidth(),getHeight())/units);int left=(getWidth()-units*pixel)/2+4*pixel,top=(getHeight()-units*pixel)/2+4*pixel;p.setColor(Color.BLACK);for(int y=0;y<matrix.getHeight();y++)for(int x=0;x<matrix.getWidth();x++)if(matrix.get(x,y)==1)c.drawRect(left+x*pixel,top+y*pixel,left+(x+1)*pixel,top+(y+1)*pixel,p);}
}
