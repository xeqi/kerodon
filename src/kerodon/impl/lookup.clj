(ns kerodon.impl.lookup
  (:require [net.cgrand.enlive-html :as enlive]
            [clojure.string :as string])
  (:import org.apache.http.entity.mime.MultipartEntity
           org.apache.http.entity.mime.content.StringBody
           org.apache.http.entity.mime.content.FileBody
           java.io.PipedOutputStream
           java.io.PipedInputStream))

(def fillable
  #{[:input
     (enlive/but
      (enlive/attr-has :type "submit"))]
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
  (if-let [elem (first (enlive/select
                        node
                        [:form (css-or-content selector)]))]
    elem
    (throw (Exception.
            (str "field could not be found with selector \"" selector "\"")))))

(defn get-form-element-by-name [name]
  [:form (enlive/attr-has :name name)])

(defn name-from-element [elem]
  (if (= :input (:tag elem))
    (:name (:attrs elem))
    (:for (:attrs elem))))

(defn get-value [state selector]
  (-> (enlive/select (:enlive state)
                     (-> (find-form-element (:enlive state) selector)
                         name-from-element
                         get-form-element-by-name))
      first
      :attrs
      :value))

(defn set-value [state selector input]
  (update-in state [:enlive]
             (fn [node]
               (enlive/transform node
                                 (-> (find-form-element node selector)
                                     name-from-element
                                     get-form-element-by-name)
                                 (fn [snode]
                                   (assoc-in snode [:attrs :value] input))))))

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

(defn multipart? [params]
  (some :filename (vals params)))

(defn add-file-part [m [k {:keys [filename content-type file]}]]
  (.addPart m
            k
            (if content-type
              (FileBody. file content-type)
              (FileBody. file)))
  m)

(defn add-param-part [m [k v]]
  (.addPart m
            k
            (StringBody. v)))

(defn multipart-body [params]
  (let [mpe (MultipartEntity.)]
    (doseq [p params]
      (if (:file (second p))
        (add-file-part mpe p)
        (add-param-part mpe p)))
    mpe))

(defn build-request-details [state selector]
  (if-let [form (first (find-form-with-submit (:enlive state) selector))]
    (let [method (keyword (string/lower-case (:method (:attrs form) "post")))
          url (or (:action (:attrs form))
                  (build-url (:request state)))
          params (into {}
                       (map (comp (juxt (comp str :name)
                                        :value) :attrs)
                            (enlive/select form
                                           [fillable])))]
      (if (multipart? params)
        (let [mpe (multipart-body params)]
          [url
           :request-method method
           :body (let [in (PipedInputStream.)
                       out (PipedOutputStream. in)]
                   (.writeTo mpe out)
                   in)
           :content-length (.getContentLength mpe)
           :content-type (.getValue (.getContentType mpe))])
        [url :request-method method :params params]))
    (throw (Exception.
            (str "button could not be found with selector \""
                 selector "\"")))))