package org.mythtv.lfmobile.data;

import android.annotation.SuppressLint;
import android.os.Looper;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import org.mythtv.lfmobile.ui.videolist.VideoListModel;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncBackendCall implements Runnable {

    public interface OnBackendCallListener {
        void onPostExecute(AsyncBackendCall taskRunner);
    }

    public ArrayList<Video> videos = new ArrayList<>();
    // Class of params and response are dependent on the call
    public Object params;
    public Object response;
    // Specifies whether the return must be on the main thread.
    public boolean mainThread = true;
    public int id;
    private ArrayList<XmlNode> xmlResults = new ArrayList<>();
    private Integer[] inTasks;
    private int[] tasks;
    private OnBackendCallListener listener;
    private final static ExecutorService executor = Executors.newCachedThreadPool();
    private static final String TAG = "lfm";
    final String CLASS = "AsyncBackendCall";

    public AsyncBackendCall(@Nullable OnBackendCallListener listener) {
        this.listener = listener;
    }


    public int[] getTasks() {
        return tasks;
    }

    public ArrayList<XmlNode> getXmlResults() {
        return xmlResults;
    }

    public XmlNode getXmlResult() {
        if (xmlResults.size() > 0)
            return xmlResults.get(0);
        else
            return null;
    }

    public void execute(Integer... tasks) {
        this.inTasks = tasks;
        executor.submit(this);
    }


    @Override
    public void run() {
        if (!XmlNode.isSetupDone())
            return;
        try {
            if (XmlNode.getIpAndPort(null) == null)
                return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return;
        }
        try {
            runTasks();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (listener != null) {
                if (mainThread) {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(() -> listener.onPostExecute(this));
                }
                else
                    listener.onPostExecute(this);
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private void runTasks() {
       tasks = new int[inTasks.length];
        int videoIndex = 0;
        int taskIndex = -1;
        Video video = null;

        for (; ; ) {
            boolean doGetOnly = false;
            boolean allowRerecord = false;
            // If there is a rowAdapter, take each video in the adapter and run
            // all tasks on it.
            taskIndex++;
            if (taskIndex >= tasks.length) {
                if (videos.size() == 0)
                    break;
                taskIndex = 0;
                videoIndex++;
            }
            if (videos.size() > 0) {
                if (videoIndex >= videos.size())
                    break;
                video = videos.get(videoIndex);
            }
            boolean isRecording = false;
            if (video != null)
                isRecording = (video.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING);

            int task = inTasks[taskIndex];
            tasks[taskIndex] = task;
            XmlNode xmlResult = null;
            boolean watched = false;
            switch (task) {
                case Action.GET_BOOKMARK:
                    //Response is long[2], bookmark and posbookmark
                    try {
                        response = fetchLastPlayPos(video);
                    } catch (IOException | XmlPullParserException e) {
                        response = new long[]{-1,-1};
                        e.printStackTrace();
                    }
                    break;
                case Action.REMOVE_BOOKMARK:
                case Action.REMOVE_LASTPLAYPOS:
                    params = new long[] {0,0};
                case Action.SET_BOOKMARK:
                case Action.SET_LASTPLAYPOS:
                    //params is long[2], bookmark and posbookmark
                    //Result in xmlResult
                    try {
                        xmlResult = updateLastPlayPos(video, (long[]) params);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Action.GET_STREAM_INFO:
                    try {
                        String urlString = XmlNode.mythApiUrl(video.hostname,
                                "/Video/GetStreamInfo?StorageGroup="
                                    + video.storageGroup
                                    + "&FileName="
                                    + URLEncoder.encode(video.filename, "UTF-8"));
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Action.SET_WATCHED:
                    watched = true;
                case Action.SET_UNWATCHED:
                    // This handles both set watched and set unwatched,
                    try {
                        int type;
                        String urlString;
                        if (isRecording) {
                            // set recording watched
                            urlString = XmlNode.mythApiUrl(video.hostname,
                                    "/Dvr/UpdateRecordedWatchedStatus?RecordedId="
                                            + video.recordedid + "&Watched=" + watched);
                            type = VideoContract.VideoEntry.RECTYPE_RECORDING;
                        }
                        else {
                            // set video watched
                            urlString = XmlNode.mythApiUrl(video.hostname,
                                    "/Video/UpdateVideoWatchedStatus?Id="
                                            + video.recordedid + "&Watched=" + watched);
                            type = VideoContract.VideoEntry.RECTYPE_VIDEO;
                        }
                        xmlResult = XmlNode.fetch(urlString, "POST");
                        VideoListModel.getInstance().startFetch(type, video.recordedid, null);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Action.DELETE_AND_RERECORD:
                    allowRerecord = true;
                case Action.DELETE:
                    // Delete recording
                    // If already deleted do not delete again.
                    if (!isRecording || "Deleted".equals(video.recGroup))
                        break;
                    try {
                        String urlString = XmlNode.mythApiUrl(null,
                                "/Dvr/GetRecorded?RecordedId="
                                        + video.recordedid);
                        xmlResult = XmlNode.fetch(urlString, null);
                        String recGroup = xmlResult.getString(new String[] {"Recording","RecGroup"});
                        if (recGroup == null || "Deleted".equals(recGroup))
                            break;
                        urlString = XmlNode.mythApiUrl(video.hostname,
                                "/Dvr/DeleteRecording?RecordedId="
                                        + video.recordedid
                                        + "&AllowRerecord=" + allowRerecord);
                        xmlResult = XmlNode.fetch(urlString, "POST");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                        VideoListModel.getInstance().startFetch(VideoContract.VideoEntry.RECTYPE_RECORDING,
                                video.recordedid, null);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Action.UNDELETE:
                    // UnDelete recording
                    if (!isRecording)
                        break;
                    try {
                        String urlString = XmlNode.mythApiUrl(video.hostname,
                                "/Dvr/UnDeleteRecording?RecordedId="
                                        + video.recordedid);
                        xmlResult = XmlNode.fetch(urlString, "POST");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                        VideoListModel.getInstance().startFetch(VideoContract.VideoEntry.RECTYPE_RECORDING,
                                video.recordedid, null);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Action.ALLOW_RERECORD:
                    if (!isRecording)
                        break;
                    try {
                        String urlString = XmlNode.mythApiUrl(video.hostname,
                                "/Dvr/AllowReRecord?RecordedId="
                                        + video.recordedid);
                        xmlResult = XmlNode.fetch(urlString, "POST");
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Action.COMMBREAK_LOAD: {
                    // params is commBreakTable
                    CommBreakTable commBreakTable = (CommBreakTable) params;
                    if (commBreakTable.entries.length > 0)
                        break;
                    String urlMethod;
                    if (isRecording)
                        urlMethod = "/Dvr/GetRecordedCommBreak?RecordedId=";
                    else
                        urlMethod = "/Video/GetVideoCommBreak?Id=";
                    try {
                        String urlString = XmlNode.mythApiUrl(null,
                                urlMethod
                                        + video.recordedid
                                        + "&OffsetType=Duration&IncludeFps=true");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (IOException | XmlPullParserException e) {
                        Log.w(TAG, CLASS + " " + e);
                        e.printStackTrace();
                        break;
                    }
                    if (commBreakTable != null)
                        commBreakTable.load(xmlResult);
                    if (commBreakTable.entries.length > 0) {
                        commBreakTable.offSetType = CommBreakTable.OFFSET_DURATION;
                        break;
                    }
                    // If Duration failed, try Frame. This could happen if there is no
                    // seek table
                    try {
                        String urlString = XmlNode.mythApiUrl(null,
                                urlMethod
                                        + video.recordedid
                                        + "&IncludeFps=true");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (IOException | XmlPullParserException e) {
                        Log.w(TAG, CLASS + " " + e);
                        break;
                    }
                    if (commBreakTable != null)
                        commBreakTable.load(xmlResult);
                    if (commBreakTable.entries.length > 0)
                        commBreakTable.offSetType = CommBreakTable.OFFSET_FRAME;
                    break;
                }

                case Action.CUTLIST_LOAD: {
                    // params is commBreakTable
                    CommBreakTable commBreakTable = (CommBreakTable) params;

                    if (commBreakTable.entries.length > 0)
                        break;

                    String urlMethod;
                    if (isRecording)
                        urlMethod = "/Dvr/GetRecordedCutList?RecordedId=";
                    else
                        urlMethod = "/Video/GetVideoCutList?Id=";
                    try {
                        String urlString = XmlNode.mythApiUrl(null,
                                urlMethod
                                        + video.recordedid
                                        + "&OffsetType=Duration&IncludeFps=true");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (IOException | XmlPullParserException e) {
                        Log.w(TAG, CLASS + " " + e);
                        break;
                    }
                    if (commBreakTable != null)
                        commBreakTable.load(xmlResult);
                    if (commBreakTable.entries.length > 0) {
                        commBreakTable.offSetType = CommBreakTable.OFFSET_DURATION;
                        break;
                    }
                    // If Duration failed, try Frame. This could happen if there is no
                    // seek table
                    try {
                        String urlString = XmlNode.mythApiUrl(null,
                                urlMethod
                                        + video.recordedid
                                        + "&IncludeFps=true");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (IOException | XmlPullParserException e) {
                        Log.w(TAG, CLASS + " " + e);
                        break;
                    }
                    if (commBreakTable != null)
                        commBreakTable.load(xmlResult);
                    if (commBreakTable.entries.length > 0)
                        commBreakTable.offSetType = CommBreakTable.OFFSET_FRAME;
                    break;
                }
                case Action.GETUPCOMINGLIST:
                    try {
                        boolean showAll = false;
                        if (params != null)
                            showAll = ((Boolean)params).booleanValue();
                        String urlString = XmlNode.mythApiUrl(null,
                                "/Dvr/GetUpcomingList?ShowAll=" + showAll);
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception In GETUPCOMINGLIST ", e);
                    }
                    break;
                case Action.DVR_WSDL:
                    try {
                        String url = XmlNode.mythApiUrl(null,
                                "/Dvr/wsdl");
                        xmlResult = XmlNode.fetch(url, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception getting Dvr wsdl.", e);
                    }
                    break;
                case Action.BACKEND_INFO:
                {
                    String urlString = null;
                    BackendCache bCache = BackendCache.getInstance();
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                "/Status/GetStatus");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Getting backend Info.", e);
                    }
                    if (xmlResult != null) {
                        String version = xmlResult.getAttribute("version");
                        if (version != null) {
                            int period = version.indexOf('.');
                            if (period > 0) {
                                bCache.mythTvVersion = Integer.parseInt(version.substring(0, period));
                                if (bCache.mythTvVersion == 0 && period == 1)
                                    // For versions like 0.24
                                    bCache.mythTvVersion = Integer.parseInt(version.substring(2,4));
                            }
                        }
                        String dateStr = xmlResult.getAttribute("ISODate");
                        if (dateStr != null) {
                            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
                            try {
                                Date backendTime = dbFormat.parse(dateStr + "+0000");
                                bCache.mTimeAdjustment = backendTime.getTime() - System.currentTimeMillis();
                                Log.i(TAG, CLASS + " Time difference " + bCache.mTimeAdjustment + " milliseconds");
                            } catch (ParseException e) {
                                Log.e(TAG, CLASS + " Exception getting backend time " + urlString, e);
                            }
                        }
                    }
                    // Find if we support the LastPlayPos API's
                    long tResult = 0;
                    XmlNode testNode = null;
                    try {
                        urlString = XmlNode.mythApiUrl(null,
                                "/Dvr/GetLastPlayPos?RecordedId=-1");
                        testNode = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception in GetLastPlayPos. " + e);
                    }
                    if (testNode != null) {
                        try {
                            tResult = Long.parseLong(testNode.getString());
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                            tResult = 0;
                        }
                    }
                    if (tResult == -1)
                        bCache.supportLastPlayPos = true;
                    else
                        bCache.supportLastPlayPos = false;
                    Log.i(TAG, CLASS + " Last Play Position Support:" + bCache.supportLastPlayPos);
                    break;
                    }
                case Action.GET_HOSTNAME:
                    try {
                        String urlString = XmlNode.mythApiUrl(null,
                                "/Myth/GetHostName");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception in Myth/GetHostName.", e);
                    }
                    break;
                case Action.FILELENGTH: {
                    // params: -1 or null to bypass check for changing
                    //         0 or a value to check for changing
                    // mValue is prior file length to be checked against
                    // Try 5 times until file length increases.
                    long priorLength = -1;
                    if (params != null)
                        priorLength = (Long) params;
                    String urlString = video.videoUrl;
                    long fileLength = -1;
                    for (int counter = 0; counter < 5; counter++) {
                        try {
                            // pause 1 second between attempts
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                        HttpURLConnection urlConnection = null;
                        try {
                            URL url = new URL(urlString);
                            urlConnection = (HttpURLConnection) url.openConnection();
                            urlConnection.addRequestProperty("Cache-Control", "no-cache");
                            urlConnection.addRequestProperty("Accept-Encoding", "identity");
                            String auth = BackendCache.getInstance().authorization;
                            if (auth != null && auth.length() > 0)
                                urlConnection.addRequestProperty("Authorization", auth);
                            urlConnection.setConnectTimeout(1000);
                            urlConnection.setReadTimeout(1000);
                            urlConnection.setRequestMethod("HEAD");
                            Log.i(TAG, CLASS + " URL: " + urlString);
                            urlConnection.connect();
                            try {
                                Log.d(TAG, CLASS + " Response: " + urlConnection.getResponseCode()
                                        + " " + urlConnection.getResponseMessage());
                            } catch (Exception ignored) {
                                // Sometimes there is a ProtocolException in the urlConnection.getResponseCode
                                // Ignore the error so that we can continue
                            }
                            String strContentLeng = urlConnection.getHeaderField("Content-Length");
                            if (strContentLeng == null) {
                                Log.e(TAG, CLASS + " FileLength failure. Content-Length null ");
                                // try again
                                continue;
                            } else {
                                fileLength = Long.parseLong(strContentLeng);
                            }
                            if (fileLength == -1)
                                Log.e(TAG, CLASS + " FileLength failure. strContentLeng: " + strContentLeng);
                            if (priorLength == -1 || fileLength > priorLength)
                                break;
                        } catch (Exception e) {
                            try {
                                Log.i(TAG, CLASS + " Response: " + urlConnection.getResponseCode()
                                        + " " + urlConnection.getResponseMessage());
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                            Log.e(TAG, CLASS + " Exception getting file length.", e);
                        } finally {
                            if (urlConnection != null)
                                urlConnection.disconnect();
                        }
                    }
                    response = new Long(fileLength);
                    break;
                }
                case Action.GUIDE: {
                    // params is array of objects
                    // params[0]: Integer channel group id
                    // params[1]: Date startTime
                    // params[2]: Date endTime
                    try {
                        Object parm[] = (Object[]) params;
                        SimpleDateFormat sdfUTC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                        sdfUTC.setTimeZone(TimeZone.getTimeZone("UTC"));
                        String urlString = XmlNode.mythApiUrl(null,
                                "/Guide/GetProgramGuide?ChannelGroupId=" + parm[0]
                                        + "&StartTime="
                                        + URLEncoder.encode(sdfUTC.format((Date) parm[1]), "UTF-8")
                                        + "&EndTime=" + URLEncoder.encode(sdfUTC.format((Date) parm[2]), "UTF-8")
                                        + "&Details=true");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Getting Guide.", e);
                    }
                    break;
                }

                case Action.CHAN_GROUPS: {
                    try {
                        String urlString = XmlNode.mythApiUrl(null,
                                "/Guide/GetChannelGroupList?IncludeEmpty=false");
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Getting Channel Groups.", e);
                    }
                    break;
                }

                case Action.SEARCHGUIDE_TITLE: {
                    try {
                        String urlString = XmlNode.mythApiUrl(null,
                                "/Guide/GetProgramList?Sort=starttime&count=100&Details=true&TitleFilter="
                                        + URLEncoder.encode((String)params, "UTF-8"));
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception Getting Guide.", e);
                    }
                    break;
                }

            }
            xmlResults.add(xmlResult);
        }

    }

    // method: GetSavedBookmark or GetLastPlayPos
    // Return array has bookmark and posbookmark or lastplaypos values
    private long[] fetchLastPlayPos(Video video) throws XmlPullParserException, IOException {
        boolean isRecording = (video.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING);
        String urlString;
        XmlNode bkmrkData = null;
        long[] retValue = {0, -1};
        String method;
        if (BackendCache.getInstance().supportLastPlayPos)
            method = "GetLastPlayPos";
        else
            method = "GetSavedBookmark";
        if (isRecording) {
            urlString = XmlNode.mythApiUrl(video.hostname,
                    "/Dvr/" + method + "?OffsetType=duration&RecordedId="
                            + video.recordedid);
            bkmrkData = XmlNode.safeFetch(urlString, null);
            try {
                retValue[0] = Long.parseLong(bkmrkData.getString());
            } catch (NumberFormatException e) {
                Exception e2 = bkmrkData.getException();
                if (BackendCache.getInstance().supportLastPlayPos && e2 != null && e2 instanceof FileNotFoundException) {
                    BackendCache.getInstance().supportLastPlayPos = false;
                    Log.w(TAG,"AsyncBakendCall.fetchLastPlayPos failed will use bookmarks instead");
                    return fetchLastPlayPos(video);
                }
                e.printStackTrace();
                retValue[0] = -1;
            }
            // sanity check bookmark - between 0 and 24 hrs.
            // note -1 means a bookmark but no seek table
            // older version of service returns garbage value when there is
            // no seek table.
            if (retValue[0] > 24 * 60 * 60 * 1000 || retValue[0] < 0)
                retValue[0] = -1;
        }
        if (retValue[0] == -1 || !isRecording) {
            // look for a position bookmark (for recording with no seek table)
            if (isRecording)
                urlString = XmlNode.mythApiUrl(video.hostname,
                        "/Dvr/" + method + "?OffsetType=frame&RecordedId="
                                + video.recordedid);
            else
                urlString = XmlNode.mythApiUrl(video.hostname,
                        "/Video/" + method + "?Id="
                                + video.recordedid);
            bkmrkData = XmlNode.safeFetch(urlString, null);
            try {
                retValue[1] = Long.parseLong(bkmrkData.getString());
            } catch (NumberFormatException e) {
                e.printStackTrace();
                retValue[1] = -1;
            }
        }
        return retValue;
    }

    // method: SetSavedBookmark or SetLastPlayPos
    // array has bookmark and posbookmark
    // Return object contains true if successful
    private XmlNode updateLastPlayPos(Video video, long[] params)
            throws XmlPullParserException, IOException {
        long mark = params[0];
        long pos = params[1];
        boolean isRecording = (video.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING);
        String urlString;
        XmlNode xmlResult = null;
        boolean found = false;
        String method;
        if (BackendCache.getInstance().supportLastPlayPos)
            method = "SetLastPlayPos";
        else
            method = "SetSavedBookmark";

        // store a mythtv bookmark
        if (isRecording) {
            urlString = XmlNode.mythApiUrl(video.hostname,
                    "/Dvr/"+method+"?OffsetType=duration&RecordedId="
                            + video.recordedid + "&Offset=" + mark);
            xmlResult = XmlNode.safeFetch(urlString, "POST");
            String result = xmlResult.getString();
            if ("true".equals(result))
                found = true;
        }
        if (!found && pos >= 0) {
            // store a mythtv position bookmark (in case there is no seek table)
            if (isRecording)
                urlString = XmlNode.mythApiUrl(video.hostname,
                        "/Dvr/"+method+"?RecordedId="
                                + video.recordedid + "&Offset=" + pos);
            else
                urlString = XmlNode.mythApiUrl(video.hostname,
                        "/Video/"+method+"?Id="
                                + video.recordedid + "&Offset=" + pos);
            xmlResult = XmlNode.safeFetch(urlString, "POST");
        }
        return xmlResult;
    }

}
