package cn.local.notifyledger;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private LinearLayout root;
    private UpdateManager updater;
    private final int ink=Color.rgb(24,43,47), muted=Color.rgb(93,111,112);
    private String money(long c){return String.format(Locale.CHINA,"¥%,.2f",c/100.0);}
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(6),0,dp(6));root.addView(t);return t;}
    private void button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);root.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));b.setOnClickListener(v->action.run());}
    private void open(Intent i){try{startActivity(i);}catch(Exception ex){Toast.makeText(this,"此系统未提供入口，请到手机设置中搜索对应选项",Toast.LENGTH_LONG).show();}}
    private boolean granted(){String value=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");ComponentName me=new ComponentName(this,PaymentListener.class);if(value==null)return false;for(String item:value.split(":"))if(me.equals(ComponentName.unflattenFromString(item)))return true;return false;}
    private String date(long time){return new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(time));}
    public void onCreate(Bundle saved){super.onCreate(saved);updater=new UpdateManager(this);}
    public void onResume(){super.onResume();render();if(updater!=null)updater.resume();}
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(244,247,244));
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(16),dp(22),dp(28));scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        text("今日花费",30,ink);text("通知记账 · 验证版 "+updater.version(),13,muted);
        button("检查更新",()->updater.check());
        button("安装已下载的更新",()->updater.install());
        Calendar start=Calendar.getInstance();start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
        Calendar end=(Calendar)start.clone();end.add(Calendar.DAY_OF_MONTH,1);
        long wx=0,ali=0;int pending=0;
        try(LedgerDb db=new LedgerDb(this);Cursor c=db.getReadableDatabase().rawQuery("SELECT source,cents,state FROM events WHERE posted>=? AND posted<?",new String[]{""+start.getTimeInMillis(),""+end.getTimeInMillis()})){
            while(c.moveToNext()){if(c.getString(2).equals("auto")){if(c.getString(0).equals("微信"))wx+=c.getLong(1);else ali+=c.getLong(1);}if(c.getString(2).equals("pending"))pending++;}
        }
        text("今日净额（退款 − 支出）",15,muted);text((wx+ali>0?"−":wx+ali<0?"+":"")+money(Math.abs(wx+ali)),42,ink);
        text("微信净支出  "+money(wx)+"     支付宝净支出  "+money(ali),18,ink);
        text("支出按通知发出日期，退款按收到日期汇总。可能漏记或延迟跨天；转出和发红包计入支出；退款按收到当日抵扣，不关联原订单。",13,muted);
        text(granted()?"● 通知读取已授权":"○ 尚未授权通知读取",17,granted()?Color.rgb(21,121,89):Color.rgb(165,85,30));
        android.content.SharedPreferences p=getSharedPreferences("status",0);
        long seen=p.getLong("lastSeen",0),connected=p.getLong("connectedAt",0);
        text("最近连接："+(connected==0?"尚无记录":date(connected))+"\n微信 / 支付宝通知回调："+p.getLong("seen",0)+" 次（含更新）\n最近收到："+(seen==0?"尚无记录":date(seen)),13,muted);
        button("1. 开启通知读取权限",()->new AlertDialog.Builder(this).setTitle("允许读取付款通知").setMessage("系统通知权限覆盖全部通知。本应用只处理微信和支付宝，保存含支付相关词的通知，可能含商户名或聊天文字。账单仅存在本机；联网仅用于你主动检查、下载 GitHub 更新，不上传账单。你可随时在系统中撤销授权。是否前往设置？").setNegativeButton("暂不",null).setPositiveButton("前往设置",(d,w)->open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))).show());
        button("2. 打开应用设置 / 后台设置",()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        button("刷新记录",this::render);
        text("首次测试",20,ink);
        text("① 在系统中为本应用开启通知读取。若开关被限制，检查应用信息页菜单是否提供“允许受限设置”。\n② 在小米设置中检查本应用的自启动及省电策略，允许后台运行；微信和支付宝也需允许支付相关通知。不同系统版本入口可能不同。\n③ 正常完成一笔小额付款，然后回到这里刷新；分别测试微信、支付宝，之后再试锁屏付款。\n④ 有通知但未识别：看下方待识别内容；没有回调：先检查通知权限和后台设置。",14,muted);
        button("运行识别示例（不写入账本）",()->new AlertDialog.Builder(this).setTitle("规则自检").setMessage("付款成功 ¥12.50 → "+money(PaymentParser.cents("付款成功 ¥12.50"))+"\n退款成功 12.50元 → 当日抵扣12.50元\n微信聊天：12.50元 → 不计入\n真实采集仍需手机付款验证。").setPositiveButton("知道了",null).show());
        text("今日待识别："+pending+" 条",18,ink);text("以下显示最近 100 条支付相关通知（含历史日期）。模糊通知不会自动计入；点击记录可排除误记。",13,muted);
        int count=0;
        try(LedgerDb db=new LedgerDb(this);Cursor c=db.getReadableDatabase().rawQuery("SELECT _id,source,content,posted,cents,state FROM events ORDER BY posted DESC LIMIT 100",null)){
            while(c.moveToNext()){
                count++;long id=c.getLong(0);String state=c.getString(5);String title=c.getString(1)+" · "+date(c.getLong(3))+" · "+(state.equals("auto")?((c.getLong(4)<0?"退款 +":"支出 −")+money(Math.abs(c.getLong(4)))):state.equals("ignored")?"已排除":"待识别 / 不计入");
                TextView row=text(title+"\n"+c.getString(2),14,state.equals("ignored")?muted:ink);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setBackgroundColor(Color.WHITE);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);row.setLayoutParams(lp);
                if(!state.equals("ignored"))row.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("排除此通知？").setMessage("将此条标为已排除；若已计入支出，会同步扣除。原始通知仍保留供核对。").setNegativeButton("取消",null).setPositiveButton("排除",(d,w)->{try(LedgerDb store=new LedgerDb(this)){store.ignore(id);}render();}).show());
            }
        }
        if(count==0)text("还没有支付相关通知。授权后等待下一笔付款。",15,muted);
        button("导出本机记录 JSON",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"付款通知记录.json");try{startActivityForResult(i,41);}catch(Exception ex){Toast.makeText(this,"找不到文件保存程序",Toast.LENGTH_LONG).show();}});
        button("清空全部记录",()->new AlertDialog.Builder(this).setTitle("清空本机记录？").setMessage("将删除所有已识别、待识别和已排除记录，无法恢复。").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{try(LedgerDb db=new LedgerDb(this)){db.getWritableDatabase().delete("events",null,null);}render();}).show());
        text("账单仅保存在此手机，不上传。联网仅用于你主动检查、下载 GitHub 更新。支付相关的聊天可能进入待识别列表；自动识别也可能误判，请用真实账单核对。",12,muted);
    }
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==41&&result==RESULT_OK&&data!=null&&data.getData()!=null){try(LedgerDb db=new LedgerDb(this);java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new java.io.IOException();out.write(db.export().getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"已导出",Toast.LENGTH_SHORT).show();}catch(Exception ex){Toast.makeText(this,"导出失败，请重试",Toast.LENGTH_LONG).show();}}}
}
