package org.mythtv.mobfront.ui.playback;

import androidx.annotation.OptIn;
import androidx.lifecycle.ViewModel;
import androidx.media3.common.Format;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.ui.PlayerView;

import org.mythtv.mobfront.data.Video;

import java.util.concurrent.ScheduledExecutorService;

public class PlaybackViewModel extends ViewModel implements PlayerView.SizeGetter {
    Video video;
    ExoPlayer player;
    long bookmark;
    float frameRate;
    long savedCurrentPosition;
    long savedDuration;
    boolean possibleEmptyTrack;
    boolean maybePlaying;


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
}