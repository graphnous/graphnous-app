package dev.graphnous.application.notification;

/**
 * Tells those listening what happens to notifications, such as an open
 * stream of them.
 */
@FunctionalInterface
public interface NotificationPublisher {

    /**
     * Tells no one.
     */
    NotificationPublisher NONE = event -> {
    };

    /**
     * Never fails: a listener that does is left out.
     */
    void publish(NotificationEvent event);
}
