package org.mythtv.lfmobile.data;

import java.util.ArrayList;
import java.util.HashMap;

// Singleton class to cache frequently used backend data
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
    public boolean wsdlDone;
    public boolean initDone;
    // from GetHostName
    public String sHostName;
    // Authorization token
    public String authorization;
    public boolean loginNeeded;
    static private String TAG = "lfm";
    static private String CLASS = "BackendCache";


    private BackendCache() {
        init();
    }

    private void init() {
        sBackendIP = Settings.getString("pref_backend");
        sBackendIP = XmlNode.fixIpAddress((sBackendIP));
        sMainPort = Settings.getString("pref_http_port");
        sHostMap = new HashMap<>();
        wsdlDone = false;
        getWsdl();
    }

    public void getWsdl() {
        AsyncBackendCall call = new AsyncBackendCall( this);
        call.mainThread = false;
        call.execute(Action.DVR_WSDL, Action.BACKEND_INFO, Action.GET_HOSTNAME);
        wsdlDone = true;
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
    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        if (taskRunner == null)
            return;
        int [] tasks = taskRunner.getTasks();
        ArrayList<XmlNode> resultsList = taskRunner.getXmlResults();
        XmlNode xml = taskRunner.getXmlResult();
        switch (tasks[0]) {
            case Action.DVR_WSDL:
                wsdlDone = false;
                canUpdateRecGroup = false;
                canForgetHistory = false;
                if (xml == null)
                    break;
                XmlNode schemaNode = xml.getNode(new String[]{"types", "schema"}, 1);
                XmlNode parameterNode;
                if (schemaNode != null) {
                    wsdlDone = true;
                    // Check if the UpdateRecordedMetadata method takes the RecGroup parameter
                    parameterNode = schemaNode.getNode
                            (new String[]{"UpdateRecordedMetadata", "complexType", "sequence", "RecGroup"}, 0);
                    if (parameterNode != null)
                        canUpdateRecGroup = true;
                    // Check if AllowReRecord supports Forget History
                    parameterNode = null;
                    if (schemaNode != null)
                        parameterNode = schemaNode.getNode
                                (new String[]{"AllowReRecord", "complexType", "sequence", "ChanId"}, 0);
                    if (parameterNode != null)
                        canForgetHistory = true;
                }
                xml = resultsList.get(2);
                if (xml == null)
                    break;
                sHostName = xml.getString();
                if (sHostName != null && sBackendIP != null && sMainPort != null)
                    sHostMap.put(sHostName, sBackendIP + ":" + sMainPort);
                initDone = true;
                break;
        }
    }

}
