package cn.local.notifyledger;

import java.util.*;
import java.util.regex.*;

/** Conservative extraction of completed payments, never arbitrary prices or coupon values. */
public final class ScreenshotParser {
    public static final class Candidate {
        public long cents, time;
        public String source, evidence, note="";
        public boolean hasDate, hasTime, sourceKnown;
        public int index;
    }
    private static final String MONEY="([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9.])";
    public static String source(String input){
        String t=input.replaceAll("\\s","");
        boolean wx=t.matches("(?s).*(微信账单|微信支付账单|微信支付凭证|微信支付|微信零钱|零钱明细|零钱通|微信转账).*")||t.contains("账单")&&t.contains("群收款")||t.contains("全部账单")&&t.contains("统计");
        boolean ali=t.contains("支付宝")||t.contains("余额宝")&&t.matches("(?s).*(交易记录|收支分析|账单).*")||t.contains("收支分析")&&t.contains("本月已省")&&t.contains("自动续费");
        if(t.matches("(?s).*(微信账单|微信支付账单|微信零钱|零钱明细|零钱通).*")&&!t.contains("支付宝账单"))return "微信";
        if(t.contains("支付宝账单")||t.contains("搜索交易记录")&&t.contains("余额宝"))return "支付宝";
        if(wx&&!ali)return "微信";if(ali&&!wx)return "支付宝";return "银行卡";
    }
    private static boolean rowBoundary(String s){return Pattern.compile("(?:^|[^0-9.])[-−–—﹣－一][¥]?"+MONEY+"(?=$|[^0-9.,])").matcher(s).find()||s.matches(".*[0-9]+\\.[0-9]{2}$")||s.matches(".*(你有一笔|收到一笔|付款成功|支付成功).*");}
    public static List<Candidate> parse(String input,long now){
        String text=input.replace('：',':').replace('￥','¥').replace('．','.');
        String[] lines=text.split("\\n");List<Candidate> out=new ArrayList<>();String channel=source(text);
        for(int i=0;i<lines.length;i++){
            String line=lines[i].replaceAll("[ \\t]","");
            if(line.matches(".*(待付款|待支付|支付失败|付款失败|申请退款|退款中|预计退款).*"))continue;
            long amount=PaymentParser.centsForSource("com.eg.android.AlipayGphone",line);
            if(amount==PaymentParser.UNKNOWN){
                Matcher m=Pattern.compile("(?:支付成功|付款成功|实付款|实际付款|实付金额|实付)[：:]?[¥]?"+MONEY).matcher(line);
                if(m.find())amount=PaymentParser.manualExpense(m.group(1).replace(",",""));
                else if(line.matches("(?:支付成功|付款成功|实付款|实付金额|实付)[：:]?" )&&i+1<lines.length){
                    Matcher next=Pattern.compile("^[¥]?"+MONEY+"元?$").matcher(lines[i+1].replaceAll("\\s", ""));
                    if(next.matches())amount=PaymentParser.manualExpense(next.group(1).replace(",",""));
                }
            }
            if(amount==PaymentParser.UNKNOWN && text.matches("(?s).*(账单|交易记录|收支明细|交易明细|消费明细|支出明细).*") && !line.matches(".*(合计|总计|总支出|总收入|余额|累计|本月支出|本月收入|本月结余|优惠|抵扣|立减|折扣).*")){
                // Bill lists show a signed amount per row; incoming transfers are not refunds.
                Matcher signed=Pattern.compile("(?:^|[^0-9.])[-−–—﹣－一]([¥]?)"+MONEY+"元?(?=$|[^0-9.,])").matcher(line);
                if(signed.find())amount=PaymentParser.manualExpense(signed.group(2).replace(",",""));
            }
            if(amount==PaymentParser.UNKNOWN && text.matches("(?s).*(账单|交易记录|收支明细|交易明细).*") && line.contains("退款")){
                Matcher refund=Pattern.compile("退款.*?\\+[¥]?"+MONEY+"元?$").matcher(line);
                if(refund.find()){long value=PaymentParser.manualExpense(refund.group(1).replace(",",""));if(value!=PaymentParser.UNKNOWN)amount=-value;}
            }
            if(amount==PaymentParser.UNKNOWN)continue;
            Candidate c=new Candidate();c.cents=amount;c.index=i;c.evidence=lines[i].trim();
            if(line.matches(".*[-−–—﹣－一][¥]?[0-9.,]+.*")){String merchant=line.replaceFirst("[-−–—﹣－一][¥]?[0-9.,]+.*$", "").trim();if(merchant.isEmpty()&&i>0&&!rowBoundary(lines[i-1])&&!lines[i-1].matches(".*(今天|昨天|昨日|前天|账单|交易记录|\\d{1,2}:\\d{2}).*"))merchant=lines[i-1].trim();if(!merchant.isEmpty())c.note=merchant.substring(0,Math.min(120,merchant.length()));}
            c.source=channel;c.sourceKnown=true;
            if(line.contains("你有一笔")||line.contains("您有一笔")||line.contains("收到一笔"))c.source="支付宝";
            Calendar cal=Calendar.getInstance();cal.setTimeInMillis(now);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
            String datePattern="(?:(20[0-9]{2})[年/.-])?([0-9]{1,2})(?:月|/|-(?=[0-9]{2}(?:\\s|$)))([0-9]{1,2})日?";
            boolean billRow=line.matches(".*[-−–—﹣－一][¥]?[0-9.,]+.*") && text.matches("(?s).*(账单|交易记录|收支明细|交易明细|消费明细|支出明细).*");
            List<String> context=new ArrayList<>();context.add(lines[i]);
            if(billRow){
                for(int j=i+1;j<Math.min(lines.length,i+7);j++){if(rowBoundary(lines[j].replaceAll("\\s","")))break;context.add(lines[j]);}
            }else for(int j:new int[]{i-1,i+1})if(j>=0&&j<lines.length&&!rowBoundary(lines[j].replaceAll("\\s",""))&&(j>0||lines[j].matches(".*(交易|支付|付款|退款|上午|下午|年|月|/).*")))context.add(lines[j]);
            Matcher date=null,time=null;Integer relative=null;
            for(String row:context){
                if(relative==null&&date==null){Matcher rel=Pattern.compile("今天|昨天|昨日|前天").matcher(row);if(rel.find())relative=rel.group().equals("今天")?0:rel.group().equals("前天")?-2:-1;
                    else{Matcher d=Pattern.compile(datePattern).matcher(row);if(d.find())date=d;}}
                if(time==null){Matcher m=Pattern.compile("(?:(上午|下午|晚上|中午|凌晨)\\s*)?([0-9]{1,2}):([0-9]{2})(?::[0-9]{2})?").matcher(row);if(m.find())time=m;}
            }
            // Inherit only explicit standalone date headings, never another transaction's date/time.
            if(relative==null&&date==null)for(int j=i-1;j>=0;j--){if(lines[j].contains(":"))continue;Matcher d=Pattern.compile(datePattern).matcher(lines[j]);if(d.find()){date=d;break;}}
            if(relative!=null){cal.add(Calendar.DAY_OF_MONTH,relative);c.hasDate=true;}
            else if(date!=null){int year=date.group(1)==null?cal.get(Calendar.YEAR):Integer.parseInt(date.group(1));int month=Integer.parseInt(date.group(2)),day=Integer.parseInt(date.group(3));
                try{Calendar test=(Calendar)cal.clone();test.setLenient(false);test.set(year,month-1,day);test.getTimeInMillis();cal=test;c.hasDate=true;}catch(IllegalArgumentException ignored){}
            }
            if(time!=null){int h=Integer.parseInt(time.group(2)),min=Integer.parseInt(time.group(3));String period=time.group(1);
                if(period!=null&&(period.equals("下午")||period.equals("晚上")||period.equals("中午"))&&h<12)h+=12;
                if(period!=null&&(period.equals("上午")||period.equals("凌晨"))&&h==12)h=0;
                if(h<24&&min<60){cal.set(Calendar.HOUR_OF_DAY,h);cal.set(Calendar.MINUTE,min);c.hasTime=true;}
            }
            c.time=cal.getTimeInMillis();out.add(c);
        }
        return out;
    }
}
