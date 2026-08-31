package org.mythtv.lfmobile.data;

import android.content.SharedPreferences;

import org.mythtv.lfmobile.MyApplication;
import org.mythtv.lfmobile.R;

import java.util.ArrayList;
import java.util.HashMap;

// Singleton class to cache frequently used backend data
@SuppressWarnings("SpellCheckingInspection")
public class BackendCache implements AsyncBackendCall.OnBackendCallListener {
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
    // from GetHostName
    public String sHostName;
    // Authorization token
    public String authorization;
    public boolean loginNeeded;
    private static final String demoName = MyApplication.getAppContext().getString(R.string.demo_name);


    private BackendCache() {
        init();
    }

    public synchronized void init() {
        sBackendIP = Settings.getString("pref_backend");
        sBackendIP = fixIpAddress((sBackendIP));
        sMainPort = Settings.getString("pref_http_port");
        sHostMap = new HashMap<>();
        getWsdl();
    }

    private void getWsdl() {
        AsyncBackendCall call = new AsyncBackendCall( this);
        call.execute(Action.DVR_WSDL, Action.BACKEND_INFO, Action.GET_HOSTNAME);
    }

    synchronized public static BackendCache getInstance() {
        if (singleton == null)
            singleton = new BackendCache();
        return singleton;
    }

    public static void flush() {
        if (singleton != null)
            singleton.init();
    }

    public String fixIpAddress(String ipAddress) {
        if (ipAddress != null) {
            ipAddress = ipAddress.replace(" ","");
            if (ipAddress.indexOf(':') > -1 && ipAddress.charAt(0)!= '[')
                ipAddress = "[" + ipAddress + "]";
            if (ipAddress.equals(demoName)) {
                ipAddress =  MyApplication.getAppContext().getString(R.string.demo_ip);
                SharedPreferences.Editor editor = Settings.getEditor();
                Settings.putString(editor,"pref_backend_userid", MyApplication.getAppContext().getString(R.string.demo_user));
                Settings.putString(editor,"pref_backend_passwd", MyApplication.getAppContext().getString(R.string.demo_pswd));
                editor.commit();
            }
        }
        return ipAddress;
    }

    @Override
    public synchronized void onPostExecute(AsyncBackendCall taskRunner) {
        if (taskRunner == null)
            return;
        int [] tasks = taskRunner.getTasks();
        ArrayList<XmlNode> resultsList = taskRunner.getXmlResults();
        XmlNode xml = taskRunner.getXmlResult();
        if (tasks[0] == Action.DVR_WSDL) {
            canUpdateRecGroup = false;
            canForgetHistory = false;
            if (xml == null)
                return;
            XmlNode schemaNode = xml.getNode(new String[]{"types", "schema"}, 1);
            XmlNode parameterNode;
            if (schemaNode != null) {
                // Check if the UpdateRecordedMetadata method takes the RecGroup parameter
                parameterNode = schemaNode.getNode
                        (new String[]{"UpdateRecordedMetadata", "complexType", "sequence", "RecGroup"}, 0);
                if (parameterNode != null)
                    canUpdateRecGroup = true;
                // Check if AllowReRecord supports Forget History
                parameterNode = schemaNode.getNode
                        (new String[]{"AllowReRecord", "complexType", "sequence", "ChanId"}, 0);
                if (parameterNode != null)
                    canForgetHistory = true;
            }
            xml = resultsList.get(2);
            if (xml == null)
                return;
            sHostName = xml.getString();
            if (sHostName != null && sBackendIP != null && sMainPort != null)
                sHostMap.put(sHostName, sBackendIP + ":" + sMainPort);
        }
    }
}
