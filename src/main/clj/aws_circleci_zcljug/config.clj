(ns aws-circleci-zcljug.config
  (:require [environ.core :as env]
            [mount.core :refer [defstate]]))

(defstate http-port
  :start (Integer/parseInt (get env/env :zcljug-http-port "8080")))

(defstate s3-bucket
  :start (get env/env :zcljug-s3-bucket))
