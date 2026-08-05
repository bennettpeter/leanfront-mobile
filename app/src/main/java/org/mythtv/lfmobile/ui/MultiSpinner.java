// Source - https://stackoverflow.com/a/22941023
// Posted by vault
// Retrieved 2025-12-15, License - CC BY-SA 3.0

package org.mythtv.lfmobile.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import androidx.appcompat.widget.AppCompatSpinner;

import org.mythtv.lfmobile.R;

/**
 * Inspired by: http://stackoverflow.com/a/6022474/1521064
 */
@SuppressWarnings("JavadocLinkAsPlainText")
public class MultiSpinner extends AppCompatSpinner {

    private CharSequence[] entries;
    private boolean[] selected;
    private MultiSpinnerListener listener;

    public MultiSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);

        try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MultiSpinner)) {
            entries = a.getTextArray(R.styleable.MultiSpinner_android_entries);
            if (entries != null) {
                selected = new boolean[entries.length]; // false-filled by default
            }
        }
    }

    private final OnMultiChoiceClickListener mOnMultiChoiceClickListener = new OnMultiChoiceClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
            selected[which] = isChecked;
        }
    };

    private final DialogInterface.OnDismissListener mOnDismissListener = new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {

            updateSelection();

            if (listener != null) {
                listener.onItemsSelected(selected);
            }

            // hide dialog
            dialog.dismiss();
        }
    };

    private void updateSelection() {
        // build new spinner text & delimiter management
        StringBuilder spinnerBuffer = new StringBuilder();
        for (int i = 0; i < entries.length; i++) {
            if (selected[i]) {
                spinnerBuffer.append(entries[i]);
                spinnerBuffer.append(", ");
            }
        }

        // Remove trailing comma
        if (spinnerBuffer.length() > 2) {
            spinnerBuffer.setLength(spinnerBuffer.length() - 2);
        }

        // display new text
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item,
                new String[] { spinnerBuffer.toString() });
        setAdapter(adapter);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean performClick() {
        new AlertDialog.Builder(getContext())
                .setMultiChoiceItems(entries, selected, mOnMultiChoiceClickListener)
                .setOnDismissListener(mOnDismissListener)
                .setPositiveButton(android.R.string.ok, (d, w)->{})
                .show();
        return true;
    }

    @SuppressWarnings("unused")
    public void setMultiSpinnerListener(MultiSpinnerListener listener) {
        this.listener = listener;
    }

    public CharSequence[] getEntries() {
        return entries;
    }

    public void setEntries(CharSequence[] entries) {
        this.entries = entries;
    }

    public boolean[] getSelected() {
        return selected;
    }

    public void setSelected(boolean[] selected) {
        this.selected = selected;
        updateSelection();
    }

    public interface MultiSpinnerListener {
        void onItemsSelected(boolean[] selected);
    }
}
