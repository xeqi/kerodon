(ns kerodon.core
  (:require [kerodon.impl :as impl]
            [peridot.core :as peridot]))

(def session peridot/session)

(defn- resolve-uri [state ^String uri]
  (if-let [request (:request state)]
    (str (.resolve (java.net.URI. (peridot.request/url request)) uri))
    uri))

(defn visit [state uri & rest]
  (impl/include-parse (apply peridot/request state (resolve-uri state uri) rest)))

(defn follow-redirect [state]
  (impl/include-parse (peridot/follow-redirect state)))

(defn follow [state selector]
  (visit state (impl/find-url state selector)))

(defn fill-in [state selector input]
  (impl/set-value state selector input))

(defn choose [state selector option]
  (impl/choose-value state selector option))

(defn check [state selector]
  (impl/check-box state selector))

(defn uncheck [state selector]
  (impl/uncheck-box state selector))

(defn attach-file [state selector file]
  (impl/set-value state selector file))

(defn press [state selector]
  (apply visit state (impl/build-request-details state selector)))

(defmacro within [state selector & fns]
  `(impl/using ~state ~selector #(-> % ~@fns)))
