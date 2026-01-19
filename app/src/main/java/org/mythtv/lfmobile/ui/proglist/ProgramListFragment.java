package org.mythtv.lfmobile.ui.proglist;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuProvider;
import androidx.drawerlayout.widget.DrawerLayout;
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
import org.mythtv.lfmobile.databinding.FragmentProglistBinding;
import org.mythtv.lfmobile.databinding.ItemProgramBinding;
import org.mythtv.lfmobile.ui.schedule.ScheduleViewModel;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

/**
 * Program List fragment that is used by upcoming list and guide search results
 */
public class ProgramListFragment extends MainActivity.MyFragment {
    private static final String TAG = "lfm";
    private static final String CLASS = "ProgramListFragment";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
    private FragmentProglistBinding binding;
    private ProgramListModel model;
    public static final int COLOR_WILLRECORD = 0xff00C000;
    public static final int COLOR_WONTRECORD = 0xffC00000;
    private MenuProvider menuProvider;
    private int orientation;
    private boolean hideNav = false;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        model = new ViewModelProvider(this).get(ProgramListModel.class);
        Bundle args = getArguments();
        if (args != null)
            model.type = args.getInt("type");
        binding = FragmentProglistBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        RecyclerView recyclerView = binding.recyclerviewProgramlist;
        ProgramListAdapter adapter = new ProgramListAdapter(this);
        recyclerView.setAdapter(adapter);
        model.programs.observe(getViewLifecycleOwner(), (list) -> {
            synchronized(model) {
                adapter.submitList(new ArrayList<>(list));
            }
            binding.swiperefresh.setRefreshing(false);
        });
        binding.swiperefresh.setOnRefreshListener(() -> {
            refresh();
        });
        DividerItemDecoration dec1 = new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dec1);
        DividerItemDecoration dec2 = new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.HORIZONTAL);
        recyclerView.addItemDecoration(dec2);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        orientation = requireActivity().getResources().getConfiguration().orientation;
        int spanCount = 1;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            spanCount = 2;
        ((GridLayoutManager)binding.recyclerviewProgramlist.getLayoutManager()).setSpanCount(spanCount);
        menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                if (model.type == ProgramListModel.TYPE_GUIDE_SEARCH) {
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
                if (model.type == ProgramListModel.TYPE_UPCOMING) {
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
                    refresh();
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
        ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(null);
        if (model.type == ProgramListModel.TYPE_GUIDE_SEARCH) {
            View v = ((MainActivity) getActivity()).mainView;
            View nav = v.findViewById(R.id.bottom_nav_view);
            if (nav != null) {
                if (nav.getVisibility() == View.VISIBLE) {
                    hideNav = true;
                    nav.setVisibility(View.GONE);
                }
            }
            DrawerLayout drawer = v.findViewById(R.id.drawer_layout);
            if (drawer != null) {
                if (drawer.getDrawerLockMode(Gravity.LEFT) == DrawerLayout.LOCK_MODE_UNLOCKED) {
                    hideNav = true;
                    drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                }
            }
        }
        refresh();
    }

    @Override
    public void onPause() {
        ((MainActivity)getActivity()).myFragment = null;
        if (menuProvider != null) {
            getActivity().removeMenuProvider(menuProvider);
            getActivity().invalidateMenu();
        }
        if (hideNav) {
            View v = ((MainActivity) getActivity()).mainView;
            View nav = v.findViewById(R.id.bottom_nav_view);
            if (nav != null)
                nav.setVisibility(View.VISIBLE);
            DrawerLayout drawer = v.findViewById(R.id.drawer_layout);
            if (drawer != null)
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    public void startFetch() {
        if (binding != null && binding.swiperefresh != null) {
            binding.swiperefresh.setRefreshing(true);
            refresh();
        }
    }

    private static class ProgramListAdapter extends ListAdapter
            <ProgramListModel.ProgramItem, ProgramListViewHolder> {

        private ProgramListFragment fragment;

        protected ProgramListAdapter(ProgramListFragment fragment) {
            super(new DiffUtil.ItemCallback<ProgramListModel.ProgramItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull ProgramListModel.ProgramItem oldItem, @NonNull ProgramListModel.ProgramItem newItem) {
                    return (Objects.equals(oldItem.startTime,newItem.startTime)
                        && Objects.equals(oldItem.title,newItem.title)
                        && oldItem.chanId == newItem.chanId);
               }
                @Override
                public boolean areContentsTheSame(@NonNull ProgramListModel.ProgramItem oldItem, @NonNull ProgramListModel.ProgramItem newItem) {
                    return Objects.equals(oldItem.statusName,newItem.statusName);
                }
            });
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public ProgramListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemProgramBinding binding
                    = ItemProgramBinding.inflate(LayoutInflater.from(parent.getContext()));
            return new ProgramListViewHolder(binding, fragment);
        }

        @Override
        public void onBindViewHolder(@NonNull ProgramListViewHolder holder, int position) {
            holder.item = getItem(position);
            holder.binding.itemDate.setText(null);
            StringBuilder dateStr = new StringBuilder();
            if (holder.item.startTime!= null) {
                try {
                    final SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
                    final SimpleDateFormat weekDay = new SimpleDateFormat("E");
                    final SimpleDateFormat timeOfDay = new SimpleDateFormat("HH:mm");
                    final DateFormat outFormat = android.text.format.DateFormat.getMediumDateFormat
                            (MyApplication.getAppContext());
                    Date date = dbFormat.parse(holder.item.startTime + "+0000");
                    dateStr.append(weekDay.format(date)).append(" ")
                            .append(outFormat.format(date)).append(" ")
                            .append(timeOfDay.format(date)).append(" ")
                            .append(holder.item.callSign).append(" ")
                            .append(holder.item.chanNum);
                    holder.binding.itemDate.setText(dateStr);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
            holder.binding.itemStatus.setText(holder.item.statusName);
            int color;
            // Recorded = -3, Recording = -2, WillRecord = -1,
            if (holder.item.status == -3
                    || holder.item.status == -2
                    || holder.item.status == -1)
                color = COLOR_WILLRECORD;
            else
                color = COLOR_WONTRECORD;
            holder.binding.itemStatus.setTextColor(color);

            StringBuilder titleStr = new StringBuilder();
            titleStr.append(holder.item.title);
            boolean haveEpisode = holder.item.episode > 0;
            boolean haveSubtitle = holder.item.subTitle != null && holder.item.subTitle.trim().length() > 0;
            if (haveEpisode || haveSubtitle)
                titleStr.append(": ");
            if (haveEpisode) {
                titleStr.append('S').append(holder.item.season).append('E').append(holder.item.episode)
                        .append(' ');
            }
            if (haveSubtitle)
                titleStr.append(holder.item.subTitle);
            holder.binding.itemTitle.setText(titleStr);
            holder.binding.itemDesc.setText(holder.item.description);
            if (holder.item.recordId == 0) {
                holder.binding.itemPaperclip.setVisibility(View.GONE);
            } else {
                holder.binding.itemPaperclip.setVisibility(View.VISIBLE);
            }
        }
    }

    private static class ProgramListViewHolder extends RecyclerView.ViewHolder {
        private final ItemProgramBinding binding;
        private ProgramListModel.ProgramItem item;

        public ProgramListViewHolder(ItemProgramBinding binding, ProgramListFragment fragment) {
            super(binding.getRoot());
            this.binding = binding;
            View.OnClickListener listener  = v -> {
                if (item == null) {
                    Toast.makeText(fragment.getContext(),
                            R.string.guide_noprog, Toast.LENGTH_LONG).show();
                    return;
                }
                actionRequest(fragment, v);

            };
            binding.getRoot().setOnClickListener(listener);
            binding.itemPaperclip.setOnClickListener(listener);
        }
        private void actionRequest(ProgramListFragment fragment, View v) {
            try {
                Bundle args = new Bundle();
                args.putLong(ScheduleViewModel.REQID, System.currentTimeMillis());
                args.putInt(ScheduleViewModel.CHANID, item.chanId);
                Date startTime = dateFormat.parse(item.startTime + "+0000");
                args.putSerializable(ScheduleViewModel.STARTTIME, startTime);
                if (v == binding.itemPaperclip)
                    args.putBoolean(ScheduleViewModel.ISOVERRIDE, true);
                switch (fragment.model.type) {
                    case ProgramListModel.TYPE_GUIDE_SEARCH:
                        args.putInt(ScheduleViewModel.SCHEDTYPE, ScheduleViewModel.SCHED_GUIDE);
                        break;
                    case ProgramListModel.TYPE_UPCOMING:
                        args.putInt(ScheduleViewModel.SCHEDTYPE, ScheduleViewModel.SCHED_UPCOMING);
                        break;
                }
                NavHostFragment navHostFragment =
                        (NavHostFragment) fragment.getActivity().getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
                NavController navController = navHostFragment.getNavController();
                navController.navigate(R.id.nav_schedule, args);
            } catch (Exception e) {
                Log.e(TAG, CLASS + " Exception setting up schedule edit.", e);
                e.printStackTrace();
            }
        }

    }
}