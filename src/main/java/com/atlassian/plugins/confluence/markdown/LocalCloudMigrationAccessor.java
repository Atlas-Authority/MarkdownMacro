package com.atlassian.plugins.confluence.markdown;

import com.atlassian.migration.app.tracker.CloudMigrationAccessor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.inject.Named;

@Named("cloudMigrationAccessor")
public class LocalCloudMigrationAccessor implements ApplicationContextAware {

    private CloudMigrationAccessor registrar;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        try {
            this.registrar = (CloudMigrationAccessor) applicationContext.getAutowireCapableBeanFactory().
                    createBean(getClass().getClassLoader().loadClass("com.atlassian.migration.app.tracker.CloudMigrationAccessor"), AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise CloudMigrationAccessor", e);
        }
    }

    public CloudMigrationAccessor getCloudMigrationAccessor() {
        return registrar;
    }
}
