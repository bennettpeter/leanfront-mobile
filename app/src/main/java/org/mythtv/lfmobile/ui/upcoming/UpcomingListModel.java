package org.mythtv.lfmobile.ui.upcoming;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.mythtv.lfmobile.data.Action;
import org.mythtv.lfmobile.data.AsyncBackendCall;
import org.mythtv.lfmobile.data.XmlNode;

import java.util.ArrayList;

public class UpcomingListModel extends ViewModel {

    MutableLiveData<ArrayList<UpcomingItem>> upcomings= new MutableLiveData<>();
    private final ArrayList<UpcomingItem> upcomingList = new ArrayList<>();
    boolean showAll;
    public static final int TYPE_UPCOMING = 1;
    public static final int TYPE_GUIDE_SEARCH = 2;
    public int type = TYPE_UPCOMING;
    String search;
    volatile int callId;

    public UpcomingListModel() {
    }

    public void startFetch() {
        AsyncBackendCall call = new AsyncBackendCall((caller) -> {
            upcomingList.clear();
            XmlNode node = caller.getXmlResult();
            if (node == null)
                return;
            node = node.getNode(new String [] {"Programs","Program"},0);
            while (node != null) {
                if (callId > caller.id)
                    return;
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
            refreshScreen();
        });
        call.mainThread = false;
        call.id = ++callId;
        if (type == TYPE_UPCOMING) {
            call.params = new Boolean(showAll);
            call.execute(Action.GETUPCOMINGLIST);
        }
        else if (type == TYPE_GUIDE_SEARCH) {
            call.params = search;
            call.execute(Action.SEARCHGUIDE_TITLE);
        }
    }

    void refreshScreen() {
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