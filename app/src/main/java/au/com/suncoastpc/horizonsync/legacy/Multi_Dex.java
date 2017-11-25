package au.com.suncoastpc.horizonsync.legacy;

import android.app.Application;
import android.content.Context;
import android.support.multidex.MultiDex;

/**
 * Created by aroth on 11/25/2017.
 */
//see:  https://stackoverflow.com/questions/44603154/how-to-fix-android-4-4-2-error-classnotfoundexception-didnt-find-class-com-go
public class Multi_Dex  extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
