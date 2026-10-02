package dev.graphnous.security;

import dev.graphnous.application.context.RequestContextProvider;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * The security configuration on its own, with an endpoint that answers
 * the request's context.
 */
@SpringBootApplication
class TestApplication {

    @RestController
    static class ContextController {

        private final RequestContextProvider contextProvider;

        ContextController(final RequestContextProvider contextProvider) {
            this.contextProvider = contextProvider;
        }

        @GetMapping("/api/v1/context")
        Map<String, Object> context() {
            final var context = this.contextProvider.get();
            final var answer = new HashMap<String, Object>();
            answer.put("user", context.user().id());
            answer.put("organization", context.organization().id().id());
            return answer;
        }
    }
}
