(ns kerodon.unit.test
  (:use [clojure.test]
        [kerodon.test])
  (:require [net.cgrand.enlive-html :as enlive]
            [hiccup.core :as hiccup])
  (:import java.io.StringReader))

(defn parse [hiccup-dom]
  (enlive/html-resource (StringReader. (hiccup/html hiccup-dom))))

(defn check-report [map f state]
  (let [a (atom nil)]
    (binding [report (fn [e] (reset! a e))]
      ((f) state "asdf"))
    (let [test-report @a]
      (is (= (:type map) (:type test-report)))
      (is (= (:expected map) (:expected test-report)))
      (if (= (:type map) :error)
        (is ((:actual map) (:actual test-report)))
        (is (= (:actual map))  (:actual test-report)))
      (if (= (:type map) :fail)
        (is (= "test.clj" (:file test-report))))
      (is (= "asdf" (:message test-report))))))

(deftest test-value?
  (testing "value?"
    (let [state {:enlive (parse [:form
                                 [:label {:for "user-id"} "User"]
                                 [:input {:id "user-id"
                                          :type "text"
                                          :name "user"
                                          :value "user-value"}]
                                 [:label {:for "area"} "Area"]
                                 [:textarea {:id "area-id"
                                             :name "area"
                                             :value "area-value"}]])}]
      (testing "fails if value is wrong"
        (check-report {:type :fail
                       :expected '(value? "User" 3)
                       :actual "user-value"}
                      #(value? "User" 3)
                      state))
      (testing "errors if field is missing"
        (check-report {:type :error
                       :expected '(value? "Unknown" 3)
                       :actual (fn [v]
                                 (re-find
                                  #"field could not be found with selector \"Unknown\""
                                  (.getMessage v)))}
                      #(value? "Unknown" 3)
                      state))
      (testing "passes if =, field found by"
        (testing "name"
          (check-report {:type :pass
                         :expected '(value? "User" "user-value")
                         :actual "user-value"}
                        #(value? "User" "user-value")
                        state))
        (testing "css"
          (check-report {:type :pass
                         :expected '(value? [:#user-id] "user-value")
                         :actual "user-value"}
                        #(value? [:#user-id] "user-value")
                        state))))))

(deftest test-status?
  (testing "status?"
    (testing "fails if status is wrong"
      (check-report {:type :fail
                     :expected '(status? 200)
                     :actual 404}
                    #(status? 200)
                    {:response {:status 404}}))
    (testing "passes if status is right"
      (check-report {:type :pass
                     :expected '(status? 200)
                     :actual 200}
                    #(status? 200)
                    {:response {:status 200}}))))

(deftest test-text?
  (testing "text?"
    (let [state {:enlive (parse [:p "yes"])}]
      (testing "fails if text is wrong"
        (check-report {:type :fail
                       :expected '(text? "no")
                       :actual "yes"}
                      #(text? "no")
                      state))
      (testing "passes if text is right"
        (check-report {:type :pass
                       :expected '(text? "yes")
                       :actual "yes"}
                      #(text? "yes")
                      state)))))

(deftest test-text-in?
  (testing "text-in?"
    (let [state {:enlive (parse [:p "this is a test"])}]
      (testing "fails if text is not found"
        (check-report {:type :fail
                       :expected '(text-in? "foo")
                       :actual "this is a test"}
                      #(text-in? "foo")
                      state))
      (testing "fails if content is subset of matcher"
        (check-report {:type :fail
                       :expected '(text-in? "and this is a test")
                       :actual "this is a test"}
                      #(text-in? "and this is a test")
                      state))
      (testing "passes if text is found"
        (check-report {:type :pass
                       :expected '(text-in? "is a")
                       :actual "this is a test"}
                      #(text-in? "is a")
                      state)))))

(deftest test-regex?
  (testing "regex?"
    (let [state {:enlive (parse [:p "foobar"])}]
      (testing "fails if regex does not match"
        (check-report {:type :fail
                       :expected '(regex? "f.*rbaz")
                       :actual "foobar"}
                      #(regex? "f.*rbaz")
                      state))
      (testing "passes if regex matches"
        (check-report {:type :pass
                       :expected '(regex? "f.*r")
                       :actual "foobar"}
                      #(regex? "f.*r")
                      state)))))

(deftest test-regex-in?
  (testing "regex-in?"
    (let [state {:enlive (parse [:p "Account Number: #12345"])}]
      (testing "fails if regex is not found"
        (check-report {:type :fail
                       :expected '(regex-in? "\\d{6}")
                       :actual "Account Number: #12345"}
                      #(regex-in? "\\d{6}")
                      state))
      (testing "passes if regex is found"
        (check-report {:type :pass
                       :expected '(regex-in? "\\d{5}")
                       :actual "Account Number: #12345"}
                      #(regex-in? "\\d{5}")
                      state)))))

(deftest test-attr?
  (testing "attr?"
    (let [state {:enlive (parse [:p {:data-url "something"} "yes"])}]
      (testing "fails if"
        (testing "attribute is"
          (testing "wrong"
            (check-report {:type :fail
                           :expected '(attr? [:p] :data-url "x")
                           :actual "something"}
                          #(attr? [:p] :data-url "x")
                          state))
          (testing "missing"
            (check-report {:type :fail
                           :expected '(attr? [:p] :unknown "x")
                           :actual ""}
                          #(attr? [:p] :unknown "x")
                          state)))
        (testing "element is missing"
          (check-report {:type :fail
                         :expected '(attr? [:tr] :data-url "")
                         :actual ""}
                        #(attr? [:tr] :data-url "")
                        state)))
      (testing "passes if attribute is right"
        (check-report {:type :pass
                       :expected '(attr? [:p] :data-url "something")
                       :actual "something"}
                      #(attr? [:p] :data-url "something")
                      state)))))

(deftest test-link?
  (testing "link?"
    (let [state {:enlive (parse [:p "Click " [:a {:href "#"} "here"] " to login"])}]
      (testing "fails if link is not found"
        (check-report {:type :fail
                       :expected '(link? "somewhere")
                       :actual '({:href "#" :text "here"})}
                      #(link? "somewhere")
                      state))
      (testing "passes if link is found"
        (check-report {:type :pass
                       :expected '(link? "here")
                       :actual [{:href "#" :text "here"}]}
                      #(link? "here")
                      state)))))

(deftest test-heading?
  (testing "heading?"
    (let [state {:enlive (parse [:section [:p "Foo"] [:h2 "FizzBuzz"] [:p "Bar"]])}]
      (testing "fails if heading is not found"
        (check-report {:type :fail
                       :expected '(heading? "Foo")
                       :actual ["FizzBuzz"]}
                      #(heading? "Foo")
                      state))
      (testing "passes if heading is found"
        (check-report {:type :pass
                       :expected '(heading? "FizzBuzz")
                       :actual ["FizzBuzz"]}
                      #(heading? "FizzBuzz")
                      state)))))

(deftest test-missing?
  (testing "missing?"
    (let [state {:enlive (parse [:div])}]
      (testing "fails if element exists"
        (check-report {:type :fail
                       :expected '(missing? [:div])
                       :actual 1}
                      #(missing? [:div])
                      state))
      (testing "passes if element does not exist"
        (check-report {:type :pass
                       :expected '(missing? [:p])
                       :actual 0}
                      #(missing? [:p])
                      state)))))
