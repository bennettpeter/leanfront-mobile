package org.mythtv.lfmobile.ui.guide;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;

import org.mythtv.lfmobile.MainActivity;
import org.mythtv.lfmobile.MyApplication;
import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.data.BackendCache;
import org.mythtv.lfmobile.data.Settings;
import org.mythtv.lfmobile.data.XmlNode;
import org.mythtv.lfmobile.databinding.FragmentGuideBinding;
import org.mythtv.lfmobile.databinding.ItemChannelBinding;
import org.mythtv.lfmobile.databinding.ItemGuideBinding;
import org.mythtv.lfmobile.databinding.ItemTimeslotBinding;
import org.mythtv.lfmobile.ui.proglist.ProgramListModel;
import org.mythtv.lfmobile.ui.schedule.ScheduleViewModel;

import java.util.Calendar;
import java.util.GregorianCalendar;

@SuppressWarnings("SpellCheckingInspection")
public class GuideFragment extends Fragment implements MainActivity.MyFragment {
    private static final String TAG = "lfm";
    private static final String CLASS = "GuideFragment";
    private FragmentGuideBinding binding;
    private GuideViewModel model;
    private boolean internalScroll;
    private MenuProvider menuProvider;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(this).get(GuideViewModel.class);
        binding = FragmentGuideBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        Context context = binding.datelist.getContext();
        DividerItemDecoration dec1 = new DividerItemDecoration(binding.datelist.getContext(), DividerItemDecoration.HORIZONTAL);
        Drawable vertDivider = AppCompatResources.getDrawable(context,R.drawable.vert_divider);
        if (vertDivider != null)
            dec1.setDrawable(vertDivider);
        DividerItemDecoration dec2 = new DividerItemDecoration(binding.datelist.getContext(), DividerItemDecoration.VERTICAL);
        Drawable horzDivider = AppCompatResources.getDrawable(context,R.drawable.horz_divider);
        if (horzDivider != null)
            dec2.setDrawable(horzDivider);

        // Setup Date / Time list along the top
        final RecyclerView.Adapter<TimeslotViewHolder> dateAdapter = new TimeslotListAdapter(this);
        binding.datelist.setAdapter(dateAdapter);
        model.dateLiveData.observe(getViewLifecycleOwner(), (list) -> dateAdapter.notifyDataSetChanged());
        binding.datelist.addItemDecoration(dec1);
        binding.datelist.addItemDecoration(dec2);
        GridLayoutManager mgr1 = new GridLayoutManager(getContext(), GuideViewModel.TIMESLOTS);
        binding.datelist.setLayoutManager(mgr1);

        binding.dateScrollView.setOnScrollChangeListener((View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) -> {
            {
                if (!internalScroll) {
                    internalScroll = true;
                    binding.scrollView.smoothScrollTo(scrollX, 0);
                    internalScroll = false;
                }
            }
        });


        // Set up Channel list  along the left
        final RecyclerView.Adapter<ChannelViewHolder> chanAdapter = new ChannelListAdapter(this);
        binding.chanlist.setAdapter(chanAdapter);
        model.chanLiveData.observe(getViewLifecycleOwner(), (list) -> {
            chanAdapter.notifyDataSetChanged();
            if (binding.proglist.getAdapter() != null)
                binding.proglist.getAdapter().notifyDataSetChanged();
        });
        binding.chanlist.addItemDecoration(dec1);
        binding.chanlist.addItemDecoration(dec2);

        binding.chanlist.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!internalScroll) {
                    internalScroll = true;
                    binding.proglist.scrollBy(dx, dy);
                    internalScroll = false;
                }
            }
        });

        // Set up Program list grid at center
        final RecyclerView.Adapter<ProgViewHolder> progAdapter = new ProgListAdapter(this);
        binding.proglist.setAdapter(progAdapter);
        model.progLiveData.observe(getViewLifecycleOwner(), (list) -> {
            progAdapter.notifyDataSetChanged();
            binding.progressBar.setVisibility(View.GONE);
        });
        binding.proglist.addItemDecoration(dec1);
        binding.proglist.addItemDecoration(dec2);
        GridLayoutManager mgr = new GridLayoutManager(getContext(), GuideViewModel.TIMESLOTS);
        binding.proglist.setLayoutManager(mgr);

        binding.proglist.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!internalScroll) {
                    internalScroll = true;
                    binding.chanlist.scrollBy(dx, dy);
                    internalScroll = false;
                }
            }
        });

        binding.scrollView.setOnScrollChangeListener((View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) -> {
            {
                if (!internalScroll) {
                    internalScroll = true;
                    binding.dateScrollView.smoothScrollTo(scrollX, 0);
                    internalScroll = false;
                }
            }
        });

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                MenuItem searchItem = menu.findItem(R.id.search);
                if (searchItem != null)
                    searchItem.setVisible(true);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                menu.removeGroup(R.id.changroup_group);
                if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED && model.chanGroupNames != null) {
                    for (int ix = 0; ix < model.chanGroupNames.size(); ix++) {
                        MenuItem item = menu.add(R.id.changroup_group, ix, ix, model.chanGroupNames.get(ix));
                        item.setCheckable(true);
                        if (ix == model.chanGroupIx)
                            item.setChecked(true);
                    }
                }
                MenuProvider.super.onPrepareMenu(menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getGroupId() == R.id.changroup_group) {
                    model.chanGroupIx = menuItem.getItemId();
                    model.chanGroupId = model.chanGroupIDs.get(model.chanGroupIx);
                    SharedPreferences.Editor editor = Settings.getEditor();
                    Settings.putString(editor, "chan_group", model.chanGroupNames.get(model.chanGroupIx));
                    editor.commit();
                    refresh(true, true, 'L');
                    return true;
                }
                if (menuItem.getItemId() == R.id.search) {
                    NavHostFragment navHostFragment =
                        (NavHostFragment) requireActivity().getSupportFragmentManager()
                                .findFragmentById(R.id.nav_host_fragment_content_main);
                    NavController navController = null;
                    if (navHostFragment != null)
                        navController = navHostFragment.getNavController();
                    Bundle args = new Bundle();
                    args.putInt("type", ProgramListModel.TYPE_GUIDE_SEARCH);
                    if (navController != null)
                        navController.navigate(R.id.nav_search, args);
                }
                return false;
            }
        };
        binding.dateSelect.setOnClickListener((v) -> {
            GregorianCalendar cal = new GregorianCalendar();
            // This would set calendar to the date previously selected.
            // We prefer to use the current date every time, to make it
            // easier to get back to the default view.
//            cal.setTime(model.guideStartTime);
            DatePickerDialog dlgDate = new DatePickerDialog(requireContext(),
                    (dpView, yy, mm, dd) -> {
                cal.set(Calendar.YEAR, yy);
                cal.set(Calendar.MONTH, mm);
                cal.set(Calendar.DAY_OF_MONTH, dd);
                TimePickerDialog dlgTime = new TimePickerDialog(getContext(), (tpView, hh, min) -> {
                    cal.set(Calendar.HOUR_OF_DAY, hh);
                    cal.set(Calendar.MINUTE, min);
                    model.guideStartTime.setTime(cal.getTimeInMillis());
                    refresh(false, true, 'L');
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false);
                dlgTime.show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            // This would add a reset button to select current date. However, it displays
            // funny on some phones
//            dlgDate.setButton(DialogInterface.BUTTON_NEUTRAL,getString(R.string.button_reset) ,
//                    (dialog, which) ->
//                {
//                    if (which == DialogInterface.BUTTON_NEUTRAL) {
//                        model.guideStartTime.setTime(System.currentTimeMillis());
//                        refresh(false, true, 'L');
//                    }
//                });
            DatePicker picker = dlgDate.getDatePicker();
            picker.setMinDate(System.currentTimeMillis() - 28L * 24 * 60 * 60000);
            picker.setMaxDate(System.currentTimeMillis() + 28L * 24 * 60 * 60000);
            dlgDate.show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        ((MainActivity) requireActivity()).myFragment = this;
        if (menuProvider != null) {
            requireActivity().addMenuProvider(menuProvider, getViewLifecycleOwner());
        }
        ActionBar bar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        String group = Settings.getString("chan_group");
        if (group.isEmpty())
            group = MyApplication.getAppContext().getString(R.string.all_title) + "\t";
        if (bar != null)
            bar.setSubtitle(group);
        // We will refresh everything here
        if (model.timeslotList.isEmpty()) {
            refresh(true, true, 'L');
        }
        else
            refresh(false, false, ' ');

    }

    @Override
    public void onPause() {
        ((MainActivity) requireActivity()).myFragment = null;
        if (menuProvider != null) {
            requireActivity().removeMenuProvider(menuProvider);
            requireActivity().invalidateMenu();
        }
        super.onPause();
    }

    // scrollDir: R, L, or blank
    void refresh(boolean resetTimeslots, boolean progressbar, char scrollDir) {
        if (progressbar)
            binding.progressBar.setVisibility(View.VISIBLE);
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        ActionBar bar = activity.getSupportActionBar();
        String group = Settings.getString("chan_group");
        if (group.isEmpty())
            group = MyApplication.getAppContext().getString(R.string.all_title) + "\t";
        if (bar != null)
            bar.setSubtitle(group);
        switch (scrollDir) {
            case 'L':
                binding.dateScrollView.fullScroll(View.FOCUS_LEFT);
                break;
            case 'R':
                binding.dateScrollView.fullScroll(View.FOCUS_RIGHT);
                break;
        }
        model.refresh(resetTimeslots);
    }

    @Override
    public void startFetch() {
        refresh(false, true, ' ');
    }

    private static class TimeslotListAdapter extends RecyclerView.Adapter<TimeslotViewHolder> {
        private final GuideFragment fragment;

        protected TimeslotListAdapter(GuideFragment fragment) {
            this.fragment = fragment;
        }


        @NonNull
        @Override
        public TimeslotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemTimeslotBinding binding = ItemTimeslotBinding.inflate(LayoutInflater.from(parent.getContext()));
            return new TimeslotViewHolder(binding, fragment);
        }

        @Override
        public void onBindViewHolder(@NonNull TimeslotViewHolder holder, int position) {
            if (position < fragment.model.timeslotList.size()) {
                String item = fragment.model.timeslotList.get(position);
                holder.timeText.setText(item);
                if (position == 0) {
                    holder.leftText.setText("<<");
                    holder.leftText.setOnClickListener((v) -> {
                        fragment.handler.postDelayed(() -> {
                            fragment.model.guideStartTime.setTime(fragment.model.guideStartTime.getTime() - GuideViewModel.TIMESLOTS * GuideViewModel.TIMESLOT_SIZE * 60000);
                            fragment.refresh(false, true, 'R');
                        }, 100);
                    });
                } else holder.leftText.setText(null);
                if (position == fragment.model.timeslotList.size() - 1) {
                    holder.rightText.setText(">>");
                    holder.rightText.setOnClickListener((v) -> {
                        fragment.handler.postDelayed(() -> {
                            fragment.model.guideStartTime.setTime(fragment.model.guideStartTime.getTime() + GuideViewModel.TIMESLOTS * GuideViewModel.TIMESLOT_SIZE * 60000);
                            fragment.refresh(false, true, 'L');
                        }, 100);
                    });
                } else holder.rightText.setText(null);
            } else holder.timeText.setText("");
        }

        @Override
        public int getItemCount() {
            return GuideViewModel.TIMESLOTS;
        }
    }

    private static class TimeslotViewHolder extends RecyclerView.ViewHolder {
        private final TextView timeText;
        private final TextView leftText;
        private final TextView rightText;

        public TimeslotViewHolder(ItemTimeslotBinding binding, GuideFragment ignoredFragment) {
            super(binding.getRoot());
            timeText = binding.timeText;
            leftText = binding.leftText;
            rightText = binding.rightText;
        }
    }

    private static class ChannelListAdapter extends RecyclerView.Adapter<ChannelViewHolder> {
        private final GuideFragment fragment;

        protected ChannelListAdapter(GuideFragment fragment) {
            this.fragment = fragment;
        }


        @NonNull
        @Override
        public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemChannelBinding binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.getContext()));
            return new ChannelViewHolder(binding, fragment);
        }

        @SuppressLint({"CheckResult", "SetTextI18n"})
        @Override
        public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
            ChannelSlot slot = fragment.model.chanList.get(position);
            if (slot.iconURL == null) {
                holder.channelImage.setImageDrawable(null);
            } else {
                try {
                    String imageUrl = XmlNode.mythApiUrl(null, slot.iconURL);
                    RequestOptions options = new RequestOptions();
                    options.timeout(5000);
                    String auth = BackendCache.getInstance().authorization;
                    LazyHeaders.Builder lzhb = new LazyHeaders.Builder();
                    if (auth != null && !auth.isEmpty()) lzhb.addHeader("Authorization", auth);
                    GlideUrl url = new GlideUrl(imageUrl, lzhb.build());

                    Glide.with(fragment.requireContext()).load(url).apply(options).into(holder.channelImage);
                } catch (Exception e) {
                    Log.e(TAG, CLASS + " Exception ", e);
                    holder.channelImage.setImageDrawable(null);
                }
            }
            holder.channelText.setText(slot.chanNum + " " + slot.callSign);
        }

        @Override
        public int getItemCount() {
            return fragment.model.chanList.size();
        }
    }

    private static class ChannelViewHolder extends RecyclerView.ViewHolder {
        private final ImageView channelImage;
        private final TextView channelText;

        public ChannelViewHolder(ItemChannelBinding binding, GuideFragment ignoredFragment) {
            super(binding.getRoot());
            channelImage = binding.channelImage;
            channelText = binding.channelText;
        }
    }

    private static class ProgListAdapter extends RecyclerView.Adapter<ProgViewHolder> {
        private final GuideFragment fragment;

        protected ProgListAdapter(GuideFragment fragment) {
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public ProgViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemGuideBinding binding = ItemGuideBinding.inflate(LayoutInflater.from(parent.getContext()));
            return new ProgViewHolder(binding, fragment);
        }

        @Override
        public void onBindViewHolder(@NonNull ProgViewHolder holder, int position) {
            if (fragment.model.progList != null && fragment.model.progList.size() > position) {
                holder.item = fragment.model.progList.get(position);
                holder.binding.progText.setText(holder.item.getGuideText(fragment.getContext()));
                String status = null;
                if (holder.item.program != null && holder.item.program.recordingStatus != null)
                    status = holder.item.program.recordingStatus;
                if (holder.item.program2 != null && holder.item.program2.recordingStatus != null)
                    status = (status == null ? "(2)" : status + '/') + holder.item.program2.recordingStatus;
                holder.binding.progStatus.setText(status);
            }

            if (holder.item.program == null || holder.item.program.recordingStatus == null) {
                holder.binding.itemPaperclip.setVisibility(View.GONE);
            } else {
                holder.binding.itemPaperclip.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return fragment.model.chanList.size() * GuideViewModel.TIMESLOTS;
        }
    }

    private static class ProgViewHolder extends RecyclerView.ViewHolder {
        private final ItemGuideBinding binding;
        private ProgSlot item;

        public ProgViewHolder(ItemGuideBinding binding, GuideFragment fragment) {
            super(binding.getRoot());
            this.binding = binding;
            View.OnClickListener listener  =  v ->{
                if (item.program == null) {
                    Toast.makeText(fragment.getContext(),
                            R.string.guide_noprog, Toast.LENGTH_LONG).show();
                    return;
                }
                String[] prompts = new String[2];
                int counter = 0;
                prompts[counter] = fragment.requireContext().getString(R.string.msg_edit_schedule, item.program.title);
                ++counter;
                if (item.program2 != null) {
                    prompts[counter] = fragment.requireContext().getString(R.string.msg_edit_schedule2, item.program2.title);
                    ++counter;
                }
                if (counter == 1) {
                    actionRequest(fragment,1, v);
                } else {
                    final String[] finalPrompts = new String[counter];
                    System.arraycopy(prompts, 0, finalPrompts, 0, counter);
                    String alertTitle = fragment.requireContext().getString(R.string.title_program_guide);
                    // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
                    AlertDialog.Builder builder = new AlertDialog.Builder(fragment.requireActivity());
                    builder .setTitle(alertTitle)
                            .setItems(finalPrompts,
                                    (dialog, which) -> {
                                        // The 'which' argument contains the index position
                                        // of the selected item
                                        if (which < finalPrompts.length) {
                                            actionRequest(fragment,which+1, v);
                                        }
                                    });
                    builder.show();
                }
            };
            binding.getRoot().setOnClickListener(listener);
            binding.itemPaperclip.setOnClickListener(listener);
        }
        private void actionRequest(GuideFragment fragment, int action, View v) {
            Bundle args = new Bundle();
            args.putLong(ScheduleViewModel.REQID, System.currentTimeMillis());
            switch (action) {
                case 1:
                    args.putInt(ScheduleViewModel.CHANID, item.program.chanId);
                    args.putSerializable(ScheduleViewModel.STARTTIME, item.program.startTime);
                    break;
                case 2:
                    args.putInt(ScheduleViewModel.CHANID, item.program2.chanId);
                    args.putSerializable(ScheduleViewModel.STARTTIME, item.program2.startTime);
                    break;
                default:
                    return;
            }
            if (v == binding.itemPaperclip)
                args.putBoolean(ScheduleViewModel.ISOVERRIDE, true);
            args.putInt(ScheduleViewModel.SCHEDREASON, ScheduleViewModel.SCHED_GUIDE);
            NavHostFragment navHostFragment =
                    (NavHostFragment) fragment.requireActivity().getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
            NavController navController = null;
            if (navHostFragment != null)
                navController = navHostFragment.getNavController();
            if (navController != null)
                navController.navigate(R.id.nav_schedule, args);
        }

    }

}