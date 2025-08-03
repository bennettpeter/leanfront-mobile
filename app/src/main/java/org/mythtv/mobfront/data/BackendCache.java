package org.mythtv.mobfront.data;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

// Singleton class to cache frequently used backend data
public class BackendCache  {
    private static BackendCache singleton;
    // Values from settings
    public String sBackendIP;
    public String sMainPort;

    // Values from wsdl
    public boolean canUpdateRecGroup;
    public boolean canForgetHistory;

    // Value from AsyncBackendCall
    public long mTimeAdjustment = 0;
    public int mythTvVersion = 0;
    // This flag will be set true during refresh if it is found that we are on a
    // backend that supports the LastPlayPos APIs (V32 or later).
    public boolean supportLastPlayPos = true;

    // Values from XmlNode
    public HashMap<String, String> sHostMap;
    public boolean isConnected;
//    public boolean wsdlDone;

    // from GetHostName
    public String sHostName;
    // Authorization token
    public String authorization;
    public boolean loginNeeded;

    private BackendCache() {
        init();
    }

    private void init() {
        sBackendIP = Settings.getString("pref_backend");
        sBackendIP = XmlNode.fixIpAddress((sBackendIP));
        sMainPort = Settings.getString("pref_http_port");
        sHostMap = new HashMap<>();
    }


    synchronized public static BackendCache getInstance() {
        if (singleton == null)
            singleton = new BackendCache();
        return singleton;
    }

    public static void flush() {
        if (singleton != null)
            singleton = null;
    }

}
