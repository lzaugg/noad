package ch.mypi.noad;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import ch.mypi.noad.databinding.ActivityMainBinding;
import ch.mypi.noad.service.NoadService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MAIN";

    private ActivityMainBinding binding;
    NoadService mService;
    boolean mBound = false;

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.i(TAG, "onServiceConnected");
            NoadService.ServiceBinder binder = (NoadService.ServiceBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.i(TAG, "onServiceDisconnected");
            mBound = false;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(getPackageName(), "Destroying Activity");
        if (mBound && mService != null) {
            doUnbindService();
        }
    }

    public void doUnbindService() {
        unbindService(serviceConnection);
        mBound = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_webview)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        Toolbar t = findViewById(R.id.lifecycle_bar);

        var menuClickListener = new Toolbar.OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (R.id.menu_prepare == item.getItemId()) {
                    Log.i(TAG, "ACTION_PREPARE");
                    prepare();
                } else if (R.id.menu_start == item.getItemId()) {
                    Log.i(TAG, "ACTION_START");
                    start();
                } else if (R.id.menu_stop == item.getItemId()) {
                    Log.i(TAG, "ACTION_STOP");
                    stop();
                } else if (R.id.menu_clear == item.getItemId()) {
                    Log.i(TAG, "ACTION_CLEAR");
                    clear();
                } else {
                    Log.i(TAG, "ACTION_UNKNOWN");
                    return false;
                }
                return true;
            }
        };
        t.setOnMenuItemClickListener(menuClickListener);



    }

    private void start() {
        if (mBound) {
            mService.start();
        }
    }

    private void stop() {
        if (mBound) {
            mService.stop();
        }
    }

    private void prepare() {
        if (mBound) {
            mService.prepare();
        }
    }

    private void clear() {
        if (mBound) {
            mService.clear();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
        // Bind to LocalService.
        var mContext = getApplicationContext();
        Intent service = new Intent(mContext, NoadService.class);
        mContext.startService(service);
        mContext.bindService(service, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
        //mService.stop();
    }

}