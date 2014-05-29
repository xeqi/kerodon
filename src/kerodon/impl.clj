(ns kerodon.impl
  (:require [net.cgrand.enlive-html :as enlive]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [flatland.ordered.map :as om])
  (:import java.io.StringReader))

(defn- assoc-conj
  "Associate a key with a value in a map. If the key already exists in the map,
  a vector of values is associated with the key.
  Stolen out of ring-codec."
  [map key val]
  (assoc map key
    (if-let [cur (get map key)]
      (if (vector? cur)
        (conj cur val)
        [cur val])
      val)))

;; selectors

(defn form-element-by [attr val]
  [:form (enlive/attr-has attr val)])

(defn css-or-content [selector]
  (if (string? selector)
    (enlive/pred #(= (map clojure.string/trim (:content %)) [selector]))
    selector))

(defn css-or-label [selector]
  (if (string? selector)
    (enlive/pred #(and (= (:tag %1) :label)
                       (= (enlive/texts [%1]) [selector])))
    selector))

(defn css-or-value [selector]
  (if (string? selector)
    (enlive/attr= :value selector)
    selector))

;; finders
(defn- not-found [type selector]
  (throw
    (IllegalArgumentException.
      (format "%s could not be found with selector \"%s\"" type selector))))

(defn form-and-button
  "Given a form, returns both the form and the specified submit button."
  [form selector]
  (let [button (first
                 (enlive/select form
                                [[#{:input :button}
                                  (enlive/attr= :type "submit")
                                  (css-or-value selector)]]))]
    [form button]))

(defn form-and-submit
  "Locates the form containing the specified submit button.
  Returns the form and the button itself."
  [node selector]
  (if-let [found
           (first (filter (fn [[form button]] (not (nil? button)))
                          (map (fn [form] (form-and-button form selector))
                               (enlive/select node [:form]))))]
    found
    (not-found "button" selector)))

(defn form-element [node selector]
  (if-let [elem (first (enlive/select
                        node
                        [:form (css-or-label selector)]))]
    elem
    (not-found "field" selector)))

(defn link [node selector]
  (if-let [link (first (enlive/select
                        node
                        [[:a (css-or-content selector)]]))]
    link
    (not-found "link" selector)))

(defn- field-to-selector [elem]
  (form-element-by :name (get-in elem [:attrs :name])))

(defn- label-to-selector [doc label]
  (if-let [id (get-in label [:attrs :for])]
    (let [selector (form-element-by :id id)]
      (if (first (enlive/select doc selector))
        selector
        (not-found "field" (keyword (str "#" id)))))
    (if-let [field (first (enlive/select label [#{:input :select :textarea}]))]
      (form-element-by :name (get-in field [:attrs :name]))
      (not-found "field inside label" (apply str (enlive/texts [label]))))))

(defn- form-element-selector [doc elem]
  (if (= :label (:tag elem))
    (label-to-selector doc elem)
    (field-to-selector elem)))

(defn form-element-query [node selector]
  (->> (form-element node selector)
       (form-element-selector node)))

(defn form-element-for [node selector]
  (enlive/select node (form-element-query node selector)))

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
                                 (form-element-query node selector)
                                 (fn [snode]
                                   (if (= (:tag snode) :textarea)
                                     (assoc-in snode [:content] [input])
                                     (assoc-in snode [:attrs :value] input)))))))

(defn- option=
  "Enlive selector for options with matching content or value"
  [option]
  [[:option #{(enlive/pred #(= [option] (:content %)))
              (enlive/attr= :value option)}]])

(defn- mark-option-selected [option]
  (fn [select-node]
    (when (empty? (enlive/select select-node (option= option)))
      (throw
        (IllegalArgumentException.
          (format "option could not be found with selector \"%s\"" option))))
    ; Transform requires a list of nodes, we only have one here
    (-> (list select-node)
        ; Deselect all options
        (enlive/transform [:option]
                          (enlive/remove-attr :selected))
        ; Select the chosen option
        (enlive/transform (option= option)
                          (enlive/set-attr :selected "selected")))))

(defn choose-value [state selector option]
  (let [enlive (:enlive state)]
    (assoc state :enlive
      (enlive/transform enlive
                        (form-element-query enlive selector)
                        (mark-option-selected option)))))

(defn check-box [state selector]
  (assoc state :enlive
    (enlive/transform (:enlive state)
                      (form-element-query (:enlive state) selector)
                      #(assoc-in % [:attrs :checked] "checked"))))

(defn uncheck-box [state selector]
  (assoc state :enlive
    (enlive/transform (:enlive state)
                      (form-element-query (:enlive state) selector)
                      #(update-in % [:attrs] dissoc :checked))))

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

(defn is-submit-button?
  "Checks if the field is a form submit button."
  [field]
  (and (= :input (:tag field))
       (= "submit" (:type (:attrs field)))))

(defn all-form-params [form & [button]]
  (reduce (fn [params field]
            (if (or (nil? button)
                    (not (is-submit-button? field))
                    (= button field))
              (if-let [value (field->value field)]
                (assoc-conj params (field-name field) value)
                params)
              params))
          (om/ordered-map)
          (enlive/select form [[#{:input :textarea :select}
                                (enlive/but (enlive/attr? :disabled))
                                (enlive/attr? :name)]])))

(defn build-request-details [state selector]
  (let [[form button] (form-and-submit (:enlive state) selector)
        method (keyword (string/lower-case (:method (:attrs form) "get")))
        url (or (not-empty (:action (:attrs form)))
                (build-url (:request state)))
        params (all-form-params form button)]
    [url :request-method method :params params]))
