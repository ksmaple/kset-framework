---
type: standard
name: kset-framework-git-commit
description: "按 kset-framework 的项目规则和实际分支约定处理 Git 工作树、提交、推送与合并；按任务范围命中时读取并适用本规范。"
---

# kset-framework Git 协作

> project-init 生成。

> 只适用于当前项目；[项目规则](../../rules/kset-framework-project-rules.md) 中的分支、worktree 与授权条款优先。

## 触发条件

**启用**：当前任务命中规范 `kset-framework-git-commit` 并要求处理 Git 工作树、提交、推送或合并。
**不启用**：仅阅读代码或只查看 Git 状态。

## 核心规则

R000: 本规范仅适用于（proj=kset-framework）所属项目仓库及其工作树；不得引用平台 `kaka-*` 规则或规范，不扩大 Git 操作授权。
R001: 操作前核对仓库、当前分支、worktree、未提交及暂存路径，按项目规则确认正式线上、集成和任务分支角色。
R002: 仅暂存本次任务路径，保留无关改动及其暂存状态；冲突、双改或归属不明时先停止相关操作。
R003: commit、push、merge 分别遵守项目规则和用户当前授权；提交前核对实际暂存内容与提交说明一致。
R004: 不使用强制推送或跳过校验的参数；合并后核对目标分支和工作树状态。

## 工作流

Step 1: 读取项目规则与本次 Git 目标，检查分支和现有改动。
Step 2: 按授权完成暂存、提交、推送或合并中适用的步骤。
Step 3: 核对分支、提交、远端及遗留改动，报告结果。

## 参考文件

- [项目规则](../../rules/kset-framework-project-rules.md)：项目 Git 执行边界。

## Collaboration

代码与文档修改由对应项目规范负责；本规范只处理 Git 工作流。
