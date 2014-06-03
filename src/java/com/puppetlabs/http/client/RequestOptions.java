package com.puppetlabs.http.client;

import com.puppetlabs.http.client.impl.*;
import org.apache.http.nio.client.HttpAsyncClient;
//import org.httpkit.client.HttpClient;
//
//import org.httpkit.client.IFilter;
//import org.httpkit.client.MultipartEntity;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class RequestOptions {
    private HttpAsyncClient client = null;
    private int timeout = 60000;
    private boolean followRedirects = true;
    private int maxRedirects = 10;
//    // TODO: we are technically leaking this http-kit class into our API,
//    // but since we're not using it anywhere I decided not to worry about it yet.
//    private IFilter filter = IFilter.ACCEPT_ALL;
//    private ExecutorService workerPool = DefaultWorkerPool.getInstance();
    private Promise<HttpResponse> promise = new Promise<HttpResponse>();
    private int keepalive = 120000;
    private ResponseBodyType as = ResponseBodyType.AUTO;

    private String url;
    private HttpMethod method = null;
    private List<String> traceRedirects = new ArrayList<String>();
    private Map<String, Object> headers;
    private Map<String, String> formParams;
    private BasicAuth basicAuth;
    private String oauthToken;
    private String userAgent;
    private Map<String, String> queryParams;
    private SSLEngine sslEngine;
    private SSLContext sslContext;
    private String sslCert;
    private String sslKey;
    private String sslCaCert;
    private boolean insecure = false;
    private Object body;
//    // TODO: we are technically leaking this http-kit class into our API,
//    // but since we're not using it anywhere I decided not to worry about it yet.
//    private List<MultipartEntity> multipartEntities;


    public RequestOptions(String url) {
        this.url = url;
    }

    public HttpAsyncClient getClient() {
        return client;
    }
    public RequestOptions setClient(HttpAsyncClient client) {
        this.client = client;
        return this;
    }

    public String getUrl() {
        return url;
    }
    public RequestOptions setUrl(String url) {
        this.url = url;
        return this;
    }

    public int getTimeout() {
        return timeout;
    }
    public RequestOptions setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public int getKeepalive() {
        return keepalive;
    }
    public RequestOptions setKeepalive(int keepalive) {
        this.keepalive = keepalive;
        return this;
    }

    public boolean getFollowRedirects() {
        return followRedirects;
    }
    public RequestOptions setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }
    public RequestOptions setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    public ResponseBodyType getAs() {
        return as;
    }
    public RequestOptions setAs(ResponseBodyType as) {
        this.as = as;
        return this;
    }

    public HttpMethod getMethod() {
        return method;
    }
    public RequestOptions setMethod(HttpMethod method) {
        this.method = method;
        return this;
    }

//    public IFilter getFilter() {
//        return filter;
//    }
//    public RequestOptions setFilter(IFilter filter) {
//        this.filter = filter;
//        return this;
//    }
//
//    public ExecutorService getWorkerPool() {
//        return workerPool;
//    }

    public Promise<HttpResponse> getPromise() {
        return this.promise;
    }
    public RequestOptions setPromise(Promise<HttpResponse> promise) {
        this.promise = promise;
        return this;
    }

    public List<String> getTraceRedirects() {
        return traceRedirects;
    }
    public RequestOptions addTraceRedirect(String url) {
        traceRedirects.add(url);
        return this;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }
    public RequestOptions setHeaders(Map<String, Object> headers) {
        this.headers = headers;
        return this;
    }

    public Map<String, String> getFormParams() {
        return formParams;
    }
    public RequestOptions setFormParams(Map<String, String> formParams) {
        this.formParams = formParams;
        return this;
    }

    public BasicAuth getBasicAuth() {
        return basicAuth;
    }
    public RequestOptions setBasicAuth(BasicAuth basicAuth) {
        this.basicAuth = basicAuth;
        return this;
    }

    public String getOAuthToken() {
        return oauthToken;
    }
    public RequestOptions setOAuthToken(String oauthToken) {
        this.oauthToken = oauthToken;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }
    public RequestOptions setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }
    public RequestOptions setQueryParams(Map<String, String> queryParams) {
        this.queryParams = queryParams;
        return this;
    }

    public SSLEngine getSslEngine() {
        return sslEngine;
    }
    public RequestOptions setSslEngine(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
        return this;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }
    public RequestOptions setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    public String getSslCert() {
        return sslCert;
    }
    public RequestOptions setSslCert(String sslCert) {
        this.sslCert = sslCert;
        return this;
    }

    public String getSslKey() {
        return sslKey;
    }
    public RequestOptions setSslKey(String sslKey) {
        this.sslKey = sslKey;
        return this;
    }

    public String getSslCaCert() {
        return sslCaCert;
    }
    public RequestOptions setSslCaCert(String sslCaCert) {
        this.sslCaCert = sslCaCert;
        return this;
    }

    public boolean getInsecure() {
        return insecure;
    }
    public RequestOptions setInsecure(boolean insecure) {
        this.insecure = insecure;
        return this;
    }

    public Object getBody() {
        return body;
    }
    public RequestOptions setBody(Object body) {
        this.body = body;
        return this;
    }

//    public List<MultipartEntity> getMultipartEntities() {
//        return multipartEntities;
//    }
//    public RequestOptions setMultipartEntities(List<MultipartEntity> entities) {
//        this.multipartEntities = entities;
//        return this;
//    }

}
