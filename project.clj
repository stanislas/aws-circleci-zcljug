(defproject aws-circleci-zcljug "0.0.0"
  :description "A CircleCi/AWS example"
  :url "https://github.com/stanislas/aws-circleci-zcljug"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[amazonica "0.3.113"
                  :exclusions [com.amazonaws/amazon-kinesis-client
                               com.amazonaws/aws-java-sdk
                               com.amazonaws/dynamodb-streams-kinesis-adapter]]
                 [bidi "2.1.2"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [ch.qos.logback/logback-core "1.2.3"]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.11.213"]
                 [com.rpl/specter "1.0.3"]
                 [environ "1.1.0"]
                 [hiccup "1.0.5"]
                 [mount "0.1.11"]
                 [net.logstash.logback/logstash-logback-encoder "4.11"]
                 [org.clojars.stanhbb/smbh-log "1.0.1"]
                 [org.clojure/clojure "1.9.0-beta2"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.eclipse.jetty/jetty-server "9.4.6.v20170531"]
                 [ring/ring-core "1.6.2"]
                 [ring/ring-jetty-adapter "1.6.2"]]

  :target-path "target/%s"
  :source-paths ["src/main/clj"]
  :test-paths ["src/test/clj"]
  :resource-paths ["src/main/resources"]
  :plugins [[lein-ancient "0.6.10"]
            [lein-environ "1.1.0"]
            [org.clojars.cvillecsteele/lein-git-version "1.2.7"]
            [lein-marginalia "0.9.0"]
            [test2junit "1.2.5"]]
  :git-version {:path           "src/main/clj/aws_circleci_zcljug"
                :root-ns        "aws_circleci_zcljug"
                :version-cmd    "git describe --match release/*.* --abbrev=4 --dirty=--DIRTY--"
                :tag-to-version ~#(if (> (count %) 8)
                                    (subs % 8)
                                    %)}
  :jvm-opts [~(str "-Daws.profile=" (if (System/getenv "CIRCLECI") "default" "oscillator"))]
  :profiles {:uberjar {:aot :all}
             :dev     {:dependencies   [[cheshire "5.8.0"]
                                        [com.amazonaws/aws-java-sdk "1.11.213"]]
                       :env            {:zcljug-http-port "8876"}
                       :source-paths   ["src/dev/clj"]
                       :resource-paths ["src/dev/resources"]}})
