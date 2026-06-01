package org.mythtv.lfmobile;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.appcompat.app.AppCompatActivity;

import org.mythtv.lfmobile.data.Settings;
import org.mythtv.lfmobile.data.XmlNode;
import org.mythtv.lfmobile.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private MainActivityModel viewModel;
    private ActivityMainBinding binding;

    private AppBarConfiguration mAppBarConfiguration;
    private NavController navController;
    public MyFragment myFragment;
    public static MainActivity mainActivity;
    public View mainView;
    public boolean bottomNavEnabled;
    // Important - these must correspond to items in
    // StringArray(R.array.startview_pref_values)
    static int[] navItems = {
            R.id.nav_videolist,
            R.id.nav_settings,
            R.id.nav_guide,
            R.id.nav_recrules,
            R.id.nav_upcoming
    };
    public static int startupView;
    static String[] views;

    private static final String TAG = "lfm";
    static final String CLASS = "MainActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        views = res.getStringArray(R.array.startview_pref_values);
        viewModel = new ViewModelProvider(this).get(MainActivityModel.class);
        MainActivityModel.instance = viewModel;
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mainView = binding.getRoot());
        setSupportActionBar(binding.appBarMain.toolbar);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        assert navHostFragment != null;
        navController = navHostFragment.getNavController();
        getStartView();
        if (!viewModel.startupDone) {
            NavGraph navGraph = navController.getNavInflater().inflate(R.navigation.mobile_navigation);
            navGraph.setStartDestination(startupView);
            navController.setGraph(navGraph);
            viewModel.startupDone = true;
        }
        NavigationView navigationView = binding.navView;
        if (navigationView != null) {
            mAppBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_videolist, R.id.nav_settings, R.id.nav_guide, R.id.nav_recrules,
                    R.id.nav_upcoming)
                    .setOpenableLayout(binding.drawerLayout)
                    .build();
            NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
            NavigationUI.setupWithNavController(navigationView, navController);
        }

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            bottomNavEnabled = Settings.getBoolean("pref_land_bottomnav");
        else
            bottomNavEnabled = true;

        BottomNavigationView bottomNavigationView = binding.appBarMain.contentMain.bottomNavView;

        if (bottomNavigationView != null && !bottomNavEnabled) {
            bottomNavigationView.setVisibility(View.GONE);
            bottomNavigationView = null;
        }

        if (bottomNavigationView != null) {
            mAppBarConfiguration = new AppBarConfiguration.Builder(startupView)
                    .build();
            NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
            NavigationUI.setupWithNavController(bottomNavigationView, navController);
        }
        viewModel.toast.observe(this, (Integer msg) -> {
            if (msg == 0)
                return;
            Toast.makeText(this,
                msg, Toast.LENGTH_LONG)
                .show();
            viewModel.toast.setValue(0);
        });
        viewModel.navigate.observe(this, (Integer dest) -> {
            if (dest == 0)
                return;
            navController.navigate(dest.intValue());
            viewModel.navigate.setValue(0);
        });
        mainActivity = this;
        binding.appBarMain.toolbar.setTitleTextAppearance(this,R.style.ToolbarTitleText);
        binding.appBarMain.toolbar.setSubtitleTextAppearance(this,R.style.ToolbarSubtitleText);
    }

    private void getStartView() {
        String start = Settings.getString("startview");
        int ix = find(views, start, 0);
        startupView = navItems[ix];
    }

    public static int find(String[] arr, String target, int idefault) {
        for (int ix = 0 ; ix < arr.length; ix++) {
            if (target.equals(arr[ix])) return ix;
        }
        return idefault;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.overflow, menu);
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_refresh && myFragment != null) {
            myFragment.startFetch();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        if (myFragment != null)
            if (myFragment.navigateUp())
                return true;
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String backendIP = Settings.getString("pref_backend");
        backendIP = XmlNode.fixIpAddress(backendIP);
        if (backendIP.length() == 0) {
            navController.navigate(R.id.nav_settings);
        }
        viewModel.startMythTask();
    }

    public interface MyFragment  {
        default void startFetch() {}
        default boolean navigateUp(){
            return false;
        }
    }
}
