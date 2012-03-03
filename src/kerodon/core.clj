(ns kerodon.core
  (:require [ring.mock.request :as request]
            [net.cgrand.enlive-html :as enlive]
            [clojure.string :as string]
            [peridot.core :as peridot]))

(def #^{:private true} fillable
  #{[:input
     (enlive/but
      (enlive/attr-has :type "submit"))]
    :textarea})

(defn #^{:private true} css-or-content [selector]
  #{(keyword selector) (enlive/pred #(= (:content %) [selector]))})


(defn #^{:private true} find-form-with-submit [node text]
  (enlive/select node
                 [[:form (enlive/has [[:input
                                       (enlive/attr= :type "submit")
                                       (enlive/attr= :value text)]])]]))

(defn #^{:private true} find-submit [node text]
  (enlive/select node
                 [[:input
                   (enlive/attr= :type "submit")
                   (enlive/attr= :value text)]]))

;TODO: merge w/ peridot
(defn build-url [{:keys [scheme server-name port uri query-string]}]
  (str (name scheme)
       "://"
       server-name
       (when (and port
                  (not= port (scheme {:https 443 :http 80})))
         (str ":" port))
       uri
       query-string))

(defn visit [state & rest]
  (let [state (apply peridot/request state rest)]
    (assoc state
      :html (enlive/html-resource
             (java.io.StringReader. (:body (:response state)))))))

(defn fill-in [state selector input]
  (update-in state [:html]
             (fn [node]
               (if-let [elem (first (enlive/select
                                     node
                                     [:form :> (css-or-content selector)]))]
                 (enlive/transform node
                                   [:form :> (enlive/attr-has
                                              :name
                                              (if (= :input (:tag elem))
                                                (:name (:attrs elem))
                                                (:for (:attrs elem))))]
                                   (fn [node]
                                     (assoc-in node [:attrs :value] input)))
                 (throw (Exception.
                         (str "field could not be found with selector \""
                              selector "\"")))))))

(defn press [state selector]
  (if-let [form (first (find-form-with-submit (:html state) selector))]
    (let [method (keyword (string/lower-case (or (:method (:attrs form))
                                                 "post")))
          url (or (:action (:attrs form))
                  (build-url (:request state)))
          params (into {}
                       (map (comp (juxt (comp str :name)
                                        (comp str :value)) :attrs)
                            (enlive/select form
                                           [fillable])))]
      (visit state
             url
             :request-method method
             :params params))
    (throw (Exception.
            (str "button could not be found with selector \""
                 selector "\"")))))

(defn follow [state text]
  (visit state (-> (:html state)
                (enlive/select [[:a (css-or-content text)]])
                first
                :attrs
                :href)))

(def follow-redirect peridot/follow-redirect)

(def session peridot/session)

(def validate peridot/dofns)