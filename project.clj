(defproject kerodon "0.9.0"
  :description "Acceptance test framework for web applications"
  :url "https://github.com/xeqi/kerodon"
  :min-lein-version "2.0.0"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [peridot "0.5.1"]
                 [enlive "1.1.6" :exclusions [org.clojure/clojure]]
                 [ring/ring-codec "1.0.1"]
                 [org.flatland/ordered "1.5.7"]]
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :profiles {:test {:dependencies [[net.cgrand/moustache "1.1.0"
                                    :exclusions
                                    [[org.clojure/clojure]
                                     [ring/ring-core]]]
                                   [ring/ring-core "1.5.0"]
                                   [javax.servlet/servlet-api "2.5"]
                                   [hiccup "1.0.5"]]
                    :resource-paths ["test-resources"]}
             :1.5  {:dependencies [^:replace [org.clojure/clojure "1.5.1"]]}
             :1.6  {:dependencies [^:replace [org.clojure/clojure "1.6.0"]]}
             :1.7  {:dependencies [^:replace [org.clojure/clojure "1.7.0"]]}
             :1.8  {:dependencies [^:replace [org.clojure/clojure "1.8.0"]]}}
  :aliases {"all" ["with-profile" "test,1.5:test,1.6:test,1.7:test,1.8"]})
