package cn.local.notifyledger;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.util.Base64;
import android.widget.*;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Download signed interface files, then serve locally; no ledger data is sent. */
public final class UiUpdates {
    public static final long BUNDLED_VERSION=1;
    public static final String DEFAULT_ENDPOINT="";
    private final Activity activity;
    private final Runnable reload;
    private final android.content.SharedPreferences prefs;
    private boolean busy=false;
    private long lastAttempt=0;
    public UiUpdates(Activity a,Runnable r){activity=a;reload=r;prefs=a.getSharedPreferences("ui-updates",0);}
    public long version(){return prefs.getLong("version",BUNDLED_VERSION);}
    public String endpoint(){return prefs.getString("endpoint",DEFAULT_ENDPOINT);}
    private String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private String hash(byte[] data) throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(data));}
    private byte[] read(InputStream in,int max) throws IOException {try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1){if(out.size()+n>max)throw new IOException("界面文件过大");out.write(b,0,n);}return out.toByteArray();}}
    private void verify(JSONObject m,byte[] html) throws Exception {
        String digest=hash(html);if(!digest.equalsIgnoreCase(m.getString("sha256")))throw new IOException("界面文件校验失败");
        byte[] key=Base64.decode(read(activity.getAssets().open("ui-public-key.txt"),4096),Base64.DEFAULT);
        java.security.Signature verifier=java.security.Signature.getInstance("SHA256withRSA");verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));
        String message=m.getLong("version")+"\n"+m.getLong("minShell")+"\n"+m.getString("sha256");verifier.update(message.getBytes(StandardCharsets.UTF_8));
        if(!verifier.verify(Base64.decode(m.getString("signature"),Base64.DEFAULT)))throw new IOException("界面签名不匹配，已拒绝加载");
    }
    public synchronized InputStream open() throws IOException {
        String path=prefs.getString("active","");if(!path.isEmpty())try{
            File dir=new File(activity.getFilesDir(),"interfaces/"+path);JSONObject m=new JSONObject(new String(read(new FileInputStream(new File(dir,"manifest.json")),65536),StandardCharsets.UTF_8));byte[] html=read(new FileInputStream(new File(dir,"index.html")),2097152);verify(m,html);return new ByteArrayInputStream(html);
        }catch(Exception e){prefs.edit().remove("active").remove("version").apply();}
        return activity.getAssets().open("index.html");
    }
    private URL safe(String address) throws Exception {URL u=new URL(address);if(!"https".equals(u.getProtocol())||u.getHost().isEmpty()||u.getUserInfo()!=null||u.getRef()!=null||u.getPort()>0||u.getQuery()!=null)throw new IOException("更新地址必须为无参数的 HTTPS 地址");return u;}
    private byte[] fetch(URL url,int max) throws Exception {
        HttpsURLConnection c=(HttpsURLConnection)url.openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Cache-Control","no-cache");c.setRequestProperty("User-Agent","DailySpending-Interface");
        try{if(c.getResponseCode()!=200)throw new IOException("服务器返回 "+c.getResponseCode());return read(c.getInputStream(),max);}finally{c.disconnect();}
    }
    private void toast(String s){if(!activity.isFinishing()&&!activity.isDestroyed())Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
    public void check(boolean manual){
        if(busy){if(manual)toast("正在检查界面更新");return;}if(endpoint().isEmpty()){if(manual)configure();return;}long now=System.currentTimeMillis();if(!manual&&now-lastAttempt<15000)return;lastAttempt=now;busy=true;
        final String address=endpoint();if(manual)toast("正在检查界面更新…");
        new Thread(()->{try {
            URL base=safe(address);JSONObject m=new JSONObject(new String(fetch(base,65536),StandardCharsets.UTF_8));long candidate=m.getLong("version");
            if(candidate<=version()){activity.runOnUiThread(()->{if(manual)toast("界面已是最新版本");});return;}
            long shell=Build.VERSION.SDK_INT>=28?activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).getLongVersionCode():activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionCode;
            if(m.getLong("minShell")>shell)throw new IOException("此界面需要更新安卓壳，请安装新版 APK");
            String filename=m.getString("html");if(!filename.matches("ui-[0-9]+\\.html"))throw new IOException("界面文件名无效");
            URL url=new URL(base,filename);if(!url.getHost().equals(base.getHost()))throw new IOException("更新来源不一致");byte[] html=fetch(url,2097152);verify(m,html);
            synchronized(this){if(!endpoint().equals(address))return;File root=new File(activity.getFilesDir(),"interfaces");File dir=new File(root,"v"+candidate+"-"+m.getString("sha256").substring(0,12));if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法保存界面");
                try(FileOutputStream out=new FileOutputStream(new File(dir,"index.html"))){out.write(html);out.getFD().sync();}try(FileOutputStream out=new FileOutputStream(new File(dir,"manifest.json"))){out.write(m.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
                if(!prefs.edit().putString("active",dir.getName()).putLong("version",candidate).commit())throw new IOException("无法切换界面");}
            activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed()){reload.run();toast("界面已更新，账本保持不变");}});
        }catch(Exception e){activity.runOnUiThread(()->{if(manual)toast("界面未更新，继续使用本地版本。"+e.getMessage());});}finally{busy=false;}},"interface-update").start();
    }
    public void configure(){EditText input=new EditText(activity);input.setSingleLine(true);input.setText(endpoint());input.setHint("https://你的地址/interface.json");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(activity).setTitle("界面更新地址").setMessage("只下载带本应用签名的界面，不上传账本。留空可暂停在线更新。").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{String value=input.getText().toString().trim();try{if(!value.isEmpty())safe(value);prefs.edit().putString("endpoint",value).apply();lastAttempt=0;toast(value.isEmpty()?"已暂停在线更新":"更新地址已保存");if(!value.isEmpty())check(true);}catch(Exception e){toast(e.getMessage());}}).show();
    }
}
