(ns kerodon.unit.core
  (:use [kerodon.core]
        [clojure.test]
        [kerodon.test])
  (:require [net.cgrand.moustache :as moustache]
            [ring.util.response :as response]
            [ring.middleware.params :as params]
            [ring.middleware.session :as session]
            [ring.middleware.multipart-params :as multipart-params]
            [net.cgrand.enlive-html :as enlive]
            [hiccup.core :as hiccup]
            [clojure.java.io :as io])
  (:import java.io.StringReader))

(defn parse [hiccup-dom]
  (enlive/html-resource (StringReader. (hiccup/html hiccup-dom))))

(defn get-value [state css]
  (-> (:enlive state)
      (enlive/select css)
      first
      :attrs
      :value))

(defn get-content [state css]
  (-> (:enlive state)
      (enlive/select css)
      first
      :content))

(deftest test-session
  (testing "session returns state with the app"
    (is (= (:app (session :x)) :x))))

(deftest test-visit
  (testing "visit"
    (let [state (visit {:app (constantly :x)} "/")]
      (testing "has response from server"
        (is (= :x (:response state))))
      (testing "has correct request"
        (let [request {:server-port 80
                       :server-name "localhost"
                       :remote-addr "localhost"
                       :uri "/"
                       :query-string nil
                       :scheme :http
                       :request-method :get
                       :headers {"host" "localhost"}
                       :body nil}]
          (is (= request (:request state))))))))

(defn test-follow-method [state selector]
  (let [state (follow state selector)]
      (testing "has response from server"
        (is (= :x (:response state))))
      (testing "has correct request"
        (let [request {:server-port 80
                       :server-name "localhost"
                       :remote-addr "localhost"
                       :uri "/login"
                       :query-string nil
                       :scheme :http
                       :request-method :get
                       :headers {"host" "localhost"}
                       :body nil}]
          (is (= request (:request state)))))))

(deftest test-follow
  (testing "follow by link"
    (let [state {:app (constantly :x)
                 :enlive (parse [:a {:id "go-login"
                                     :href "/login"} "login"])}]
      (testing "text"
        (test-follow-method state "login"))
      (testing "css"
        (test-follow-method state :#go-login))
      (testing "not found throws exception"
        (is (thrown-with-msg? Exception
              #"link could not be found with selector \"NonExistant\""
              (follow state "NonExistant")))))))

(defn test-fill-in-method [state user password textarea]
  (testing "for text input changes :enlive form"
    (let [state (fill-in state user "x")]
      (is (= "x" (get-value state [:#user-id])))))
  (testing "for password input changes :enlive form"
    (let [state (fill-in state password  "x")]
      (is (= "x" (get-value state [:#password-id])))))
  (testing "for text area changes :enlive form"
    (let [state (fill-in state textarea "x")]
      (is (= ["x"] (get-content state [:#textarea-id]))))))

(deftest test-fill-in
  (testing "fill-in"
    (let [state {:app (constantly :x)
                 :enlive (parse [:form
                                 [:label {:for "user"} "User"]
                                 [:input {:id "user-id"
                                          :type "text"
                                          :name "user"}]
                                 [:label {:for "password"} "Password"]
                                 [:input {:id "password-id"
                                          :type "password"
                                          :name "password"}]
                                 [:label {:for "area"} "Area"]
                                 [:textarea {:id "textarea-id"
                                             :name "area"}]])}]
      (testing "by label text"
        (test-fill-in-method state "User" "Password" "Area"))
      (testing "by field css"
        (test-fill-in-method state [:#user-id] [:#password-id] [:#textarea-id]))
      (testing "not found throws exception"
        (is (thrown-with-msg? Exception
              #"field could not be found with selector \"NonExistant\""
              (fill-in state "NonExistant" "")))))))

(defn test-press-method [data request test-body]
  (let [state {:app (constantly :x)
               :enlive (parse [:form data
                               [:label {:for "user"} "User"]
                               [:input {:id "user-id"
                                        :type "text"
                                        :name "user"
                                        :value "user-value"}]
                               [:label {:for "password"} "Password"]
                               [:input {:id "password-id"
                                        :type "password"
                                        :name "password"
                                        :value "password-value"}]
                               [:label {:for "area"} "Area"]
                               [:textarea {:id "textarea-id"
                                           :name "area"}
                                "area-value"]
                               [:input {:id "submit-id"
                                        :type "submit"
                                        :value "Login"}]])
               :request {:server-port 80
                         :server-name "localhost"
                         :remote-addr "localhost"
                         :uri "/login"
                         :query-string nil
                         :scheme :http
                         :request-method :get
                         :headers {"host" "localhost"}}}]
    (testing "by value"
      (testing "sends from :enlive form"
        (let [state (press state "Login")]
          (is (= request (dissoc (:request state) :body)))
          (test-body (:body (:request state))))))
    (testing "by css"
      (testing "sends from :enlive form"
        (let [state (press state [:#submit-id])]
          (is (= request (dissoc (:request state) :body)))
          (test-body (:body (:request state))))))
    (testing "not found throws exception"
      (is (thrown-with-msg? Exception
            #"button could not be found with selector \"NonExistant\""
            (press state "NonExistant"))))))

(deftest test-press
  (testing "press"
    (let [query "user=user-value&password=password-value&area=area-value"]
      (testing "without method"
        (let [request {:remote-addr "localhost"
                       :scheme :http
                       :request-method :post
                       :query-string nil
                       :content-type "application/x-www-form-urlencoded"
                       :uri "/login"
                       :server-name "localhost"
                       :headers {"content-length" "55"
                                 "content-type"
                                 "application/x-www-form-urlencoded"
                                 "host" "localhost"}
                       :content-length 55
                       :server-port 80}
              t #(is (= query (slurp %)))]
          (test-press-method {:action "/login"} request t)
          (testing "or action"
            (test-press-method {} request t))))
      (testing "with method :post"
        (let [request {:remote-addr "localhost"
                       :scheme :http
                       :request-method :post
                       :query-string nil
                       :content-type "application/x-www-form-urlencoded"
                       :uri "/login"
                       :server-name "localhost"
                       :headers {"content-length" "55"
                                 "content-type"
                                 "application/x-www-form-urlencoded"
                                 "host" "localhost"}
                       :content-length 55
                       :server-port 80}
              t #(is (= query (slurp %)))]
          (test-press-method {:action "/login" :method :post} request t)
          (testing "without action"
            (test-press-method {:method :post} request t))))
      (testing "with method :get"
        (let [request {:remote-addr "localhost"
                       :scheme :http
                       :request-method :get
                       :query-string query
                       :uri "/login"
                       :server-name "localhost"
                       :headers {"host" "localhost"}
                       :server-port 80}
              t (fn [x])]
          (test-press-method {:action "/login" :method :get} request t)
          (testing "without action"
            (test-press-method {:method :get} request t))))

      (testing "with file input"
        (let [state {:app (constantly :x)
                     :enlive (list
                              {:tag :html
                               :attrs nil
                               :content
                               (list
                                {:tag :body
                                 :attrs nil
                                 :content
                                 (list
                                  {:tag :form
                                   :attrs nil
                                   :content
                                   (list
                                    {:tag :label
                                     :attrs {:for "file"}
                                     :content '("File")}
                                    {:tag :input
                                     :attrs {:value (proxy [java.io.File]
                                                        ["file"])
                                             :type "file"
                                             :name "file"
                                             :id "file-id"}
                                     :content nil}
                                    {:tag :input
                                     :attrs {:value "Login"
                                             :type "submit"
                                             :id "submit-id"}
                                     :content nil})})})})
                     :request {:server-port 80
                               :server-name "localhost"
                               :remote-addr "localhost"
                               :uri "/login"
                               :query-string nil
                               :scheme :http
                               :request-method :get
                               :headers {"host" "localhost"}}}
              request {:remote-addr "localhost"
                       :scheme :http
                       :request-method :post
                       :query-string nil
                       :uri "/login"
                       :server-name "localhost"
                       :headers {"host" "localhost"}
                       :server-port 80}]
          (testing "by value"
            (testing "sends from :enlive form"
              (let [state (press state "Login")]
                (is (= request (-> (:request state)
                                   (dissoc :body)
                                   (dissoc :content-type)
                                   (update-in [:headers] dissoc "content-type")
                                   (dissoc :content-length)
                                   (update-in [:headers] dissoc
                                              "content-length"))))
                (is (re-find #"multipart/form-data;" (:content-type
                                                      (:request state)))))))
          (testing "by css"
            (testing "sends from :enlive form"
              (let [state (press state [:#submit-id])]
                (is (= request (-> (:request state)
                                   (dissoc :body)
                                   (dissoc :content-type)
                                   (update-in [:headers] dissoc "content-type")
                                   (dissoc :content-length)
                                   (update-in [:headers] dissoc
                                              "content-length"))))
                (is (re-find #"multipart/form-data;" (:content-type
                                                      (:request state))))))))))))

(deftest test-follow-redirect
  (testing "follow-redirect"
    (testing "sends request to redirected url"
      (let [state {:app (constantly :x)
                   :response {:status 302
                              :headers {"Location" "/url"}
                              :body ""}
                   :request {:remote-addr "localhost"
                             :scheme :http
                             :request-method :get
                             :query-string nil
                             :uri "/"
                             :server-name "localhost"
                             :headers {"host" "localhost"}
                             :server-port 80}}
            request {:remote-addr "localhost"
                     :scheme :http
                     :request-method :get
                     :query-string nil
                     :uri "/url"
                     :server-name "localhost"
                     :headers {"host" "localhost"
                               "referrer" "http://localhost/"}
                     :server-port 80
                     :body nil}]
        (is (= request (:request (follow-redirect state))))))
    (testing "throws error if previous response was not a redirect"
      (let [state {:app (constantly :x)
                   :response {:status 200 :body ""}}]
        (is (thrown-with-msg? IllegalArgumentException
              #"Previous response was not a redirect"
              (follow-redirect state)))))))

(deftest test-attach-file
  (testing "attach-file"
    (let [state {:enlive (parse [:form
                                 [:label {:for "file"} "File"]
                                 [:input {:id "file-id"
                                          :type "file"
                                          :name "file"}]])}]
      (let [expected {:enlive
                      '({:tag :html
                         :attrs nil
                         :content
                         ({:tag :body
                           :attrs nil
                           :content
                           ({:tag :form
                             :attrs nil
                             :content
                             ({:tag :label
                               :attrs {:for "file"}
                               :content ("File")}
                              {:tag :input
                               :attrs {:value :x
                                       :type "file"
                                       :name "file"
                                       :id "file-id"}
                               :content ()})})})})}]
        (testing "by label text"
          (is (= expected (attach-file state "File" :x))))
        (testing "by field css"
          (is (= expected (attach-file state [:#file-id] :x)))))
      (testing "not found throws exception"
        (is (thrown-with-msg? Exception
              #"field could not be found with selector \"NonExistant\""
              (attach-file state "NonExistant" :x)))))))

(deftest test-within
  (testing "within"
    (let [state {:enlive (parse [:div {:id "outer"}
                                 [:div {:id "inner"}]])}]
      (testing "mutates :enlive for body params"
        (let [expected {:enlive '({:tag :div
                                   :attrs {:id "inner"}
                                   :content nil})}]
          (let [state (within state [:#inner]
                              ((fn [state] (is (= expected state))
                                 (assoc state :enlive nil))))]
            (testing "and for return value"
              (is (= '{:enlive
                       ({:tag :html :attrs nil
                         :content ({:tag :body :attrs nil
                                    :content ({:tag :div :attrs {:id "outer"}
                                               :content ()})})})}
                     state))))
          (let [state (within state [:#inner]
                              ((fn [state] (is (= expected state))
                                 (-> state
                                     (assoc :enlive nil)
                                     (assoc :request :x)))))]
            (testing "but not for return value when :request is different"
              (is (= nil (:enlive state)))))
          (let [state (within state [:#inner]
                              ((fn [state] (is (= expected state))
                                 (-> state
                                     (assoc :enlive nil)
                                     (assoc :response :x)))))]
            (testing "but not for return value when :response is different"
              (is (= nil (:enlive state))))))))))
