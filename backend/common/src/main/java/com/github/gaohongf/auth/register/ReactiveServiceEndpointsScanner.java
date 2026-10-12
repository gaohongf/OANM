package com.github.gaohongf.auth.register;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.condition.PatternsRequestCondition;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.auth.annotation.LoginOnly;
import com.github.gaohongf.auth.register.ServiceEndpoints.Endpoint;

public class ReactiveServiceEndpointsScanner extends AbstractServiceEndpointsScanner<RequestMappingHandlerMapping> {
    private static final Set<String> EMPTY_PATH = Collections.singleton("");

    public ReactiveServiceEndpointsScanner(RequestMappingHandlerMapping handlerMapping) {
        super(handlerMapping);
    }

    /**
     * `@RequestMapping` 没指定方法时，展开成这些方法。
     * <p>
     * 必须展开成具体方法而不能记成 `*:/path`：拦截器是按真实请求方法推导权限键的
     * （`GET:/path`、`POST:/path`…），记一个通配键谁也匹配不上。
     */
    private static final List<RequestMethod> ALL_METHODS = List.of(
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH);

    @Override
    public ServiceEndpoints scan() {
        Set<Endpoint> found = new LinkedHashSet<>();
        
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo mapping = entry.getKey();
            HandlerMethod handler = entry.getValue();

            if (shouldSkip(handler)) {
                continue;
            }

            Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
            PatternsRequestCondition patternsRequestCondition = mapping.getPatternsCondition();
            Set<String> patterns = patternsRequestCondition.isEmptyPathMapping() ? EMPTY_PATH
                    : patternsRequestCondition.getPatterns().stream().map(PathPattern::getPatternString)
                            .collect(Collectors.toSet());
            for (String pattern : patterns) {
                if (methods.isEmpty()) {
                    for (RequestMethod method : ALL_METHODS) {
                        found.add(new Endpoint(method.name(), pattern));
                    }
                } else {
                    for (RequestMethod method : methods) {
                        found.add(new Endpoint(method.name(), pattern));
                    }
                }
            }
        }

        return new ServiceEndpoints(List.copyOf(found));
    }

    private static boolean shouldSkip(HandlerMethod handler) {
        if (handler.getMethodAnnotation(IsOpen.class) != null
                || handler.getMethodAnnotation(LoginOnly.class) != null) {
            return true;
        }
        return false;
    }
}
