# API 域规范

A001: 所有接口 POST，HTTP 状态码固定 200
A002: 禁止路径传参，参数放 body
A003: URL 全小写，模块名单数名词，动作用动词短语
A004: 列表 POST /{module}/list，详情 POST /{module}/get，创建 POST /{module} 或 create，更新 update，删除 delete
A005: 业务操作 POST /{module}/{action}，批量 POST /{module}/batch-{action}
A006: 禁止 query-by 驼峰，使用 query-by-{field} 连字符
A007: 响应 ApiResult 含 errCode、code、errMsg、data、traceId
A008: errCode 0 成功，400 参数，401 未登录，403 无权限，500 系统
A009: 业务异常用 errCode，禁止改 HTTP status
A010: Token 头 X-Session-Token，链路 X-Trace-Id
A011: 禁止 Map/HashMap 作参数或返回类型
A012: 公开路径前缀 /api/public/，内部 /api/
A013: 不产出 design/api 文档；落地以 Controller/Command/DTO 代码为准
A014: DTO/Command 字段与 DDD 语义一致，camelCase
A015: 错误码格式 [MODULE][LEVEL][SEQ]
A016: 校验错误返回 errors 数组
A017: 敏感参数禁止 Query
A018: 批量最多 100 条
A019: POST 查询 list/get/page 天然幂等
A020: POST 创建须 Idempotency-Key 与服务端去重 24h
A021: POST 更新须乐观锁 ver 字段
A022: 禁止 @PathVariable 业务 id
A023: 新增变更接口由 proj-coder 实现
A024: 单项目单 API 风格，默认 Full POST；禁止与 REST 混用，除非 project-spec 明确
A025: 默认 Body Content-Type application/json；文件上传用 multipart/form-data
A026: 分页请求含 pageNum、pageSize；分页响应含 total 与 data 列表
