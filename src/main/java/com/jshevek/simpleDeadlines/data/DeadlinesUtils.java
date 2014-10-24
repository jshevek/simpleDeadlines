package com.jshevek.simpleDeadlines.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MergeCursor;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import com.jshevek.simpleDeadlines.R;
import com.jshevek.simpleDeadlines.provider.DeadlinesContract;

import java.io.*;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeadlinesUtils {
    public static final String BACKUP_FILENAME = "backup.sd";
    public static final Uri SHARE_BASE_URI = Uri.parse("http://sd.casimir-lab.net");
    private static final String TAG = DeadlinesUtils.class.getSimpleName();

    public static final int LVL_TODAY = 1;
    public static final int LVL_URGENT = 3;
    public static final int LVL_WORRYING = 7;
    public static final int LVL_NICE = 15;
    public static final int LVL_NEVERMIND = -1;
    public static final Map<Integer, Integer> LVL_ALL = new LinkedHashMap<>();

    static {
        LVL_ALL.put(LVL_TODAY, R.color.today);
        LVL_ALL.put(LVL_URGENT, R.color.urgent);
        LVL_ALL.put(LVL_WORRYING, R.color.worrying);
        LVL_ALL.put(LVL_NICE, R.color.nice);
        LVL_ALL.put(LVL_NEVERMIND, R.color.nevermind);
    }

    public static Uri contentValuesToShareUri(ContentValues values) {
        Uri.Builder builder = SHARE_BASE_URI.buildUpon();
        String group = values.getAsString(DeadlinesContract.Deadlines.GROUP);

        builder.appendPath(values.getAsString(DeadlinesContract.Deadlines.LABEL));
        if (TextUtils.isEmpty(group))
            builder.appendEncodedPath("%00");
        else
            builder.appendPath(group);
        builder.appendPath(values.getAsString(DeadlinesContract.Deadlines.DUE_DATE));
        builder.appendPath(values.getAsString(DeadlinesContract.Deadlines.DONE));

        return builder.build();
    }

    public static int dayCountToLvl(int days) {
        Integer[] keys = LVL_ALL.keySet().toArray(new Integer[]{});

        for (int i = 0; i < keys.length - 1; ++i) {
            if (days <= keys[i])
                return keys[i];
        }

        return LVL_NEVERMIND;
    }

    public static Uri performBackup(Context context) {
        ContentResolver cr = context.getContentResolver();
        Uri.Builder uriBuilder = DeadlinesContract.Deadlines.CONTENT_URI.buildUpon();
        Uri archivedUri = uriBuilder.appendPath(DeadlinesContract.Deadlines.FILTER_ARCHIVED).build();
        MergeCursor c = new MergeCursor(new Cursor[]{
                cr.query(DeadlinesContract.Deadlines.CONTENT_URI, null, null, null, null),
                cr.query(archivedUri, null, null, null, null)
        });

        String buff = "";
        ContentValues values = new ContentValues();
        while (c.moveToNext()) {
            DatabaseUtils.cursorRowToContentValues(c, values);
            buff += (buff.equals("") ? "" : "\n");
            buff += contentValuesToShareUri(values);
            values.clear();
        }

        try {
            FileOutputStream fos = context.openFileOutput(BACKUP_FILENAME, Context.MODE_WORLD_READABLE);
            fos.write(buff.getBytes());
            fos.close();
            return Uri.fromFile(context.getFileStreamPath(BACKUP_FILENAME));
        } catch (Exception ex) {
            Log.e(TAG, "Failed to perform backup", ex);
        }
        return Uri.EMPTY;
    }

    public static int performRecover(Context context, Uri uri) {
        ContentResolver cr = context.getContentResolver();
        try {
            InputStream is = cr.openInputStream(uri);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            int count = 0;

            while ((line = br.readLine()) != null) {
                ContentValues values = shareUriToContentValues(Uri.parse(line));
                cr.insert(DeadlinesContract.Deadlines.CONTENT_URI, values);
                ++count;
            }

            return count;
        } catch (FileNotFoundException ex) {
            Log.e(TAG, "Backup file not found", ex);
        } catch (IOException ex) {
            Log.e(TAG, "Failed to read backup file", ex);
        }

        return 0;
    }

    public static ContentValues shareUriToContentValues(Uri uri) {
        List<String> segments = uri.getPathSegments();
        if (segments.size() < 4)
            throw new IllegalArgumentException(String.format("Malformed Uri: %s", uri));

        ContentValues values = new ContentValues(4);

        if (TextUtils.isEmpty(segments.get(0)))
            throw new IllegalArgumentException(String.format("Empty label: %s", uri));
        values.put(DeadlinesContract.Deadlines.LABEL, segments.get(0));

        if (segments.get(1).getBytes()[0] == 0)
            values.put(DeadlinesContract.Deadlines.GROUP, (String) null);
        else
            values.put(DeadlinesContract.Deadlines.GROUP, segments.get(1));

        if (TextUtils.isEmpty(segments.get(2)) || !TextUtils.isDigitsOnly(segments.get(2)))
            throw new IllegalArgumentException(String.format("Malformed date: %s", uri));
        values.put(DeadlinesContract.Deadlines.DUE_DATE, Long.parseLong(segments.get(2)));

        if (TextUtils.isEmpty(segments.get(3)) || !TextUtils.isDigitsOnly(segments.get(3)))
            throw new IllegalArgumentException(String.format("Malformed done state: %s", uri));
        values.put(DeadlinesContract.Deadlines.DONE, Integer.parseInt(segments.get(3)));

        return values;
    }

    public static int timeToDayCount(long time) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        long todayDays = today.getTimeInMillis() / DateUtils.DAY_IN_MILLIS;
        long deadlineDays = time / DateUtils.DAY_IN_MILLIS;

        return (int) (deadlineDays - todayDays);
    }
}
