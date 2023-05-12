package ch.mypi.noad.service;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleService;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ch.mypi.noad.installer.AssetManager;
import ch.mypi.noad.process.ProcessRunner;


public class NoadService extends LifecycleService {
    public static final String NODEJS_EXE = "lib-node-noad.so";

    private static final String ASSET_NAME = "demo-js";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String TAG = "NoadService";
    private final IBinder binder;
    private ProcessRunner processRunner;
    private AssetManager assetManager;

    public NoadService() {
        super();

        // other stuff
        binder = new ServiceBinder();
    }

    @Override
    public void onCreate() {
        var mContext = getApplicationContext();
        var rootFilesDir = mContext.getFilesDir();

        var sourceFileZip = new File(mContext.getApplicationInfo().sourceDir);

        var srvAppPath = Paths.get(rootFilesDir.getAbsolutePath(), ASSET_NAME).toFile();
        File nativeLibsDir = new File(mContext.getApplicationInfo().nativeLibraryDir);

        // service ports
        var srvServicePort = 3000;

        // SRV
        var srvServerEnv = new HashMap<String, String>();
        srvServerEnv.put("PORT", "" + srvServicePort);

        processRunner = new ProcessRunner(nativeLibsDir, srvServerEnv, "./" + NODEJS_EXE,
                srvAppPath + "/index.js");

        processRunner.setStdoutHandler(log -> Log.i("Runner-stdout", log));
        processRunner.setStderrHandler(log -> Log.w("Runner-stderr", log));
        assetManager = new AssetManager("v1", rootFilesDir, sourceFileZip);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        this.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }


    public void stop() {
        if (processRunner != null && processRunner.isAlive()) {
            processRunner.stop(2, TimeUnit.SECONDS);
        } else {
            Log.w(TAG, "PROCESS_RUNNER_ALREADY_STOPPED");
        }

    }

    public void start() {
        if (processRunner == null) {
            throw new IllegalStateException("Something went wrong in the world. Got start before being created...");
        }
        if (!processRunner.isAlive()) {
            CompletableFuture.runAsync(processRunner, executor);
        } else {
            Log.w(TAG, "PROCESS_RUNNER_ALREADY_RUNNING");
        }
    }

    public void prepare() {
        if (assetManager != null) {
            // ATTENTION: don't do it like this (blocking the main thread), use it's own thread or a background job.
            assetManager.install(ASSET_NAME);
        }
    }

    public void clear() {
        if (assetManager != null) {
            // ATTENTION: don't do it like this (blocking the main thread), use it's own thread or a background job.
            assetManager.uninstall(ASSET_NAME);
        }
    }

    public class ServiceBinder extends Binder {
        public NoadService getService() {
            // Return this instance of NodeService so clients can call public methods
            return NoadService.this;
        }
    }
}
