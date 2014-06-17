;; This namespace is a wrapper around the http client functionality provided
;; by Apache HttpAsyncClient. It allows the use of PEM files for HTTPS configuration.
;;
;; In the options to any request method, an existing SSLContext object can be
;; supplied under :ssl-context. If this is present it will be used. If it's
;; not, the wrapper will attempt to use a set of PEM files stored in
;; (:ssl-cert :ssl-key :ssl-ca-cert) to create the SSLContext. It is also
;; still possible to use an SSLEngine directly, and if this is present under
;; the key :sslengine it will be used before any other options are tried.
;; 
;; See the puppetlabs.http.sync namespace for synchronous versions of all
;; these methods.

(ns puppetlabs.http.client.async
  (:import (com.puppetlabs.http.client HttpMethod HttpClientException)
           (org.apache.http.nio.client HttpAsyncClient)
           (org.apache.http.impl.nio.client HttpAsyncClients)
           (org.apache.http.client.methods HttpGet HttpHead HttpPost HttpPut HttpTrace HttpDelete HttpOptions HttpPatch)
           (org.apache.http.concurrent FutureCallback)
           (org.apache.http.message BasicHeader)
           (org.apache.http Header))
  (:require [puppetlabs.certificate-authority.core :as ssl])
  (:refer-clojure :exclude (get)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private SSL configuration functions

(defn- initialize-ssl-context-from-pems
  [req]
  (-> req
    (assoc :ssl-context (ssl/pems->ssl-context
                          (:ssl-cert req)
                          (:ssl-key req)
                          (:ssl-ca-cert req)))
    (dissoc :ssl-cert :ssl-key :ssl-ca-cert)))

(defn- initialize-ssl-context-from-ca-pem
  [req]
  (-> req
      (assoc :ssl-context (ssl/ca-cert-pem->ssl-context
                            (:ssl-ca-cert req)))
      (dissoc :ssl-ca-cert)))

(defn- configure-ssl-from-pems
  "Configures an SSLEngine in the request starting from a set of PEM files"
  [req]
  (initialize-ssl-context-from-pems req))

(defn- configure-ssl-from-ca-pem
  "Configures an SSLEngine in the request starting from a CA PEM file"
  [req]
  (initialize-ssl-context-from-ca-pem req))

(defn configure-ssl
  "Configures a request map to have an SSLContext. It will use an existing one
  (stored in :ssl-context) if already present, and will fall back to a set of
  PEM files (stored in :ssl-cert, :ssl-key, and :ssl-ca-cert) if those are present.
  If none of these are present this does not modify the request map."
  [req]
  (cond
    (:ssl-context req) req
    (every? (partial req) [:ssl-cert :ssl-key :ssl-ca-cert]) (configure-ssl-from-pems req)
    (:ssl-ca-cert req) (configure-ssl-from-ca-pem req)
    :else req))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private utility functions

(defn- check-url!
  [url]
  (when (nil? url)
    (throw (IllegalArgumentException. "Host URL cannot be nil"))))

(defn prepare-headers
  [headers]
  (into-array
    Header
    (map #(BasicHeader. (first %) (second %)) headers)))

(defn- coerce-opts
  [opts]
  {:url     (:url opts)
   :method  (clojure.core/get opts :method :get)
   :headers (prepare-headers (:headers opts))
   :body    (:body opts)})

(defn- construct-request
  [method url]
  (condp = method
    :get    (HttpGet. url)
    :head   (HttpHead. url)
    :post   (HttpPost. url)
    :put    (HttpPut. url)
    :delete (HttpDelete. url)
    :trace  (HttpTrace. url)
    :options (HttpOptions. url)
    :patch  (HttpPatch. url)
    (throw (IllegalArgumentException. (format "Unsupported request method: %s" method)))))

(defn get-resp-headers
  [http-response]
  (reduce
    (fn [acc h]
      (assoc acc (.getName h) (.getValue h)))
    {}
    (.getAllHeaders http-response)))

(defn- response-map
  [opts http-response]
  {:opts    opts
   :status  (.. http-response getStatusLine getStatusCode)
   :headers (get-resp-headers http-response)
   :body    (when-let [entity (.getEntity http-response)]
              (.getContent entity))})

(defn- deliver-result
  [client result opts callback response]
  (try
    (deliver result
             (if callback
               (try
                 (callback response)
                 (catch Exception e
                   {:opts  opts
                    :error e}))
              response))
    (finally
      (.close client))))

(defn- future-callback
  [client result opts callback]
  (reify FutureCallback
    (completed [this http-response]
      (let [response (response-map opts http-response)]
        (deliver-result client result opts callback response)))
    (failed [this e]
      (deliver-result client result opts callback
                      {:opts opts :error e}))
    (cancelled [this]
      (deliver-result client result opts callback
                      {:opts  opts
                       :error (HttpClientException. "Request cancelled")}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn create-client
  [opts]
  (let [opts    (configure-ssl opts)
        client  (if (:ssl-context opts)
                  (.. (HttpAsyncClients/custom) (setSSLContext (:ssl-context opts)) build)
                  (HttpAsyncClients/createDefault))]
    (.start client)
    client))


(defn request
  "Issues an async HTTP request and returns a promise object to which the value
  of `(callback {:opts _ :status _ :headers _ :body _})` or
     `(callback {:opts _ :error _})` will be delivered.

  When unspecified, `callback` is the identity function.

  Request options:

  * :url
  * :method - the HTTP method (:get, :head, :post, :put, :delete, :options, :patch
  * :headers - a map of headers
  * :body - the body; may be a String or any type supported by clojure's reader

  SSL options:

  * :ssl-context - an instance of SSLContext

  OR

  * :ssl-cert - path to a PEM file containing the client cert
  * :ssl-key - path to a PEM file containing the client private key
  * :ssl-ca-cert - path to a PEM file containing the CA cert"
  [opts callback]
  (check-url! (:url opts))
  (let [client        (create-client opts)
        {:keys [method url] :as coerced-opts} (coerce-opts opts)
        request       (construct-request method url)
        result        (promise)]
    (.setHeaders request (:headers coerced-opts))
    (.execute client request
              (future-callback client result opts callback))
    result))

(defn- wrap-with-ssl-config
  [method]
  (fn wrapped-fn
    ([url]
     (wrapped-fn url {} nil))

    ([url callback-or-opts]
     (if (map? callback-or-opts)
       (wrapped-fn url callback-or-opts nil)
       (wrapped-fn url {} callback-or-opts)))

    ([url opts callback]
     (check-url! url)
     (method url (configure-ssl opts) callback))))

(def ^{:arglists '([url] [url callback-or-opts] [url opts callback])} get
  "Issue an async HTTP GET request."
  (wrap-with-ssl-config :get))

(def ^{:arglists '([url] [url callback-or-opts] [url opts callback])} head
  "Issue an async HTTP HEAD request."
  (wrap-with-ssl-config :head))

(def ^{:arglists '([url] [url callback-or-opts] [url opts callback])} post
  "Issue an async HTTP POST request."
  (wrap-with-ssl-config :post))

(def ^{:arglists '([url] [url callback-or-opts] [url opts callback])} put
  "Issue an async HTTP PUT request."
  (wrap-with-ssl-config :put))

(def ^{:arglists '([url] [url callback-or-opts] [url opts callback])} delete
  "Issue an async HTTP DELETE request."
  (wrap-with-ssl-config :delete))

(def ^{:arglists '([url] [url callback-or-opts] [url opts callback])} options
  "Issue an async HTTP OPTIONS request."
  (wrap-with-ssl-config :options))

(def ^{:arglists '([url] [url callback-or-opts] [url opts callback])} patch
  "Issue an async HTTP PATCH request."
  (wrap-with-ssl-config :patch))
