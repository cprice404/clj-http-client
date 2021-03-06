(ns puppetlabs.http.client.async-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :refer [resource]] 
            [puppetlabs.kitchensink.ssl :as ks-ssl]
            [puppetlabs.http.client.async :as http])
  (:import [javax.net.ssl SSLEngine]))

(deftest ssl-config-with-files
  (let [req {:url "http://localhost"
             :method :get
             :ssl-cert (resource "resources/cert.pem")
             :ssl-key (resource "resources/key.pem")
             :ssl-ca-cert (resource "resources/ca.pem")}
        configured-req (http/configure-ssl req)]

    (testing "configure-ssl sets up an SSLEngine when given cert, key, ca-cert"
      (is (instance? SSLEngine (:sslengine configured-req))))

    (testing "removes ssl-cert, ssl-key, ssl-ca-cert"
      (is (not (:ssl-cert configured-req)))
      (is (not (:ssl-key configured-req)))
      (is (not (:ssl-ca-cert configured-req))))))

(deftest ssl-config-without-ssl-params
  (let [req {:url "http://localhost"
             :method :get}
        configured-req (http/configure-ssl req)]

    (testing "configure-ssl does nothing when given no ssl parameters"
      (is (= req configured-req)))))

(deftest ssl-config-with-context
  (let [req {:url "http://localhost"
             :method :get
             :ssl-context (ks-ssl/pems->ssl-context
                            (resource "resources/cert.pem")
                            (resource "resources/key.pem")
                            (resource "resources/ca.pem"))}
        configured-req (http/configure-ssl req)]

    (testing "configure-ssl uses an existing ssl context"
      (is (instance? SSLEngine (:sslengine configured-req))))))

(deftest ssl-config-with-sslengine
  (let [req {:url "http://localhost"
             :method :get
             :ssl-cert (resource "resources/cert.pem")
             :ssl-key (resource "resources/key.pem")
             :ssl-ca-cert (resource "resources/ca.pem")
             :sslengine "thing"}
        configured-req (http/configure-ssl req)]
    (testing "configure-ssl does nothing when :sslengine is given"
      (is (= req configured-req)))))
