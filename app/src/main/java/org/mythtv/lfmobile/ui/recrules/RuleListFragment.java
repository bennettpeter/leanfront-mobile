package org.mythtv.lfmobile.ui.recrules;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.annotation.SuppressLint;
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

import org.mythtv.lfmobile.MainActivity;
import org.mythtv.lfmobile.MyApplication;
import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.databinding.FragmentRulelistBinding;
import org.mythtv.lfmobile.databinding.ItemRecruleBinding;
import org.mythtv.lfmobile.ui.schedule.ScheduleViewModel;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class RuleListFragment extends Fragment implements MainActivity.MyFragment {
    private static final String TAG = "lfm";
    private static final String CLASS = "RuleListFragment";
    private MenuProvider menuProvider;
    private RuleListViewModel model;
    private FragmentRulelistBinding binding;
    private static final HashMap<String, Integer> typeNames = new HashMap<>();

    static {
        typeNames.put("Do not Record", R.string.recrule_DonotRecord);
        typeNames.put("Not Recording", R.string.recrule_NotRecording);
        typeNames.put("Override Recording", R.string.recrule_OverrideRecording);
        typeNames.put("Record All", R.string.recrule_RecordAll);
        typeNames.put("Record Daily", R.string.recrule_RecordDaily);
        typeNames.put("Record One", R.string.recrule_RecordOne);
        typeNames.put("Record Weekly", R.string.recrule_RecordWeekly);
        typeNames.put("Recording Template", R.string.recrule_RecordingTemplate);
        typeNames.put("Single Record", R.string.recrule_SingleRecord);
    }

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
            adapter.submitList(new ArrayList<>(list));
            binding.swiperefresh.setRefreshing(false);
        });
        binding.swiperefresh.setOnRefreshListener(this::refresh);
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
        int orientation = requireActivity().getResources().getConfiguration().orientation;
        int spanCount = 1;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            spanCount = 2;
        GridLayoutManager lm = (GridLayoutManager)binding.recyclerviewRulelist.getLayoutManager();
        if (lm != null)
            lm.setSpanCount(spanCount);
        menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menu.add(R.id.recrules_group, R.id.id_new_rule, 0, R.string.menu_new_recrule);
                menu.add(R.id.recrules_group, R.id.id_new_template, 0, R.string.menu_new_template);
            }
            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuProvider.super.onPrepareMenu(menu);
            }
            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                int reason = 0;
                if (id == R.id.id_new_rule)
                    reason = ScheduleViewModel.SCHED_NEWRULE;
                else if (id == R.id.id_new_template)
                    reason = ScheduleViewModel.SCHED_NEWTEMPLATE;
                if (reason != 0) {
                    try {
                        Bundle args = new Bundle();
                        args.putLong(ScheduleViewModel.REQID, System.currentTimeMillis());
                        args.putInt(ScheduleViewModel.SCHEDREASON, reason);
                        NavHostFragment navHostFragment =
                                (NavHostFragment) requireActivity().getSupportFragmentManager()
                                        .findFragmentById(R.id.nav_host_fragment_content_main);
                        if (navHostFragment != null) {
                            NavController navController = navHostFragment.getNavController();
                            navController.navigate(R.id.nav_schedule, args);
                        }
                        return true;
                    } catch (Exception e) {
                        Log.e(TAG, CLASS + " Exception setting up new rule edit.", e);
                    }
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
        ((MainActivity)requireActivity()).myFragment = this;
        if (menuProvider != null) {
            requireActivity().addMenuProvider(menuProvider,getViewLifecycleOwner());
        }
        ActionBar ab = ((AppCompatActivity)requireActivity()).getSupportActionBar();
        if (ab != null) {
            ab.setTitle(R.string.title_recrules);
            ab.setSubtitle(null);
        }
        refresh();
    }

    @Override
    public void onPause() {
        ((MainActivity)requireActivity()).myFragment = null;
        if (menuProvider != null) {
            requireActivity().removeMenuProvider(menuProvider);
            requireActivity().invalidateMenu();
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    public void startFetch() {
        if (binding != null) {
            binding.swiperefresh.setRefreshing(true);
            refresh();
        }
    }

    @SuppressLint("SimpleDateFormat")
    private static class RuleListAdapter extends ListAdapter
            <RuleListViewModel.RuleItem, RuleListFragment.RuleListViewHolder> {
        private final RuleListFragment fragment;

        static final SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
        static final SimpleDateFormat weekDay = new SimpleDateFormat("E");
        static final SimpleDateFormat timeOfDay = new SimpleDateFormat("HH:mm");
        static final DateFormat outFormat = android.text.format.DateFormat.getMediumDateFormat
                (MyApplication.getAppContext());

        protected RuleListAdapter(RuleListFragment fragment) {
            super(new DiffUtil.ItemCallback<>() {

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
                    if (date != null)
                        dateStr.append(weekDay.format(date)).append(" ")
                                .append(outFormat.format(date)).append(" ")
                                .append(timeOfDay.format(date));
                    holder.binding.itemLast.setText(dateStr);
                    holder.binding.itemLastTitle.setVisibility(View.VISIBLE);
                    holder.binding.itemLast.setVisibility(View.VISIBLE);
                } catch (ParseException e) {
                    Log.e(TAG, CLASS + " Exception ", e);
                }
            }

            holder.binding.itemNextTitle.setVisibility(View.GONE);
            holder.binding.itemNext.setVisibility(View.GONE);
            holder.binding.itemNext.setText(null);
            if (holder.item.nextRecording != null) {
                try {
                    StringBuilder dateStr = new StringBuilder();
                    Date date = dbFormat.parse(holder.item.nextRecording + "+0000");
                    if (date != null)
                        dateStr.append(weekDay.format(date)).append(" ")
                                .append(outFormat.format(date)).append(" ")
                                .append(timeOfDay.format(date));
                    holder.binding.itemNext.setText(dateStr);
                    holder.binding.itemNextTitle.setVisibility(View.VISIBLE);
                    holder.binding.itemNext.setVisibility(View.VISIBLE);
                } catch (ParseException e) {
                    Log.e(TAG, CLASS + " Exception ", e);
                }
            }
            Integer tValue = typeNames.get(holder.item.type);
            if (tValue == null)
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
            View.OnClickListener listener  = v -> actionRequest(fragment, v);
            binding.getRoot().setOnClickListener(listener);
        }

        private void actionRequest(RuleListFragment fragment, View ignoredV) {
            try {
                Bundle args = new Bundle();
                args.putLong(ScheduleViewModel.REQID, System.currentTimeMillis());
                args.putInt(ScheduleViewModel.RECORDID, item.id);
                args.putInt(ScheduleViewModel.SCHEDREASON, ScheduleViewModel.SCHED_RULELIST);
                NavHostFragment navHostFragment =
                        (NavHostFragment) fragment.requireActivity().getSupportFragmentManager()
                                .findFragmentById(R.id.nav_host_fragment_content_main);
                if (navHostFragment != null) {
                    NavController navController = navHostFragment.getNavController();
                    navController.navigate(R.id.nav_schedule, args);
                }
            } catch (Exception e) {
                Log.e(TAG, CLASS + " Exception setting up schedule edit.", e);
            }
        }
    }
}