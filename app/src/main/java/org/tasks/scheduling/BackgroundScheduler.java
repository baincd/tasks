package org.tasks.scheduling;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.data.Task;
import javax.inject.Inject;
import org.tasks.files.FileHelper;
import org.tasks.injection.ForApplication;
import org.tasks.injection.InjectingJobIntentService;
import org.tasks.injection.ServiceComponent;
import org.tasks.jobs.WorkManager;
import org.tasks.preferences.Preferences;
import timber.log.Timber;

public class BackgroundScheduler extends InjectingJobIntentService {

  @Inject @ForApplication Context context;
  @Inject TaskDao taskDao;
  @Inject WorkManager jobManager;
  @Inject RefreshScheduler refreshScheduler;
  @Inject Preferences preferences;

  public static void enqueueWork(Context context) {
    BackgroundScheduler.enqueueWork(
        context,
        BackgroundScheduler.class,
        InjectingJobIntentService.JOB_ID_BACKGROUND_SCHEDULER,
        new Intent(context, BackgroundScheduler.class));
  }

  @Override
  protected void doWork(@NonNull Intent intent) {
    Timber.d("onHandleWork(%s)", intent);

    NotificationSchedulerIntentService.enqueueWork(context, false);
    CalendarNotificationIntentService.enqueueWork(context);
    GeofenceSchedulingIntentService.enqueueWork(context);

    for (Task task : taskDao.needsRefresh()) {
      refreshScheduler.scheduleRefresh(task);
    }

    FileHelper.delete(context, preferences.getCacheDirectory());
  }

  @Override
  protected void inject(ServiceComponent component) {
    component.inject(this);
  }
}
