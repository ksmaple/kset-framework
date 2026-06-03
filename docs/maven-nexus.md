# 发布到远端 Nexus

框架库通过 Maven `deploy` 发布到 Nexus 3（Release / Snapshot 分仓）。

## 1. Nexus 仓库

在 Nexus 中准备两个 **maven2 (hosted)** 仓库（名称可自定义，与下面属性对齐）：

| 仓库 | 用途 | 默认名称 |
|------|------|----------|
| Release | 正式版 `x.y.z` | `maven-releases` |
| Snapshot | 快照版 `x.y.z-SNAPSHOT` | `maven-snapshots` |

记下 Nexus 基址；本仓库默认：`http://192.168.53.5:8081`（见 `kset-parent/pom.xml` 中 `kset.nexus.url`）。

## 2. 本机 Maven 凭证

将 [settings-nexus.example.xml](maven/settings-nexus.example.xml) 中的 `<servers>` 合并进 `~/.m2/settings.xml`（**勿提交密码到 Git**）。

`server.id` 必须为：

- `kset-nexus-releases`
- `kset-nexus-snapshots`

## 3. 发布命令

在仓库根目录执行：

```bash
mvn clean deploy -Pnexus
```

默认发布到 `http://192.168.53.5:8081`。临时改用其他 Nexus：

```bash
mvn clean deploy -Pnexus -Dkset.nexus.url=http://other-nexus:8081
```

## 4. 配置位置

| 项 | 位置 |
|----|------|
| `distributionManagement` | `kset-parent/pom.xml` → profile `nexus` |
| 仓库 URL 属性 | `kset.nexus.url`（默认 `http://192.168.53.5:8081`） |
| 凭证 | `~/.m2/settings.xml` → `<servers>` |

## 5. 业务项目引用

发布成功后，业务工程继承 BOM：

```xml
<parent>
    <groupId>com.kset</groupId>
    <artifactId>kset-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
```

并在 `settings.xml` 或项目 `pom.xml` 的 `<repositories>` 中配置同一 Nexus（或 group 仓库）以下载依赖。

## 6. 常见问题

| 现象 | 处理 |
|------|------|
| `401 Unauthorized` | 检查 `settings.xml` 中 server id 与用户名密码 |
| `Return code 400: Repository does not allow updating assets` | SNAPSHOT 发到 release 仓，或版本号未带 `-SNAPSHOT` |
| `distributionManagement missing` | 未加 `-Pnexus` profile |
| `Connection refused` | 检查 Nexus 是否可达、`192.168.53.5:8081` 与仓库名是否正确 |
