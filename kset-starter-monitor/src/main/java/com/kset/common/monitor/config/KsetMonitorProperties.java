package com.kset.common.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "kset.monitor")
public class KsetMonitorProperties {

    private boolean enabled = true;

    private String backend = "log";

    private final Cat cat = new Cat();
    private final SamplerConfig sampler = new SamplerConfig();
    private final Reporter reporter = new Reporter();
    private final Web web = new Web();
    private final Servlet servlet = new Servlet();
    private final Dubbo dubbo = new Dubbo();
    private final Gateway gateway = new Gateway();
    private final Mybatis mybatis = new Mybatis();
    private final HttpClient httpClient = new HttpClient();
    private final Redis redis = new Redis();
    private final ThreadPool threadPool = new ThreadPool();
    private final Async async = new Async();
    private final Scheduled scheduled = new Scheduled();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public Cat getCat() {
        return cat;
    }

    public SamplerConfig getSampler() {
        return sampler;
    }

    public Reporter getReporter() {
        return reporter;
    }

    public Web getWeb() {
        return web;
    }

    public Servlet getServlet() {
        return servlet;
    }

    public Dubbo getDubbo() {
        return dubbo;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public Mybatis getMybatis() {
        return mybatis;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public Redis getRedis() {
        return redis;
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    public Async getAsync() {
        return async;
    }

    public Scheduled getScheduled() {
        return scheduled;
    }

    public static class Web {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Cat {
        private boolean initialize = false;
        private String domain;

        public boolean isInitialize() {
            return initialize;
        }

        public void setInitialize(boolean initialize) {
            this.initialize = initialize;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }
    }

    public static class Servlet {
        private boolean traceEnabled = true;
        private boolean grayTagEnabled = true;

        public boolean isTraceEnabled() {
            return traceEnabled;
        }

        public void setTraceEnabled(boolean traceEnabled) {
            this.traceEnabled = traceEnabled;
        }

        public boolean isGrayTagEnabled() {
            return grayTagEnabled;
        }

        public void setGrayTagEnabled(boolean grayTagEnabled) {
            this.grayTagEnabled = grayTagEnabled;
        }
    }

    public static class Dubbo {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Gateway {
        private boolean enabled = true;
        private boolean traceEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isTraceEnabled() {
            return traceEnabled;
        }

        public void setTraceEnabled(boolean traceEnabled) {
            this.traceEnabled = traceEnabled;
        }
    }

    public static class Mybatis {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class HttpClient {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Redis {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ThreadPool {
        private boolean enabled = true;
        private boolean tracePropagationEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isTracePropagationEnabled() {
            return tracePropagationEnabled;
        }

        public void setTracePropagationEnabled(boolean tracePropagationEnabled) {
            this.tracePropagationEnabled = tracePropagationEnabled;
        }
    }

    public static class Async {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Scheduled {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class SamplerConfig {
        private double rate = 1.0;

        public double getRate() {
            return rate;
        }

        public void setRate(double rate) {
            this.rate = rate;
        }
    }

    public static class Reporter {
        private boolean asyncEnabled = false;
        private int queueCapacity = 2048;

        /**
         * 历史兼容字段；门面层固定同步上报，异步策略由具体 backend 或外部监控框架决定。
         */
        @Deprecated(since = "1.0.0", forRemoval = false)
        public boolean isAsyncEnabled() {
            return asyncEnabled;
        }

        /**
         * 历史兼容字段；门面层固定同步上报，异步策略由具体 backend 或外部监控框架决定。
         */
        @Deprecated(since = "1.0.0", forRemoval = false)
        public void setAsyncEnabled(boolean asyncEnabled) {
            this.asyncEnabled = asyncEnabled;
        }

        /**
         * 历史兼容字段；门面层不再创建异步队列。
         */
        @Deprecated(since = "1.0.0", forRemoval = false)
        public int getQueueCapacity() {
            return queueCapacity;
        }

        /**
         * 历史兼容字段；门面层不再创建异步队列。
         */
        @Deprecated(since = "1.0.0", forRemoval = false)
        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }
}
