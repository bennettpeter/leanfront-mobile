package org.mythtv.mobfront.ui.playback;

import androidx.lifecycle.ViewModel;
import androidx.media3.exoplayer.ExoPlayer;

import org.mythtv.mobfront.data.Video;

import java.util.concurrent.ScheduledExecutorService;

public class PlaybackViewModel extends ViewModel {
    Video video;
    ExoPlayer player;
    long bookmark;
    double frameRate;
    ScheduledExecutorService executor = null;
    long savedCurrentPosition;
    long savedDuration;
    boolean possibleEmptyTrack;

}