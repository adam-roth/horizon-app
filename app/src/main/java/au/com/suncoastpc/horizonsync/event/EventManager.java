package au.com.suncoastpc.horizonsync.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import au.com.suncoastpc.horizonsync.utils.Environment;

public class EventManager {
	private static final Map<EventType, List<EventHandler>> HANDLER_MAP = Collections.synchronizedMap(new HashMap<EventType, List<EventHandler>>());
	
	static {
		//initialize the map with empty lists for each possible event type
		for (EventType type : EventType.values()) {
			HANDLER_MAP.put(type, Collections.synchronizedList(new ArrayList<EventHandler>()));
		}
	}
	
	public static void registerAll(EventHandler handler) {
		register(EnumSet.allOf(EventType.class), handler);
	}
	
	public static void register(Collection<EventType> types, EventHandler handler) {
		for (EventType type : types) {
			register(type, handler);
		}
	}
	
	public static void register(EventType type, EventHandler handler) {
		List<EventHandler> handlers = HANDLER_MAP.get(type);
		synchronized(handlers) {
			if (! handlers.contains(handler)) {
				handlers.add(handler);
			}
		}
	}
	
	public static boolean deregister(EventType type, EventHandler handler) {
		List<EventHandler> handlers = HANDLER_MAP.get(type);
		synchronized(handlers) {
			return handlers.remove(handler);
		}
	}
	
	public static boolean deregister(Collection<EventType> types, EventHandler handler) {
		boolean success = true;
		for (EventType type : types) {
			success |= deregister(type, handler);
		}
		
		return success;
	}
	
	public static boolean deregisterAll(EventHandler handler) {
		return deregister(EnumSet.allOf(EventType.class), handler);
	}
	
	public static void post(EventType type) {
		post(type, null);
	}
	
	public static void postAsync(EventType type) {
		postAsync(type, null);
	}
	
	public static boolean postMain(EventType type) {
		return postMain(type, null);
	}
	
	public static void post(EventType type, Object data) {
		//notify handlers directly, on the current thread
		List<EventHandler> handlers = HANDLER_MAP.get(type);
		synchronized(handlers) {
			for (EventHandler handler : handlers) {
				handler.handleEvent(type, data);
			}
		}
		
		//XXX:  won't work, as some classes register for events as part of static initializers; instead objects that can be destroyed upon logout (or other events) need to deregister themselves upon destruction [this primarily applies to 'Activity' instances]
		//special-case; if we're dealing with a logout event then clear all event handlers
		//if (type == EventType.LOGOUT) {
		//	for (EventType eventType : EventType.values()) {
		//		HANDLER_MAP.get(eventType).clear();
		//	}
		//}
	}
	
	public static void postAsync(final EventType type, final Object data) {
		//notify handlers on a new background thread
		new Thread() {
			@Override
			public void run() {
				post(type, data);
			}
		}.start();
	}
	
	public static boolean postMain(final EventType type, final Object data) {
		//notify handlers on the main/UI thread
		if (Environment.isMainThread()) {
			post(type, data);
			return true;
		}
		
		Runnable call = new Runnable() {
			@Override
			public void run() {
				post(type, data);
			}
		};
		return Environment.runOnMainThread(call);
	}
}
