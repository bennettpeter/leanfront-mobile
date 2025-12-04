package org.mythtv.lfmobile.ui.videolist;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.mythtv.lfmobile.MyApplication;
import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.data.FetchVideos;
import org.mythtv.lfmobile.data.Settings;
import org.mythtv.lfmobile.data.Video;
import org.mythtv.lfmobile.data.VideoCursorMapper;
import org.mythtv.lfmobile.data.VideoDbHelper;

import java.util.ArrayList;
import java.util.List;

public class VideoListModel extends ViewModel {

    MutableLiveData<List<Video>> videos;
    static final int TYPE_RECGROUP = 1;
    static final int TYPE_SERIES = 2;
    static final int TYPE_VIDEODIR = 3;
    int pageType;
    // Rec group being shown
    String recGroup;
    // title of series being shown
    String title;
    private final ArrayList <Video> videoList = new ArrayList<>();
    ArrayList <String> recGroups = new ArrayList<>();
    private static VideoListModel instance;
    String allTitle = "";
    String videosTitle = "";
    // videoPath must not have any leading or trailing slash
    String videoPath = "";

    public VideoListModel() {
        addCloseable(() -> {
            instance = null;
        });
        instance = this;
        videos = new MutableLiveData<>();
        pageType = TYPE_RECGROUP;
        Context context = MyApplication.getAppContext();
        allTitle = context.getString(R.string.all_title) + "\t";
        recGroup = allTitle;
        videosTitle = context.getString(R.string.group_videos) + "\t";
        setRecGroup(allTitle);
        startFetch();
    }

    public static VideoListModel getInstance() {
        return instance;
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
        synchronized(this) {
            loadRecGroupList();
            if (pageType == TYPE_RECGROUP)
                loadRecGroup(recGroup);
            else if (pageType == TYPE_SERIES)
                loadTitle();
            else if (pageType == TYPE_VIDEODIR)
                loadVideos();
            videos.postValue(videoList);
        }
    }
    private void loadRecGroupList() {
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
        recGroups.add(videosTitle);
    }


    void setRecGroup(String recGroup) {
        this.recGroup = recGroup;
        if (videosTitle.equals(recGroup)) {
            setVideos("");
            return;
        }
        pageType = TYPE_RECGROUP;
        this.title = recGroup;
    }
    private void loadRecGroup(String recGroup) {
        videoList.clear();
        // Add an "All" entry at the top to represent all series together
        {
            Video video = new Video.VideoBuilder()
                    .id(-1).title(allTitle)
                    .subtitle("")
                    .progflags("0")
                    .build();
            video.type = Video.TYPE_SERIES;
            videoList.add(video);
        }
        // Load only a list of unique titles
        Context context = MyApplication.getAppContext();
        VideoDbHelper dbh = VideoDbHelper.getInstance(context);
        SQLiteDatabase db = dbh.getReadableDatabase();
        if (db == null)
            return;
        StringBuilder sql = new StringBuilder("SELECT title, MIN(bg_image_url), MIN(card_image) FROM video "
            + "WHERE rectype = 1 ");
        String [] parms;
        if (allTitle.equals(recGroup)) {
            sql.append("AND recgroup NOT IN ('LiveTV','Deleted') ");
            parms = new String[0];
        } else {
            sql.append("AND recgroup = ? ");
            parms = new String[]{recGroup};
        }
        sql.append("GROUP BY title ORDER BY titlematch");
        Cursor csr = db.rawQuery(sql.toString(), parms);
        if (csr.moveToFirst()) {
            while (!csr.isAfterLast()) {
                String imageUrl = csr.getString(1);
                if (imageUrl == null)
                    imageUrl = csr.getString(2);
                String title = csr.getString(0);
                Video video = new Video.VideoBuilder()
                        .id(-1).title(title)
                        .subtitle("")
                        .cardImageUrl(imageUrl)
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

    void setTitle(String title) {
        pageType = TYPE_SERIES;
        this.title = title;
    }

    private void loadTitle() {
        videoList.clear();
        Context context = MyApplication.getAppContext();
        VideoDbHelper dbh = VideoDbHelper.getInstance(context);
        SQLiteDatabase db = dbh.getReadableDatabase();
        if (db == null)
            return;
        StringBuilder sql = new StringBuilder("SELECT * FROM video "
                + "WHERE rectype = 1 ");
        ArrayList <String> parms = new ArrayList<>();
        if (allTitle.equals(recGroup)) {
            sql.append("AND recgroup NOT IN ('LiveTV','Deleted') ");
        } else{
            sql.append("AND recgroup = ? ");
            parms.add(recGroup);
        }
        if (!allTitle.equals(title)) {
            sql.append("AND title = ? ");
            parms.add(title);
        }
        String ascdesc = Settings.getString("pref_seq_ascdesc");
        String sort = Settings.getString("pref_seq");
        sql.append("ORDER BY ")
            .append(sort).append(" ").append(ascdesc);
        if ("airdate".equals(sort))
            sql.append(", ")
                .append("season ").append(ascdesc).append(", ")
                .append("episode ").append(ascdesc).append(", ")
                .append("starttime ").append(ascdesc);
        Cursor csr = db.rawQuery(sql.toString(), parms.toArray(new String[]{}));
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

    // input dir is a subdirectory of the current videoPath
    void setVideos(String dir) {
        pageType = TYPE_VIDEODIR;
        if ("..".equals(dir)) {
            int lsp = videoPath.lastIndexOf('/');
            if (lsp >= 0)
                videoPath = videoPath.substring(0, lsp);
            else
                videoPath = "";
        } else if (dir.length() > 0) {
            if (videoPath.length() > 0)
                videoPath = videoPath + "/" + dir;
            else
                videoPath = dir;
        }
        Context context = MyApplication.getAppContext();
        String colon;
        if (videoPath.length() > 0)
            colon = " : ";
        else
            colon = "";

        this.title = context.getString(R.string.group_videos) + colon + videoPath;
    }


        // input dir is a subdirectory of the current videoPath
    private void loadVideos() {
        videoList.clear();
        Context context = MyApplication.getAppContext();
        VideoDbHelper dbh = VideoDbHelper.getInstance(context);
        SQLiteDatabase db = dbh.getReadableDatabase();
        if (db == null)
            return;
        String fnSort = makeTitleSort("filename", '/').toString();
        String sql = "SELECT * FROM videoview "
                + "WHERE rectype = 2 "
                + "AND  filename like ? "
                + "ORDER BY "
                + fnSort;
        String [] parms;
        if (videoPath.length() > 0)
            parms = new String[]{videoPath + "/%"};
        else
            parms = new String[]{"%"};
        Cursor csr = db.rawQuery(sql, parms);
        VideoCursorMapper mapper = new VideoCursorMapper();
        String cursubdir = "";
        int startPoint = videoPath.length();
        if (startPoint > 0)
            startPoint = 1;
        if (csr.moveToFirst()) {
            mapper.bindColumns(csr);
            while (!csr.isAfterLast()) {
                Video video = mapper.get(csr);
                int lsp = video.filename.lastIndexOf('/');
                if (lsp > videoPath.length()) {
                    String subdir = video.filename.substring(videoPath.length()+startPoint, lsp);
                    int lsps = subdir.lastIndexOf('/');
                    if (lsps >= 0)
                        subdir = subdir.substring(0,lsps);
                    if (subdir.equals(cursubdir)) {
                        csr.moveToNext();
                        continue;
                    }
                    else {
                        video = new Video.VideoBuilder()
                                .id(-1).title(subdir)
                                .subtitle(null)
                                .cardImageUrl(video.cardImageUrl)
                                .progflags("0")
                                .build();
                        video.type = Video.TYPE_VIDEODIR;
                        cursubdir = subdir;
                    }
                }
                else {
                    video.type = Video.TYPE_VIDEO;
                }
                videoList.add(video);
                csr.moveToNext();
            }
        }
        videos.postValue(videoList);
        csr.close();
    }

    /**
     * Create the Sql to sort with excluding articles "the" "a" etc at the front
     * or at the front of directory names
     * @param columnName Column for sorting on
     * @param delim Delimiter to use - ^ for title and / for directory
     * @return StringBuilder with resulting phrase for "order by"
     */
    public static StringBuilder makeTitleSort(String columnName, char delim) {
        final String[] articles = MyApplication.getAppContext().getResources()
                .getStringArray(R.array.title_sort_articles);
        // Sort uppercase title
        // REPLACE(REPLACE(REPLACE('^'||UPPER(title),'^THE ','^'),'^A ','^'),'^AN ','^')
        StringBuilder titleSort = new StringBuilder();
        titleSort.append("'").append(delim).append("'||UPPER(")
                .append(columnName).append(")");
        for (String article : articles) {
            // Empty entries may be a single space
            article = article.trim();
            if (article != null && article.length() > 0) {
                titleSort.insert(0, "REPLACE(");
                titleSort.append(",'").append(delim).append(article)
                        .append(" ','").append(delim).append("')");
            }
        }
        return titleSort;
    }

}