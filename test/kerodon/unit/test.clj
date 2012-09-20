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
                                 [:label {:for "user"} "User"]
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