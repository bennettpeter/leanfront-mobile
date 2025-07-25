package org.mythtv.mobfront.ui.settings;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import org.mythtv.mobfront.MainActivity;
import org.mythtv.mobfront.R;
import org.mythtv.mobfront.ui.videolist.VideoListModel;

//import org.mythtv.mobfront.databinding.FragmentSettingsBinding;

public class SettingsFragment extends PreferenceFragmentCompat
{

//    private FragmentSettingsBinding binding;
    private boolean reloadDB;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
//        PreferenceManager preferenceManager = getPreferenceManager();
//        PreferenceScreen screen = preferenceManager.createPreferenceScreen(getContext());
//        PreferenceCategory cat = new PreferenceCategory(getContext());
//        cat.setTitle("MythTV Backend");
//        screen.addPreference(cat);
//        Preference ipAddress = new EditTextPreference(getContext());
//        ipAddress.setTitle("IP Address or DNS name");
//        ipAddress.setKey("pref_backend");
//        cat.addPreference(ipAddress);
//        setPreferenceScreen(screen);
        setPreferencesFromResource(R.xml.preferences,null);
        findPreference("pref_backend")
                .setOnPreferenceChangeListener((pref,action) -> {
                String newVal = action.toString();
                // strip any '[' or ']' characters, which are invalid and will
                // be used for identifying an IPV6
                newVal = newVal.replace("[","");
                newVal = newVal.replace("]","");
                ((EditTextPreference)pref).setText(newVal);
//                MainActivity.startFetch(-1, null, null);
                reloadDB = true;
                return false;
            });
        findPreference("pref_http_port")
            .setOnPreferenceChangeListener((pref,action) -> {
                ((EditTextPreference)pref).setText(validateNumber(action, 1, 65535, 6544));
                reloadDB = true;
                return false;
            });

        findPreference("pref_max_vids")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, 1000, 90000, 10000));
                    reloadDB = true;
                    return false;
                });


        //        addPreferencesFromResource(R.xml.pref_2);
//        addPreferencesFromResource(R.xml.pref_2);
    }

//    public View onCreateView(@NonNull LayoutInflater inflater,
//                             ViewGroup container, Bundle savedInstanceState) {
//        SettingsViewModel settingsViewModel =
//                new ViewModelProvider(this).get(SettingsViewModel.class);
//
//        binding = FragmentSettingsBinding.inflate(inflater, container, false);
//        View root = binding.getRoot();
//
//        final TextView textView = binding.textSettings;
//        settingsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
//        return root;
//    }
//
//    @Override
//    public void onDestroyView() {
//        super.onDestroyView();
//        binding = null;
//    }


    @Override
    public void onResume() {
        reloadDB = false;
        super.onResume();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(null);
    }

    @Override
    public void onPause() {
        if (reloadDB && VideoListModel.instance != null)
            VideoListModel.instance.startFetch();
        reloadDB = false;
        super.onPause();
    }

    private static String validateNumber(Object action, int min, int max, int defValue) {
        String s;
        int i;
        s = action.toString();
        try {
            i = Integer.parseInt(s);
        } catch (Exception e) {
            i = defValue;
        }
        if (i < min)
            i = min;
        else if (i > max)
            i = max;
        s = String.valueOf(i);
        return s;
    }

}