(ns kerodon.impl.lookup
  (:require [net.cgrand.enlive-html :as enlive]
            [ring.mock.request :as request]
            [clojure.string :as string]))

(def fillable
  #{[:input
     #{(enlive/but
        (enlive/attr-has :type "submit"))}]
    :textarea})

(defn css-or-content [selector]
  (if (string? selector)
    (enlive/pred #(= (:content %) [selector]))
    selector))

(defn find-form-with-submit [node selector]
  (enlive/select node
                 [[:form (enlive/has [[:input
                                       (enlive/attr= :type "submit")
                                       (if (string? selector)
                                         (enlive/attr= :value selector)
                                         selector)]])]]))

(defn find-submit [node text]
  (enlive/select node
                 [[:input
                   (enlive/attr= :type "submit")
                   (enlive/attr= :value text)]]))

(defn include-parse [state]
  (assoc state
    :enlive (when-let [body (:body (:response state))]
              (enlive/html-resource
               (java.io.StringReader. body)))))

;TODO: merge w/ peridot
(defn build-url
  [{:keys [scheme server-name port uri query-string]}]
  (str (name scheme)
       "://"
       server-name
       (when (and port
                  (not= port (scheme {:https 443 :http 80})))
         (str ":" port))
       uri
       query-string))

(defn find-form-element [node selector]
  (if-let [elem (enlive/select
                 node
                 [:form (css-or-content selector)])]
    elem
    (throw (Exception.
            (str "field could not be found with selector
                              \"" selector "\"")))))

(defn get-form-element-by-name [name]
  [:form (enlive/attr-has :name name)])

(defn name-from-element [elem]
  (if (= :input (:tag elem))
    (:name (:attrs elem))
    (:for (:attrs elem))))

(defn get-value [state selector]
  ((fn [node]
     (-> (enlive/select node
                        (-> (find-form-element node selector)
                            name-from-element
                            get-form-element-by-name))
         first
         :attrs
         :value))
   (:enlive state)))

(defn set-value [state selector input]
  (update-in state [:enlive]
             (fn [node]
               (enlive/transform node
                                 (-> (find-form-element node selector)
                                     name-from-element
                                     get-form-element-by-name)
                                 (fn [node]
                                   (assoc-in node [:attrs :value] input))))))

(defn find-url [state selector]
  (-> (:enlive state)
      (enlive/select [[:a (css-or-content selector)]])
      first
      :attrs
      :href))

(defn using [state selector f]
  (let [new-state (f (update-in state [:enlive]
                                #(enlive/select
                                  %
                                  selector)))]
    (if (and (= (:request new-state)
                (:request state))
             (= (:response new-state)
                (:response state)))
      (update-in state
                 [:enlive]
                 #(enlive/transform %
                                    selector
                                    (constantly (:enlive new-state))))
      new-state)))

(defn build-request-details [state selector]
  (if-let [form (first (find-form-with-submit (:enlive state) selector))]
    (let [method (keyword (string/lower-case (:method (:attrs form) "post")))
          url (or (:action (:attrs form))
                  (build-url (:request state)))
          params (into {}
                       (map (comp (juxt (comp str :name)
                                        (comp str :value)) :attrs)
                            (enlive/select form
                                           [fillable])))]
      [url
       :request-method method
       :params params])
    (throw (Exception.
            (str "button could not be found with selector \""
                 selector "\"")))))