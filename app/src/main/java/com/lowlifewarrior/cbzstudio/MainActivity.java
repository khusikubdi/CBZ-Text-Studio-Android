package com.lowlifewarrior.cbzstudio;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.*;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web; private LocalEditor server; private ValueCallback<Uri[]> chooser;
    private File pendingSave; private String base;
    private static final int OPEN=101, SAVE=102;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try {server=new LocalEditor(this);server.start(180000,false);base=server.base();} catch(Exception e){Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();finish();return;}
        web=new WebView(this);setContentView(web);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportZoom(false);
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){return !r.getUrl().toString().startsWith(base);}});
        web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams params){if(chooser!=null)chooser.onReceiveValue(null);chooser=cb;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);try{startActivityForResult(i,OPEN);}catch(Exception e){chooser.onReceiveValue(null);chooser=null;}return true;}});
        web.addJavascriptInterface(new Bridge(),"AndroidStudio");web.loadUrl(base);
    }
    private void save(File f,String name,String mime){runOnUiThread(()->{if(pendingSave!=null){Toast.makeText(this,"Finish the previous save first",Toast.LENGTH_SHORT).show();return;}pendingSave=f;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,name.replaceAll("[/\\\\]","_"));try{startActivityForResult(i,SAVE);}catch(Exception e){pendingSave=null;Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();}});}
    public class Bridge {
        @JavascriptInterface public void saveText(String name,String data,String mime){try{File f=File.createTempFile("project-",".tmp",getCacheDir());try(FileOutputStream o=new FileOutputStream(f)){o.write(data.getBytes(StandardCharsets.UTF_8));}save(f,name,mime);}catch(Exception e){runOnUiThread(()->Toast.makeText(MainActivity.this,e.toString(),Toast.LENGTH_LONG).show());}}
        @JavascriptInterface public void saveArchive(String id,String name){try{save(server.completedExport(id),name,"application/zip");}catch(Exception e){runOnUiThread(()->Toast.makeText(MainActivity.this,e.toString(),Toast.LENGTH_LONG).show());}}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==OPEN&&chooser!=null){Uri[] u=null;if(result==RESULT_OK&&data!=null){if(data.getClipData()!=null){u=new Uri[data.getClipData().getItemCount()];for(int j=0;j<u.length;j++)u[j]=data.getClipData().getItemAt(j).getUri();}else if(data.getData()!=null)u=new Uri[]{data.getData()};}chooser.onReceiveValue(u);chooser=null;}else if(request==SAVE){File f=pendingSave;pendingSave=null;if(f!=null&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri dest=data.getData();new Thread(()->{try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(dest,"w")){if(out==null)throw new IOException("Cannot open destination");LocalEditor.copy(in,out);runOnUiThread(()->Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Save failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();}}}
    @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("AndroidStudio");web.destroy();}if(server!=null)server.stop();super.onDestroy();}
}
