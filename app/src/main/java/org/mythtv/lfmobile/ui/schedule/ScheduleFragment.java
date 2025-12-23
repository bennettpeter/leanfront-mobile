package org.mythtv.lfmobile.ui.schedule;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.mythtv.lfmobile.MainActivity;
import org.mythtv.lfmobile.MyApplication;
import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.data.AsyncBackendCall;
import org.mythtv.lfmobile.data.BackendCache;
import org.mythtv.lfmobile.databinding.FragmentScheduleBinding;
import org.mythtv.lfmobile.ui.MultiSpinner;
import org.mythtv.lfmobile.ui.proglist.ProgramListModel;
import org.mythtv.lfmobile.ui.videolist.VideoListModel;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ScheduleFragment extends MainActivity.MyFragment {

    private ScheduleViewModel model;
    private FragmentScheduleBinding binding;

    private static DateFormat mTimeFormatter;
    private static DateFormat mDateFormatter;
    private static DateFormat mDayFormatter;
    private String templateName = "";
    ArrayList<Integer> typePrompts = new ArrayList<>();
    ArrayList<String> typeOptions = new ArrayList<>();
    ArrayList<String> inputPrompts = new ArrayList<>();
    ArrayList<Integer> inputValues = new ArrayList<>();
    private boolean hideNav = false;

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

    static final NumericFocus numericFocus= new NumericFocus();
    private MenuProvider menuProvider;

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
        if (hideNav) {
            View v = ((MainActivity) getActivity()).mainView;
            View nav = v.findViewById(R.id.bottom_nav_view);
            nav.setVisibility(View.VISIBLE);
        }
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
        binding = FragmentScheduleBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        View v = ((MainActivity)getActivity()).mainView;
        View nav = v.findViewById(R.id.bottom_nav_view);
        if (nav != null) {
            if (nav.getVisibility() == View.VISIBLE) {
                hideNav = true;
                nav.setVisibility(View.GONE);
            }
        }
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

    }

    @Override
    public void onStart() {
        super.onStart();
        model.toast.observe(this, (Integer msg) -> {
            if (msg == 0)
                return;
            Toast.makeText(getContext(),
                            msg, Toast.LENGTH_LONG)
                    .show();
            model.toast.setValue(0);
        });

        model.initDoneLiveData.observe(getViewLifecycleOwner(), (done) -> {
            switch (done) {
                case ScheduleViewModel.INIT_READY:
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
    }

    @Override
    public void onResume() {
        super.onResume();
        ((MainActivity) getActivity()).myFragment = this;
        if (menuProvider != null)
            getActivity().addMenuProvider(menuProvider,getViewLifecycleOwner());
    }

    @Override
    public void onPause() {
        ((MainActivity) getActivity()).myFragment = null;
        if (menuProvider != null) {
            getActivity().removeMenuProvider(menuProvider);
            getActivity().invalidateMenu();
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
        // Start Time
        initText(binding.dateTime, model.recordRule.startTime);
        // Template
//        setupSpinner(binding.template, model.templateNames);
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
                    if (! newTamplateName.equals(templateName)) {
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
        binding.searchType.setEnabled(model.schedType != model.SCHED_GUIDE
                && model.recordRule.recordId == 0);
        binding.searchType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                model.recordRule.searchType = searchValues[position];
                // Schedule Type changes based on search type
                setupScheduleType();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //error if we get here
            }
        });
        // Title
        initText(binding.title, model.recordRule.title);
        // Subtitle
        initText(binding.subtitle, model.recordRule.subtitle);
        // Season & Episode
        if (model.recordRule.episode > 0)
            initText(binding.seasonEpisode,
                    "S" + model.recordRule.season + " E" + model.recordRule.episode);
        // Description
        initText(binding.description, model.recordRule.description);
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
            }
        });
//        binding.scheduleType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                model.recordRule.type = typeOptions.get(position);
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {
//            }
//        });
        // Recording Group
        initSpinner(binding.recordingGroup, null,
                model.mRecGroupList.toArray(new String[]{}), model.recordRule.recGroup);
//        binding.recordingGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                model.recordRule.recGroup = model.mRecGroupList.get(position);
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {
//            }
//        });
        // Active
        binding.active.setChecked(!model.recordRule.inactive);
//        binding.active.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
//            model.recordRule.inactive = !isChecked;
//        });
        // Playback Group
        initSpinner(binding.playbackGroup, null,
                model.mPlayGroupList.toArray(new String[]{}), model.recordRule.playGroup);
        // Start Offset
        initText(binding.startOffset, model.recordRule.startOffset);
        binding.startOffset.setOnFocusChangeListener(numericFocus);
        binding.startOffset.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {  }
            @Override public void afterTextChanged(Editable s) {
                validateNumber(binding.startOffset, s,-480,480, 0);
            }
        });
        // End Offset
        initText(binding.endOffset, model.recordRule.endOffset);
        binding.endOffset.setOnFocusChangeListener(numericFocus);
        binding.endOffset.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {  }
            @Override public void afterTextChanged(Editable s) {
                validateNumber(binding.endOffset, s,-480,480, 0);
            }
        });
        // New Episodes Only
        binding.newEpisOnly.setChecked(model.recordRule.newEpisOnly);
        // Priority
        initText(binding.recPriority, model.recordRule.recPriority);
        binding.recPriority.setOnFocusChangeListener(numericFocus);
        binding.recPriority.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {  }
            @Override public void afterTextChanged(Editable s) {
                validateNumber(binding.recPriority, s,-100,100, 0);
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
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {  }
            @Override public void afterTextChanged(Editable s) {
                validateNumber(binding.maxEpisodes, s,0,100, 0);
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
        // Save Button
        binding.saveButton.setOnClickListener((v -> {
            save(false);
        }));
        // Close Button
        binding.closeButton.setOnClickListener((v -> {
            close();
        }));
    }

    private void initText(TextView view, Date time) {
        if (mTimeFormatter == null) {
            Context context = MyApplication.getAppContext();
            mTimeFormatter = android.text.format.DateFormat.getTimeFormat(context);
            mDateFormatter = android.text.format.DateFormat.getDateFormat(context);
            mDayFormatter = new SimpleDateFormat("EEE ");
        }
        if (time != null) {
            view.setText(
                    mDayFormatter.format(time)
                            + mDateFormatter.format(time)
                            + " " + mTimeFormatter.format(time));
        }
    }

    private void initText(TextView view, CharSequence text) {
        if (text != null)
            view.setText(text);
    }

    private void initText(TextView view, int number) {
        if (view.hasFocus() && number != 0)
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
            if (!"Default".equalsIgnoreCase(model.recordRule.category)) {
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

    private void updateFromView() {
        // Call Sign - N/A - display only
        // Start Time - N/A - display only
        // Template - N/A - applied when selected
        // Search Type (spinner)
        int position = binding.searchType.getSelectedItemPosition();
        model.recordRule.searchType = searchValues[position];
        // Title
        model.recordRule.title = binding.title.getText().toString();
        // Subtitle
        model.recordRule.subtitle = binding.subtitle.getText().toString();
        // Season & Episode - N/A - display only
        // Description
        model.recordRule.description = binding.description.getText().toString();
        // Schedule Type (spinner)
        position = binding.scheduleType.getSelectedItemPosition();
        model.recordRule.type = typeOptions.get(position);
        // Recording Group (spinner)
        position = binding.recordingGroup.getSelectedItemPosition();
        model.recordRule.recGroup = model.mRecGroupList.get(position);
        // Active
        model.recordRule.inactive = ! binding.active.isChecked();
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
        boolean [] selected = binding.selectedFilters.getSelected();
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
        model.recordRule.autoCommflag =   selected[0];
        model.recordRule.autoMetaLookup = selected[1];
        model.recordRule.autoTranscode =  selected[2];
        model.recordRule.autoUserJob1 =   selected[3];
        model.recordRule.autoUserJob2 =   selected[4];
        model.recordRule.autoUserJob3 =   selected[5];
        model.recordRule.autoUserJob4 =   selected[6];
        // Metadata Lookup Id (Inetref)
        model.recordRule.inetref = binding.inetref.getText().toString();
    }

    private void save(boolean close) {
        updateFromView();
        model.save(close);
    }

    private boolean canClose() {
        updateFromView();
        String recordRuleStr = AsyncBackendCall.getString(model.recordRule);
        int newHashCode = recordRuleStr.hashCode();
        if (!binding.saveButton.isEnabled() || newHashCode == model.savedHashCode
                || model.savedHashCode == 0)
            return true;
        else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder
                    .setTitle(R.string.menu_changes)
                    .setItems(R.array.prompt_save_changes,
                            (dialog, which) -> {
                                // The 'which' argument contains the index position
                                // of the selected item
                                // 0 = save, 1 = continue, 2 = exit
                                switch (which) {
                                    case 0:
                                        save(true);
                                        break;
                                    case 2:
                                        model.savedHashCode = 0;
                                        close();
                                        break;
                                }
                            });
            builder.show();
            return false;
        }
//        getActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    private void close() {
        getActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    // Save is disabled for deleteing a non-existent record
    // It is not disabled for updating a record without any changes
    // because that would mean checking all fields after every
    // keystroke
    private void enableSave() {
        boolean enabled = true;
        if (model.recordRule == null
                || (model.recordRule.recordId == 0
                && "Not Recording". equals(model.recordRule.type)))
            enabled = false;
//        else {
//            String recordRuleStr = AsyncBackendCall.getString(model.recordRule);
//            int newHashCode = recordRuleStr.hashCode();
//            if (newHashCode == model.savedHashCode)
//                enabled = false;
//        }
        binding.saveButton.setEnabled(enabled);
    }

    private static void validateNumber(EditText view, Object action, int min, int max, int defValue) {
        String s;
        int i;
        boolean fix = false;
        s = action.toString();
        if (s.isEmpty() || "-".equals(s))
            return;
        try {
            i = Integer.parseInt(s);
        } catch (Exception e) {
            i = defValue;
            fix = true;
        }
        if (i < min) {
            i = min;
            fix = true;
        }
        else if (i > max) {
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
    }

    private static int parseInt(String str) {
        int ret = 0;
        if (! str.isEmpty() && ! "-".equals(str)) {
            try {
                ret = Integer.parseInt(str);
            } catch (Exception ex) {
                ret = 0;
            }
        }
        return ret;
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