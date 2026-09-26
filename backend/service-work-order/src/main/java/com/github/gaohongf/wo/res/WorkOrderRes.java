package com.github.gaohongf.wo.res;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.github.gaohongf.serializer.annotation.User;

import lombok.Data;

/**
 * 工单返回体。
 * <p>
 * {@link User} 标在 id 字段上, 序列化时会自动替换成用户对象:
 * 
 * <pre>
 *   "createBy": 1
 *     ↓
 *   "createBy": { "id": 1, "username": "alice" }
 * </pre>
 * 
 * 输出哪些成员由注解属性控制。解析不到用户（服务不可用、用户已删）时降级成
 * {@code {"id": 1}}, 不会导致整个响应失败。
 */
@Data
public class WorkOrderRes {

    private String id;

    private String name;

    /** 创建人。username 默认开启, nickname 默认关闭 */
    @User
    private Long createBy;

    /** 更新人。这里额外要昵称 */
    @User(nickname = true)
    private Long updateBy;
}
