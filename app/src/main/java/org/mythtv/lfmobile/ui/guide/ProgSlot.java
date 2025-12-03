package org.mythtv.lfmobile.ui.guide;

import android.annotation.SuppressLint;
import android.content.Context;

import org.mythtv.lfmobile.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@SuppressLint("SimpleDateFormat")
public class ProgSlot extends RowSlot {
    public static final int POS_LEFT = 1;
    public static final int POS_MIDDLE = 2;
    public static final int POS_RIGHT = 3;
    private static DateFormat timeFormatter;
    private static DateFormat dateFormatter;
    private static DateFormat dayFormatter;
    public Date timeSlot;       // Time of this grid position
    public Program program;
    public Program program2;    // In case of 15 minute programs
    // position in grid.
    public int position = 0;

    public ProgSlot(int cellType, int position, Date timeSlot) {
        super(cellType);
        this.position = position;
        this.timeSlot = timeSlot;
    }

    public String getGuideText(Context context) {
        if (timeFormatter == null) {
            timeFormatter = android.text.format.DateFormat.getTimeFormat(context);
            dateFormatter = android.text.format.DateFormat.getLongDateFormat(context);
            dayFormatter = new SimpleDateFormat("EEE ");
        }
        StringBuilder build = new StringBuilder();
        try {
            boolean titleDone = false;
            if (timeSlot != null && cellType == CELL_SEARCHRESULT)
                build.append(dayFormatter.format(timeSlot))
                        .append(dateFormatter.format(timeSlot)).append(' ')
                        .append(timeFormatter.format(timeSlot)).append('\n');
            if (cellType == CELL_PROGRAM || cellType == CELL_SEARCHRESULT) {
                if (program != null) {
                    if (program2 != null)
                        build.append("1. ");
                    titleDone = getTitle(build, program);
                    if (titleDone && program2 == null) {
                        build.append('\n');
                        if (program.season > 0 && program.episode > 0)
                            build.append('S').append(program.season).append('E').append(program.episode).append(' ');
                        if (program.subTitle != null)
                            build.append(program.subTitle);
                    }
                }
                if (!titleDone && program != null) {
                    build.append(program.title).append(' ').append(context.getString(R.string.note_program_continuation));
                }
                if (program2 != null) {
                    build.append('\n').append("2. ");
                    getTitle(build, program2);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return build.toString();
    }

    private boolean getTitle(StringBuilder build, Program program) {
        boolean titleDone = false;
        if (program != null) {
            long timeSetStart = 0;
            if (timeSlot != null) {
                timeSetStart = (program.startTime.getTime() - timeSlot.getTime());
                if (timeSetStart < 0 && (position == POS_LEFT)
                        || timeSetStart > 0) {
                    build.append("(").append(timeFormatter.format(program.startTime)).append(") ");
                    build.append(program.title);
                    titleDone = true;
                }
            }
            if (!titleDone && timeSetStart == 0) {
                build.append(program.title);
                titleDone = true;
            }
        }
        return titleDone;
    }

}
