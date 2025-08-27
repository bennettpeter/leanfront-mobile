package org.mythtv.lfmobile.data;

import android.database.Cursor;

/**
 * VideoCursorMapper maps a database Cursor to a Video object.
 * This can be used with video or videoview. With video,
 * lastUsed will be set to 0.
 */
public class VideoCursorMapper  {

    //    private int idIndex;
    private int rectypeIndex;
    private int titleIndex;
    private int titlematchIndex;
    private int nameIndex;
    private int descIndex;
    private int videoUrlIndex;
    private int videoUrlPathIndex;
    private int bgImageUrlIndex;
    private int cardImageUrlIndex;
    private int channelIndex;
    private int recordedidIndex;
    private int recGroupIndex;
    private int playGroupIndex;
    private int seasonIndex;
    private int episodeIndex;
    private int airdateIndex;
    private int starttimeIndex;
    private int endtimeIndex;
    private int durationIndex;
    private int prodyearIndex;
    private int filenameIndex;
    private int filesizeIndex;
    private int hostnameIndex;
    private int progflagsIndex;
    private int videoPropsIndex;
    private int videoPropNamesIndex;
    private int chanidIndex;
    private int channumIndex;
    private int callsignIndex;
    private int storageGroupIndex;
    private int lastUsedIndex;
    private int showRecentIndex;


    public void bindColumns(Cursor cursor) {
//        idIndex = cursor.getColumnIndex(VideoContract.VideoEntry._ID);
        rectypeIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_RECTYPE);
        titleIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_TITLE);
        titlematchIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_TITLEMATCH);
        nameIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_SUBTITLE);
        descIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_DESC);
        videoUrlIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_VIDEO_URL);
        videoUrlPathIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_VIDEO_URL_PATH);
        bgImageUrlIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL);
        cardImageUrlIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_CARD_IMG);
        channelIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_CHANNEL);
        recordedidIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_RECORDEDID);
        recGroupIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_RECGROUP);
        playGroupIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_PLAYGROUP);
        seasonIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_SEASON);
        episodeIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_EPISODE);
        airdateIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_AIRDATE);
        starttimeIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_STARTTIME);
        endtimeIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_ENDTIME);
        durationIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_DURATION);
        prodyearIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_PRODUCTION_YEAR);
        filenameIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_FILENAME);
        filesizeIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_FILESIZE);
        hostnameIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_HOSTNAME);
        progflagsIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_PROGFLAGS);
        videoPropsIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_VIDEOPROPS);
        videoPropNamesIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_VIDEOPROPNAMES);
        chanidIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_CHANID);
        channumIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_CHANNUM);
        callsignIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_CALLSIGN);
        storageGroupIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_STORAGEGROUP);
        lastUsedIndex = cursor.getColumnIndex(VideoContract.StatusEntry.COLUMN_LAST_USED);
        showRecentIndex = cursor.getColumnIndex(VideoContract.StatusEntry.COLUMN_SHOW_RECENT);
    }

    public Video get(Cursor cursor) {

        // One time it failed with cursor closed. I don't know why
        // but maybe this will catch it.
        if (cursor.isClosed())
            return new Video.VideoBuilder()
                    .title("ERROR - CURSOR CLOSED")
                    .build();
        // Get the values of the video.
        int rectype = cursor.getInt(rectypeIndex);
        String title = cursor.getString(titleIndex);
        String titlematch = cursor.getString(titlematchIndex);
        String subtitle = cursor.getString(nameIndex);
        String desc = cursor.getString(descIndex);
        String videoUrl = cursor.getString(videoUrlIndex);
        String videoUrlPath = cursor.getString(videoUrlPathIndex);
        String bgImageUrl = cursor.getString(bgImageUrlIndex);
        String cardImageUrl = cursor.getString(cardImageUrlIndex);
        String channel = cursor.getString(channelIndex);
        String recordedid = cursor.getString(recordedidIndex);
        String recGroup = cursor.getString(recGroupIndex);
        String playGroup = cursor.getString(playGroupIndex);
        String season = cursor.getString(seasonIndex);
        String episode = cursor.getString(episodeIndex);
        String airdate = cursor.getString(airdateIndex);
        String starttime = cursor.getString(starttimeIndex);
        String endtime = cursor.getString(endtimeIndex);
        String duration = cursor.getString(durationIndex);
        String prodyear = cursor.getString(prodyearIndex);
        String filename = cursor.getString(filenameIndex);
        long filesize = cursor.getLong(filesizeIndex);
        String hostname = cursor.getString(hostnameIndex);
        String progflags = cursor.getString(progflagsIndex);
        String videoProps = cursor.getString(videoPropsIndex);
        String videoPropNames = cursor.getString(videoPropNamesIndex);
        String chanid = cursor.getString(chanidIndex);
        String channum = cursor.getString(channumIndex);
        String callsign = cursor.getString(callsignIndex);
        String storageGroup = cursor.getString(storageGroupIndex);
        long lastUsed = 0;
        if (lastUsedIndex >= 0 && !cursor.isNull(lastUsedIndex))
            lastUsed = cursor.getLong(lastUsedIndex);
        boolean showRecent = true;
        if (showRecentIndex >= 0 && !cursor.isNull(showRecentIndex))
            showRecent = (cursor.getInt(showRecentIndex) != 0);

        // Build a Video object to be processed.
        return new Video.VideoBuilder()
                .rectype(rectype)
                .title(title)
                .titlematch(titlematch)
                .subtitle(subtitle)
                .description(desc)
                .videoUrl(videoUrl)
                .videoUrlPath(videoUrlPath)
                .bgImageUrl(bgImageUrl)
                .cardImageUrl(cardImageUrl)
                .channel(channel)
                .recordedid(recordedid)
                .recGroup(recGroup)
                .playGroup(playGroup)
                .season(season)
                .episode(episode)
                .airdate(airdate)
                .starttime(starttime)
                .endtime(endtime)
                .duration(duration)
                .prodyear(prodyear)
                .filename(filename)
                .filesize(filesize)
                .hostname(hostname)
                .progflags(progflags)
                .videoProps(videoProps)
                .videoPropNames(videoPropNames)
                .chanid(chanid)
                .channum(channum)
                .callsign(callsign)
                .storageGroup(storageGroup)
                .lastUsed(lastUsed)
                .showRecent(showRecent)
                .build();
    }

//    private Cursor myCursor;
//    public void changeCursor(Cursor cursor) {
//        myCursor = cursor;
//    }

//    public Object get(int index) {
//        if (index >= 0)
//            myCursor.moveToPosition(index);
//        return convert(myCursor);
//    }
}
