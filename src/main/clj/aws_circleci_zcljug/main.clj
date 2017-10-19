(ns aws-circleci-zcljug.main
  (:require [aws-circleci-zcljug.config :as config]
            [aws_circleci_zcljug.version :as version]
            [amazonica.aws.s3 :as s3]
            [bidi.ring :as bidi]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate] :as mount]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.middleware.resource :as res]
            [ring.util.response :as resp]
            [clojure.java.io :as io])
  (:import [java.util UUID])
  (:gen-class))

(defn version [request]
  (resp/response version/version))

(defn write-request-to-s3 [request-id request-text]
  (try
    (let [file (io/file "/tmp/" request-id)]
      (spit file request-text)
      (s3/put-object
        :bucket-name config/s3-bucket
        :key request-id
        :file file))
    (catch Exception e
      (log/error e "s3 bucket error"))))

(defn user [request]
  (let [request-id   (str (UUID/randomUUID))
        request-text (pr-str request)]
    (log/info request-text)
    (write-request-to-s3 request-id request-text))
  (resp/response "OK"))

(defstate routes
  :start ["/"
          [["version" {:get #'version}]
           [[:user] {:get #'user}]]])

(defstate handler
  :start (-> routes
             (bidi/make-handler)
             (params/wrap-params)
             (res/wrap-resource "public")))

(defstate server
  :start (jetty/run-jetty #'handler
                          {:port  config/http-port
                           :join? false})
  :stop (.stop server))

(defn -main [& args]
  (log/info "Starting server")
  (mount/start)
  (log/info "Started"))
