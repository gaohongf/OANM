package com.github.gaohongf.software.entity.res;

/**
 * 下载凭据：前端拿它触发一次同源下载。
 *
 * @param url      同源相对地址，形如 /file/2026/10/24/xxx.exe
 * @param fileName 上传时的原始文件名
 */
public record DownloadRes(String url, String fileName) {
}
