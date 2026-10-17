package cn.local.notifyledger;

import java.math.BigDecimal;
import java.util.regex.*;
import java.util.*;

public final class PaymentParser {
    public static final long UNKNOWN = Long.MIN_VALUE;
    public static boolean relevant(String s) {
        return Pattern.compile("支付|付款|消费|扣款|支出|收款|到账|退款|转账|红包|交易").matcher(s).find();
    }
    // Conservative: never infer an expense from an amount alone.
    public static long cents(String s) {
        if (Pattern.compile("失败|待支付|待付款|等待付款|未支付|未付款|取消|关闭|验证码|优惠|立减|余额|额度|还款|账单日|应还|退款申请|申请退款|退款处理中|退款中|待退款|预计到账").matcher(s).find()) return UNKNOWN;
        boolean refund=Pattern.compile("退款成功|退款到账|退款已到账|退款已退回|已退款|成功退款").matcher(s).find();
        if (!refund && Pattern.compile("收款|收钱|到账|收入|收到转账|向你转账|给你转账|收到红包|领取红包|领取了红包|红包领取|收取|退回|退款").matcher(s).find()) return UNKNOWN;
        boolean outgoing=Pattern.compile("支付成功|付款成功|扣款成功|消费成功|成功支付|成功付款|成功扣款|支出|转账成功|转出成功|已转账|红包发送成功|红包已发出|成功发出红包|成功发送红包|发送红包成功|发出红包成功|你发出了红包|你发送了红包").matcher(s).find();
        if (!refund && !outgoing) return UNKNOWN;
        Pattern p=Pattern.compile("[¥￥]\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9.])|(?<![0-9.])([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元");
        Matcher m=p.matcher(s); Set<Long> values=new HashSet<>();
        while(m.find()) {
            try {String n=m.group(1)!=null?m.group(1):m.group(2); long v=new BigDecimal(n.replace(",", "")).movePointRight(2).longValueExact(); if(v>0 && v<=1000000000L) values.add(v); } catch(Exception ignored) {return UNKNOWN;}
        }
        return values.size()==1?(refund?-values.iterator().next():values.iterator().next()):UNKNOWN;
    }
}
