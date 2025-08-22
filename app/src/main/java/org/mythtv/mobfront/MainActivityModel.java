package org.mythtv.mobfront;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.lifecycle.ViewModel;

import org.mythtv.mobfront.data.BackendCache;
import org.mythtv.mobfront.data.Settings;
import org.mythtv.mobfront.data.XmlNode;
import org.mythtv.mobfront.ui.playback.PlaybackViewModel;
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

public class MainActivityModel extends ViewModel {

    private MythTask mythTask = new MythTask();
    private static int TASK_INTERVAL = 240;
    private ScheduledExecutorService executor = null;
    private static boolean mWasInBackground = true;
    static final String TAG = "mfe";
    static final String CLASS = "MainActivityModel";
    final MutableLiveData<Integer> navigate = new MutableLiveData<>();
    final MutableLiveData<Integer> toast = new MutableLiveData<>();
    static MainActivityModel instance;
    long lastRestartTime;

    public static MainActivityModel getInstance() {
        return instance;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        instance = null;
    }

    public void startMythTask() {
        if (executor == null)
            restartMythTask();
    }
    public synchronized void restartMythTask() {
        long now = System.currentTimeMillis();
        if (now - lastRestartTime < 2000)
            return;
        lastRestartTime = now;
        if (executor != null)
            executor.shutdown();
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleWithFixedDelay(mythTask, 0, TASK_INTERVAL, TimeUnit.SECONDS);
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
//                                MainActivity.this.runOnUiThread(() -> {
//                                    navController.navigate(R.id.nav_settings);
//                                });
                                navigate.postValue(R.id.nav_settings);
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
//                        final int msg = toastMsg;
//                        MainActivity.this.runOnUiThread(() -> {
//                            Toast.makeText(MainActivity.this,
//                                            msg, Toast.LENGTH_LONG)
//                                    .show();
//                        });
                        toast.postValue(toastMsg);
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
