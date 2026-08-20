package com.kset.common.utils.random;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 多业务加权随机注册中心。
 */
public class KsetRandomRegistry {

    private static final KsetRandomRegistry INSTANCE = new KsetRandomRegistry();

    private final ConcurrentHashMap<String, WeightedRandomEngine> engines = new ConcurrentHashMap<>();
    private volatile WeightedRandomObserver globalObserver;
    private volatile WeightedRandomPersistence globalPersistence;
    private volatile WeightedRandomReplayStore globalReplayStore;

    private KsetRandomRegistry() {
    }

    public static KsetRandomRegistry getInstance() {
        return INSTANCE;
    }

    public void setGlobalObserver(WeightedRandomObserver observer) {
        this.globalObserver = observer;
        engines.values().forEach(engine -> {
            if (engine.getConfig().getName() != null) {
                engine.setObserver(observer);
            }
        });
    }

    public void setGlobalPersistence(WeightedRandomPersistence persistence) {
        this.globalPersistence = persistence;
        engines.values().forEach(engine -> engine.setPersistence(persistence));
    }

    public void setGlobalReplayStore(WeightedRandomReplayStore replayStore) {
        this.globalReplayStore = replayStore;
        engines.values().forEach(engine -> engine.setReplayStore(replayStore));
    }

    public void register(String name, WeightedRandomConfig config) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (config == null) {
            throw new IllegalArgumentException("config 不能为空");
        }
        config.setName(name);
        WeightedRandomEngine engine = WeightedRandomEngine.builder()
                .config(config)
                .observer(globalObserver)
                .persistence(globalPersistence)
                .replayStore(globalReplayStore)
                .build();
        engines.put(name, engine);
    }

    public String draw(String name, String... hiddenKeys) {
        return getEngine(name).draw(hiddenKeys);
    }

    public WeightedRandomMetrics getMetrics(String name) {
        return getEngine(name).getMetrics();
    }

    public void flush(String name) {
        getEngine(name).flush();
    }

    /** Flushes and removes a random pool, releasing its configuration and counters. */
    public boolean unregister(String name) {
        WeightedRandomEngine engine = engines.remove(name);
        if (engine == null) {
            return false;
        }
        engine.flush();
        return true;
    }

    /** Flushes and removes all registered random pools. */
    public void clear() {
        engines.values().forEach(WeightedRandomEngine::flush);
        engines.clear();
    }

    public void flushAll() {
        engines.values().forEach(WeightedRandomEngine::flush);
    }

    public void reportAll() {
        engines.values().forEach(WeightedRandomEngine::getMetrics);
    }

    public WeightedRandomReplayer replay(String name, long fromSeq) {
        WeightedRandomEngine engine = getEngine(name);
        WeightedRandomReplayStore store = globalReplayStore;
        if (store == null) {
            throw new IllegalStateException("未配置 WeightedRandomReplayStore");
        }
        return engine.openReplaySession(store, fromSeq);
    }

    public WeightedRandomEngine getEngine(String name) {
        WeightedRandomEngine engine = engines.get(name);
        if (engine == null) {
            throw new IllegalArgumentException("未注册的随机池: " + name);
        }
        return engine;
    }

    public boolean contains(String name) {
        return engines.containsKey(name);
    }
}
