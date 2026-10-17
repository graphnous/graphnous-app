package dev.graphnous.api.notification;

import dev.graphnous.application.notification.NotificationEvent;
import dev.graphnous.application.notification.NotificationListener;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryNotificationStreamTest {

    private final InMemoryNotificationStream stream = new InMemoryNotificationStream();

    private final System.SystemId shop = System.SystemId.generate();
    private final System.SystemId blog = System.SystemId.generate();

    @Test
    void deliversTheEventsOfASystemToItsListeners() {
        final var received = new ArrayList<NotificationEvent>();
        final var ofBlog = new ArrayList<NotificationEvent>();

        stream.subscribe(shop, received::add);
        stream.subscribe(blog, ofBlog::add);

        stream.publish(NotificationEvent.allRead(shop, null));

        assertThat(received).containsExactly(NotificationEvent.allRead(shop, null));
        assertThat(ofBlog).isEmpty();
    }

    @Test
    void stopsDeliveringToAListenerThatUnsubscribed() {
        final List<NotificationEvent> received = new ArrayList<>();
        final NotificationListener listener = received::add;

        stream.subscribe(shop, listener);
        stream.unsubscribe(shop, listener);
        stream.publish(NotificationEvent.allRead(shop, null));

        assertThat(received).isEmpty();
    }

    @Test
    void keepsDeliveringToTheOthersWhenAListenerFails() {
        final var received = new ArrayList<NotificationEvent>();

        stream.subscribe(shop, event -> {
            throw new IllegalStateException("The client went away");
        });
        stream.subscribe(shop, received::add);

        stream.publish(NotificationEvent.allRead(shop, null));

        assertThat(received).hasSize(1);
    }
}
