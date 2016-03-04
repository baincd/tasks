/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.gcal;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.Time;

import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.service.TaskService;

import org.tasks.R;
import org.tasks.injection.ForApplication;
import org.tasks.preferences.Preferences;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;

import timber.log.Timber;

import static android.provider.BaseColumns._ID;

public class GCalHelper {

    /** If task has no estimated time, how early to set a task in calendar (seconds)*/
    private static final long DEFAULT_CAL_TIME = DateUtilities.ONE_HOUR;

    private final Context context;
    private final TaskService taskService;
    private final Preferences preferences;

    @Inject
    public GCalHelper(@ForApplication Context context, TaskService taskService, Preferences preferences) {
        this.context = context;
        this.taskService = taskService;
        this.preferences = preferences;
    }

    public String getTaskEventUri(Task task) {
        String uri;
        if (!TextUtils.isEmpty(task.getCalendarURI())) {
            uri = task.getCalendarURI();
        } else {
            task = taskService.fetchById(task.getId(), Task.CALENDAR_URI);
            if(task == null) {
                return null;
            }
            uri = task.getCalendarURI();
        }

        return uri;
    }

    public void createTaskEventIfEnabled(Task t) {
        if (!t.hasDueDate()) {
            return;
        }
        createTaskEventIfEnabled(t, true);
    }

    private void createTaskEventIfEnabled(Task t, boolean deleteEventIfExists) {
        if (preferences.isDefaultCalendarSet()) {
            ContentResolver cr = context.getContentResolver();
            Uri calendarUri = createTaskEvent(t, cr, new ContentValues(), deleteEventIfExists);
            if (calendarUri != null) {
                t.setCalendarUri(calendarUri.toString());
            }
        }
    }

    public Uri createTaskEvent(Task task, ContentResolver cr, ContentValues values) {
        return createTaskEvent(task, cr, values, true);
    }

    public Uri createTaskEvent(Task task, ContentResolver cr, ContentValues values, boolean deleteEventIfExists) {
        String eventuri = getTaskEventUri(task);

        if(!TextUtils.isEmpty(eventuri) && deleteEventIfExists) {
            deleteTaskEvent(task);
        }

        try{
            values.put(CalendarContract.Events.TITLE, task.getTitle());
            values.put(CalendarContract.Events.DESCRIPTION, task.getNotes());
            values.put(CalendarContract.Events.HAS_ALARM, 0);
            boolean valuesContainCalendarId = (values.containsKey(CalendarContract.Events.CALENDAR_ID) &&
                    !TextUtils.isEmpty(values.getAsString(CalendarContract.Events.CALENDAR_ID)));
            if (!valuesContainCalendarId) {
                String calendarId = preferences.getDefaultCalendar();
                if (!TextUtils.isEmpty(calendarId)) {
                    values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                }
            }

            createStartAndEndDate(task, values);

            Uri eventUri = cr.insert(CalendarContract.Events.CONTENT_URI, values);
            cr.notifyChange(eventUri, null);

            return eventUri;

        } catch (Exception e) {
            // won't work on emulator
            Timber.e(e, e.getMessage());
        }

        return null;
    }

    public void rescheduleRepeatingTask(Task task, ContentResolver cr) {
        String taskUri = getTaskEventUri(task);
        if (TextUtils.isEmpty(taskUri)) {
            return;
        }

        Uri eventUri = Uri.parse(taskUri);
        String calendarId = getCalendarId(eventUri, cr);
        if (calendarId == null) { // Bail out, no calendar id
            task.setCalendarUri(""); //$NON-NLS-1$
            return;
        }
        ContentValues cv = new ContentValues();
        cv.put(CalendarContract.Events.CALENDAR_ID, calendarId);

        Uri uri = createTaskEvent(task, cr, cv, false);
        task.setCalendarUri(uri.toString());
    }

    private static String getCalendarId(Uri uri, ContentResolver cr) {
        Cursor calendar = cr.query(uri, new String[]{CalendarContract.Events.CALENDAR_ID}, null, null, null);
        try {
            calendar.moveToFirst();
            return calendar.getString(0);
        } catch (CursorIndexOutOfBoundsException e) {
            Timber.e(e, e.getMessage());
            return null;
        } finally  {
            calendar.close();
        }
    }

    public boolean deleteTaskEvent(Task task) {
        boolean eventDeleted = false;
        String uri;
        if(task.containsNonNullValue(Task.CALENDAR_URI)) {
            uri = task.getCalendarURI();
        } else {
            task = taskService.fetchById(task.getId(), Task.CALENDAR_URI);
            if(task == null) {
                return false;
            }
            uri = task.getCalendarURI();
        }

        if(!TextUtils.isEmpty(uri)) {
            try {
                Uri calendarUri = Uri.parse(uri);

                // try to load calendar
                ContentResolver cr = context.getContentResolver();
                Cursor cursor = cr.query(calendarUri, new String[] { CalendarContract.Events.DTSTART }, null, null, null); //$NON-NLS-1$
                try {
                    boolean alreadydeleted = cursor.getCount() == 0;

                    if (!alreadydeleted) {
                        cr.delete(calendarUri, null, null);
                        eventDeleted = true;
                    }
                } finally {
                    cursor.close();
                }

                task.setCalendarUri( "");
            } catch (Exception e) {
                Timber.e(e, e.getMessage());
            }
        }

        return eventDeleted;
    }

    public void createStartAndEndDate(Task task, ContentValues values) {
        long dueDate = task.getDueDate();
        long tzCorrectedDueDate = dueDate + TimeZone.getDefault().getOffset(dueDate);
        long tzCorrectedDueDateNow = DateUtilities.now() + TimeZone.getDefault().getOffset(DateUtilities.now());
        // FIXME: doesnt respect timezones, see story 17443653
        if(task.hasDueDate()) {
            if(task.hasDueTime()) {
                long estimatedTime = task.getEstimatedSeconds()  * 1000;
                if(estimatedTime <= 0) {
                    estimatedTime = DEFAULT_CAL_TIME;
                }
                if (preferences.getBoolean(R.string.p_end_at_deadline, true)) {
                    values.put(CalendarContract.Events.DTSTART, dueDate);
                    values.put(CalendarContract.Events.DTEND, dueDate + estimatedTime);
                }else{
                    values.put(CalendarContract.Events.DTSTART, dueDate - estimatedTime);
                    values.put(CalendarContract.Events.DTEND, dueDate);
                }
                // setting a duetime to a previously timeless event requires explicitly setting allDay=0
                values.put(CalendarContract.Events.ALL_DAY, "0");
                values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            } else {
                values.put(CalendarContract.Events.DTSTART, tzCorrectedDueDate);
                values.put(CalendarContract.Events.DTEND, tzCorrectedDueDate);
                values.put(CalendarContract.Events.ALL_DAY, "1");
            }
        } else {
            values.put(CalendarContract.Events.DTSTART, tzCorrectedDueDateNow);
            values.put(CalendarContract.Events.DTEND, tzCorrectedDueDateNow);
            values.put(CalendarContract.Events.ALL_DAY, "1");
        }
        if ("1".equals(values.get(CalendarContract.Events.ALL_DAY))) {
            values.put(CalendarContract.Events.EVENT_TIMEZONE, Time.TIMEZONE_UTC);
        } else {
            values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        }
    }

    public AndroidCalendar getCalendar(String id) {
        ContentResolver cr = context.getContentResolver();

        Cursor c = cr.query(CalendarContract.Calendars.CONTENT_URI, Calendars.CALENDARS_PROJECTION,
                Calendars.CALENDARS_WHERE + " AND Calendars._id=" + id, null, Calendars.CALENDARS_SORT);
        try {
            if (c.moveToFirst()) {
                int nameColumn = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
                String name = c.getString(nameColumn);
                return new AndroidCalendar(id, name);
            }
        } finally {
            if(c != null) {
                c.close();
            }
        }

        return null;
    }

    /**
     * Appends all user-modifiable calendars to listPreference.
     */
    public List<AndroidCalendar> getCalendars() {
        ContentResolver cr = context.getContentResolver();

        Cursor c = cr.query(CalendarContract.Calendars.CONTENT_URI, Calendars.CALENDARS_PROJECTION,
                Calendars.CALENDARS_WHERE, null, Calendars.CALENDARS_SORT);
        try {
            List<AndroidCalendar> calendars = new ArrayList<>();

            if (c == null || c.getCount() == 0) {
                // Something went wrong when querying calendars. Only offer them
                // the system default choice
                return calendars;
            }

            int idColumn = c.getColumnIndex(_ID);
            int nameColumn = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
            while (c.moveToNext()) {
                String id = c.getString(idColumn);
                String name = c.getString(nameColumn);
                calendars.add(new AndroidCalendar(id, name));
            }
            return calendars;
        } finally {
            if(c != null) {
                c.close();
            }
        }
    }
}
