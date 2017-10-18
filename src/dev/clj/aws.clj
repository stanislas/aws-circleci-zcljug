(ns aws
  (:require [amazonica.aws.securitytoken :as sts]))

;; To avoid accidental use of a productive account, we check here the numeber of account and
;; exit if it is not a developer account.
(defn check-dev-account!! [& skip-check]
  (when-not (System/getenv "CIRCLECI")
    (let [caller-identity (sts/get-caller-identity :version "2011-06-15")]
      (if (or (= (:account caller-identity) "655043939509")
              skip-check)
        caller-identity
        (throw (ex-info "You are not logged in the DEV account, please change your credentials"
                        caller-identity))))))
(defonce caller-identity (check-dev-account!!))


