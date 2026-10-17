package dev.graphnous.api.notification;

import dev.graphnous.application.notification.NotificationEvent;
import dev.graphnous.application.notification.NotificationListener;
import dev.graphnous.application.notification.NotificationPublisher;
import dev.graphnous.application.notification.NotificationSubscriber;
import dev.graphnous.domain.system.System;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Delivers what happens to the notifications of a system to the listeners
 * subscribed to it, such as open notification streams. Scans and requests
 * publish from their own threads while requests subscribe and unsubscribe,
 * so the listeners are kept in concurrent collections, and subscribing and
 * unsubscribing are each one atomic update of the system's listeners.
 */
@Component
public class InMemoryNotificationStream implements NotificationPublisher, NotificationSubscriber {

    private static final Logger log = LoggerFactory.getLogger(InMemoryNotificationStream.class);

    private final Map<UUID, List<NotificationListener>> listeners = new ConcurrentHashMap<>();

    @Override
    public void subscribe(final System.SystemId systemId, final NotificationListener listener) {
        this.listeners.compute(systemId.id(), (id, systemListeners) -> {
            final var subscribed = systemListeners == null ? new CopyOnWriteArrayList<NotificationListener>() : systemListeners;
            subscribed.add(listener);

            return subscribed;
        });
    }

    @Override
    public void unsubscribe(final System.SystemId systemId, final NotificationListener listener) {
        this.listeners.computeIfPresent(systemId.id(), (id, systemListeners) -> {
            systemListeners.remove(listener);

            return systemListeners.isEmpty() ? null : systemListeners;
        });
    }

    /**
     * Delivers the event to every listener of its system. A listener that
     * fails, such as a stream the client already closed, does not keep it
     * from the others, nor fail what published it.
     */
    @Override
    public void publish(final NotificationEvent event) {
        final var systemListeners = this.listeners.get(event.systemId().id());

        if (systemListeners == null) {
            return;
        }

        for (final var listener : systemListeners) {
            try {
                listener.onEvent(event);
            } catch (final RuntimeException e) {
                log.debug("Delivering a notification event of system systemId={} failed", event.systemId().id(), e);
            }
        }
    }
}
