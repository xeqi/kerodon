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

(defn css-or-content [selector]
  (if (string? selector)
    (enlive/pred #(= (:content %) [selector]))
    selector))


(defn #^{:private true} find-form-with-submit [node selector]
  (enlive/select node
                 [[:form (enlive/has [[:input
                                       (enlive/attr= :type "submit")
                                       (if (string? selector)
                                         (enlive/attr= :value selector)
                                         selector)]])]]))

(defn #^{:private true} find-submit [node text]
  (enlive/select node
                 [[:input
                   (enlive/attr= :type "submit")
                   (enlive/attr= :value text)]]))

;TODO: merge w/ peridot
(defn #^{:private true} build-url
  [{:keys [scheme server-name port uri query-string]}]
  (str (name scheme)
       "://"
       server-name
       (when (and port
                  (not= port (scheme {:https 443 :http 80})))
         (str ":" port))
       uri
       query-string))

(defn #^{:private true} assoc-enlive [state]
  (assoc state
    :enlive (when-let [body (:body (:response state))]
              (enlive/html-resource
               (java.io.StringReader. body)))))

(defn visit [state & rest]
  (assoc-enlive (apply peridot/request state rest)))

(defn #^{:private true} get-form-element [node selector]
  (first (enlive/select
          node
          [:form :> (css-or-content selector)])))

(defn get-form-element-by-name [name]
  [:form :> (enlive/attr-has
             :name
             name)])

(defn name-from-element [elem]
  (if (= :input (:tag elem))
    (:name (:attrs elem))
    (:for (:attrs elem))))

(defn get-value [state selector]
  ((fn [node]
     (if-let [elem (get-form-element node selector)]
       (-> (enlive/select node
                      (-> elem
                          name-from-element
                          get-form-element-by-name))
           first
           :attrs
           :value)
       (throw (Exception.
               (str "field could not be found with selector \""
                    selector "\"")))))
   (:enlive state)))

(defn fill-in [state selector input]
  (update-in state [:enlive]
             (fn [node]
               (if-let [elem (get-form-element node selector)]
                 (enlive/transform node
                                   (-> elem
                                       name-from-element
                                       get-form-element-by-name)
                                   (fn [node]
                                     (assoc-in node [:attrs :value] input)))
                 (throw (Exception.
                         (str "field could not be found with selector \""
                              selector "\"")))))))

(defn press [state selector]
  (if-let [form (first (find-form-with-submit (:enlive state) selector))]
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
  (visit state (-> (:enlive state)
                (enlive/select [[:a (css-or-content text)]])
                first
                :attrs
                :href)))

(defn follow-redirect [state]
  (assoc-enlive (peridot/follow-redirect state)))

(def session peridot/session)

(defn has
  ([state form]
     (has state form nil))
  ([state form msg]
     (form state msg)))

(defn validate [comparator generator expected exp-msg]
  (fn [state msg]
    (try (let [value (generator state)]
           (clojure.test/do-report {:actual value
                                    :type (if (comparator value expected)
                                            :pass
                                            :fail)
                                    :message msg
                                    :expected exp-msg}))
         (catch java.lang.Throwable t
           (clojure.test/do-report {:actual t
                                    :type :error
                                    :message msg
                                    :expected exp-msg})))
    state))

(defn text? [expected]
  (validate =
            #(apply str (enlive/texts (:enlive %)))
            expected
            (list 'text? expected)))

(defn status? [expected]
  (validate =
            (comp :status :response)
            expected
            (list 'status? expected)))

(defn value? [selector expected]
  (validate =
            #(get-value % selector)
            expected
            (list 'value? selector expected)))

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

(defmacro within [state selector & fns]
  `(using ~state ~selector #(-> % ~@fns)))