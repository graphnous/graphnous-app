package dev.graphnous.application.notification;

import dev.graphnous.domain.system.System;

/**
 * Listens to what happens to the notifications of a system.
 */
public interface NotificationSubscriber {

    void subscribe(System.SystemId systemId, NotificationListener listener);

    void unsubscribe(System.SystemId systemId, NotificationListener listener);
}
