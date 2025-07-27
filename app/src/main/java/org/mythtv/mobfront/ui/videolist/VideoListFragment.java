package org.mythtv.mobfront.ui.videolist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.VolumeShaper;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.mythtv.mobfront.R;
import org.mythtv.mobfront.data.Video;
import org.mythtv.mobfront.data.VideoContract;
import org.mythtv.mobfront.databinding.FragmentVideolistBinding;
import org.mythtv.mobfront.databinding.ItemVideolistBinding;
import org.mythtv.mobfront.ui.playback.PlaybackActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
/**
 * Fragment that demonstrates a responsive layout pattern where the format of the content
 * transforms depending on the size of the screen. Specifically this Fragment shows items in
 * the [RecyclerView] using LinearLayoutManager in a small screen
 * and shows items using GridLayoutManager in a large screen.
 */
public class VideoListFragment extends Fragment {
    private static final String TAG = "mfe";
    private static final String CLASS = "FetchVideos";

    private FragmentVideolistBinding binding;
    private VideoListModel videoListModel;
    private MenuProvider menuProvider;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                if (videoListModel.pageType == VideoListModel.TYPE_SERIES) {
                    videoListModel.pageType = VideoListModel.TYPE_RECGROUP;
                    videoListModel.loadRecGroup(videoListModel.recGroup);
                    ActionBar bar = ((AppCompatActivity)getActivity()).getSupportActionBar();
                    bar.setSubtitle(videoListModel.recGroup);
                    bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE);
                }
                else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, callback);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        videoListModel =
                new ViewModelProvider(this).get(VideoListModel.class);

        binding = FragmentVideolistBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        RecyclerView recyclerView = binding.recyclerviewVideolist;
        VideoListAdapter adapter = new VideoListAdapter(this);
        recyclerView.setAdapter(adapter);
        videoListModel.videos.observe(getViewLifecycleOwner(), (list) -> {
            adapter.submitList(new ArrayList<>(list));
            binding.swiperefresh.setRefreshing(false);
        });
        binding.swiperefresh.setOnRefreshListener(() -> {
            videoListModel.startFetch();
        });
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                Log.i(TAG,"oncreatemenu");
            }
            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                menu.removeGroup(R.id.recgroup_group);
                if (videoListModel.pageType == VideoListModel.TYPE_RECGROUP) {
                    SubMenu sub = menu.addSubMenu(R.id.recgroup_group, R.id.recgroup_group, 1, "Recording Group");
                    if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED) {
                        for (int ix = 0; ix < videoListModel.recGroups.size(); ix++)
                            sub.add(R.id.recgroup_group, 0, ix, videoListModel.recGroups.get(ix));
                    }
                }
                MenuProvider.super.onPrepareMenu(menu);
            }

//            @Override
//            public void onMenuClosed(@NonNull Menu menu) {
//                MenuProvider.super.onMenuClosed(menu);
//                menu.clear();
//            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
//                switch (id) {
//                    case 0:
                if (id == 0) {
                    // case 0 means a rec group was selected
                    videoListModel.loadRecGroup(menuItem.getTitle().toString());
                    ((AppCompatActivity) getActivity()).getSupportActionBar()
                            .setSubtitle(menuItem.getTitle().toString());
                    return true;
                } else if (id == android.R.id.home) {
//                    case android.R.id.home:
                    int orientation = requireActivity().getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        return true;
                    }
                } else if (id == R.id.menu_refresh) {
                    binding.swiperefresh.setRefreshing(true);
                    videoListModel.startFetch();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (menuProvider != null)
            getActivity().addMenuProvider(menuProvider);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle("MythTV");
        if (videoListModel.pageType == VideoListModel.TYPE_RECGROUP)
            ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(videoListModel.recGroup);
        else if (videoListModel.pageType == VideoListModel.TYPE_SERIES)
            ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(videoListModel.title);
        videoListModel.refresh();
        if (videoListModel.pageType == VideoListModel.TYPE_SERIES) {
            ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            bar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
        }
    }

    @Override
    public void onPause() {
        if (menuProvider != null) {
            getActivity().removeMenuProvider(menuProvider);
            getActivity().invalidateMenu();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void onItemClick(int position) {
        Log.i(TAG,"position:" + position);
        if (videoListModel.pageType == VideoListModel.TYPE_RECGROUP) {
            videoListModel.pageType = VideoListModel.TYPE_SERIES;
            videoListModel.loadTitle(videoListModel.videoList.get(position).title);
            ActionBar bar = ((AppCompatActivity)getActivity()).getSupportActionBar();
            bar.setSubtitle(videoListModel.title);
            bar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP|ActionBar.DISPLAY_SHOW_TITLE);
        }
        else if (videoListModel.pageType == VideoListModel.TYPE_SERIES) {
            Activity activity = getActivity();
            Intent intent = new Intent(activity, PlaybackActivity.class);
            intent.putExtra(PlaybackActivity.VIDEO, videoListModel.videoList.get(position));
//            intent.putExtra(PlaybackActivity.VIDEO, taskRunner.getVideo());
//            intent.putExtra(PlaybackActivity.BOOKMARK, bookmark);
//            intent.putExtra(PlaybackActivity.POSBOOKMARK, posBookmark);
            activity.startActivity(intent);
        }
    }

    private static class VideoListAdapter extends ListAdapter<Video, VideoListViewHolder> {

        private VideoListFragment fragment;

        protected VideoListAdapter(VideoListFragment fragment) {
            super(new DiffUtil.ItemCallback<Video>() {
                @Override
                public boolean areItemsTheSame(@NonNull Video oldItem, @NonNull Video newItem) {
                    if (oldItem.id == -1 && newItem.id == -1)
                        return oldItem.title.equals(newItem.title);
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Video oldItem, @NonNull Video newItem) {
                    return oldItem.id == newItem.id;
                }
            });
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public VideoListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemVideolistBinding binding = ItemVideolistBinding.inflate(LayoutInflater.from(parent.getContext()));
            return new VideoListViewHolder(binding, fragment);
        }

        @Override
        public void onBindViewHolder(@NonNull VideoListViewHolder holder, int position) {
            Video video = getItem(position);
            if (video.type == Video.TYPE_SERIES)
                holder.textView.setText(video.title);
            else if (video.type == Video.TYPE_EPISODE) {
                StringBuilder subtitle = new StringBuilder();
//                if (video.isDamaged())
//                    subtitle.append("\uD83D\uDCA5");
                if (video.isWatched())
                    subtitle.append("\uD83D\uDC41");
                // symbols for deleted - "🗑" "🗶" "␡"
                if (video.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
                        && "Deleted".equals(video.recGroup))
                    subtitle.append("\uD83D\uDDD1");
                if (video.season != null && video.season.compareTo("0") > 0) {
                    subtitle.append('S').append(video.season).append('E').append(video.episode)
                            .append(' ');
                }
                subtitle.append(video.subtitle);
                holder.textView.setText(subtitle);
            }
//            holder.position = position;
            String url = video.cardImageUrl;
            Glide.with(fragment)
                    .load(url)
                    .timeout(10000)
                    .into(holder.imageView);
        }
    }

    private static class VideoListViewHolder extends RecyclerView.ViewHolder {

//        private Fragment fragment;
        private final ImageView imageView;
        private final TextView textView;
//        private int position;

        public VideoListViewHolder(ItemVideolistBinding binding, VideoListFragment fragment) {
            super(binding.getRoot());
            imageView = binding.imageViewItemVideolist;
            textView = binding.textViewItemVideolist;
            binding.getRoot().setOnClickListener( (view) ->  {
                // If this does not work try getAbsoluteAdapterPosition
                fragment.onItemClick( getBindingAdapterPosition() );
            });
        }
    }
}