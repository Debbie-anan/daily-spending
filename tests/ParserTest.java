import cn.local.notifyledger.PaymentParser;
public class ParserTest {
    private static int count;
    static void check(String s,long expected){long got=PaymentParser.cents(s);if(got!=expected)throw new AssertionError(s+": "+got+" != "+expected);count++;}
    public static void main(String[] args){
        check("微信支付\n付款成功\n￥12.50",1250);
        check("支付宝\n支付成功 20元",2000);
        check("成功付款 1,234.56元",123456);
        check("付款成功 ¥12.50\n付款成功 ¥12.50",1250);
        check("付款成功 0.01元",1);
        check("退款成功 12.50元",-1250);
        check("收款到账 12.50元",PaymentParser.UNKNOWN);
        check("转账成功 20元",PaymentParser.UNKNOWN);
        check("付款失败 12元",PaymentParser.UNKNOWN);
        check("付款成功 12元 余额100元",PaymentParser.UNKNOWN);
        check("付款成功 原价20元 实付18元",PaymentParser.UNKNOWN);
        check("聊天：给我20元",PaymentParser.UNKNOWN);
        check("待付款 30元",PaymentParser.UNKNOWN);
        check("未支付 30元",PaymentParser.UNKNOWN);
        check("支付成功",PaymentParser.UNKNOWN);
        check("付款成功 ￥0.00",PaymentParser.UNKNOWN);
        check("付款成功 12.345元",PaymentParser.UNKNOWN);
        check("付款成功 ￥12.345",PaymentParser.UNKNOWN);
        check("支付成功 999999999999999999999元",PaymentParser.UNKNOWN);
        check("付款成功 优惠2元 实付18元",PaymentParser.UNKNOWN);
        check("退款已到账 20元",-2000);
        check("退款成功 2元",-200);
        check("申请退款 20元",PaymentParser.UNKNOWN);
        check("退款处理中 20元",PaymentParser.UNKNOWN);
        check("退款成功 原价20元 退款5元",PaymentParser.UNKNOWN);
        check("收到转账 20元",PaymentParser.UNKNOWN);
        check("转账已收款 20元",PaymentParser.UNKNOWN);
        check("红包发送成功 8.88元",PaymentParser.UNKNOWN);
        check("你发出了红包 5元",PaymentParser.UNKNOWN);
        check("收到红包 8.88元",PaymentParser.UNKNOWN);
        check("领取了红包 8.88元",PaymentParser.UNKNOWN);
        check("红包 8.88元",PaymentParser.UNKNOWN);
        check("付款成功 转账20元",PaymentParser.UNKNOWN);
        check("红包退款成功 5元",PaymentParser.UNKNOWN);
        if(!PaymentParser.paymentTitle("com.tencent.mm","微信支付"))throw new AssertionError();
        if(PaymentParser.paymentTitle("com.tencent.mm","朋友甲"))throw new AssertionError();
        if(PaymentParser.paymentTitle("other.app","微信支付"))throw new AssertionError();
        if(PaymentParser.paymentTitle("com.tencent.mm","朋友发来微信支付"))throw new AssertionError();
        if(!PaymentParser.paymentTitle("com.eg.android.AlipayGphone","支付助手"))throw new AssertionError();
        if(PaymentParser.paymentTitle("com.eg.android.AlipayGphone","联系人"))throw new AssertionError();
        if(PaymentParser.manualExpense("8.88")!=888 || PaymentParser.manualExpense("0.01")!=1)throw new AssertionError();
        for(String invalid:new String[]{"0","-1","1.234","1e2","","99999999.99"})if(PaymentParser.manualExpense(invalid)!=PaymentParser.UNKNOWN)throw new AssertionError(invalid);
        String ali="com.eg.android.AlipayGphone",wx="com.tencent.mm";
        if(PaymentParser.centsForSource(ali,"转账成功 20元")!=2000)throw new AssertionError();
        if(PaymentParser.centsForSource(ali,"成功转出 0.01元")!=1)throw new AssertionError();
        for(String text:new String[]{"收到转账20元","转账已到账20元","转账成功 对方向你转账20元","转账处理中20元","红包发送成功20元","转账成功 原金额20元 实付18元"})if(PaymentParser.centsForSource(ali,text)!=PaymentParser.UNKNOWN)throw new AssertionError(text);
        if(PaymentParser.centsForSource(wx,"转账成功20元")!=PaymentParser.UNKNOWN)throw new AssertionError();
        long netExpense=3000+PaymentParser.cents("退款成功 35元");
        if(netExpense!=-500)throw new AssertionError("refund net should be +5");
        if(PaymentParser.relevant("你好"))throw new AssertionError();
        System.out.println(count+" parser cases passed");
    }
}
