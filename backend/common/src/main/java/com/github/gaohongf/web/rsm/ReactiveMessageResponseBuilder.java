package com.github.gaohongf.web.rsm;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.response.ApiResponseDescriptor;
import com.lingyun.base.response.ResponseBuilder;
import com.lingyun.base.rsm.message.Response;
import com.lingyun.base.rsm.message.ResponseMessage;
import com.lingyun.base.rsm.message.ResponseMessageService;
import com.lingyun.base.rsm.message.ResponseType;

import lombok.AllArgsConstructor;

@Primary
@Component
@AllArgsConstructor
public class ReactiveMessageResponseBuilder implements ResponseBuilder<Response> {

    private final ResponseMessageService messageService;
    private final static ResponseMessage DEFAULT_RSM_NOT_FOUND = new ResponseMessage(
            -1,
            GenericRsm.RSM_NOT_FOUND,
            "未找到响应消息",
            "ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR.value());

    @Override
    public Response build(ApiResponseDescriptor descriptor, Consumer<HttpStatus> statusConsumer,
            Supplier<HttpHeaders> headerSupplier) {
        ResponseMessage message = Optional.ofNullable(messageService.findByMessageKey(descriptor.getMessageKey()))
                .or(() -> messageService.findOptByMessageKey(GenericRsm.RSM_NOT_FOUND))
                .orElseGet(() -> DEFAULT_RSM_NOT_FOUND);
                        statusConsumer.accept(HttpStatus.valueOf(message.getResponseStatus()));

        return createResponse(message, descriptor.getArgs(), descriptor.getData());
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
