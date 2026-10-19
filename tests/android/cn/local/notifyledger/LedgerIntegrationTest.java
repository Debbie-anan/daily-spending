package cn.local.notifyledger;
import android.app.Notification;
import android.content.Context;
import android.database.Cursor;
import android.service.notification.StatusBarNotification;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class LedgerIntegrationTest {
    Context context;
    @Before public void before(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("ledger.db");}
    @Test public void screenshotAndDelayedNotificationAreNotBothCounted(){
        long now=System.currentTimeMillis();
        try(LedgerDb db=new LedgerDb(context)){
            assertTrue(db.insertScreenshot("screenshot-image-0","支付宝",1800,now,"晚餐"));
            assertFalse(db.insertScreenshot("screenshot-image-0","支付宝",1800,now,"晚餐"));
            assertFalse(db.duplicateDescription("支付宝",1800,now).isEmpty());
            assertTrue(db.duplicateDescription("微信",1800,now).isEmpty());
            assertTrue(db.insert("delayed-notification","支付宝","付款成功 ¥18.00",now,1800));
            try(Cursor c=db.getReadableDatabase().rawQuery("SELECT state FROM events WHERE fingerprint='delayed-notification'",null)){assertTrue(c.moveToFirst());assertEquals("pending",c.getString(0));}
            try(Cursor c=db.getReadableDatabase().rawQuery("SELECT SUM(cents) FROM events WHERE state='auto'",null)){c.moveToFirst();assertEquals(1800,c.getLong(0));}
        }
    }
    @Test public void liveAlipay18AndRefreshIdentity(){
        PaymentListener listener=Robolectric.buildService(PaymentListener.class).create().get();
        Notification n=new Notification.Builder(context,"payment").setContentTitle("交易提醒").setContentText("你有一笔18.00元的支出，点此查看详情。").setWhen(System.currentTimeMillis()).build();
        StatusBarNotification sbn=new StatusBarNotification("com.eg.android.AlipayGphone","com.eg.android.AlipayGphone",11,"pay",1000,0,0,n,android.os.Process.myUserHandle(),System.currentTimeMillis());
        listener.onNotificationPosted(sbn);listener.onNotificationPosted(sbn);
        try(LedgerDb db=new LedgerDb(context);Cursor c=db.getReadableDatabase().rawQuery("SELECT source,cents,content FROM events",null)){
            assertEquals(1,c.getCount());c.moveToFirst();assertEquals("支付宝",c.getString(0));assertEquals(1800,c.getLong(1));assertFalse(c.getString(2).contains("点此"));
        }
        listener.onDestroy();
    }
    @Test public void unrelatedAppsAndTitlesAreIgnored(){
        PaymentListener listener=Robolectric.buildService(PaymentListener.class).create().get();
        Notification n=new Notification.Builder(context,"payment").setContentTitle("交易提醒").setContentText("你有一笔18.00元的支出，点此查看详情。").build();
        listener.onNotificationPosted(new StatusBarNotification("other.app","other.app",11,null,1000,0,0,n,android.os.Process.myUserHandle(),System.currentTimeMillis()));
        n=new Notification.Builder(context,"payment").setContentTitle("朋友").setContentText("付款成功 ¥18.00").build();
        listener.onNotificationPosted(new StatusBarNotification("com.tencent.mm","com.tencent.mm",12,null,1000,0,0,n,android.os.Process.myUserHandle(),System.currentTimeMillis()));
        try(LedgerDb db=new LedgerDb(context);Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM events",null)){c.moveToFirst();assertEquals(0,c.getInt(0));}listener.onDestroy();
    }
    @Test public void sameAmountDifferentRealPaymentsCanBeConfirmed(){
        try(LedgerDb db=new LedgerDb(context)){
            long now=System.currentTimeMillis();db.insertManual("银行卡","日常消费","午餐",now,1800);
            assertTrue(db.insertScreenshot("screenshot-other-0","支付宝",1800,now,"晚餐"));
            assertTrue(db.insertScreenshot("screenshot-other-1","支付宝",1800,now+600000,"另一笔"));
            assertTrue(db.export().contains("午餐"));
        }catch(Exception ex){throw new AssertionError(ex);}
    }
    @Test public void batchReviewRequiresConfirmationAndSkipsExisting() throws Exception {
        android.app.Activity activity=Robolectric.buildActivity(android.app.Activity.class).setup().get();
        long now=System.currentTimeMillis();
        try(LedgerDb db=new LedgerDb(context)){db.insert("auto-existing","支付宝","付款成功 ¥18.00",now,1800);}
        ScreenshotImport flow=new ScreenshotImport(activity,()->{});
        java.lang.reflect.Field itemsField=ScreenshotImport.class.getDeclaredField("items");itemsField.setAccessible(true);
        java.util.List items=(java.util.List)itemsField.get(flow);
        Class<?> type=Class.forName("cn.local.notifyledger.ScreenshotImport$Item");java.lang.reflect.Constructor<?> ctor=type.getDeclaredConstructor();ctor.setAccessible(true);
        java.util.List<ScreenshotParser.Candidate> candidates=ScreenshotParser.parse("支付宝账单\n超市 -18.00\n咖啡 -12.50\n晚餐 -25.00",now);
        int index=0;for(ScreenshotParser.Candidate candidate:candidates){Object item=ctor.newInstance();java.lang.reflect.Field value=type.getDeclaredField("value"),fp=type.getDeclaredField("fingerprint");value.setAccessible(true);fp.setAccessible(true);value.set(item,candidate);fp.set(item,"screenshot-test-"+(index++));items.add(item);}
        java.lang.reflect.Method show=ScreenshotImport.class.getDeclaredMethod("showReview");show.setAccessible(true);show.invoke(flow);org.robolectric.shadows.ShadowLooper.idleMainLooper();
        android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        java.util.List<android.widget.CheckBox> checks=new java.util.ArrayList<>();findChecks(dialog.getWindow().getDecorView(),checks);
        assertEquals(4,checks.size());assertFalse(checks.get(0).isChecked());assertTrue(checks.get(1).isChecked());assertTrue(checks.get(2).isChecked());
        dialog.getButton(-1).performClick();assertTrue(dialog.isShowing());
        checks.get(3).setChecked(true);dialog.getButton(-1).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();
        try(LedgerDb db=new LedgerDb(context);Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*),SUM(cents) FROM events WHERE state='auto'",null)){c.moveToFirst();assertEquals(3,c.getInt(0));assertEquals(5550,c.getLong(1));}
        flow.close();activity.finish();
    }
    private void findChecks(android.view.View view,java.util.List<android.widget.CheckBox> out){if(view instanceof android.widget.CheckBox)out.add((android.widget.CheckBox)view);if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)findChecks(group.getChildAt(i),out);}}
}
