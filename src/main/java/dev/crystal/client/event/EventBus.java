package dev.crystal.client.event;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class EventBus {
    private final Map<Class<?>, List<Entry>> map = new ConcurrentHashMap<>();

    public void subscribe(Object obj) {
        for (Method m : obj.getClass().getMethods()) {
            if (!m.isAnnotationPresent(EventHandler.class) || m.getParameterCount() != 1) continue;
            map.computeIfAbsent(m.getParameterTypes()[0], k -> new ArrayList<>()).add(new Entry(obj, m));
        }
    }

    public void unsubscribe(Object obj) {
        map.values().forEach(l -> l.removeIf(e -> e.owner == obj));
    }

    public <T extends Event> T post(T event) {
        List<Entry> list = map.get(event.getClass());
        if (list == null) return event;
        for (Entry e : list) {
            try { e.method.invoke(e.owner, event); }
            catch (Exception ex) { ex.printStackTrace(); }
            if (event instanceof CancellableEvent ce && ce.isCancelled()) break;
        }
        return event;
    }

    private record Entry(Object owner, Method method) {}
}
