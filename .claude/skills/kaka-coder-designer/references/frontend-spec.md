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
F011: DateTime 的 TS 类型默认 string，wire 格式 `yyyy-MM-dd HH:mm:ss`（与 api A027 一致）；dayjs 等解析格式 `YYYY-MM-DD HH:mm:ss`；禁止在 DTO 类型中用 number 表示时刻除非 project-spec 登记 epoch 单位
F012: 解析与展示须经项目统一日期工具（dayjs/date-fns 等）且显式指定与 A027 相同格式；禁止用 ISO 默认解析器解析空格分隔格式导致 NaN 或时区漂移
F013: 提交给 API 的 DateTime 须格式化为 `yyyy-MM-dd HH:mm:ss` 字符串；表单/组件取值与 types 字段类型须一致，禁止 UI 层 Date 对象直传未格式化
