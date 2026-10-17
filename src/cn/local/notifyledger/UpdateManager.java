package cn.local.notifyledger;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public final class UpdateManager {
    public static final String REPO="https://github.com/Debbie-anan/daily-spending";
    private final Activity activity;
    private boolean busy=false, awaitingPermission=false;
    public UpdateManager(Activity a){activity=a;}
    private boolean alive(){return !activity.isFinishing()&&!activity.isDestroyed();}
    private long code(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    private PackageInfo installed() throws Exception {return activity.getPackageManager().getPackageInfo(activity.getPackageName(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);}
    public String version(){try{return installed().versionName;}catch(Exception e){return "?";}}
    private File apk(){return new File(activity.getCacheDir(),"updates/release.apk");}
    private void error(String text){if(alive())new AlertDialog.Builder(activity).setTitle("更新未完成").setMessage(text).setNegativeButton("关闭",null).setPositiveButton("打开发布页",(d,w)->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(REPO+"/releases/latest")));}catch(Exception ignored){}}).show();}
    private HttpsURLConnection connection(String address) throws Exception {
        for(int i=0;i<6;i++){
            URL url=new URL(address);String host=url.getHost();
            if(!"https".equals(url.getProtocol()) || !(host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")))throw new IOException("下载地址不受支持");
            HttpsURLConnection c=(HttpsURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","DailySpending-Android");c.setRequestProperty("Accept","application/octet-stream");
            int status=c.getResponseCode();if(status>=300&&status<400){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("无效下载跳转");address=new URL(url,next).toString();continue;}
            if(status!=200){c.disconnect();throw new IOException("GitHub 返回 "+status);}
            return c;
        }throw new IOException("下载跳转过多");
    }
    private JSONObject metadata() throws Exception {
        HttpsURLConnection c=connection(REPO+"/releases/latest/download/update.json");
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("更新信息过大");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));
        }finally{c.disconnect();}
    }
    public void check(){
        if(busy){Toast.makeText(activity,"正在处理更新，请稍候",Toast.LENGTH_SHORT).show();return;}busy=true;
        ProgressDialog progress=new ProgressDialog(activity);progress.setMessage("正在检查 GitHub 最新版本…");progress.setCancelable(false);progress.show();
        new Thread(()->{
            try {JSONObject m=metadata();long candidate=m.getLong("versionCode");long current=code(installed());
                if(!UpdatePolicy.validApkUrl(m.getString("apkUrl"))||!UpdatePolicy.validHash(m.getString("sha256")))throw new IOException("更新信息格式无效");
                activity.runOnUiThread(()->{busy=false;if(!alive())return;progress.dismiss();if(!UpdatePolicy.newer(current,candidate)){new AlertDialog.Builder(activity).setTitle("已是最新版本").setMessage("当前版本："+version()).setPositiveButton("知道了",null).show();return;}new AlertDialog.Builder(activity).setTitle("发现新版本 "+m.optString("versionName")).setMessage(m.optString("notes","修复与改进")+"\n\n从 GitHub 下载并覆盖安装，保留本机记录。安装需你确认。").setNegativeButton("稍后",null).setPositiveButton("下载更新",(d,w)->download(m)).show();});
            }catch(Exception ex){activity.runOnUiThread(()->{busy=false;if(alive()){progress.dismiss();error("暂时无法检查更新。请检查网络，或稍后再试。\n"+ex.getMessage());}});}
        },"check-update").start();
    }
    private String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private Signature[] signatures(PackageInfo p){return Build.VERSION.SDK_INT>=28?(p.signingInfo==null?null:p.signingInfo.getApkContentsSigners()):p.signatures;}
    private void verify(File f,long expectedVersion) throws Exception {
        PackageInfo candidate=activity.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
        PackageInfo own=installed();if(candidate==null||!activity.getPackageName().equals(candidate.packageName)||!UpdatePolicy.newer(code(own),code(candidate))||(expectedVersion>0&&code(candidate)!=expectedVersion))throw new IOException("安装包版本或应用标识不匹配");
        Signature[] a=signatures(candidate),b=signatures(own);if(a==null||b==null||a.length!=b.length)throw new IOException("安装包签名不匹配");
        Set<String> left=new HashSet<>(),right=new HashSet<>();for(Signature s:a)left.add(s.toCharsString());for(Signature s:b)right.add(s.toCharsString());if(!left.equals(right))throw new IOException("安装包签名不匹配");
    }
    private void download(JSONObject m){
        busy=true;ProgressDialog progress=new ProgressDialog(activity);progress.setTitle("下载更新");progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);progress.setIndeterminate(true);progress.setCancelable(false);progress.show();
        new Thread(()->{File part=new File(activity.getCacheDir(),"updates/release.part");
            try {if(!part.getParentFile().isDirectory()&&!part.getParentFile().mkdirs())throw new IOException("无法创建下载目录");
                HttpsURLConnection c=connection(m.getString("apkUrl"));MessageDigest digest=MessageDigest.getInstance("SHA-256");long downloaded=0,length=c.getContentLengthLong();if(length>UpdatePolicy.MAX_APK_BYTES){c.disconnect();throw new IOException("安装包过大");}
                try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){downloaded+=n;if(downloaded>UpdatePolicy.MAX_APK_BYTES)throw new IOException("安装包过大");out.write(b,0,n);digest.update(b,0,n);final int percent=length>0?(int)(100*downloaded/length):0;activity.runOnUiThread(()->{if(alive()){progress.setIndeterminate(length<=0);progress.setProgress(percent);}});}}finally{c.disconnect();}
                if(length>0&&downloaded!=length)throw new IOException("下载不完整");if(!hex(digest.digest()).equalsIgnoreCase(m.getString("sha256")))throw new IOException("安装包校验失败，请重试");verify(part,m.getLong("versionCode"));
                File dest=apk();if(dest.exists()&&!dest.delete())throw new IOException("无法替换旧安装包");if(!part.renameTo(dest))throw new IOException("无法保存安装包");
                activity.runOnUiThread(()->{busy=false;if(alive()){progress.dismiss();install();}});
            }catch(Exception ex){part.delete();activity.runOnUiThread(()->{busy=false;if(alive()){progress.dismiss();error("下载或校验失败，未安装任何更新。\n"+ex.getMessage());}});}
        },"download-update").start();
    }
    public void resume(){if(awaitingPermission){awaitingPermission=false;if(activity.getPackageManager().canRequestPackageInstalls())install();}}
    public void install(){
        try {verify(apk(),0);
            if(!activity.getPackageManager().canRequestPackageInstalls()){
                new AlertDialog.Builder(activity).setTitle("允许安装更新").setMessage("请在下一页允许“今日花费”安装应用。返回后继续安装已校验的更新包。").setNegativeButton("稍后",null).setPositiveButton("去设置",(d,w)->{try{awaitingPermission=true;activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}catch(Exception ex){awaitingPermission=false;error("无法打开安装权限设置，请在系统设置中查找“安装未知应用”。");}}).show();return;
            }
            Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/update.apk");Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("app update",uri));activity.startActivity(i);
        }catch(Exception ex){error("暂无可安装的新版本，或安装包验证失败。请先点击“检查更新”。\n"+ex.getMessage());}
    }
}
