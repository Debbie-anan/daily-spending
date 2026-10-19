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
    public static List<Candidate> parse(String input,long now){
        String text=input.replace('：',':').replace('￥','¥').replace('．','.');
        String[] lines=text.split("\\n");List<Candidate> out=new ArrayList<>();
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
                Matcher signed=Pattern.compile("(?:^|[^0-9.])[-−]([¥]?)"+MONEY+"元?$").matcher(line);
                if(signed.find())amount=PaymentParser.manualExpense(signed.group(2).replace(",",""));
            }
            if(amount==PaymentParser.UNKNOWN && text.matches("(?s).*(账单|交易记录|收支明细|交易明细).*") && line.contains("退款")){
                Matcher refund=Pattern.compile("退款.*?\\+[¥]?"+MONEY+"元?$").matcher(line);
                if(refund.find()){long value=PaymentParser.manualExpense(refund.group(1).replace(",",""));if(value!=PaymentParser.UNKNOWN)amount=-value;}
            }
            if(amount==PaymentParser.UNKNOWN)continue;
            Candidate c=new Candidate();c.cents=amount;c.index=i;c.evidence=lines[i].trim();
            if(line.matches(".*[-−][¥]?[0-9.,]+元?$")){String merchant=line.replaceFirst("[-−][¥]?[0-9.,]+元?$", "").trim();if(!merchant.isEmpty())c.note=merchant.substring(0,Math.min(120,merchant.length()));}
            String near=String.join("\n",Arrays.copyOfRange(lines,Math.max(0,i-2),Math.min(lines.length,i+3)));
            // Notification wording is specific to Alipay. Otherwise do not infer a bank payment as Alipay.
            if(line.contains("你有一笔")||line.contains("您有一笔")||line.contains("收到一笔")||text.contains("支付宝")&&!text.contains("微信")){c.source="支付宝";c.sourceKnown=true;}
            else if(text.contains("微信")&&!text.contains("支付宝")){c.source="微信";c.sourceKnown=true;}
            else if(text.matches("(?s).*(银行卡|储蓄卡|借记卡|信用卡).*")){c.source="银行卡";c.sourceKnown=true;}
            else {c.source="银行卡";c.sourceKnown=false;}
            Calendar cal=Calendar.getInstance();cal.setTimeInMillis(now);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
            // A bill's date heading applies until the next heading. Never parse decimals as dates.
            String datePattern="(?:(20[0-9]{2})[年/.-])?([0-9]{1,2})(?:月|/|-(?=[0-9]{2}(?:\\s|$)))([0-9]{1,2})日?";
            Matcher date=null;
            for(int j:new int[]{i,i+1}){if(j>=lines.length)continue;Matcher m=Pattern.compile(datePattern).matcher(lines[j]);if(m.find()){date=m;break;}}
            if(date==null)for(int j=i-1;j>=0;j--){Matcher m=Pattern.compile(datePattern).matcher(lines[j]);if(m.find()){date=m;break;}}
            if(date!=null){int year=date.group(1)==null?cal.get(Calendar.YEAR):Integer.parseInt(date.group(1));int month=Integer.parseInt(date.group(2)),day=Integer.parseInt(date.group(3));
                try{Calendar test=(Calendar)cal.clone();test.setLenient(false);test.set(year,month-1,day);test.getTimeInMillis();cal=test;c.hasDate=true;}catch(IllegalArgumentException ignored){}
            }
            // Nearest row first; a bare status-bar clock is never a transaction time.
            Matcher time=null;
            for(int offset:line.matches(".*[-−][¥]?[0-9.,]+元?$")?new int[]{0,1,-1}:new int[]{0,-1,1}){int j=i+offset;if(j<0||j>=lines.length)continue;
                String row=lines[j];if(j==0&&!row.matches(".*(交易|支付|付款|退款|年|月|/).*"))continue;
                if(offset!=0&&row.matches(".*(?:你有一笔|收到一笔|实付|支付成功).*"))continue;
                Matcher m=Pattern.compile("(?:(上午|下午|晚上|中午|凌晨)\\s*)?([0-9]{1,2}):([0-9]{2})(?::[0-9]{2})?").matcher(row);
                if(m.find()){time=m;break;}
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
