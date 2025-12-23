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
    public static final int TIMESLOT_SIZE = 30; //minutes
    // 8 time slots = 4 hours of guide data at a time
    public static final int TIMESLOTS = 8;
    private static DateFormat mTimeFormatter;
    private static DateFormat mDateFormatter;
    private static DateFormat mDayFormatter;
    // time of first entry
    Date guideStartTime;
    int chanGroupIx;
    int chanGroupId;
    ArrayList<String> chanGroupNames;
    ArrayList<Integer> chanGroupIDs;
    ArrayList<ChannelSlot> chanList = new ArrayList<>();
    MutableLiveData<ArrayList<ChannelSlot>> chanLiveData = new MutableLiveData<>();
    ArrayList<String> timeslotList = new ArrayList<>();
    MutableLiveData<ArrayList<String>> dateLiveData = new MutableLiveData<>();
    ArrayList<ProgSlot> progList = new ArrayList<>();
    MutableLiveData<ArrayList<ProgSlot>> progLiveData = new MutableLiveData<>();
    String allTitle = "";

    public GuideViewModel() {
        Context context = MyApplication.getAppContext();
        allTitle = context.getString(R.string.all_title) + "\t";
        chanGroupIx = 0;
        loadDefaultGroup();
    }

    void refresh(boolean resetTimeslots) {
        loadTimeslots(resetTimeslots);
        loadChanGroups();
    }

    synchronized void loadTimeslots(boolean resetTimeslots) {
        GregorianCalendar now = new GregorianCalendar();
        if (guideStartTime != null && !resetTimeslots) {
            now.setTime(guideStartTime);
        }
        int minute = now.get(Calendar.MINUTE);
        if (minute < TIMESLOT_SIZE) minute = 0;
        else minute = TIMESLOT_SIZE;
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        now.set(Calendar.MINUTE, minute);
        guideStartTime = new Date(now.getTimeInMillis());
        if (mTimeFormatter == null) {
            Context context = MyApplication.getAppContext();
            mTimeFormatter = android.text.format.DateFormat.getTimeFormat(context);
            mDateFormatter = android.text.format.DateFormat.getDateFormat(context);
            mDayFormatter = new SimpleDateFormat("EEE ");
        }
        Date time = new Date(guideStartTime.getTime());
        timeslotList.clear();
        for (int ix = 0; ix < TIMESLOTS; ix++) {
            timeslotList.add(mDayFormatter.format(time) + mDateFormatter.format(time) + " " + mTimeFormatter.format(time));
            time.setTime(time.getTime() + TIMESLOT_SIZE * 60 * 1000);
        }
        dateLiveData.postValue(timeslotList);
    }

    synchronized void loadChanGroups() {
        AsyncBackendCall call = new AsyncBackendCall((caller) -> {
            XmlNode result = caller.getXmlResult();
            if (result == null) return;
            loadDefaultGroup();
            XmlNode groupNode = null;
            for (; ; ) {
                if (groupNode == null)
                    groupNode = result.getNode("ChannelGroups").getNode("ChannelGroup");
                else groupNode = groupNode.getNextSibling();
                if (groupNode == null) break;
                chanGroupIDs.add(groupNode.getInt("GroupId", 0));
                chanGroupNames.add(groupNode.getString("Name"));
            }
            String defGroupName = Settings.getString("chan_group");
            chanGroupIx = chanGroupNames.indexOf(defGroupName);
            if (chanGroupIx < 0) chanGroupIx = 0;
            chanGroupId = chanGroupIDs.get(chanGroupIx);
            loadGuide();
        });
        call.execute(Action.CHAN_GROUPS);
    }

    void loadDefaultGroup() {
        chanGroupIDs = new ArrayList<>();
        chanGroupIDs.add(0);
        chanGroupNames = new ArrayList<>();
        chanGroupNames.add(MyApplication.getAppContext().getString(R.string.all_title) + "\t");
    }

    synchronized private void loadGuide() {
        AsyncBackendCall call = new AsyncBackendCall((caller) -> {
            XmlNode result = caller.getXmlResult();
            if (result == null) return;
            loadGuideData(result, 0);
            chanLiveData.postValue(chanList);
            progLiveData.postValue(progList);
        });
        Date guideEndTime = new Date(guideStartTime.getTime() + TIMESLOT_SIZE * TIMESLOTS * 60000);
//        call.params = new Object[]{Integer.valueOf(chanGroupId), guideStartTime, guideEndTime};
        call.args.put("CHANGROUPID", chanGroupId);
        call.args.put("STARTTIME", guideStartTime);
        call.args.put("ENDTIME", guideEndTime);
        call.execute(Action.GUIDE);
    }

    void loadGuideData(XmlNode result, int start) {
        // If the user has changed time period or channel group,
        // throw away furhter use of the old group or time slot
        if (result == null)
            return;
        chanList.clear();
        progList.clear();
        XmlNode chanNode = null;
        for (; ; ) {
            if (chanNode == null)
                chanNode = result.getNode("Channels").getNode("ChannelInfo", start);
            else chanNode = chanNode.getNextSibling();
            if (chanNode == null) break;

            ChannelSlot entry = new ChannelSlot();
            entry.chanId = chanNode.getInt("ChanId", -1);
            entry.chanNum = chanNode.getString("ChanNum");
            entry.callSign = chanNode.getString("CallSign");
            entry.iconURL = chanNode.getString("IconURL");
            chanList.add(entry);
            int adapterPos = progList.size();
            // Add an empty row to the progList
            Date timeSlot = new Date(guideStartTime.getTime());
            for (int count = 0; count < TIMESLOTS; count++) {
                int pos;
                if (count == 0)
                    pos = ProgSlot.POS_LEFT;
                else if (count == TIMESLOTS - 1)
                    pos = ProgSlot.POS_RIGHT;
                else
                    pos = ProgSlot.POS_MIDDLE;
                progList.add(new ProgSlot(ProgSlot.CELL_PROGRAM,pos,new Date(timeSlot.getTime())));
                timeSlot.setTime(timeSlot.getTime()+30*60000);
            }

            XmlNode programNode = null;
            for (; ; ) {
                if (programNode == null)
                    programNode = chanNode.getNode("Programs").getNode("Program");
                else programNode = programNode.getNextSibling();
                if (programNode == null) break;
                Program program = new Program(programNode, chanNode);
                if (program.startTime == null || program.endTime == null) continue;
                long lPos = (program.startTime.getTime() - guideStartTime.getTime()) / (TIMESLOT_SIZE * 60);
                float fPos = (float) lPos / 1000.0f;
                // Start position is the slot wherein the show starts.
                int startPos = (int) (fPos);
                if (startPos >= TIMESLOTS) continue;
                if (startPos < 0) startPos = 0;

                lPos = (program.endTime.getTime() - guideStartTime.getTime()) / (TIMESLOT_SIZE * 60);
                fPos = (float) lPos / 1000.0f;
                // End position is the slot before the one where the show ends
                // unless it ends in the same slot as it starts.
                int endPos = (int) (fPos);
                if (endPos <= 0) continue;
                if (endPos >= TIMESLOTS) endPos = TIMESLOTS;
                if (endPos == startPos) ++endPos;

                for (int ix = adapterPos + startPos; ix < adapterPos + endPos; ix++) {
                    ProgSlot slot = progList.get(ix);
                    if (slot.program == null) slot.program = program;
                    else if (slot.program2 == null) {
                        if (program.startTime.after(slot.program.startTime))
                            slot.program2 = program;
                        else {
                            slot.program2 = slot.program;
                            slot.program = program;
                        }
                    }
                }
            }
        }
    }

}
