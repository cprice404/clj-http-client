(ns puppetlabs.http.client.async-plaintext-test
  (:import (com.puppetlabs.http.client Async RequestOptions ClientOptions)
           (org.apache.http.impl.nio.client HttpAsyncClients)
           (java.net URI SocketTimeoutException ServerSocket)
           (java.io ByteArrayInputStream StringReader BufferedInputStream PipedOutputStream PipedInputStream))
  (:require [clojure.test :refer :all]
            [puppetlabs.http.client.test-common :refer :all]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.testutils.bootstrap :as testutils]
            [puppetlabs.trapperkeeper.testutils.logging :as testlogging]
            [puppetlabs.trapperkeeper.testutils.webserver :as testwebserver]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :as jetty9]
            [puppetlabs.http.client.common :as common]
            [puppetlabs.http.client.async :as async]
            [schema.test :as schema-test]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(use-fixtures :once schema-test/validate-schemas)

(defn hello-world-app
  [_]
  {:status 200
   :body "Hello, World!"})

(tk/defservice test-web-service
  [[:WebserverService add-ring-handler]]
  (init [this context]
        (add-ring-handler hello-world-app "/hello")
        context))

(deftest persistent-async-client-test
  (testlogging/with-test-logging
    (testutils/with-app-with-config hello-world-app
    [jetty9/jetty9-service test-web-service]
    {:webserver {:port 10000}}
    (testing "java async client"
      (let [request-options (RequestOptions. (URI. "http://localhost:10000/hello/"))
            client-options (ClientOptions.)
            client (Async/createClient client-options)]
        (testing "HEAD request with persistent async client"
          (let [response (.head client request-options)]
            (is (= 200 (.getStatus (.deref response))))
            (is (= nil (.getBody (.deref response))))))
        (testing "GET request with persistent async client"
          (let [response (.get client request-options)]
            (is (= 200 (.getStatus (.deref response))))
            (is (= "Hello, World!" (slurp (.getBody (.deref response)))))))
        (testing "POST request with persistent async client"
          (let [response (.post client request-options)]
            (is (= 200 (.getStatus (.deref response))))
            (is (= "Hello, World!" (slurp (.getBody (.deref response)))))))
        (testing "PUT request with persistent async client"
          (let [response (.put client request-options)]
            (is (= 200 (.getStatus (.deref response))))
            (is (= "Hello, World!" (slurp (.getBody (.deref response)))))))
        (testing "DELETE request with persistent async client"
          (let [response (.delete client request-options)]
            (is (= 200 (.getStatus (.deref response))))
            (is (= "Hello, World!" (slurp (.getBody (.deref response)))))))
        (testing "TRACE request with persistent async client"
          (let [response (.trace client request-options)]
            (is (= 200 (.getStatus (.deref response))))
            (is (= "Hello, World!" (slurp (.getBody (.deref response)))))))
        (testing "OPTIONS request with persistent async client"
          (let [response (.options client request-options)]
            (is (= 200 (.getStatus (.deref response))))
            (is (= "Hello, World!" (slurp (.getBody (.deref response)))))))
        (testing "PATCH request with persistent async client"
          (let [response (.patch client request-options)]
            (is (= 200 (.getStatus (.deref response))))
            (is (= "Hello, World!" (slurp (.getBody (.deref response)))))))
        (testing "client closes properly"
          (.close client)
          (is (thrown? IllegalStateException
                       (.get client request-options))))))
    (testing "clojure async client"
      (let [client (async/create-client {})]
        (testing "HEAD request with persistent async client"
          (let [response (common/head client "http://localhost:10000/hello/")]
            (is (= 200 (:status @response)))
            (is (= nil (:body @response)))))
        (testing "GET request with persistent async client"
          (let [response (common/get client "http://localhost:10000/hello/")]
            (is (= 200 (:status @response)))
            (is (= "Hello, World!" (slurp (:body @response))))))
        (testing "POST request with persistent async client"
          (let [response (common/post client "http://localhost:10000/hello/")]
            (is (= 200 (:status @response)))
            (is (= "Hello, World!" (slurp (:body @response))))))
        (testing "PUT request with persistent async client"
          (let [response (common/put client "http://localhost:10000/hello/")]
            (is (= 200 (:status @response)))
            (is (= "Hello, World!" (slurp (:body @response))))))
        (testing "DELETE request with persistent async client"
          (let [response (common/delete client "http://localhost:10000/hello/")]
            (is (= 200 (:status @response)))
            (is (= "Hello, World!" (slurp (:body @response))))))
        (testing "TRACE request with persistent async client"
          (let [response (common/trace client "http://localhost:10000/hello/")]
            (is (= 200 (:status @response)))
            (is (= "Hello, World!" (slurp (:body @response))))))
        (testing "OPTIONS request with persistent async client"
          (let [response (common/options client "http://localhost:10000/hello/")]
            (is (= 200 (:status @response)))
            (is (= "Hello, World!" (slurp (:body @response))))))
        (testing "PATCH request with persistent async client"
          (let [response (common/patch client "http://localhost:10000/hello/")]
            (is (= 200 (:status @response)))
            (is (= "Hello, World!" (slurp (:body @response))))))
        (testing "client closes properly"
          (common/close client)
          (is (thrown? IllegalStateException
                       (common/get client
                                   "http://localhost:10000/hello/")))))))))

(deftest request-with-client-test
  (testlogging/with-test-logging
    (testutils/with-app-with-config hello-world-app
      [jetty9/jetty9-service test-web-service]
      {:webserver {:port 10000}}
      (let [client (HttpAsyncClients/createDefault)
            opts   {:method :get :url "http://localhost:10000/hello/"}]
        (.start client)
        (testing "GET request works with request-with-client"
          (let [response (async/request-with-client opts nil client)]
            (is (= 200 (:status @response)))
            (is (= "Hello, World!" (slurp (:body @response))))))
        (testing "Client persists when passed to request-with-client"
          (let [response (async/request-with-client opts nil client)]
            (is (= 200 (:status @response)))
            (is (= "Hello, World!" (slurp (:body @response))))))
        (.close client)))))

(deftest query-params-test-async
  (testlogging/with-test-logging
    (testutils/with-app-with-config hello-world-app
      [jetty9/jetty9-service test-params-web-service]
      {:webserver {:port 8080}}
        (testing "URL Query Parameters work with the Java client"
          (let [client (Async/createClient (ClientOptions.))]
            (try
              (let [request-options (RequestOptions.
                                      (URI. "http://localhost:8080/params?foo=bar&baz=lux"))
                    response        (.get client request-options)]
                (is (= 200 (.getStatus (.deref response))))
                (is (= queryparams (read-string (slurp (.getBody
                                                         (.deref response)))))))
              (finally
                (.close client)))))
        (testing "URL Query Parameters work with the clojure client"
          (with-open [client (async/create-client {})]
            (let [opts     {:method       :get
                            :url          "http://localhost:8080/params/"
                            :query-params queryparams
                            :as           :text}
                  response (common/get client "http://localhost:8080/params" opts)]
                (is (= 200 (:status @response)))
                (is (= queryparams (read-string (:body @response)))))))
        (testing "URL Query Parameters can be set directly in the URL"
          (with-open [client (async/create-client {})]
            (let [response (common/get client
                                       "http://localhost:8080/params?paramone=one"
                                       {:as :text})]
              (is (= 200 (:status @response)))
              (is (= (str {"paramone" "one"}) (:body @response))))))
        (testing (str "URL Query Parameters set in URL are overwritten if params "
                      "are also specified in options map")
          (with-open [client (async/create-client {})]
            (let [response (common/get client
                                       "http://localhost:8080/params?paramone=one&foo=lux"
                                       query-options)]
              (is (= 200 (:status @response)))
              (is (= queryparams (read-string (:body @response))))))))))

(deftest redirect-test-async
  (testlogging/with-test-logging
    (testutils/with-app-with-config hello-world-app
      [jetty9/jetty9-service redirect-web-service]
      {:webserver {:port 8080}}
      (testing (str "redirects on POST not followed by persistent Java client "
                    "when forceRedirects option not set to true")
        (let [client (Async/createClient (ClientOptions.))]
          (try
            (let [request-options  (RequestOptions.
                                     (URI. "http://localhost:8080/hello"))
                  response         (.post client request-options)]
              (is (= 302 (.getStatus (.deref response)))))
            (finally
              (.close client)))))
      (testing "redirects on POST followed by Java client when option is set"
        (let [client (Async/createClient (.. (ClientOptions.)
                                             (setForceRedirects true)))]
          (try
            (let [request-options (RequestOptions.
                                    (URI. "http://localhost:8080/hello"))
                  response        (.post client request-options)]
              (is (= 200 (.getStatus (.deref response))))
              (is (= "Hello, World!" (slurp (.getBody (.deref response))))))
            (finally
              (.close client)))))
      (testing "redirects not followed by Java client when :follow-redirects is false"
        (let [client (Async/createClient (.. (ClientOptions.)
                                             (setFollowRedirects false)))]
          (try
            (let [request-options (RequestOptions.
                                    (URI. "http://localhost:8080/hello"))
                  response        (.get client request-options)]
              (is (= 302 (.getStatus (.deref response)))))
            (finally
              (.close client)))))
      (testing ":follow-redirects overrides :force-redirects for Java client"
        (let [client (Async/createClient (.. (ClientOptions.)
                                             (setFollowRedirects false)
                                             (setForceRedirects true)))]
          (try
            (let [request-options (RequestOptions.
                                    (URI. "http://localhost:8080/hello"))
                  response        (.get client request-options)]
              (is (= 302 (.getStatus (.deref response)))))
            (finally
              (.close client)))))
      (testing (str "redirects on POST not followed by clojure client "
                    "when :force-redirects is not set to true")
        (with-open [client (async/create-client {:force-redirects false})]
          (let [opts     {:method :post
                          :url    "http://localhost:8080/hello"
                          :as     :text}
                response (common/post client "http://localhost:8080/hello" opts)]
            (is (= 302 (:status @response))))))
      (testing (str "redirects on POST followed by persistent clojure client "
                    "when option is set")
        (with-open [client (async/create-client {:force-redirects true})]
          (let [response (common/post client
                                      "http://localhost:8080/hello"
                                      {:as :text})]
            (is (= 200 (:status @response)))
            (is (= "Hello, World!" (:body @response))))))
      (testing (str "persistent clojure client does not follow redirects when "
                    ":follow-redirects is set to false")
        (with-open [client (async/create-client {:follow-redirects false})]
          (let [response (common/get client
                                     "http://localhost:8080/hello"
                                     {:as :text})]
            (is (= 302 (:status @response))))))
      (testing ":follow-redirects overrides :force-redirects with persistent clj client"
        (with-open [client (async/create-client {:follow-redirects false
                                                 :force-redirects true})]
          (let [response (common/get client
                                     "http://localhost:8080/hello"
                                     {:as :text})]
            (is (= 302 (:status @response)))))))))

(deftest short-connect-timeout-persistent-java-test-async
  (testing (str "connection times out properly for java persistent client "
                "async request with short timeout")
    (with-open [client (-> (ClientOptions.)
                           (.setConnectTimeoutMilliseconds 250)
                           (Async/createClient))]
      (let [request-options     (RequestOptions. "http://127.0.0.255:65535")
            time-before-connect (System/currentTimeMillis)]
        (is (connect-exception-thrown? (-> client
                                           (.get request-options)
                                           (.deref)
                                           (.getError)))
            "Unexpected result for connection attempt")
        (is (elapsed-within-range? time-before-connect 2000)
            "Connection attempt took significantly longer than timeout")))))

(deftest short-connect-timeout-persistent-clojure-test-async
  (testing (str "connection times out properly for clojure persistent client "
                "async request with short timeout")
    (with-open [client (async/create-client
                         {:connect-timeout-milliseconds 250})]
      (let [time-before-connect (System/currentTimeMillis)]
        (is (connect-exception-thrown? (-> @(common/get
                                              client
                                              "http://127.0.0.255:65535")
                                           :error))
            "Unexpected result for connection attempt")
        (is (elapsed-within-range? time-before-connect 2000)
            "Connection attempt took significantly longer than timeout")))))

(deftest longer-connect-timeout-test-async
  (testing "connection succeeds for async request with longer connect timeout"
    (testlogging/with-test-logging
      (testwebserver/with-test-webserver hello-world-app port
        (let [url (str "http://localhost:" port "/hello")]
          (testing "java persistent async client"
            (with-open [client (-> (ClientOptions.)
                                   (.setConnectTimeoutMilliseconds 2000)
                                   (Async/createClient))]
              (let [response (-> client
                                 (.get (RequestOptions. url))
                                 (.deref))]
                (is (= 200 (.getStatus response)))
                (is (= "Hello, World!" (slurp (.getBody response)))))))
          (testing "clojure persistent async client"
            (with-open [client (async/create-client
                                 {:connect-timeout-milliseconds 2000})]
              (let [response @(common/get client url {:as :text})]
                (is (= 200 (:status response)))
                (is (= "Hello, World!" (:body response)))))))))))

(deftest short-socket-timeout-persistent-java-test-async
  (testing (str "socket read times out properly for persistent java async "
                "request with short timeout")
    (with-open [client (-> (ClientOptions.)
                           (.setSocketTimeoutMilliseconds 1)
                           (Async/createClient))
                server (ServerSocket. 0)]
      (let [request-options     (-> "http://127.0.0.1:"
                                    (str (.getLocalPort server))
                                    (RequestOptions.))
            time-before-connect (System/currentTimeMillis)]
        (is (instance? SocketTimeoutException (-> client
                                                  (.get request-options)
                                                  (.deref)
                                                  (.getError)))
            "Unexpected result for get attempt")
        (is (elapsed-within-range? time-before-connect 2000)
            "Get attempt took significantly longer than timeout")))))

(deftest short-socket-timeout-persistent-clojure-test-async
  (testing (str "socket read times out properly for clojure persistent client "
                "async request with short timeout")
    (with-open [client (async/create-client
                         {:socket-timeout-milliseconds 250})
                server (ServerSocket. 0)]
      (let [url                 (str "http://127.0.0.1:" (.getLocalPort server))
            time-before-connect (System/currentTimeMillis)]
        (is (instance? SocketTimeoutException
                       (-> @(common/get client url)
                           :error))
            "Unexpected result for get attempt")
        (is (elapsed-within-range? time-before-connect 2000)
            "Get attempt took significantly longer than timeout")))))

(deftest longer-socket-timeout-test-async
  (testing "get succeeds for async request with longer socket timeout"
    (testlogging/with-test-logging
      (testwebserver/with-test-webserver hello-world-app port
        (let [url (str "http://localhost:" port "/hello")]
          (testing "java persistent async client"
            (with-open [client (-> (ClientOptions.)
                                   (.setSocketTimeoutMilliseconds 2000)
                                   (Async/createClient))]
              (let [response (-> client
                                 (.get (RequestOptions. url))
                                 (.deref))]
                (is (= 200 (.getStatus response)))
                (is (= "Hello, World!" (slurp (.getBody response)))))))
          (testing "clojure persistent async client"
            (with-open [client (async/create-client
                                 {:socket-timeout-milliseconds 2000})]
              (let [response @(common/get client url {:as :text})]
                (is (= 200 (:status response)))
                (is (= "Hello, World!" (:body response)))))))))))

(deftest streamed-content-test-async
  (testing "can read bytes from stream before response is complete"
    (let [big-string (apply str (repeat (* 1024 1024) "f"))

          initial-bytes-read? (promise)
          ;instream (ByteArrayInputStream. (.getBytes "foo" "UTF-8"))
          outstream (PipedOutputStream.)
          instream (PipedInputStream.)
          _ (.connect instream outstream)
          outwriter (io/make-writer outstream {})
          _ (future
              (println "WRITING STUFF TO STREAM")
              (.write outwriter big-string)
              ;(.flush outwriter)
              ;(.flush outstream)
              ;(.flush instream)
              (println "BLOCKING ON PROMISE")
              @initial-bytes-read?
              (println "DONE BLOCKING ON PROMISE")
              ;(println "SLEEPING")
              ;(Thread/sleep 5000)
              (println "WRITING MORE STUFF TO STREAM")
              (.write outwriter "BAR")
              (println "CLOSING STREAM")
              (.close outwriter))
          streamed-response-handler (fn [req]
                                      (println "RETURNING RESPONSE MAP")
                                      {:status 200
                                       :body instream})]
      (testlogging/with-test-logging
        (testwebserver/with-test-webserver streamed-response-handler port
          (let [url (str "http://localhost:" port "/hello")]
            (log/info "URL:" url)
            (testing "clojure persistent async client"
              (with-open [client (async/create-client {})]
                (log/info "!!!!!!!!!!!!!!!! About to make request")
                (let [early-response-callback (promise)
                      complete-promise (common/get client url {:as :stream
                                                               :decompress-body false
                                                               :early-response-callback early-response-callback})
                      _ (log/info "GOT COMPLETE PROMISE:" complete-promise)
                      _ (log/info "WAITING FOR EARLY RESPONSE PROMISE")
                      response @early-response-callback]
                  (log/info "EARLY RESPONSE PROMISE DELIVERED")
                  (if-let [ex (:error response)]
                    (throw ex))
                  (is (= (:complete-promise response) complete-promise))
                  (log/info "!!!!!!!!!!!!!! Got response.")
                  (log/info " response keys: " (keys response))
                  (is (= 200 (:status response)))
                  (let [instream #spy/d (:body response)
                        buf (make-array Byte/TYPE 3)
                        _ (.read instream buf)]
                    ;; make sure we can read a few chars off of the stream.  there
                    ;; may be buffering going on in various layers, so we won't
                    ;; make any assertions about being able to read the entirety
                    ;; of the original 'big-string', yet.
                    (is (= "fff" (String. buf "UTF-8")))
                    (deliver initial-bytes-read? true)
                    (let [final-string (str "fff" (slurp instream))]
                      (is (= (str big-string "BAR") final-string)))
                    (log/info "TEST DONE READING BODY.")

                    @complete-promise
                    (log/info "DEREFERENCED RESPONSE PROMISE!")
                    (.close client)))))))
      )
    )))







