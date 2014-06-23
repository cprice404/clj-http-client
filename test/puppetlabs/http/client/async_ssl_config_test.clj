(ns puppetlabs.http.client.async-ssl-config-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :refer [resource]] 
            [puppetlabs.certificate-authority.core :as ssl]
            [puppetlabs.http.client.async :as http]
            [schema.test :as schema-test])
  (:import [javax.net.ssl SSLContext]))

(use-fixtures :once schema-test/validate-schemas)

(deftest ssl-config-with-files
  (let [req {:url "http://localhost"
             :method :get
             :ssl-cert (resource "ssl/cert.pem")
             :ssl-key (resource "ssl/key.pem")
             :ssl-ca-cert (resource "ssl/ca.pem")}
        configured-req (http/configure-ssl req)]

    (testing "configure-ssl sets up an SSLContext when given cert, key, ca-cert"
      (is (instance? SSLContext (:ssl-context configured-req))))

    (testing "removes ssl-cert, ssl-key, ssl-ca-cert"
      (is (not (:ssl-cert configured-req)))
      (is (not (:ssl-key configured-req)))
      (is (not (:ssl-ca-cert configured-req))))))

(deftest ssl-config-with-ca-file
  (let [req {:url         "http://localhost"
             :method      :get
             :ssl-ca-cert (resource "ssl/ca.pem")}
        configured-req (http/configure-ssl req)]

    (testing "configure-ssl sets up an SSLContext when given ca-cert"
      (is (instance? SSLContext (:ssl-context configured-req))))

    (testing "removes ssl-ca-cert"
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
             :ssl-context (ssl/pems->ssl-context
                            (resource "ssl/cert.pem")
                            (resource "ssl/key.pem")
                            (resource "ssl/ca.pem"))}
        configured-req (http/configure-ssl req)]

    (testing "configure-ssl uses an existing ssl context"
      (is (instance? SSLContext (:ssl-context configured-req))))))