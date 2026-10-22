package com.apixa.common.db;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Ensures that the parent directory for any configured SQLite database file exists
 * before HikariCP or Hibernate attempts to obtain a connection.
 * SQLite JDBC does not auto-create missing parent directories, causing [SQLITE_CANTOPEN].
 */
@Component
public class DatabaseDirectoryInitializer implements BeanFactoryPostProcessor, PriorityOrdered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        try {
            Environment env = beanFactory.getBean(Environment.class);
            String url = env.getProperty("spring.datasource.url");
            if (url != null && url.startsWith("jdbc:sqlite:")) {
                String path = url.substring("jdbc:sqlite:".length());
                int qIdx = path.indexOf('?');
                if (qIdx >= 0) {
                    path = path.substring(0, qIdx);
                }
                File dbFile = new File(path);
                File parent = dbFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
            }
        } catch (Exception ignored) {
            // Fail open: standard DataSource initialization will report any genuine fatal configuration issue
        }
    }
}
