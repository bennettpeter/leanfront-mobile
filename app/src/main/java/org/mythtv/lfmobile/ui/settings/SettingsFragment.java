package org.mythtv.lfmobile.ui.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import org.mythtv.lfmobile.MainActivity;
import org.mythtv.lfmobile.MainActivityModel;
import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.data.BackendCache;
import org.mythtv.lfmobile.data.Settings;
import org.mythtv.lfmobile.ui.videolist.VideoListModel;

public class SettingsFragment extends PreferenceFragmentCompat implements MainActivity.MyFragment
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
        findPreference("pref_tweak_ts_search_pkts")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, 600, 100000, 2600));
                    return false;
                });
        findPreference("pref_drag_range")
                .setOnPreferenceChangeListener((pref,action) -> {
            ((EditTextPreference)pref).setText(validateNumber(action, 5, 60, 20));
            return false;
        });
        findPreference("pref_drag_accel")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateFloat(action, 1f, 10f, 3.5f));
                    return false;
                });

        findPreference("pref_duration_textsize")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, 14, 50, 14));
                    return false;
                });

        findPreference("pref_land_bottomnav")
                .setOnPreferenceChangeListener((pref,action) -> {
                    getActivity().recreate();
                    return true;
                });
        findPreference("pref_startview")
                .setOnPreferenceChangeListener((pref,action) -> {
                    if (!Settings.getString("pref_startview").equals(action.toString())) {
                        getActivity().finish();
                        Intent i = getContext().getPackageManager()
                                .getLaunchIntentForPackage(getContext().getPackageName());
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getContext().startActivity(i);
                        return true;
                    }
                    return false;
                });
        if (!BackendCache.getInstance().loginNeeded) {
            findPreference("pref_backend_userid").setVisible(false);
            findPreference("pref_backend_passwd").setVisible(false);
        }
    }

    @Override
    public void onResume() {
        ((MainActivity)getActivity()).myFragment = this;
        isActive = true;
        reloadDB = false;
        super.onResume();
        ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        bar.setSubtitle(null);
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
        ((MainActivity)getActivity()).myFragment = null;
        if (reloadDB && VideoListModel.getInstance() != null)
            VideoListModel.getInstance().startFetch();
        reloadDB = false;
        isActive = false;
        super.onPause();
        MainActivityModel viewModel = new ViewModelProvider(getActivity()).get(MainActivityModel.class);
        if (BackendCache.getInstance().authorization == null)
            viewModel.restartMythTask();
    }

    public static String validateNumber(Object action, int min, int max, int defValue) {
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

    private static String validateFloat(Object action, float min, float max, float defValue) {
        String s;
        float f;
        s = action.toString();
        try {
            f = Float.parseFloat(s);
        } catch (Exception e) {
            f = defValue;
        }
        if (f < min)
            f = min;
        else if (f > max)
            f = max;
        s = String.valueOf(f);
        return s;
    }


}