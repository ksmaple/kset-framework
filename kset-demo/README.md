# kset-demo

Demo modules are examples and integration test carriers for KSet starters. They are isolated from the default root reactor, so framework packaging and publishing do not build or deploy demo artifacts unless a demo profile is enabled explicitly.

## Reactor Profiles

| Profile | Modules | Usage |
|--------|---------|-------|
| default | framework modules only | release/package/deploy path |
| `with-demo` | all demo modules | full demo build or regression |
| `demo-standalone` | `demo-standalone-service` | standalone service only |
| `demo-micro` | `demo-micro-service` | micro-service demo only |
| `demo-gateway` | `demo-gateway` | gateway demo only |
| `demo-smoke` | test filter only | run `*ApplicationTest` smoke tests |

## Common Commands

Framework release path:

```bash
mvn test
mvn package
mvn deploy
```

Fast demo smoke:

```bash
mvn test -Pwith-demo,demo-smoke
```

Full demo regression:

```bash
mvn test -Pwith-demo
```

Run one demo service:

```bash
mvn spring-boot:run -Pdemo-standalone -pl kset-demo/demo-standalone-service -am
mvn spring-boot:run -Pdemo-micro -pl kset-demo/demo-micro-service -am
mvn spring-boot:run -Pdemo-gateway -pl kset-demo/demo-gateway -am
```

Package one demo service without tests:

```bash
mvn package -Pdemo-micro -pl kset-demo/demo-micro-service -am -DskipTests
```

## Modules

| Module | Port | Purpose |
|--------|------|---------|
| `demo-standalone-service` | 18081 | Web + datasource + Redis + monitor without cloud dependencies |
| `demo-micro-service` | 18082 | Web service with Nacos, Dubbo, Sentinel, Redis, MQ, cache and framework integration tests |
| `demo-gateway` | 8080 | Gateway + monitor example |

## Environment

Service runtime configuration lives in each module's `src/main/resources/application.yaml`.

The [env](env) directory contains copyable component snippets only. It is not loaded automatically at runtime. Use it when switching middleware addresses, changing datasource type, or adding optional components.

