package com.agroshift.core.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {FieldEntity.class, OutboxEntity.class, ClimateProfileEntity.class,
        FarmRecordEntity.class, FieldWaterEntity.class, ChatSessionEntity.class,
        ChatMessageEntity.class}, version = 5, exportSchema = true)
public abstract class AgroShiftDatabase extends RoomDatabase {
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE fields ADD COLUMN latitude REAL");
            db.execSQL("ALTER TABLE fields ADD COLUMN longitude REAL");
            db.execSQL("ALTER TABLE fields ADD COLUMN rotationKey TEXT");
            db.execSQL("ALTER TABLE fields ADD COLUMN waterAccess TEXT");
            db.execSQL("ALTER TABLE fields ADD COLUMN goal TEXT");
            db.execSQL("CREATE TABLE IF NOT EXISTS climate_profiles ("
                    + "cellKey TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, "
                    + "startYear INTEGER NOT NULL, endYear INTEGER NOT NULL, "
                    + "fetchedAtMillis INTEGER NOT NULL, profileJson TEXT NOT NULL, "
                    + "PRIMARY KEY(cellKey))");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE fields ADD COLUMN soil TEXT");
            db.execSQL("CREATE TABLE IF NOT EXISTS farm_records ("
                    + "id TEXT NOT NULL, fieldId TEXT NOT NULL, type TEXT NOT NULL, "
                    + "title TEXT NOT NULL, amountRupees REAL NOT NULL, dateMillis INTEGER NOT NULL, "
                    + "done INTEGER NOT NULL, deletedAtMillis INTEGER, PRIMARY KEY(id))");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_farm_records_fieldId ON farm_records (fieldId)");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS field_water ("
                    + "fieldId TEXT NOT NULL, lastWateredEpochDay INTEGER, turnDay INTEGER, "
                    + "turnStartMinute INTEGER, turnDurationMinutes INTEGER, turnRemindMinutes INTEGER, "
                    + "updatedAtMillis INTEGER NOT NULL, PRIMARY KEY(fieldId))");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS chat_sessions ("
                    + "id TEXT NOT NULL, fieldId TEXT, title TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, "
                    + "updatedAtMillis INTEGER NOT NULL, messageCount INTEGER NOT NULL, PRIMARY KEY(id))");
            db.execSQL("CREATE TABLE IF NOT EXISTS chat_messages ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL, "
                    + "position INTEGER NOT NULL, fromFarmer INTEGER NOT NULL, text TEXT NOT NULL, "
                    + "actionsJson TEXT NOT NULL, expert TEXT, usedFieldData INTEGER NOT NULL, "
                    + "languageCode TEXT NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId ON chat_messages (sessionId)");
        }
    };

    public abstract FarmDao farmDao();

    public abstract ChatDao chatDao();

    public static AgroShiftDatabase create(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AgroShiftDatabase.class,
                "agroshift.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build();
    }
}
