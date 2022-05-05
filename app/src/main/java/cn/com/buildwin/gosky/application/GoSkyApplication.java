package cn.com.buildwin.gosky.application;

import android.app.Application;

public class GoSkyApplication extends Application {
    private static GoSkyApplication sMyApplication = null;
    @Override public void onCreate() {
        sMyApplication = this;
        super.onCreate();
    }

    public static synchronized GoSkyApplication getApplication() {
        return sMyApplication;
    }
}
