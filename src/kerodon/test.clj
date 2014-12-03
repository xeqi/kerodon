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

(defmacro regex-in? [expected]
  `(validate re-find?
             #(apply str (enlive/texts (:enlive %)))
             ~expected
             (~'regex-in? ~expected)))

(defmacro text? [expected]
  `(validate =
             #(apply str (enlive/texts (:enlive %)))
             ~expected
             (~'text? ~expected)))

(defmacro text-in? [expected]
  `(validate #(.contains %1 %2)
             #(apply str (enlive/texts (:enlive %)))
             ~expected
             (~'text-in? ~expected)))

(defmacro link? [key & [val]]
  `(validate #(let [href# (:href %2)
                    text# (:text %2)]
                (some (fn [link#]
                        (or (and href# (= href# (:href link#)))
                            (and text# (= text# (:text link#)))))
                      %1))
             #(map (fn [link#]
                     {:href (get-in link# [:attrs :href])
                      :text (apply str (enlive/texts (:content link#)))})
                   (enlive/select (:enlive %) [:a]))
             (if (nil? ~val)
               {:href ~key}
               {~key ~val})
             (~'link? ~key ~val)))

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
