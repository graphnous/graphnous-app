package dev.graphnous;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync()
public class GraphnousApplication {

    static void main(String[] args) {
        SpringApplication.run(GraphnousApplication.class, args);
    }
}
