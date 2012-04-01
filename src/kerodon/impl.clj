(ns kerodon.impl
  (:require [net.cgrand.enlive-html :as enlive]
            [clojure.string :as string])
  (:import java.io.StringReader))

;; selectors
(def fillable
  #{[:input
     (enlive/but
      (enlive/attr-has :type "submit"))]
    :textarea})

(defn form-element-by-name [name]
  [:form (enlive/attr-has :name name)])

(defn css-or-content [selector]
  (if (string? selector)
    (enlive/pred #(= (:content %) [selector]))
    selector))

(defn css-or-value [selector]
  (if (string? selector)
    (enlive/attr= :value selector)
    selector))

;; finders
(defn form-with-submit [node selector]
  (if-let [elem (first (enlive/select
                        node
                        [[:form
                          (enlive/has
                           [[:input
                             (enlive/attr= :type "submit")
                             (css-or-value selector)]])]]))]
    elem
    (throw (IllegalArgumentException.
            (str "button could not be found with selector \""
                 selector "\"")))))

(defn form-element [node selector]
  (if-let [elem (first (enlive/select
                        node
                        [:form (css-or-content selector)]))]
    elem
    (throw (IllegalArgumentException.
            (str "field could not be found with selector \"" selector "\"")))))

(defn link [node selector]
  (if-let [link (first (enlive/select
                        node
                        [[:a (css-or-content selector)]]))]
    link
    (throw (IllegalArgumentException.
            (str "link could not be found with selector \""
                 selector "\"")))))

(defn name-from-element [elem]
  (if (= :input (:tag elem))
    (:name (:attrs elem))
    (:for (:attrs elem))))

(defn form-element-for [node selector]
  (enlive/select node
                 (-> (form-element node selector)
                     name-from-element
                     form-element-by-name)))

;; state manipulation

(defn include-parse [state]
  (assoc state
    :enlive (when-let [body (:body (:response state))]
              (enlive/html-resource
               (StringReader. body)))))

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

(defn get-value [state selector]
  (-> (:enlive state)
      (form-element-for selector)
      first
      :attrs
      :value))

(defn set-value [state selector input]
  (update-in state [:enlive]
             (fn [node]
               (enlive/transform node
                                 (-> (form-element node selector)
                                     name-from-element
                                     form-element-by-name)
                                 (fn [snode]
                                   (assoc-in snode [:attrs :value] input))))))

(defn find-url [state selector]
  (-> (:enlive state)
      (link selector)
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
  (let [form (form-with-submit (:enlive state) selector)
        method (keyword (string/lower-case (:method (:attrs form) "post")))
        url (or (:action (:attrs form))
                (build-url (:request state)))
        params (into {}
                     (map (comp (juxt (comp str :name)
                                      :value) :attrs)
                          (enlive/select form
                                         [fillable])))]
    [url :request-method method :params params]))