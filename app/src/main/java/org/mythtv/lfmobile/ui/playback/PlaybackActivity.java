package org.mythtv.lfmobile.ui.playback;

import androidx.annotation.OptIn;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.UnstableApi;

import android.os.Bundle;

import org.mythtv.lfmobile.R;

public class PlaybackActivity extends AppCompatActivity {
    public static final String VIDEO = "Video";
    public static final String BOOKMARK = "bookmark";
    public static final String POSBOOKMARK = "posbookmark";
    public static final String FRAMERATE = "framerate";

    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, PlaybackFragment.newInstance())
                    .commitNow();
            // It is using android:theme="@style/Theme.AppCompat.NoActionBar" to eliminate
            // the action bar instead of this code, which will now crash with a NullPointerException.
//            ActionBar actionBar = getSupportActionBar();
//            actionBar.hide();
        }
    }
}