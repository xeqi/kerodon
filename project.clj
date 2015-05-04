(defproject kerodon "0.6.1-SNAPSHOT"
  :description "Acceptance test framework for web applications"
  :url "https://github.com/xeqi/kerodon"
  :min-lein-version "2.0.0"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [peridot "0.4.0"]
                 [enlive "1.1.5" :exclusions [org.clojure/clojure]]
                 [ring/ring-codec "1.0.0"]
                 [org.flatland/ordered "1.5.2"]]
  :profiles {:test {:dependencies [[net.cgrand/moustache "1.1.0"
                                    :exclusions
                                    [[org.clojure/clojure]
                                     [ring/ring-core]]]
                                   [ring/ring-core "1.3.2"]
                                   [javax.servlet/servlet-api "2.5"]
                                   [hiccup "1.0.5"]]
                    :resource-paths ["test-resources"]}
             :dev  {:dependencies [^:replace [org.clojure/clojure "1.6.0"]]}
             :1.4  {:dependencies [^:replace [org.clojure/clojure "1.4.0"]]}
             :1.5  {:dependencies [^:replace [org.clojure/clojure "1.5.1"]]}
             :1.6  {:dependencies [^:replace [org.clojure/clojure "1.6.0"]]}}
  :aliases {"all" ["with-profile" "test,1.4:test,1.5:test,1.6"]})
