(ns aws-circleci-zcljug.main
  (:require [aws-circleci-zcljug.config :as config]
            [aws_circleci_zcljug.version :as version]
            [bidi.ring :as bidi]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate] :as mount]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.middleware.resource :as res]
            [ring.util.response :as resp])
  (:gen-class))

(defn version [request]
  (resp/response version/version))

(defn user [request]
  (log/info (pr-str request))
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
