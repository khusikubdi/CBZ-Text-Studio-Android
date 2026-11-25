package com.lowlifewarrior.cbzstudio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Base64;
import android.util.AtomicFile;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import fi.iki.elonen.NanoHTTPD;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import java.util.zip.*;

final class LocalEditor extends NanoHTTPD {
    private final Context context; private final File archives, exports; private final AtomicFile session;
    private final String token=UUID.randomUUID().toString().replace("-","");
    private final Map<String,Job> jobs=new HashMap<>();
    private static final long MB=1024L*1024L;
    static class Job {File file; int count; boolean ready; Job(File f){file=f;}}
    LocalEditor(Context c) throws IOException {super("127.0.0.1",0);context=c.getApplicationContext();archives=new File(c.getFilesDir(),"archives");exports=new File(c.getCacheDir(),"exports");if(!archives.mkdirs()&&!archives.isDirectory())throw new IOException("Archive directory unavailable");exports.mkdirs();session=new AtomicFile(new File(c.getFilesDir(),"session.json"));}
    String base(){return "http://127.0.0.1:"+getListeningPort()+"/"+token+"/";}
    static void copy(InputStream in,OutputStream out)throws IOException{byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
    static String id(){return UUID.randomUUID().toString().replace("-","");}
    static void validate(String id)throws IOException{if(!id.matches("[a-f0-9]{20,32}"))throw new IOException("Invalid archive ID");}
    File archive(String id)throws IOException{validate(id);return new File(archives,id+".cbz");}
    File meta(String id)throws IOException{validate(id);return new File(archives,id+".json");}
    static byte[] bytes(InputStream in,long max)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>max)throw new IOException("Input too large");o.write(b,0,n);}return o.toByteArray();}
    static byte[] body(IHTTPSession s,long max)throws IOException {long n=length(s,max);ByteArrayOutputStream o=new ByteArrayOutputStream();transfer(s.getInputStream(),o,n);return o.toByteArray();}
    static long length(IHTTPSession s,long max)throws IOException {String h=s.getHeaders().get("content-length");long n=h==null?0:Long.parseLong(h);if(n<0||n>max)throw new IOException("Request exceeds limit");if(h==null&&s.getHeaders().containsKey("transfer-encoding"))throw new IOException("Unsupported streaming upload");return n;}
    static void transfer(InputStream in,OutputStream out,long n)throws IOException{byte[] b=new byte[65536];while(n>0){int k=in.read(b,0,(int)Math.min(n,b.length));if(k<0)throw new EOFException("Incomplete upload");out.write(b,0,k);n-=k;}}
    static JSONObject json(byte[] b)throws JSONException{return new JSONObject(new String(b,StandardCharsets.UTF_8));}
    static JSONObject obj(Object... pairs)throws JSONException{JSONObject o=new JSONObject();for(int i=0;i<pairs.length;i+=2)o.put((String)pairs[i],pairs[i+1]);return o;}
    static void writeAtomic(AtomicFile f,byte[] data)throws IOException{FileOutputStream out=f.startWrite();try{out.write(data);f.finishWrite(out);}catch(IOException e){f.failWrite(out);throw e;}}
    static Response response(Response.Status status,String mime,byte[] b){Response r=newFixedLengthResponse(status,mime,new ByteArrayInputStream(b),b.length);r.addHeader("Cache-Control","no-store");r.addHeader("X-Content-Type-Options","nosniff");return r;}
    static Response ok(Object o){return response(Response.Status.OK,"application/json; charset=utf-8",o.toString().getBytes(StandardCharsets.UTF_8));}
    static Response error(Exception e){try{return response(Response.Status.BAD_REQUEST,"application/json; charset=utf-8",obj("error",e.getMessage()==null?e.toString():e.getMessage()).toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){return newFixedLengthResponse(Response.Status.BAD_REQUEST,"application/json","{\"error\":\"Request failed\"}");}}
    static final Pattern PARTS=Pattern.compile("\\d+|\\D+");
    static int natural(String a,String b){Matcher x=PARTS.matcher(a.toLowerCase(Locale.ROOT)),y=PARTS.matcher(b.toLowerCase(Locale.ROOT));while(x.find()){if(!y.find())return 1;String p=x.group(),q=y.group();int r;if(Character.isDigit(p.charAt(0))&&Character.isDigit(q.charAt(0))){p=p.replaceFirst("^0+(?!$)","");q=q.replaceFirst("^0+(?!$)","");r=Integer.compare(p.length(),q.length());if(r==0)r=p.compareTo(q);}else r=p.compareTo(q);if(r!=0)return r;}return y.find()?-1:a.compareTo(b);}
    static boolean supported(String n){return n.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp|gif|bmp|avif)$")&&!Arrays.stream(n.split("/")).anyMatch(p->p.startsWith(".")||p.equals("__MACOSX"));}
    static String mime(String n){n=n.toLowerCase(Locale.ROOT);return n.endsWith(".png")?"image/png":n.endsWith(".webp")?"image/webp":n.endsWith(".gif")?"image/gif":n.endsWith(".bmp")?"image/bmp":n.endsWith(".avif")?"image/avif":"image/jpeg";}
    static JSONArray dimensions(InputStream in){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeStream(in,null,o);return o.outWidth>0&&o.outHeight>0?new JSONArray().put(o.outWidth).put(o.outHeight):null;}
    JSONObject describe(String id,String hash)throws Exception{List<String> names=new ArrayList<>();JSONArray dims=new JSONArray();try(ZipFile z=new ZipFile(archive(id))){Enumeration<? extends ZipEntry> entries=z.entries();while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();if(!e.isDirectory()&&supported(e.getName())&&e.getSize()>=0&&e.getSize()<=150*MB)names.add(e.getName());}names.sort(LocalEditor::natural);if(names.isEmpty())throw new IOException("No supported images in archive");if(names.size()>10000)throw new IOException("Too many pages");for(String n:names){try(InputStream in=z.getInputStream(z.getEntry(n))){JSONArray d=dimensions(in);dims.put(d==null?JSONObject.NULL:d);}}}JSONObject m=obj("id",id,"fingerprint",hash,"pages",new JSONArray(names),"dimensions",dims);writeAtomic(new AtomicFile(meta(id)),m.toString().getBytes(StandardCharsets.UTF_8));return m;}
    JSONObject metadata(String id)throws Exception{return json(new AtomicFile(meta(id)).readFully());}
    byte[] page(String id,int index)throws Exception{String name=metadata(id).getJSONArray("pages").getString(index);try(ZipFile z=new ZipFile(archive(id));InputStream in=z.getInputStream(z.getEntry(name))){return bytes(in,150*MB);}}
    static String hash(byte[] d)throws Exception{StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    synchronized File completedExport(String id)throws IOException{validate(id);Job j=jobs.get(id);if(j==null||!j.ready)throw new IOException("Export not ready");return j.file;}
    @Override public Response serve(IHTTPSession s){try{
        String prefix="/"+token+"/";String uri=s.getUri();if(!uri.startsWith(prefix))return newFixedLengthResponse(Response.Status.FORBIDDEN,"text/plain","Invalid local session");String p=uri.substring(prefix.length());String[] a=p.split("/");boolean get=s.getMethod()==Method.GET;
        if(get&&p.isEmpty()){try(InputStream in=context.getAssets().open("index.html")){return response(Response.Status.OK,"text/html; charset=utf-8",bytes(in,5*MB));}}
        if(get&&p.equals("status"))return ok(obj("ocr",false,"languages",new JSONArray(Arrays.asList("eng","jpn","kor","chi_sim","chi_tra")),"ai",obj("smart",true,"comic",false)));
        if(get&&p.equals("session")){synchronized(session){return session.getBaseFile().exists()?response(Response.Status.OK,"application/json",session.readFully()):ok("null");}}
        if(get&&a.length==3&&a[0].equals("image")){String name=metadata(a[1]).getJSONArray("pages").getString(Integer.parseInt(a[2]));return response(Response.Status.OK,mime(name),page(a[1],Integer.parseInt(a[2])));}
        if(get&&a.length==2&&a[0].equals("download")){File f=completedExport(a[1]);Response r=newFixedLengthResponse(Response.Status.OK,"application/zip",new FileInputStream(f),f.length());r.addHeader("Content-Disposition","attachment; filename=edited.cbz");return r;}
        if(s.getMethod()!=Method.POST)return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","Not found");
        if(p.equals("session")){byte[] d=body(s,128*MB);JSONObject next=json(d);synchronized(session){if(session.getBaseFile().exists()&&json(session.readFully()).optLong("revision")>next.optLong("revision"))return ok(obj("saved",false));writeAtomic(session,d);}return ok(obj("saved",true));}
        if(p.equals("open")){String k=id();File f=archive(k);MessageDigest md=MessageDigest.getInstance("SHA-256");try{long n=length(s,4L*1024*MB);try(OutputStream out=new java.security.DigestOutputStream(new FileOutputStream(f),md)){transfer(s.getInputStream(),out,n);}return ok(describe(k,hash(md.digest())));}catch(Exception e){f.delete();meta(k).delete();throw e;}}
        if(p.equals("insert")){byte[] d=body(s,50*MB);String k=id();if(dimensions(new ByteArrayInputStream(d))==null)throw new IOException("Invalid image");String ext=d.length>8&&d[0]==(byte)0x89?"png":d.length>2&&d[0]==(byte)0xff?"jpg":d.length>4&&d[0]=='R'&&d[1]=='I'?"webp":d.length>3&&d[0]=='G'?"gif":d.length>2&&d[0]=='B'?"bmp":null;if(ext==null)throw new IOException("Choose PNG, JPEG, WEBP, GIF or BMP");try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(archive(k)))){z.putNextEntry(new ZipEntry("inserted."+ext));z.write(d);z.closeEntry();}return ok(describe(k,hash(MessageDigest.getInstance("SHA-256").digest(d))));}
        if(p.equals("export-start")){String k=id();synchronized(this){jobs.put(k,new Job(new File(exports,k+".cbz")));}return ok(obj("id",k));}
        if(a.length==2&&a[0].equals("export-page")){byte[] d=body(s,200*MB);if(d.length<8||d[0]!=(byte)0x89)throw new IOException("Expected PNG");synchronized(this){Job j=jobs.get(a[1]);if(j==null||j.ready)throw new IOException("Invalid job");File f=new File(exports,a[1]+"-"+(j.count+1)+".png");try(FileOutputStream o=new FileOutputStream(f)){o.write(d);}return ok(obj("count",++j.count));}}
        if(a.length==2&&a[0].equals("export-end")){synchronized(this){Job j=jobs.get(a[1]);if(j==null||j.ready||j.count==0)throw new IOException("Invalid job");try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(j.file))){z.setLevel(0);for(int i=1;i<=j.count;i++){File f=new File(exports,a[1]+"-"+i+".png");z.putNextEntry(new ZipEntry(String.format(Locale.ROOT,"%06d.png",i)));try(InputStream in=new FileInputStream(f)){copy(in,z);}z.closeEntry();}}for(int i=1;i<=j.count;i++)new File(exports,a[1]+"-"+i+".png").delete();j.ready=true;return ok(obj("count",j.count));}}
        if(a.length==3&&a[0].equals("ocr")){JSONObject opts=json(body(s,32*MB));byte[] d=opts.has("crop")?Base64.decode(opts.getString("crop"),Base64.DEFAULT):page(a[1],Integer.parseInt(a[2]));return ok(obj("boxes",ocr(d,opts),"engine","smart"));}
        return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","Not found");
    }catch(Exception e){return error(e);}}
    static TextRecognizer recognizer(String lang){switch(lang){case "jpn":return TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());case "kor":return TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());case "chi_sim":case "chi_tra":return TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());default:return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);}}
    static JSONArray ocr(byte[] d,JSONObject opts)throws Exception{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(d,0,d.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Invalid image");BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=1;while((long)bounds.outWidth*bounds.outHeight/o.inSampleSize/o.inSampleSize>12000000L)o.inSampleSize*=2;Bitmap bm=BitmapFactory.decodeByteArray(d,0,d.length,o);if(bm==null)throw new IOException("Could not decode image");double sx=(double)bounds.outWidth/bm.getWidth(),sy=(double)bounds.outHeight/bm.getHeight();TextRecognizer r=recognizer(opts.optString("lang","eng"));JSONArray result=new JSONArray();try{Text t=Tasks.await(r.process(InputImage.fromBitmap(bm,0)),180,TimeUnit.SECONDS);boolean grouped=opts.optBoolean("group",true);int threshold=opts.optInt("threshold",65);for(Text.TextBlock block:t.getTextBlocks()){List<Text.Line> lines=block.getLines();if(grouped){List<Text.Line> accepted=new ArrayList<>();for(Text.Line l:lines)if(confidence(l)>=threshold)accepted.add(l);add(result,accepted,sx,sy);}else for(Text.Line l:lines)if(confidence(l)>=threshold)add(result,Collections.singletonList(l),sx,sy);}return result;}finally{r.close();bm.recycle();}}
    static int confidence(Text.Line l){float c=l.getConfidence();return Math.round(c*100);}
    static void add(JSONArray out,List<Text.Line> lines,double sx,double sy)throws JSONException{Rect box=null;StringBuilder text=new StringBuilder();double h=0,conf=0;int n=0;for(Text.Line l:lines){Rect b=l.getBoundingBox();if(b==null||b.isEmpty()||l.getText().trim().isEmpty())continue;if(box==null)box=new Rect(b);else box.union(b);if(n>0)text.append('\n');text.append(l.getText());h+=b.height();conf+=confidence(l);n++;}if(n==0)return;int pad=2;out.put(obj("x",Math.max(0,(int)Math.floor(box.left*sx)-pad),"y",Math.max(0,(int)Math.floor(box.top*sy)-pad),"w",(int)Math.ceil(box.width()*sx)+2*pad,"h",(int)Math.ceil(box.height()*sy)+2*pad,"text",text.toString(),"size",Math.max(8,(int)Math.round(h/n*sy)),"confidence",Math.round(conf/n)));}
}
