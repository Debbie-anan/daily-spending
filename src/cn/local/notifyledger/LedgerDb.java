package cn.local.notifyledger;

import android.content.*;
import android.database.sqlite.*;
import android.database.Cursor;
import org.json.*;

public final class LedgerDb extends SQLiteOpenHelper {
    public LedgerDb(Context c) {super(c,"ledger.db",null,4);}
    public void onConfigure(SQLiteDatabase db) {try(Cursor c=db.rawQuery("PRAGMA secure_delete=ON",null)){c.moveToFirst();}}
    public void onCreate(SQLiteDatabase db) {db.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY, fingerprint TEXT UNIQUE, source TEXT, content TEXT, posted INTEGER, received INTEGER, cents INTEGER, state TEXT, note TEXT NOT NULL DEFAULT '')");}
    public void onUpgrade(SQLiteDatabase db,int oldV,int newV) {
        if(oldV<2) {try(Cursor c=db.rawQuery("SELECT _id,content,state,received FROM events",null)) {while(c.moveToNext()) {
            long amount=PaymentParser.cents(c.getString(1));ContentValues v=new ContentValues();v.put("cents",amount);
            if(!c.getString(2).equals("ignored"))v.put("state",amount!=PaymentParser.UNKNOWN?"auto":"pending");
            if(amount!=PaymentParser.UNKNOWN && amount<0)v.put("posted",c.getLong(3));
            db.update("events",v,"_id=?",new String[]{c.getString(0)});
        }}}
        if(oldV<3) {try(Cursor c=db.rawQuery("SELECT _id,content,cents,state FROM events",null)) {while(c.moveToNext()) {
            ContentValues v=new ContentValues();v.put("content",RecordPrivacy.summary(c.getString(1),c.getLong(2),c.getString(3)));
            db.update("events",v,"_id=?",new String[]{c.getString(0)});
        }}}
        if(oldV<4)db.execSQL("ALTER TABLE events ADD COLUMN note TEXT NOT NULL DEFAULT ''");
    }
    public boolean insert(String fingerprint,String source,String content,long posted,long cents) {
        ContentValues v=new ContentValues(); v.put("fingerprint",fingerprint);v.put("source",source);v.put("content",content);v.put("posted",cents!=PaymentParser.UNKNOWN && cents<0?System.currentTimeMillis():posted);v.put("received",System.currentTimeMillis());v.put("cents",cents);v.put("state",cents!=PaymentParser.UNKNOWN?"auto":"pending");
        // A delayed notification may describe a screenshot already entered. Keep it for review instead of counting twice.
        if(cents!=PaymentParser.UNKNOWN && hasScreenshotMatch(source,cents,v.getAsLong("posted")))v.put("state","pending");
        return getWritableDatabase().insertWithOnConflict("events",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
    public boolean hasFingerprint(String fp){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM events WHERE fingerprint=? LIMIT 1",new String[]{fp})){return c.moveToFirst();}}
    private boolean hasScreenshotMatch(String source,long cents,long posted){
        java.util.Calendar day=java.util.Calendar.getInstance();day.setTimeInMillis(posted);day.set(java.util.Calendar.HOUR_OF_DAY,0);day.set(java.util.Calendar.MINUTE,0);day.set(java.util.Calendar.SECOND,0);day.set(java.util.Calendar.MILLISECOND,0);long start=day.getTimeInMillis();day.add(java.util.Calendar.DAY_OF_MONTH,1);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM events WHERE source=? AND cents=? AND state='auto' AND fingerprint LIKE 'screenshot-%' AND posted>=? AND posted<? LIMIT 1",new String[]{source,""+cents,""+start,""+day.getTimeInMillis()})){return c.moveToFirst();}
    }
    public String duplicateDescription(String source,long cents,long posted){
        java.util.Calendar day=java.util.Calendar.getInstance();day.setTimeInMillis(posted);day.set(java.util.Calendar.HOUR_OF_DAY,0);day.set(java.util.Calendar.MINUTE,0);day.set(java.util.Calendar.SECOND,0);day.set(java.util.Calendar.MILLISECOND,0);long start=day.getTimeInMillis();day.add(java.util.Calendar.DAY_OF_MONTH,1);
        StringBuilder out=new StringBuilder();try(Cursor c=getReadableDatabase().rawQuery("SELECT posted,note,content FROM events WHERE source=? AND cents=? AND state='auto' AND posted>=? AND posted<? ORDER BY ABS(posted-?) LIMIT 5",new String[]{source,""+cents,""+start,""+day.getTimeInMillis(),""+posted})){while(c.moveToNext()){if(out.length()>0)out.append("\n");out.append(new java.text.SimpleDateFormat("HH:mm",java.util.Locale.CHINA).format(new java.util.Date(c.getLong(0)))).append(" · ").append(c.getString(1).isEmpty()?c.getString(2):c.getString(1));}}
        return out.toString();
    }
    public boolean insertScreenshot(String fp,String source,long cents,long posted,String note){
        if(!source.matches("微信|支付宝|银行卡")||cents==0||cents==PaymentParser.UNKNOWN||Math.abs(cents)>1000000000L||note.length()>120)throw new IllegalArgumentException();
        ContentValues v=new ContentValues();v.put("fingerprint",fp);v.put("source",source);v.put("cents",cents);v.put("posted",posted);v.put("received",System.currentTimeMillis());v.put("content","截图记账："+(cents<0?"退款":"支出")+" ¥"+java.math.BigDecimal.valueOf(cents).abs().movePointLeft(2).toPlainString());v.put("note",note.trim());v.put("state","auto");
        return getWritableDatabase().insertWithOnConflict("events",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
    public void accept(long id){ContentValues v=new ContentValues();v.put("state","auto");getWritableDatabase().update("events",v,"_id=? AND state='pending' AND cents!=?",new String[]{""+id,""+PaymentParser.UNKNOWN});}
    public void ignore(long id) {ContentValues v=new ContentValues();v.put("state","ignored");getWritableDatabase().update("events",v,"_id=?",new String[]{""+id});}
    public void insertManual(String source,String category,String note,long posted,long cents) {
        if(!source.matches("微信|支付宝|银行卡") || !category.matches("日常消费|转账|红包|其他支出") || cents<=0 || cents>1000000000L || note==null || note.length()>120)throw new IllegalArgumentException("无效手动记录");
        ContentValues v=new ContentValues();v.put("fingerprint","manual-"+java.util.UUID.randomUUID());v.put("source",source);v.put("content","手动记账："+category+" ¥"+java.math.BigDecimal.valueOf(cents).movePointLeft(2).toPlainString());v.put("note",note.trim());v.put("posted",posted);v.put("received",System.currentTimeMillis());v.put("cents",cents);v.put("state","auto");
        getWritableDatabase().insertOrThrow("events",null,v);
    }
    public String export() throws JSONException {
        JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM events ORDER BY posted DESC",null)) {while(c.moveToNext()) {JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++)o.put(c.getColumnName(i),c.getString(i));a.put(o);}}
        JSONObject out=new JSONObject();out.put("version",2);out.put("timeBasis","expense_notification_post_time_refund_received_time");out.put("amountConvention","positive_expense_negative_refund_unknown_min_long");out.put("events",a);return out.toString(2);
    }
}
