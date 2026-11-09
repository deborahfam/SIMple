package com.simple.mobile;

import android.app.Application;
import com.simple.mobile.data.telephony.UssdDispatcher;
import java.util.concurrent.Executors;

public final class SimpleApplication extends Application {
    private UssdDispatcher dispatcher;
    @Override public void onCreate() {
        super.onCreate();
        dispatcher = new UssdDispatcher(this, Executors.newSingleThreadExecutor());
    }
    public UssdDispatcher dispatcher() { return dispatcher; }
}
