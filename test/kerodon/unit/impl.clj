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

(defn form-produces [fields-html expected]
  (let [html (str "<form action=/>"
                  fields-html
                  "<input type=submit value=Submit>"
                  "</form>")
        enlive (to-html-resource html)
        parsed (build-request-details {:enlive enlive} "Submit")
        details (apply hash-map :url parsed)]
    (is (= expected (:params details)))))

(deftest test-build-request-details
  "Test that we can parse the value of a variety of different form elements
  Attempts to follow the spec:
  http://www.w3.org/TR/html401/interact/forms.html#h-17.13"
  (testing "text input"
    (form-produces "<input type=text value=some-text name=in>"
                   {"in" "some-text"}))
  (testing "hidden input"
    (form-produces "<input type=hidden value=hide-me name=in>"
                   {"in" "hide-me"}))
  (testing "valueless text input"
    (form-produces "<input type=text name=in>"
                   {"in" ""}))
  (testing "password input"
    (form-produces "<input type=password value=secret name=in>"
                   {"in" "secret"}))
  (testing "unchecked checkbox input"
    (form-produces "<input type=checkbox value=yes name=in>"
                   {}))
  (testing "checked checkbox input with no value"
    (form-produces "<input type=checkbox name=in checked>"
                   {"in" "on"}))
  (testing "checked checkbox input"
    (form-produces "<input type=checkbox value=yes name=in checked>"
                   {"in" "yes"}))
  (testing "hidden + unchecked checkbox input"
    (form-produces (str "<input type=hidden value=off name=box>"
                        "<input type=checkbox value=on name=box>")
                   {"box" "off"}))
  (testing "unchecked radio input"
    (form-produces "<input type=radio value=A name=in>"
                   {}))
  (testing "checked radio input"
    (form-produces "<input type=radio value=A name=in checked>"
                   {"in" "A"}))
  (testing "one of many checked radio input"
    (form-produces (str "<input type=radio value=A name=in>"
                        "<input type=radio value=B name=in checked>")
                   {"in" "B"}))
  (testing "multiple inputs with same name"
    (form-produces (str "<input type=checkbox value=A name=in checked>"
                        "<input type=checkbox value=B name=in checked>")
                   {"in" ["A" "B"]}))
  (testing "map of inputs"
    (form-produces (str "<input name=a[b] value=1>"
                        "<input name=a[c] value=2>")
                   {"a[b]" "1" "a[c]" "2"}))
  (testing "select with simple options"
    (form-produces (str "<select name=in>"
                        "<option>One</option>"
                        "<option>Two</option>"
                        "</select>")
                   {"in" "One"}))
  (testing "item chosen select with simple options"
    (form-produces (str "<select name=in>"
                        "<option>One</option>"
                        "<option selected>Two</option>"
                        "</select>")
                   {"in" "Two"}))
  (testing "select with valued options"
    (form-produces (str "<select name=in>"
                        "<option value=1>One</option>"
                        "<option value=2>Two</option>"
                        "</select>")
                   {"in" "1"}))
  (testing "item chosen select with valued options"
    (form-produces (str "<select name=in>"
                        "<option value=1>One</option>"
                        "<option value=2 selected>Two</option>"
                        "</select>")
                   {"in" "2"}))
  (testing "select with optgroup options"
    (form-produces (str "<select name=in>"
                        "<optgroup label=A>"
                        "<option value=1>One</option>"
                        "</optgroup>"
                        "<optgroup label=B>"
                        "<option value=2>Two</option>"
                        "</optgroup>"
                        "</select>")
                   {"in" "1"}))
  (testing "item chosen select with optgroup options"
    (form-produces (str "<select name=in>"
                        "<optgroup label=A>"
                        "<option value=1>One</option>"
                        "</optgroup>"
                        "<optgroup label=B>"
                        "<option value=2 selected>Two</option>"
                        "</optgroup>"
                        "</select>")
                   {"in" "2"}))
  (testing "multiple select with nothing selected"
    (form-produces (str "<select multiple name=in>"
                        "<option value=1>One</option>"
                        "<option value=2>Two</option>"
                        "</select>")
                   {}))
  (testing "multiple select with multiple selected"
    (form-produces (str "<select multiple name=in>"
                        "<option value=1 selected>One</option>"
                        "<option value=2 selected>Two</option>"
                        "</select>")
                   {"in" ["1" "2"]}))
  (testing "empty textarea"
    (form-produces "<textarea name=text></textarea>"
                   {"text" ""}))
  (testing "textarea"
    (form-produces "<textarea name=text>textual content</textarea>"
                   {"text" "textual content"}))
  (testing "unnamed controls"
    (form-produces (str "<input type=text value=unused>"
                        "<select><option>A</option></select>"
                        "<textarea>content</textarea>")
                   {}))
  (testing "disabled controls"
    (form-produces (str "<input type=text name=A disabled>"
                        "<select name=B disabled><option>A</option></select>"
                        "<textarea name=C disabled>content</textarea>")
                   {})))