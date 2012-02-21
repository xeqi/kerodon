(ns kerodon.core
  (:require [ring.mock.request :as request]
            [net.cgrand.enlive-html :as enlive]
            [clojure.string :as string]))

(defn #^{:private true} field-value [node value name]
  (enlive/transform node
                    [:form :> (enlive/attr-has :name name)]
                    (fn [node]
                      (assoc-in node [:attrs :value] value))))

(defn #^{:private true} find-form-with-submit [node text]
  (enlive/select node
                 [[:form (enlive/has [[:input
                                       (enlive/attr-has :type "submit")
                                       (enlive/attr-has :value text)]])]]))

(defn #^{:private true} find-submit [node text]
  (enlive/select node
                 [[:input
                   (enlive/attr-has :type "submit")
                   (enlive/attr-has :value text)]]))

(def #^{:private true} fillable
  #{[:input
     (enlive/but
      (enlive/attr-has :type "submit"))]
    :textarea})

(defn fill-in [state text input]
  (update-in state [:html]
             (fn [node]
               (->> (enlive/select node [:form :> (enlive/pred #(= (:content %)
                                                                  [text]))])
                   first
                   :attrs
                   :for
                   (field-value node input)))))

(defn request
  ([state method url params]
     (let [response ((:ring-app state)
                     (reduce #(request/header %1 "Cookie" %2)
                              (request/request method url params)
                              (:cookie-string state)))]
       (-> response
           (assoc :html (enlive/html-resource
                         (java.io.StringReader. (:body response))))
           ;; TODO: handle cookies better
           ;; This completely ignores cookie attributes
           (assoc :cookie-string
             (let [cookies (map #(re-find #"[^;]*" %)
                                ((:headers response) "Set-Cookie"))]
               (if (empty? cookies)
                 (:cookie-string state)
                 cookies)))
           (assoc :ring-app (:ring-app state)))))
  ([state method url]
     (request state method url {})))

(defn follow-redirect [state]
  (request state :get ((:headers state) "Location")))

(defn press [state text]
  (let [form (first (find-form-with-submit (:html state) text))
        method (keyword (string/lower-case (:method (:attrs form))))
        submit (first (find-submit form text))
        url (:action (:attrs form))
        params (into {}
                     (map (comp (juxt (comp str :name)
                                      (comp str :value)) :attrs)
                          (enlive/select form
                                         [fillable])))]
    (request state
             method
             url
             params)))

(defn follow [state text]
  (request state :get
            (-> (:html state)
                (enlive/select [[:a (enlive/pred #(= (:content %) [text]))]])
                first
                :attrs
                :href)))

(defn init [& params]
  (apply hash-map params))

(defn status [f]
  (fn [{status :status}] (f status)))

(defn headers [f]
  (fn [{headers :headers}] (f headers)))

(defn html [f]
  (fn [{html :html}] (f html)))

(defn validate [state & fs]
  (doseq [f fs]
    (f state))
  state)