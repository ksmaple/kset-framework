package com.kset.common.monitor.autoconfigure;

import com.kset.common.utils.http.KsetHttp;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class KsetMonitorHttpClientAutoConfiguration {

    public KsetMonitorHttpClientAutoConfiguration(Environment environment) {
        KsetHttp.setMonitorEnabled(
                environment.getProperty("kset.monitor.enabled", Boolean.class, true)
                        && environment.getProperty("kset.monitor.http-client.enabled", Boolean.class, true));
    }
}
