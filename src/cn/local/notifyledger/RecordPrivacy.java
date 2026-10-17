package cn.local.notifyledger;

import java.math.BigDecimal;

/** Drop legacy notification text while preserving structured ledger values. */
public final class RecordPrivacy {
    public static String summary(String original,long cents,String state) {
        if(cents==PaymentParser.UNKNOWN || cents==0 || "pending".equals(state))
            return "历史通知（原文已移除）";
        String text=original==null?"":original;
        String kind=cents<0?"退款成功":text.contains("红包")?"红包支出":text.contains("转账")||text.contains("转出")?"转账成功":"付款成功";
        if(text.startsWith("手动记账："))kind="手动记账："+(text.contains("红包")?"红包":text.contains("转账")?"转账":"其他支出");
        return kind+" ¥"+BigDecimal.valueOf(cents).abs().movePointLeft(2).setScale(2).toPlainString();
    }
}
