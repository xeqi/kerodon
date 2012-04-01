(ns kerodon.core
  (:require [kerodon.impl :as impl]
            [peridot.core :as peridot]))

(defn visit [state & rest]
  (impl/include-parse (apply peridot/request state rest)))

(defn fill-in [state selector input]
  (impl/set-value state selector input))

(defn press [state selector]
  (apply visit state (impl/build-request-details state selector)))

(defn follow [state selector]
  (visit state (impl/find-url state selector)))

(defn follow-redirect [state]
  (impl/include-parse (peridot/follow-redirect state)))

(def session peridot/session)

(defmacro within [state selector & fns]
  `(impl/using ~state ~selector #(-> % ~@fns)))

(defn attach-file [state selector file]
  (impl/set-value state selector file))