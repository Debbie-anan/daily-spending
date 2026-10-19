package cn.local.notifyledger;

import android.graphics.*;
import com.google.android.gms.tasks.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import java.util.*;
import java.util.regex.*;

/** Two local passes: preserve money from the full image; retry small date text enlarged. */
public final class LocalScreenshotOcr {
    public static final class Row {
        public String text;public final Rect bounds;
        Row(String t,Rect b){text=t;bounds=new Rect(b);}
    }
    public static final class Result {
        public final List<Row> rows;public final String text;public final int recoveredDates;
        Result(List<Row> r,int recovered){rows=r;text=flatten(r);recoveredDates=recovered;}
    }
    private static final Pattern CLOCK=Pattern.compile("([0-9]{1,2}):([0-9]{2})");
    private static boolean date(String text){return text.matches("(?s).*(今天|昨天|昨日|前天|[0-9]{1,2}月[0-9]{1,2}|[0-9]{1,2}[-/][0-9]{1,2}).*");}
    private static String clock(String text){Matcher m=CLOCK.matcher(text.replace('：',':'));return m.find()?Integer.parseInt(m.group(1))+":"+m.group(2):"";}
    static boolean canRetryDate(Row row,int imageHeight){return row.bounds.top>imageHeight*.12&&!clock(row.text).isEmpty()&&!date(row.text)&&!row.text.matches("(?s).*(?:[0-9]+\\.[0-9]+|[¥￥]).*");}
    static String restoredPrefix(String relative,String original){Matcher period=Pattern.compile("上午|下午|晚上|中午|凌晨").matcher(original);return relative+" "+(period.find()?period.group():"")+clock(original);}
    private static List<Row> rows(Text text){List<Row> rows=new ArrayList<>();for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines())if(line.getBoundingBox()!=null)rows.add(new Row(line.getText(),line.getBoundingBox()));return rows;}
    public static Task<Result> recognize(TextRecognizer recognizer,Bitmap image){
        Task<Result> base=recognizer.process(InputImage.fromBitmap(image,0)).continueWithTask(full->{
            if(!full.isSuccessful())throw full.getException()==null?new IllegalStateException():full.getException();
            List<Row> original=rows(full.getResult());boolean needsRetry=false;
            for(Row row:original)if(canRetryDate(row,image.getHeight())){needsRetry=true;break;}
            if(!needsRetry)return Tasks.forResult(new Result(original,0));
            // Restrict to the metadata column; never replace already-recognized monetary values.
            int left=(int)(image.getWidth()*.10),right=(int)(image.getWidth()*.66);
            Bitmap crop=Bitmap.createBitmap(image,left,0,right-left,image.getHeight());
            float scale=(float)Math.min(3.0,Math.sqrt(4000000.0/((long)crop.getWidth()*crop.getHeight())));
            Bitmap zoom=Bitmap.createScaledBitmap(crop,Math.max(1,Math.round(crop.getWidth()*scale)),Math.max(1,Math.round(crop.getHeight()*scale)),true);
            if(crop!=image&&crop!=zoom)crop.recycle();
            return recognizer.process(InputImage.fromBitmap(zoom,0)).continueWith(retry->{
                int recovered=0;
                try{if(retry.isSuccessful()){
                    List<Row> improved=rows(retry.getResult());
                    for(Row row:original){String time=clock(row.text);if(!canRetryDate(row,image.getHeight()))continue;
                        for(Row candidate:improved){float cy=candidate.bounds.exactCenterY()/scale;
                            if(date(candidate.text)&&time.equals(clock(candidate.text))&&Math.abs(cy-row.bounds.exactCenterY())<Math.max(12,row.bounds.height())){row.text=candidate.text;recovered++;break;}
                        }
                    }
                }return new Result(original,recovered);}finally{if(zoom!=image)zoom.recycle();}
            });
        });
        return base.continueWithTask(result->{
            if(!result.isSuccessful())throw result.getException()==null?new IllegalStateException():result.getException();
            Result full=result.getResult();return retryRows(recognizer,image,full.rows,0,0,full.recoveredDates);
        });
    }
    private static Task<Result> retryRows(TextRecognizer recognizer,Bitmap image,List<Row> rows,int start,int attempts,int recovered){
        int at=start;while(at<rows.size()&&!canRetryDate(rows.get(at),image.getHeight()))at++;
        if(at>=rows.size()||attempts>=12)return Tasks.forResult(new Result(rows,recovered));
        final int index=at;Row row=rows.get(index);Rect b=row.bounds;
        int left=Math.max(0,Math.min(b.left-12,(int)(image.getWidth()*.15))),top=Math.max(0,b.top-b.height()/2);
        int right=Math.min(image.getWidth(),Math.max(b.right+12,(int)(image.getWidth()*.40))),bottom=Math.min(image.getHeight(),b.bottom+b.height()/2);
        Bitmap crop=Bitmap.createBitmap(image,left,top,Math.max(1,right-left),Math.max(1,bottom-top));
        Bitmap zoom=Bitmap.createScaledBitmap(crop,crop.getWidth()*4,crop.getHeight()*4,true);if(crop!=image&&crop!=zoom)crop.recycle();
        Bitmap enhanced=Bitmap.createBitmap(zoom.getWidth(),zoom.getHeight(),Bitmap.Config.ARGB_8888);
        Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);float r=.2126f*3,g=.7152f*3,blue=.0722f*3;
        paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{r,g,blue,0,-510,r,g,blue,0,-510,r,g,blue,0,-510,0,0,0,1,0})));
        new Canvas(enhanced).drawBitmap(zoom,0,0,paint);if(zoom!=image)zoom.recycle();
        return recognizer.process(InputImage.fromBitmap(enhanced,0)).continueWithTask(retry->{
            int fixed=recovered;
            try{if(retry.isSuccessful()){String text=retry.getResult().getText().replace('\n',' ');if(date(text)&&clock(row.text).equals(clock(text))){row.text=text;fixed++;}}}
            finally{enhanced.recycle();}
            if(date(row.text))return retryRows(recognizer,image,rows,index+1,attempts+1,fixed);
            // Chinese 今天 may be mistaken for punctuation when joined to a time. Read just the prefix.
            final int alreadyFixed=fixed;
            int prefixLeft=Math.max(0,Math.min(b.left-10,(int)(image.getWidth()*.17)));
            int prefixRight=Math.min(image.getWidth(),Math.max(prefixLeft+30,(int)(image.getWidth()*.25)));
            Bitmap prefix=Bitmap.createBitmap(image,prefixLeft,top,prefixRight-prefixLeft,bottom-top);
            Bitmap prefixZoom=Bitmap.createScaledBitmap(prefix,prefix.getWidth()*5,prefix.getHeight()*5,true);if(prefix!=image&&prefix!=prefixZoom)prefix.recycle();
            return recognizer.process(InputImage.fromBitmap(prefixZoom,0)).continueWithTask(token->{
                int corrected=alreadyFixed;
                try{if(token.isSuccessful()){Matcher relative=Pattern.compile("今天|昨天|昨日|前天").matcher(token.getResult().getText());if(relative.find()){row.text=restoredPrefix(relative.group(),row.text);corrected++;}}}
                finally{if(prefixZoom!=image)prefixZoom.recycle();}
                return retryRows(recognizer,image,rows,index+1,attempts+1,corrected);
            });
        });
    }
    private static String flatten(List<Row> input){
        List<Row> lines=new ArrayList<>(input);lines.sort((a,b)->{int dy=Integer.compare(a.bounds.top,b.bounds.top);return dy==0?Integer.compare(a.bounds.left,b.bounds.left):dy;});
        List<Row> ordered=new ArrayList<>();for(int at=0;at<lines.size();){int end=at+1;Rect anchor=lines.get(at).bounds;while(end<lines.size()){Rect rect=lines.get(end).bounds;if(Math.abs(rect.centerY()-anchor.centerY())>=Math.min(rect.height(),anchor.height())/2)break;end++;}List<Row> row=new ArrayList<>(lines.subList(at,end));row.sort((a,b)->Integer.compare(a.bounds.left,b.bounds.left));ordered.addAll(row);at=end;}
        StringBuilder text=new StringBuilder();Rect last=null;for(Row row:ordered){Rect rect=row.bounds;if(text.length()>0)text.append(last!=null&&Math.abs(last.centerY()-rect.centerY())<Math.min(last.height(),rect.height())/2?' ':'\n');text.append(row.text);last=rect;}return text.toString();
    }
}
