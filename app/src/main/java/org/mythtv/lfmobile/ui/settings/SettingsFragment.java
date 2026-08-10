package org.mythtv.lfmobile.ui.settings;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.mythtv.lfmobile.MainActivity;
import org.mythtv.lfmobile.MainActivityModel;
import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.data.BackendCache;
import org.mythtv.lfmobile.ui.videolist.VideoListModel;

public class SettingsFragment extends PreferenceFragmentCompat implements MainActivity.MyFragment
{

    public static boolean isActive = false;
    private boolean reloadDB;
    private MenuProvider menuProvider;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences,null);
        myFindPreference ("pref_backend")
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
        myFindPreference ("pref_http_port")
            .setOnPreferenceChangeListener((pref,action) -> {
                ((EditTextPreference)pref).setText(validateNumber(action, 1, 65535, 6544));
                reloadDB = true;
                return false;
            });

        myFindPreference ("pref_backend_userid")
                .setOnPreferenceChangeListener((pref, action) -> {
                    ((EditTextPreference) pref).setText(action.toString().trim());
                    BackendCache.getInstance().authorization = null;
                    reloadDB = true;
                    return false;
                });

        myFindPreference ("pref_backend_passwd")
                .setOnPreferenceChangeListener((pref, action) -> {
                    ((EditTextPreference) pref).setText(action.toString().trim());
                    BackendCache.getInstance().authorization = null;
                    reloadDB = true;
                    return false;
                });

        myFindPreference ("pref_max_vids")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, 1000, 90000, 10000));
                    reloadDB = true;
                    return false;
                });


        myFindPreference ("pref_skip_back")
            .setOnPreferenceChangeListener((pref,action) -> {
                ((EditTextPreference)pref).setText(validateNumber(action, 1, 3600, 10));
                return false;
            });

        myFindPreference ("pref_skip_fwd")
            .setOnPreferenceChangeListener((pref,action) -> {
                ((EditTextPreference)pref).setText(validateNumber(action, 1, 3600, 60));
                return false;
            });

        myFindPreference ("pref_commskip_start")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, -10, 10, 0));
                    return false;
                });

        myFindPreference ("pref_commskip_end")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, -10, 10, 0));
                    return false;
                });

        myFindPreference ("pref_num_cc_chans")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, -0, 4, 2));
                    return false;
                });

        myFindPreference ("pref_jump")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, -1, 60, 5));
                    return false;
                });
        myFindPreference ("pref_tweak_ts_search_pkts")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, 600, 100000, 2600));
                    return false;
                });
        myFindPreference ("pref_drag_range")
                .setOnPreferenceChangeListener((pref,action) -> {
            ((EditTextPreference)pref).setText(validateNumber(action, 5, 60, 20));
            return false;
        });
        myFindPreference ("pref_drag_accel")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateFloat(action, 1f, 10f, 3.5f));
                    return false;
                });

        myFindPreference ("pref_duration_textsize")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, 14, 50, 14));
                    return false;
                });

        myFindPreference ("pref_guide_timeslots")
                .setOnPreferenceChangeListener((pref,action) -> {
                    ((EditTextPreference)pref).setText(validateNumber(action, 1, 16, 8));
                    return false;
                });

        if (!BackendCache.getInstance().loginNeeded) {
            myFindPreference ("pref_backend_userid").setVisible(false);
            myFindPreference ("pref_backend_passwd").setVisible(false);
        }
    }


    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OnBackPressedCallback bpCallback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                onBack();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, bpCallback);
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
                MenuItem settingsItem = menu.findItem(R.id.nav_settings);
                if (settingsItem != null)
                    settingsItem.setVisible(false);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                return false;
            }
        };
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onResume() {
        ((MainActivity)requireActivity()).myFragment = this;
        isActive = true;
        reloadDB = false;
        super.onResume();
        if (menuProvider != null) {
            requireActivity().addMenuProvider(menuProvider, getViewLifecycleOwner());
        }
        ActionBar bar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (bar != null) {
            bar.setSubtitle(null);
            bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE|ActionBar.DISPLAY_SHOW_HOME|ActionBar.DISPLAY_HOME_AS_UP);
            bar.setHomeAsUpIndicator(R.drawable.west_24px);
            bar.setHomeButtonEnabled(true);
        }
        if (BackendCache.getInstance().loginNeeded) {
            myFindPreference ("pref_backend_userid").setVisible(true);
            myFindPreference ("pref_backend_passwd").setVisible(true);
        } else {
            myFindPreference ("pref_backend_userid").setVisible(false);
            myFindPreference ("pref_backend_passwd").setVisible(false);
        }
        View v = ((MainActivity) requireActivity()).mainView;
        View nav = v.findViewById(R.id.bottom_nav_view);
        if (nav != null)
            nav.setVisibility(View.GONE);

        DrawerLayout drawer = v.findViewById(R.id.drawer_layout);
        if (drawer != null) {
            if (drawer.getDrawerLockMode(Gravity.LEFT) == DrawerLayout.LOCK_MODE_UNLOCKED) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        }

    }

    @Override
    public void onPause() {
        ((MainActivity)requireActivity()).myFragment = null;
        if (menuProvider != null) {
            requireActivity().removeMenuProvider(menuProvider);
            requireActivity().invalidateMenu();
        }
        if (reloadDB && VideoListModel.getInstance() != null)
            VideoListModel.getInstance().startFetch();
        reloadDB = false;
        isActive = false;
        super.onPause();
        MainActivityModel viewModel = new ViewModelProvider(requireActivity()).get(MainActivityModel.class);
        if (BackendCache.getInstance().authorization == null)
            viewModel.restartMythTask();
    }

        public void onBack() {
        ((MainActivity)requireActivity()).resetApp();
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

    @SuppressWarnings("SameParameterValue")
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

    @NonNull Preference myFindPreference(CharSequence key) {
        Preference ret = findPreference (key);
        if (ret == null)
            ret = new Preference(requireContext());
        return ret;
    }
}