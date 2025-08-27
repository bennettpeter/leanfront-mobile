package org.mythtv.lfmobile.ui.playback;

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
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.data.Action;
import org.mythtv.lfmobile.data.AsyncBackendCall;
import org.mythtv.lfmobile.data.CommBreakTable;
import org.mythtv.lfmobile.data.Settings;
import org.mythtv.lfmobile.data.Video;

@UnstableApi
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
    private long lastSeekFrom;
    private boolean lastSeekIsFwd;
    private long lastSeekTime;

    private final int commBreakOption =  Settings.getInt("pref_commskip");
    // Note this will be cleared if it was set as skipcom and there is no commercial data
    String prevnextOption =  Settings.getString("pref_prevnext");
    final int jump =  Settings.getInt("pref_jump");
    public static final int COMMBREAK_OFF = 0;
    public static final int COMMBREAK_NOTIFY = 1;
    public static final int COMMBREAK_SKIP = 2;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Updater updater = new Updater();
    final MutableLiveData<long[]> commSkipToast = new MutableLiveData<>();
    final MutableLiveData<Long> commBreakDlg = new MutableLiveData<>();
    private static final String TAG = "lfm";
    final String CLASS = "PlaybackViewModel";
    // aspectValues[0] will change from 0f to the default value of the video
    float[] aspectValues = {0f, 1.333333f, 1.7777777f, 0.5625f};
    static final int [] ASPECT_DRAWABLES = {R.drawable.ic_aspect_button,
            R.drawable.ic_aspect_4x3, R.drawable.ic_aspect_16x9, R.drawable.ic_aspect_9x16};
    int currentAspectIx = 0;
    float currentAspect = 0.0f;
    static final int [] RESIZE_MODES = {
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    };
    static final int [] RESIZE_DRAWABLES = {R.drawable.ic_zoom_button,R.drawable.ic_zoom_large};
    int currentResizeIx = 0;
    int currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;

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
                            && vs.pixelWidthHeightRatio != format.pixelWidthHeightRatio) {
                        vs = new VideoSize(vs.width, vs.height, format.pixelWidthHeightRatio);
                        break;
                    }
                }
            }
        }
        aspectValues[0] =  (float)vs.width / (float)vs.height * vs.pixelWidthHeightRatio;
        if (currentAspectIx != 0)
            vs = new VideoSize(vs.width, vs.height,
                    currentAspect * (float) vs.height / (float) vs.width);
        return vs;
    }

    void fillTables(Activity activity) {
        AsyncBackendCall call = new AsyncBackendCall((caller) -> {
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
                    commSkipToast.postValue(new long[]
                            {-2, newPosition - player.getCurrentPosition()});
                    player.seekTo(newPosition);
                    break;
                case COMMBREAK_NOTIFY:
                    commBreakDlg.postValue(newPosition);
                    break;
            }
            endCommBreakMs = newPosition + 500;
        }
        setNextCommBreak(-1);
    }
    private boolean commSkipCheck() {
        if (commBreakTable == null || commBreakTable.entries.length == 0) {
            commSkipToast.postValue(new long[] {-5, 0});
            return false;
        }
        return true;
    }

    long skipComBack() {
        if (!commSkipCheck())
            return 0;
        long position = player.getCurrentPosition();
        if (lastSeekIsFwd && System.currentTimeMillis() - lastSeekTime < 10000l) {
            player.seekTo(lastSeekFrom);
            commSkipToast.postValue(new long[] {-3,lastSeekFrom - position});
            lastSeekTime = 0;
            return lastSeekFrom;
        }
        long newPosition = 0;
        int mark = 0;
        synchronized (commBreakTable) {
            // Get the last entry that satisfies offset < position
            for (CommBreakTable.Entry entry : commBreakTable.entries) {
                long offsetMs = commBreakTable.getOffsetMs(entry);
                if (offsetMs < position - 20000) {
                    newPosition = offsetMs;
                    mark = entry.mark;
                } else
                    break;
            }
        }
        if (newPosition == 0) {
            newPosition = 100;
            mark = -4;
        }
        setNextCommBreak(Long.MAX_VALUE);
        // If this is a start point, prevent it from immediately skipping
        if (mark == CommBreakTable.MARK_CUT_START)
            priorCommBreak = newPosition
                    + (long)Settings.getInt("pref_commskip_start") * 1000;
        player.seekTo(newPosition);
        commSkipToast.postValue(new long[] {mark, newPosition - position});
        lastSeekFrom = position;
        lastSeekIsFwd = false;
        lastSeekTime = System.currentTimeMillis();
        return newPosition;
    }

    long skipComForward() {
        if (!commSkipCheck())
            return 0;
        long position = player.getCurrentPosition();
        if (! lastSeekIsFwd && System.currentTimeMillis() - lastSeekTime < 10000l) {
            player.seekTo(lastSeekFrom);
            commSkipToast.postValue(new long[] {-3, lastSeekFrom - position});
            lastSeekTime = 0;
            return lastSeekFrom;
        }
        long newPosition = 0;
        int mark = 0;
        synchronized (commBreakTable) {
            // Get the first entry that satisfies offset > position
            for (CommBreakTable.Entry entry : commBreakTable.entries) {
                long offsetMs = commBreakTable.getOffsetMs(entry);
                if (offsetMs > position + 5000) {
                    newPosition = offsetMs;
                    mark = entry.mark;
                    break;
                }
            }
        }
        if (newPosition > 0) {
            setNextCommBreak(Long.MAX_VALUE);
            // If this is a start point, prevent it from immediately skipping
            if (mark == CommBreakTable.MARK_CUT_START)
                priorCommBreak = newPosition
                        + (long)Settings.getInt("pref_commskip_start") * 1000;
            player.seekTo(newPosition);
            commSkipToast.postValue(new long[] {mark, newPosition - position});
            lastSeekFrom = position;
            lastSeekIsFwd = true;
            lastSeekTime = System.currentTimeMillis();
        } else
            commSkipToast.postValue(new long[] {-1, 0});
        return newPosition;
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