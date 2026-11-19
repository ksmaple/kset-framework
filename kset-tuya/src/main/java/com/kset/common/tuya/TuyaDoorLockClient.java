package com.kset.common.tuya;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.kset.common.tuya.model.*;
import com.kset.common.tuya.util.PasswordEncryptUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 涂鸦智能门锁API客户端
 * <p>
 * 封装门锁临时密码相关操作：获取密钥ticket、创建临时密码、删除临时密码。
 * <p>
 * 使用示例:
 * <pre>{@code
 * TuyaConfig config = TuyaConfig.builder()
 *     .clientId("your_client_id")
 *     .secret("your_secret")
 *     .region(TuyaRegion.CN)
 *     .build();
 *
 * TuyaApiClient apiClient = new TuyaApiClient(config);
 * TuyaDoorLockClient doorLock = new TuyaDoorLockClient(apiClient);
 *
 * // 创建临时密码
 * TempPasswordParam param = TempPasswordParam.builder()
 *     .name("访客密码")
 *     .password("1234567")
 *     .effectiveTime(1700000000L)
 *     .invalidTime(1700086400L)
 *     .type(0)
 *     .build();
 * Long passwordId = doorLock.createTempPassword("device_id", param);
 *
 * // 删除临时密码
 * doorLock.deleteTempPassword("device_id", passwordId);
 * }</pre>
 *
 * @author kset
 */
@Slf4j
public class TuyaDoorLockClient {

    private final TuyaApiClient apiClient;

    public TuyaDoorLockClient(TuyaApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 获取临时密钥ticket
     *
     * @param deviceId 设备ID
     * @return 密钥ticket信息
     */
    public PasswordTicket getPasswordTicket(String deviceId) {
        log.info("获取门锁密码ticket: deviceId={}", deviceId);

        String path = "/v1.0/devices/" + deviceId + "/door-lock/password-ticket";
        TuyaResponse<PasswordTicket> response = apiClient.post(
                path, null, new TypeReference<>() {});

        if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
            log.info("获取密码ticket成功: deviceId={}, ticketId={}",
                    deviceId, response.getResult().getTicketId());
            return response.getResult();
        }

        String errMsg = response != null ? response.getMsg() : "响应为空";
        log.error("获取密码ticket失败: deviceId={}, error={}", deviceId, errMsg);
        throw new RuntimeException("获取密码ticket失败: " + errMsg);
    }

    /**
     * 创建门锁临时密码
     * <p>
     * 流程：
     * <ol>
     *   <li>获取临时密钥ticket（含加密的ticket_key）</li>
     *   <li>用accessKey（即Access Secret）AES解密ticket_key，得到原始密钥</li>
     *   <li>用原始密钥AES加密密码明文，输出hex</li>
     *   <li>调用创建接口</li>
     * </ol>
     *
     * @param deviceId 设备ID
     * @param param    临时密码参数
     * @return 密码ID
     */
    public Long createTempPassword(String deviceId, TempPasswordParam param) {
        log.info("创建门锁临时密码: deviceId={}, name={}", deviceId, param.getName());

        // 1. 获取临时密钥
        PasswordTicket ticket = getPasswordTicket(deviceId);

        // 2. 用 PasswordEncryptUtil 解密ticket_key并加密密码
        String accessSecret = apiClient.getConfig().getSecret();
        String encryptedPassword;
        try {
            encryptedPassword = PasswordEncryptUtil.encryptPassword(
                    param.getPassword(), accessSecret, ticket.getTicketKey());
            log.info("密码加密成功: ticketId={}", ticket.getTicketId());
        } catch (Exception e) {
            log.error("密码加密失败: deviceId={}", deviceId, e);
            throw new RuntimeException("密码加密失败", e);
        }

        // 3. 构建请求body
        JSONObject body = new JSONObject();
        body.put("name", param.getName());
        body.put("password", encryptedPassword);
        body.put("password_type", "ticket");
        body.put("ticket_id", ticket.getTicketId());
        body.put("effective_time", param.getEffectiveTime());
        body.put("invalid_time", param.getInvalidTime());

        if (param.getPhone() != null) {
            body.put("phone", param.getPhone());
        }
        if (param.getType() != null) {
            body.put("type", param.getType());
        }
        if (param.getTimeZone() != null) {
            body.put("time_zone", param.getTimeZone());
        }
        if (param.getScheduleList() != null && !param.getScheduleList().isEmpty()) {
            List<Map<String, Object>> scheduleList = new ArrayList<>();
            for (ScheduleItem item : param.getScheduleList()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("effective_time", item.getEffectiveTime());
                map.put("invalid_time", item.getInvalidTime());
                map.put("working_day", item.getWorkingDay());
                scheduleList.add(map);
            }
            body.put("schedule_list", scheduleList);
        }
        if (param.getRelateDevList() != null && !param.getRelateDevList().isEmpty()) {
            body.put("relate_dev_list", param.getRelateDevList());
        }

        // 4. 调用创建接口
        String path = "/v1.0/devices/" + deviceId + "/door-lock/temp-password";
        TuyaResponse<JSONObject> response = apiClient.post(
                path, body, new TypeReference<>() {});

        if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
            Long passwordId = response.getResult().getLong("id");
            log.info("创建临时密码成功: deviceId={}, passwordId={}", deviceId, passwordId);
            return passwordId;
        }

        String errMsg = response != null ? response.getMsg() : "响应为空";
        log.error("创建临时密码失败: deviceId={}, error={}", deviceId, errMsg);
        throw new RuntimeException("创建临时密码失败: " + errMsg);
    }

    /**
     * 删除门锁临时密码
     *
     * @param deviceId   设备ID
     * @param passwordId 密码ID
     * @return 是否成功
     */
    public boolean deleteTempPassword(String deviceId, Long passwordId) {
        log.info("删除门锁临时密码: deviceId={}, passwordId={}", deviceId, passwordId);

        String path = "/v1.0/devices/" + deviceId
                + "/door-lock/temp-passwords/" + passwordId;
        TuyaResponse<Boolean> response = apiClient.delete(
                path, null, new TypeReference<>() {});

        if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
            log.info("删除临时密码成功: deviceId={}, passwordId={}", deviceId, passwordId);
            return Boolean.TRUE.equals(response.getResult());
        }

        String errMsg = response != null ? response.getMsg() : "响应为空";
        log.error("删除临时密码失败: deviceId={}, passwordId={}, error={}",
                deviceId, passwordId, errMsg);
        throw new RuntimeException("删除临时密码失败: " + errMsg);
    }
}
