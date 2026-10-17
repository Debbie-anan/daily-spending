package cn.local.notifyledger;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;
import org.json.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;
    private UpdateManager updater;
    private boolean ready=false;
    private boolean granted(){String value=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");ComponentName me=new ComponentName(this,PaymentListener.class);if(value==null)return false;for(String item:value.split(":"))if(me.equals(ComponentName.unflattenFromString(item)))return true;return false;}
    public void onCreate(Bundle saved){
        super.onCreate(saved);updater=new UpdateManager(this);
        getWindow().setStatusBarColor(Color.rgb(255,250,241));getWindow().setNavigationBarColor(Color.rgb(255,250,241));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web=new WebView(this);web.setBackgroundColor(Color.rgb(255,250,241));web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.getSettings().setSupportMultipleWindows(false);
        web.addJavascriptInterface(new Bridge(),"Ledger");
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return r.getUrl().toString().equals("file:///android_asset/index.html")?null:new WebResourceResponse("text/plain","utf-8",new java.io.ByteArrayInputStream(new byte[0]));}
            public void onPageFinished(WebView v,String url){ready=true;refresh();}
        });
        setContentView(web);web.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        web.loadUrl("file:///android_asset/index.html");
    }
    public void onResume(){super.onResume();if(updater!=null)updater.resume();refresh();}
    private void refresh(){if(ready&&web!=null)web.evaluateJavascript("window.refreshLedger && window.refreshLedger()",null);}
    private void open(Intent i){try{startActivity(i);}catch(Exception ex){Toast.makeText(this,"此系统未提供入口，请在手机设置中搜索对应选项",Toast.LENGTH_LONG).show();}}
    public final class Bridge {
        @JavascriptInterface public String readState(){
            try(LedgerDb db=new LedgerDb(MainActivity.this)){
                JSONObject o=new JSONObject(db.export());android.content.SharedPreferences p=getSharedPreferences("status",0);
                o.put("granted",granted());o.put("lastSeen",p.getLong("lastSeen",0));o.put("connectedAt",p.getLong("connectedAt",0));o.put("seen",p.getLong("seen",0));o.put("appVersion",updater.version());return o.toString();
            }catch(Exception ex){return "{\"error\":\"无法读取本机账本，请重新打开应用\"}";}
        }
        @JavascriptInterface public void action(String name){runOnUiThread(()->{
            if(name.equals("permission"))new AlertDialog.Builder(MainActivity.this).setTitle("读取付款通知").setMessage("系统授权覆盖所有通知。本应用只处理微信、支付宝中含支付相关词的通知，可能含商户名或聊天文字。账单仅保存在手机，不上传。可随时在系统设置撤销授权。").setNegativeButton("暂不",null).setPositiveButton("前往授权",(d,w)->open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))).show();
            else if(name.equals("settings"))open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
            else if(name.equals("update"))new AlertDialog.Builder(MainActivity.this).setTitle("更新来源已设为私有").setMessage("GitHub 仓库目前是私有的，免登录检查更新暂不可用。请使用聊天中提供的新版 APK 覆盖安装；本机记账不受影响。").setPositiveButton("知道了",null).show();
            else if(name.equals("install"))updater.install();
            else if(name.equals("export")){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"付款通知记录.json");try{startActivityForResult(i,41);}catch(Exception e){Toast.makeText(MainActivity.this,"找不到文件保存程序",Toast.LENGTH_LONG).show();}}
            else if(name.equals("clear"))new AlertDialog.Builder(MainActivity.this).setTitle("清空本机记录？").setMessage("所有已识别、待识别和已排除记录将永久删除，无法恢复。").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{try(LedgerDb db=new LedgerDb(MainActivity.this)){db.getWritableDatabase().delete("events",null,null);}refresh();}).show();
        });}
        @JavascriptInterface public void ignore(long id){runOnUiThread(()->new AlertDialog.Builder(MainActivity.this).setTitle("排除此通知？").setMessage("此通知将不再计入净额，原文保留供核对。").setNegativeButton("取消",null).setPositiveButton("排除",(d,w)->{try(LedgerDb db=new LedgerDb(MainActivity.this)){db.ignore(id);}refresh();}).show());}
    }
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==41&&result==RESULT_OK&&data!=null&&data.getData()!=null){try(LedgerDb db=new LedgerDb(this);java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new java.io.IOException();out.write(db.export().getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"已导出",Toast.LENGTH_SHORT).show();}catch(Exception ex){Toast.makeText(this,"导出失败，请重试",Toast.LENGTH_LONG).show();}}}
    public void onBackPressed(){if(web!=null&&ready)web.evaluateJavascript("window.handleBack && window.handleBack()",value->{if("false".equals(value))finish();});else super.onBackPressed();}
    protected void onDestroy(){ready=false;if(web!=null){web.removeJavascriptInterface("Ledger");web.destroy();web=null;}super.onDestroy();}
}
