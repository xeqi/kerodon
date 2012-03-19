(ns kerodon.impl.multipart
  (:import org.apache.http.entity.mime.MultipartEntity
           org.apache.http.entity.mime.content.FileBody
           java.io.ByteArrayOutputStream))

(defn add-part [m [f c]]
  (.addPart m
            (.getName f)
            (if c
              (FileBody. f c)
              (FileBody. f)))
  m)

(defn make-multipart [& params]
  (let [baos (ByteArrayOutputStream.)]
    (.writeTo
     (reduce add-part
             (MultipartEntity.)
             files)
     baos)
    (.toString baos)))