package org.mythtv.mobfront.data;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import org.mythtv.mobfront.MyApplication;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncBackendCall implements Runnable {

    public interface OnBackendCallListener {
        void onPostExecute(AsyncBackendCall taskRunner);
    }

    public ArrayList<Video> videos = new ArrayList<>();
    public HashMap<String, String> params = new HashMap<>();
    private ArrayList<XmlNode> xmlResults = new ArrayList<>();
    private int[] tasks;
    private Activity activity;
    private OnBackendCallListener listener;
    private View view;
    private final static ExecutorService executor = Executors.newCachedThreadPool();

    public AsyncBackendCall(@Nullable Activity activity, @Nullable OnBackendCallListener listener,
                            @Nullable View view) {
        this.activity = activity;
        this.listener = listener;
        this.view = view;
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
        this.tasks = new int[tasks.length];
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
                if (activity != null)
                    activity.runOnUiThread(() -> listener.onPostExecute(this));
                else if (view != null)
                    view.post(() -> listener.onPostExecute(this));
                else
                    listener.onPostExecute(this);
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private void runTasks() {
        BackendCache bCache = BackendCache.getInstance();
        Context context = MyApplication.getAppContext();
        HttpURLConnection urlConnection = null;
        int videoIndex = 0;
        int taskIndex = -1;
        Video video;
        for (; ; ) {
            boolean doGetOnly = false;
            // If there is a rowAdapter, take each video in the adapter and run
            // all tasks on it.
            taskIndex++;
            if (taskIndex >= tasks.length) {
//                if (rowAdapter == null)
//                    break;
                taskIndex = 0;
                videoIndex++;
            }
            if (videos.size() > 0) {
                if (videoIndex >= videos.size())
                    break;

//            if (rowAdapter != null) {
                video = videos.get(videoIndex);
                // in row adapter only process videos and series.
//                if ( ! (video.type == MainFragment.TYPE_VIDEO
//                        || video.type == MainFragment.TYPE_EPISODE))
//                    continue;
            }

        }


    }
}
