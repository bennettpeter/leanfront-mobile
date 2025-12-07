package org.mythtv.lfmobile.ui.upcoming;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.mythtv.lfmobile.MainActivity;
import org.mythtv.lfmobile.MyApplication;
import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.databinding.FragmentUpcomingBinding;
import org.mythtv.lfmobile.databinding.ItemUpcomingBinding;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

/**
/**
 * Fragment that demonstrates a responsive layout pattern where the format of the content
 * transforms depending on the size of the screen. Specifically this Fragment shows items in
 * the [RecyclerView] using LinearLayoutManager in a small screen
 * and shows items using GridLayoutManager in a large screen.
 */
public class UpcomingListFragment extends MainActivity.MyFragment {
    private static final String TAG = "lfm";
    private static final String CLASS = "UpcomingListFragment";
    private FragmentUpcomingBinding binding;
    private UpcomingListModel model;
    private ArrayList<UpcomingListModel.UpcomingItem> upcomingList;
    public static final int COLOR_WILLRECORD = 0xff00C000;
    public static final int COLOR_WONTRECORD = 0xffC00000;
    private MenuProvider menuProvider;
    private int orientation;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        model = new ViewModelProvider(this).get(UpcomingListModel.class);
        Bundle args = getArguments();
        if (args != null)
            model.type = args.getInt("type");
        binding = FragmentUpcomingBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        RecyclerView recyclerView = binding.recyclerviewUpcominglist;
        UpcomingListAdapter adapter = new UpcomingListAdapter(this);
        recyclerView.setAdapter(adapter);
        model.upcomings.observe(getViewLifecycleOwner(), (list) -> {
            synchronized(model) {
                adapter.submitList(upcomingList = new ArrayList<>(list));
            }
            binding.swiperefresh.setRefreshing(false);
        });
        binding.swiperefresh.setOnRefreshListener(() -> {
            model.startFetch();
        });
        DividerItemDecoration dec = new DividerItemDecoration(recyclerView.getContext(),
               DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dec);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        orientation = requireActivity().getResources().getConfiguration().orientation;
        int spanCount = 1;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            spanCount = 2;
        ((GridLayoutManager)binding.recyclerviewUpcominglist.getLayoutManager()).setSpanCount(spanCount);
        menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                if (model.type == UpcomingListModel.TYPE_GUIDE_SEARCH) {
                    MenuItem searchItem = menu.findItem(R.id.search);
                    if (searchItem != null) {
                        searchItem.setVisible(true);
                        searchItem.expandActionView();
                        SearchView searchView = (SearchView) searchItem.getActionView();
                        searchView.setQueryHint(getString(R.string.hint_guide_search));
                        searchView.setIconifiedByDefault(false);
                        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                            @Override
                            public boolean onQueryTextSubmit(String query) {
                                model.search = query;
                                refresh();
                                return true;
                            }
                            @Override
                            public boolean onQueryTextChange(String newText) {
                                if (newText.trim().length() > 2) {
                                    model.search = newText;
                                    refresh();
                                    return true;
                                }
                                return false;
                            }
                        });
                        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                            @Override
                            public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
                                return true;
                            }

                            @Override
                            public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
                                NavHostFragment navHostFragment =
                                        (NavHostFragment) getActivity().getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
                                NavController navController = navHostFragment.getNavController();
                                navController.navigateUp();
                                return false;
                            }
                        });
                    }
                    MenuItem refreshItem = menu.findItem(R.id.menu_refresh);
                    if (refreshItem != null)
                        refreshItem.setVisible(false);
                }
                if (model.type == UpcomingListModel.TYPE_UPCOMING) {
                    MenuItem item = menu.add(R.id.upcoming_group, R.id.id_show_all, Menu.NONE, R.string.menu_show_all);
                    item.setCheckable(true);
                    item.setChecked(model.showAll);
                }
            }
            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuProvider.super.onPrepareMenu(menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.id_show_all) {
                    model.showAll = !menuItem.isChecked();
                    menuItem.setChecked(model.showAll);
                    model.startFetch();
                    return true;
                }
                return false;
            }
        };
    }


    void refresh() {
        model.startFetch();
    }


    @Override
    public void onResume() {
        super.onResume();
        ((MainActivity)getActivity()).myFragment = this;
        if (menuProvider != null) {
            getActivity().addMenuProvider(menuProvider,getViewLifecycleOwner());
        }
        ((MainActivity)getActivity()).myFragment = this;
        ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(null);
        model.startFetch();
    }

    @Override
    public void onPause() {
        ((MainActivity)getActivity()).myFragment = null;
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

    public void startFetch() {
        if (binding != null && binding.swiperefresh != null) {
            binding.swiperefresh.setRefreshing(true);
            model.startFetch();
        }
    }

    private static class UpcomingListAdapter extends ListAdapter
            <UpcomingListModel.UpcomingItem, UpcomingListViewHolder> {

        private UpcomingListFragment fragment;
        private float defaultTextSize = 15.0f;
        private float largeTextSize = 20.0f;

        protected UpcomingListAdapter(UpcomingListFragment fragment) {
            super(new DiffUtil.ItemCallback<UpcomingListModel.UpcomingItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull UpcomingListModel.UpcomingItem oldItem, @NonNull UpcomingListModel.UpcomingItem newItem) {
                    return (Objects.equals(oldItem.startTime,newItem.startTime)
                        && Objects.equals(oldItem.title,newItem.title)
                        && oldItem.chanId == newItem.chanId);
               }
                @Override
                public boolean areContentsTheSame(@NonNull UpcomingListModel.UpcomingItem oldItem, @NonNull UpcomingListModel.UpcomingItem newItem) {
                    return Objects.equals(oldItem.statusName,newItem.statusName);
                }
            });
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public UpcomingListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemUpcomingBinding binding
                    = ItemUpcomingBinding.inflate(LayoutInflater.from(parent.getContext()));
            return new UpcomingListViewHolder(binding, fragment);
        }

        @Override
        public void onBindViewHolder(@NonNull UpcomingListViewHolder holder, int position) {
            UpcomingListModel.UpcomingItem item = getItem(position);
            holder.itemDateView.setText(null);
            StringBuilder dateStr = new StringBuilder();
            if (item.startTime!= null) {
                try {
                    final SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
                    final SimpleDateFormat weekDay = new SimpleDateFormat("E");
                    final SimpleDateFormat timeOfDay = new SimpleDateFormat("HH:mm");
                    final DateFormat outFormat = android.text.format.DateFormat.getMediumDateFormat
                            (MyApplication.getAppContext());
                    Date date = dbFormat.parse(item.startTime + "+0000");
                    dateStr.append(weekDay.format(date)).append(" ")
                            .append(outFormat.format(date)).append(" ")
                            .append(timeOfDay.format(date)).append(" ")
                            .append(item.callSign).append(" ")
                            .append(item.chanNum);
                    holder.itemDateView.setText(dateStr);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
            holder.itemStatusView.setText(item.statusName);
            int color;
            // Recorded = -3, Recording = -2, WillRecord = -1,
            if (item.status == -3
                    || item.status == -2
                    || item.status == -1)
                color = COLOR_WILLRECORD;
            else
                color = COLOR_WONTRECORD;
            holder.itemStatusView.setTextColor(color);

            StringBuilder titleStr = new StringBuilder();
            titleStr.append(item.title);
            boolean haveEpisode = item.episode > 0;
            boolean haveSubtitle = item.subTitle != null && item.subTitle.trim().length() > 0;
            if (haveEpisode || haveSubtitle)
                titleStr.append(": ");
            if (haveEpisode) {
                titleStr.append('S').append(item.season).append('E').append(item.episode)
                        .append(' ');
            }
            if (haveSubtitle)
                titleStr.append(item.subTitle);
            holder.itemTitleView.setText(titleStr);
            holder.itemDescView.setText(item.description);
        }
    }

    private static class UpcomingListViewHolder extends RecyclerView.ViewHolder {
        private final TextView itemDateView;
        private final TextView itemStatusView;
        private final TextView itemTitleView;
        private final TextView itemDescView;

        public UpcomingListViewHolder(ItemUpcomingBinding binding, UpcomingListFragment fragment) {
            super(binding.getRoot());
            itemTitleView = binding.itemTitle;
            itemDateView = binding.itemDate;
            itemDescView = binding.itemDesc;
            itemStatusView = binding.itemStatus;
        }
    }
}