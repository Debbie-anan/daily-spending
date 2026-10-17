package cn.local.notifyledger;

import java.math.BigDecimal;
import java.util.regex.*;
import java.util.*;

public final class PaymentParser {
    public static final long UNKNOWN = Long.MIN_VALUE;
    public static boolean relevant(String s) {
        return !Pattern.compile("转账|转出|红包").matcher(s).find() && Pattern.compile("支付|付款|消费|扣款|退款").matcher(s).find();
    }
    public static long centsForSource(String pkg,String text) {
        if(pkg.equals("com.eg.android.AlipayGphone")) {
            text=text.replaceAll("(?:你|您)有一笔\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*元)的?支出","付款成功 $1");
            text=text.replaceAll("(?:你|您)收到一笔\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*元)退款","退款成功 $1");
        }
        if(pkg.equals("com.eg.android.AlipayGphone") && Pattern.compile("转账|转出").matcher(text).find()) {
            if(Pattern.compile("红包|收款|收到|到账|收入|向你|给你|退回|退款|收取|失败|等待|待确认|处理中|待收款").matcher(text).find())return UNKNOWN;
            if(!Pattern.compile("转账成功|转出成功|成功转账|成功转出").matcher(text).find())return UNKNOWN;
            return cents(text.replaceAll("转账成功|转出成功|成功转账|成功转出","付款成功").replaceAll("转账|转出",""));
        }
        return cents(text);
    }
    public static boolean paymentTitle(String pkg,String title) {
        if(title==null)return false;
        title=title.trim();
        if(pkg.equals("com.tencent.mm"))return title.matches("微信支付|微信支付凭证|支付凭证");
        if(pkg.equals("com.eg.android.AlipayGphone"))return title.matches("支付宝|支付宝支付助手|支付助手|付款成功|支付成功|退款成功|退款提醒|交易提醒|消费提醒|转账成功|转出成功");
        return false;
    }
    public static long manualExpense(String input) {
        if(input==null || !input.matches("[0-9]{1,8}(\\.[0-9]{1,2})?"))return UNKNOWN;
        try {long value=new BigDecimal(input).movePointRight(2).longValueExact();return value>0 && value<=1000000000L?value:UNKNOWN;}catch(Exception ex){return UNKNOWN;}
    }
    // Conservative: never infer an expense from an amount alone.
    public static long cents(String s) {
        if (Pattern.compile("转账|转出|红包").matcher(s).find()) return UNKNOWN;
        if (Pattern.compile("失败|待支付|待付款|等待付款|未支付|未付款|取消|关闭|验证码|优惠|立减|余额|额度|还款|账单日|应还|退款申请|申请退款|退款处理中|退款中|待退款|预计到账").matcher(s).find()) return UNKNOWN;
        boolean refund=Pattern.compile("退款成功|退款到账|退款已到账|退款已退回|已退款|成功退款").matcher(s).find();
        if (!refund && Pattern.compile("收款|收钱|到账|收入|收到转账|向你转账|给你转账|收到红包|领取红包|领取了红包|红包领取|收取|退回|退款").matcher(s).find()) return UNKNOWN;
        boolean outgoing=Pattern.compile("支付成功|付款成功|扣款成功|消费成功|成功支付|成功付款|成功扣款").matcher(s).find();
        if (!refund && !outgoing) return UNKNOWN;
        Pattern p=Pattern.compile("[¥￥]\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9.])|(?<![0-9.])([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元");
        Matcher m=p.matcher(s); Set<Long> values=new HashSet<>();
        while(m.find()) {
            try {String n=m.group(1)!=null?m.group(1):m.group(2); long v=new BigDecimal(n.replace(",", "")).movePointRight(2).longValueExact(); if(v>0 && v<=1000000000L) values.add(v); } catch(Exception ignored) {return UNKNOWN;}
        }
        return values.size()==1?(refund?-values.iterator().next():values.iterator().next()):UNKNOWN;
    }
}
