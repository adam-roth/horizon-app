package au.com.suncoastpc.horizonsync.activity.base;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import au.com.suncoastpc.horizonsync.utils.Environment;


public abstract class EnvironmentAwareActivity extends AppCompatActivity {
    protected boolean launchAborted = false;

    //XXX:  subclasses should override this as appropriate
    protected boolean requiresAuthenticatedUser() {
        return false;
    }

    //XXX:  subclasses should implement this to specify the activity to use to handle login/authentication
    protected abstract Class<? extends Activity> getLoginActivity();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Environment.getApplicationContext() == null) {
            Environment.setApplicationContext(getApplicationContext());
        }
    }

    protected boolean isLaunchAborted() {
        return launchAborted;
    }
}
