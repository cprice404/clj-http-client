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

(deftest sync-client-get-test
  (testlogging/with-test-logging
    (testutils/with-app-with-config app
       [jetty9/jetty9-service test-web-service]
       {:webserver {:port 10000}}
       (testing "java sync client"
         (let [options (RequestOptions. "http://localhost:10000/hello/")
               response (SyncHttpClient/get options)]
           (is (= 200 (.getStatus response)))
           (is (= "Hello, World!" (slurp (.getBody response))))))
       (testing "clojure sync client"
         (let [response (sync/get "http://localhost:10000/hello/")]
           (is (= 200 (:status response)))
           (is (= "Hello, World!" (slurp (:body response)))))))))

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

(deftest sync-client-post-test
  (testlogging/with-test-logging
    (testutils/with-app-with-config app
      [jetty9/jetty9-service test-web-service]
      {:webserver {:port 10000}}
      (testing "java sync client"
        (let [options (RequestOptions. "http://localhost:10000/hello/")
              response (SyncHttpClient/post options)]
          (is (= 200 (.getStatus response)))
          (is (= "Hello, World!" (slurp (.getBody response))))))
      (testing "clojure sync client"
        (let [response (sync/post "http://localhost:10000/hello/")]
          (is (= 200 (:status response)))
          (is (= "Hello, World!" (slurp (:body response)))))))))

(deftest sync-client-put-test
  (testlogging/with-test-logging
    (testutils/with-app-with-config app
      [jetty9/jetty9-service test-web-service]
      {:webserver {:port 10000}}
      (testing "java sync client"
        (let [options (RequestOptions. "http://localhost:10000/hello/")
              response (SyncHttpClient/put options)]
          (is (= 200 (.getStatus response)))
          (is (= "Hello, World!" (slurp (.getBody response))))))
      (testing "clojure sync client"
        (let [response (sync/put "http://localhost:10000/hello/")]
          (is (= 200 (:status response)))
          (is (= "Hello, World!" (slurp (:body response)))))))))

(deftest sync-client-delete-test
  (testlogging/with-test-logging
    (testutils/with-app-with-config app
      [jetty9/jetty9-service test-web-service]
      {:webserver {:port 10000}}
      (testing "java sync client"
        (let [options (RequestOptions. "http://localhost:10000/hello/")
              response (SyncHttpClient/delete options)]
          (is (= 200 (.getStatus response)))
          (is (= "Hello, World!" (slurp (.getBody response))))))
      (testing "clojure sync client"
        (let [response (sync/delete "http://localhost:10000/hello/")]
          (is (= 200 (:status response)))
          (is (= "Hello, World!" (slurp (:body response)))))))))

(deftest sync-client-trace-test
  (testlogging/with-test-logging
    (testutils/with-app-with-config app
      [jetty9/jetty9-service test-web-service]
      {:webserver {:port 10000}}
      (testing "java sync client"
        (let [options (RequestOptions. "http://localhost:10000/hello/")
              response (SyncHttpClient/trace options)]
          (is (= 200 (.getStatus response)))
          (is (= "Hello, World!" (slurp (.getBody response))))))
      (testing "clojure sync client"
        (let [response (sync/trace "http://localhost:10000/hello/")]
          (is (= 200 (:status response)))
          (is (= "Hello, World!" (slurp (:body response)))))))))

(deftest sync-client-options-test
  (testlogging/with-test-logging
    (testutils/with-app-with-config app
      [jetty9/jetty9-service test-web-service]
      {:webserver {:port 10000}}
      (testing "java sync client"
        (let [options (RequestOptions. "http://localhost:10000/hello/")
              response (SyncHttpClient/options options)]
          (is (= 200 (.getStatus response)))
          (is (= "Hello, World!" (slurp (.getBody response))))))
      (testing "clojure sync client"
        (let [response (sync/options "http://localhost:10000/hello/")]
          (is (= 200 (:status response)))
          (is (= "Hello, World!" (slurp (:body response)))))))))