package org.mythtv.mobfront;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.Menu;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.appcompat.app.AppCompatActivity;

import org.mythtv.mobfront.data.Settings;
import org.mythtv.mobfront.data.XmlNode;
import org.mythtv.mobfront.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private MainActivityModel viewModel;
    private ActivityMainBinding binding;

    private AppBarConfiguration mAppBarConfiguration;
    private NavController navController;
    static final String TAG = "mfe";
    static final String CLASS = "MainActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MainActivityModel.class);
        MainActivityModel.instance = viewModel;
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.appBarMain.toolbar);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        assert navHostFragment != null;
        navController = navHostFragment.getNavController();

        NavigationView navigationView = binding.navView;
        if (navigationView != null) {
            mAppBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_videolist, R.id.nav_settings)
                    .setOpenableLayout(binding.drawerLayout)
                    .build();
            NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
            NavigationUI.setupWithNavController(navigationView, navController);
        }

        BottomNavigationView bottomNavigationView = binding.appBarMain.contentMain.bottomNavView;
        if (bottomNavigationView != null) {
            mAppBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_videolist)
                    .build();
            NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
            NavigationUI.setupWithNavController(bottomNavigationView, navController);
        }
        viewModel.toast.observe(this, (Integer msg) -> {
            Toast.makeText(this,
                msg, Toast.LENGTH_LONG)
                .show();
        });
        viewModel.navigate.observe(this, (Integer dest) -> {
            navController.navigate(dest.intValue());
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.overflow, menu);
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.nav_settings) {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
            navController.navigate(R.id.nav_settings);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
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
}
