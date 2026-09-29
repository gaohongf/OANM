package com.github.gaohongf.auth.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.auth.client.AuthInternalApi;
import com.github.gaohongf.auth.mq.model.RegisterServiceAuth;
import com.github.gaohongf.auth.register.EndpointRegistrationService;

import lombok.AllArgsConstructor;

/**
 * 自注册的 HTTP 入口。
 *
 * <h2>为什么除了 Kafka 还要留一个 HTTP 端点</h2>
 * 正常路径是 Kafka（消息不会丢，service-auth 不在时也留得住）。但有两种情况它不顶用：
 * <ul>
 *   <li>消息已经过了 retention（服务停了很久才重启，期间的消息被清理）</li>
 *   <li>Kafka 本身不可用</li>
 * </ul>
 * 这两种情况下如果只有 Kafka，那个服务的端点就永远没有权限行 —— 而现象只是"所有人都 403"。
 * 所以保留一个不依赖 MQ 的补报入口，同时也作为调用方的即时兜底。
 *
 * <p>标 {@link IsOpen}：调用方是服务身份、没有用户 token，走鉴权必然 403。
 * 因此它和所有内部端点一样<b>绝不能从网关暴露</b> —— 路径是 {@code /auth/internal/**}，
 * 不以 {@code /api} 开头，而网关只匹配 {@code /api/**}，所以天然不可达。
 */
@AllArgsConstructor
@RestController
@RequestMapping(AuthInternalApi.REGISTER)
public class InternalRegistrationController {

    private final EndpointRegistrationService registrationService;

    /**
     * 登记一批端点权限键。幂等：重复上报已存在的键不会产生重复行，也不会改动它们的标签。
     *
     * @return 本次新建的键
     */
    @IsOpen
    @PostMapping
    public List<String> register(@RequestBody RegisterServiceAuth registration) {
        return registrationService.register(registration.getAuths());
    }
}
