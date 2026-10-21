# Git 工作流

本项目直接在主工作树的 `main` 分支修改、提交并推送到 `origin/main`。日常研发和修复不创建任务分支或独立 worktree。除非用户在当前任务中另有明确要求，完成变更和必要的静态自查后直接提交、推送。

1. 开始前确认当前目录、分支、工作区状态和 `origin/main`。已有未提交改动保留原样，先辨认本次任务涉及的文件。
2. 仅暂存本次任务的文件或代码块。不得使用 `git add -A`、`git commit -a` 将其他任务的改动带入提交；同一文件混有无关改动时须先隔离暂存范围。
3. 提交前检查暂存内容与格式，提交信息默认用中文。推送前核对远端是否前进；遇到分叉或冲突，先处理并保护本地未提交改动，不强制推送或重写远端历史。
4. 推送到 `origin/main` 后核对提交和工作区状态。未明确要求时，不清理已有分支、worktree 或其他未提交改动。

常用命令：

```bash
git status --short --branch
git add -- <本次文件>
git diff --cached --check
git diff --cached --stat
git commit -m "中文提交说明"
git push origin main
```
