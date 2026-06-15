package com.rubenazo.buscaConciertos.adapters.out.sqlite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.CompositeDatabasePopulator;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class SqliteConfig {

    @Bean
    DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        var initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);

        var schemaSql = new ResourceDatabasePopulator();
        schemaSql.addScript(new ClassPathResource("schema.sql"));

        DatabasePopulator migrationPopulator = new DatabasePopulator() {
            @Override
            public void populate(Connection connection) throws SQLException {
                SqliteSchemaMigrations.ensureSeverityColumn(connection);
                SqliteSchemaMigrations.ensureScoreColumn(connection);
            }
        };

        var composite = new CompositeDatabasePopulator();
        composite.addPopulators(migrationPopulator, schemaSql);

        initializer.setDatabasePopulator(composite);
        return initializer;
    }
}
