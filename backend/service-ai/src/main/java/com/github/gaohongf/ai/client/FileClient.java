package com.github.gaohongf.ai.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.lingyun.base.rsm.exception.RequestException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 从 service-files 读取图片字节。
 *
 * <h2>为什么要把图片读回本地, 而不是直接把 URL 交给模型</h2>
 * 上传返回的是相对地址 {@code /file/...}, 只有本项目的网关/内网能访问;
 * 模型跑在 {@code api.deepseek.com}, 既拿不到相对地址, 也访问不到 localhost。
 * Spring AI 对 URI 型 {@code Media} 是原样转给模型服务端的, 不会替我们下载。
 * 所以这里先取回字节, 构造 {@code Media(MimeType, byte[])}, 由 Spring AI 内联成 base64。
 *
 * <h2>MIME 从哪来</h2>
 * 不猜扩展名优先: 直接读 service-files 响应的 {@code Content-Type}（文件上传时
 * X File Storage 已按内容/扩展名写入）。只有响应头缺失或是
 * {@code application/octet-stream} 时, 才退回按 URL 后缀推断。
 */
@Slf4j
@Component
public class FileClient {

    /** 读图是对话链路的一环, 不能无限等; 超时会变成一条可读的 error 帧 */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final WebClient fileWebClient;

    public FileClient(@Qualifier("fileWebClient") WebClient fileWebClient) {
        this.fileWebClient = fileWebClient;
    }

    /**
     * 读取一张图片。
     *
     * @param url 上传接口返回的相对地址, 如 {@code /file/2026/10/19/xxx.jpg}
     * @throws RequestException 文件不存在、读取超时或无法确定 MIME 时
     */
    public FileContent load(String url) {
        try {
            return fileWebClient.get()
                    .uri(url)
                    .exchangeToMono(response -> {
                        if (!response.statusCode().is2xxSuccessful()) {
                            return response.releaseBody()
                                    .then(Mono.error(new RequestException(
                                            "图片读取失败（HTTP " + response.statusCode().value() + "）")));
                        }
                        MediaType headerType = response.headers().contentType().orElse(null);
                        return response.bodyToMono(byte[].class)
                                .map(bytes -> new FileContent(resolveContentType(url, headerType), bytes));
                    })
                    .timeout(TIMEOUT)
                    .block();
        } catch (RequestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // 连接被拒、超时、响应过大都会走到这里。原始异常只进日志,
            // 对用户统一成一句能看懂的话。
            log.warn("读取图片失败: url={}", url, ex);
            throw new RequestException(ex, "图片读取失败，请稍后重试");
        }
    }

    private static String resolveContentType(String url, MediaType headerType) {
        if (headerType != null && !MediaType.APPLICATION_OCTET_STREAM.equals(headerType)) {
            return headerType.toString();
        }
        return MediaTypeFactory.getMediaType(url)
                .orElseThrow(() -> new RequestException("无法识别图片类型"))
                .toString();
    }

    /**
     * @param contentType 形如 {@code image/jpeg}
     * @param bytes       图片原始字节, 交给 Spring AI 内联成 base64 data URL
     */
    public record FileContent(String contentType, byte[] bytes) {
    }
}
