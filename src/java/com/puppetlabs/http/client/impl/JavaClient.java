package com.puppetlabs.http.client.impl;

import com.puppetlabs.http.client.HttpClientException;
import com.puppetlabs.http.client.HttpMethod;
import com.puppetlabs.http.client.HttpResponse;
import com.puppetlabs.http.client.RequestOptions;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.*;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class JavaClient {

    private static final String PROTOCOL = "TLS";

    private static Map<String, Object> prepareHeaders(RequestOptions options) {
        Map<String, Object> result = new HashMap<String, Object>();
        if (options.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : options.getHeaders().entrySet()) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static CoercedRequestOptions coerceRequestOptions(RequestOptions options) {
        String url = options.getUrl();

        SSLContext sslContext = null;
        if (options.getSslContext() != null) {
            sslContext = options.getSslContext();
        } else if (options.getInsecure()) {
            sslContext = getInsecureSslContext();
        }

        HttpMethod method = options.getMethod();
        if (method == null) {
            method = HttpMethod.GET;
        }

        Map<String, Object> headers = prepareHeaders(options);

        Object body = options.getBody();

        return new CoercedRequestOptions(url, method, headers, body, sslContext);
    }

    private static SSLContext getInsecureSslContext() {
        SSLContext context = null;
        try {
            context = SSLContext.getInstance(PROTOCOL);
        } catch (NoSuchAlgorithmException e) {
            throw new HttpClientException("Unable to construct HTTP context", e);
        }
        try {
            context.init(null, new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] chain,
                                                       String authType) throws CertificateException {
                            // Always trust
                        }

                        public void checkServerTrusted(X509Certificate[] chain,
                                                       String authType) throws CertificateException {
                            // Always trust
                        }
                    }},
                    null);
        } catch (KeyManagementException e) {
            throw new HttpClientException("Unable to initialize insecure SSL context", e);
        }
        return context;
    }

    public static Promise<HttpResponse> request(final RequestOptions options, final IResponseCallback callback) {
        CoercedRequestOptions coercedOptions = coerceRequestOptions(options);

        final CloseableHttpAsyncClient client = createClient(coercedOptions);

        HttpRequestBase request = buildRequest(coercedOptions);

        final Promise<HttpResponse> promise = new Promise<HttpResponse>();

        client.execute(request, new FutureCallback<org.apache.http.HttpResponse>() {
            @Override
            public void completed(org.apache.http.HttpResponse httpResponse) {
                InputStream body = null;
                try {
                    HttpEntity entity = httpResponse.getEntity();
                    if (entity != null) {
                        body = entity.getContent();
                    }
                } catch (Exception e) {
                    deliverResponse(client, options, new HttpResponse(options, e), callback, promise);
                }
                Map<String, String> headers = new HashMap<String, String>();
                for (Header h : httpResponse.getAllHeaders()) {
                    headers.put(h.getName(), h.getValue());
                }
                deliverResponse(client, options, new HttpResponse(options, body, headers, httpResponse.getStatusLine().getStatusCode()), callback, promise);
            }

            @Override
            public void failed(Exception e) {
                deliverResponse(client, options, new HttpResponse(options, e), callback, promise);
            }

            @Override
            public void cancelled() {
                deliverResponse(client, options, new HttpResponse(options, new HttpClientException("Request cancelled", null)), callback, promise);
            }
        });

        return promise;
    }

    private static CloseableHttpAsyncClient createClient(CoercedRequestOptions coercedOptions) {
        CloseableHttpAsyncClient client;
        if (coercedOptions.getSslContext() != null) {
            client = HttpAsyncClients.custom().setSSLContext(coercedOptions.getSslContext()).build();
        } else {
            client = HttpAsyncClients.createDefault();
        }
        client.start();
        return client;
    }

    private static void deliverResponse(CloseableHttpAsyncClient client, RequestOptions options,
                                        HttpResponse httpResponse, IResponseCallback callback,
                                        Promise<HttpResponse> promise) {
        try {
            if (callback != null) {
                try {
                    promise.deliver(callback.handleResponse(httpResponse));
                } catch (Exception ex) {
                    promise.deliver(new HttpResponse(options, ex));
                }
            } else {
                promise.deliver(httpResponse);
            }
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                throw new HttpClientException("Unable to close client", e);
            }
        }
    }

    private static HttpRequestBase buildRequest(CoercedRequestOptions coercedOptions) {
        switch (coercedOptions.getMethod()) {
            case GET:
                return new HttpGet(coercedOptions.getUrl());
            case HEAD:
                return new HttpHead(coercedOptions.getUrl());
            case POST:
                return new HttpPost(coercedOptions.getUrl());
            case PUT:
                return new HttpPut(coercedOptions.getUrl());
            case DELETE:
                return new HttpDelete(coercedOptions.getUrl());
            case TRACE:
                return new HttpTrace(coercedOptions.getUrl());
            case OPTIONS:
                return new HttpOptions(coercedOptions.getUrl());
            case PATCH:
                return new HttpPatch(coercedOptions.getUrl());
            default:
                throw new HttpClientException("Unable to construct request for:" + coercedOptions.getMethod() + ", " + coercedOptions.getUrl(), null);
        }
    }
}
