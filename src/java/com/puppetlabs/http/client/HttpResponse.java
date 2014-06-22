package com.puppetlabs.http.client;

import com.puppetlabs.http.client.RequestOptions;

import java.util.Map;

public class HttpResponse {
    private RequestOptions options;
    private String origContentEncoding;
    private Throwable error;
    private Object body;
    private Map<String, String> headers;
    private Integer status;

    public HttpResponse(RequestOptions options, Throwable error) {
        this.options = options;
        this.error = error;
    }

    public HttpResponse(RequestOptions options, String origContentEncoding, Object body, Map<String, String> headers, int status) {
        this.options = options;
        this.origContentEncoding = origContentEncoding;
        this.body = body;
        this.headers = headers;
        this.status = status;
    }

    public RequestOptions getOptions() {
        return options;
    }

    public String getOrigContentEncoding() { return origContentEncoding; }

    public Throwable getError() {
        return error;
    }

    public Object getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Integer getStatus() {
        return status;
    }
}
