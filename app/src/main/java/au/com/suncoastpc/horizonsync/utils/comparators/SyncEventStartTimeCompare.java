package au.com.suncoastpc.horizonsync.utils.comparators;

import java.util.Comparator;

import au.com.suncoastpc.horizonsync.model.SyncEvent;

/**
 * Created by aroth on 11/25/2017.
 */

public class SyncEventStartTimeCompare implements Comparator<SyncEvent> {
    @Override
    public int compare(SyncEvent left, SyncEvent right) {
        return left.getEventTime().compareTo(right.getEventTime());
    }
}
