package com.puppetlabs.http.client;

import com.puppetlabs.certificate_authority.CertificateAuthority;
import com.puppetlabs.http.client.impl.JavaClient;
import com.puppetlabs.http.client.impl.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

public class SyncHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyncHttpClient.class);


    private static void logAndRethrow(String msg, Throwable t) {
        LOGGER.error(msg, t);
        throw new HttpClientException(msg, t);
    }

    // TODO: move this into the async java API if we ever add one
    private static RequestOptions configureSsl(RequestOptions options) {
        if (options.getSslContext() != null) {
            return options;
        }

        if ((options.getSslCert() != null) &&
                (options.getSslKey() != null) &&
                (options.getSslCaCert() != null)) {
            try {
                options.setSslContext(
                        CertificateAuthority.pemsToSSLContext(
                                new FileReader(options.getSslCert()),
                                new FileReader(options.getSslKey()),
                                new FileReader(options.getSslCaCert()))
                );
            } catch (KeyStoreException e) {
                logAndRethrow("Error while configuring SSL", e);
            } catch (CertificateException e) {
                logAndRethrow("Error while configuring SSL", e);
            } catch (IOException e) {
                logAndRethrow("Error while configuring SSL", e);
            } catch (NoSuchAlgorithmException e) {
                logAndRethrow("Error while configuring SSL", e);
            } catch (KeyManagementException e) {
                logAndRethrow("Error while configuring SSL", e);
            } catch (UnrecoverableKeyException e) {
                logAndRethrow("Error while configuring SSL", e);
            }
            options.setSslCert(null);
            options.setSslKey(null);
            options.setSslCaCert(null);
            return options;
        }

        if (options.getSslCaCert() != null) {
            try {
                options.setSslContext(
                        CertificateAuthority.caCertPemToSSLContext(
                                new FileReader(options.getSslCaCert()))
                );
            } catch (KeyStoreException e) {
                logAndRethrow("Error while configuring SSL", e);
            } catch (CertificateException e) {
                logAndRethrow("Error while configuring SSL", e);
            } catch (IOException e) {
                logAndRethrow("Error while configuring SSL", e);
            } catch (NoSuchAlgorithmException e) {
                logAndRethrow("Error while configuring SSL", e);
            } catch (KeyManagementException e) {
                logAndRethrow("Error while configuring SSL", e);
            }
            options.setSslCaCert(null);
            return options;
        }

        return options;
    }

    public static Response request(RequestOptions options) {
        // TODO: if we end up implementing an async version of the java API,
        // we should refactor this implementation so that it is based on the
        // async one, as Patrick has done in the clojure API.

        options = configureSsl(options);

        Promise<Response> promise =  JavaClient.request(options, null);

        Response response = null;
        try {
            response = promise.deref();
        } catch (InterruptedException e) {
            logAndRethrow("Error while waiting for http response", e);
        }
        if (response.getError() != null) {
            logAndRethrow("Error executing http request", response.getError());
        }
        return response;
    }


    public static Response get(String url) {
        return get(new RequestOptions(url));
    }
    public static Response get(RequestOptions requestOptions) {
        return request(requestOptions.setMethod(HttpMethod.GET));
    }

    public static Response head(String url) {
        return head(new RequestOptions(url));
    }
    public static Response head(RequestOptions requestOptions) {
        return request(requestOptions.setMethod(HttpMethod.HEAD));
    }

    public static Response post(String url) {
        return post(new RequestOptions(url));
    }
    public static Response post(RequestOptions requestOptions) {
        return request(requestOptions.setMethod(HttpMethod.POST));
    }

    public static Response put(String url) {
        return put(new RequestOptions(url));
    }
    public static Response put(RequestOptions requestOptions) {
        return request(requestOptions.setMethod(HttpMethod.PUT));
    }

    public static Response delete(String url) {
        return delete(new RequestOptions(url));
    }
    public static Response delete(RequestOptions requestOptions) {
        return request(requestOptions.setMethod(HttpMethod.DELETE));
    }

    public static Response trace(String url) {
        return trace(new RequestOptions(url));
    }
    public static Response trace(RequestOptions requestOptions) {
        return request(requestOptions.setMethod(HttpMethod.TRACE));
    }

    public static Response options(String url) {
        return options(new RequestOptions(url));
    }
    public static Response options(RequestOptions requestOptions) {
        return request(requestOptions.setMethod(HttpMethod.OPTIONS));
    }

    public static Response patch(String url) {
        return patch(new RequestOptions(url));
    }
    public static Response patch(RequestOptions requestOptions) {
        return request(requestOptions.setMethod(HttpMethod.PATCH));
    }
}
