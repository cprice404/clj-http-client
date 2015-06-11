(ns scratch.test-webapp
  (:require [clojure.java.io :as io]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.app :as tk-app]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :as j9]
            [clojure.tools.namespace.repl :refer (refresh)]
            [clojure.tools.logging :as log])
  (:import (java.io PipedOutputStream PipedInputStream)))

(def app-atom (atom nil))

(defn start-webserver
  []
  (let [streamed-response-handler (fn [req]
                                    (let [initial-bytes-read? (promise)
                                          ;instream (ByteArrayInputStream. (.getBytes "foo" "UTF-8"))
                                          outstream (PipedOutputStream.)
                                          instream (PipedInputStream.)
                                          _ (.connect instream outstream)
                                          outwriter (io/make-writer outstream {})
                                          _ (future
                                              (log/info "WRITING STUFF TO STREAM")
                                              (.write outwriter (apply str (repeat (* 32 1024) "f")))
                                              (.flush outwriter)
                                              (.flush outstream)
                                              ;(.flush instream)
                                              ;@initial-bytes-read?
                                              (log/info "SLEEPING")
                                              (Thread/sleep 5000)
                                              (log/info "WRITING MORE STUFF TO STREAM")
                                              (.write outwriter "BAR")
                                              (log/info "CLOSING STREAM")
                                              (.close outwriter))]
                                      (log/info "RETURNING RESPONSE MAP")
                                      {:status 200
                                       :body instream}))]
    (let [app (tk/boot-services-with-config
                [j9/jetty9-service]
                {:webserver {:port 10000
                             :host "localhost"}
                 :global {:logging-config "./test/scratch/logback-test-webapp.xml"}})
          jetty (tk-app/get-service app :WebserverService)]
      (j9/add-ring-handler jetty streamed-response-handler "/hello")
      (reset! app-atom app))))

(defn stop-webserver
  []
  (tk-app/stop @app-atom))

(defn reset
  []
  (stop-webserver)
  (refresh :after 'scratch.test-webapp/start-webserver))