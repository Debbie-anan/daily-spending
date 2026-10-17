package cn.local.notifyledger;

import android.app.Notification;
import android.content.*;
import android.os.Bundle;
import android.service.notification.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class PaymentListener extends NotificationListenerService {
    public void onListenerConnected(){getSharedPreferences("status",0).edit().putBoolean("connected",true).putLong("connectedAt",System.currentTimeMillis()).apply();}
    public void onListenerDisconnected(){getSharedPreferences("status",0).edit().putBoolean("connected",false).apply();requestRebind(new ComponentName(this,PaymentListener.class));}
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg=sbn.getPackageName();
        if(!pkg.equals("com.tencent.mm")&&!pkg.equals("com.eg.android.AlipayGphone"))return;
        SharedPreferences prefs=getSharedPreferences("status",0);
        prefs.edit().putLong("lastSeen",System.currentTimeMillis()).putLong("seen",prefs.getLong("seen",0)+1).apply();
        Notification n=sbn.getNotification();
        if((n.flags & Notification.FLAG_GROUP_SUMMARY)!=0)return;
        Bundle e=n.extras; if(e==null)return;
        CharSequence title=e.getCharSequence(Notification.EXTRA_TITLE);
        if(!PaymentParser.paymentTitle(pkg,title==null?null:title.toString()))return;
        LinkedHashSet<String> parts=new LinkedHashSet<>();
        for(String key:new String[]{Notification.EXTRA_TITLE,Notification.EXTRA_TEXT,Notification.EXTRA_BIG_TEXT,Notification.EXTRA_SUB_TEXT}) {CharSequence s=e.getCharSequence(key);if(s!=null&&!s.toString().trim().isEmpty())parts.add(s.toString());}
        CharSequence[] lines=e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if(lines!=null)for(CharSequence line:lines)if(line!=null)parts.add(line.toString());
        String text=String.join("\n",parts);if(!PaymentParser.relevant(text)&&!(pkg.equals("com.eg.android.AlipayGphone")&&text.matches("(?s).*(转账|转出).*")))return;
        long amount=PaymentParser.centsForSource(pkg,text);if(amount==PaymentParser.UNKNOWN)return;
        String summary=(amount<0?"退款成功":text.matches("(?s).*(转账|转出).*")?"转账成功":"付款成功")+" ¥"+java.math.BigDecimal.valueOf(amount).abs().movePointLeft(2).toPlainString();
        // Updates of a notification with identical content and original time are one observation.
        String raw=sbn.getKey()+"|"+n.when+"|"+amount;
        String fingerprint;
        try {byte[] hash=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));StringBuilder h=new StringBuilder();for(byte b:hash)h.append(String.format(Locale.ROOT,"%02x",b&255));fingerprint=h.toString();}catch(Exception ex){return;}
        try(LedgerDb db=new LedgerDb(this)) {db.insert(fingerprint,pkg.equals("com.tencent.mm")?"微信":"支付宝",summary,sbn.getPostTime(),amount);}
    }
}
