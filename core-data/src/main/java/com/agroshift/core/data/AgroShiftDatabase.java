package com.agroshift.core.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {FieldEntity.class, OutboxEntity.class, ClimateProfileEntity.class,
        FarmRecordEntity.class}, version = 3, exportSchema = true)
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

    public abstract FarmDao farmDao();

    public static AgroShiftDatabase create(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AgroShiftDatabase.class,
                "agroshift.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build();
    }
}
