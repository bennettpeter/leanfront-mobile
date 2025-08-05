package org.mythtv.mobfront.ui.playback;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.mythtv.mobfront.R;
import org.mythtv.mobfront.data.Action;
import org.mythtv.mobfront.data.AsyncBackendCall;
import org.mythtv.mobfront.data.BackendCache;
import org.mythtv.mobfront.data.Settings;
import org.mythtv.mobfront.databinding.FragmentPlaybackBinding;
import org.mythtv.mobfront.player.MyExtractorsFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class PlaybackFragment extends Fragment {

    private PlaybackViewModel viewModel;
    private FragmentPlaybackBinding binding;
    private String mAudio = Settings.getString("pref_audio");
    static final String TAG = "mfe";
    static final String CLASS = "PlaybackFragment";
    Handler handler = new Handler(Looper.getMainLooper());
    Updater updater = new Updater();
    private long mTimeLastError = 0;


    public static PlaybackFragment newInstance() {
        return new PlaybackFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);
        Intent intent =  getActivity().getIntent();
        viewModel.video = intent.getParcelableExtra(PlaybackActivity.VIDEO);
        viewModel.bookmark = intent.getLongExtra(PlaybackActivity.BOOKMARK, 0l);
        viewModel.frameRate = intent.getDoubleExtra(PlaybackActivity.FRAMERATE, 30l);
        setPlaySettings();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlaybackBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer(true);
        }
    }
    public void onResume() {
        super.onResume();
        if ((Build.VERSION.SDK_INT <= 23 || viewModel.player == null)) {
            initializePlayer(true);
        }
        hideNavigation();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT <= 23) {
            if (binding.playerView != null) {
                binding.playerView.onPause();
            }
            setBookmark();
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT > 23) {
            if (binding.playerView != null) {
                binding.playerView.onPause();
            }
            setBookmark();
            releasePlayer();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initializePlayer(boolean enableControls) {
//        Log.i(TAG, CLASS + " Initializing Player for " + mVideo.title + " " + mVideo.videoUrl);
//        mTrackSelector = new DefaultTrackSelector(getContext());
//        ExoPlayer.Builder builder = new ExoPlayer.Builder(getContext(),rFactory);
        DefaultRenderersFactory rFactory = new DefaultRenderersFactory(getContext());
        int extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
        if ("mediacodec".equals(mAudio))
            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
        else if ("ffmpeg".equals(mAudio))
            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
        rFactory.setExtensionRendererMode(extMode);
        rFactory.setEnableDecoderFallback(true);
        ExoPlayer.Builder builder = new ExoPlayer.Builder(getContext(), rFactory);
//        builder.setTrackSelector(mTrackSelector);
        int seekBack = Settings.getInt("pref_skip_back") * 1000;
        builder.setSeekBackIncrementMs(seekBack);
        int seekFwd = Settings.getInt("pref_skip_fwd") * 1000;
        builder.setSeekForwardIncrementMs(seekFwd);
        viewModel.player = builder.build();
        binding.playerView.setPlayer(viewModel.player);
        PlayerEventListener playerEventListener = new PlayerEventListener();
        viewModel.player.addListener(playerEventListener);
        MediaItem mediaItem = MediaItem.fromUri(viewModel.video.videoUrl);
        MyExtractorsFactory extFactory = new MyExtractorsFactory();
        String auth = BackendCache.getInstance().authorization;
        DataSource.Factory dsFactory =
            () -> {
                HttpDataSource.Factory factory = new DefaultHttpDataSource.Factory();
                HttpDataSource dataSource = factory.createDataSource();
                if (auth != null && auth.length() > 0)
                    dataSource.setRequestProperty("Authorization", auth);
                return dataSource;
            };

        DefaultMediaSourceFactory pmf = new DefaultMediaSourceFactory
                (dsFactory, extFactory);
        ProgressiveMediaSource mediaSource = (ProgressiveMediaSource) pmf.createMediaSource(mediaItem);
        mediaSource.setPossibleEmptyTrack(viewModel.possibleEmptyTrack);
        viewModel.player.setMediaSource(mediaSource, viewModel.bookmark);
        viewModel.player.prepare();
        viewModel.player.play();
        handler.postDelayed(updater, 2000);
    }

    class Updater implements Runnable {
        @Override
        public void run() {
            try {
                if (viewModel.player.isPlaying()) {
                    viewModel.savedDuration = viewModel.player.getDuration();
                    viewModel.savedCurrentPosition = viewModel.player.getCurrentPosition();
                    handler.postDelayed(this,1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
//        mSubtitles = getActivity().findViewById(R.id.leanback_subtitles);
//        if (mSubtitles != null) {
//            mSubtitles.setFractionalTextSize
//                    (SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * mSubtitleSize / 100.0f);
//        }
//
//        mPlayerEventListener = new PlayerEventListener();
//        mPlayer.addListener(mPlayerEventListener);
//
//        mPlayerAdapter = new LeanbackPlayerAdapter(getActivity(), mPlayer, UPDATE_DELAY);
//        if (mPlaybackActionListener == null) {
//            mPlaybackActionListener = new PlaybackActionListener(this, mPlaylist);
//            int sampleOffsetUs = 1000 * Settings.getInt("pref_audio_sync", mVideo.playGroup);
//            mPlaybackActionListener.sampleOffsetUs = sampleOffsetUs;
//            if (sampleOffsetUs != 0)
//                mAudioPause = true;
//        }
//        VideoPlayerGlue oldGlue = mPlayerGlue;
//        mPlayerGlue = new VideoPlayerGlue(getActivity(), mPlayerAdapter,
//                mPlaybackActionListener, mRecordid > 0);
//        mPlayerGlue.setEnableControls(enableControls);
//        if (oldGlue == null)
//            mPlayerGlue.setAutoPlay("true".equals(Settings.getString("pref_autoplay",mVideo.playGroup)));
//        else
//            mPlayerGlue.setAutoPlay(oldGlue.getAutoPlay());
//        mPlayerGlue.setHost(new VideoSupportFragmentGlueHost(this));
//        hideControlsOverlay(false);
//        play(mVideo);
//        ArrayObjectAdapter mRowsAdapter = initializeRelatedVideosRow();
//        setAdapter(mRowsAdapter);
//        mPlayerGlue.setupSelectedListener();
//    }

        protected void releasePlayer() {
        if (viewModel.player != null) {
//            updateTrackSelectorParameters();
//            updateStartPosition();
//            releaseServerSideAdsLoader();
//            debugViewHelper.stop();
//            debugViewHelper = null;
            viewModel.player.release();
            viewModel.player = null;
            binding.playerView.setPlayer(/* player= */ null);
//            mediaItems = Collections.emptyList();
        }
    }
    public void hideNavigation () {
        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            View view = getView();
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    void setBookmark() {
        if (viewModel.player == null)
            return;
        viewModel.bookmark = viewModel.player.getCurrentPosition();
        long duration = viewModel.player.getDuration();
        int action2 = Action.DUMMY;
        // Clear bookmark if at end of video and set watched
        if (duration - viewModel.bookmark < 10000) {
            viewModel.bookmark = 0;
            action2 = Action.SET_WATCHED;
        }
        long params[] = new long[2];
        params[0] = viewModel.bookmark;
        params[1] =  (long) (viewModel.frameRate * 100.0f) * params[0] / 100000;
        AsyncBackendCall call = new AsyncBackendCall(getActivity(), null);
        call.videos.add(viewModel.video);
        call.params = params;
        call.execute(Action.SET_BOOKMARK, action2);
    }

    public void markWatched(boolean watched) {
        AsyncBackendCall call = new AsyncBackendCall(getActivity(), null);
        call.videos.add(viewModel.video);
        call.params = new Boolean(watched);
        call.execute(Action.SET_WATCHED);
    }

    private void setPlaySettings() {
//        viewModel.possibleEmptyTrack = "true".equals(Settings.getString("pref_poss_empty"));
    }

    class PlayerEventListener implements Player.Listener {
        private int mDialogStatus = 0;
        private static final int DIALOG_NONE   = 0;
        private static final int DIALOG_ACTIVE = 1;
        private static final int DIALOG_EXIT   = 2;
        private static final int DIALOG_RETRY  = 3;

        @Override
        public void onPlayerError(@NonNull PlaybackException ex) {
            handlePlayerError(ex, -1);
        }

        @OptIn(markerClass = UnstableApi.class)
        private void handlePlayerError(Exception ex, int msgNum) {
            Throwable cause = null;
            if (ex != null)
                Log.e(TAG, CLASS + " Player Error " + viewModel.video.title + " " + viewModel.video.videoUrl, ex);
            long now = System.currentTimeMillis();
            int recommendation = 0;
            boolean setPossibleEmptyTrack = false;
            if (ex != null && ex instanceof ExoPlaybackException) {
                ExoPlaybackException error = (ExoPlaybackException)ex;
                switch (error.type) {
                    case ExoPlaybackException.TYPE_REMOTE:
                        msgNum = R.string.pberror_remote;
                        cause = null;
                        break;
                    case ExoPlaybackException.TYPE_RENDERER:
                        msgNum = R.string.pberror_renderer;
                        cause = error.getRendererException();
                        // handle error caused by selecting an unsupported audio track
                        if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                            String mimeType = ((MediaCodecRenderer.DecoderInitializationException) cause).mimeType;
                            if (mimeType != null && mimeType.startsWith("audio")) {
                                recommendation = R.string.pberror_recommend_audio;
                            }
                        }
                        break;
                    case ExoPlaybackException.TYPE_SOURCE:
                        msgNum = R.string.pberror_source;
                        cause = error.getSourceException();
                        if (cause != null && cause.getMessage().startsWith("Unexpected ArrayIndexOutOfBoundsException")) {
                            msgNum = R.string.pberror_extractor_array;
                            break;
                        }
                        else {
//                            setPossibleEmptyTrack = true;
                            msgNum = R.string.pberror_source;
                        }
                        break;
                    case ExoPlaybackException.TYPE_UNEXPECTED:
                        msgNum = R.string.pberror_unexpected;
                        cause = error.getUnexpectedException();
//                        if (mPlayerGlue.getSavedCurrentPosition() < 200
                        if (viewModel.savedCurrentPosition < 200
                                && "Playback stuck buffering and not loading".equals(cause.getMessage()))
                            setPossibleEmptyTrack = true;
                        // this error comes from fire stick 4k when selecting an mpeg level l2 audio track
                        if ("Multiple renderer media clocks enabled.".equals(cause.getMessage()))
                            recommendation = R.string.pberror_recommend_ffmpeg;
                        break;
                    default:
                        msgNum = R.string.pberror_default;
                        cause = null;
                        break;
                }
            }

            Context context = getContext();
            if (context != null) {
                StringBuilder msg = new StringBuilder();
                if (msgNum > -1)
                    msg.append(context.getString(msgNum));
                if (ex != null)
                    msg.append("\n").append(ex.getMessage());
                String alertMsg = msg.toString();
                if (cause != null)
                    msg.append("\n").append(cause.getMessage());
                Log.e(TAG, CLASS + " Player Error " + msg);
                // if we are near the start or end
                long currPos = viewModel.savedCurrentPosition;
                long duration = viewModel.savedDuration;
                boolean failAtStart = duration <= 0 || currPos <= 0;
                boolean failAtEnd = !failAtStart && Math.abs(duration - currPos) < 10000;
                if (mDialogStatus == DIALOG_NONE) {
                    // If there has been over 30 seconds since last error report
                    // try to recover from error by playing on.
                    if (failAtEnd
                            || mTimeLastError < now - 30000 ) {
//                        if ("true".equals(Settings.getString("pref_error_toast"))) {
                            Toast.makeText(getActivity(),
                                    getActivity().getString(msgNum),
                                    Toast.LENGTH_LONG)
                                    .show();
//                        }
                        // if we are at the end - just end playback
                        if (failAtEnd)
                            markWatched(true);
                        else {
                            // Try to continue playback
                            if (currPos > 0)
                                viewModel.bookmark = currPos;
                            if (setPossibleEmptyTrack && !viewModel.possibleEmptyTrack) {
                                viewModel.possibleEmptyTrack = true;
//                                Toast.makeText(getActivity(),
//                                        getActivity().getString(R.string.pberror_recommend_ignoreextra),
//                                        Toast.LENGTH_LONG)
//                                        .show();
                                viewModel.player.stop();
                                initializePlayer(true);
                            }
                            else
                                initializePlayer(true);
                        }
                        mTimeLastError = now;
                    }
                    else {
                        // More than 1 error per 30 seconds.
                        // Alert message for user to decide on continuing.
                        AlertDialogListener listener = new AlertDialogListener();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
//                                R.style.Theme_AppCompat_Dialog_Alert);
                        builder.setTitle(R.string.pberror_title);
                        if (recommendation > 0)
                            builder.setMessage(recommendation);
                        else
                            builder.setMessage(alertMsg);
                        // add a button
                        builder.setPositiveButton(R.string.pberror_button_continue, listener);
                        builder.setNegativeButton(R.string.pberror_button_exit, listener);
                        builder.setOnDismissListener(
                                dialog -> {
                                    if (mDialogStatus != DIALOG_RETRY)
                                        getActivity().finish();
                                    mDialogStatus = DIALOG_NONE;
                                });
                        builder.show();
                        mDialogStatus = DIALOG_ACTIVE;
                        mTimeLastError = 0;
                    }
                }
            }

        }

        class AlertDialogListener implements DialogInterface.OnClickListener {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        mDialogStatus = DIALOG_RETRY;
                        viewModel.bookmark = viewModel.savedCurrentPosition;
                        initializePlayer(true);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        mDialogStatus = DIALOG_EXIT;
                        break;
                }
            }
        }

    }



}