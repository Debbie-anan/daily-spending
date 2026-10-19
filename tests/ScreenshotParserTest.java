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
        System.out.println(checks+" screenshot parsing checks passed");
    }
}
