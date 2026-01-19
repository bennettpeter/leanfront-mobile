package org.mythtv.lfmobile.ui.recrules;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;


import org.mythtv.lfmobile.data.Action;
import org.mythtv.lfmobile.data.AsyncBackendCall;
import org.mythtv.lfmobile.data.XmlNode;

import java.util.ArrayList;

public class RuleListViewModel extends ViewModel {
    MutableLiveData<ArrayList<RuleItem>> rules = new MutableLiveData<>();
    private final ArrayList<RuleItem> ruleList = new ArrayList<>();

    public void startFetch() {
        AsyncBackendCall call = new AsyncBackendCall((caller) -> {
            ruleList.clear();
            XmlNode node = caller.getXmlResult();
            if (node == null)
                return;
            node = node.getNode(new String [] {"RecRules","RecRule"},0);
            while (node != null) {
                RuleItem item = new RuleItem();
                item.title = node.getString("Title");
                item.nextRecording = node.getString("NextRecording");
                item.lastRecorded = node.getString("LastRecorded");
                item.type = node.getString("Type");
                item.inactive = node.getNode("Inactive").getBoolean();
                item.id = node.getInt("Id",0);
                ruleList.add(item);
                node = node.getNextSibling();
            }
            refreshScreen();
        });
        call.execute(Action.GETRECORDSCHEDULELIST);
    }

    void refreshScreen() {
        synchronized(this) {
            rules.postValue(ruleList);
        }
    }

    static class RuleItem {
        String title;
        String type;
        String nextRecording;
        String lastRecorded;
        boolean inactive;
        int id;
    }
}