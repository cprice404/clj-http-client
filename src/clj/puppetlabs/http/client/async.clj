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
  (:import (com.puppetlabs.http.client HttpClientException ClientOptions)
           (org.apache.http.impl.nio.client HttpAsyncClients)
           (org.apache.http.client.methods HttpGet HttpHead HttpPost HttpPut HttpTrace HttpDelete HttpOptions HttpPatch)
           (org.apache.http.client.utils URIBuilder)
           (org.apache.http.concurrent FutureCallback)
           (org.apache.http.message BasicHeader)
           (org.apache.http Consts Header HttpRequest HttpResponse)
           (org.apache.http.nio.entity NStringEntity)
           (org.apache.http.entity InputStreamEntity ContentType)
           (java.io InputStream)
           (com.puppetlabs.http.client.impl Compression StreamingAsyncResponseConsumer Promise FnDeliverable)
           (org.apache.http.client RedirectStrategy)
           (org.apache.http.impl.client LaxRedirectStrategy DefaultRedirectStrategy)
           (org.apache.http.nio.conn.ssl SSLIOSessionStrategy)
           (org.apache.http.client.config RequestConfig)
           (org.apache.http.nio.client.methods HttpAsyncMethods AsyncCharConsumer AsyncByteConsumer)
           (org.apache.http.nio.client HttpAsyncClient)
           (clojure.lang IFn))
  (:require [puppetlabs.ssl-utils.core :as ssl]
            [clojure.string :as str]
            [puppetlabs.kitchensink.core :as ks]
            [puppetlabs.http.client.common :as common]
            [schema.core :as schema]
            [clojure.tools.logging :as log])
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

(schema/defn configure-ssl-ctxt :- (schema/either {} common/SslContextOptions)
  "Configures a request map to have an SSLContext. It will use an existing one
  (stored in :ssl-context) if already present, and will fall back to a set of
  PEM files (stored in :ssl-cert, :ssl-key, and :ssl-ca-cert) if those are present.
  If none of these are present this does not modify the request map."
  [opts :- common/SslOptions]
  (cond
    (:ssl-context opts) opts
    (every? opts [:ssl-cert :ssl-key :ssl-ca-cert]) (configure-ssl-from-pems opts)
    (:ssl-ca-cert opts) (configure-ssl-from-ca-pem opts)
    :else opts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private utility functions

(defn- add-accept-encoding-header
  [decompress-body? headers]
  (if (and decompress-body?
           (not (contains? headers "accept-encoding")))
    (assoc headers "accept-encoding"
                   (BasicHeader. "accept-encoding" "gzip, deflate"))
    headers))

(defn- add-content-type-header
  [content-type headers]
  (if content-type
    (assoc headers "content-type" (BasicHeader. "Content-Type"
                                                (.toString content-type)))
    headers))

(defn- prepare-headers
  [{:keys [headers decompress-body]} content-type]
  (->> headers
       (reduce
         (fn [acc [k v]]
           (assoc acc (str/lower-case k) (BasicHeader. k v)))
         {})
       (add-accept-encoding-header decompress-body)
       (add-content-type-header content-type)
       vals
       (into-array Header)))

(defn- parse-url
  [url query-params]
  (if (nil? query-params)
    url
    (let [uri-builder (reduce #(.addParameter %1 (key %2) (val %2))
                              (.clearParameters (URIBuilder. url))
                              query-params)]
      (.build uri-builder))))

(defn content-type
  [body {:keys [headers]}]
  (if-let [content-type-value (some #(when (= "content-type"
                                           (clojure.string/lower-case (key %)))
                                       (val %))
                                    headers)]
    ;; In the case when the caller provides the body as a string, and does not
    ;; specify a charset, we choose one for them.  There will always be _some_
    ;; charset used to encode the string, and in this case we choose UTF-8
    ;; (instead of letting the underlying Apache HTTP client library
    ;; choose ISO-8859-1) because UTF-8 is a more reasonable default.
    (let [content-type (ContentType/parse content-type-value)
          charset (.getCharset content-type)
          should-choose-charset? (and (string? body) (not charset))]
      (if should-choose-charset?
        (ContentType/create (.getMimeType content-type) Consts/UTF_8)
        content-type))))

(defn- coerce-opts
  [{:keys [url body query-params] :as opts}]
  (let [url          (parse-url url query-params)
        content-type (content-type body opts)]
    {:url     url
     :method  (clojure.core/get opts :method :get)
     :headers (prepare-headers opts content-type)
     :body    (cond
                (string? body) (if content-type
                                 (NStringEntity. body content-type)
                                 (NStringEntity. body))
                (instance? InputStream body) (InputStreamEntity. body)
                :else body)}))

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

(defn- get-resp-headers
  [http-response]
  (reduce
    (fn [acc h]
      (assoc acc (.. h getName toLowerCase) (.getValue h)))
    {}
    (.getAllHeaders http-response)))

(defmulti decompress (fn [resp] (get-in resp [:headers "content-encoding"])))

(defmethod decompress "gzip"
  [resp]
  (-> resp
      (ks/dissoc-in [:headers "content-encoding"])
      (update-in [:body] #(Compression/gunzip %))))

(defmethod decompress "deflate"
  [resp]
  (-> resp
     (ks/dissoc-in [:headers "content-encoding"])
     (update-in [:body] #(Compression/inflate %))))

(defmethod decompress nil
  [resp]
  resp)

(defn- parse-content-type
  [content-type-header]
  (if (empty? content-type-header)
    nil
    (let [content-type (ContentType/parse content-type-header)]
      {:mime-type (.getMimeType content-type)
       :charset   (.getCharset content-type)})))

(defmulti coerce-body-type (fn [resp] (get-in resp [:opts :as])))

(defmethod coerce-body-type :text
  [resp]
  (let [charset (or (get-in resp [:content-type-params :charset] "UTF-8"))]
    (assoc resp :body (if (:body resp)
                        (slurp (:body resp) :encoding charset)
                        ""))))

(schema/defn response-map
  [opts :- common/RequestOptions
   http-response]
  (log/info "building response map; RESPONSE TYPE: " (type http-response))
  (let [headers       (get-resp-headers http-response)
        orig-encoding (headers "content-encoding")]
    (log/info "OPTS:" (dissoc opts :early-response-callback))
    {:opts                  opts
     :orig-content-encoding orig-encoding
     :status                (.. http-response getStatusLine getStatusCode)
     :headers               headers
     :content-type          (parse-content-type (headers "content-type"))
     :body                  (when-let [entity (.getEntity http-response)]
                              (.getContent entity))}))

(schema/defn error-response :- common/ErrorResponse
  [opts :- common/UserRequestOptions
   e :- Exception]
  {:opts opts
   :error e})

(schema/defn callback-response :- common/Response
  [opts :- common/UserRequestOptions
   callback :- common/ResponseCallbackFn
   response :- common/Response]
  (if callback
    (try
      (callback response)
      (catch Exception e
        (log/info "FAILURE WHEN TRYING TO ISSUE CALLBACK:")
        (.printStackTrace e)
        (error-response opts e)))
    response))

(schema/defn deliver-result
  [result :- common/ResponsePromise
   opts :- common/UserRequestOptions
   callback :- common/ResponseCallbackFn
   response :- common/Response]
  (log/info "DELIVERING RESULT:" response)
  (deliver result (callback-response opts callback response)))

(schema/defn prepare-response
  [opts :- common/RequestOptions
   http-response :- HttpResponse]
  (cond-> (response-map opts http-response)
    (:decompress-body opts) (decompress)
    (not= :stream (:as opts)) (coerce-body-type)))

(schema/defn future-callback
  [client :- common/Client
   result :- common/ResponsePromise
   opts :- common/RequestOptions
   callback :- common/ResponseCallbackFn]
  (reify FutureCallback
    (completed [this http-response]
      (log/info "ORIG completed called, http-response:" http-response)
      (try
        (let [response (prepare-response opts http-response)]
          (deliver-result result opts callback response))
        (catch Exception e
          (log/warn e "Error when delivering response")
          (deliver-result result opts callback
                          (error-response opts e)))))
    (failed [this e]
      (log/info "FAILED:")
      (.printStackTrace e)
      (deliver-result result opts callback
                      (error-response opts e)))
    (cancelled [this]
      (log/info "CANCELLED")
      (deliver-result result opts callback
                      (error-response
                        opts
                        (HttpClientException. "Request cancelled"))))))

(schema/defn extract-ssl-opts :- common/SslOptions
  [opts :- common/ClientOptions]
  (select-keys opts [:ssl-context :ssl-ca-cert :ssl-cert :ssl-key]))

(schema/defn ^:always-validate ssl-strategy :- SSLIOSessionStrategy
  [ssl-ctxt-opts :- common/SslContextOptions
   ssl-prot-opts :- common/SslProtocolOptions]
  (SSLIOSessionStrategy.
    (:ssl-context ssl-ctxt-opts)
    (if (contains? ssl-prot-opts :ssl-protocols)
      (into-array String (:ssl-protocols ssl-prot-opts))
      ClientOptions/DEFAULT_SSL_PROTOCOLS)
    (if (contains? ssl-prot-opts :cipher-suites)
      (into-array String (:cipher-suites ssl-prot-opts)))
    SSLIOSessionStrategy/BROWSER_COMPATIBLE_HOSTNAME_VERIFIER))

(schema/defn ^:always-validate redirect-strategy :- RedirectStrategy
  [opts :- common/ClientOptions]
  (let [follow-redirects (:follow-redirects opts)
        force-redirects (:force-redirects opts)]
    (cond
      (and (not (nil? follow-redirects)) (not follow-redirects))
        (proxy [RedirectStrategy] []
          (isRedirected [req resp context]
            false)
          (getRedirect [req resp context]
            nil))
        force-redirects
          (LaxRedirectStrategy.)
        :else
          (DefaultRedirectStrategy.))))

(schema/defn request-config :- RequestConfig
  [connect-timeout-milliseconds :- (schema/maybe schema/Int)
   socket-timeout-milliseconds :- (schema/maybe schema/Int)]
  (let [request-config-builder (RequestConfig/custom)]
    (if connect-timeout-milliseconds
      (.setConnectTimeout request-config-builder
                          connect-timeout-milliseconds))
    (if socket-timeout-milliseconds
      (.setSocketTimeout request-config-builder
                         socket-timeout-milliseconds))
    (.build request-config-builder)))

(schema/defn ^:always-validate create-default-client :- common/Client
  [opts :- common/ClientOptions]
  (let [ssl-ctxt-opts   (configure-ssl-ctxt (extract-ssl-opts opts))
        ssl-prot-opts   (select-keys opts [:ssl-protocols :cipher-suites])
        client-builder  (HttpAsyncClients/custom)
        connect-timeout (:connect-timeout-milliseconds opts)
        socket-timeout  (:socket-timeout-milliseconds opts)
        client          (do (when (:ssl-context ssl-ctxt-opts)
                              (.setSSLStrategy client-builder
                                               (ssl-strategy
                                                 ssl-ctxt-opts ssl-prot-opts)))
                            (.setRedirectStrategy client-builder
                                                  (redirect-strategy opts))
                            (if (or connect-timeout socket-timeout)
                              (.setDefaultRequestConfig client-builder
                                                        (request-config
                                                          connect-timeout
                                                          socket-timeout)))
                            (.build client-builder))]
    (.start client)
    client))

(defn basic-execute
  [client request callback]
  (.execute client request callback))

(schema/defn streaming-execute
  [client :- HttpAsyncClient
   request :- HttpRequest
   opts :- common/RequestOptions
   callback :- FutureCallback
   complete-promise :- IFn
   early-response-callback :- IFn]
  (log/info "IT'S THE STREAMING OF THE FUTURE!!!!!!!!!!!!!!")
  ;; The 'Future'/'FutureCallback' model used by default with the apache async
  ;; http client library has a bit of a shortcoming in that you can't dereference
  ;; the future until the response has been read in its entirety, even though
  ;; there are useful things you can do with the rest of the response object
  ;; earlier than that.  In the Java API, you'd get around this by implementing
  ;; your own HttpAsyncResponseConsumer and doing stuff with it directly in
  ;; the member methods.  That's kind of nathty for a Clojure API, so what we're
  ;; doing here is to change the callback model a bit.
  ;;
  ;; We have our own ResponseConsumer, which will call the callback's `complete`
  ;; method as soon as its 'onEntityEnclosed' method is executed, which happens
  ;; after all of the header parsing but before any bytes have been read from
  ;; the response body.  This will, in turn, cause the Clojure promise to be
  ;; delivered, which will allow the consuming code to move on with processing
  ;; the response.  Reading data from the response body stream will block until
  ;; bytes are available or the end of the stream is reached.
  ;;
  ;; TODO:
  ;; 1. tests
  ;; 2. figure out what happens if the response has no body; does the 'onEntityEnclosed'
  ;;    method of the consumer get called at all?  If not, need some conditional
  ;;    logic that will call the `completed` method when the response is complete.
  ;; 3. error handling; in the old model, when you dereferenced the Clojure promise,
  ;;    you knew that the processing was complete, and the promise would either
  ;;    contain the successful response, or it would contain an error.  In this new
  ;;    model, it's theoretically possible to dereference the promise and *then*
  ;;    have a failure occur while the client is reading the rest of the response,
  ;;    and as of now, I don't know if there's any guarantee that that error will
  ;;    bubble up in any meaningful way.  We might need to separate this 'fancy-streaming'
  ;;    stuff out into new functions after all, rather than a feature flag, and then
  ;;    when people use it we could require them to pass in an error callback fn
  ;;    or something.
  (let [#_wrapped-promise #_(proxy [Promise] []
                               (deliver [this val] (deliver early-response-callback val)))
        consumer (StreamingAsyncResponseConsumer.
                   (FnDeliverable. (fn [http-response] (early-response-callback
                                                         (-> (prepare-response opts http-response)
                                                           (assoc :complete-promise complete-promise))))))
        ;; we still need a callback to pass to the client library.  The idea I had
        ;; here was to wrap the original callback and ignore the `completed` method,
        ;; since presumably that'll already have been called, but to still pass
        ;; through to the failed/cancelled methods.  However, this implementation
        ;; isn't going to actually do anything useful, because the implementation
        ;; of those methods is just going to try to deliver the Clojure promise,
        ;; which will already have been delivered (so, effectively, these callbacks
        ;; are a no-op as currently written here).  It seems like a promise is
        ;; not going to be a sufficient return type to use for both blocking
        ;; the caller until the response is built up enough for them to start working
        ;; on AND also give them a way to be notified if an error or cancellation
        ;; occurs afterward.
        ;wrapped-future-callback (reify
        ;                          FutureCallback
        ;                          (completed [this result]
        ;                            (log/info "completed has already been called; ignoring"))
        ;                          (failed [this ex]
        ;                            (.failed callback ex))
        ;                          (cancelled [this]
        ;                            (.cancelled callback)))
        ]
    (let [future-response (.execute client
                            (HttpAsyncMethods/create request)
                            consumer
                            callback)]
      (log/info "FUTURE RESPONSE:" future-response)
      ;(let [response (.get future-response)]
      ;  (log/info "GOT RESPONSE FROM FUTURE:" response))
      )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate request-with-client :- common/ResponsePromise
  "Issues an async HTTP request with the specified client and returns a promise
   object to which the value of
   `(callback {:opts _ :status _ :headers _ :body _})` or
   `(callback {:opts _ :error _})` will be delivered.

   When unspecified, `callback` is the identity function.

   opts:

   * :url
   * :method - the HTTP method (:get, :head, :post, :put, :delete, :trace,
               :options, :patch)
   * :headers - a map of headers
   * :body - the body; may be a String or any type supported by clojure's reader
   * :decompress-body - if `true`, an 'accept-encoding' header with a value of
        'gzip, deflate' will be added to the request, and the response will be
        automatically decompressed if it contains a recognized 'content-encoding'
        header.  defaults to `true`.
   * :as - used to control the data type of the response body.  Supported values
       are `:text` and `:stream`, which will return a `String` or an
       `InputStream`, respectively.  Defaults to `:stream`.
   * :query-params - used to set the query parameters of an http request"
  [opts :- common/RawUserRequestOptions
   callback :- common/ResponseCallbackFn
   client]
  (let [early-response-callback (clojure.core/get opts :early-response-callback)
        defaults {:headers {}
                  :body nil
                  :decompress-body true
                  :as :stream}
        opts (-> (merge defaults opts)
               (dissoc :early-response-callback))
        {:keys [method url body] :as coerced-opts} (coerce-opts opts)
        request (construct-request method url)
        complete-response-promise (promise)]
    (.setHeaders request (:headers coerced-opts))
    (when body
      (.setEntity request body))
    (let [callback (future-callback client complete-response-promise opts callback)]
      (if early-response-callback
        (streaming-execute client request opts callback
          complete-response-promise
          early-response-callback)
        (basic-execute client request callback)))

    complete-response-promise))

(schema/defn create-client :- (schema/protocol common/HTTPClient)
  "Creates a client to be used for making one or more HTTP requests.

   opts (base set):

   * :force-redirects - used to set whether or not the client should follow
       redirects on POST or PUT requests. Defaults to false.
   * :follow-redirects - used to set whether or  not the client should follow
       redirects in general. Defaults to true. If set to false, will override
       the :force-redirects setting.
   * :connect-timeout-milliseconds - maximum number of milliseconds that the
       client will wait for a connection to be established.  A value of zero is
       interpreted as infinite.  A negative value for or the absence of this
       option is interpreted as undefined (system default).
   * :socket-timeout-milliseconds - maximum number of milliseconds that the
       client will allow for no data to be available on the socket before
       closing the underlying connection, 'SO_TIMEOUT' in socket terms.  A
       timeout of zero is interpreted as an infinite timeout.  A negative value
       for or the absence of this setting is interpreted as undefined (system
       default).
   * :ssl-protocols - used to set the list of SSL protocols that the client
       could select from when talking to the server. Defaults to 'TLSv1',
       'TLSv1.1', and 'TLSv1.2'.
   * :cipher-suites - used to set the cipher suites that the client could
       select from when talking to the server. Defaults to the complete
       set of suites supported by the underlying language runtime.

   opts (ssl-specific where only one of the following combinations permitted):

   * :ssl-context - an instance of SSLContext

   OR

   * :ssl-cert - path to a PEM file containing the client cert
   * :ssl-key - path to a PEM file containing the client private key
   * :ssl-ca-cert - path to a PEM file containing the CA cert

   OR

   * :ssl-ca-cert - path to a PEM file containing the CA cert"
  [opts :- common/ClientOptions]
  (let [client (create-default-client opts)]
    (reify common/HTTPClient
      (get [this url] (common/get this url {}))
      (get [_ url opts] (request-with-client (assoc opts :method :get :url url) nil client))
      (head [this url] (common/head this url {}))
      (head [_ url opts] (request-with-client (assoc opts :method :head :url url) nil client))
      (post [this url] (common/post this url {}))
      (post [_ url opts] (request-with-client (assoc opts :method :post :url url) nil client))
      (put [this url] (common/put this url {}))
      (put [_ url opts] (request-with-client (assoc opts :method :put :url url) nil client))
      (delete [this url] (common/delete this url {}))
      (delete [_ url opts] (request-with-client (assoc opts :method :delete :url url) nil client))
      (trace [this url] (common/trace this url {}))
      (trace [_ url opts] (request-with-client (assoc opts :method :trace :url url) nil client))
      (options [this url] (common/options this url {}))
      (options [_ url opts] (request-with-client (assoc opts :method :options :url url) nil client))
      (patch [this url] (common/patch this url {}))
      (patch [_ url opts] (request-with-client (assoc opts :method :patch :url url) nil client))
      (close [_] (.close client)))))
