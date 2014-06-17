(ns puppetlabs.http.client.sync-plaintext-test
  (:import (com.puppetlabs.http.client SyncHttpClient RequestOptions
                                       HttpClientException)
           (javax.net.ssl SSLHandshakeException))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.testutils.bootstrap :as testutils]
            [puppetlabs.trapperkeeper.testutils.logging :as testlogging]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :as jetty9]
            [puppetlabs.http.client.sync :as sync]))

(defn app
  [req]
  {:status 200
   :body "Hello, World!"})

(tk/defservice test-web-service
  [[:WebserverService add-ring-handler]]
  (init [this context]
        (add-ring-handler app "/hello")
        context))

(defn basic-test
  [http-method java-method clj-fn]
  (testing (format "sync client: HTTP method: '%s'" http-method)
    (testlogging/with-test-logging
      (testutils/with-app-with-config app
        [jetty9/jetty9-service test-web-service]
        {:webserver {:port 10000}}
        (testing "java sync client"
          (let [options (RequestOptions. "http://localhost:10000/hello/")
                response (java-method options)]
            (is (= 200 (.getStatus response)))
            (is (= "Hello, World!" (slurp (.getBody response))))))
        (testing "clojure sync client"
          (let [response (clj-fn "http://localhost:10000/hello/")]
            (is (= 200 (:status response)))
            (is (= "Hello, World!" (slurp (:body response))))))))))

(deftest sync-client-head-test
  (testlogging/with-test-logging
    (testutils/with-app-with-config app
      [jetty9/jetty9-service test-web-service]
      {:webserver {:port 10000}}
      (testing "java sync client"
        (let [options (RequestOptions. "http://localhost:10000/hello/")
              response (SyncHttpClient/head options)]
          (is (= 200 (.getStatus response)))
          (is (= nil (.getBody response)))))
      (testing "clojure sync client"
        (let [response (sync/head "http://localhost:10000/hello/")]
          (is (= 200 (:status response)))
          (is (= nil (:body response))))))))

(deftest sync-client-get-test
  (basic-test "GET" #(SyncHttpClient/get %) sync/get))

(deftest sync-client-post-test
  (basic-test "POST" #(SyncHttpClient/post %) sync/post))

(deftest sync-client-put-test
  (basic-test "PUT" #(SyncHttpClient/put %) sync/put))

(deftest sync-client-delete-test
  (basic-test "DELETE" #(SyncHttpClient/delete %) sync/delete))

(deftest sync-client-trace-test
  (basic-test "TRACE" #(SyncHttpClient/trace %) sync/trace))

(deftest sync-client-options-test
  (basic-test "OPTIONS" #(SyncHttpClient/options %) sync/options))

(deftest sync-client-patch-test
  (basic-test "PATCH" #(SyncHttpClient/patch %) sync/patch))

(defn header-app
  [req]
  (let [val (get-in req [:headers "fooheader"])]
    {:status  200
     :headers {"myrespheader" val}
     :body    val}))

(tk/defservice test-header-web-service
  [[:WebserverService add-ring-handler]]
  (init [this context]
       (add-ring-handler header-app "/hello")
       context))

(deftest sync-client-request-headers-test
  (testlogging/with-test-logging
    (testutils/with-app-with-config header-app
      [jetty9/jetty9-service test-header-web-service]
      {:webserver {:port 10000}}
      (testing "java sync client"
        (let [options (-> (RequestOptions. "http://localhost:10000/hello/")
                          (.setHeaders {"fooheader" "foo"}))
              response (SyncHttpClient/post options)]
          (is (= 200 (.getStatus response)))
          (is (= "foo" (slurp (.getBody response))))
          (is (= "foo" (-> (.getHeaders response) (.get "myrespheader"))))))
      (testing "clojure sync client"
        (let [response (sync/post "http://localhost:10000/hello/" {:headers {"fooheader" "foo"}})]
          (is (= 200 (:status response)))
          (is (= "foo" (slurp (:body response))))
          (is (= "foo" (get-in response [:headers "myrespheader"]))))))))