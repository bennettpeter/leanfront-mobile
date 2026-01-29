package org.mythtv.lfmobile.ui.schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;

import org.mythtv.lfmobile.MainActivity;
import org.mythtv.lfmobile.MyApplication;
import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.data.AsyncBackendCall;
import org.mythtv.lfmobile.data.AsyncRemoteCall;
import org.mythtv.lfmobile.data.BackendCache;
import org.mythtv.lfmobile.databinding.FragmentScheduleBinding;
import org.mythtv.lfmobile.ui.MultiSpinner;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class ScheduleFragment extends MainActivity.MyFragment {

    static final int[] searchPrompts = {
            R.string.sched_srch_None,
            R.string.sched_srch_PowerSearch,
            R.string.sched_srch_TitleSearch,
            R.string.sched_srch_KeywordSearch,
            R.string.sched_srch_PeopleSearch,
            R.string.sched_srch_Manual_Search
    };
    static final String[] searchValues = {
            "None",
            "Power Search",
            "Title Search",
            "Keyword Search",
            "People Search",
            "Manual Search"
    };
    // These must match the vaalues above
    static final int SRCH_NONE = 0;
    static final int SRCH_POWER = 1;
    static final int SRCH_TITLE = 2;
    static final int SRCH_KWORD = 3;
    static final int SRCH_PEOPLE = 4;
    static final int SRCH_MANUAL = 5;
    static final int[] sDupMethodPrompts = {
            R.string.sched_dup_none, R.string.sched_dup_s_and_d,
            R.string.sched_dup_s_then_d, R.string.sched_dup_s,
            R.string.sched_dup_d};
    static final String[] sDupMethodValues = {
            "None", "Subtitle and Description",
            "Subtitle then Description", "Subtitle",
            "Description"};
    static final int[] sDupScopePrompts = {
            R.string.sched_dup_both, R.string.sched_dup_curr,
            R.string.sched_dup_prev};
    static final String[] sDupScopeValues = {
            "All Recordings", "Current Recordings",
            "Previous Recordings"};
    static final int[] sExtendPrompts = {
            R.string.sched_extend_none, R.string.sched_extend_espn, R.string.sched_extend_mlb};
    static final String[] sExtendValues = {
            "None", "ESPN", "MLB"};
    static final int[] sRecProfilePrompts = {
            R.string.sched_recprof_default, R.string.sched_recprof_livetv,
            R.string.sched_recprof_highq, R.string.sched_recprof_lowq};
    static final String[] sRecProfileValues = {
            "Default", "Live TV",
            "High Quality", "Low Quality"};
    static final int[] sPostProcPrompts = {
            R.string.sched_pp_commflag, R.string.sched_pp_metadata,
            R.string.sched_pp_transcode,
            R.string.sched_pp_job1, R.string.sched_pp_job2,
            R.string.sched_pp_job3, R.string.sched_pp_job4};
    static final NumericFocus numericFocus = new NumericFocus();
    private static DateFormat mTimeFormatter;
    private static DateFormat mDateFormatter;
    private static DateFormat mDayFormatter;
    ArrayList<Integer> typePrompts = new ArrayList<>();
    ArrayList<String> typeOptions = new ArrayList<>();
    ArrayList<String> inputPrompts = new ArrayList<>();
    ArrayList<Integer> inputValues = new ArrayList<>();
    private ScheduleViewModel model;
    private FragmentScheduleBinding binding;
    private String templateName = "";
    private boolean hideNav = false;
    private MenuProvider menuProvider;

    private static int validateNumber(EditText view, Object action, int min, int max, int defValue) {
        String s;
        int i;
        boolean fix = false;
        s = action.toString();
        if (s.isEmpty() || "-".equals(s))
            return 0;
        try {
            i = Integer.parseInt(s);
        } catch (Exception e) {
            i = defValue;
            fix = true;
        }
        if (i < min) {
            i = min;
            fix = true;
        } else if (i > max) {
            i = max;
            fix = true;
        }
        if (fix) {
            String newVal;
            if (i == 0)
                newVal = "";
            else
                newVal = String.valueOf(i);
            view.setText(newVal);
            view.setSelection(newVal.length());
        }
        return i;
    }

    private static int parseInt(String str) {
        int ret = 0;
        if (!str.isEmpty() && !"-".equals(str)) {
            try {
                ret = Integer.parseInt(str);
            } catch (Exception ex) {
                ret = 0;
            }
        }
        return ret;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                if (canClose()) {
                    setEnabled(false);
                    getActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        getActivity().getOnBackPressedDispatcher().addCallback(this, callback);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void startFetch() {

    }

    public boolean navigateUp() {
        requireActivity().getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(this).get(ScheduleViewModel.class);
        model.toast.observe(getViewLifecycleOwner(), (Integer msg) -> {
            if (msg == 0)
                return;
            Toast.makeText(getContext(),
                            msg, Toast.LENGTH_LONG)
                    .show();
            model.toast.setValue(0);
        });
        model.alert.observe(getViewLifecycleOwner(), (String msg) -> {
            if (msg == null)
                return;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.sched_failed).setMessage(msg).show();
            model.alert.setValue(null);
        });
        model.initDoneLiveData.observe(getViewLifecycleOwner(), (done) -> {
            switch (done) {
                case ScheduleViewModel.INIT_READY:
                    ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
                    if (model.isOverride)
                        bar.setTitle(R.string.menu_override);
                    else if ("Recording Template".equals(model.recordRule.type))
                        bar.setTitle(R.string.recrule_RecordingTemplate);
                    else
                        bar.setTitle(R.string.menu_schedule);
                    bar.setSubtitle(null);
                    setupViews();
                    break;
                case ScheduleViewModel.INIT_SAVED:
                    enableSave();
                    break;
                case ScheduleViewModel.INIT_CLOSE:
                    close();
                    break;
            }
        });
        Bundle args = getArguments();
        model.init(args);
        binding = FragmentScheduleBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                MenuItem refreshItem = menu.findItem(R.id.menu_refresh);
                if (refreshItem != null)
                    refreshItem.setVisible(false);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                return false;
            }
        };
        binding.dttime.setOnClickListener((v) -> {
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(model.manualStartTime);
            DatePickerDialog dlgDate = new DatePickerDialog(getContext(), (dpView, yy, mm, dd) -> {
                cal.set(Calendar.YEAR, yy);
                cal.set(Calendar.MONTH, mm);
                cal.set(Calendar.DAY_OF_MONTH, dd);
                TimePickerDialog dlgTime = new TimePickerDialog(getContext(), (tpView, hh, min) -> {
                    cal.set(Calendar.HOUR_OF_DAY, hh);
                    cal.set(Calendar.MINUTE, min);
                    model.manualStartTime.setTime(cal.getTimeInMillis());
                    initText(binding.dttime, model.manualStartTime, null);
                    updateHeading();
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false);
                dlgTime.show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            DatePicker picker = dlgDate.getDatePicker();
            long minDate = System.currentTimeMillis();
            if (model.manualStartTime.getTime() < minDate)
                minDate = model.manualStartTime.getTime();
            picker.setMinDate(minDate);
//            picker.setMaxDate(System.currentTimeMillis() + 56L * 24 * 60 * 60000);
            dlgDate.show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        ((MainActivity) getActivity()).myFragment = this;
        if (menuProvider != null)
            getActivity().addMenuProvider(menuProvider, getViewLifecycleOwner());
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

    @Override
    public void onPause() {
        ((MainActivity) getActivity()).myFragment = null;
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
    public void onStop() {
        updateFromView();
        super.onStop();
    }

    private void setupViews() {
        // Call Sign
        initText(binding.callSign, model.recordRule.station);
        // Start Time & End Time
        initText(binding.dateTime, model.recordRule.startTime, model.recordRule.endTime);
        // Template
        initSpinner(binding.template, null,
                model.templateNames.toArray(new String[]{}), templateName);
        binding.template.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Apply template to record rule
                // note 0 is a blank entry
                if (position > 0) {
                    RecordRule template = model.templateList.get(position - 1);
                    String newTamplateName = model.templateNames.get(position);
                    if (!newTamplateName.equals(templateName)) {
                        model.mergeTemplate(template);
                        templateName = newTamplateName;
                        setupViews();
                    }
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        // Search Type (spinner)
        initSpinner(binding.searchType, searchPrompts, searchValues, model.recordRule.searchType);
        binding.searchType.setEnabled(
                model.schedReason == ScheduleViewModel.SCHED_NEWRULE
                && model.recordRule.recordId == 0);
        binding.searchType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String oldType = model.recordRule.searchType;
                model.recordRule.searchType = searchValues[position];
                // Schedule Type changes based on search type
                setupScheduleType();
                dynamicSetups(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                onItemSelected(parent, null, 0, 0);
            }
        });
        // Title
        initText(binding.title, model.recordRule.title);
        binding.title.setOnFocusChangeListener( (v, hasFocus) -> {
            if (!hasFocus)
                fixTitle();
        });
        binding.title.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {  }
            @Override
            public void afterTextChanged(Editable s) {
                enableSave();
            }
        });
        // Subtitle
        initText(binding.subtitle, model.recordRule.subtitle);
        // Season & Episode
        if (model.recordRule.episode > 0)
            initText(binding.seasonEpisode,
                    "S" + model.recordRule.season + " E" + model.recordRule.episode);
        else
            binding.seasonEpisode.setVisibility(View.GONE);
        // Description
        initText(binding.description, model.recordRule.description);
        binding.description.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {  }
            @Override
            public void afterTextChanged(Editable s) {
                fixTitle();
            }
        });
        // Channel
        int pos = model.chanids.indexOf(model.recordRule.chanId);
        String chansel = null;
        if (pos > -1)
            chansel = model.names.get(pos);
        initSpinner(binding.channel, null, model.names.toArray(new String[]{}), chansel);
        binding.channel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateHeading();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Start Date and Time
        model.manualStartTime = model.recordRule.startTime;
        if (model.manualStartTime == null)
            model.manualStartTime = new Date();
        initText(binding.dttime, model.manualStartTime, null);

        // Duration
        if (model.recordRule.startTime != null
                && model.recordRule.endTime != null)
            initText(binding.duration,
                    (int) ((model.recordRule.endTime.getTime()
                            - model.recordRule.startTime.getTime()) / 60000L));
        else
            initText(binding.duration, 5);
        binding.duration.setOnFocusChangeListener(numericFocus);
        binding.duration.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                validateNumber(binding.duration, s, 1, 1440, 5);
                updateHeading();
            }
        });

        // Schedule Type (spinner)
        setupScheduleType();
        binding.scheduleType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                model.recordRule.type = typeOptions.get(position);
                // save becomes disabled in some cases
                enableSave();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                onItemSelected(parent, null, 0, 0);
            }
        });
        // Recording Group
        initSpinner(binding.recordingGroup, null,
                model.mRecGroupList.toArray(new String[]{}), model.recordRule.recGroup);
        // Active
        binding.active.setChecked(!model.recordRule.inactive);
        // Playback Group
        initSpinner(binding.playbackGroup, null,
                model.mPlayGroupList.toArray(new String[]{}), model.recordRule.playGroup);
        // Start Offset
        initText(binding.startOffset, model.recordRule.startOffset);
        binding.startOffset.setOnFocusChangeListener(numericFocus);
        binding.startOffset.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {  }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {   }
            @Override
            public void afterTextChanged(Editable s) {
                validateNumber(binding.startOffset, s, -480, 480, 0);
            }
        });
        // End Offset
        initText(binding.endOffset, model.recordRule.endOffset);
        binding.endOffset.setOnFocusChangeListener(numericFocus);
        binding.endOffset.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {  }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {  }
            @Override
            public void afterTextChanged(Editable s) {
                validateNumber(binding.endOffset, s, -480, 480, 0);
            }
        });
        // New Episodes Only
        binding.newEpisOnly.setChecked(model.recordRule.newEpisOnly);
        // Priority
        initText(binding.recPriority, model.recordRule.recPriority);
        binding.recPriority.setOnFocusChangeListener(numericFocus);
        binding.recPriority.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {  }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {  }
            @Override
            public void afterTextChanged(Editable s) {
                validateNumber(binding.recPriority, s, -100, 100, 0);
            }
        });

        // Preferred Input
        inputPrompts.clear();
        inputValues.clear();
        for (int ix = 0; ix < model.mInputList.size(); ix++) {
            String value = model.mInputList.valueAt(ix);
            if (value != null) {
                inputPrompts.add(value);
                inputValues.add(model.mInputList.keyAt(ix));
            }
        }
        initSpinner(binding.preferredInput, null,
                inputPrompts.toArray(new String[]{}), model.mInputList.get(model.recordRule.preferredInput));
        // Dup Method
        initSpinner(binding.dupMethod, sDupMethodPrompts,
                sDupMethodValues, model.recordRule.dupMethod);
        // Dup Scope
        initSpinner(binding.dupIn, sDupScopePrompts,
                sDupScopeValues, model.recordRule.dupIn);
        // Auto Extend
        initSpinner(binding.autoExtend, sExtendPrompts,
                sExtendValues, model.recordRule.autoExtend);
        // Filters
        initMultiSpinner(binding.selectedFilters,
                model.mRecRuleFilterList.toArray(new String[]{}), model.recordRule.filter);
        // Recording Profile
        initSpinner(binding.recProfile, sRecProfilePrompts,
                sRecProfileValues, model.recordRule.recProfile);
        // Storage Group
        initSpinner(binding.storageGroup, null,
                model.mRecStorageGroupList.toArray(new String[]{}), model.recordRule.storageGroup);
        // Max Episodes
        initText(binding.maxEpisodes, model.recordRule.maxEpisodes);
        binding.maxEpisodes.setOnFocusChangeListener(numericFocus);
        binding.maxEpisodes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {  }
            @Override
            public void afterTextChanged(Editable s) {
                validateNumber(binding.maxEpisodes, s, 0, 100, 0);
            }
        });
        // Deletion on max (max newest)
        binding.maxNewest.setChecked(model.recordRule.maxNewest);
        // Auto Expire
        binding.autoExpire.setChecked(model.recordRule.autoExpire);
        // post Processing
        int ppVal = 0;
        ppVal |= model.recordRule.autoCommflag ? 1 << 0 : 0;
        ppVal |= model.recordRule.autoMetaLookup ? 1 << 1 : 0;
        ppVal |= model.recordRule.autoTranscode ? 1 << 2 : 0;
        ppVal |= model.recordRule.autoUserJob1 ? 1 << 3 : 0;
        ppVal |= model.recordRule.autoUserJob2 ? 1 << 4 : 0;
        ppVal |= model.recordRule.autoUserJob3 ? 1 << 5 : 0;
        ppVal |= model.recordRule.autoUserJob4 ? 1 << 6 : 0;
        initMultiSpinner(binding.postProc,
                makeStrings(sPostProcPrompts).toArray(new String[]{}), ppVal);
        // Metadata Lookup Id (Inetref)
        initText(binding.inetref, model.recordRule.inetref);
        // Metadata search phrase
        if ("None".equals(model.recordRule.searchType))
            initText(binding.searchPhrase, model.recordRule.title);
        // Metadata search buttons
        View.OnClickListener mdSrchListener = (v) -> {
            int task;
            if (v == binding.searchTvmazeBn)
                task = AsyncRemoteCall.ACTION_LOOKUP_TVMAZE;
            else if (v == binding.searchTvdbBn)
                task = AsyncRemoteCall.ACTION_LOOKUP_TVDB;
            else if (v == binding.searchTmdbTvBn)
                task = AsyncRemoteCall.ACTION_LOOKUP_TV;
            else if (v == binding.searchTmdbMvBn)
                task = AsyncRemoteCall.ACTION_LOOKUP_MOVIE;
            else
                return;
            AsyncRemoteCall call = new AsyncRemoteCall(getActivity(), taskRunner -> {
                selectMetaResult(taskRunner);
            });
            call.stringParameter = binding.searchPhrase.getText().toString();
            call.execute(task);
        };
        binding.searchTvdbBn.setOnClickListener(mdSrchListener);
        binding.searchTvmazeBn.setOnClickListener(mdSrchListener);
        binding.searchTmdbTvBn.setOnClickListener(mdSrchListener);
        binding.searchTmdbMvBn.setOnClickListener(mdSrchListener);
        // Save Button
        binding.saveButton.setOnClickListener((v -> {
            save(false);
        }));
        // Close Button
        binding.closeButton.setOnClickListener((v -> {
            close();
        }));
    }

    void selectMetaResult(AsyncRemoteCall taskRunner) {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());
        final AsyncRemoteCall.Parser parser;
        int task = taskRunner.tasks[0];
        switch (task) {
            case AsyncRemoteCall.ACTION_LOOKUP_TVMAZE:
            case AsyncRemoteCall.ACTION_LOOKUP_TV:
            case AsyncRemoteCall.ACTION_LOOKUP_TVDB:
            case AsyncRemoteCall.ACTION_LOOKUP_MOVIE:
                parser = taskRunner.results.get(0);
                break;
            default:
                return;
        }
        ArrayList<CharSequence> prompts = new ArrayList<>();
        for (AsyncRemoteCall.TvEntry entry : parser.entries) {
            if (entry.name == null || entry.id == 0)
                break; // should not happen
            StringBuilder stringBuilder = new StringBuilder(entry.name);
            boolean paren=false;
            if (entry.firstAirDate != null
                    && entry.firstAirDate.length() >= 4) {
                paren = true;
                stringBuilder
                        .append(" [")
                        .append(entry.firstAirDate.substring(0, 4));
            }
            if (entry.type != null) {
                if (!paren) {
                    stringBuilder.append(" [");
                    paren = true;
                }
                else
                    stringBuilder.append(" ");
                stringBuilder.append(entry.type);
            }
            if (paren)
                stringBuilder.append("]");
            if (entry.overview != null && entry.overview.length() > 0) {
                String desc = entry.overview.trim();
                if (desc.length() > 300)
                    desc = desc.substring(0,300) + " ...";
                stringBuilder.append(" :\n");
                stringBuilder.append("-----\n");
                stringBuilder.append(desc);
            }
            stringBuilder.append("\n====");
//            stringBuilder.append('\n');
            prompts.add(stringBuilder.toString());
        }
        if (prompts.size() > 0)
            alertBuilder.setTitle(R.string.sched_metadata_select_prompt);
        else
            alertBuilder.setTitle(R.string.sched_metadata_select_none);
        alertBuilder
                .setItems(prompts.toArray(new CharSequence[0]),
                        (dialog, which) -> {
                            // The 'which' argument contains the index position
                            // of the selected item
                            if (which < parser.entries.size()) {
                                StringBuilder inetRef = new StringBuilder();
                                switch(taskRunner.tasks[0]) {
                                    case AsyncRemoteCall.ACTION_LOOKUP_TVMAZE:
                                        inetRef.append("tvmaze.py_");
                                        break;
                                    case AsyncRemoteCall.ACTION_LOOKUP_TV:
                                        inetRef.append("tmdb3tv.py_");
                                        break;
                                    case AsyncRemoteCall.ACTION_LOOKUP_MOVIE:
                                        inetRef.append("tmdb3.py_");
                                        break;
                                    case AsyncRemoteCall.ACTION_LOOKUP_TVDB:
                                        inetRef.append("ttvdb4.py_");
                                        break;
                                }
                                inetRef.append(parser.entries.get(which).id);
                                binding.inetref.setText(inetRef.toString());
                            }
                        });
        alertBuilder.show();
    }

    private void updateHeading() {
        int srchPos = binding.searchType.getSelectedItemPosition();
        if (srchPos == SRCH_MANUAL) {
            // Channel
            int chanPos = binding.channel.getSelectedItemPosition();
            model.recordRule.chanId = model.chanids.get(chanPos);
            model.recordRule.station = model.callSigns.get(chanPos);
            // Start Time
            model.recordRule.startTime = new Date(model.manualStartTime.getTime());
            // End Time
            String sVal = binding.duration.getText().toString();
            int duration = parseInt(sVal);
            model.recordRule.endTime = new Date(model.manualStartTime.getTime() + duration * 60000L);
            // Call Sign
            initText(binding.callSign, model.recordRule.station);
            // Start Time & End Time
            initText(binding.dateTime, model.recordRule.startTime, model.recordRule.endTime);
        }
    }

    private void initText(TextView view, Date time, Date endTime) {
        if (mTimeFormatter == null) {
            Context context = MyApplication.getAppContext();
            mTimeFormatter = android.text.format.DateFormat.getTimeFormat(context);
            mDateFormatter = android.text.format.DateFormat.getDateFormat(context);
            mDayFormatter = new SimpleDateFormat("EEE ");
        }
        String result = new String();
        if (time != null) {
            result = mDayFormatter.format(time)
                            + mDateFormatter.format(time)
                            + " " + mTimeFormatter.format(time);
        }
        if (endTime != null) {
            result = result + " - " + mTimeFormatter.format(endTime);
        }
        view.setText(result);
    }

    private void initText(TextView view, CharSequence text) {
        view.setText(text);
    }

    private void initText(TextView view, int number) {
        view.setText(String.valueOf(number));
    }

    private void setupSpinner(Spinner spin, ArrayList<String> list) {
        setupSpinner(spin, list.toArray(new String[]{}));
    }

    private void setupSpinner(Spinner spin, String[] list) {
        ArrayAdapter<String> ad = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                list);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin.setAdapter(ad);
        spin.setSelection(0);
    }

    private ArrayList<String> makeStrings(int[] resources) {
        ArrayList<String> list = new ArrayList<>();
        Context context = getContext();
        for (int res : resources) {
            list.add(context.getString(res));
        }
        return list;
    }

    private void initSpinner(Spinner spin, int[] prompts, String[] values, String text) {
        if (prompts == null)
            setupSpinner(spin, values);
        else
            setupSpinner(spin, makeStrings(prompts));
        if (text != null) {
            for (int ix = 0; ix < values.length; ix++) {
                if (text.equalsIgnoreCase(values[ix])) {
                    spin.setSelection(ix);
                    break;
                }
            }
        }
    }

    private void initMultiSpinner(MultiSpinner spin, String[] prompts, int checkedMask) {
        spin.setEntries(prompts);
        boolean[] selected = new boolean[prompts.length];
        for (int ix = 0; ix < selected.length; ix++) {
            selected[ix] = ((checkedMask & (1 << ix)) != 0);
        }
        spin.setSelected(selected);
    }

    private void setupScheduleType() {
        // Type options depend on what is being done
        // Logic from ScheduleEditor::Load, values from recordintypes.cpp
        // toDescription(RecordingType rectype) and toRawString(RecordingType rectype)
        typePrompts.clear();
        typeOptions.clear();

        if ("Recording Template".equalsIgnoreCase(model.recordRule.type)) {
            if (!"Default".equalsIgnoreCase(model.recordRule.category)
                    && model.recordRule.recordId > 0) {
                typePrompts.add(R.string.sched_type_del_template);
                typeOptions.add("Not Recording");
            }
            typePrompts.add(R.string.sched_type_mod_template);
            typeOptions.add("Recording Template");
        } else if ("Override Recording".equalsIgnoreCase(model.recordRule.type)
                || "Do not Record".equalsIgnoreCase(model.recordRule.type)
                || model.neverRecord || model.newOverride) {
            if (model.neverRecord) {
                typePrompts.add(R.string.sched_type_forget_history);
                typeOptions.add("Forget History");
            } else {
                typePrompts.add(R.string.sched_type_del_override);
                typeOptions.add("Not Recording");
                typePrompts.add(R.string.sched_type_rec_override);
                typeOptions.add("Override Recording");
                typePrompts.add(R.string.sched_type_dont_rec_override);
                typeOptions.add("Do not Record");
            }
            if (!"Manual Search".equals(model.recordRule.searchType)) {
                typePrompts.add(R.string.sched_type_never_rec_override);
                typeOptions.add("Never Record");
            }
            if (BackendCache.getInstance().canForgetHistory) {
                // current recording = 3, prev recording = 2, never rec = 11
                if (model.recordRule.recStatusCode == 2
                        || model.recordRule.recStatusCode == 3
                        || model.recordRule.recStatusCode == 11) {
                    typePrompts.add(R.string.sched_type_forget_history);
                    typeOptions.add("Forget History");
                }
            }
        } else {
            boolean hasChannel = (model.recordRule.station != null);
            final boolean isManual = "Manual Search".equalsIgnoreCase(model.recordRule.searchType);
            final boolean isSearch = !"None".equalsIgnoreCase(model.recordRule.searchType);
            typePrompts.add(R.string.sched_type_not);
            typeOptions.add("Not Recording");
            if (hasChannel && !isSearch || isManual) {
                typePrompts.add(R.string.sched_type_this);
                typeOptions.add("Single Record");
            }
            if (!isManual) {
                typePrompts.add(R.string.sched_type_one);
                typeOptions.add("Record One");
            }
            if (!hasChannel || isManual || isSearch) {
                typePrompts.add(R.string.sched_type_weekly);
                typeOptions.add("Record Weekly");
                typePrompts.add(R.string.sched_type_daily);
                typeOptions.add("Record Daily");
            }
            if (!isManual) {
                typePrompts.add(R.string.sched_type_all);
                typeOptions.add("Record All");
            }
        }
        int[] intTypePrompts = new int[typePrompts.size()];
        for (int ix = 0; ix < intTypePrompts.length; ix++)
            intTypePrompts[ix] = typePrompts.get(ix);
        initSpinner(binding.scheduleType, intTypePrompts, typeOptions.toArray(new String[]{}), model.recordRule.type);
    }

    private void dynamicSetups(int position) {
        if (model.schedReason == model.SCHED_NEWRULE) {
            binding.title.setText(null);
            binding.subtitle.setText(null);
            binding.description.setText(null);
        }
        if (position != SRCH_MANUAL) {
            binding.channelHeading.setVisibility(View.GONE);
            binding.channel.setVisibility(View.GONE);
            binding.dttimeHeading.setVisibility(View.GONE);
            binding.dttime.setVisibility(View.GONE);
            binding.durationHeading.setVisibility(View.GONE);
            binding.duration.setVisibility(View.GONE);
        }
        if ("Recording Template".equals(model.recordRule.type)) {
            binding.title.setFocusableInTouchMode(true);
            binding.subtitleHeading.setVisibility(View.GONE);
            binding.subtitle.setVisibility(View.GONE);
            binding.descriptionHeading.setVisibility(View.GONE);
            binding.description.setVisibility(View.GONE);
        }
        else switch (position) {
            case SRCH_NONE:
                binding.title.setFocusable(false);
                binding.subtitleHeading.setVisibility(View.VISIBLE);
                binding.subtitle.setVisibility(View.VISIBLE);
                binding.subtitleHeading.setText(R.string.sched_subtitle);
                binding.subtitle.setFocusable(false);
                binding.descriptionHeading.setText(R.string.sched_description);
                binding.description.setFocusable(false);
                break;
            case SRCH_POWER:
                binding.title.setFocusableInTouchMode(true);
                binding.subtitleHeading.setVisibility(View.VISIBLE);
                binding.subtitle.setVisibility(View.VISIBLE);
                binding.subtitleHeading.setText(R.string.sched_add_tables);
                binding.subtitle.setFocusableInTouchMode(true);
                binding.descriptionHeading.setText(R.string.sched_sql_where);
                binding.description.setFocusableInTouchMode(true);
                break;
            case SRCH_TITLE:
            case SRCH_KWORD:
            case SRCH_PEOPLE:
                binding.title.setFocusable(false);
                binding.subtitleHeading.setVisibility(View.GONE);
                binding.subtitle.setVisibility(View.GONE);
                binding.descriptionHeading.setText(R.string.sched_srch_value);
                binding.description.setFocusableInTouchMode(true);
                break;
            case SRCH_MANUAL:
                binding.title.setFocusableInTouchMode(true);
                binding.subtitleHeading.setVisibility(View.GONE);
                binding.subtitle.setVisibility(View.GONE);
                binding.descriptionHeading.setText(R.string.sched_description);
                binding.description.setFocusable(false);
                binding.description.setText(null);
                binding.channelHeading.setVisibility(View.VISIBLE);
                binding.channel.setVisibility(View.VISIBLE);
                binding.dttimeHeading.setVisibility(View.VISIBLE);
                binding.dttime.setVisibility(View.VISIBLE);
                binding.durationHeading.setVisibility(View.VISIBLE);
                binding.duration.setVisibility(View.VISIBLE);
                break;
        }
        enableSave();
    }

    private void updateFromView() {
        fixTitle();
        // Call Sign - N/A - display only
        // Start Time - N/A - display only
        // Template - N/A - applied when selected
        // Search Type (spinner)
        int srchPos = binding.searchType.getSelectedItemPosition();
        model.recordRule.searchType = searchValues[srchPos];
        // Title
        model.recordRule.title = binding.title.getText().toString();
        // Subtitle
        model.recordRule.subtitle = binding.subtitle.getText().toString();
        // Season & Episode - N/A - display only
        // Description
        model.recordRule.description = binding.description.getText().toString();
        // Special values for manual search
        if (srchPos == SRCH_MANUAL) {
            // Channel
            int chanPos = binding.channel.getSelectedItemPosition();
            model.recordRule.chanId = model.chanids.get(chanPos);
            model.recordRule.station = model.callSigns.get(chanPos);
            // Start Time
            model.recordRule.startTime = new Date(model.manualStartTime.getTime());
            // Findday
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(model.recordRule.startTime);
            model.recordRule.findDay = cal.get(GregorianCalendar.DAY_OF_WEEK);
            if (model.recordRule.findDay == 7)
                model.recordRule.findDay = 0;
            // findtime
            final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
            model.recordRule.findTime = timeFormat.format(model.recordRule.startTime);
            // End Time
            String sVal = binding.duration.getText().toString();
            int duration = parseInt(sVal);
            model.recordRule.endTime = new Date(model.manualStartTime.getTime() + duration * 60000L);
        }
        // Schedule Type (spinner)
        int position = binding.scheduleType.getSelectedItemPosition();
        model.recordRule.type = typeOptions.get(position);
        // Recording Group (spinner)
        position = binding.recordingGroup.getSelectedItemPosition();
        model.recordRule.recGroup = model.mRecGroupList.get(position);
        // Active
        model.recordRule.inactive = !binding.active.isChecked();
        // Playback Group
        position = binding.playbackGroup.getSelectedItemPosition();
        model.recordRule.playGroup = model.mPlayGroupList.get(position);
        // Start Offset
        String sVal = binding.startOffset.getText().toString();
        int nVal = parseInt(sVal);
        model.recordRule.startOffset = nVal;
        // End Offset
        sVal = binding.endOffset.getText().toString();
        nVal = parseInt(sVal);
        model.recordRule.endOffset = nVal;
        // New Episodes Only
        model.recordRule.newEpisOnly = binding.newEpisOnly.isChecked();
        // Priority
        sVal = binding.recPriority.getText().toString();
        nVal = parseInt(sVal);
        model.recordRule.recPriority = nVal;
        // Preferred Input (spinner)
        position = binding.preferredInput.getSelectedItemPosition();
        model.recordRule.preferredInput = inputValues.get(position);
        // Dup Method (spinner)
        position = binding.dupMethod.getSelectedItemPosition();
        model.recordRule.dupMethod = sDupMethodValues[position];
        // Dup Scope (spinner)
        position = binding.dupIn.getSelectedItemPosition();
        model.recordRule.dupIn = sDupScopeValues[position];
        // Auto Extend (spinner)
        position = binding.autoExtend.getSelectedItemPosition();
        model.recordRule.autoExtend = sExtendValues[position];
        // Filters (MultiSpinner)
        boolean[] selected = binding.selectedFilters.getSelected();
        nVal = 0;
        for (int ix = 0; ix < selected.length; ix++) {
            if (selected[ix])
                nVal |= (1 << ix);
        }
        model.recordRule.filter = nVal;
        // Recording Profile (spinner)
        position = binding.recProfile.getSelectedItemPosition();
        model.recordRule.recProfile = sRecProfileValues[position];
        // Storage Group (spinner)
        position = binding.storageGroup.getSelectedItemPosition();
        model.recordRule.storageGroup = model.mRecStorageGroupList.get(position);
        // Max Episodes
        sVal = binding.maxEpisodes.getText().toString();
        nVal = parseInt(sVal);
        model.recordRule.maxEpisodes = nVal;
        // Deletion on max (max newest)
        model.recordRule.maxNewest = binding.maxNewest.isChecked();
        // Auto Expire
        model.recordRule.autoExpire = binding.autoExpire.isChecked();
        // post Processing (MultiSpinner)
        selected = binding.postProc.getSelected();
        model.recordRule.autoCommflag = selected[0];
        model.recordRule.autoMetaLookup = selected[1];
        model.recordRule.autoTranscode = selected[2];
        model.recordRule.autoUserJob1 = selected[3];
        model.recordRule.autoUserJob2 = selected[4];
        model.recordRule.autoUserJob3 = selected[5];
        model.recordRule.autoUserJob4 = selected[6];
        // Metadata Lookup Id (Inetref)
        model.recordRule.inetref = binding.inetref.getText().toString();
    }

    // Fix title for search types
    void fixTitle() {
        EditText source = null;
        EditText dest2 = null;
        int srchPos = binding.searchType.getSelectedItemPosition();
        String suffix = "("+getString(searchPrompts[srchPos])+")";
        switch (srchPos) {
            case SRCH_MANUAL:
                dest2 = binding.description;
            case SRCH_POWER:
                String title = binding.title.getText().toString();
                if (!title.matches(".*\\(.*\\).*"))
                    source = binding.title;
                break;
            case SRCH_TITLE:
            case SRCH_KWORD:
            case SRCH_PEOPLE:
                source = binding.description;
                break;
        }
        if ("Recording Template".equalsIgnoreCase(model.recordRule.type)) {
            source = null;
            String title = binding.title.getText().toString();
            if (!title.matches(".*\\(.*\\).*")) {
                source = binding.title;
                suffix = "(" + getString(R.string.recrule_template) + ")";
            }
        }
        if (source != null) {
            String text = source.getText().toString();
            text = text.trim();
            if (!text.isEmpty()) {
                text = text +" " + suffix;
            }
            String titleText = binding.title.getText().toString();
            if (!text.equals(titleText))
                binding.title.setText(text);
        }
        if (dest2 != null) {
            String oldText = dest2.getText().toString();
            String newtext = binding.title.getText().toString();
            if (!newtext.equals(oldText))
                dest2.setText(newtext);
        }
        enableSave();
    }

    private void save(boolean close) {
        updateFromView();
        model.save(close);
    }

    private boolean canClose() {
        updateFromView();
        String recordRuleStr = AsyncBackendCall.getString(model.recordRule);
        int newHashCode = recordRuleStr.hashCode();
        if (newHashCode == model.savedHashCode)
            return true;
        else {
            boolean canSave = binding.saveButton.isEnabled();
            int prompts;
            if (canSave)
                prompts = R.array.prompt_save_changes;
            else
                prompts = R.array.prompt_cannot_save;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder
                    .setTitle(R.string.menu_changes)
                    .setItems(prompts,
                            (dialog, which) -> {
                                // The 'which' argument contains the index position
                                // of the selected item
                                // 0 = save, 1 = continue, 2 = exit
                                if (!canSave)
                                    which++;
                                switch (which) {
                                    case 0:
                                        save(true);
                                        break;
                                    case 2:
                                        model.savedHashCode = newHashCode;
                                        close();
                                        break;
                                }
                            });
            builder.show();
            return false;
        }
    }

    private void close() {
        getActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    // Save is disabled for deleting a non-existent record
    // It is not disabled for updating a record without any changes
    // because that would mean checking all fields after every
    // keystroke
    private void enableSave() {
        boolean enabled = true;
        int typePos = binding.scheduleType.getSelectedItemPosition();
        String type = typeOptions.get(typePos);
        boolean notRec = "Not Recording".equals(type);
        if (model.recordRule == null
                || (model.recordRule.recordId == 0 && notRec)
                || (model.schedReason == ScheduleViewModel.SCHED_NEWRULE
                    && binding.searchType.getSelectedItemPosition() == SRCH_NONE)
                || binding.title.getText().length() == 0)
            enabled = false;
        binding.saveButton.setEnabled(enabled);
    }

    static private class NumericFocus implements View.OnFocusChangeListener {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            TextView tv = (TextView) v;
            String s = tv.getText().toString();
            if (hasFocus) {
                if ("0".equals(s))
                    tv.setText("");
            } else {
                int numval = parseInt(s);
                tv.setText(String.valueOf(numval));
            }
        }
    }

}