package dev.graphnous.api.event;

import dev.graphnous.application.event.ApplicationEvent;
import dev.graphnous.application.event.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringEventPublisher(
            final ApplicationEventPublisher publisher
    ) {
        this.publisher = publisher;
    }

    @Override
    public void publish(final ApplicationEvent event) {
        publisher.publishEvent(event);
    }

}