package com.agroshift.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.engine.WaterAccess;
import com.agroshift.core.domain.farm.BinItem;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.feature.farm.RecycleBinSheet;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Deleted farms and records can be found in the bin and restored. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = TestAgroShiftApp.class)
public class RecycleBinFlowTest {
    private static void settle() {
        for (int i = 0; i < 30; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static <T> T read(LiveData<T> data) {
        androidx.lifecycle.Observer<T> keep = value -> { };
        data.observeForever(keep);
        settle();
        T value = data.getValue();
        data.removeObserver(keep);
        return value;
    }

    private static void clickRestore(View sheet) {
        RecyclerView list = sheet.findViewById(com.agroshift.feature.farm.R.id.bin_list);
        sheet.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY));
        sheet.layout(0, 0, 1080, 2000);
        assertTrue(list.getChildCount() > 0);
        list.getChildAt(0).findViewById(com.agroshift.feature.farm.R.id.bin_restore).performClick();
        settle();
    }

    @Test
    public void deletedFarmAndNoteCanBeRestored() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("agroshift_language", Context.MODE_PRIVATE).edit()
                .putString("selected_tag", "en-PK").putBoolean("onboarding_seen", true).commit();
        AppServices services = (AppServices) context;
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        FarmField field = services.fieldRepository().add("Bin farm", 4, 31.4, 73.1, RotationPlan.COTTON_WHEAT,
                WaterAccess.values()[0], FarmGoal.BALANCED, SoilType.UNKNOWN).value();
        FarmRecord note = services.recordRepository().add(field.id(), FarmRecord.Type.NOTE, "Check canal", 0).value();
        settle();
        services.recordRepository().delete(note.id());
        settle();
        Thread.sleep(5);
        services.fieldRepository().delete(field.id());
        settle();

        List<BinItem> bin = read(services.recordRepository().observeBin());
        assertEquals(2, bin.size());
        assertEquals(BinItem.Kind.FARM, bin.get(0).kind());
        assertEquals(BinItem.Kind.NOTE, bin.get(1).kind());
        assertTrue(bin.get(1).farmInBin());
        assertTrue(read(services.fieldRepository().observeFields()).isEmpty());

        RecycleBinSheet sheet = new RecycleBinSheet();
        sheet.show(activity.getSupportFragmentManager(), "bin");
        settle();
        assertNotNull(sheet.getView());
        clickRestore(sheet.requireView());
        assertEquals(1, read(services.fieldRepository().observeFields()).size());
        clickRestore(sheet.requireView());
        assertEquals(1, read(services.recordRepository().observe(field.id())).size());
        assertTrue(read(services.recordRepository().observeBin()).isEmpty());
    }
}
