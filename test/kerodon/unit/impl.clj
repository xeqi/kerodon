(ns kerodon.unit.impl
  (:use [kerodon.impl]
        [clojure.test])
  (:require [net.cgrand.enlive-html :as enlive])
  (:import java.io.StringReader))

(deftest test-to-html-resource
  "The Ring spec specifies that the response :body can be:

 {String, ISeq, File, InputStream}

So, make sure to-html-resource can handle all these cases. Enlive knows how to read
files and input streams, and those are hard to create in test cases, so don't test those."
  (let [html "<html><body><h1>Don Kero</h1></body></html>"
        expected (enlive/html-resource (StringReader. html))]
    (testing "String"
      (is (= expected (to-html-resource html))))
    (testing "LazySeq"
      (is (= expected (to-html-resource (map identity ["<html>" "<body>" "<h1>" "Don Kero" "</h1>" "</body>" "</html>"])))))))
