(ns kerodon.core
  (:require [kerodon.impl.lookup :as lookup]
            [peridot.core :as peridot]))

(defn visit [state & rest]
  (lookup/include-parse (apply peridot/request state rest)))

(defn fill-in [state selector input]
  (lookup/set-value state selector input))

(defn press [state selector]
  (apply visit state (lookup/build-request-details state selector)))

(defn follow [state selector]
  (visit state (lookup/find-url state selector)))

(defn follow-redirect [state]
  (lookup/include-parse (peridot/follow-redirect state)))

(def session peridot/session)

(defmacro within [state selector & fns]
  `(lookup/using ~state ~selector #(-> % ~@fns)))

(defmulti attach-file
  (fn [state selector file-descriptor] (type file-descriptor)))

(defmethod attach-file java.io.File [state selector file]
  (attach-file state selector
               {:filename (.getName file)
                :content-type nil
                :file file}))

(defmethod attach-file java.util.Map [state selector file-descriptor]
  (lookup/set-value state selector file-descriptor))