package dev.graphnous.api.configuration;

import jakarta.persistence.EntityManagerFactory;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;

@Configuration
@EnableNeo4jRepositories(basePackages = "dev.graphnous", transactionManagerRef = "neo4jTransactionManager")
@EnableJpaRepositories(basePackages = "dev.graphnous", transactionManagerRef = "jpaTransactionManager")
public class PersistenceConfiguration {

    /**
     * The graph's, and what {@code @Transactional} means when it does not say: nearly everything here reads and writes
     * the graph, and the one service that does not says so.
     */
    @Bean
    @Primary
    Neo4jTransactionManager neo4jTransactionManager(
            final Driver driver,
            final DatabaseSelectionProvider databaseSelectionProvider
    ) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }

    /**
     * The embedded database's.
     */
    @Bean
    JpaTransactionManager jpaTransactionManager(final EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}

