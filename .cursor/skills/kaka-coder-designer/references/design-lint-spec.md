# 设计产出检查（ddd / sql）

> kaka-coder-designer · 新模块设计阶段按需勾选

## 文档（仅 ddd / sql）

- [ ] `design/ddd/NNN-*.md` 存在（新模块）
- [ ] `design/sql/*.sql` 存在（有持久化时）
- [ ] DDD 无 snake_case 业务字段、无 API/H5 名
- [ ] 无 `design/api/`、`design/frontend/` 等（除非历史存量，新模块禁止）

## 代码（API / 前端 / 转换）

- [ ] Controller/DTO 符合 `kaka-coder-designer/references/api-spec.md`
- [ ] TS service/types 与 API JSON、DDD 语义一致
- [ ] DateTime 字段类型与 project-spec「时间格式」及 api A027、frontend F011 一致
- [ ] Converter 双层 MapStruct，字段与 ddd/sql 一致

## 索引

- [ ] `design/INDEX.md` 仅登记 DDD/SQL

## 可选 grep

```powershell
# 新模块不应新增 design/api
Test-Path ".claude\design\api"
```
