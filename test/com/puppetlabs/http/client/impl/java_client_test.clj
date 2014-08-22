(ns com.puppetlabs.http.client.impl.java-client-test
  (:import (com.puppetlabs.http.client.impl JavaClient))
  (:require [clojure.test :refer :all]))

(deftest test-coerce-body-type
  (is (= "foo" (JavaClient/coerceBodyType nil nil nil))))
