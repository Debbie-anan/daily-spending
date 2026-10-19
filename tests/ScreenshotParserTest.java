import cn.local.notifyledger.*;
import java.util.*;
public class ScreenshotParserTest {
    static long now;static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static List<ScreenshotParser.Candidate> parse(String text){return ScreenshotParser.parse(text,now);}
    public static void main(String[] args){
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));Calendar cal=Calendar.getInstance();cal.set(2026,9,19,21,20,0);now=cal.getTimeInMillis();
        String notification="7:16\n10月19日 周一\n你的关注 下午7:15\n某用户的新动态\n交易提醒 下午7:15\n你有一笔18.00元的支出，点此查看详情。";
        List<ScreenshotParser.Candidate> a=parse(notification);check(a.size()==1,"notification count");check(a.get(0).cents==1800&&a.get(0).source.equals("支付宝"),"18 Alipay");cal.setTimeInMillis(a.get(0).time);check(cal.get(Calendar.HOUR_OF_DAY)==19&&cal.get(Calendar.MINUTE)==15,"PM 7:15 must be 19:15");check(a.get(0).hasDate&&a.get(0).hasTime,"notification date/time");
        a=parse("支付宝账单\n2026年10月19日\n超市 -18.00\n19:15\n咖啡店 -12.50\n18:20\n本月支出 -300.00\n余额 -100.00");check(a.size()==2,"multiple bill rows, exclude totals");check(a.get(0).cents==1800&&a.get(1).cents==1250,"multiple amounts");check(a.get(0).note.equals("超市"),"merchant note");cal.setTimeInMillis(a.get(1).time);check(cal.get(Calendar.HOUR_OF_DAY)==18&&cal.get(Calendar.MINUTE)==20,"do not inherit previous bill time");
        a=parse("微信账单\n早餐 -3.00\n2026/10/18 08:30\n午餐 -25.00\n2026/10/19 12:10");check(a.size()==2,"bill dates below row");cal.setTimeInMillis(a.get(0).time);check(cal.get(Calendar.DAY_OF_MONTH)==18,"row-specific date");cal.setTimeInMillis(a.get(1).time);check(cal.get(Calendar.DAY_OF_MONTH)==19,"second row date");
        a=parse("10月19日\n交易提醒 下午5:00\n你有一笔15.90元的支出，点此查看详情。\n退款提醒 下午4:58\n你收到一笔13.55元退款，点此查看账单详情！\n你有5克能量刚成熟");check(a.size()==2&&a.get(0).cents==1590&&a.get(1).cents==-1355,"multiple notifications and refund");
        a=parse("4:50\n惊喜神券8元\n支付成功 ¥44.40\n招商银行储蓄卡\n本单使用借记卡支付成功\n20元奖励\n预计到账 20元");check(a.size()==1&&a.get(0).cents==4440,"checkout ignores coupons");check(!a.get(0).hasTime&&!a.get(0).hasDate,"status clock is not payment time");
        a=parse("支付宝账单\n10月19日\n奶茶 -18.00\n19:15\n晚饭 -18.00\n19:40");check(a.size()==2,"do not silently merge equal real transactions");
        a=parse("支付宝账单\n10月19日\n商户退款 +13.55\n18:00\n收到转账 +20.00");check(a.size()==1&&a.get(0).cents==-1355,"bill refund excludes incoming transfers");
        check(parse("待付款 25元\n支付失败 25元\n申请退款 10元\n本月支出 -400.00").isEmpty(),"failures and aggregates");
        check(parse("今日净额 -34.36\n微信 -12.90\n银行卡 -21.46").isEmpty(),"own dashboard isn't a bill list");
        check(parse("支付成功 ¥12.345").isEmpty(),"don't truncate fractional cent");
        check(PaymentParser.centsForSource("com.eg.android.AlipayGphone","交易提醒\n你有一笔18.00元的支出，点此查看详情。") ==1800,"live 18 regression");
        String alipay="9:45\n搜索交易记录 搜索\n全部 支出 转账 退款 订单 筛选\n10月\n支出 收入\n¥1,137.98 ¥0.00\n新增了1笔自动续费\n本月已省14.28元 收支分析\n示例超市 -29.07\n日用百货\n今天19:40\n示例蛋糕店 -18.00\n餐饮美食\n今天19:15\n余额宝-收益发放 0.14\n投资理财\n今天05:04\n示例服装订单 -71.90\n运动户外 等待确认收货\n昨天22:26\n示例护肤订单 -198.42 等待确认收货\n美容美发\n昨天11:56\n余额宝-收益发放 0.14";
        a=parse(alipay);check(a.size()==4,"reported screenshot has four expenses");long[] sums={2907,1800,7190,19842};int[] days={19,19,18,18},hours={19,19,22,11},minutes={40,15,26,56};
        for(int i=0;i<4;i++){ScreenshotParser.Candidate c=a.get(i);check(c.cents==sums[i]&&c.source.equals("支付宝")&&c.sourceKnown,"Alipay amount/channel "+i);cal.setTimeInMillis(c.time);check(cal.get(Calendar.DAY_OF_MONTH)==days[i]&&cal.get(Calendar.HOUR_OF_DAY)==hours[i]&&cal.get(Calendar.MINUTE)==minutes[i]&&c.hasDate&&c.hasTime,"multiline relative datetime "+i);}
        a=parse(alipay.replace("-71.90","一71.90").replace("-198.42","−198.42"));check(a.size()==4,"OCR dash variants must retain yesterday expenses");
        a=parse("微信账单\n全部账单 统计 常见问题\n便利店 -25.00\n商户消费\n昨天 13:20");check(a.size()==1&&a.get(0).source.equals("微信"),"WeChat channel");cal.setTimeInMillis(a.get(0).time);check(cal.get(Calendar.DAY_OF_MONTH)==18,"WeChat yesterday");
        check(ScreenshotParser.source("账单 全部账单 统计 常见问题 商户消费").equals("微信"),"WeChat titleless bill features");
        check(ScreenshotParser.source("账单 群收款 全部交易类型").equals("微信"),"WeChat group collection filter");
        check(ScreenshotParser.source("微信账单 商户支付宝服务").equals("微信"),"merchant name must not override page channel");
        check(ScreenshotParser.source("信用卡账单 招商银行").equals("银行卡"),"bank source fallback");
        check(ScreenshotParser.source("支付成功 实付25元 美团").equals("银行卡"),"unknown source fallback");
        a=parse("账单\n商户 -25.00\n消费\n2026-10-18 13:20");cal.setTimeInMillis(a.get(0).time);check(a.get(0).source.equals("银行卡")&&a.get(0).sourceKnown&&cal.get(Calendar.DAY_OF_MONTH)==18,"bank default and ISO date not a row boundary");
        Calendar crossing=Calendar.getInstance();crossing.set(2026,0,1,20,0,0);a=ScreenshotParser.parse("支付宝账单\n商户-12.00\n等待确认收货\n昨天10:00",crossing.getTimeInMillis());cal.setTimeInMillis(a.get(0).time);check(cal.get(Calendar.YEAR)==2025&&cal.get(Calendar.MONTH)==11&&cal.get(Calendar.DAY_OF_MONTH)==31,"yesterday year boundary");
        a=parse("搜索交易记录\n支出 收入 收支分析\n示例服饰订单-71.90\n运动户外 等待确认收货\n昨天22:26\n示例护肤订单-198.42\n美容美发 等待确认收货\n昨天11:56\n余额宝-收益发放0.14\n投资理财\n昨天05:14\n示例定金-60.00\n美容美发 自动扣款成功\n10-17 21:19\n【0.01锁权礼】-0.01\n其他\n10-17 21:17\n退款-示例服饰15.90\n退款\n10-17 17:01");
        check(a.size()==5,"latest screenshot four expenses plus unsigned refund");long expense=0,refund=0;for(ScreenshotParser.Candidate c:a){if(c.cents>0)expense+=c.cents;else refund-=c.cents;check(c.source.equals("支付宝")&&c.hasDate&&c.hasTime,"latest screenshot metadata");}check(expense==33033&&refund==1590,"latest screenshot totals");
        check(parse("支付宝账单\n退款失败15.90\n待退款12.00\n退款处理中8.00").isEmpty(),"don't count incomplete refunds");
        a=parse("Q搜索交易记录\r\n全部 支出 转账 退款 订单\r\n收支分祈\r\n示例服饰.. -71.90\r\n运动戶外 等待确认收货\r\n昨天22:26\r\n示例护肤.. -198.42\r\n美容美发 等侍确认收货\r\n昨天11:56\r\n余额颜宝-收益发放0.14\r\n昨天05:14\r\n示例定金.. -60.00\r\n美容美发 自动扣款成功\r\n10-17 21:19\r\n【0.01锁权礼】-0.01\r\n其他\r\n10-17 21:17\r\n退款-示例...15.90\r\n退款\r\n10-17 17:01");
        check(a.size()==5,"actual OCR ellipsis must not swallow negative amounts");long[] expectedOCR={7190,19842,6000,1,-1590};int[] expectedHours={22,11,21,21,17},expectedMinutes={26,56,19,17,1};
        for(int i=0;i<5;i++){ScreenshotParser.Candidate c=a.get(i);cal.setTimeInMillis(c.time);check(c.cents==expectedOCR[i]&&c.source.equals("支付宝"),"real OCR amount/source "+i);check(c.hasDate&&c.hasTime&&cal.get(Calendar.HOUR_OF_DAY)==expectedHours[i]&&cal.get(Calendar.MINUTE)==expectedMinutes[i],"real OCR date/time "+i);}
        check(parse("支付宝账单\n退款提醒 下午4:58\n退款处理中 15.90").isEmpty(),"notification clocks are not refund amounts");
        check(ScreenshotParser.source("Q搜素交易记录 收支分祈 余额額宝收益发放").equals("支付宝"),"OCR source alias");
        a=parse("支付宝账单\n示例..-18.00\nPFÆ22:26");check(a.size()==1&&!a.get(0).hasDate&&a.get(0).hasTime,"uncertain relative dates must remain flagged");
        System.out.println(checks+" screenshot parsing checks passed");
    }
}
