package org.mythtv.mobfront.ui.videolist;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.mythtv.mobfront.MyApplication;
import org.mythtv.mobfront.R;
import org.mythtv.mobfront.data.FetchVideos;
import org.mythtv.mobfront.data.Video;
import org.mythtv.mobfront.data.VideoCursorMapper;
import org.mythtv.mobfront.data.VideoDbHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class VideoListModel extends ViewModel {

//    private final MutableLiveData<List<String>> mTexts;
    MutableLiveData<List<Video>> videos;
    static int level = 0;
//    ArrayList<Integer> typeList = new ArrayList();
    static final int TYPE_RECGROUP = 1;
    static final int TYPE_SERIES = 2;
    static final int TYPE_VIDEODIR = 3;
//    ArrayDeque <Level> stack = new ArrayDeque();
    int pageType;
    // Rec group being shown
    String recGroup;
    // title of series being shown
    String title;
    // Video directory being shown
    String directory;
    int focusedId;
    ArrayList <Video> videoList = new ArrayList<>();
    ArrayList <String> recGroups = new ArrayList<>();
    public static VideoListModel instance;
    String allTitle = "";

    public VideoListModel() {
        addCloseable(() -> {
            instance = null;
        });
        instance = this;
        videos = new MutableLiveData<>();
        pageType = TYPE_RECGROUP;
        Context context = MyApplication.getAppContext();
        allTitle = context.getString(R.string.group_all) + "\t";
        recGroup = allTitle;
        startFetch();
    }
    /**
     * Fetch video list
     *
     * @param rectype    Set to -1 to fetch all, or to either
     *                   VideoContract.VideoEntry.RECTYPE_RECORDING or
     *                   VideoContract.VideoEntry.RECTYPE_VIDEO
     * @param recordedId Set to null, or recordedId if only one to be refreshed
     * @param recGroup   Set to a recording group if only that one is to
     *                   be refreshed
     */
    public void startFetch(int rectype, String recordedId, String recGroup) {
        FetchVideos fetchVideos = new FetchVideos(MyApplication.getAppContext(), rectype, recordedId, recGroup);
        fetchVideos.execute((taskRunner) -> {
            refresh();
        });
    }

    public void startFetch() {
        startFetch(-1, null, null);
    }

    void refresh() {
        loadRecGroupList();
        if (pageType == TYPE_RECGROUP)
            loadRecGroup(recGroup);
        else if (pageType == TYPE_SERIES)
            loadTitle(title);
        videos.postValue(videoList);
    }
    void loadRecGroupList() {
        recGroups.clear();
        recGroups.add(allTitle);
        Context context = MyApplication.getAppContext();
        // Load only a list of unique recgroups
        VideoDbHelper dbh = VideoDbHelper.getInstance(context);
        SQLiteDatabase db = dbh.getReadableDatabase();
        if (db == null)
            return;
        final String sql = "SELECT recgroup FROM video "
                + "WHERE rectype = 1 "
                + "GROUP BY recgroup ORDER BY recgroup";
        Cursor csr = db.rawQuery(sql, null);
        if (csr.moveToFirst()) {
            while (!csr.isAfterLast()) {
                String recgroup = csr.getString(0);
                recGroups.add(recgroup);
                csr.moveToNext();
            }
        }
        csr.close();
    }

    void loadRecGroup(String recGroup) {
        pageType = TYPE_RECGROUP;
        this.recGroup = recGroup;
        videoList.clear();
        // Load only a list of unique titles
        Context context = MyApplication.getAppContext();
        VideoDbHelper dbh = VideoDbHelper.getInstance(context);
        SQLiteDatabase db = dbh.getReadableDatabase();
        if (db == null)
            return;
        StringBuilder sql = new StringBuilder("SELECT title, MIN(card_image) FROM video "
            + "WHERE rectype = 1 ");
        String [] parms;
        if (!allTitle.equals(recGroup)) {
            sql.append("AND recgroup = ? ");
            parms = new String[]{recGroup};
        }
        else
            parms = new String[0];
        sql.append("GROUP BY title ORDER BY titlematch");
        Cursor csr = db.rawQuery(sql.toString(), parms);
        if (csr.moveToFirst()) {
            while (!csr.isAfterLast()) {
                String title = csr.getString(0);
                Video video = new Video.VideoBuilder()
                        .id(-1).title(title)
                        .subtitle("")
                        .cardImageUrl(csr.getString(1))
                        .progflags("0")
                        .build();
                video.type = Video.TYPE_SERIES;
                videoList.add(video);
                csr.moveToNext();
            }
        }
        videos.postValue(videoList);
        csr.close();
    }

    void loadTitle(String title) {
        pageType = TYPE_SERIES;
        this.title = title;
        videoList.clear();
        // Load only a list of unique titles
        Context context = MyApplication.getAppContext();
        VideoDbHelper dbh = VideoDbHelper.getInstance(context);
        SQLiteDatabase db = dbh.getReadableDatabase();
        if (db == null)
            return;
        StringBuilder sql = new StringBuilder("SELECT * FROM video "
                + "WHERE rectype = 1 ");
        String [] parms;
        if (!allTitle.equals(recGroup)) {
            sql.append("AND recgroup = ? ");
            parms = new String[]{recGroup, title};
        }
        else
            parms = new String[]{title};
        sql.append("AND title = ? ORDER BY airdate, starttime");
        Cursor csr = db.rawQuery(sql.toString(), parms);
        VideoCursorMapper mapper = new VideoCursorMapper();
        if (csr.moveToFirst()) {
            mapper.bindColumns(csr);
            while (!csr.isAfterLast()) {
                Video video = mapper.get(csr);
                video.type = Video.TYPE_EPISODE;
                videoList.add(video);
                csr.moveToNext();
            }
        }
        videos.postValue(videoList);
        csr.close();

    }

}