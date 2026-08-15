---
name: kset-framework-fixer
description: "kset-framework 修复技能，proj=kset-framework。触发：build 失败、测试失败、Lint、缺陷修复。仅改本仓。"
---



# kset-framework-fixer



> 项目约定：[project-spec.md](../kset-framework-coder/references/project-spec.md)。init 仅生成本项目的 coder/fixer，不迁移平台公共技能。



## 触发条件



**启用**：编译失败、测试失败、Lint 告警、运行时缺陷、用户明确要求修错；保持 `kset-framework-coder` 已约定的风格。



**不启用**：新功能开发或项目内设计 → `kset-framework-coder`。



## 核心规则



R001: 先读 project-spec 并核对本仓失败上下文，保持与 `kset-framework-coder` 风格一致

R002: 最小变更，禁止借机大面积重构

R003: 修复默认验收标准为文件逻辑完成与静态自查

R004: 输出含根因、策略、影响范围；不确定时列出待确认项

R005: 命名/API 类问题以 project-spec 与本仓现有契约为准

R006: 验证方式仅按用户当次要求与项目已有能力决定
R007: 文件逻辑交付不得被默认编译或测试阻塞；验证方式仅按用户当次要求与项目现有能力决定
R008: 缺陷涉及外部文档、外部项目或跨仓文件时须核对 project-spec 中的轻量引用记录，禁止把未确认的外部位置当作修复依据
R009: 定位缺陷只读取 project-spec、任务相关源码、构建配置、运行配置、项目文档及用户或 CI 直接提供的错误文本；不得为复现或补全上下文读取构建产物文件
R010: 禁止打开、解析、反编译或提取任何编译与构建产物本体；默认排除 `target/`、`build/`、`dist/`、`out/`、`bin/`、`.gradle/`、`.next/`、`.nuxt/`、`.cache/`、`.turbo/`、`coverage/`、`node_modules/` 及其中内容，以及 `*.class`、`*.jar`、`*.war`、`*.ear`、`*.dll`、`*.exe`、`*.pdb`、`*.nupkg`、`*.o`、`*.obj`、`*.so`、`*.dylib`、`*.a`、`*.lib`、`*.pyc`、`*.pyo`；构建目录内的文本报告、生成源码和清单同样不得读取



## 工作流



```

Step 0: 确认任务为修错（新功能 → kset-framework-coder）

Step 1: 读 project-spec 的项目约定、关键目录及相关外部引用；仅使用用户或 CI 直接提供的失败信息，并在 R009–R010 允许范围内核对源码与配置

Step 2: 定位根因，拟定最小补丁

Step 3: 完成文件逻辑自查；验证方式仅按用户当次要求与项目现有能力决定，不在技能中预定义脚本命令

Step 4: 输出修复摘要

```



## Collaboration



| 场景 | 技能 |

|------|------|

| 新功能/扩展 | `kset-framework-coder` |

| 项目约定 | `kset-framework-coder/references/project-spec.md` |
