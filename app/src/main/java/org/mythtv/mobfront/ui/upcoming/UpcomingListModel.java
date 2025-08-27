package org.mythtv.mobfront.ui.upcoming;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.mythtv.mobfront.MyApplication;
import org.mythtv.mobfront.R;
import org.mythtv.mobfront.data.Action;
import org.mythtv.mobfront.data.AsyncBackendCall;
import org.mythtv.mobfront.data.FetchVideos;
import org.mythtv.mobfront.data.Video;
import org.mythtv.mobfront.data.VideoCursorMapper;
import org.mythtv.mobfront.data.VideoDbHelper;
import org.mythtv.mobfront.data.XmlNode;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class UpcomingListModel extends ViewModel {

    MutableLiveData<ArrayList<UpcomingItem>> upcomings;
    private final ArrayList<UpcomingItem> upcomingList = new ArrayList<>();
    boolean showAll;

    public UpcomingListModel() {
        upcomings = new MutableLiveData<>();
//        refresh();
        startFetch();
    }

    public void startFetch() {
        AsyncBackendCall call = new AsyncBackendCall((caller) -> {
            upcomingList.clear();
            XmlNode node = caller.getXmlResult();
            if (node == null)
                return;
            node = node.getNode(new String [] {"Programs","Program"},0);
            while (node != null) {
                UpcomingItem item = new UpcomingItem();
                item.startTime = node.getString("StartTime");
                item.endTime = node.getString("EndTime");
                XmlNode chanNode = node.getNode("Channel");
                item.chanId = chanNode.getInt("ChanId",0);
                item.chanNum = chanNode.getString("ChanNum");
                item.callSign = chanNode.getString("CallSign");
                item.chanName = chanNode.getString("ChannelName");
                XmlNode recNode = node.getNode("Recording");
                item.status = recNode.getInt("Status",-999);
                item.statusName = recNode.getString("StatusName");
                item.title = node.getString("Title");
                item.subTitle = node.getString("SubTitle");
                item.season = node.getInt("Season",0);
                item.episode = node.getInt("Episode",0);
                item.description = node.getString("Description");
                upcomingList.add(item);
                node = node.getNextSibling();
            }
            refresh();
        });
        call.params = new Boolean(showAll);
        call.execute(Action.GETUPCOMINGLIST);
    }

    void refresh() {
        synchronized(this) {
            upcomings.postValue(upcomingList);
        }
    }

    static class UpcomingItem {
        public String startTime;
        public String endTime;
        public int chanId;
        public String chanNum;
        public String callSign;
        public String chanName;
        public int status;
        public String statusName;
        public String title;
        public String subTitle;
        public int season;
        public int episode;
        public String description;

    }
}