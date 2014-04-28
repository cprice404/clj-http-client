package com.puppetlabs.http.client.impl;

import com.puppetlabs.http.client.HttpMethod;

import javax.net.ssl.SSLContext;
import java.util.Map;

public class CoercedRequestOptions {
    private final String url;
    private final HttpMethod method;
    private final Map<String, Object> headers;
    private final Object body;
    private final SSLContext sslContext;


    public CoercedRequestOptions(String url,
                                 HttpMethod method,
                                 Map<String, Object> headers,
                                 Object body,
                                 SSLContext sslContext) {
        this.url = url;
        this.method = method;
        this.headers = headers;
        this.body = body;
        this.sslContext = sslContext;
    }

    public String getUrl() {
        return url;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public Object getBody() {
        return body;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }
}
