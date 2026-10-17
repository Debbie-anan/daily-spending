import cn.local.notifyledger.RecordPrivacy;
import cn.local.notifyledger.PaymentParser;

public class RecordPrivacyTest {
    static void check(String original,long cents,String state,String expected){String got=RecordPrivacy.summary(original,cents,state);if(!got.equals(expected))throw new AssertionError(got);}
    public static void main(String[] args){
        check("张某某：付款成功 12.50元，手机号13812345678",1250,"auto","付款成功 ¥12.50");
        check("商店订单ABC退款成功10元",-1000,"auto","退款成功 ¥10.00");
        check("转账给姓名地址 8元",800,"ignored","转账成功 ¥8.00");
        check("私密聊天正文",PaymentParser.UNKNOWN,"pending","历史通知（原文已移除）");
        check("已排除的私密聊天正文",PaymentParser.UNKNOWN,"ignored","历史通知（原文已移除）");
        check("手动记账：红包 ¥8.88",888,"auto","手动记账：红包 ¥8.88");
        check(null,100,"auto","付款成功 ¥1.00");
        System.out.println("7 legacy privacy redaction cases passed");
    }
}
