package org.mythtv.lfmobile.ui.recrules;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.mythtv.lfmobile.MainActivity;
import org.mythtv.lfmobile.MyApplication;
import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.databinding.FragmentProglistBinding;
import org.mythtv.lfmobile.databinding.FragmentRulelistBinding;
import org.mythtv.lfmobile.databinding.ItemProgramBinding;
import org.mythtv.lfmobile.databinding.ItemRecruleBinding;
import org.mythtv.lfmobile.ui.proglist.ProgramListFragment;
import org.mythtv.lfmobile.ui.proglist.ProgramListModel;
import org.mythtv.lfmobile.ui.schedule.ScheduleViewModel;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class RuleListFragment extends MainActivity.MyFragment {
    private static final String TAG = "lfm";
    private static final String CLASS = "RuleListFragment";
    private MenuProvider menuProvider;
    private int orientation;
    private RuleListViewModel model;
    private FragmentRulelistBinding binding;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(this).get(RuleListViewModel.class);
        binding = FragmentRulelistBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        RecyclerView recyclerView = binding.recyclerviewRulelist;
        RuleListAdapter adapter = new RuleListFragment.RuleListAdapter(this);
        recyclerView.setAdapter(adapter);
        model.rules.observe(getViewLifecycleOwner(), (list) -> {
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
        ((GridLayoutManager)binding.recyclerviewRulelist.getLayoutManager()).setSpanCount(spanCount);
        menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                MenuItem item = menu.add(R.id.recrules_group, R.id.id_new_rule, 0, R.string.menu_new_recrule);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
//                menu.removeGroup(R.id.recrules_group);
//                MenuItem item = menu.add(R.id.recrules_group, R.id.id_new_rule, 0, R.string.menu_new_recrule);
                MenuProvider.super.onPrepareMenu(menu);
            }
            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                // TODO: Implement new rule process
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
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.title_recrules);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(null);

        refresh();
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

    private static class RuleListAdapter extends ListAdapter
            <RuleListViewModel.RuleItem, RuleListFragment.RuleListViewHolder> {
        private RuleListFragment fragment;
        static final SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
        static final SimpleDateFormat weekDay = new SimpleDateFormat("E");
        static final SimpleDateFormat timeOfDay = new SimpleDateFormat("HH:mm");
        static final DateFormat outFormat = android.text.format.DateFormat.getMediumDateFormat
                (MyApplication.getAppContext());

        protected RuleListAdapter(RuleListFragment fragment) {
            super(new DiffUtil.ItemCallback<RuleListViewModel.RuleItem>() {

                @Override
                public boolean areItemsTheSame(@NonNull RuleListViewModel.RuleItem oldItem, @NonNull RuleListViewModel.RuleItem newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull RuleListViewModel.RuleItem oldItem, @NonNull RuleListViewModel.RuleItem newItem) {
                    return false;
                }
            });
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public RuleListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemRecruleBinding binding
                    = ItemRecruleBinding.inflate(LayoutInflater.from(parent.getContext()));
            return new RuleListViewHolder(binding, fragment);
        }

        @Override
        public void onBindViewHolder(@NonNull RuleListViewHolder holder, int position) {

            holder.item = getItem(position);
            holder.binding.itemTitle.setText(holder.item.title);
            holder.binding.itemLastTitle.setVisibility(View.GONE);
            holder.binding.itemLast.setVisibility(View.GONE);
            holder.binding.itemLast.setText(null);
            if (holder.item.lastRecorded != null) {
                try {
                    StringBuilder dateStr = new StringBuilder();
                    Date date = dbFormat.parse(holder.item.lastRecorded + "+0000");
                    dateStr.append(weekDay.format(date)).append(" ")
                            .append(outFormat.format(date)).append(" ")
                            .append(timeOfDay.format(date));
                    holder.binding.itemLast.setText(dateStr);
                    holder.binding.itemLastTitle.setVisibility(View.VISIBLE);
                    holder.binding.itemLast.setVisibility(View.VISIBLE);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }

            holder.binding.itemNextTitle.setVisibility(View.GONE);
            holder.binding.itemNext.setVisibility(View.GONE);
            holder.binding.itemNext.setText(null);
            if (holder.item.nextRecording != null) {
                try {
                    StringBuilder dateStr = new StringBuilder();
                    Date date = dbFormat.parse(holder.item.nextRecording + "+0000");
                    dateStr.append(weekDay.format(date)).append(" ")
                            .append(outFormat.format(date)).append(" ")
                            .append(timeOfDay.format(date));
                    holder.binding.itemNext.setText(dateStr);
                    holder.binding.itemNextTitle.setVisibility(View.VISIBLE);
                    holder.binding.itemNext.setVisibility(View.VISIBLE);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
            String typeKey = "recrule_" + holder.item.type.replace(" ","");
            Context context = MyApplication.getAppContext();
            int tValue = context.getResources().getIdentifier(typeKey,"string",context.getPackageName());
            if (tValue == 0)
                holder.binding.itemType.setText(holder.item.type);
            else
                holder.binding.itemType.setText(tValue);
            if (holder.item.inactive)
                holder.binding.itemInactive.setVisibility(View.VISIBLE);
            else
                holder.binding.itemInactive.setVisibility(View.GONE);
        }
    }
    private static class RuleListViewHolder extends RecyclerView.ViewHolder {
        private final ItemRecruleBinding binding;
        private  RuleListViewModel.RuleItem item;

        public RuleListViewHolder(ItemRecruleBinding binding, RuleListFragment fragment) {
            super(binding.getRoot());
            this.binding = binding;
            View.OnClickListener listener  = v -> {
                actionRequest(fragment, v);

            };
            binding.getRoot().setOnClickListener(listener);
        }

        private void actionRequest(RuleListFragment fragment, View v) {
            try {
                Bundle args = new Bundle();
                args.putLong(ScheduleViewModel.REQID, System.currentTimeMillis());
                args.putInt(ScheduleViewModel.RECORDID, item.id);
                args.putInt(ScheduleViewModel.SCHEDTYPE, ScheduleViewModel.SCHED_RULELIST);

//                args.putInt(ScheduleViewModel.CHANID, item.chanId);
//                Date startTime = dateFormat.parse(item.startTime + "+0000");
//                args.putSerializable(ScheduleViewModel.STARTTIME, startTime);
//                if (v == binding.itemPaperclip)
//                    args.putBoolean(ScheduleViewModel.ISOVERRIDE, true);
//                args.putInt(ScheduleViewModel.SCHEDTYPE, ScheduleViewModel.SCHED_GUIDE);
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