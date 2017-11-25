package au.com.suncoastpc.horizonsync.event;

public interface EventHandler {
	public void handleEvent(final EventType eventType, final Object eventData);
}
