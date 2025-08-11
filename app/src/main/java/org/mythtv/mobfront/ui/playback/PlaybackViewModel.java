package org.mythtv.mobfront.ui.playback;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.media3.common.Format;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.ui.PlayerView;

import org.mythtv.mobfront.data.Action;
import org.mythtv.mobfront.data.AsyncBackendCall;
import org.mythtv.mobfront.data.CommBreakTable;
import org.mythtv.mobfront.data.Settings;
import org.mythtv.mobfront.data.Video;

public class PlaybackViewModel extends ViewModel implements PlayerView.SizeGetter {
    Video video;
    ExoPlayer player;
    long bookmark;
    float frameRate;
    long savedCurrentPosition;
    long savedDuration;
    boolean possibleEmptyTrack;
    boolean maybePlaying;
    CommBreakTable commBreakTable = new CommBreakTable();
    long priorCommBreak = -1;
    long nextCommBreakMs = Long.MAX_VALUE;
    long endCommBreakMs = Long.MAX_VALUE;
    private final int commBreakOption =  Settings.getInt("pref_commskip");
    public static final int COMMBREAK_OFF = 0;
    public static final int COMMBREAK_NOTIFY = 1;
    public static final int COMMBREAK_SKIP = 2;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Updater updater = new Updater();
    final MutableLiveData<Integer> commSkipToast = new MutableLiveData<>();
    final MutableLiveData<Long> commBreakDlg = new MutableLiveData<>();
    final String TAG = "mfe";
    final String CLASS = "PlaybackViewModel";


    @OptIn(markerClass = UnstableApi.class)
    @Override
    public VideoSize getVideoSize() {
        VideoSize vs = player.getVideoSize();
        int count = player.getRendererCount();
        for (int ix = 0; ix < count; ix++ ) {
            Renderer renderer = player.getRenderer(ix);
            if (renderer.getState() != Renderer.STATE_DISABLED) {
                if ("ExperimentalFfmpegVideoRenderer".equals(renderer.getName())) {
                    Format format = player.getVideoFormat();
                    if (vs.width == format.width && vs.height == format.height
                            && vs.pixelWidthHeightRatio != format.pixelWidthHeightRatio)
                        return new VideoSize(vs.width, vs.height, format.pixelWidthHeightRatio);
                }
            }
        }
        return vs;
    }

    void fillTables(Activity activity) {
        AsyncBackendCall call = new AsyncBackendCall(activity, (caller) -> {
            if (commBreakTable.frameratex1000 > 1)
                frameRate = (float) (commBreakTable.frameratex1000 / 1000.);
            if (commBreakTable.entries.length > 0)
                setNextCommBreak(-1);
        });
        call.videos.add(video);
        call.params = commBreakTable;
        call.execute(Action.CUTLIST_LOAD, Action.COMMBREAK_LOAD);
    }

    // pass in -1 to get current position, otherwise use position passed in
    public void setNextCommBreak(long position) {
        if (commBreakOption != COMMBREAK_OFF) {
            if (position == -1)
                position = player.getCurrentPosition();
            long nextCommBreak = Long.MAX_VALUE;
            CommBreakTable.Entry startEntry = null;
            long startOffsetMs = 0;
            synchronized (commBreakTable) {
                for (CommBreakTable.Entry entry : commBreakTable.entries) {
                    long offsetMs = commBreakTable.getOffsetMs(entry);
                    if (entry.mark == CommBreakTable.MARK_CUT_START) {
                        startEntry = entry;
                        startOffsetMs = commBreakTable.getOffsetMs(startEntry);
                    } else {
                        long possible = startOffsetMs + Settings.getInt("pref_commskip_start") * 1000;
                        if (position <= offsetMs && entry.mark == CommBreakTable.MARK_CUT_END
                                && startEntry != null && possible != priorCommBreak) {
                            nextCommBreak = possible;
                            break;
                        }
                    }
                }
            }
            nextCommBreakMs = nextCommBreak;
        }

    }

    void startPlayback() {
        handler.postDelayed(updater, 2000);
    }

    void onUpdateProgress() {
        long currPos = savedCurrentPosition;
        if (currPos >= endCommBreakMs) {
            synchronized (this) {
                endCommBreakMs = Long.MAX_VALUE;
            }
            onEndCommBreak();
        }
        if (currPos >= nextCommBreakMs) {
            long next = nextCommBreakMs;
            synchronized (this) {
                nextCommBreakMs = Long.MAX_VALUE;
            }
            onCommBreak(next, currPos);
        }
    }

    public void onCommBreak(long nextCommBreakMs, long position) {
        long newPosition = -1;
        switch (commBreakOption) {
            case COMMBREAK_SKIP:
            case COMMBREAK_NOTIFY:
                synchronized (commBreakTable) {
                    for (CommBreakTable.Entry entry : commBreakTable.entries) {
                        long offsetMs = commBreakTable.getOffsetMs(entry);
                        // Skip past earlier entries
                        if (offsetMs <= nextCommBreakMs)
                            continue;
                        // We should now be at the MARK_CUT_END of the selected comm break
                        // If not or if we are past it, do nothing.
                        if (position <= offsetMs && entry.mark == CommBreakTable.MARK_CUT_END) {
                            newPosition = offsetMs + (long) Settings.getInt("pref_commskip_end") * 1000;
                        } else
                            Log.e(TAG, CLASS + " No end commbreak entry for: " + nextCommBreakMs);
                        break;
                    }
                }
                break;
            default:
                return;
        }
        if (newPosition > 0 && newPosition > position) {
            priorCommBreak = nextCommBreakMs;
            switch (commBreakOption) {
                case COMMBREAK_SKIP:
                    player.seekTo(newPosition);
                    commSkipToast.postValue(-2);
                    break;
                case COMMBREAK_NOTIFY:
                    commBreakDlg.postValue(newPosition);
                    break;
            }
            endCommBreakMs = newPosition + 500;
        }
        setNextCommBreak(-1);
    }


    public void onEndCommBreak() {
        // This will dismiss the dialog
        commBreakDlg.postValue(0L);
    }
    class Updater implements Runnable {
    @Override
        public void run() {
            try {
                if (maybePlaying && player != null) {
                    savedDuration = player.getDuration();
                    savedCurrentPosition = player.getCurrentPosition();
                    onUpdateProgress();
                    handler.postDelayed(this,1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}