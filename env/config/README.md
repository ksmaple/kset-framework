# env/config 配置源

本目录是本地部署环境的**唯一配置编辑入口**。不要直接改同步目标目录下的同名文件，改这里后重新执行 `env/up` 即可。

## 文件说明

| 文件 | 同步目标 |
|------|----------|
| `.env.example` | 首次启动时复制为 `env/.env`（若不存在） |
| `.env`（可选，git 忽略） | 覆盖 `env/.env` |
| `cat/client.xml` | `env/cat/appdatas/client.xml`、`kset-demo/env/cat/client.xml`、`data/appdatas/cat/client.xml` |

## 使用

```powershell
# 首次：复制环境变量模板
Copy-Item env/config/.env.example env/config/.env

# 编辑 config/.env、config/cat/client.xml 后一键启动
.\env\up.ps1
```
