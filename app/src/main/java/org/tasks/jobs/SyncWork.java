package org.tasks.jobs;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;
import javax.inject.Inject;
import org.tasks.LocalBroadcastManager;
import org.tasks.caldav.CaldavSynchronizer;
import org.tasks.gtasks.GoogleTaskSynchronizer;
import org.tasks.injection.InjectingWorker;
import org.tasks.injection.JobComponent;
import org.tasks.preferences.Preferences;
import timber.log.Timber;

public class SyncWork extends InjectingWorker {

  private static final Object LOCK = new Object();

  @Inject CaldavSynchronizer caldavSynchronizer;
  @Inject GoogleTaskSynchronizer googleTaskSynchronizer;
  @Inject LocalBroadcastManager localBroadcastManager;
  @Inject Preferences preferences;

  public SyncWork(@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
  }

  @NonNull
  @Override
  public Result run() {
    synchronized (LOCK) {
      if (preferences.isSyncOngoing()) {
        return Result.RETRY;
      }
    }

    preferences.setSyncOngoing(true);
    localBroadcastManager.broadcastRefresh();
    try {
      caldavSynchronizer.sync();
      googleTaskSynchronizer.sync();
    } catch (Exception e) {
      Timber.e(e);
    } finally {
      preferences.setSyncOngoing(false);
      localBroadcastManager.broadcastRefresh();
    }
    return Result.SUCCESS;
  }

  @Override
  protected void inject(JobComponent component) {
    component.inject(this);
  }
}
