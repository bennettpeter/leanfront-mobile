package org.mythtv.mobfront;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.Menu;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.appcompat.app.AppCompatActivity;

import org.mythtv.mobfront.data.BackendCache;
import org.mythtv.mobfront.data.Settings;
import org.mythtv.mobfront.data.XmlNode;
import org.mythtv.mobfront.databinding.ActivityMainBinding;
import org.mythtv.mobfront.ui.settings.SettingsFragment;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private NavController navController;
    private ScheduledExecutorService executor = null;
    private MythTask mythTask = new MythTask();
    private static int TASK_INTERVAL = 240;
    static final String TAG = "mfe";
    static final String CLASS = "MainActivity";
    static private MainActivity instance;
    private static boolean mWasInBackground = true;

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        instance = this;
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        assert navHostFragment != null;
        navController = navHostFragment.getNavController();

        NavigationView navigationView = binding.navView;
        if (navigationView != null) {
            mAppBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_videolist, R.id.nav_settings)
                    .setOpenableLayout(binding.drawerLayout)
                    .build();
            NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
            NavigationUI.setupWithNavController(navigationView, navController);
        }

        BottomNavigationView bottomNavigationView = binding.appBarMain.contentMain.bottomNavView;
        if (bottomNavigationView != null) {
            mAppBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_videolist)
                    .build();
            NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
            NavigationUI.setupWithNavController(bottomNavigationView, navController);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        // Using findViewById because NavigationView exists in different layout files
        // between w600dp and w1240dp
        NavigationView navView = findViewById(R.id.nav_view);
        // The navigation drawer already has the items including the items in the overflow menu
        // We only inflate the overflow menu if the navigation drawer isn't visible
        getMenuInflater().inflate(R.menu.overflow, menu);
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.nav_settings) {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
            navController.navigate(R.id.nav_settings);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    public void restartMythTask() {
        if (executor != null)
            executor.shutdown();
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleWithFixedDelay(mythTask, 0, TASK_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String backendIP = Settings.getString("pref_backend");
        backendIP = XmlNode.fixIpAddress(backendIP);
        if (backendIP.length() == 0) {
            navController.navigate(R.id.nav_settings);
        }
        if (mWasInBackground || executor == null)
            restartMythTask();
        mWasInBackground = false;
    }

    private class MythTask implements Runnable {
        boolean mVersionMessageShown = false;

        @Override
        public synchronized void run() {
            try {
                boolean loginTried = false;
                boolean loginNeededNow = false;
                boolean connection = false;
                boolean connectionfail = false;
                String backendIP = Settings.getString("pref_backend");
                backendIP = XmlNode.fixIpAddress(backendIP);
                if (backendIP.length() == 0)
                    return;
                while (!connection) {
                    if (SettingsFragment.isActive) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }
                    if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState()
                            == Lifecycle.State.CREATED) {
                        // process is now in the background
                        mWasInBackground = true;
                        if (executor != null)
                            executor.shutdown();
                        executor = null;
                    }
                    if (executor == null)
                        return;

                    int toastMsg = 0;
                    if (loginNeededNow) {
                        BackendCache.getInstance().loginNeeded = true;
                        try {
                            String result = null;
                            StringBuilder urlBuilder = new StringBuilder
                                    (XmlNode.mythApiUrl(null,
                                            "/Myth/LoginUser"))
                                    .append("?UserName=")
                                    .append(URLEncoder.encode(Settings.getString("pref_backend_userid").trim(), "UTF-8"))
                                    .append("&Password=")
                                    .append(URLEncoder.encode(Settings.getString("pref_backend_passwd").trim(), "UTF-8"));
                            XmlNode loginXml = XmlNode.fetch(urlBuilder.toString(), "POST");
                            result = loginXml.getString();
                            connection = true;
                            if (result.length() == 0) {
                                Log.e(TAG, CLASS + " MythTask empty response from LoginUser");
                                BackendCache.getInstance().authorization = null;
                            } else
                                BackendCache.getInstance().authorization = result;
                        } catch (Exception e) {
                            Log.e(TAG, CLASS + " Exception in LoginUser.", e);
                            BackendCache.getInstance().authorization = null;
                        }
                        loginTried = true;
                    }

                    try {
                        String result = null;
                        String url = XmlNode.mythApiUrl(null,
                                "/Myth/DelayShutdown");
                        if (url == null)
                            return;
                        XmlNode bkmrkData = XmlNode.fetch(url, "POST");
                        result = bkmrkData.getString();
                        connection = true;
                        if (!"true".equals(result))
                            Log.e(TAG, CLASS + " MythTask Incorrect response from DelayShutdown: " + result);
                    } catch (FileNotFoundException | XmlPullParserException ex) {
                        if (!mVersionMessageShown) {
                            toastMsg = R.string.msg_no_delayshutdown;
                            mVersionMessageShown = true;
                        }
                        connection = true;
                        Log.e(TAG, CLASS + " MythTask DelayShutdown Exception ", ex);
                    } catch (IOException e) {
                        if ("Unauthorized: 401".equals(e.getMessage())) {
                            if (Settings.getString("pref_backend_userid").length() == 0
                                    || Settings.getString("pref_backend_passwd").length() == 0
                                    || loginTried) {
                                BackendCache.getInstance().loginNeeded = true;
                                toastMsg = R.string.msg_backend_login_req;
                                MainActivity.this.runOnUiThread(() -> {
                                    navController.navigate(R.id.nav_settings);
                                });
                            } else
                                loginNeededNow = true;
                        } else
                            toastMsg = R.string.msg_no_connection;
                        connectionfail = true;
                    }
                    if (connectionfail)
                        if (wakeBackend())
                            toastMsg = R.string.msg_wake_backend;

                    if (toastMsg != 0) {
                        final int msg = toastMsg;
                        MainActivity.this.runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this,
                                msg, Toast.LENGTH_LONG)
                               .show();
                        });
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG, CLASS + " MythTask Exception ", ex);
            }
        }

        public boolean wakeBackend() {
            Context context = MyApplication.getAppContext();
            if (context == null)
                return false;
            String backendMac = Settings.getString("pref_backend_mac");
            if (backendMac.length() == 0)
                return false;

            // The magic packet is a broadcast frame containing anywhere within its payload
            // 6 bytes of all 255 (FF FF FF FF FF FF in hexadecimal), followed by sixteen
            // repetitions of the target computer's 48-bit MAC address, for a total of 102 bytes.

            byte[] msg = new byte[102];
            int ix;
            for (ix = 0; ix < 6; ix++)
                msg[ix] = (byte) 0xff;

            int msglen = 6;
            String[] tokens = backendMac.split(":");
            byte[] macaddr = new byte[6];

            if (tokens.length != 6) {
                Log.e(TAG, CLASS + " wakeBackend WakeOnLan(" + backendMac + "): Incorrect MAC length");
                return false;
            }

            for (int y = 0; y < 6; y++) {
                try {
                    macaddr[y] = (byte) Integer.parseInt(tokens[y], 16);
                } catch (NumberFormatException e) {
                    Log.e(TAG, CLASS + " wakeBackend WakeOnLan(" + backendMac + "): Invalid MAC address");
                    return false;
                }

            }

            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 6; y++)
                    msg[msglen++] = macaddr[y];

            Log.i(TAG, CLASS + " wakeBackend WakeOnLan(): Sending WOL packet to " + backendMac);

            try {
                DatagramPacket DpSend = new DatagramPacket(msg, msg.length, InetAddress.getByName("255.255.255.255"), 9);
                DatagramSocket ds = new DatagramSocket();
                ds.send(DpSend);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

    }
}