# Coding 域规范（常用编码纪律）

C001: 从缓存、静态字段、单例、配置中心、ThreadLocal 取出的 List/Set/Map 禁止原地 add/remove/clear/sort，须先拷贝或 new 新集合再变更
C002: 禁止把可变集合引用直接赋给可被多调用方共享的字段或返回值，对外须防御性拷贝或不可变视图
C003: 禁止在类成员或静态 Map 中缓存同一可变集合实例供多处写入
C004: Entity/DTO/VO 的集合 getter 禁止返回可写内部引用，对外须拷贝或只读包装
C005: 跨线程共享的 Map/List 须 Concurrent 容器或不可变副本，禁止多线程裸读写 HashMap/ArrayList
C006: 禁止不判空就对可能为 null 的引用链式调用
C007: 禁止把 null 的 Boolean/Integer/Long 隐式当作 false 或 0 参与业务分支
C008: 字符串相等禁止用 ==，须 equals 或项目约定的空安全比较
C009: 禁止空 catch 或仅 printStackTrace 吞掉异常
C010: 禁止 catch 宽泛 Exception 后不记录 traceId/业务键即结束
C011: IO、连接、流须在 try-with-resources 或 finally 关闭，禁止泄漏
C012: 业务代码禁止 System.out/err 代替日志框架
C013: 禁止在 foreach 遍历中修改被遍历集合的结构，须迭代器删除或遍历副本
C014: 禁止在高频路径重复 new SimpleDateFormat、Pattern、MessageDigest 等非线程安全昂贵对象
C015: 禁止在高频循环用 Random 或 Math.random 生成业务主键或安全随机数
C016: 禁止对包装类、字符串字面量或枚举常量做 synchronized 锁
C017: 双重检查单例的实例字段须 volatile 或等价发布语义
C018: 金额运算禁止 float/double，BigDecimal 禁止 new BigDecimal(double) 构造
C019: 禁止 static 共享非线程安全的 Date、Calendar、SimpleDateFormat
C020: 时间戳比较禁止混用秒与毫秒单位
C021: SQL 禁止拼接用户输入，须参数化，见 fix 域 X019
C022: 禁止在本地事务内直接发不可回滚的 MQ/HTTP，须 Outbox 或补偿，见 event 域 E010
C023: 禁止在循环内逐条查库或逐条远程调用，须批量、分页或合并请求
C024: 禁止修改 Controller 入参 body 对象后原样透传下游
C025: 禁止在循环内对同一 JSON 字符串反复解析做字段补丁
C026: 日志禁止拼接未脱敏 PII，见 log 域 L008
C027: 禁止硬编码密码、密钥、token 进源码或提交仓库
C028: 禁止魔法数字表达业务状态，须命名常量或枚举
C029: 业务线程禁止 Thread.sleep 作流程编排，须调度、消息或异步框架
C030: 不产出 design/coding 文档；落地以代码与审查清单为准
C031: 禁止在业务代码体内使用类型或成员的全称限定引用（如 Java `com.foo.Bar`、Python `pkg.module.func`），须在文件头 import/using/from 引入后使用简单名
C031a: 简单名冲突时优先 import 别名（Java `import … as`、TypeScript `import { X as Y }` 等），仅当无法别名化时对其中一个类型保留全称并加行内注释说明
C031b: 本约束不适用于代码生成模板、单行注释中的文档链接、以及框架要求的注解全限定名（如 `@org.springframework…` 若项目惯例允许可保留，新增代码优先短名 + import）
