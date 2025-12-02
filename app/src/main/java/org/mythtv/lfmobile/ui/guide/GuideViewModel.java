package org.mythtv.lfmobile.ui.guide;

import android.content.Context;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.mythtv.lfmobile.MyApplication;
import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.data.Action;
import org.mythtv.lfmobile.data.AsyncBackendCall;
import org.mythtv.lfmobile.data.Settings;
import org.mythtv.lfmobile.data.XmlNode;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class GuideViewModel extends ViewModel {
    //    public static final int TIMESLOTS = 8;
//    public static final int COLUMNS = TIMESLOTS+2;
    public static final int TIMESLOT_SIZE = 30; //minutes
    //    public static final int TIME_ROW_INTERVAL = 8;
    public static final int TIMESLOTS = 8;
//    public static final int TIMESLOTS = 60 * 24 / TIMESLOT_SIZE * DATE_RANGE;

//    MutableLiveData<ArrayList<GuideSlot>> mutableLiveData;
//    private final ArrayList<GuideSlot> guideSlots = new ArrayList<>();

    // time of first entry
    Date guideStartTime;
//    Date displayStartTime;
//    int displayStartPos;
    int chanGroupIx;
    int chanGroupId;
    ArrayList<String> chanGroupNames;
    ArrayList<Integer> chanGroupIDs;

    // An arraylist for each time slot
    // Each entry of timeSlots is an arraylist of all channels for that slot
    private ArrayList<ArrayList<ProgSlot>> progList = new ArrayList<>();
    MutableLiveData<ArrayList<ArrayList<ProgSlot>>> progLiveData = new MutableLiveData<>();
    ArrayList<ChannelSlot> chanList = new ArrayList<>();
    MutableLiveData<ArrayList<ChannelSlot>> chanLiveData = new MutableLiveData<>();
    ArrayList<String> timeslotList = new ArrayList<>();
    MutableLiveData<ArrayList<String>> dateLiveData = new MutableLiveData<>();
    String allTitle = "";
    private static DateFormat mTimeFormatter;
    private static DateFormat mDateFormatter;
    private static DateFormat mDayFormatter;

    // Program slot data
    // One day's data - 48 entries for each channel
//    private ArrayList<GuideSlot> guiddeList = new ArrayList<>();


    public GuideViewModel() {
        Context context = MyApplication.getAppContext();
        allTitle = context.getString(R.string.all_title) + "\t";
        chanGroupIx = 0;
        loadDefaultGroup();
    }

    void refresh() {
        loadTimeslots();
        loadChanGroups();
    }

    synchronized void loadTimeslots() {
        GregorianCalendar now = new GregorianCalendar();
        int minute = now.get(Calendar.MINUTE);
        if (minute < TIMESLOT_SIZE)
            minute = 0;
        else
            minute = TIMESLOT_SIZE;
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        now.set(Calendar.MINUTE, minute);
        guideStartTime = new Date(now.getTimeInMillis());
//        now.set(Calendar.HOUR_OF_DAY, 0);
//        now.set(Calendar.MINUTE, 0);
//        guideStartTime = new Date(now.getTimeInMillis());
        if (mTimeFormatter == null) {
            Context context = MyApplication.getAppContext();
            mTimeFormatter = android.text.format.DateFormat.getTimeFormat(context);
            mDateFormatter = android.text.format.DateFormat.getDateFormat(context);
            mDayFormatter = new SimpleDateFormat("EEE ");
        }
        Date time = guideStartTime;
        timeslotList.clear();
        for (int ix = 0; ix < TIMESLOTS; ix++) {
            timeslotList.add(mDayFormatter.format(time) + mDateFormatter.format(time)
                    + " " + mTimeFormatter.format(time));
//            if (time.equals(displayStartTime))
//                displayStartPos = timeslotList.size() - 2;
            time.setTime(time.getTime() + TIMESLOT_SIZE*60*1000);
        }
        dateLiveData.postValue(timeslotList);
    }

    synchronized void loadChanGroups() {
        AsyncBackendCall call = new AsyncBackendCall((caller) -> {
            XmlNode result = caller.getXmlResult();
            if (result == null)
                return;
            loadDefaultGroup();
            XmlNode groupNode = null;
            for (; ; ) {
                if (groupNode == null)
                    groupNode = result.getNode("ChannelGroups").getNode("ChannelGroup");
                else
                    groupNode = groupNode.getNextSibling();
                if (groupNode == null)
                    break;
                chanGroupIDs.add(groupNode.getInt("GroupId",0));
                chanGroupNames.add(groupNode.getString("Name"));
            }
            String defGroupName = Settings.getString("chan_group");
            chanGroupIx = chanGroupNames.indexOf(defGroupName);
            if (chanGroupIx < 0)
                chanGroupIx = 0;
            chanGroupId = chanGroupIDs.get(chanGroupIx);
            loadChannels();
        });
        call.execute(Action.CHAN_GROUPS);
    }

    void loadDefaultGroup() {
        chanGroupIDs = new ArrayList<>();
        chanGroupIDs.add(0);
        chanGroupNames = new ArrayList<>();
        chanGroupNames.add(MyApplication.getAppContext().getString(R.string.all_title) + "\t");
    }

    Date getSlotDate(int slot) {
        if (slot >= timeslotList.size())
            slot = timeslotList.size() - 1;
        if (slot < 0)
            slot = 0;
        long time = guideStartTime.getTime() + slot * (TIMESLOT_SIZE*60*1000);
        return new Date(time);
    }

    int getDateSlot(Date date) {
        long slot = ( date.getTime() - guideStartTime.getTime() ) / (TIMESLOT_SIZE*60*1000);
        if (slot >= timeslotList.size())
            slot = timeslotList.size() - 1;
        if (slot < 0)
            slot = 0;
        return (int) slot;
    }

    synchronized private void loadChannels() {
        AsyncBackendCall call = new AsyncBackendCall((caller) -> {
            XmlNode node = caller.getXmlResult();
            if (node == null)
                return;
            chanList.clear();
            node = node.getNode(new String [] {"Channels","ChannelInfo"},0);
            while (node != null) {
                ChannelSlot entry = new ChannelSlot();
                entry.chanId = node.getInt("ChanId",-1);
                entry.chanNum = node.getString("ChanNum");
                entry.callSign = node.getString("CallSign");
                entry.iconURL = node.getString("IconURL");
                chanList.add(entry);
                node = node.getNextSibling();
            }
//            ChannelSlot entry = new ChannelSlot();
//            entry.chanId = 0;
//            entry.chanNum = "";
//            entry.callSign = "";
//            chanList.add(entry);
            chanLiveData.postValue(chanList);
        });
        // Dates here are 1 and 2 minute past midnight in 1970
        // This to get a channel list without any guide data
        call.params = new Object[] {new Integer(chanGroupId), new Date(60000L), new Date(120000L)};
        call.execute(Action.GUIDE);
    }

}
