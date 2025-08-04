package org.mythtv.mobfront.ui.videolist;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;

import org.mythtv.mobfront.R;
import org.mythtv.mobfront.data.Action;
import org.mythtv.mobfront.data.AsyncBackendCall;
import org.mythtv.mobfront.data.BackendCache;
import org.mythtv.mobfront.data.Video;
import org.mythtv.mobfront.data.VideoContract;
import org.mythtv.mobfront.data.XmlNode;
import org.mythtv.mobfront.databinding.FragmentVideolistBinding;
import org.mythtv.mobfront.databinding.ItemVideolistBinding;
import org.mythtv.mobfront.ui.playback.PlaybackActivity;

import java.util.ArrayList;
import java.util.Objects;

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
    private ArrayList <Video> videoList = new ArrayList<>();


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                if (videoListModel.pageType == VideoListModel.TYPE_SERIES) {
                    videoListModel.pageType = VideoListModel.TYPE_RECGROUP;
                    videoListModel.setRecGroup(videoListModel.recGroup);
                    refresh();
                }
                else if (videoListModel.pageType == VideoListModel.TYPE_VIDEODIR
                    && videoListModel.videoPath.length() > 0) {
                    videoListModel.setVideos("..");
                    refresh();
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
            synchronized(videoListModel) {
                adapter.submitList(videoList = new ArrayList<>(list));
            }
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
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                menu.removeGroup(R.id.recgroup_group);
                if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED) {
                    for (int ix = 0; ix < videoListModel.recGroups.size(); ix++)
                        menu.add(R.id.recgroup_group, 0, ix, videoListModel.recGroups.get(ix));
                }
//                }
                MenuProvider.super.onPrepareMenu(menu);
            }
            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == 0) {
                    videoListModel.setRecGroup(menuItem.getTitle().toString());
                    refresh();
                    return true;
                } else if (id == android.R.id.home) {
                    int orientation = requireActivity().getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        return true;
                    }
                } else if (id == R.id.menu_refresh) {
                    if (binding != null && binding.swiperefresh != null) {
                        binding.swiperefresh.setRefreshing(true);
                        videoListModel.startFetch();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    void refresh() {
        ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        bar.setSubtitle(videoListModel.title);
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if ((videoListModel.pageType == VideoListModel.TYPE_SERIES
            || (videoListModel.pageType == VideoListModel.TYPE_VIDEODIR
                && videoListModel.videoPath.length() > 0)))
                bar.setDisplayHomeAsUpEnabled(true);
            else
                bar.setDisplayHomeAsUpEnabled(false);
        }
        videoListModel.refresh();
    }


    @Override
    public void onResume() {
        super.onResume();
        if (menuProvider != null) {
            getActivity().addMenuProvider(menuProvider);
//            getActivity().invalidateMenu();
        }
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle("MythTV");
        if (videoListModel.pageType == VideoListModel.TYPE_RECGROUP)
            ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(videoListModel.recGroup);
        else if (videoListModel.pageType == VideoListModel.TYPE_SERIES)
            ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(videoListModel.title);
        videoListModel.refresh();
        refresh();
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
        if (videoListModel.pageType == VideoListModel.TYPE_RECGROUP) {
            videoListModel.pageType = VideoListModel.TYPE_SERIES;
            videoListModel.setTitle(videoList.get(position).title);
            refresh();
        }
        else if (videoListModel.pageType == VideoListModel.TYPE_SERIES) {
            play(videoList.get(position));
        }
        else if (videoListModel.pageType == VideoListModel.TYPE_VIDEODIR) {
            if (videoList.get(position).type == Video.TYPE_VIDEODIR) {
                videoListModel.setVideos(videoList.get(position).title);
                refresh();
            }
            else if (videoList.get(position).type == Video.TYPE_VIDEO) {
                play(videoList.get(position));
            }
        }
    }

//    static final String [] XMLTAGS_VIDEO_INFO = {"VideoStreamInfos","VideoStreamInfo"};
    private void play(Video video) {
        AsyncBackendCall call = new AsyncBackendCall(getActivity(),
                taskRunner -> {
            long [] bookmark = (long[])taskRunner.response;
            if (bookmark[0] == -1 && bookmark[1] == -1) {
                Toast.makeText(getContext(), R.string.error_cannot_play,Toast.LENGTH_LONG).show();
                return;
            }
            XmlNode streamInfo = taskRunner.getXmlResult();
            double frameRate = 0.0;
            double avgFrameRate = 0.0;
            if (streamInfo != null) {
                XmlNode vsi = streamInfo.getNode("VideoStreamInfos");
                vsi = vsi.getNode("VideoStreamInfo");
                while (vsi != null && !"V".equals(vsi.getString("CodecType")))
                    vsi = vsi.getNextSibling();
                frameRate = Double.valueOf(vsi.getString("FrameRate"));
                avgFrameRate = Double.valueOf(vsi.getString("AvgFrameRate"));
            }
            if (frameRate == 0.0)
                frameRate = avgFrameRate;
            if (frameRate == 0.0)
                frameRate = 30.0;
            if (bookmark[0] <= 0 && bookmark[1] > 0) {
                // Need to convert pos bookmark
                bookmark[0] = bookmark[1] * 100000 / (long) (frameRate * 100.0f);
            }
            Activity activity = getActivity();
            Intent intent = new Intent(activity, PlaybackActivity.class);
            intent.putExtra(PlaybackActivity.VIDEO, video);
//            intent.putExtra(PlaybackActivity.VIDEO, taskRunner.getVideo());
            intent.putExtra(PlaybackActivity.BOOKMARK, bookmark[0]);
            intent.putExtra(PlaybackActivity.FRAMERATE, frameRate);
//                intent.putExtra(PlaybackActivity.POSBOOKMARK, bookmark[1]);
            activity.startActivity(intent);

        });
        call.videos.add(video);
        call.execute(Action.GET_STREAM_INFO, Action.GET_BOOKMARK);

    }

    static String getEpisodeSubtitle(Video video) {
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
        else if (video.airdate!= null) {
            subtitle.append(video.airdate).append(" ");
        }
        if (video.subtitle == null || video.subtitle.trim().length() == 0
                || video.type == Video.TYPE_VIDEO)
            subtitle.append(video.title).append(": ");
        subtitle.append(video.subtitle);
        return subtitle.toString();
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
                    return getEpisodeSubtitle(oldItem).equals(getEpisodeSubtitle(newItem))
                            && Objects.equals(oldItem.cardImageUrl, newItem.cardImageUrl);
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
                holder.textView.setText(getEpisodeSubtitle(video));
            }
            else if (video.type == Video.TYPE_VIDEO) {
                holder.textView.setText(getEpisodeSubtitle(video));
            }
            else if (video.type == Video.TYPE_VIDEODIR) {
                holder.textView.setText(video.title);
            }
            else
                holder.textView.setText(String.valueOf(video.type));

            if (video.type == Video.TYPE_VIDEODIR) {
                holder.imageView.setImageResource(R.drawable.ic_folder_24px);
            }
            else {

                String imageUrl = video.cardImageUrl;
                if (imageUrl == null) {
                    holder.imageView.setImageResource(R.drawable.ic_movie_24px);
                }
                else {
                    RequestOptions options = new RequestOptions()
                            .error(R.drawable.ic_movie_24px);

                    String auth =  BackendCache.getInstance().authorization;
                    LazyHeaders.Builder lzhb =  new LazyHeaders.Builder();
                    if (auth != null && auth.length() > 0)
                        lzhb.addHeader("Authorization", auth);
                    GlideUrl url = new GlideUrl(imageUrl, lzhb.build());

                    Glide.with(fragment.getContext())
                            .load(url)
                            .apply(options)
                            .into(holder.imageView);
                }
            }
        }
    }

    private static class VideoListViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imageView;
        private final TextView textView;

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