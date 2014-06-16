package com.puppetlabs.http.client.impl;

import com.puppetlabs.http.client.HttpClientException;
import com.puppetlabs.http.client.HttpMethod;
import com.puppetlabs.http.client.HttpResponse;
import com.puppetlabs.http.client.RequestOptions;
//import org.httpkit.HttpMethod;
//import org.httpkit.client.*;
//
//import javax.net.ssl.SSLEngine;
//import javax.xml.bind.DatatypeConverter;
//import java.io.IOException;
//import java.io.UnsupportedEncodingException;
//import java.net.URLEncoder;
//import java.util.HashMap;
//import java.util.Map;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
//import org.apache.http.nio.client.HttpAsyncClient;

import javax.net.ssl.*;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class JavaClient {

    private static final String PROTOCOL = "TLS";

//    private static CloseableHttpAsyncClient defaultClient = initializeDefaultClient();
//
//    private static CloseableHttpAsyncClient initializeDefaultClient() {
//        CloseableHttpAsyncClient client = HttpAsyncClients.createDefault();
//        client.start();
//        return client;
//    }
//
//    public static HttpAsyncClient getDefaultClient() {
//        return defaultClient;
//    }

    private static String buildQueryString(Map<String, String> params) {
        // TODO: add support for nested query params.  For now we assume a flat,
        // String->String data structure.
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            first = false;
            try {
                sb.append(URLEncoder.encode(entry.getKey(), "utf8"));
                sb.append("=");
                sb.append(URLEncoder.encode(entry.getValue(), "utf8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Error while url-encoding query string", e);
            }
        }
        return sb.toString();
    }

    private static String getBasicAuthValue(BasicAuth auth) {
        String userPasswordStr = auth.getUser() + ":" + auth.getPassword();
        try {
            return "Basic " + DatatypeConverter.printBase64Binary(userPasswordStr.getBytes("utf8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error while attmempting to encode basic auth", e);
        }
    }

    private static Map<String, Object> prepareHeaders(RequestOptions options) {
        Map<String, Object> result = new HashMap<String, Object>();
        if (options.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : options.getHeaders().entrySet()) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        if (options.getFormParams() != null) {
            result.put("Content-Type", "application/x-www-form-urlencoded");
        }
        if (options.getBasicAuth() != null) {
            result.put("Authorization", getBasicAuthValue(options.getBasicAuth()));
        }
        if (options.getOAuthToken() != null) {
            result.put("Authorization", "Bearer " + options.getOAuthToken());
        }
        if (options.getUserAgent() != null) {
            result.put("User-Agent", options.getUserAgent());
        }
        return result;
    }

    private static CoercedRequestOptions coerceRequestOptions(RequestOptions options) {
        String url;
        if (options.getQueryParams() != null) {
            if (options.getUrl().indexOf('?') == -1) {
                url = options.getUrl() + "?" + buildQueryString(options.getQueryParams());
            } else {
                url = options.getUrl() + "&" + buildQueryString(options.getQueryParams());
            }
        } else {
            url = options.getUrl();
        }

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

        Object body;
        if (options.getFormParams() != null) {
            body = buildQueryString(options.getFormParams());
        } else {
            body = options.getBody();
        }

//        if (options.getMultipartEntities() != null) {
//            String boundary = MultipartEntity.genBoundary(options.getMultipartEntities());
//
//            headers = options.getHeaders();
//            headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);
//
//            body = MultipartEntity.encode(boundary, options.getMultipartEntities());
//        }

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
//        HttpAsyncClient client = options.getClient();
//        if (client == null) {
//            client = getDefaultClient();
//        }

        CoercedRequestOptions coercedOptions = coerceRequestOptions(options);

        final CloseableHttpAsyncClient client = createClient(coercedOptions);

        HttpRequestBase request = buildRequest(coercedOptions);

        final Promise<HttpResponse> promise = new Promise<HttpResponse>();

        client.execute(request, new FutureCallback<org.apache.http.HttpResponse>() {
            @Override
            public void completed(org.apache.http.HttpResponse httpResponse) {
                InputStream body = null;
                try {
                    body = httpResponse.getEntity().getContent();
                } catch (IOException e) {
                    deliverResponse(client, options, new HttpResponse(options, e), callback, promise);
//                    callback.handleResponse()
//                    promise.deliver();
                }
                Map<String, String> headers = new HashMap<String, String>();
                for (Header h : httpResponse.getAllHeaders()) {
                    headers.put(h.getName(), h.getValue());
                }
                deliverResponse(client, options, new HttpResponse(options, body, headers, httpResponse.getStatusLine().getStatusCode()), callback, promise);
//                promise.deliver(new HttpResponse(options, body, headers, httpResponse.getStatusLine().getStatusCode()));
            }

            @Override
            public void failed(Exception e) {
                deliverResponse(client, options, new HttpResponse(options, e), callback, promise);
//                promise.deliver(new HttpResponse(options, e));
            }

            @Override
            public void cancelled() {
                deliverResponse(client, options, new HttpResponse(options, new HttpClientException("Request cancelled", null)), callback, promise);
//                promise.deliver(new HttpResponse(options, new HttpClientException("Request cancelled", null)));
            }
        });

        return promise;

/*
 final HttpGet request2 = new HttpGet("http://www.apache.org/");
    httpclient.execute(request2, new FutureCallback<HttpResponse>() {

        public void completed(final HttpResponse response2) {
            latch1.countDown();
            System.out.println(request2.getRequestLine() + "->" + response2.getStatusLine());
        }

        public void failed(final Exception ex) {
            latch1.countDown();
            System.out.println(request2.getRequestLine() + "->" + ex);
        }

        public void cancelled() {
            latch1.countDown();
            System.out.println(request2.getRequestLine() + " cancelled");
        }

    });
    latch1.await();
 */




//        RequestConfig config = new RequestConfig(coercedOptions.getMethod(),
//                coercedOptions.getHeaders(), coercedOptions.getBody(),
//                options.getTimeout(), options.getKeepalive());
//
//        RespListener listener = new RespListener(
//                new ResponseHandler(options, coercedOptions, callback), options.getFilter(),
//                options.getWorkerPool(), options.getAs().getValue());
//
//        client.exec(options.getUrl(), config, coercedOptions.getSslEngine(), listener);

//        return options.getPromise();
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
            default:
                throw new HttpClientException("Unable to construct request for:" + coercedOptions.getMethod() + ", " + coercedOptions.getUrl(), null);
        }
    }
}
