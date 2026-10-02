package dev.graphnous.application.event;

public interface EventPublisher {

    void publish(ApplicationEvent event);

}