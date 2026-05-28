package com.kset.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全链路监控配置（引入 starter-monitor 后默认开启）。
 */
@ConfigurationProperties(prefix = "kset.monitor")
public class KsetMonitorProperties {

    private boolean enabled = true;

    private String backend = "log";

    private final SamplerConfig sampler = new SamplerConfig();
    private final Reporter reporter = new Reporter();
    private final Servlet servlet = new Servlet();
    private final Dubbo dubbo = new Dubbo();
    private final Gateway gateway = new Gateway();
    private final ThreadPool threadPool = new ThreadPool();
    private final Async async = new Async();
    private final SlowLog slowLog = new SlowLog();

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

    public SamplerConfig getSampler() {
        return sampler;
    }

    public Reporter getReporter() {
        return reporter;
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

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    public Async getAsync() {
        return async;
    }

    public SlowLog getSlowLog() {
        return slowLog;
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
        private boolean traceEnabled = true;

        public boolean isTraceEnabled() {
            return traceEnabled;
        }

        public void setTraceEnabled(boolean traceEnabled) {
            this.traceEnabled = traceEnabled;
        }
    }

    public static class ThreadPool {
        private boolean tracePropagationEnabled = true;

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

    public static class SlowLog {
        private boolean httpEnabled = false;
        private long httpThresholdMs = 500;
        private long transactionWarnMs = 500;

        public boolean isHttpEnabled() {
            return httpEnabled;
        }

        public void setHttpEnabled(boolean httpEnabled) {
            this.httpEnabled = httpEnabled;
        }

        public long getHttpThresholdMs() {
            return httpThresholdMs;
        }

        public void setHttpThresholdMs(long httpThresholdMs) {
            this.httpThresholdMs = httpThresholdMs;
        }

        public long getTransactionWarnMs() {
            return transactionWarnMs;
        }

        public void setTransactionWarnMs(long transactionWarnMs) {
            this.transactionWarnMs = transactionWarnMs;
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
        private boolean asyncEnabled = true;
        private int queueCapacity = 2048;

        public boolean isAsyncEnabled() {
            return asyncEnabled;
        }

        public void setAsyncEnabled(boolean asyncEnabled) {
            this.asyncEnabled = asyncEnabled;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }
}
