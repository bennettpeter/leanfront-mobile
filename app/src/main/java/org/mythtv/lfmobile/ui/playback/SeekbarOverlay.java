package org.mythtv.lfmobile.ui.playback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import org.mythtv.lfmobile.data.CommBreakTable;

public class SeekbarOverlay extends View {
    CommBreakTable commBreakTable;
    Paint adPaint;
    PlaybackViewModel model;

    public SeekbarOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        adPaint = new Paint();
        adPaint.setARGB(255,235,64,52);
    }

    public void setup(CommBreakTable commBreakTable, PlaybackViewModel model) {
        this.commBreakTable = commBreakTable;
        this.model = model;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (commBreakTable != null && commBreakTable.entries.length > 0) {
            int width = getWidth();
            int height = getHeight();
            int left = 7;
            float density = getContext().getResources().getDisplayMetrics().density;
            left = (int) ( (float)left * density );
            width = width - left * 2;
            long duration = model.getDuration();
            if (duration <= 0)
                return;
            long breakStart = -1;
            long breakEnd = -1;
            for (int ix = 0 ; ix < commBreakTable.entries.length; ix++) {
                CommBreakTable.Entry entry = commBreakTable.entries[ix];
                switch (entry.mark) {
                    case CommBreakTable.MARK_COMM_START:
                    case CommBreakTable.MARK_CUT_START:
                        breakStart = commBreakTable.getOffsetMs(entry);
                        break;
                    case CommBreakTable.MARK_COMM_END:
                    case CommBreakTable.MARK_CUT_END:
                        breakEnd = commBreakTable.getOffsetMs(entry);
                        break;
                }
                if (breakStart >= 0f && breakEnd >= 0f) {
                    canvas.drawRect( (float)(breakStart * width / duration + left),  -0f,
                            (float)(breakEnd * width / duration + left),  height, adPaint);
                    breakStart = -1;
                    breakEnd = -1;
                }
            }
        }
        super.onDraw(canvas);
    }
}
