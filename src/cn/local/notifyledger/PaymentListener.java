package cn.local.notifyledger;

import android.app.Notification;
import android.content.*;
import android.os.Bundle;
import android.service.notification.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class PaymentListener extends NotificationListenerService {
    private static volatile PaymentListener connected;
    public static boolean isConnected(){return connected!=null;}
    private void status(String reason){getSharedPreferences("status",0).edit().putString("lastResult",reason).putLong("lastCheck",System.currentTimeMillis()).apply();}
    public void onListenerConnected(){connected=this;getSharedPreferences("status",0).edit().putBoolean("connected",true).putLong("connectedAt",System.currentTimeMillis()).apply();scanActive();}
    public void onListenerDisconnected(){if(connected==this)connected=null;getSharedPreferences("status",0).edit().putBoolean("connected",false).apply();try{requestRebind(new ComponentName(this,PaymentListener.class));}catch(RuntimeException ignored){}}
    public void onDestroy(){if(connected==this)connected=null;getSharedPreferences("status",0).edit().putBoolean("connected",false).apply();super.onDestroy();}
    public static int refreshActive(Context context){PaymentListener listener=connected;if(listener==null){try{requestRebind(new ComponentName(context,PaymentListener.class));}catch(RuntimeException ignored){}return -1;}return listener.scanActive();}
    private int scanActive(){try{int added=0;StatusBarNotification[] notifications=getActiveNotifications();if(notifications==null)return 0;for(StatusBarNotification sbn:notifications)if(process(sbn,false))added++;return added;}catch(SecurityException|IllegalStateException ex){return -1;}}
    public void onNotificationPosted(StatusBarNotification sbn){process(sbn,true);}
    private boolean process(StatusBarNotification sbn,boolean live){
        if(sbn==null)return false;String pkg=sbn.getPackageName();
        if(!pkg.equals("com.tencent.mm")&&!pkg.equals("com.eg.android.AlipayGphone"))return false;
        if(live){SharedPreferences prefs=getSharedPreferences("status",0);prefs.edit().putLong("lastSeen",System.currentTimeMillis()).putLong("seen",prefs.getLong("seen",0)+1).apply();}
        Notification n=sbn.getNotification();if(n==null)return false;if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0){status("分组汇总已跳过，等候单笔通知");return false;}
        Bundle e=n.extras;if(e==null)return false;
        CharSequence title=e.getCharSequence(Notification.EXTRA_TITLE);
        if(!PaymentParser.paymentTitle(pkg,title==null?null:title.toString().trim())){
            title=e.getCharSequence(Notification.EXTRA_TITLE_BIG);
            if(!PaymentParser.paymentTitle(pkg,title==null?null:title.toString().trim())){status("非付款通知标题，未保存内容");return false;}
        }
        LinkedHashSet<String> parts=new LinkedHashSet<>();
        for(String key:new String[]{Notification.EXTRA_TITLE,Notification.EXTRA_TEXT,Notification.EXTRA_BIG_TEXT,Notification.EXTRA_SUB_TEXT}){CharSequence s=e.getCharSequence(key);if(s!=null&&!s.toString().trim().isEmpty())parts.add(s.toString());}
        CharSequence[] lines=e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if(lines!=null)for(CharSequence line:lines)if(line!=null)parts.add(line.toString());
        // Prefer the single transaction text: expanded layouts can include unrelated summaries.
        String text=String.join("\n",parts);long amount=PaymentParser.UNKNOWN;
        java.util.Set<Long> candidates=new java.util.HashSet<>();
        for(String part:parts){long value=PaymentParser.centsForSource(pkg,part);if(value!=PaymentParser.UNKNOWN)candidates.add(value);}
        if(candidates.size()==1)amount=candidates.iterator().next();
        else if(candidates.isEmpty())amount=PaymentParser.centsForSource(pkg,text);
        if(amount==PaymentParser.UNKNOWN){status("金额或付款状态不明确，未入账");return false;}
        String summary=(amount<0?"退款成功":text.matches("(?s).*(转账|转出).*")?"转账成功":"付款成功")+" ¥"+java.math.BigDecimal.valueOf(amount).abs().movePointLeft(2).toPlainString();
        // Both live callbacks and refresh scans use the same identity; refresh does not double-count.
        String raw=sbn.getKey()+"|"+n.when+"|"+amount;String fingerprint;
        try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));StringBuilder h=new StringBuilder();for(byte b:hash)h.append(String.format(Locale.ROOT,"%02x",b&255));fingerprint=h.toString();}catch(Exception ex){return false;}
        try(LedgerDb db=new LedgerDb(this)){boolean added=db.insert(fingerprint,pkg.equals("com.tencent.mm")?"微信":"支付宝",summary,sbn.getPostTime(),amount);status(added?"已处理付款/退款（疑似截图重复的待核对）":"已存在，未重复记录");return added;}
        catch(RuntimeException ex){status("账本写入失败，请下拉重试");return false;}
    }
}
