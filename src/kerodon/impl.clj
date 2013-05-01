(ns kerodon.impl
  (:require [net.cgrand.enlive-html :as enlive]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:import java.io.StringReader))

;; selectors

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
  (if (some #{(:tag elem)} [:input :textarea])
    (:name (:attrs elem))
    (:for (:attrs elem))))

(defn form-element-for [node selector]
  (enlive/select node
                 (-> (form-element node selector)
                     name-from-element
                     form-element-by-name)))

;; state manipulation


(defprotocol ToHtmlResource
  (to-html-resource [obj]))

(extend-protocol ToHtmlResource
  clojure.lang.ISeq
  (to-html-resource [s]  (to-html-resource (apply str s)))
  String
  (to-html-resource [str] (enlive/html-resource (StringReader. str)))
  Object
  (to-html-resource [o] (enlive/html-resource o)))

(defn include-parse [state]
  (assoc state
    :enlive (when-let [body (:body (:response state))]
              (to-html-resource body))))

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
  (or (-> (:enlive state)
          (form-element-for selector)
          first
          :attrs
          :value)
      ""))

(defn get-attr [state selector attr]
  (-> (:enlive state)
      (enlive/select selector)
      first
      :attrs
      (get attr)))

(defn set-value [state selector input]
  (update-in state [:enlive]
             (fn [node]
               (enlive/transform node
                                 (-> (form-element node selector)
                                     name-from-element
                                     form-element-by-name)
                                 (fn [snode]
                                   (if (= (:tag snode) :textarea)
                                     (assoc-in snode [:content] [input])
                                     (assoc-in snode [:attrs :value] input)))))))

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

;; Reading Form

(defn field-name
  "Get the name attribute for a form field"
  [field] (get-in field [:attrs :name]))

(defmulti field->param
  "Get key-value pair for this form field, or nil if it should be ignored"
  :tag)

(defmethod field->param :textarea
  [field] [(field-name field) (first (:content field))])

(defn- selected-option
  "Get the option that is considered selected"
  [options]
  (first
    (or (seq (enlive/select options [(enlive/attr? :selected)]))
        options)))

(defn- option-value
  "Get the real value of an <option> element"
  [option]
  (if-let [value (get-in option [:attrs :value])]
    value
    (first (:content option))))

(defmethod field->param :select
  [field] [(field-name field)
           (-> (enlive/select field [:option])
               (selected-option)
               (option-value))])

(declare input->param)
(defmethod field->param :input
  [field] (input->param field))

(defmulti input->param
  "Get key-value pair for types of <input>, or nil if it should be ignored"
  #(-> % :attrs :type))

(defmethod input->param :default
  [field]
  [(field-name field) (get-in field [:attrs :value])])

(defn all-form-params [form]
  (into {} (map field->param
                (enlive/select form [[#{:input :textarea :select}
                                      (enlive/attr? :name)]]))))

(defn build-request-details [state selector]
  (let [form (form-with-submit (:enlive state) selector)
        method (keyword (string/lower-case (:method (:attrs form) "post")))
        url (or (not-empty (:action (:attrs form)))
                (build-url (:request state)))
        params (all-form-params form)]
    [url :request-method method :params params]))
