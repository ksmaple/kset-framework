package com.kset.agent.sandbox;

/**
 * 沙箱代码执行接口
 */
public interface CodeSandbox {

    /**
     * 执行代码
     *
     * @param language      代码语言：python / javascript
     * @param code          代码内容
     * @param timeoutSeconds 超时时间（秒）
     * @return 执行结果
     */
    SandboxResult execute(String language, String code, int timeoutSeconds);
}
