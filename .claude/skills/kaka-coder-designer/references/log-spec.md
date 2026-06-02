# Log 域规范

L001: traceId 与 API X-Trace-Id 一致
L002: 结构化 JSON 日志，含 level、traceId、logger、message、timestamp
L003: HTTP 慢接口阈值大于 500ms 记 WARN，含 URL、Method、参数摘要
L004: SQL 慢查询阈值大于 200ms 记 WARN，含 SQL、参数、返回行数
L005: 外部 HTTP 慢调用阈值大于 1000ms 记 WARN
L006: MQ 消费慢处理阈值大于 5000ms 记 WARN
L007: 慢日志 WARN 接入告警，1 分钟内同类型超 10 条触发
L008: 敏感字段脱敏：手机号中间四位、身份证中间位、邮箱用户名首字符加掩码
L009: 禁止日志输出密码、token、密钥明文
L010: 日志字段名与 API/DDD 语义对齐
