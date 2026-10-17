package cn.local.notifyledger;

import android.content.*;
import android.database.sqlite.*;
import android.database.Cursor;
import org.json.*;

public final class LedgerDb extends SQLiteOpenHelper {
    public LedgerDb(Context c) {super(c,"ledger.db",null,2);}
    public void onCreate(SQLiteDatabase db) {db.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY, fingerprint TEXT UNIQUE, source TEXT, content TEXT, posted INTEGER, received INTEGER, cents INTEGER, state TEXT)");}
    public void onUpgrade(SQLiteDatabase db,int oldV,int newV) {
        if(oldV<2) {try(Cursor c=db.rawQuery("SELECT _id,content,state,received FROM events",null)) {while(c.moveToNext()) {
            long amount=PaymentParser.cents(c.getString(1));ContentValues v=new ContentValues();v.put("cents",amount);
            if(!c.getString(2).equals("ignored"))v.put("state",amount!=PaymentParser.UNKNOWN?"auto":"pending");
            if(amount!=PaymentParser.UNKNOWN && amount<0)v.put("posted",c.getLong(3));
            db.update("events",v,"_id=?",new String[]{c.getString(0)});
        }}}
    }
    public void insert(String fingerprint,String source,String content,long posted,long cents) {
        ContentValues v=new ContentValues(); v.put("fingerprint",fingerprint);v.put("source",source);v.put("content",content);v.put("posted",cents!=PaymentParser.UNKNOWN && cents<0?System.currentTimeMillis():posted);v.put("received",System.currentTimeMillis());v.put("cents",cents);v.put("state",cents!=PaymentParser.UNKNOWN?"auto":"pending");
        getWritableDatabase().insertWithOnConflict("events",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    public void ignore(long id) {ContentValues v=new ContentValues();v.put("state","ignored");getWritableDatabase().update("events",v,"_id=?",new String[]{""+id});}
    public String export() throws JSONException {
        JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM events ORDER BY posted DESC",null)) {while(c.moveToNext()) {JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++)o.put(c.getColumnName(i),c.getString(i));a.put(o);}}
        JSONObject out=new JSONObject();out.put("version",2);out.put("timeBasis","expense_notification_post_time_refund_received_time");out.put("amountConvention","positive_expense_negative_refund_unknown_min_long");out.put("events",a);return out.toString(2);
    }
}
