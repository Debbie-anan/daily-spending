package cn.local.notifyledger;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.*;
import java.io.*;
import java.security.MessageDigest;

/** Local-only, user-selected images. No screenshot or OCR transcript is persisted. */
public final class ScreenshotImport {
    private final Activity activity;
    private final Runnable changed;
    private final List<Item> items=new ArrayList<>();
    private boolean stopped;
    private AlertDialog progress,review;
    private TextRecognizer recognizer;
    private int failed,empty;
    private static final class Item {
        ScreenshotParser.Candidate value;String fingerprint;CheckBox check;TextView status;
        boolean existing, batchDuplicate;
    }
    public ScreenshotImport(Activity a,Runnable changed){activity=a;this.changed=changed;}
    private boolean alive(){return !stopped&&!activity.isFinishing()&&!activity.isDestroyed();}
    public void close(){stopped=true;if(progress!=null)progress.dismiss();if(review!=null)review.dismiss();if(recognizer!=null)recognizer.close();}
    public void start(List<Uri> images){
        progress=new AlertDialog.Builder(activity).setTitle("本机识别截图")
            .setMessage("正在识别多笔消费，图片不会上传。识别完成后请逐笔核对。")
            .setNegativeButton("取消",(d,w)->close()).setCancelable(false).show();
        recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        next(images,0);
    }
    private void next(List<Uri> images,int index){
        if(!alive())return;
        if(index>=images.size()){progress.dismiss();recognizer.close();recognizer=null;showReview();return;}
        progress.setMessage("正在本机识别第 "+(index+1)+" / "+images.size()+" 张截图…");
        new Thread(()->{
            try{
                byte[] bytes;
                try(InputStream in=activity.getContentResolver().openInputStream(images.get(index));ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    if(in==null)throw new IOException();byte[] buffer=new byte[8192];int n;
                    while((n=in.read(buffer))!=-1){if(out.size()+n>25*1024*1024)throw new IOException("too large");out.write(buffer,0,n);}bytes=out.toByteArray();
                }
                StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(Locale.ROOT,"%02x",b&255));
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(options.outWidth<1||options.outHeight<1)throw new IOException();
                options.inSampleSize=1;while((long)(options.outWidth/options.inSampleSize)*(options.outHeight/options.inSampleSize)>12000000L)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)throw new IOException();
                int degrees=0;try{android.media.ExifInterface exif=new android.media.ExifInterface(new ByteArrayInputStream(bytes));int rotation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);if(rotation==6)degrees=90;else if(rotation==3)degrees=180;else if(rotation==8)degrees=270;}catch(IOException ignored){}
                final Bitmap image=bitmap;final int rotation=degrees;final String imageHash=hash.toString();
                activity.runOnUiThread(()->{
                    if(!alive()){image.recycle();return;}
                    recognizer.process(InputImage.fromBitmap(image,rotation)).addOnSuccessListener(result->{
                        if(!alive())return;
                        List<Text.Line> lines=new ArrayList<>();for(Text.TextBlock block:result.getTextBlocks())lines.addAll(block.getLines());
                        lines.removeIf(line->line.getBoundingBox()==null);
                        lines.sort((a,b)->{Rect x=a.getBoundingBox(),y=b.getBoundingBox();int dy=Integer.compare(x.top,y.top);return dy==0?Integer.compare(x.left,y.left):dy;});
                        // Group lines on a shared row, then order left-to-right (stable, transitive sorting).
                        List<Text.Line> ordered=new ArrayList<>();for(int at=0;at<lines.size();){int end=at+1;Rect anchor=lines.get(at).getBoundingBox();while(end<lines.size()){Rect rect=lines.get(end).getBoundingBox();if(Math.abs(rect.centerY()-anchor.centerY())>=Math.min(rect.height(),anchor.height())/2)break;end++;}List<Text.Line> row=new ArrayList<>(lines.subList(at,end));row.sort((a,b)->Integer.compare(a.getBoundingBox().left,b.getBoundingBox().left));ordered.addAll(row);at=end;}lines=ordered;
                        StringBuilder text=new StringBuilder();Rect last=null;
                        for(Text.Line line:lines){Rect rect=line.getBoundingBox();if(text.length()>0)text.append(last!=null&&rect!=null&&Math.abs(last.centerY()-rect.centerY())<Math.min(last.height(),rect.height())/2?' ':'\n');text.append(line.getText());last=rect;}
                        List<ScreenshotParser.Candidate> found=ScreenshotParser.parse(text.toString(),System.currentTimeMillis());
                        if(found.isEmpty())empty++;
                        for(ScreenshotParser.Candidate c:found){Item item=new Item();item.value=c;item.fingerprint="screenshot-"+imageHash+"-"+c.index;items.add(item);}
                    }).addOnFailureListener(error->{if(alive())failed++;}).addOnCompleteListener(task->{image.recycle();if(alive())next(images,index+1);});
                });
            }catch(Exception|OutOfMemoryError ex){activity.runOnUiThread(()->{if(alive()){failed++;next(images,index+1);}});}
        },"local-screenshot-decode").start();
    }
    private String money(long cents){return java.math.BigDecimal.valueOf(cents).abs().movePointLeft(2).toPlainString();}
    private String time(long ms){return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(ms));}
    private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(1);int p=(int)(18*activity.getResources().getDisplayMetrics().density);v.setPadding(p,p,p,p);return v;}
    private void showReview(){
        if(items.isEmpty()){new AlertDialog.Builder(activity).setTitle("未识别到明确消费")
            .setMessage("请选含账单列表、支出金额或支付成功信息的清晰截图。优惠券、余额和汇总金额不会自动记入。可裁剪无关内容后重试。"+(failed>0?"\n有 "+failed+" 张图片无法识别。":""))
            .setPositiveButton("知道了",null).show();return;}
        LinearLayout list=column();TextView intro=new TextView(activity);intro.setText("识别到 "+items.size()+" 笔候选。勾选后批量入账；点“修改”核对金额、渠道、日期、时间和备注。疑似重复默认不选。\n未显示年份按今年；缺失日期/时间按当前值预填，请修改。"+(failed+empty>0?"\n另有 "+failed+" 张读取失败，"+empty+" 张未识别到消费。":""));list.addView(intro);
        Set<String> batch=new HashSet<>();
        for(Item item:items){
            String day=new java.text.SimpleDateFormat("yyyyMMdd",Locale.CHINA).format(new Date(item.value.time));item.batchDuplicate=!batch.add(day+"|"+item.value.source+"|"+item.value.cents);
            LinearLayout card=column();card.setBackgroundColor(Color.rgb(255,250,241));
            item.check=new CheckBox(activity);item.status=new TextView(activity);Button edit=new Button(activity);edit.setText("修改金额、日期或备注");
            card.addView(item.check);card.addView(item.status);card.addView(edit);list.addView(card);
            update(item,true);edit.setEnabled(!item.existing);edit.setOnClickListener(v->edit(item));
        }
        CheckBox verified=new CheckBox(activity);verified.setText("我已核对所选记录的金额、日期、时间和渠道");list.addView(verified);
        ScrollView scroll=new ScrollView(activity);scroll.addView(list);
        review=new AlertDialog.Builder(activity).setTitle("截图消费 · 批量确认").setView(scroll).setNegativeButton("取消",null).setPositiveButton("入账所选",null).create();
        review.setOnShowListener(d->review.getButton(-1).setOnClickListener(v->{
            if(!verified.isChecked()){Toast.makeText(activity,"请先核对并勾选确认",Toast.LENGTH_LONG).show();return;}
            List<Item> selected=new ArrayList<>();int duplicates=0;Set<String> inBatch=new HashSet<>();
            try(LedgerDb db=new LedgerDb(activity)){
                for(Item item:items)if(item.check.isChecked()&&!item.existing){
                    if(item.value.time>System.currentTimeMillis()+300000){Toast.makeText(activity,"有记录时间晚于当前时间，请修改",Toast.LENGTH_LONG).show();return;}
                    selected.add(item);String day=new java.text.SimpleDateFormat("yyyyMMdd",Locale.CHINA).format(new Date(item.value.time));
                    if(!db.duplicateDescription(item.value.source,item.value.cents,item.value.time).isEmpty()||!inBatch.add(day+"|"+item.value.source+"|"+item.value.cents))duplicates++;
                }
            }
            if(selected.isEmpty()){Toast.makeText(activity,"请先选择要入账的记录",Toast.LENGTH_SHORT).show();return;}
            if(duplicates>0)new AlertDialog.Builder(activity).setTitle("所选含 "+duplicates+" 笔疑似重复")
                .setMessage("账本或本批截图中有同一天、同渠道、同金额记录。可能是同一笔，也可能是两笔真实消费。确认它们是不同交易才继续。")
                .setNegativeButton("返回核对",null).setPositiveButton("确认是不同交易，入账",(d2,w)->save(selected)).show();
            else save(selected);
        }));review.show();
    }
    private void update(Item item,boolean initial){
        ScreenshotParser.Candidate c=item.value;String duplicate;
        try(LedgerDb db=new LedgerDb(activity)){item.existing=db.hasFingerprint(item.fingerprint);duplicate=db.duplicateDescription(c.source,c.cents,c.time);}
        item.check.setText(c.source+" · "+(c.cents<0?"退款 +":"支出 -")+money(c.cents)+" 元\n"+time(c.time));
        item.check.setEnabled(!item.existing);
        item.status.setText(item.existing?"这张截图的此条记录已经入账":(item.batchDuplicate?"本批有同日同渠道同金额的其他候选，默认不选。\n":"")+(!duplicate.isEmpty()?"疑似重复，默认不选：\n"+duplicate+"\n":"")+
            (!c.sourceKnown?"⚠ 渠道未确定，请修改\n":"")+(!c.hasDate?"⚠ 未识别日期，请核对\n":"")+(!c.hasTime?"⚠ 未识别交易时间，请核对\n":"")+(c.note.isEmpty()?"":"备注："+c.note+"\n")+"识别依据："+c.evidence);
        if(initial)item.check.setChecked(!item.existing&&!item.batchDuplicate&&duplicate.isEmpty());
    }
    private void save(List<Item> selected){
        int added=0;
        try(LedgerDb db=new LedgerDb(activity)){
            android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
            try{for(Item item:selected)if(db.insertScreenshot(item.fingerprint,item.value.source,item.value.cents,item.value.time,item.value.note))added++;sql.setTransactionSuccessful();}finally{sql.endTransaction();}
        }catch(Exception ex){Toast.makeText(activity,"保存失败，未批量写入，请重试",Toast.LENGTH_LONG).show();return;}
        review.dismiss();changed.run();Toast.makeText(activity,"已入账 "+added+" 笔；已存在的截图记录已跳过",Toast.LENGTH_LONG).show();
    }
    private void edit(Item item){
        ScreenshotParser.Candidate c=item.value;LinearLayout form=column();
        EditText amount=new EditText(activity);amount.setInputType(8194);amount.setText(money(c.cents));amount.setHint("金额（元）");form.addView(amount);
        Spinner source=new Spinner(activity);source.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"微信","支付宝","银行卡"}));source.setSelection(c.source.equals("微信")?0:c.source.equals("支付宝")?1:2);form.addView(source);
        Spinner kind=new Spinner(activity);kind.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"支出","退款（抵扣所选日期）"}));kind.setSelection(c.cents<0?1:0);form.addView(kind);
        EditText note=new EditText(activity);note.setHint("消费备注（选填）");note.setText(c.note);note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});form.addView(note);
        Calendar date=Calendar.getInstance();date.setTimeInMillis(c.time);Button day=new Button(activity),clock=new Button(activity);day.setText(time(date.getTimeInMillis()).substring(0,10));clock.setText(time(date.getTimeInMillis()).substring(11));form.addView(day);form.addView(clock);
        day.setOnClickListener(v->new DatePickerDialog(activity,(view,y,m,d)->{date.set(y,m,d);day.setText(time(date.getTimeInMillis()).substring(0,10));},date.get(Calendar.YEAR),date.get(Calendar.MONTH),date.get(Calendar.DAY_OF_MONTH)).show());
        clock.setOnClickListener(v->new TimePickerDialog(activity,(view,h,m)->{date.set(Calendar.HOUR_OF_DAY,h);date.set(Calendar.MINUTE,m);clock.setText(time(date.getTimeInMillis()).substring(11));},date.get(Calendar.HOUR_OF_DAY),date.get(Calendar.MINUTE),true).show());
        ScrollView scroll=new ScrollView(activity);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("核对这笔消费").setView(scroll).setNegativeButton("取消",null).setPositiveButton("更新候选",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{long cents=PaymentParser.manualExpense(amount.getText().toString().trim());if(cents==PaymentParser.UNKNOWN){amount.setError("请输入大于0的金额，最多两位小数");return;}c.cents=kind.getSelectedItemPosition()==1?-cents:cents;c.source=source.getSelectedItem().toString();c.note=note.getText().toString().trim();c.time=date.getTimeInMillis();c.sourceKnown=c.hasDate=c.hasTime=true;update(item,false);dialog.dismiss();}));dialog.show();
    }
}
