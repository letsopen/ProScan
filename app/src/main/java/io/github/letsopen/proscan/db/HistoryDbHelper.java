package io.github.letsopen.proscan.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "scan_history.db";
    // 版本 2：升级为安全迁移（保留用户数据），不再 DROP TABLE
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_HISTORY = "history";

    private static final String COLUMN_ID = "id";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_TYPE = "type";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    // 单例 + 后台线程：所有数据库操作都离开主线程
    private static volatile HistoryDbHelper instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static HistoryDbHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (HistoryDbHelper.class) {
                if (instance == null) {
                    instance = new HistoryDbHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private HistoryDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_HISTORY + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_CONTENT + " TEXT,"
                + COLUMN_TYPE + " TEXT,"
                + COLUMN_TIMESTAMP + " INTEGER"
                + ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 安全迁移：旧版本缺少列或结构损坏时，尽量保留用户数据
        if (oldVersion < 2) {
            // v1 与 v2 表结构一致，这里只做防御性修复（表不存在则重建）
            if (!tableExists(db, TABLE_HISTORY)) {
                onCreate(db);
            }
        }
    }

    private boolean tableExists(SQLiteDatabase db, String table) {
        Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='?' AND name=?".replaceFirst("\\?", "table"),
                new String[]{table});
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    public interface DataCallback<T> {
        void onResult(T data);
    }

    /** 写入一条历史记录（异步） */
    public void addHistoryItem(String content, String type) {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_CONTENT, content);
            values.put(COLUMN_TYPE, type);
            values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
            db.insert(TABLE_HISTORY, null, values);
        });
    }

    /** 读取全部历史记录（异步，回调返回） */
    public void getAllHistoryItems(DataCallback<List<HistoryItem>> callback) {
        executor.execute(() -> callback.onResult(queryAll()));
    }

    /** 同步读取（仅供已有后台线程使用，如二维码生成线程） */
    public List<HistoryItem> getAllHistoryItemsSync() {
        return queryAll();
    }

    private List<HistoryItem> queryAll() {
        List<HistoryItem> historyList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_HISTORY + " ORDER BY " + COLUMN_TIMESTAMP + " DESC";
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    HistoryItem item = new HistoryItem(
                            cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)),
                            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE)),
                            new Date(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))));
                    historyList.add(item);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return historyList;
    }

    /** 删除一条记录（异步） */
    public void deleteHistoryItem(long id) {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_HISTORY, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        });
    }

    /** 清空全部历史（异步） */
    public void clearAllHistory() {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_HISTORY, null, null);
        });
    }
}
