package org.mythtv.mobfront.ui.playback;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import org.mythtv.mobfront.R;

public class PlaybackActivity extends AppCompatActivity {
    public static final String VIDEO = "Video";
    public static final String BOOKMARK = "bookmark";
    public static final String POSBOOKMARK = "posbookmark";
    public static final String FRAMERATE = "framerate";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, PlaybackFragment.newInstance())
                    .commitNow();
            ActionBar actionBar = getSupportActionBar();
            actionBar.hide();
        }
    }
}