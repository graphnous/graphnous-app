package dev.graphnous.api.configuration;

import dev.graphnous.api.exception.NoOpErrorReporter;
import dev.graphnous.application.exception.ErrorReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExceptionConfiguration {

    @Bean
    public ErrorReporter noOpErrorReporter() {
        return new NoOpErrorReporter();
    }

}
