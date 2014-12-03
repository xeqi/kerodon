(ns kerodon.test
  (:require [net.cgrand.enlive-html :as enlive]
            [kerodon.impl :as impl]))

(defn has
  ([state form]
     (has state form nil))
  ([state form msg]
     (form state msg)))

(defmacro validate [comparator generator expected exp-msg]
  `(fn [state# msg#]
     (try (let [value# (~generator state#)]
            (clojure.test/do-report {:actual value#
                                     :type (if (~comparator value# ~expected)
                                             :pass
                                             :fail)
                                     :message msg#
                                     :expected (quote ~exp-msg)}))
          (catch java.lang.Throwable t#
            (clojure.test/do-report {:actual t#
                                     :type :error
                                     :message msg#
                                     :expected (quote ~exp-msg)})))
     state#))

(defn re-match? [s r]
  (not (nil? (re-matches (re-pattern r) s))))

(defn re-find? [s r]
  (not (nil? (re-find (re-pattern r) s))))

(defmacro regex? [expected]
  `(validate re-match?
             #(apply str (enlive/texts (:enlive %)))
             ~expected
             (~'regex? ~expected)))

(defmacro some-regex? [expected]
  `(validate re-find?
             #(apply str (enlive/texts (:enlive %)))
             ~expected
             (~'some-regex? ~expected)))

(defmacro text? [expected]
  `(validate =
             #(apply str (enlive/texts (:enlive %)))
             ~expected
             (~'text? ~expected)))

(defmacro some-text? [expected]
  `(validate #(.contains %1 %2)
             #(apply str (enlive/texts (:enlive %)))
             ~expected
             (~'some-text? ~expected)))

(defn submap?
  "Checks whether m contains all entries in sub."
  [^java.util.Map m ^java.util.Map sub]
  (.containsAll (.entrySet m) (.entrySet sub)))

(defmacro link? [text & [href]]
  (let [expected (if (nil? href)
                   (list 'link? text)
                   (list 'link? text href))]
    `(validate (fn [coll# search#] (some #(submap? %1 search#) coll#))
               #(map (fn [link#]
                       {:href (get-in link# [:attrs :href])
                        :text (apply str (enlive/texts (:content link#)))})
                     (enlive/select (:enlive %) [:a]))
               (cond
                (nil? ~href) {:text ~text}
                (= :href ~text) {:href ~href}
                :else {:text ~text :href ~href})
               ~expected)))

(defmacro heading? [expected]
  `(validate #(some (partial = %2) %1)
             #(map (fn [h#] (apply str (enlive/texts (:content h#))))
                   (concat
                     (enlive/select (:enlive %) [:h1])
                     (enlive/select (:enlive %) [:h2])
                     (enlive/select (:enlive %) [:h3])
                     (enlive/select (:enlive %) [:h4])
                     (enlive/select (:enlive %) [:h5])
                     (enlive/select (:enlive %) [:h6])))
             ~expected
             (~'heading? ~expected)))

(defmacro status? [expected]
  `(validate =
            (comp :status :response)
            ~expected
            (~'status? ~expected)))

(defmacro value? [selector expected]
  `(validate =
             #(impl/get-value % ~selector)
             ~expected
             (~'value? ~selector ~expected)))

(defmacro missing? [selector]
  `(validate =
             #(count (enlive/select (:enlive %) ~selector))
             0
             (~'missing? ~selector)))

(defmacro attr? [selector attr expected]
  `(validate =
             #(impl/get-attr % ~selector ~attr)
             ~expected
             (~'attr? ~selector ~attr ~expected)))
