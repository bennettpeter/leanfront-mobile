package org.mythtv.mobfront.ui.playback;

import androidx.lifecycle.ViewModelProvider;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.SubtitleView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.mythtv.mobfront.R;
import org.mythtv.mobfront.databinding.FragmentPlaybackBinding;
import org.mythtv.mobfront.databinding.FragmentVideolistBinding;

import java.util.Collections;

public class PlaybackFragment extends Fragment {

    private PlaybackViewModel viewModel;
    private FragmentPlaybackBinding binding;

    public static PlaybackFragment newInstance() {
        return new PlaybackFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);
        viewModel.video = getActivity().getIntent().getParcelableExtra(PlaybackActivity.VIDEO);
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
            releasePlayer();
        }
    }

    private void initializePlayer(boolean enableControls) {
//        Log.i(TAG, CLASS + " Initializing Player for " + mVideo.title + " " + mVideo.videoUrl);
//        mTrackSelector = new DefaultTrackSelector(getContext());
//        DefaultRenderersFactory rFactory = new DefaultRenderersFactory(getContext());
//        int extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
//        if ("mediacodec".equals(mAudio))
//            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
//        else if ("ffmpeg".equals(mAudio))
//            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
//        rFactory.setExtensionRendererMode(extMode);
//        rFactory.setEnableDecoderFallback(true);
//        ExoPlayer.Builder builder = new ExoPlayer.Builder(getContext(),rFactory);
        ExoPlayer.Builder builder = new ExoPlayer.Builder(getContext());
//        builder.setTrackSelector(mTrackSelector);
        viewModel.player = builder.build();
        binding.playerView.setPlayer(viewModel.player);

        MediaItem mediaItem = MediaItem.fromUri(viewModel.video.videoUrl);
        viewModel.player.setMediaItem(mediaItem);
        viewModel.player.prepare();
        viewModel.player.play();

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
    }

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
//        if (clientSideAdsLoader != null) {
//            clientSideAdsLoader.setPlayer(null);
//        } else {
//            binding.playerView.getAdViewGroup().removeAllViews();
//        }
    }
    public void hideNavigation () {
        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            View view = getView();
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }


}