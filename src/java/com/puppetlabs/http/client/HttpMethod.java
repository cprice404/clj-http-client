package com.puppetlabs.http.client;

// This is really dumb, but I didn't want to leak the HTTPKit class into the
// API for now.

import org.apache.http.client.methods.*;

public enum HttpMethod {
    GET(HttpGet.class),
    HEAD(HttpHead.class),
    POST(HttpPost.class),
    PUT(HttpPut.class),
    DELETE(HttpDelete.class),
    TRACE(HttpTrace.class),
    OPTIONS(HttpOptions.class),
    PATCH(HttpPatch.class);

    private Class<? extends HttpRequestBase> httpMethod;

    HttpMethod(Class<? extends HttpRequestBase> httpMethod) {
        this.httpMethod = httpMethod;
    }

    public Class<? extends HttpRequestBase> getValue() {
        return this.httpMethod;
    }

}
