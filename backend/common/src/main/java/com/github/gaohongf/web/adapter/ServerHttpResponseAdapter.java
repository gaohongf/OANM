package com.github.gaohongf.web.adapter;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpResponse;

public class ServerHttpResponseAdapter implements ServerHttpResponse {
    private final org.springframework.http.server.reactive.ServerHttpResponse response;

    public ServerHttpResponseAdapter(org.springframework.http.server.reactive.ServerHttpResponse response) {
        this.response = response;
    }

    @Override
    public OutputStream getBody() throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBody'");
    }

    @Override
    public HttpHeaders getHeaders() {
        return response.getHeaders();
    }

    @Override
    public void close() {
        response.setComplete();
    }

    @Override
    public void flush() throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'flush'");
    }

    @Override
    public void setStatusCode(HttpStatusCode status) {
        response.setStatusCode(status);
    }

}
