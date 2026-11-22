package com.agroshift.core.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.agroshift.app.TestAgroShiftApp;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Existing farmers must keep their saved farms when the app updates. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = TestAgroShiftApp.class)
public class DatabaseUpgradeTest {
    private static final String NAME = "upgrade-test.db";

    private static File schema(int version) {
        for (String base : new String[] {"../core-data/schemas", "core-data/schemas"}) {
            File file = new File(base, "com.agroshift.core.data.AgroShiftDatabase/" + version + ".json");
            if (file.exists()) {
                return file;
            }
        }
        throw new IllegalStateException("Missing schema " + version);
    }

    private static void createVersionOne(File path) throws Exception {
        JSONObject database = new JSONObject(new String(Files.readAllBytes(schema(1).toPath()),
                StandardCharsets.UTF_8)).getJSONObject("database");
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(path, null);
        JSONArray entities = database.getJSONArray("entities");
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.getJSONObject(i);
            String table = entity.getString("tableName");
            db.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", table));
            JSONArray indices = entity.optJSONArray("indices");
            for (int k = 0; indices != null && k < indices.length(); k++) {
                db.execSQL(indices.getJSONObject(k).getString("createSql").replace("${TABLE_NAME}", table));
            }
        }
        JSONArray setup = database.getJSONArray("setupQueries");
        for (int i = 0; i < setup.length(); i++) {
            db.execSQL(setup.getString(i));
        }
        ContentValues field = new ContentValues();
        field.put("id", "old-farm");
        field.put("name", "Old farm");
        field.put("areaAcres", 3.5);
        field.put("crop", "wheat");
        field.put("updatedAtMillis", 10L);
        db.insertOrThrow("fields", null, field);
        db.setVersion(1);
        db.close();
    }

    @Test
    public void everyOlderDatabaseUpgradesAndKeepsFarms() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File path = context.getDatabasePath(NAME);
        assertTrue(path.getParentFile().exists() || path.getParentFile().mkdirs());
        createVersionOne(path);

        AgroShiftDatabase upgraded = Room.databaseBuilder(context, AgroShiftDatabase.class, NAME)
                .addMigrations(AgroShiftDatabase.MIGRATION_1_2, AgroShiftDatabase.MIGRATION_2_3,
                        AgroShiftDatabase.MIGRATION_3_4, AgroShiftDatabase.MIGRATION_4_5)
                .allowMainThreadQueries().build();
        List<FieldEntity> fields = upgraded.farmDao().activeFields();
        assertEquals(1, fields.size());
        assertEquals("Old farm", fields.get(0).name);
        assertNotNull(upgraded.chatDao().messages("none"));
        assertEquals(5, upgraded.getOpenHelper().getWritableDatabase().getVersion());
        upgraded.close();
    }
}
