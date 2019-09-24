package com.chriniko.schedulerlock.sample;

import com.chriniko.schedulerlock.sample.core.ScheduleLock;
import com.chriniko.schedulerlock.sample.core.ScheduleLockInterceptor;
import com.chriniko.schedulerlock.sample.scheduler.SampleScheduler;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.matcher.Matchers;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

@Slf4j
public class Bootstrap {

    public static void main(String[] args) throws Exception {

        log.info("bootstrapping...");

        Injector injector = Guice.createInjector(new BasicModule());

        // ...

        TimeUnit.MINUTES.sleep(5);
    }


    public static class BasicModule implements Module {

        @Override
        public void configure(Binder binder) {

            // --- scheduler config ---
            final ScheduleLockInterceptor scheduleLockInterceptor = new ScheduleLockInterceptor();
            binder.bind(ScheduleLockInterceptor.class).toInstance(scheduleLockInterceptor);

            binder.bindInterceptor(
                    Matchers.any(),
                    Matchers.annotatedWith(ScheduleLock.class),
                    scheduleLockInterceptor
            );

            binder.bind(SampleScheduler.class).asEagerSingleton();


            // --- db config ---
            HikariDataSource hikariDataSource = new HikariDataSource();

            hikariDataSource.setPoolName("hikari-pool");
            hikariDataSource.setConnectionTestQuery("SELECT 1");
            hikariDataSource.setMinimumIdle(4);
            hikariDataSource.setMaximumPoolSize(6);
            hikariDataSource.setIdleTimeout(10_000);

            hikariDataSource.setJdbcUrl("jdbc:mysql://localhost:3306/test");
            hikariDataSource.setUsername("root");
            hikariDataSource.setPassword("rootpw");

            DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager();
            dataSourceTransactionManager.setDataSource(hikariDataSource);

            JdbcTemplate jdbcTemplate = new JdbcTemplate(hikariDataSource);
            binder.bind(JdbcTemplate.class).toInstance(jdbcTemplate);

            Resource resource = new ClassPathResource("mysql-dump/create_schedulers_lock_table.sql");
            ResourceDatabasePopulator databasePopulator = new ResourceDatabasePopulator(resource);
            databasePopulator.execute(hikariDataSource);

            binder.bind(TransactionTemplate.class).toInstance(new TransactionTemplate(dataSourceTransactionManager));
        }
    }
}
