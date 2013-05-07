(ns kerodon.impl
  (:require [net.cgrand.enlive-html :as enlive]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [ring.util.codec :as codec]
            [flatland.ordered.map :as om])
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

(defmulti field->value
  "Get the value of the form field, or nil if it should be ignored"
  :tag)

(defmethod field->value :textarea
  ;; A textarea's child content is its value
  [field] (-> field :content first str))

(defn- selected-options
  "Get the <select> options that are considered selected"
  [options select-by-default]
  (or (seq (enlive/select options [(enlive/attr? :selected)]))
      (when select-by-default [(first options)])))

(defn- option-value
  "Get the real value of an <option> element"
  [option]
  (if-let [value (get-in option [:attrs :value])]
    value
    (first (:content option))))

(defmethod field->value :select
  ;; Single selects have the first element selected by default
  ;; Multiple selects do not have anything selected by default
  [field]
  (let [single (not (contains? (:attrs field) :multiple))]
    (-> (enlive/select field [:option])
        (selected-options single)
        (->> (map option-value))
        ((fn [values] (if single (first values) (seq values)))))))

(declare input->value)
(defmethod field->value :input
  ;; Delegate input field handling to a second multimethod
  [field] (input->value field))

(defn- ns-keyword [name]
  (when name (keyword "kerodon.impl" name)))

(defmulti input->value
  "Get the value for each type of <input>, or nil if it should be ignored"
  #(-> % :attrs :type ns-keyword))

(derive ::checkbox ::box)
(derive ::radio ::box)

(defmethod input->value ::box
  ;;Radios and checkboxes only send their value when they are checked
  ;; The spec says no value is undefined, but de-facto standard is "on"
  [field]
  (when (contains? (:attrs field) :checked)
    (get-in field [:attrs :value] "on")))

(defmethod input->value ::file
  ;; File inputs should not be coerced into strings
  [field]
  (get-in field [:attrs :value]))

(defmethod input->value :default
  ;; Any input not specified is treated as text input and stringified
  [field]
  (str (get-in field [:attrs :value])))

(defn field-name
  "Get the name attribute for a form field"
  [field] (get-in field [:attrs :name]))

(defn all-form-params [form]
  (reduce (fn [params field]
            (if-let [value (field->value field)]
              (codec/assoc-conj params (field-name field) value)
              params))
          (om/ordered-map)
          (enlive/select form [[#{:input :textarea :select}
                                (enlive/but (enlive/attr? :disabled))
                                (enlive/attr? :name)]])))

(defn build-request-details [state selector]
  (let [form (form-with-submit (:enlive state) selector)
        method (keyword (string/lower-case (:method (:attrs form) "post")))
        url (or (not-empty (:action (:attrs form)))
                (build-url (:request state)))
        params (all-form-params form)]
    [url :request-method method :params params]))
