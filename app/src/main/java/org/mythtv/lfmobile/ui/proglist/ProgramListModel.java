package org.mythtv.lfmobile.ui.proglist;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.mythtv.lfmobile.data.Action;
import org.mythtv.lfmobile.data.AsyncBackendCall;
import org.mythtv.lfmobile.data.XmlNode;

import java.util.ArrayList;

public class ProgramListModel extends ViewModel {

    MutableLiveData<ArrayList<ProgramItem>> programs = new MutableLiveData<>();
    private final ArrayList<ProgramItem> programList = new ArrayList<>();
    boolean showAll;
    public static final int TYPE_UPCOMING = 1;
    public static final int TYPE_GUIDE_SEARCH = 2;
    public int type = TYPE_UPCOMING;
    String search;
    volatile int callId;

    public void startFetch() {
        AsyncBackendCall call = new AsyncBackendCall((caller) -> {
            programList.clear();
            XmlNode node = caller.getXmlResult();
            if (node == null)
                return;
            node = node.getNode(new String [] {"Programs","Program"},0);
            while (node != null) {
                if (callId > caller.id)
                    return;
                ProgramItem item = new ProgramItem();
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
                programList.add(item);
                node = node.getNextSibling();
            }
            refreshScreen();
        });
        call.id = ++callId;
        if (type == TYPE_UPCOMING) {
            call.args.put("SHOWALL",showAll);
            call.execute(Action.GETUPCOMINGLIST);
        }
        else if (type == TYPE_GUIDE_SEARCH) {
            if (search != null && ! search.isEmpty()) {
                call.args.put("TITLEFILTER", search);
                call.execute(Action.SEARCHGUIDE_TITLE);
            }
        }
    }

    void refreshScreen() {
        synchronized(this) {
            programs.postValue(programList);
        }
    }

    static class ProgramItem {
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