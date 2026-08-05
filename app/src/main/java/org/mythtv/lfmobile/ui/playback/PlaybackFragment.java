package org.mythtv.lfmobile.ui.playback;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.SampleQueue;
import androidx.media3.exoplayer.source.SingleSampleMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.DefaultTimeBar;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.data.Action;
import org.mythtv.lfmobile.data.AsyncBackendCall;
import org.mythtv.lfmobile.data.CommBreakTable;
import org.mythtv.lfmobile.data.MythHttpDataSource;
import org.mythtv.lfmobile.data.Settings;
import org.mythtv.lfmobile.databinding.FragmentPlaybackBinding;
import org.mythtv.lfmobile.player.MyExtractorsFactory;
import org.mythtv.lfmobile.player.MyRenderersFactory;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.Locale;


@SuppressWarnings({"ExtractMethodRecommender", "SpellCheckingInspection"})
@UnstableApi
public class PlaybackFragment extends Fragment {

    private PlaybackViewModel viewModel;
    private FragmentPlaybackBinding binding;
    private final String prefAudio = Settings.getString("pref_audio");
    private final String prefVideo = Settings.getString("pref_video");

    private static final String TAG = "lfm";
    static final String CLASS = "PlaybackFragment";
    private long mTimeLastError = 0;
    AlertDialog dialog;
    private final DialogDismiss dialogDismiss = new DialogDismiss();
    private AspectRatioFrameLayout contentFrame;
    private StringBuilder formatBuilder;
    private Formatter formatter;
    private View contentView;
    private GestureDetector gestureDetector;
    private final GestureProcess gestureProcess = new GestureProcess();
    private final int skipBack = Settings.getInt("pref_skip_back");
    private final int skipFwd = Settings.getInt("pref_skip_fwd");
    private float downXPos;
    private int screenDPI;
    private long downPlayPos;
    private boolean dragInProgress;
    private final float seekRange = Settings.getFloat("pref_drag_range") * 60000f;
    private final float seekAccel = Settings.getFloat("pref_drag_accel");
    private final int prefTextSize = Settings.getInt("pref_duration_textsize");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String [] subtExtens = {"srt", "ssa", "ass", "vtt", "ttml"};
    private final String [] subtMimes = {MimeTypes.APPLICATION_SUBRIP, MimeTypes.TEXT_SSA,
            MimeTypes.TEXT_SSA, MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_TTML};
    private final boolean [] subtFound = new boolean[subtExtens.length];
    private int subtChecked = -1;

    public static PlaybackFragment newInstance() {
        return new PlaybackFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        formatBuilder = new StringBuilder();
        formatter = new Formatter(formatBuilder, Locale.getDefault());

        viewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);
        Intent intent =  requireActivity().getIntent();
        viewModel.video = intent.getParcelableExtra(PlaybackActivity.VIDEO);
        viewModel.bookmark = intent.getLongExtra(PlaybackActivity.BOOKMARK, 0L);
        viewModel.frameRate = intent.getFloatExtra(PlaybackActivity.FRAMERATE, 30f);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlaybackBinding.inflate(inflater, container, false);
        View v=binding.getRoot();
        setObservers(v);
        return v;
    }

    private void setObservers(View v) {
        viewModel.commSkipToast.observe(getViewLifecycleOwner(), this::commskipToast);
        viewModel.commBreakDlg.observe(getViewLifecycleOwner(), this::commBreakDlg);
        viewModel.durationLive.observe(getViewLifecycleOwner(), (Long duration) -> {
            TextView durationView = v.findViewById(R.id.my_duration);
            if (durationView != null) {
                durationView.setText(Util.getStringForTime(formatBuilder, formatter, duration));
            }
            DefaultTimeBar defaultTimeBar = v.findViewById(androidx.media3.ui.R.id.exo_progress);
            defaultTimeBar.setDuration(duration);
        });
        viewModel.playerErrorLive.observe(getViewLifecycleOwner(),
                (Object[] parm)-> handlePlayerError((Exception) parm[0], (Integer)parm[1]));
        viewModel.overlayDone.observe(getViewLifecycleOwner(),
                (Boolean aBoolean)-> handler.postDelayed(new Runnable() {
            boolean olaySetupDone = false;
            @Override
            public void run() {
                if (!olaySetupDone && viewModel.getDuration() > 0) {
                    SeekbarOverlay olay = v.findViewById(R.id.ad_overlay);
                    if (olay != null) {
                        olay.setup(viewModel.commBreakTable, viewModel);
                        olay.invalidate();
                    }
                    olaySetupDone = true;
                }
                if (!olaySetupDone)
                    handler.postDelayed(this, 500);
            }
        }, 500));
        viewModel.setBookmark.observe(getViewLifecycleOwner(), (Boolean aBoolean)-> setBookmark());
    }

    @SuppressLint({"StringFormatInvalid", "DefaultLocale"})
    private void commskipToast(long[] markrange) {
        int mark = (int) markrange[0];
        long range = markrange[1];
        int msgnum;
        switch (mark) {
            case CommBreakTable.MARK_CUT_START: msgnum = R.string.msg_commskip_start; break;
            case CommBreakTable.MARK_CUT_END:   msgnum = R.string.msg_commskip_end; break;
            case -1:                            msgnum = R.string.msg_commskip_last; break;
            case -2:                            msgnum = R.string.msg_commskip_skipped; break;
            case -3:                            msgnum = R.string.msg_commskip_back; break;
            case -4:                            msgnum = R.string.msg_commskip_start_show; break;
            case -5:                            msgnum = R.string.msg_commskip_none; break;
            default: return;
        }
        range = range / 1000L;
        long mins = range / 60L;
        long secs = Math.abs(range % 60L);

        Context ctx = requireContext();
        String time;
        if (msgnum == R.string.msg_commskip_none)
            time = "";
        else
            time = String.format(" (%1$ 02d:%2$02d).", mins, secs);
        String msg = ctx.getString(msgnum) + time;
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG)
            .show();


    }

    // pass in 0 to dismiss dialog
    @SuppressLint("RtlHardcoded")
    private void commBreakDlg(long newPosition) {
        dismissDialog();
        if (newPosition == 0)
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.title_comm_playing)
                .setItems(R.array.menu_commplaying,
                        (dialog, which) -> {
                            // The 'which' argument contains the index position
                            // of the selected item
                            // 0 = skip commercial
                            if (which == 0) {
                                if (viewModel.player.getCurrentPosition()
                                        < newPosition) {
                                    viewModel.player.seekTo(newPosition);
                                }
                                // 1 = do not skip commercial. Defaults to doing nothing
                            }
                        })
                .setOnDismissListener(dialogDismiss);
        dialog = builder.create();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
            lp.x = 0;
            lp.y = 0;
            lp.width = Resources.getSystem().getDisplayMetrics().widthPixels / 4;
            lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
            window.setAttributes(lp);
            window.setBackgroundDrawable(new ColorDrawable(Color.argb(192, 192, 192, 192)));
        }
    }

    public void dismissDialog() {
        if (dialog != null && dialog.isShowing())
            dialog.dismiss();
        dialog = null;
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer();
            viewModel.maybePlaying = true;
        }
        setupControls();
        contentView = requireView().findViewById(R.id.player_view);
        if (contentView != null) {
            gestureDetector = new GestureDetector(requireContext(),gestureProcess);
            contentView.setOnTouchListener((View vv, MotionEvent event) -> {
                gestureDetector.onTouchEvent(event);
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    downXPos = event.getX();
                    downPlayPos = viewModel.player.getCurrentPosition();
                } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)
                    dragAction(event);
                return true;
            });
        }
        setConfig(requireActivity().getResources().getConfiguration());
        screenDPI = requireContext().getResources().getDisplayMetrics().densityDpi;
    }

    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT <= 23) {//  || viewModel.player == null)) {
            initializePlayer();
            viewModel.maybePlaying = true;
        }
        hideNavigation();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT <= 23) {
            binding.playerView.onPause();
            viewModel.maybePlaying = false;
            setBookmark();
            viewModel.speed = viewModel.player.getPlaybackParameters().speed;
            releasePlayer();
        }
        viewModel.isIncreasing = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT > 23) {
            binding.playerView.onPause();
            viewModel.maybePlaying = false;
            setBookmark();
            viewModel.speed = viewModel.player.getPlaybackParameters().speed;
            releasePlayer();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setConfig(newConfig);
    }

    void setConfig(Configuration newConfig) {
        View view = requireView();
        TextView position = view.findViewById(androidx.media3.ui.R.id.exo_position);
        TextView duration = view.findViewById(R.id.my_duration);
        TextView skipDuration = view.findViewById(R.id.my_skip_duration);
        TextView skipSep = view.findViewById(R.id.my_skip_sep);
        TextView durSep = view.findViewById(R.id.my_dur_sep);
        int textSize = 14;
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
            textSize = prefTextSize;
        position.setTextSize(textSize);
        duration.setTextSize(textSize);
        skipSep.setTextSize(textSize);
        durSep.setTextSize(textSize);
        skipDuration.setTextSize(textSize);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initializePlayer() {
        if (viewModel.video.videoUrl == null)
            return;
        // Check for external subtitles
        if (subtChecked == -1) {
            subtChecked = 0;
            for (int ix = 0; ix < subtExtens.length; ix++) {
                AsyncBackendCall call = new AsyncBackendCall((taskRunner) -> {
                    // results[0] = id, results[1] = Http Response
                    Integer[] results = (Integer[]) (taskRunner.response);
                    if (results[1] == 200) {
                        subtFound[results[0]] = true;
                        Log.i(TAG, CLASS + " External subtitle found: " + subtExtens[results[0]]);
                    }
                    subtChecked++;
                });
                call.args.put("ID", ix);
                int dot = viewModel.video.videoUrl.lastIndexOf('.');
                call.args.put("URL", viewModel.video.videoUrl.substring(0, dot + 1) + subtExtens[ix]);
                call.execute(Action.TESTURL);
            }
        }
        if (subtChecked < subtExtens.length) {
            handler.postDelayed(this::initializePlayer, 100);
            return;
        }
        TextView durationView = requireView().findViewById(R.id.my_duration);
        durationView.setText(null);
        viewModel.fileLength = 0;
        MyRenderersFactory rFactory = new MyRenderersFactory(requireContext());
        int extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
        if ("mediacodec".equals(prefAudio))
            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
        else if ("ffmpeg".equals(prefAudio))
            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
        rFactory.setExtensionRendererMode(extMode);
        extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
        if ("mediacodec".equals(prefVideo))
            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
        else if ("ffmpeg".equals(prefVideo))
            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
        rFactory.setVideoExtensionRendererMode(extMode);
        rFactory.setEnableDecoderFallback(true);
        ExoPlayer.Builder builder = new ExoPlayer.Builder(requireContext(), rFactory);
        builder.setSeekBackIncrementMs(viewModel.seekBack * 1000L);
        builder.setSeekForwardIncrementMs(viewModel.seekFwd * 1000L);
        // 12 hours to cater for playback of recordings in progress
        builder.setStuckPlayingNotEndingTimeoutMs(12*60*60*1000);
        viewModel.player = builder.build();
        binding.playerView.setPlayer(viewModel.player);
        binding.playerView.setSizeGetter(viewModel);
        viewModel.playerEventListener = new PlaybackViewModel.PlayerEventListener(viewModel);
        viewModel.player.addListener(viewModel.playerEventListener);
        MediaItem mediaItem = MediaItem.fromUri(viewModel.video.videoUrl);
        MyExtractorsFactory extFactory = new MyExtractorsFactory();
        viewModel.fillTables();
        String userAgent = Util.getUserAgent(requireActivity(), "PlaybackViewModel");
        DataSource.Factory dsFactory = new MythHttpDataSource.Factory(userAgent);
        DefaultMediaSourceFactory pmf = new DefaultMediaSourceFactory
                (dsFactory, extFactory);
        //noinspection deprecation
        pmf.experimentalParseSubtitlesDuringExtraction(false);
        viewModel.mediaSource = (ProgressiveMediaSource) pmf.createMediaSource(mediaItem);
        viewModel.mediaSource.setPossibleEmptyTrack(viewModel.possibleEmptyTrack);
        // see DefaultMediaSourceFactory for non-deprecated code. Must be done
        // in conjunction with abve call to experimentalParseSubtitlesDuringExtraction
        @SuppressWarnings("deprecation")
        SingleSampleMediaSource.Factory singleSampleMediaSourceFactory =
                new SingleSampleMediaSource.Factory(dsFactory);
        ArrayList<MediaSource> msList = new ArrayList<>();
        msList.add(viewModel.mediaSource);
        for (int ix = 0 ; ix < subtExtens.length; ix++) {
            if (subtFound[ix]) {
                int dot = viewModel.video.videoUrl.lastIndexOf('.');
                Uri subtitleUri = Uri.parse(viewModel.video.videoUrl
                        .substring(0, dot+1) + subtExtens[ix]);
                MediaItem.SubtitleConfiguration subtitle =
                        new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                                .setMimeType(subtMimes[ix]) // The correct MIME type (required).
                                .setLabel("External (" + subtExtens[ix] + ")")
//                        .setLanguage(language) // The subtitle language (optional).
//                        .setSelectionFlags(selectionFlags) // Selection flags for the track (optional).
                                .build();
                MediaSource subtmediaSource =
                        singleSampleMediaSourceFactory.createMediaSource(
                                subtitle, /* durationUs= */ C.TIME_UNSET);
                msList.add(subtmediaSource);
            }
        }

        if (msList.size() > 1) {
            MediaSource [] mediaSources = new MediaSource[msList.size()];
            mediaSources = msList.toArray(mediaSources);
            MergingMediaSource mmSource = new MergingMediaSource(mediaSources);
            viewModel.player.setMediaSource(mmSource, viewModel.bookmark);
        }
        else
            viewModel.player.setMediaSource(viewModel.mediaSource, viewModel.bookmark);
        viewModel.player.setPlaybackSpeed(viewModel.speed);
        viewModel.savedDuration = 0;
        viewModel.player.prepare();
        viewModel.playbackStartTime = System.currentTimeMillis();
        viewModel.player.play();
        viewModel.startPlayback();
        viewModel.getFileLength(false);
        viewModel.startStatusMonitor();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setAudioSync() {
        boolean found = false;
        SampleQueue[] sampleQueues = viewModel.mediaSource.getSampleQueues();
        for (SampleQueue sampleQueue : sampleQueues) {
            if (sampleQueue.getUpstreamFormat() != null
                    && MimeTypes.isAudio(sampleQueue.getUpstreamFormat().sampleMimeType)) {
                sampleQueue.setSampleOffsetUs(viewModel.sampleOffsetUs);
                found = true;
            }
        }
        if (found) {
//             This check is needed to prevent it continually resetting, because
//             this routine is called again 5 seconds after doing the moveBackward
            if (viewModel.priorSampleOffsetUs != viewModel.sampleOffsetUs)
                moveBackward(50);
            viewModel.priorSampleOffsetUs = viewModel.sampleOffsetUs;
        }
    }

    public void moveBackward(int millis) {
        if (!viewModel.player.isCurrentMediaItemSeekable())
            return;
        long newPosition = viewModel.player.getCurrentPosition() - millis;
        newPosition = (newPosition < 0) ? 0 : newPosition;
        viewModel.player.seekTo(newPosition);
    }

    private void moveForward(int millis) {
        if (!viewModel.player.isCurrentMediaItemSeekable())
            return;
        long newPosition = viewModel.player.getCurrentPosition() + millis;
        newPosition = (newPosition < 0) ? 0 : newPosition;
        seekTo(newPosition);
    }

    protected void releasePlayer() {
        if (viewModel.player != null) {
            viewModel.player.release();
            viewModel.player = null;
            binding.playerView.setPlayer(/* player= */ null);
        }
    }
    public void hideNavigation () {
        if (requireActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            View view = requireView();
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
        AsyncBackendCall call = new AsyncBackendCall(null);
        call.videos.add(viewModel.video);
        call.args.put("BOOKMARK", viewModel.bookmark);
        call.args.put("POSBOOKMARK", (long) (viewModel.frameRate * 100.0f) * viewModel.bookmark / 100000);
        call.execute(Action.SET_BOOKMARK, action2);
    }

    public void markWatched() {
        AsyncBackendCall call = new AsyncBackendCall(null);
        call.videos.add(viewModel.video);
        call.execute(Action.SET_WATCHED);
    }

    void seekTo(long position) {
        if (position < 0)
            position = 100;
        boolean doReset = (viewModel.getDuration() > viewModel.player.getDuration()
                && position > viewModel.player.getDuration());
        if (position > viewModel.getDuration() - 5000) {
            position = viewModel.getDuration() - 5000;
            if (viewModel.player.getPlaybackParameters().speed > 1.0f) {
                if (viewModel.player.getCurrentPosition() > viewModel.getDuration() - 10000) {
                    viewModel.speed = 1.0f;
                    viewModel.player.setPlaybackSpeed(viewModel.speed);
                }
            }
        }
        if (!doReset) {
            viewModel.player.seekTo(position);
        }
        else {
            viewModel.bookmark = position;
            viewModel.speed = viewModel.player.getPlaybackParameters().speed;
            viewModel.player.stop();
            initializePlayer();
        }
    }

    @SuppressLint("RtlHardcoded")
    private void setupControls() {
        View previousButton = requireView().findViewById(R.id.my_exo_prev);
        if (previousButton != null) {
            previousButton.setOnClickListener((View v) -> {
                long ret = 0;
                if ("skipcom".equals(viewModel.prevnextOption)){
                    ret = viewModel.skipComBack();
                }
                if (ret == 0) {
                    viewModel.prevnextOption = null;
                    long pos = viewModel.player.getCurrentPosition() - viewModel.jump * 60000L;
                    if (pos < 0L)
                        pos = 100L;
                    seekTo(pos);
                }
                binding.playerView.showController();
            });
        }
        View nextButton = requireView().findViewById(R.id.my_exo_next);
        if (nextButton != null) {
            nextButton.setOnClickListener((View v) -> {
                long ret = 0;
                if ("skipcom".equals(viewModel.prevnextOption)){
                    ret = viewModel.skipComForward();
                }
                if (ret == 0) {
                    viewModel.prevnextOption = null;
                    long pos = viewModel.player.getCurrentPosition() + viewModel.jump * 60000L;
                    seekTo(pos);
                }
                binding.playerView.showController();
            });
        }
        View rewButton = requireView().findViewById(androidx.media3.ui.R.id.exo_rew_with_amount);
        if (rewButton != null) {
            rewButton.setOnClickListener((View v) -> {
                long pos = viewModel.player.getCurrentPosition() - viewModel.seekBack * 1000L;
                if (pos < 0L)
                    pos = 100L;
                seekTo(pos);
                binding.playerView.showController();
            });
        }
        View ffwdButton = requireView().findViewById(androidx.media3.ui.R.id.exo_ffwd_with_amount);
        if (ffwdButton != null) {
            ffwdButton.setOnClickListener((View v) -> {
                long pos = viewModel.player.getCurrentPosition() + viewModel.seekFwd * 1000L;
                if (pos < 0L)
                    pos = 100L;
                seekTo(pos);
                binding.playerView.showController();
            });
        }
        ImageView aspectButton = requireView().findViewById(R.id.my_aspect);
        if (aspectButton != null) {
            aspectButton.setOnClickListener((View v) -> {
                viewModel.currentAspectIx++;
                if (viewModel.currentAspectIx >= viewModel.aspectValues.length)
                    viewModel.currentAspectIx = 0;
                viewModel.currentAspect = viewModel.aspectValues[viewModel.currentAspectIx];
                if (contentFrame == null)
                    contentFrame = requireView().findViewById(androidx.media3.ui.R.id.exo_content_frame);
                contentFrame.setAspectRatio(viewModel.currentAspect);
                aspectButton.setImageResource(PlaybackViewModel.ASPECT_DRAWABLES[viewModel.currentAspectIx]);
                binding.playerView.showController();
            });
        }
        ImageView zoomButton = requireView().findViewById(R.id.my_zoom);
        if (zoomButton != null) {
            zoomButton.setOnClickListener((View v) -> {
                viewModel.currentResizeIx++;
                if (viewModel.currentResizeIx >= PlaybackViewModel.RESIZE_MODES.length)
                    viewModel.currentResizeIx = 0;
                viewModel.currentResizeMode = PlaybackViewModel.RESIZE_MODES[viewModel.currentResizeIx];
                if (contentFrame == null)
                    contentFrame = requireView().findViewById(androidx.media3.ui.R.id.exo_content_frame);
                contentFrame.setResizeMode(viewModel.currentResizeMode);
                zoomButton.setImageResource(PlaybackViewModel.RESIZE_DRAWABLES[viewModel.currentResizeIx]);
                binding.playerView.showController();
            });
        }
        ImageView syncButton = requireView().findViewById(R.id.my_sync);
        if (syncButton != null) {
            syncButton.setOnClickListener((vsb ) -> {
                binding.playerView.hideController();
                AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(requireContext());
//                        R.style.Theme_AppCompat_Dialog_Alert);
                dlgBuilder.setTitle(R.string.title_select_audiosync)
                        .setView(R.layout.player_seekbar)
                        .setOnDismissListener(dialogDismiss);
                dialog = dlgBuilder.create();
                dialog.show();
                Window window = dialog.getWindow();
                if (window == null)
                    return;
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
                lp.x=0;
                lp.y=0;
                lp.width= Resources.getSystem().getDisplayMetrics().widthPixels / 2;
                lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
                window.setAttributes(lp);
                window.setBackgroundDrawable(new ColorDrawable(Color.argb(192,192,192,192)));
                SeekBar seekBar = dialog.findViewById(R.id.seekbar);
                if (seekBar == null)
                    return;
                seekBar.setMax(5000); // --2500ms to +2500ms
                seekBar.setProgress((int)(viewModel.sampleOffsetUs/1000 + 2500));
                TextView seekValue = dialog.findViewById(R.id.seekbar_value);
                if (seekValue == null)
                    return;
                String text = String.format(Locale.ROOT, "%+d",viewModel.sampleOffsetUs / 1000);
                seekValue.setText(text);
                TextView textMinus = dialog.findViewById(R.id.textminus);
                if (textMinus == null)
                    return;
                textMinus.setOnClickListener((vtm )-> {
                    int value = (int)(viewModel.sampleOffsetUs/1000 + 2500);
                    if (value > 50)
                        value -= 50;
                    seekBar.setProgress(value);
                });
                TextView textPlus = dialog.findViewById(R.id.textplus);
                if (textPlus == null)
                    return;
                textPlus.setOnClickListener((vtp )-> {
                    int value = (int)(viewModel.sampleOffsetUs/1000 + 2500);
                    if (value <= 4950)
                        value += 50;
                    seekBar.setProgress(value);
                });
                seekBar.setOnSeekBarChangeListener(
                        new SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                                value = value / 50 * 50;
                                viewModel.sampleOffsetUs = ((long)value - 2500) * 1000;
                                String text1 = String.format(Locale.ROOT, "%+d",viewModel.sampleOffsetUs / 1000);
                                seekValue.setText(text1);
                                setAudioSync();
                            }
                            @Override
                            public void onStartTrackingTouch(SeekBar seekBar) {  }
                            @Override
                            public void onStopTrackingTouch(SeekBar seekBar) {  }
                        }
                );
            });
        }
        handler.postDelayed(this::enableControls, 2000);

    }

    void enableControls() {
        Resources resources = requireContext().getResources();
        float buttonAlphaEnabled =
                (float) resources.getInteger(androidx.media3.ui.R.integer.exo_media_button_opacity_percentage_enabled) / 100;
        float buttonAlphaDisabled =
                (float) resources.getInteger(androidx.media3.ui.R.integer.exo_media_button_opacity_percentage_disabled) / 100;
        if (viewModel.player == null) {
            handler.postDelayed(this::enableControls, 2000);
        } else {
            boolean canSeek = viewModel.player.isCurrentMediaItemSeekable();
            View previousButton = requireView().findViewById(R.id.my_exo_prev);
            if (previousButton != null) {
                previousButton.setEnabled(canSeek);
                previousButton.setAlpha(canSeek ? buttonAlphaEnabled : buttonAlphaDisabled);
            }
            View nextButton = requireView().findViewById(R.id.my_exo_next);
            if (nextButton != null) {
                nextButton.setEnabled(canSeek);
                nextButton.setAlpha(canSeek ? buttonAlphaEnabled : buttonAlphaDisabled);
            }
        }

    }

    private int mDialogStatus = 0;
    private static final int DIALOG_NONE   = 0;
    private static final int DIALOG_ACTIVE = 1;
    private static final int DIALOG_EXIT   = 2;
    private static final int DIALOG_RETRY  = 3;


    @OptIn(markerClass = UnstableApi.class)
    private void handlePlayerError(Exception ex, int msgNum) {
        Throwable cause = null;
        if (ex != null)
            Log.e(TAG, CLASS + " Player Error " + viewModel.video.title + " " + viewModel.video.videoUrl, ex);
        long now = System.currentTimeMillis();
        int recommendation = 0;
        boolean setPossibleEmptyTrack = false;
        if (ex instanceof ExoPlaybackException) {
            ExoPlaybackException error = (ExoPlaybackException) ex;
            switch (error.type) {
                case ExoPlaybackException.TYPE_REMOTE:
                    msgNum = R.string.pberror_remote;
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
                    cause = error.getSourceException();
                    String message = cause.getMessage();
                    if (message == null)
                        message = "";
                    if (message.startsWith("Unexpected ArrayIndexOutOfBoundsException")) {
                        msgNum = R.string.pberror_extractor_array;
                        break;
                    } else {
                        setPossibleEmptyTrack = true;
                        msgNum = R.string.pberror_source;
                    }
                    break;
                case ExoPlaybackException.TYPE_UNEXPECTED:
                    msgNum = R.string.pberror_unexpected;
                    cause = error.getUnexpectedException();
                    if (viewModel.savedCurrentPosition < 200
                            && "Playback stuck buffering and not loading".equals(cause.getMessage()))
                        setPossibleEmptyTrack = true;
                    // this error comes from fire stick 4k when selecting an mpeg level l2 audio track
                    if ("Multiple renderer media clocks enabled.".equals(cause.getMessage()))
                        recommendation = R.string.pberror_recommend_ffmpeg;
                    break;
                default:
                    msgNum = R.string.pberror_default;
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
                        || mTimeLastError < now - 30000) {
                    Toast.makeText(requireActivity(),
                                    requireActivity().getString(msgNum),
                                    Toast.LENGTH_LONG)
                            .show();
                    // if we are at the end - just end playback
                    if (failAtEnd)
                        markWatched();
                    else {
                        // Try to continue playback
                        if (currPos > 0)
                            viewModel.bookmark = currPos;
                        viewModel.speed = viewModel.player.getPlaybackParameters().speed;
                        viewModel.player.stop();
                        if (setPossibleEmptyTrack && !viewModel.possibleEmptyTrack)
                            viewModel.possibleEmptyTrack = true;
                        initializePlayer();
                    }
                    mTimeLastError = now;
                } else {
                    // More than 1 error per 30 seconds.
                    // Alert message for user to decide on continuing.
                    AlertDialogListener listener = new AlertDialogListener();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
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
                                    requireActivity().finish();
                                mDialogStatus = DIALOG_NONE;
                            });
                    builder.show();
                    mDialogStatus = DIALOG_ACTIVE;
                    mTimeLastError = 0;
                }
            }
        }
    }

    void dragAction(MotionEvent event) {
        if(!viewModel.player.isCurrentMediaItemSeekable())
            return;
        TextView skipDuration = requireView().findViewById(R.id.my_skip_duration);
        TextView skipSeparator = requireView().findViewById(R.id.my_skip_sep);
        if (dragInProgress && event.getAction() == MotionEvent.ACTION_UP) {
            skipDuration.setVisibility(View.GONE);
            skipSeparator.setVisibility(View.GONE);
            dragInProgress = false;
            return;
        }
        float distance = (event.getX() - downXPos) / contentView.getWidth();
        float absDist = Math.abs(distance);
        if (!dragInProgress) {
            float absDistInch = Math.abs((event.getX() - downXPos) / screenDPI);
            float absLeftDistInch = Math.abs(downXPos / screenDPI);
            float absRightDistInch = Math.abs(((float)contentView.getWidth() -downXPos) / screenDPI);
            if (absLeftDistInch < 0.1f || absRightDistInch < 0.1f || absDistInch < 0.1f)
                return;
            else
                dragInProgress = true;
        }
        binding.playerView.showController();
        long msecs = (long) (Math.pow(absDist,seekAccel) * seekRange);
        if (distance < 0.0f)
            msecs = -msecs;
        skipDuration.setVisibility(View.VISIBLE);
        skipSeparator.setVisibility(View.VISIBLE);
        skipDuration.setText(Util.getStringForTime(formatBuilder, formatter, msecs));
        seekTo(downPlayPos + msecs);
    }

    class AlertDialogListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    mDialogStatus = DIALOG_RETRY;
                    viewModel.bookmark = viewModel.savedCurrentPosition;
                    viewModel.speed = viewModel.player.getPlaybackParameters().speed;
                    viewModel.player.stop();
                    initializePlayer();
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    mDialogStatus = DIALOG_EXIT;
                    break;
            }
        }
    }


    class DialogDismiss implements DialogInterface.OnDismissListener {
        @Override
        public void onDismiss(DialogInterface dialogInterface) {
            if (dialog == dialogInterface)
                dialog = null;
        }
    }

    class GestureProcess extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            contentView.performClick();
            return true;
        }

        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
            binding.playerView.showController();
            float pos = e.getX() / contentView.getWidth();
            if (pos < 0.333)
                moveBackward(skipBack * 1000);
            else if (pos < 0.667)
                Util.handlePlayPauseButtonAction(viewModel.player);
            else
                moveForward(skipFwd * 1000);
            return true;
        }

    }

}