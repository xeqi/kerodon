(defproject org.clojars.wjlroe/kerodon "0.0.6-SNAPSHOT"
  :description "Acceptance test framework for web applications. wjlroe's fork with missing? helper"
  :url "https://github.com/wjlroe/kerodon"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [peridot "0.0.5"]
                 [enlive "1.0.0" :exclusions [org.clojure/clojure]]]
  :profiles {:test {:dependencies [[net.cgrand/moustache "1.1.0"
                                    :exclusions
                                    [[org.clojure/clojure]
                                     [ring/ring-core]]]
                                   [ring/ring-core "1.0.2"]
                                   [hiccup "1.0.0-beta1"]]
                    :resource-paths ["test-resources"]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0-beta4"]]}}
  :aliases {"all" ["with-profile" "test:test,1.4"]})