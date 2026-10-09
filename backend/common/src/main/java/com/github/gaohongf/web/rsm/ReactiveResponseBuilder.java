package com.github.gaohongf.web.rsm;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;

import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.HttpStatusRsm;
import com.lingyun.base.rsm.ResponseBuilder;
import com.lingyun.base.rsm.RsmManager;
import com.lingyun.base.rsm.message.Response;
import com.lingyun.base.rsm.message.ResponseMessage;
import com.lingyun.base.rsm.message.ResponseMessageService;
import com.lingyun.base.rsm.message.ResponseType;

import lombok.AllArgsConstructor;
@Primary 
@Component 
@AllArgsConstructor
public class ReactiveResponseBuilder implements ResponseBuilder<Response> {

    private final ResponseMessageService messageService;

    @Override
    public Response build(String msg, Object data, Object[] varargs) {
        ResponseMessage message = Optional.ofNullable(messageService.findByMessageKey(msg))
                .orElseGet(() -> messageService.findByMessageKey(HttpStatusRsm.OK));
        return createResponse(message, varargs, data);
    }

    @Override
    public Response build(ServerHttpResponse response, String msg, Object data, Object[] varargs) {
        ResponseMessage message = Optional.ofNullable(messageService.findByMessageKey(msg))
                .orElseGet(() -> messageService.findByMessageKey(HttpStatusRsm.OK));
        response.setStatusCode(HttpStatusCode.valueOf(message.getResponseStatus()));

        return createResponse(message, varargs, data);
    }

    @Override
    public String buildMessage(String msg, Object[] varargs) {
        return messageService.findOptByMessageKey(msg)
                .map(ResponseMessage::getTemplate)
                .map(template -> MessageFormat.format(template, varargs))
                .orElse(msg);
    }

    private Response createResponse(ResponseMessage message, Object[] params, Object data) {
        Response rsp = new Response();
        rsp.setCode(message.getCode());
        rsp.setType(parseType(message.getType()));
        rsp.setMsg(MessageFormat.format(message.getTemplate(), params));
        rsp.setData(data);
        return rsp;
    }

    /**
     * 将数据库中的 type 字符串解析为 {@link ResponseType} 枚举。
     *
     * @param type 类型字符串（如 "SUCCESS"、"WARN"、"INFO"、"ERROR"）
     * @return 对应的 ResponseType 枚举值，解析失败时返回 SUCCESS
     */
    private static ResponseType parseType(String type) {
        try {
            return type != null ? ResponseType.valueOf(type) : ResponseType.SUCCESS;
        } catch (IllegalArgumentException e) {
            return ResponseType.SUCCESS;
        }
    }
}
