package cn.local.notifyledger;

import android.content.Context;
import android.graphics.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.android.gms.tasks.Tasks;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Opt-in local device/emulator test: fixtures are supplied with adb, never committed. */
@RunWith(AndroidJUnit4.class)
public class LocalOcrDeviceTest {
    @Test public void recognizeSelectedLocalFixture() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name=InstrumentationRegistry.getArguments().getString("fixture","ocr-test.jpg");
        if(!name.matches("[a-zA-Z0-9_.-]+"))throw new IllegalArgumentException();
        File input=new File(context.getFilesDir(),name);
        org.junit.Assume.assumeTrue("Supply a local test image in target private files",input.isFile());
        Bitmap bitmap=BitmapFactory.decodeFile(input.toString());Assert.assertNotNull(bitmap);
        TextRecognizer recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        try {
            Text result=Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),90,TimeUnit.SECONDS);
            JSONArray lines=new JSONArray();
            for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines()){
                Rect box=line.getBoundingBox();if(box==null)continue;
                JSONObject row=new JSONObject();row.put("text",line.getText());row.put("left",box.left);row.put("top",box.top);row.put("right",box.right);row.put("bottom",box.bottom);lines.put(row);
            }
            JSONObject output=new JSONObject();output.put("width",bitmap.getWidth());output.put("height",bitmap.getHeight());output.put("text",result.getText());output.put("lines",lines);
            try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),name+".ocr.json"))){out.write(output.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            Assert.assertTrue(lines.length()>0);
            LocalScreenshotOcr.Result enhanced=Tasks.await(LocalScreenshotOcr.recognize(recognizer,bitmap),90,TimeUnit.SECONDS);
            JSONObject checked=new JSONObject();checked.put("text",enhanced.text);checked.put("recoveredDates",enhanced.recoveredDates);
            java.util.Calendar now=java.util.Calendar.getInstance();now.set(2026,9,19,23,0,0);
            JSONArray candidates=new JSONArray();java.util.List<ScreenshotParser.Candidate> parsed=ScreenshotParser.parse(enhanced.text,now.getTimeInMillis());
            for(ScreenshotParser.Candidate c:parsed){JSONObject item=new JSONObject();item.put("cents",c.cents);item.put("source",c.source);item.put("date",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(c.time)));item.put("hasDate",c.hasDate);item.put("hasTime",c.hasTime);candidates.put(item);}checked.put("candidates",candidates);
            try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),name+".enhanced.json"))){out.write(checked.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            String expected=InstrumentationRegistry.getArguments().getString("expected");if(expected!=null){String[] values=expected.split(",");Assert.assertEquals(values.length,parsed.size());for(int i=0;i<values.length;i++){Assert.assertEquals(Long.parseLong(values[i]),parsed.get(i).cents);Assert.assertEquals("支付宝",parsed.get(i).source);Assert.assertTrue(parsed.get(i).hasDate);Assert.assertTrue(parsed.get(i).hasTime);}
                String dates=InstrumentationRegistry.getArguments().getString("expectedDates");if(dates!=null){String[] expectedDates=dates.split(",");Assert.assertEquals(expectedDates.length,parsed.size());for(int i=0;i<expectedDates.length;i++)Assert.assertEquals(expectedDates[i],new java.text.SimpleDateFormat("yyyy-MM-dd_HH:mm",java.util.Locale.CHINA).format(new java.util.Date(parsed.get(i).time)));}}

        }finally{recognizer.close();bitmap.recycle();}
    }
}
