package com.jshevek.simpleDeadlines.dashclock;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.text.format.DateUtils;

import com.jshevek.simpleDeadlines.R;
import com.jshevek.simpleDeadlines.data.DeadlinesUtils;
import com.jshevek.simpleDeadlines.provider.DeadlinesContract;
import com.jshevek.simpleDeadlines.ui.MainActivity;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import java.util.Calendar;

public class DeadlinesExtension extends DashClockExtension {
    private static final int NB_LINES = 5;
    private ContentResolver _cr;

    @Override
    public void onCreate() {
        super.onCreate();

        _cr = getContentResolver();
    }

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);

        setUpdateWhenScreenOn(true);
    }

    @Override
    protected void onUpdateData(int reason) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR, 0);
        long diff = today.getTimeInMillis() + DeadlinesUtils.LVL_TODAY * DateUtils.DAY_IN_MILLIS;
        String selection = DeadlinesContract.Deadlines.DUE_DATE + " <= ?"
                + " AND " + DeadlinesContract.Deadlines.DONE + " = 0";
        String[] selectionArgs = {String.valueOf(diff)};

        Cursor cursor = _cr.query(DeadlinesContract.Deadlines.CONTENT_URI, null,
                selection, selectionArgs, null);

        ExtensionData data = new ExtensionData()
                .visible(cursor.getCount() > 0)
                .icon(R.drawable.ic_launcher_white)
                .clickIntent(new Intent(getApplicationContext(), MainActivity.class))
                .status(getString(R.string.dashclock_status, cursor.getCount()))
                .expandedTitle(getString(R.string.dashclock_title, cursor.getCount()))
                .expandedBody(makeBody(cursor));
        publishUpdate(data);
    }

    private static String makeBody(Cursor cursor) {
        StringBuilder builder = new StringBuilder();

        if (!cursor.moveToFirst())
            return "";

        for (int i = 0; i < cursor.getCount() && i < NB_LINES; ++i) {
            ContentValues values = new ContentValues();
            DatabaseUtils.cursorRowToContentValues(cursor, values);
            builder.append(values.getAsString(DeadlinesContract.Deadlines.LABEL) + "\n");
            cursor.moveToNext();
        }

        return builder.toString();
    }
}
