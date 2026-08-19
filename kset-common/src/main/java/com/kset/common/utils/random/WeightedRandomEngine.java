package com.kset.common.utils.random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 加权随机引擎：可选加权、指标观测、日志重放与持久化 SPI。
 */
public class WeightedRandomEngine {

    private volatile WeightedRandomConfig config;
    private volatile Map<String, Double> weights;
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Random seededRandom;
    private final AtomicLong totalDraws = new AtomicLong();
    private final AtomicLong nextSeq = new AtomicLong();
    private volatile int configVersion;
    private final List<DrawEvent> journalBuffer = new ArrayList<>();

    private volatile WeightedRandomObserver observer;
    private volatile WeightedRandomPersistence persistence;
    private volatile WeightedRandomReplayStore replayStore;

    private WeightedRandomEngine(WeightedRandomConfig config,
                                 WeightedRandomObserver observer,
                                 WeightedRandomPersistence persistence,
                                 WeightedRandomReplayStore replayStore,
                                 boolean restoreCounters) {
        this.config = config;
        this.weights = new LinkedHashMap<>(config.resolvedWeights());
        this.configVersion = 1;
        this.observer = observer;
        this.persistence = persistence;
        this.replayStore = replayStore;
        this.seededRandom = config.getSeed() != null ? new Random(config.getSeed()) : null;

        if (persistence != null && config.getName() != null) {
            Optional<WeightedRandomConfig> persisted = persistence.loadConfig(config.getName());
            persisted.ifPresent(stored -> {
                this.config = mergeConfig(config, stored);
                this.weights = new LinkedHashMap<>(this.config.resolvedWeights());
            });
            if (restoreCounters) {
                Map<String, Long> loaded = persistence.loadCounters(config.getName());
                loaded.forEach((key, count) -> counters.computeIfAbsent(key, ignored -> new LongAdder()).add(count));
                long sum = loaded.values().stream().mapToLong(Long::longValue).sum();
                totalDraws.set(sum);
            }
        }
        if (replayStore != null && config.getName() != null) {
            nextSeq.set(replayStore.loadLatestSeq(config.getName()) + 1);
        }
        initCounters();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String draw(String... hiddenKeys) {
        List<String> hidden = hiddenKeys == null || hiddenKeys.length == 0
                ? List.of()
                : Arrays.asList(hiddenKeys);
        return drawInternal(hidden, true);
    }

    public WeightedRandomMetrics getMetrics() {
        WeightedRandomMetrics metrics = buildMetricsSnapshot();
        if (observer != null && config.getName() != null) {
            observer.onMetricsReport(config.getName(), metrics);
        }
        return metrics;
    }

    public void updateWeights(Map<String, Double> newWeights) {
        if (newWeights == null || newWeights.isEmpty()) {
            throw new IllegalArgumentException("newWeights 不能为空");
        }
        synchronized (this) {
            this.weights = new LinkedHashMap<>(newWeights);
            this.configVersion++;
            initCountersForNewKeys();
            if (persistence != null && config.getName() != null) {
                WeightedRandomConfig updated = copyConfigWithWeights(newWeights);
                persistence.saveConfig(config.getName(), updated);
                config = updated;
            }
        }
        if (observer != null && config.getName() != null) {
            observer.onConfigChanged(config.getName(), config);
        }
    }

    public void flush() {
        synchronized (this) {
            if (persistence != null && config.getName() != null) {
                Map<String, Long> snapshot = snapshotCounters();
                persistence.saveCounters(config.getName(), snapshot);
            }
            flushJournalBuffer();
        }
    }

    public String replayFromSeed(int count) {
        requireReplayEnabled();
        requireSeed();
        if (count <= 0) {
            throw new IllegalArgumentException("count 必须大于 0: " + count);
        }
        Random random = new Random(config.getSeed());
        String result = null;
        for (int i = 0; i < count; i++) {
            result = pickOnce(random, List.of());
        }
        return result;
    }

    public List<String> previewSequence(int n) {
        requireReplayEnabled();
        requireSeed();
        if (n <= 0) {
            throw new IllegalArgumentException("n 必须大于 0: " + n);
        }
        Random random = new Random(config.getSeed());
        long skip = totalDraws.get();
        for (long i = 0; i < skip; i++) {
            pickOnce(random, List.of());
        }
        List<String> preview = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            preview.add(pickOnce(random, List.of()));
        }
        return preview;
    }

    public List<String> replayFromJournal(WeightedRandomReplayStore store, long fromSeq, int limit) {
        if (store == null) {
            throw new IllegalArgumentException("store 不能为空");
        }
        String name = config.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("replayFromJournal 需要配置 name");
        }
        return store.loadDrawEvents(name, fromSeq, limit).stream().map(DrawEvent::key).toList();
    }

    public WeightedRandomReplayer openReplaySession(WeightedRandomReplayStore store, long fromSeq) {
        if (store == null) {
            throw new IllegalArgumentException("store 不能为空");
        }
        String name = config.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("openReplaySession 需要配置 name");
        }
        return new WeightedRandomReplayer(name, store.loadDrawEvents(name, fromSeq, 0), observer);
    }

    public WeightedRandomEngine forkForReplay() {
        return builder()
                .config(config)
                .observer(null)
                .persistence(null)
                .replayStore(null)
                .restoreCounters(false)
                .build();
    }

    public String getName() {
        return config.getName();
    }

    public WeightedRandomConfig getConfig() {
        return config;
    }

    public void setObserver(WeightedRandomObserver observer) {
        this.observer = observer;
    }

    public void setPersistence(WeightedRandomPersistence persistence) {
        this.persistence = persistence;
    }

    public void setReplayStore(WeightedRandomReplayStore replayStore) {
        this.replayStore = replayStore;
        if (replayStore != null && config.getName() != null) {
            nextSeq.set(replayStore.loadLatestSeq(config.getName()) + 1);
        }
    }

    private synchronized String drawInternal(List<String> hidden, boolean record) {
        Random random = resolveRandom();
        String key = pickOnce(random, hidden);
        if (record) {
            recordDraw(key, hidden);
        }
        return key;
    }

    private String pickOnce(Random random, List<String> hidden) {
        Map<String, Double> pool = WeightedRandomSupport.effectivePool(weights, hidden);
        return WeightedRandomSupport.pick(
                pool,
                random,
                config.resolvedAlgorithm(),
                config.getSampleBase());
    }

    private void recordDraw(String key, List<String> hidden) {
        long total = totalDraws.incrementAndGet();
        if (config.isMetricsEnabled()) {
            counters.computeIfAbsent(key, ignored -> new LongAdder()).increment();
        }
        DrawEvent event = null;
        if (config.isJournalEnabled()) {
            event = appendJournal(key, hidden);
        }
        if (observer != null && config.getName() != null) {
            observer.onDraw(config.getName(), key, total);
        }
        if (event != null && config.getJournalBufferSize() > 0
                && journalBuffer.size() >= config.getJournalBufferSize()) {
            flushJournalBuffer();
        }
    }

    private DrawEvent appendJournal(String key, List<String> hidden) {
        DrawEvent event = DrawEvent.of(
                config.getName(),
                nextSeq.getAndIncrement(),
                key,
                System.currentTimeMillis(),
                hidden,
                configVersion,
                config.resolvedAlgorithm(),
                config.getSeed());
        journalBuffer.add(event);
        return event;
    }

    private synchronized void flushJournalBuffer() {
        if (replayStore == null || config.getName() == null || journalBuffer.isEmpty()) {
            journalBuffer.clear();
            return;
        }
        replayStore.appendDrawEvents(config.getName(), List.copyOf(journalBuffer));
        journalBuffer.clear();
    }

    private synchronized WeightedRandomMetrics buildMetricsSnapshot() {
        long total = totalDraws.get();
        double weightSum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        List<WeightedRandomMetrics.ItemMetric> itemMetrics = new ArrayList<>();
        double chiSquare = 0d;
        double maxDeviation = 0d;

        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            String key = entry.getKey();
            double weight = entry.getValue();
            long count = counters.containsKey(key) ? counters.get(key).sum() : 0L;
            double expected = total > 0 && weightSum > 0 ? weight / weightSum : 0d;
            double actual = total > 0 ? (double) count / total : 0d;
            double deviation = actual - expected;
            maxDeviation = Math.max(maxDeviation, Math.abs(deviation));
            if (total > 0 && expected > 0) {
                chiSquare += Math.pow(count - expected * total, 2) / (expected * total);
            }
            itemMetrics.add(WeightedRandomMetrics.ItemMetric.builder()
                    .key(key)
                    .count(count)
                    .weight(weight)
                    .expectedRate(expected)
                    .actualRate(actual)
                    .deviation(deviation)
                    .build());
        }

        return WeightedRandomMetrics.builder()
                .name(config.getName())
                .algorithm(config.resolvedAlgorithm())
                .totalDraws(total)
                .seed(config.getSeed())
                .configVersion(configVersion)
                .snapshotTimeMs(System.currentTimeMillis())
                .maxDeviation(maxDeviation)
                .chiSquare(chiSquare)
                .items(itemMetrics)
                .build();
    }

    private Map<String, Long> snapshotCounters() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        counters.forEach((key, adder) -> snapshot.put(key, adder.sum()));
        return snapshot;
    }

    private void initCounters() {
        for (String key : weights.keySet()) {
            counters.computeIfAbsent(key, ignored -> new LongAdder());
        }
    }

    private void initCountersForNewKeys() {
        for (String key : weights.keySet()) {
            counters.computeIfAbsent(key, ignored -> new LongAdder());
        }
    }

    private Random resolveRandom() {
        return seededRandom != null ? seededRandom : ThreadLocalRandom.current();
    }

    private void requireReplayEnabled() {
        if (!config.isReplayEnabled()) {
            throw new IllegalStateException("replayEnabled 未开启");
        }
    }

    private void requireSeed() {
        if (config.getSeed() == null) {
            throw new IllegalStateException("种子重放需要配置 seed");
        }
    }

    private static WeightedRandomConfig mergeConfig(WeightedRandomConfig runtime, WeightedRandomConfig persisted) {
        WeightedRandomConfig merged = WeightedRandomConfig.builder()
                .name(firstNonBlank(runtime.getName(), persisted.getName()))
                .weights(persisted.getWeights() != null && !persisted.getWeights().isEmpty()
                        ? persisted.getWeights() : runtime.getWeights())
                .items(persisted.getItems() != null ? persisted.getItems() : runtime.getItems())
                .algorithm(persisted.getAlgorithm() != null ? persisted.getAlgorithm() : runtime.getAlgorithm())
                .seed(persisted.getSeed() != null ? persisted.getSeed() : runtime.getSeed())
                .sampleBase(persisted.getSampleBase() > 0 ? persisted.getSampleBase() : runtime.getSampleBase())
                .metricsEnabled(runtime.isMetricsEnabled())
                .replayEnabled(runtime.isReplayEnabled())
                .journalEnabled(runtime.isJournalEnabled())
                .journalBufferSize(runtime.getJournalBufferSize() > 0
                        ? runtime.getJournalBufferSize() : persisted.getJournalBufferSize())
                .build();
        return merged;
    }

    private WeightedRandomConfig copyConfigWithWeights(Map<String, Double> newWeights) {
        return WeightedRandomConfig.builder()
                .name(config.getName())
                .weights(new LinkedHashMap<>(newWeights))
                .algorithm(config.getAlgorithm())
                .seed(config.getSeed())
                .sampleBase(config.getSampleBase())
                .metricsEnabled(config.isMetricsEnabled())
                .replayEnabled(config.isReplayEnabled())
                .journalEnabled(config.isJournalEnabled())
                .journalBufferSize(config.getJournalBufferSize())
                .build();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    public static final class Builder {
        private WeightedRandomConfig config;
        private WeightedRandomObserver observer;
        private WeightedRandomPersistence persistence;
        private WeightedRandomReplayStore replayStore;
        private boolean restoreCounters = true;

        public Builder name(String name) {
            ensureConfig();
            config.setName(name);
            return this;
        }

        public Builder weights(Map<String, Double> weights) {
            ensureConfig();
            config.setWeights(weights);
            return this;
        }

        public Builder items(List<String> items) {
            ensureConfig();
            config.setItems(items);
            return this;
        }

        public Builder algorithm(WeightedRandomAlgorithm algorithm) {
            ensureConfig();
            config.setAlgorithm(algorithm);
            return this;
        }

        public Builder seed(Long seed) {
            ensureConfig();
            config.setSeed(seed);
            return this;
        }

        public Builder sampleBase(int sampleBase) {
            ensureConfig();
            config.setSampleBase(sampleBase);
            return this;
        }

        public Builder metricsEnabled(boolean metricsEnabled) {
            ensureConfig();
            config.setMetricsEnabled(metricsEnabled);
            return this;
        }

        public Builder replayEnabled(boolean replayEnabled) {
            ensureConfig();
            config.setReplayEnabled(replayEnabled);
            return this;
        }

        public Builder journalEnabled(boolean journalEnabled) {
            ensureConfig();
            config.setJournalEnabled(journalEnabled);
            return this;
        }

        public Builder journalBufferSize(int journalBufferSize) {
            ensureConfig();
            config.setJournalBufferSize(journalBufferSize);
            return this;
        }

        public Builder config(WeightedRandomConfig config) {
            this.config = config;
            return this;
        }

        public Builder observer(WeightedRandomObserver observer) {
            this.observer = observer;
            return this;
        }

        public Builder persistence(WeightedRandomPersistence persistence) {
            this.persistence = persistence;
            return this;
        }

        public Builder replayStore(WeightedRandomReplayStore replayStore) {
            this.replayStore = replayStore;
            return this;
        }

        public Builder restoreCounters(boolean restoreCounters) {
            this.restoreCounters = restoreCounters;
            return this;
        }

        public WeightedRandomEngine build() {
            if (config == null) {
                config = WeightedRandomConfig.builder().build();
            }
            config.resolvedWeights();
            return new WeightedRandomEngine(config, observer, persistence, replayStore, restoreCounters);
        }

        private void ensureConfig() {
            if (config == null) {
                config = WeightedRandomConfig.builder().build();
            }
        }
    }
}
