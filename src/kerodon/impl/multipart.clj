(ns kerodon.impl.multipart
  (:import org.apache.http.entity.mime.MultipartEntity
           org.apache.http.entity.mime.content.FileBody
           java.io.ByteArrayOutputStream))

       (enlive/but
        (enlive/attr-has :type "file"))

(defn add-part [m [f c]]
  (.addPart m
            (.getName f)
            (if c
              (FileBody. f c)
              (FileBody. f)))
  m)

(defn make-multipart [& files]
  (let [baos (ByteArrayOutputStream.)]
    (.writeTo
     (reduce add-part
             (MultipartEntity.)
             files)
     baos)
    (.toString baos)))

(defn attach-file
  ([state selector file] (attach-file state selector file nil))
  ([state selector file content-type]
     (update-in state [:enlive]
                (fn [node]
                  (if-let [elem (get-form-element node selector)]
                    (enlive/transform node
                                      (-> elem
                                          name-from-element
                                          get-form-element-by-name)
                                      (fn [node]
                                        (assoc-in node [:attrs :value]
                                                  {:file file
                                                   :content-type content-type})))
                    (throw (Exception.
                            (str "field could not be found with selector \""
                                 selector "\""))))))))