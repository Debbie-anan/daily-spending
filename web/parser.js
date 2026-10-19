/* Local bill parser. Geometry preserves the connection between names, amounts and dates. */
(function(root){
const source=t=>/支付宝|余额宝|搜索交易记录|收支分析/.test(t)?'支付宝':/微信|零钱|账单统计|零钱通|微信支付/.test(t)?'微信':'银行卡';
function rows(data){const lines=(data.blocks||[]).flatMap(b=>(b.paragraphs||[]).flatMap(p=>p.lines||[]));let out=[];for(const l of lines.sort((a,b)=>a.bbox.y0-b.bbox.y0)){const y=(l.bbox.y0+l.bbox.y1)/2;let row=out.find(r=>Math.abs(r.y-y)<Math.min(r.h,l.bbox.y1-l.bbox.y0)*.55);if(!row)out.push(row={y,h:l.bbox.y1-l.bbox.y0,parts:[]});row.parts.push(l)}return out.sort((a,b)=>a.y-b.y).map(r=>({text:r.parts.sort((a,b)=>a.bbox.x0-b.bbox.x0).map(l=>l.text.trim()).join(' '),y:r.y,h:r.h}));}
function date(text,base){let anchor=new Date(base+'T12:00:00'),d=new Date(anchor),known=false,explicitYear=false,m;
 if(/前天/.test(text)){d.setDate(d.getDate()-2);known=true}else if(/昨天|昨日/.test(text)){d.setDate(d.getDate()-1);known=true}else if(/今天|今日/.test(text)){known=true}
 else if(m=text.match(/(?:^|[^\d])(?:(20\d{2})[-/.年]\s*)?(\d{1,2})[-/月]\s*(\d{1,2})(?:日)?(?!\d)/)){
 const yy=m[1]?+m[1]:anchor.getFullYear(),mo=+m[2],da=+m[3];explicitYear=!!m[1];d=new Date(yy,mo-1,da,12);known=mo>=1&&mo<=12&&da>=1&&da<=31&&d.getMonth()===mo-1&&d.getDate()===da;
 if(known&&!explicitYear&&d>anchor)d.setFullYear(d.getFullYear()-1);
 }
 let tm=text.match(/(\d{1,2})[:：](\d{2})/),hasTime=!!tm&&+tm[1]<24&&+tm[2]<60;
 d.setHours(hasTime?+tm[1]:12,hasTime?+tm[2]:0,0,0);return {posted:d.getTime(),known,hasTime};}
function baseDate(rs,filename,fallback){
 // Only an explicit capture date is evidence; a file's modification time may be its download time.
 const m=String(filename).match(/(?:Screenshot|截屏|截图)[_-]?(20\d{2})[-_]?([01]\d)[-_]?([0-3]\d)/i);
 if(m){const text=m[1]+'-'+m[2]+'-'+m[3],d=date(text,fallback);if(d.known)return text;}
 const head=rs.slice(0,7).map(r=>r.text).join(' '),h=head.match(/(?:(20\d{2})年)?(\d{1,2})月(\d{1,2})日\s*(?:周|星期)/);
 if(h){const d=date((h[1]?h[1]+'-':'')+h[2]+'-'+h[3],fallback);if(d.known){const v=new Date(d.posted);return v.getFullYear()+'-'+String(v.getMonth()+1).padStart(2,'0')+'-'+String(v.getDate()).padStart(2,'0');}}
 return fallback;
}
function parse(input,base){let rs=Array.isArray(input)?input:String(input).split('\n').map(text=>({text})),all=rs.map(r=>r.text).join('\n'),channel=source(all),out=[];let section='';for(let i=0;i<rs.length;i++){if(/^(今天|今日|昨天|昨日|前天|(?:20\d{2}[-/年])?\d{1,2}[-/月]\d{1,2}日?)$/.test(rs[i].text.trim()))section=rs[i].text;let t=rs[i].text.replace(/[−–—]/g,'-'),m=t.match(/[-+]\s*(\d{1,7}[.,]\d{2})(?!\d)/);let refund=/退款/.test(t)&&!/退款.*订单.*筛选/.test(t);if(!m&&refund)m=t.match(/(\d{1,7}\.\d{2})\s*$/);if(!m||(!refund&&m[0].startsWith('+')))continue;let before=rs.slice(Math.max(0,i-1),i+1).map(r=>r.text).join(' ');if(/本月|支出[¥羊￥]|收入[¥羊￥]|收益发放/.test(t))continue;let end=i+1;while(end<rs.length&&end<i+6&&!/[-+]\s*\d+[.,]\d{2}/.test(rs[end].text)){end++;if(/\d[:：]\d{2}/.test(rs[end-1].text))break;}let tail=rs.slice(i,end).map(r=>r.text).join(' ');let dt=date(tail,base);if(!dt.known&&section)dt=date(section+' '+tail,base);let cents=Math.round(Number(m[1].replace(',','.'))*100)*(refund||m[0].startsWith('+')?-1:1);let note=t.replace(m[0],'').replace(/\s+/g,' ').trim();if(!note&&i)note=rs[i-1].text;out.push({source:channel,cents,note:note|| (refund?'退款':'消费'),...dt,evidence:tail,dateRow:rs.slice(i,end).find(r=>/\d[:：]\d{2}/.test(r.text))});}return out;}
root.BillParser={source,rows,date,baseDate,parse};
})(typeof window==='undefined'?globalThis:window);
