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
    private static final String UI_URL="file:///android_asset/index.html";
    private boolean ready=false;
    private boolean granted(){String value=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");ComponentName me=new ComponentName(this,PaymentListener.class);if(value==null)return false;for(String item:value.split(":"))if(me.equals(ComponentName.unflattenFromString(item)))return true;return false;}
    public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(255,250,241));getWindow().setNavigationBarColor(Color.rgb(255,250,241));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        web=new WebView(this);web.setBackgroundColor(Color.rgb(255,250,241));web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setBlockNetworkLoads(true);web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setAllowFileAccessFromFileURLs(false);web.getSettings().setAllowUniversalAccessFromFileURLs(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.getSettings().setSupportMultipleWindows(false);
        web.addJavascriptInterface(new Bridge(),"Ledger");
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){if(r.getUrl().toString().equals(UI_URL))try{return new WebResourceResponse("text/html","utf-8",getAssets().open("index.html"));}catch(Exception ex){return new WebResourceResponse("text/plain","utf-8",new java.io.ByteArrayInputStream("界面加载失败，请重新打开应用".getBytes(StandardCharsets.UTF_8)));}return new WebResourceResponse("text/plain","utf-8",new java.io.ByteArrayInputStream(new byte[0]));}
            public void onPageFinished(WebView v,String url){ready=true;refresh();}
        });
        android.widget.FrameLayout container=new android.widget.FrameLayout(this);
        container.setBackgroundColor(Color.rgb(255,250,241));
        container.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));
        container.setOnApplyWindowInsetsListener((v,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());left=safe.left;top=safe.top;right=safe.right;bottom=safe.bottom;}
            else{left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){DisplayCutout cutout=insets.getDisplayCutout();left=Math.max(left,cutout.getSafeInsetLeft());top=Math.max(top,cutout.getSafeInsetTop());right=Math.max(right,cutout.getSafeInsetRight());bottom=Math.max(bottom,cutout.getSafeInsetBottom());}}
            // Pad the native container so WebView's measured viewport excludes system UI.
            int breathingRoom=(int)(8*getResources().getDisplayMetrics().density+.5f);
            v.setPadding(left,top+breathingRoom,right,bottom);
            return Build.VERSION.SDK_INT>=30?WindowInsets.CONSUMED:insets.consumeSystemWindowInsets();
        });
        setContentView(container);container.post(()->container.requestApplyInsets());
        web.loadUrl(UI_URL);
    }
    public void onResume(){super.onResume();refresh();}
    private String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "0.9.4";}}
    private void refresh(){if(ready&&web!=null)web.evaluateJavascript("window.refreshLedger && window.refreshLedger()",null);}
    private void open(Intent i){try{startActivity(i);}catch(Exception ex){Toast.makeText(this,"此系统未提供入口，请在手机设置中搜索对应选项",Toast.LENGTH_LONG).show();}}
    public final class Bridge {
        @JavascriptInterface public String readState(){
            try(LedgerDb db=new LedgerDb(MainActivity.this)){
                JSONObject o=new JSONObject(db.export());android.content.SharedPreferences p=getSharedPreferences("status",0);
                o.put("granted",granted());o.put("lastSeen",p.getLong("lastSeen",0));o.put("connectedAt",p.getLong("connectedAt",0));o.put("seen",p.getLong("seen",0));o.put("appVersion",version());o.put("uiVersion",9);o.put("onlineConfigured",false);o.put("offline",true);return o.toString();
            }catch(Exception ex){return "{\"error\":\"无法读取本机账本，请重新打开应用\"}";}
        }
        @JavascriptInterface public void action(String name){runOnUiThread(()->{
            if(name.equals("permission"))new AlertDialog.Builder(MainActivity.this).setTitle("读取付款通知").setMessage("系统授权覆盖所有通知。本应用只识别标题符合微信支付或支付宝支付提示、金额明确的付款及退款通知。微信转账和红包不自动记入；支付宝金额明确的成功转出可自动记入，新记录仅保存金额、渠道、时间和摘要。账单仅保存在手机，不上传。可随时在系统设置撤销授权。").setNegativeButton("暂不",null).setPositiveButton("前往授权",(d,w)->open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))).show();
            else if(name.equals("settings"))open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
            else if(name.equals("manual"))manualEntry();
            else if(name.equals("export")){Toast.makeText(MainActivity.this,"导出文件含账本数据；保存到云盘可能被同步，请选择可信位置",Toast.LENGTH_LONG).show();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"付款通知记录.json");try{startActivityForResult(i,41);}catch(Exception e){Toast.makeText(MainActivity.this,"找不到文件保存程序",Toast.LENGTH_LONG).show();}}
            else if(name.equals("clear"))new AlertDialog.Builder(MainActivity.this).setTitle("清空本机记录？").setMessage("所有已识别、待识别和已排除记录将永久删除，无法恢复。").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{try(LedgerDb db=new LedgerDb(MainActivity.this)){db.getWritableDatabase().delete("events",null,null);}refresh();}).show();
        });}
        @JavascriptInterface public void ignore(long id){runOnUiThread(()->new AlertDialog.Builder(MainActivity.this).setTitle("排除此通知？").setMessage("此通知将不再计入净额，摘要保留供核对。").setNegativeButton("取消",null).setPositiveButton("排除",(d,w)->{try(LedgerDb db=new LedgerDb(MainActivity.this)){db.ignore(id);}refresh();}).show());}
    }
    private void manualEntry(){
        android.widget.LinearLayout form=new android.widget.LinearLayout(this);form.setOrientation(1);int padding=(int)(20*getResources().getDisplayMetrics().density);form.setPadding(padding,0,padding,0);
        android.widget.TextView label=new android.widget.TextView(this);label.setText("金额（元）");form.addView(label);
        android.widget.EditText amount=new android.widget.EditText(this);amount.setHint("例如 8.88");amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);form.addView(amount);
        android.widget.Spinner source=new android.widget.Spinner(this);source.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"微信","支付宝","银行卡"}));form.addView(source);
        android.widget.Spinner category=new android.widget.Spinner(this);category.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"日常消费","转账","红包","其他支出"}));form.addView(category);
        android.widget.TextView noteLabel=new android.widget.TextView(this);noteLabel.setText("消费备注（选填，最多120字）");form.addView(noteLabel);
        android.widget.EditText note=new android.widget.EditText(this);note.setHint("例如：午饭、超市买菜、电费");note.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);note.setMaxLines(3);note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});form.addView(note);
        java.util.Calendar when=java.util.Calendar.getInstance();android.widget.Button date=new android.widget.Button(this);date.setText(new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.CHINA).format(when.getTime()));form.addView(date);
        date.setOnClickListener(v->{DatePickerDialog picker=new DatePickerDialog(this,(view,y,m,d)->{when.set(y,m,d);date.setText(new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.CHINA).format(when.getTime()));},when.get(java.util.Calendar.YEAR),when.get(java.util.Calendar.MONTH),when.get(java.util.Calendar.DAY_OF_MONTH));picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();});
        android.widget.ScrollView formScroll=new android.widget.ScrollView(this);formScroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("手动记一笔支出").setView(formScroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{long cents=PaymentParser.manualExpense(amount.getText().toString().trim());if(cents==PaymentParser.UNKNOWN){amount.setError("请输入大于0的金额，最多两位小数");return;}try(LedgerDb db=new LedgerDb(this)){db.insertManual(source.getSelectedItem().toString(),category.getSelectedItem().toString(),note.getText().toString(),when.getTimeInMillis(),cents);}catch(Exception ex){Toast.makeText(this,"保存失败，请重试；已填写内容保留",Toast.LENGTH_LONG).show();return;}dialog.dismiss();refresh();Toast.makeText(this,"已记入支出",Toast.LENGTH_SHORT).show();}));dialog.show();
    }
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==41&&result==RESULT_OK&&data!=null&&data.getData()!=null){try(LedgerDb db=new LedgerDb(this);java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new java.io.IOException();out.write(db.export().getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"已导出",Toast.LENGTH_SHORT).show();}catch(Exception ex){Toast.makeText(this,"导出失败，请重试",Toast.LENGTH_LONG).show();}}}
    public void onBackPressed(){if(web!=null&&ready)web.evaluateJavascript("window.handleBack && window.handleBack()",value->{if("false".equals(value))finish();});else super.onBackPressed();}
    protected void onDestroy(){ready=false;if(web!=null){web.removeJavascriptInterface("Ledger");web.destroy();web=null;}super.onDestroy();}
}
