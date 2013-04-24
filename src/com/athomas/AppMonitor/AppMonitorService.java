package com.athomas.AppMonitor;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import com.athomas.AppMonitor.event.RemoveFileEvent;
import com.athomas.AppMonitor.event.StopServiceEvent;
import com.squareup.otto.Subscribe;

import java.text.SimpleDateFormat;
import java.util.Date;


public class AppMonitorService extends Service {

    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_SAVE_VALUES = "save_values";

    private final static String TAG = AppMonitorService.class.getSimpleName();
    private final static int DELAY = 2500;

    private Handler handler = new Handler();
    private FileManager fileManager = new FileManager();

    private WindowManager windowManager;
    private TextView resultView;

    private String packageName;
    private boolean saveValues;

    private Runnable repetitiveTask = new Runnable() {
        @Override
        public void run() {
            String result = new StringBuilder()
                    .append("launched every " + DELAY / 1000 +"s")
                    .append("\n")
                    .append(Cmd.$().cpuinfo().lines(4).exec())
                    .append("Battery")
                    .append(Cmd.$().battery().grep("level").exec())
                    .append("Native size: ")
                    .append(Cmd.$().meminfo(packageName).grep("Native").regexp("([0-9]+)", 4).exec())
                    .append(" - alloc: ")
                    .append(Cmd.$().meminfo(packageName).grep("Native").regexp("([0-9]+)", 5).exec())
                    .append("\n")
                    .append("Dalvik size: ")
                    .append(Cmd.$().meminfo(packageName).grep("Dalvik").regexp("([0-9]+)", 4).exec())
                    .append(" - alloc: ")
                    .append(Cmd.$().meminfo(packageName).grep("Dalvik").regexp("([0-9]+)", 5).exec())
                    .append("\n")
                    .append("Activities: ")
                    .append(Cmd.$().meminfo(packageName).grep("Activities").regexp("Activities:.*([0-9]+)").exec())
                    .append(" - Views: ")
                    .append(Cmd.$().meminfo(packageName).grep("Views").regexp("Views:[ ]+([0-9]+)").exec())
                    .toString();
            resultView.setText(result);

            // save the values by storing them on the sdcard
            if (saveValues) {
                String cpu = Cmd.$().cpuinfo().grep(packageName).regexp("([\\d.]*)%").exec();
                String battery = Cmd.$().battery().grep("level").regexp("level: ([\\d.]*)").exec();
                String currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());

                fileManager.append(currentTime + "/" + cpu + "/" + battery);
            }

            // get the current time
            handler.postDelayed(this, DELAY);
        }
    };

    @Subscribe
    public void handleStopService(StopServiceEvent event) {
        stopSelf();
    }

    @Subscribe
    public void handleRemoveFile(RemoveFileEvent event) {
        fileManager.removeFile();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        saveValues = intent.getBooleanExtra(EXTRA_SAVE_VALUES, false);

        handler.post(repetitiveTask);
        return Service.START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // add view on top of other apps
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        resultView = new TextView(this);
        resultView.setBackgroundColor(getResources().getColor(R.color.background_color));
        resultView.setTextSize(6);
        resultView.setTextColor(Color.CYAN);
        resultView.setPadding(6, 6, 6, 6);

        resultView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.LEFT;

        windowManager.addView(resultView, params);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        if(resultView != null) {
            windowManager.removeView(resultView);
        }

    }

    public IBinder onBind(Intent intent) {
        return null;
    }

}
