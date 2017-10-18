(defproject aws-circleci-zcljug "0.0.0"
  :description "A CircleCi/AWS example"
  :url "https://github.com/stanislas/aws-circleci-zcljug"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]

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
  :jvm-opts [~(str "-Daws.profile=" (if (System/getenv "CIRCLECI") "default" "oscillator"))])
