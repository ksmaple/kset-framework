# Frontend 域规范

F001: types 与 service 须与 API JSON 字段对齐
F002: 字段语义见 naming spec，格式见 H5 层 camelCase 规则
F003: 禁止 is 前缀布尔属性
F004: 可选字段使用 TS 可选属性
F005: API 客户端统一封装请求与 ApiResult 解析
F006: 目录分层：types、services、pages 或项目约定路径
F007: 不产出 design/frontend 文档；落地以 TS 代码为准
F008: 后端 Full POST 时前端须 POST 且参数放 body
F009: traceId 与 API 响应 traceId 一致用于日志关联
F010: 命名与 API JSON 键名一致，禁止前端专用字段偏离契约
