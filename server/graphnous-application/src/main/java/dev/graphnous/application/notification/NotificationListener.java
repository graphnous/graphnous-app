package dev.graphnous.application.notification;

@FunctionalInterface
public interface NotificationListener {

    void onEvent(NotificationEvent event);
}
