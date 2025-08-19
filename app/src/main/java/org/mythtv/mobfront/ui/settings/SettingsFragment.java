package org.mythtv.mobfront.ui.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import org.mythtv.mobfront.R;
import org.mythtv.mobfront.data.BackendCache;
import org.mythtv.mobfront.ui.videolist.VideoListModel;

public class SettingsFragment extends PreferenceFragmentCompat
{

    public static boolean isActive = false;
    private boolean reloadDB;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences,null);
        findPreference("pref_backend")
                .setOnPreferenceChangeListener((pref,action) -> {
                String newVal = action.toString();
                // strip any '[' or ']' characters, which are invalid and will
                // be used for identifying an IPV6
                newVal = newVal.replace("[","");
                newVal = newVal.replace("]","");
                ((EditTextPreference)pref).setText(newVal);
                BackendCache.getInstance().authorization = null;
                reloadDB = true;
                return false;
            });
        findPreference("pref_http_port")
            .setOnPreferenceChangeListener((pref,action) -> {
                ((EditTextPreference)pref).setText(validateNumber(action, 1, 65535, 6544));
                reloadDB = true;
                return false;
            });

        findPreference("pref_backend_userid")
                .setOnPreferenceChangeListener((pref, action) -> {
                    ((EditTextPreference) pref).setText(action.toString().trim());
                    BackendCache.getInstance().authorization = null;
                    reloadDB = true;
                    return false;
                });

        findPreference("pref_backend_passwd")
                .setOnPreferenceChangeListener((pref, action) -> {
                    ((EditTextPreference) pref).setText(action.toString().trim());
                    BackendCache.getInstance().authorization = null;
                    reloadDB = true;
                    return false;
                });

        findPreference("pref_max_vids")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, 1000, 90000, 10000));
                    reloadDB = true;
                    return false;
                });


        findPreference("pref_skip_back")
            .setOnPreferenceChangeListener((pref,action) -> {
                ((EditTextPreference)pref).setText(validateNumber(action, 1, 3600, 10));
                return false;
            });

        findPreference("pref_skip_fwd")
            .setOnPreferenceChangeListener((pref,action) -> {
                ((EditTextPreference)pref).setText(validateNumber(action, 1, 3600, 60));
                return false;
            });

        findPreference("pref_commskip_start")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, -10, 10, 0));
                    return false;
                });

        findPreference("pref_commskip_end")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, -10, 10, 0));
                    return false;
                });

        findPreference("pref_num_cc_chans")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, -0, 4, 2));
                    return false;
                });

        findPreference("pref_jump")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, -1, 60, 5));
                    return false;
                });

        if (!BackendCache.getInstance().loginNeeded) {
            findPreference("pref_backend_userid").setVisible(false);
            findPreference("pref_backend_passwd").setVisible(false);
        }
    }

    @Override
    public void onResume() {
        isActive = true;
        reloadDB = false;
        super.onResume();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(null);
        if (BackendCache.getInstance().loginNeeded) {
            findPreference("pref_backend_userid").setVisible(true);
            findPreference("pref_backend_passwd").setVisible(true);
        } else {
            findPreference("pref_backend_userid").setVisible(false);
            findPreference("pref_backend_passwd").setVisible(false);
        }
    }

    @Override
    public void onPause() {
        if (reloadDB && VideoListModel.getInstance() != null)
            VideoListModel.getInstance().startFetch();
        reloadDB = false;
        isActive = false;
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