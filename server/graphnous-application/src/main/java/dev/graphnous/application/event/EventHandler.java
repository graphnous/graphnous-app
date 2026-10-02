package dev.graphnous.application.event;

public interface EventHandler<T extends ApplicationEvent> {

    void handle(T event);

}
