# 发布到远端 Nexus

框架库通过 Maven 发布到 Nexus 3。正式版本发布到 `maven-releases`，快照版本发布到 `maven-snapshots`。

## 1. 发布前检查

在仓库根目录执行：

```bash
git status --short
mvn -q -DskipTests compile
```

正式发布前建议保持 Git 工作区干净。`release:prepare` 会修改版本、提交 release commit、打 tag，工作区不干净时容易失败。

## 2. Maven 凭据

`deploy` 使用 `distributionManagement.repository.id` 到 Maven `settings.xml` 查找凭据。仓库 POM 里的 `kset.nexus.username` / `kset.nexus.password` 只是属性，不会自动成为 deploy 凭据。

将 [settings-nexus.example.xml](maven/settings-nexus.example.xml) 中的 `<servers>` 合并到本机 `~/.m2/settings.xml`，不要把真实密码提交到 Git。

必须包含这两个 server id：

```xml
<servers>
    <server>
        <id>kset-nexus-releases</id>
        <username>...</username>
        <password>...</password>
    </server>
    <server>
        <id>kset-nexus-snapshots</id>
        <username>...</username>
        <password>...</password>
    </server>
</servers>
```

临时发布时也可以使用单独 settings 文件：

```bash
mvn -s /path/to/settings-nexus.xml -DskipTests deploy -Pnexus
```

## 3. 标准 Release 流程

适合正式版本，例如 `0.0.3`。

```bash
mvn release:clean
mvn release:prepare \
  -DreleaseVersion=0.0.3 \
  -DdevelopmentVersion=0.0.4-SNAPSHOT \
  -Dtag=v0.0.3 \
  -Darguments="-DskipTests"
mvn release:perform \
  -Pnexus \
  -Darguments="-DskipTests -Pnexus"
```

Windows PowerShell 写法：

```powershell
mvn release:clean
mvn release:prepare `
  "-DreleaseVersion=0.0.3" `
  "-DdevelopmentVersion=0.0.4-SNAPSHOT" `
  "-Dtag=v0.0.3" `
  "-Darguments=-DskipTests"
mvn release:perform `
  -Pnexus `
  "-Darguments=-DskipTests -Pnexus"
```

`release:prepare` 会做这些事：

- 将所有模块从 SNAPSHOT 切到正式版本
- 执行校验
- 提交 release commit
- 创建 Git tag
- 将版本推进到下一个 SNAPSHOT

`release:perform` 会检出 tag 并执行 `deploy`，把正式制品发布到 Nexus。

## 4. 直接发布当前版本

适合已经手工确认版本号，并且只想执行 Maven deploy 的场景。

先确认版本：

```bash
mvn -q help:evaluate -Dexpression=project.version -DforceStdout
```

发布：

```bash
mvn -q -DskipTests deploy -Pnexus
```

临时指定 Nexus 地址：

```bash
mvn -q -DskipTests deploy -Pnexus -Dkset.nexus.url=http://other-nexus:8081
```

如果当前是正式版本 `x.y.z`，会发到 `maven-releases`；如果是 `x.y.z-SNAPSHOT`，会发到 `maven-snapshots`。

## 5. 手工切换版本

一般优先使用 `release:prepare`。如需手工切版本，可执行：

```bash
mvn -q versions:set -DnewVersion=0.0.3 -DgenerateBackupPoms=false
```

本仓库根 POM 是聚合器，`kset-parent` 不是它的 parent；如果发现子模块仍残留旧版本，需要同步更新所有 `pom.xml` 中的 parent/version。

检查残留：

```bash
rg "0\.0\.3-SNAPSHOT" -g pom.xml
```

## 6. 配置位置

| 项 | 位置 |
|----|------|
| Nexus profile | 根 `pom.xml` 与 `kset-parent/pom.xml` 的 profile `nexus` |
| Release 仓库 id | `kset-nexus-releases` |
| Snapshot 仓库 id | `kset-nexus-snapshots` |
| 默认 Nexus 地址 | `http://192.168.53.5:8081` |
| 凭据 | `~/.m2/settings.xml` 的 `<servers>` |

根聚合 POM 配置了 `maven.deploy.skip=true`，根 POM 自身不发布；实际发布的是 `kset-parent`、`kset-common`、`kset-cloud` 和各 starter 模块。

## 7. 发布后验证

```bash
mvn -q help:evaluate -Dexpression=project.version -DforceStdout
git status --short
```

也可以在 Nexus 页面检查：

- `com.kset:kset-parent`
- `com.kset:kset-common`
- `com.kset:kset-cloud`
- `com.kset:kset-starter-*`

业务项目继承发布后的 parent：

```xml
<parent>
    <groupId>com.kset</groupId>
    <artifactId>kset-parent</artifactId>
    <version>0.0.3</version>
</parent>
```

## 8. 常见问题

| 现象 | 处理 |
|------|------|
| `401 Unauthorized` | 检查 `settings.xml` 中是否存在 `kset-nexus-releases` / `kset-nexus-snapshots`，以及账号是否有 deploy 权限 |
| `Repository does not allow updating assets` | release 仓库通常不允许覆盖同版本；需要换新版本号 |
| `distributionManagement missing` | 发布命令缺少 `-Pnexus` |
| `Connection refused` | 检查 Nexus 地址、网络和仓库名 |
| `Unknown lifecycle phase ".version"` | PowerShell 下带点号的 Maven 参数要加引号，例如 `"-Dexpression=project.version"` |
