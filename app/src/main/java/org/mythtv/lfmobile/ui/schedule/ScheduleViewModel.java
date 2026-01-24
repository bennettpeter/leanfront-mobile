package org.mythtv.lfmobile.ui.schedule;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.mythtv.lfmobile.MyApplication;
import org.mythtv.lfmobile.R;
import org.mythtv.lfmobile.data.Action;
import org.mythtv.lfmobile.data.AsyncBackendCall;
import org.mythtv.lfmobile.data.VideoContract;
import org.mythtv.lfmobile.data.VideoDbHelper;
import org.mythtv.lfmobile.data.XmlNode;

import java.util.ArrayList;
import java.util.Date;

public class ScheduleViewModel extends ViewModel {
    public static final String REQID = "REQID";
    public static final String CHANID = "CHANID";
    public static final String STARTTIME = "STARTTIME";
    public static final String RECORDID = "RECORDID";
    public static final String SCHEDREASON = "SCHEDREASON";
    public static final String ISOVERRIDE = "ISOVERRIDE";
    private boolean initDone;
    MutableLiveData<Integer> initDoneLiveData = new MutableLiveData<>();
    static public final int INIT_READY = 1;
    static public final int INIT_SAVED = 2;
    static public final int INIT_CLOSE = 3;
    final MutableLiveData<Integer> toast = new MutableLiveData<>();

    int recordId;
    int schedReason;
    public final static int SCHED_GUIDE = 1;
    public final static int SCHED_RULELIST = 2;
    public final static int SCHED_UPCOMING = 3;
    public final static int SCHED_NEWRULE = 4;
    public final static int SCHED_NEWTEMPLATE = 5;

    boolean isOverride;
    boolean newOverride;
    boolean neverRecord;
    ArrayList<RecordRule> templateList = new ArrayList<>();
    public ArrayList<String> templateNames = new ArrayList<>();
    /*
    Details are in this order
        Action.GETPROGRAMDETAILS,
        Action.GETRECORDSCHEDULELIST,
        Action.GETPLAYGROUPLIST,
        Action.GETRECGROUPLIST,
        Action.GETRECSTORAGEGROUPLIST,
        Action.GETINPUTLIST
        Action.GETRECRULEFILTERLIST
 */
    ArrayList<XmlNode> detailsList;
    RecordRule recordRule;
    boolean isDirty;
    ArrayList<String> mPlayGroupList;
    ArrayList<String> mRecGroupList;
    ArrayList<String> mRecStorageGroupList;
    SparseArray<String> mInputList = new SparseArray<>();
    ArrayList<String> mRecRuleFilterList;
    ArrayList<String> names = new ArrayList<>();
    ArrayList<Integer> chanids = new ArrayList<>();
    ArrayList<String> callSigns = new ArrayList<>();
    // This date contains the value set by user before it is updated
    // into recordRule.startTime
    Date manualStartTime = new Date();

    int mGroupId;
    int chanId;
    Date startTime;
    int savedHashCode;
    long reqId;
    private static final String TAG = "lfm";
    final String CLASS = "ScheduleViewModel";

    void init(Bundle args) {
        if (initDone && reqId == args.getLong(REQID))
            ;
        else {
            reqId = args.getLong(REQID);
            chanId = args.getInt(CHANID, 0);
            startTime = (Date) args.getSerializable(STARTTIME);
            recordId = args.getInt(RECORDID,0);
            schedReason = args.getInt(SCHEDREASON,0);
            isOverride = args.getBoolean(ISOVERRIDE, false);
            AsyncBackendCall call = new AsyncBackendCall((caller) -> {
                detailsList = caller.getXmlResults();
                setupData();
                initDone = true;
                initDoneLiveData.postValue(INIT_READY);
            });
            int firstCall;
            if (chanId != 0 && startTime != null) {
                // Creating from program schedule
                call.args.put("CHANID", chanId);
                call.args.put("STARTTIME", startTime);
                firstCall = Action.GETPROGRAMDETAILS;
            } else {
                // New Recording - searchType currently only manual.
                firstCall = Action.DUMMY;
            }
            call.execute(
                    firstCall,
                    Action.GETRECORDSCHEDULELIST,
                    Action.GETPLAYGROUPLIST,
                    Action.GETRECGROUPLIST,
                    Action.GETRECSTORAGEGROUPLIST,
                    Action.GETINPUTLIST,
                    Action.GETRECRULEFILTERLIST);
        }

    }

    private void setupData() {
        // There are these cases
        // - New manual recording (schedType != SCHED_GUIDE)
        //     from Recording Rules list
        // - New recording from program guide (recordId == 0)
        // - Update existing recording (recordId found in list of rec rules)
        isDirty = false;
        RecordRule defaultTemplate = null;
        RecordRule progDetails = null;
        // New manual recording
//        if (schedType == ???) {
//            progDetails = new RecordRule();
//        }
        // Recording from program guide or upcoming list create overrode
        if (detailsList.get(0) != null) {
            XmlNode programNode = detailsList.get(0); // ACTION_GETPROGRAMDETAILS
            if (programNode != null) {
                progDetails = new RecordRule().fromProgram(programNode);
                recordId = progDetails.recordId;
                if (recordId == 0)
                    isOverride = false;
                String recordingStatus = programNode.getNode("Recording").getString("StatusName");
                int recType = programNode.getNode("Recording").getInt("RecType", 0);
                if (recordingStatus == null)
                    recordingStatus = programNode.getNode("Recording").getString("Status");
                if (recType == 7 || recType == 8) {
                    // editing an override
                    isOverride = true;
                } else if ("NeverRecord".equals(recordingStatus)) {
                    // Special override
                    neverRecord = true;
                    isOverride = true;
                } else {
                    if (isOverride)
                        newOverride = true;
                }
            }
        }
        XmlNode recRulesNode = detailsList.get(1) // ACTION_GETRECORDSCHEDULELIST
                .getNode("RecRules");
        if (recRulesNode != null) {
            XmlNode recRuleNode = recRulesNode.getNode("RecRule");
            templateNames.clear();
            templateNames.add("");
            while (recRuleNode != null) {
                int id = recRuleNode.getInt("Id", 0);
                if (id == recordId) {
                    recordRule = new RecordRule().fromSchedule(recRuleNode);
                    if (neverRecord) {
                        recordRule.type = "Never Record";
                    } else if (newOverride) {
                        recordRule.parentId = recordId;
                        recordRule.recordId = 0;
                        recordRule.type = "Not Recording";
                        if (!"Manual Search".equals(recordRule.searchType))
                            recordRule.searchType = "None";
                    }
                    if (isOverride) {
                        recordRule.inactive = false;
                        recordRule.recStatusCode = progDetails.recStatusCode;
                        recordRule.recordingStatus = progDetails.recordingStatus;
                        recordRule.startTime = progDetails.startTime;
                        recordRule.chanId = progDetails.chanId;
                        recordRule.channelName = progDetails.channelName;
                        recordRule.station = progDetails.station;
                    }
                }
                String type = recRuleNode.getString("Type");
                if ("Recording Template".equals(type)) {
                    RecordRule template = new RecordRule().fromSchedule(recRuleNode);
                    templateList.add(template);
                    templateNames.add(template.title);
                    if ("Default (Template)".equals(template.title))
                        defaultTemplate = template;
                }
                recRuleNode = recRuleNode.getNextSibling();
            }
        }
        if (recordRule == null) {
            if (recordId != 0)
                toast.postValue(R.string.msg_rec_rule_gone);
            recordRule = new RecordRule().mergeTemplate(defaultTemplate);
            recordRule.findTime = "00:00:00";
            recordRule.findDay = 0;
        }
        if (recordRule.type == null)
            if (schedReason == SCHED_NEWTEMPLATE)
                recordRule.type = "Recording Template";
            else
                recordRule.type = "Not Recording";
        if (recordRule.startTime == null)
            recordRule.startTime = new Date();
        if (recordRule.searchType == null) {
//            switch(schedType) {
//                case SEARCH_MANUAL:
//                    recordRule.searchType = "Manual Search";
//                    break;
//                default:
                    recordRule.searchType = "None";
//                    break;
//            }
        }
        if (progDetails != null) {
            recordRule.mergeProgram(progDetails);
            if ("Manual Search".equals(recordRule.searchType)) {
                // startTime is correct for this showing but endTime
                // is the original end time from the rule. Convert
                // end time to the same date as start time.
                long startTm = recordRule.startTime.getTime();
                long endTm = recordRule.endTime.getTime();
                long startDt = startTm / (24l*60l*60l*1000l);
                endTm = endTm % (24l*60l*60l*1000l);
                endTm = startDt * (24l*60l*60l*1000l) + endTm;
                if (endTm < startTm)
                    endTm += (24l*60l*60l*1000l);
                recordRule.endTime.setTime(endTm);
            }
        }
        // Lists
        mPlayGroupList = XmlNode.getStringList(detailsList.get(2)); // ACTION_GETPLAYGROUPLIST
        mRecGroupList = XmlNode.getStringList(detailsList.get(3)); // ACTION_GETRECGROUPLIST
        mRecStorageGroupList = XmlNode.getStringList(detailsList.get(4)); // ACTION_GETRECSTORAGEGROUPLIST

        mInputList.put(0, MyApplication.getAppContext().getString(R.string.sched_input_any));
        XmlNode inputListNode = detailsList.get(5); // ACTION_GETINPUTLIST
        if (inputListNode != null) {
            XmlNode inputsNode = inputListNode.getNode("Inputs");
            if (inputsNode != null) {
                XmlNode inputNode = inputsNode.getNode("Input");
                while (inputNode != null) {
                    int id = inputNode.getInt("Id",-1);
                    String displayName = inputNode.getString("DisplayName");
                    if (id > 0)
                        mInputList.put(id, displayName);
                    inputNode = inputNode.getNextSibling();
                }
            }
        }
        mRecRuleFilterList = new ArrayList<>();
        XmlNode filterListNode = detailsList.get(6); // ACTION_GETRECRULEFILTERLIST
        if (filterListNode != null) {
            XmlNode filtersNode = filterListNode.getNode("RecRuleFilters");
            if (filtersNode != null) {
                XmlNode filterNode = filtersNode.getNode("RecRuleFilter");
                while (filterNode != null) {
                    int id = filterNode.getInt("Id",-1);
                    String description = filterNode.getString("Description");
                    for (int ix = mRecRuleFilterList.size(); ix <= id; ix++)
                        mRecRuleFilterList.add(null);
                    if (id >= 0)
                        mRecRuleFilterList.set(id, description);
                    filterNode = filterNode.getNextSibling();
                }
            }
        }
        if (schedReason != SCHED_NEWRULE) {
            String recordRuleStr = AsyncBackendCall.getString(recordRule);
            savedHashCode = recordRuleStr.hashCode();
        }
        if (schedReason == SCHED_NEWRULE || "Manual Search".equals(recordRule.searchType) ) {
            loadChannels();
        }
    }

    private void loadChannels() {
        if (names.isEmpty()) {
            // Get list of channels
            Context context = MyApplication.getAppContext();
            VideoDbHelper dbh = VideoDbHelper.getInstance(context);
            SQLiteDatabase db = dbh.getReadableDatabase();
            if (db == null)
                return;
            final String[] columns = {
                    VideoContract.VideoEntry.COLUMN_SUBTITLE,
                    VideoContract.VideoEntry.COLUMN_CHANID,
                    VideoContract.VideoEntry.COLUMN_CALLSIGN
            };
            Cursor cursor = db.query(
                    VideoContract.VideoEntry.VIEW_NAME,   // The table to query
                    columns,             // The array of columns to return (pass null to get all)
                    VideoContract.VideoEntry.COLUMN_RECTYPE
                            + " = " + VideoContract.VideoEntry.RECTYPE_CHANNEL, // The where clause
                    null,          // The values for the WHERE clause
                    null,                   // don't group the rows
                    null,                   // don't filter by row groups
                    "CAST (" + VideoContract.VideoEntry.COLUMN_CHANNUM + " AS REAL), "
                            + VideoContract.VideoEntry.COLUMN_CHANNUM  // The sort order
            );
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0));
                chanids.add(cursor.getInt(1));
                callSigns.add(cursor.getString(2));
            }
            cursor.close();
            VideoDbHelper.releaseDatabase();
        }
    }

    void mergeTemplate(RecordRule template) {
        isDirty = true;
        recordRule.inactive = template.inactive;
        recordRule.recPriority = template.recPriority;
        recordRule.preferredInput = template.preferredInput;
        recordRule.startOffset  =  template.startOffset;
        recordRule.endOffset    =  template.endOffset;
        recordRule.dupMethod    =  template.dupMethod;
        recordRule.dupIn        =  template.dupIn;
        recordRule.autoExtend   =  template.autoExtend;
        recordRule.newEpisOnly  =  template.newEpisOnly;
        recordRule.filter       =  template.filter;
        recordRule.recProfile   =  template.recProfile;
        recordRule.recGroup     =  template.recGroup;
        recordRule.storageGroup =  template.storageGroup;
        recordRule.playGroup    =  template.playGroup;
        recordRule.autoExpire   =  template.autoExpire;
        recordRule.maxEpisodes  =  template.maxEpisodes;
        recordRule.maxNewest    =  template.maxNewest;
        recordRule.autoCommflag   = template.autoCommflag  ;
        recordRule.autoMetaLookup = template.autoMetaLookup;
        recordRule.autoTranscode  = template.autoTranscode ;
        recordRule.autoUserJob1   = template.autoUserJob1  ;
        recordRule.autoUserJob2   = template.autoUserJob2  ;
        recordRule.autoUserJob3   = template.autoUserJob3  ;
        recordRule.autoUserJob4   = template.autoUserJob4  ;
        recordRule.transcoder = template.transcoder;
    }

    void save(boolean close) {
        AsyncBackendCall call = new AsyncBackendCall((caller)->{
            if (caller == null)
                return;
            XmlNode response = caller.getXmlResult();
            String result = null;
            if (response != null)
                result = response.getString();
            if (result == null || "false".equals(result)) {
                toast.postValue(R.string.sched_failed);
            } else {
                String recordRuleStr = AsyncBackendCall.getString(recordRule);
                savedHashCode = recordRuleStr.hashCode();
                toast.postValue(R.string.sched_updated);
                Log.i(TAG, CLASS + " Recording scheduled, Response:" + result);
                if (caller.getTasks()[0] == Action.DELETERECRULE)
                    recordRule.recordId = 0;
                else if (recordRule.recordId == 0) {
                    try {
                        recordRule.recordId = Integer.parseInt(result);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                int resp = INIT_SAVED;
                if (close) {
                    try {
                        // pause to allow change and reschedule before returning
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                    }
                    resp = INIT_CLOSE;
                }
                // enable save or close
                initDoneLiveData.postValue(resp);
            }
        });
        call.mainThread = false;
        call.args.put("RECORDRULE", recordRule);
        if ("Forget History".equals(recordRule.type)) {
            call.execute(Action.FORGETHISTORY);
        }
        else if ("Never Record".equals(recordRule.type)) {
            call.execute(Action.ADDDONTRECORDSCHEDULE);
        }
        else if ("Not Recording". equals(recordRule.type)) {
            if (recordRule.recordId > 0)
                call.execute(Action.DELETERECRULE);
        }
        else {
            String recordRuleStr = AsyncBackendCall.getString(recordRule);
            int hashCode = recordRuleStr.hashCode();
            if (hashCode == savedHashCode)
                toast.postValue(R.string.sched_notupdated);
            else
                call.execute(Action.ADD_OR_UPDATERECRULE);
        }
        
    }

}