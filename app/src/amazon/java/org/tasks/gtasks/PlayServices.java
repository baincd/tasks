package org.tasks.gtasks;

import android.app.Activity;
import javax.inject.Inject;
import org.tasks.drive.DriveLoginActivity;
import org.tasks.play.AuthResultHandler;

public class PlayServices {

  @Inject
  public PlayServices() {}

  public boolean isPlayServicesAvailable() {
    return false;
  }

  public boolean refreshAndCheck() {
    return false;
  }

  public void resolve(Activity activity) {}

  public String getStatus() {
    return null;
  }

  public void getDriveAuthToken(
      DriveLoginActivity driveLoginActivity, String a, AuthResultHandler authResultHandler) {}
}
