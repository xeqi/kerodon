(ns kerodon.unit.core
  (:use [kerodon.core]
        [clojure.test]
        [kerodon.test])
  (:require [peridot.request :as request]
            [net.cgrand.moustache :as moustache]
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
          (is (= request (:request state)))))
      (testing "resolves relative links against the URI of the last request"
        (-> (session #(response/response (request/url %)))
            (visit "http://example.com/foo/bar")
            (has (text? "http://example.com/foo/bar"))
            (visit "/fuz/buz")
            (has (text? "http://example.com/fuz/buz"))
            (visit "cats")
            (has (text? "http://example.com/fuz/cats"))
            (visit "/dogs/")
            (has (text? "http://example.com/dogs/"))
            (visit "fish")
            (has (text? "http://example.com/dogs/fish"))
            (visit "https://www.example.com/carbs")
            (has (text? "https://www.example.com/carbs")))))))

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
                 :enlive (parse [:div {}
                                 [:a {:id "go-login" :href "/login"} "login"]
                                 [:a {:href "/login"} "   \n link with whitespaces \n "]])}]
      (testing "text"
        (test-follow-method state "login"))
      (testing "text with whitespaces"
        (test-follow-method state "link with whitespaces"))
      (testing "css"
        (test-follow-method state :#go-login))
      (testing "not found throws exception"
        (is (thrown-with-msg? Exception
              #"link could not be found with selector \"NonExistant\""
              (follow state "NonExistant")))))))

(defn test-press-method [data request test-body]
  (let [state {:app (constantly :x)
               :enlive (parse [:form data
                               [:label {:for "user-id"} "User"]
                               [:input {:id "user-id"
                                        :type "text"
                                        :name "user"
                                        :value "user-value"}]
                               [:label {:for "password-id"} "Password"]
                               [:input {:id "password-id"
                                        :type "password"
                                        :name "password"
                                        :value "password-value"}]
                               [:input {:id "submit-id"
                                        :type "submit"
                                        :value "Login"}]
                               ;; also test for using a submit -button- (instead of input)
                               [:button {:id "submit-id2"
                                         :type "submit"
                                         :value "Login2"}
                                "Button Login"]])
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
          (test-body (:body (:request state))))
        (let [state (press state "Button Login")]
          (is (= request (dissoc (:request state) :body)))
          (test-body (:body (:request state))))))
    (testing "by css"
      (testing "sends from :enlive form"
        (let [state (press state [:#submit-id])]
          (is (= request (dissoc (:request state) :body)))
          (test-body (:body (:request state))))
        (let [state (press state [:#submit-id2])]
          (is (= request (dissoc (:request state) :body)))
          (test-body (:body (:request state))))))
    (testing "not found throws exception"
      (is (thrown-with-msg? Exception
            #"button could not be found with selector \"NonExistant\""
            (press state "NonExistant"))))))

(deftest test-press
  (testing "press"
    (let [query (str "user=user-value&password=password-value")]
      (testing "without method"
        (let [request {:remote-addr "localhost"
                       :scheme :http
                       :request-method :post
                       :query-string nil
                       :content-type "application/x-www-form-urlencoded"
                       :uri "/login"
                       :server-name "localhost"
                       :headers {"content-length" (str (count query))
                                 "content-type"
                                 "application/x-www-form-urlencoded"
                                 "host" "localhost"}
                       :content-length (count query)
                       :server-port 80}
              t #(is (= query (slurp %)))]
          (test-press-method {:action "/login"} request t)
          (testing "or action"
            (test-press-method {} request t))
          (testing "empty action"
            (test-press-method {:action ""} request t))))
      (testing "with method :post"
        (let [request {:remote-addr "localhost"
                       :scheme :http
                       :request-method :post
                       :query-string nil
                       :content-type "application/x-www-form-urlencoded"
                       :uri "/login"
                       :server-name "localhost"
                       :headers {"content-length" (str (count query))
                                 "content-type"
                                 "application/x-www-form-urlencoded"
                                 "host" "localhost"}
                       :content-length (count query)
                       :server-port 80}
              t #(is (= query (slurp %)))]
          (test-press-method {:action "/login" :method :post} request t)
          (testing "without action"
            (test-press-method {:method :post} request t))
          (testing "empty action"
            (test-press-method {:action "" :method :post} request t))))
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
            (test-press-method {:method :get} request t))
          (testing "empty action"
            (test-press-method {:action "" :method :get} request t))))

      (testing "with file input"
        (let [state {:app (constantly :x)
                     :enlive (list
                               {:tag :form
                                :attrs nil
                                :content
                                (list
                                  {:tag :label
                                   :attrs {:for "file-id"}
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
                                   :content nil})})
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

(deftest test-press-with-multiple-buttons
  (let [state {:app (constantly :x)
               :enlive (parse [:form {:action "/login" :method :post}
                               [:label {:for "user-id"} "User"]
                               [:input {:id "user-id"
                                        :type "text"
                                        :name "user"
                                        :value "user-value"}]
                               [:label {:for "password-id"} "Password"]
                               [:input {:id "password-id"
                                        :type "password"
                                        :name "password"
                                        :value "password-value"}]
                               [:input {:id "submit-yes-id"
                                        :type "submit"
                                        :name "submit"
                                        :value "Yes"}]
                               [:input {:id "submit-no-id"
                                        :type "submit"
                                        :name "submit"
                                        :value "No"}]
                               [:input {:id "submit-cancel-id"
                                        :type "submit"
                                        :name "submit"
                                        :value "Cancel"}]])
               :request {:server-port 80
                         :server-name "localhost"
                         :remote-addr "localhost"
                         :uri "/login"
                         :query-string nil
                         :scheme :http
                         :request-method :get
                         :headers {"host" "localhost"}}}]
    (testing "press-with-multiple-buttons"
      (testing "'Yes' button"
        (let [pressed-yes (press state "Yes")
              body (slurp (:body (:request pressed-yes)))]
          (is (= "user=user-value&password=password-value&submit=Yes" body)))))))

(defn form-params [state submit]
  (-> state
      (press submit)
      :request :body slurp))

(defn test-fill-in-helper [control label selector value result]
  (let [state {:app (constantly :x)
               :enlive (parse [:form {:action "/"} control
                               [:input {:type "submit" :value "Submit"}]])}]
    (testing "using label updates form value"
      (let [state (fill-in state label value)]
        (is (= result (form-params state "Submit")))))
    (testing "using selector updates form value"
      (let [state (fill-in state selector value)]
        (is (= result (form-params state "Submit")))))))

(deftest test-fill-in
  (testing "fill-in"
    (testing "text input"
      (test-fill-in-helper '([:label {:for "user-id"} "User"]
                             [:input {:type "text"
                                      :id "user-id" :name "user"}])
                           "User" :#user-id "x" "user=x"))
    (testing "password input"
      (test-fill-in-helper '([:label {:for "password-id"} "Password"]
                             [:input {:type "password"
                                      :id "password-id" :name "password"}])
                           "Password" :#password-id "secret" "password=secret"))
    (testing "textarea"
      (test-fill-in-helper '([:label {:for "message-id"} "Message"]
                             [:textarea {:id "message-id" :name "message"}])
                           "Message" :#message-id "blah" "message=blah"))
    (testing "selector not found throws exception"
      (let [state {:app (constantly :x)
                   :enlive (parse [:form [:input {:name "something"}]])}]
        (is (thrown-with-msg? Exception
              #"field could not be found with selector \":#NonExistant\""
              (fill-in state :#NonExistant "")))))
    (testing "label not found throws exception"
      (let [state {:app (constantly :x)
                   :enlive (parse [:form [:input {:name "something"}]])}]
        (is (thrown-with-msg? Exception
              #"field could not be found with selector \"NonExistant\""
              (fill-in state "NonExistant" "")))))
    (testing "label doesn't match throws exception"
      (let [state {:app (constantly :x)
                   :enlive (parse [:form
                                   [:label {:for "another"} "dangling"]
                                   [:input {:name "something"}]])}]
        (is (thrown-with-msg? Exception
              #"field could not be found with selector \":#another\""
              (fill-in state "dangling" "")))))))

(deftest test-choose
  (letfn [(build-state [control]
            {:app (constantly :x)
             :enlive (parse [:form {:action "/"} control
                             [:input {:type "submit" :value "Submit"}]])})
          (submit [state] (form-params state "Submit"))]
    (testing "choose"
      (testing "select with bare options"
        (let [state (build-state
                      '([:label {:for "colour"} "Colour"]
                        [:select {:name "colour" :id "colour"}
                         [:option "Red"]
                         [:option "Blue"]
                         [:option "Green"]
                         [:option "Orange"]]))]
          (testing "select and option by label"
            (is (= "colour=Green"
                   (-> state (choose "Colour" "Green") submit))))
          (testing "select by selector, option by label"
            (is (= "colour=Orange"
                   (-> state (choose :#colour "Orange") submit))))
          (testing "changing the selection"
            (is (= "colour=Green"
                   (-> state
                       (choose :#colour "Blue")
                       (choose :#colour "Green")
                       submit))))
          (testing "cannot find select"
            (is (thrown-with-msg?
                  Exception
                  #"field could not be found with selector \"NonExistant\""
                  (-> state (choose "NonExistant" "") submit))))
          (testing "select by label, cannot find option"
            (is (thrown-with-msg?
                  Exception
                  #"option could not be found with selector \"NonExistant\""
                  (-> state (choose "Colour" "NonExistant") submit))))
          ))
      (testing "select with value options"
        (let [state (build-state
                      '([:label {:for "colour"} "Colour"]
                        [:select {:name "colour" :id "colour"}
                         [:option {:value "ff0000"} "Red"]
                         [:option {:value "0000ff"} "Blue"]
                         [:option {:value "00ff00"} "Green"]
                         [:option {:value "ff6600"} "Orange"]]))]
          (testing "select and option by label"
            (is (= "colour=00ff00"
                   (-> state (choose "Colour" "Green") submit))))
          (testing "select by selector, option by label"
            (is (= "colour=00ff00"
                   (-> state (choose :#colour "Green") submit))))
          (testing "select by label, option by value"
            (is (= "colour=ff6600"
                   (-> state (choose "Colour" "ff6600") submit))))
          (testing "select by selector, option by value"
            (is (= "colour=ff6600"
                   (-> state (choose :#colour "ff6600") submit))))
          (testing "changing the selection"
            (is (= "colour=0000ff"
                   (-> state
                       (choose "Colour" "ff0000")
                       (choose "Colour" "Blue")
                       submit)))))))))

(deftest test-check-uncheck
  (letfn [(build-state [control]
            {:app (constantly :x)
             :enlive (parse [:form {:action "/"} control
                             [:input {:type "submit" :value "Submit"}]])})
          (submit [state] (form-params state "Submit"))]
    (testing "check / uncheck"
      (testing "unchecked checkbox"
        (let [state (build-state '([:label {:for "remember"} "Remember"]
                                   [:input {:type :checkbox
                                            :value "Yes"
                                            :id "remember"
                                            :name "remember"}]))]
          (testing "check by label"
            (is (= "remember=Yes" (-> state (check "Remember") submit))))
          (testing "check by selector"
            (is (= "remember=Yes" (-> state (check :#remember) submit))))

          (testing "uncheck by label"
            (is (= "" (-> state (uncheck "Remember") submit))))
          (testing "uncheck by selector"
            (is (= "" (-> state (uncheck :#remember) submit))))))
      (testing "checked checkbox"
        (let [state (build-state '([:label {:for "remember"} "Remember"]
                                   [:input {:type :checkbox
                                            :value "Yes"
                                            :checked "checked"
                                            :id "remember"
                                            :name "remember"}]))]
          (testing "check by label"
            (is (= "remember=Yes" (-> state (check "Remember") submit))))
          (testing "check by selector"
            (is (= "remember=Yes" (-> state (check :#remember) submit))))

          (testing "uncheck by label"
            (is (= "" (-> state (uncheck "Remember") submit))))
          (testing "uncheck by selector"
            (is (= "" (-> state (uncheck :#remember) submit))))))
      (testing "checkbox inside label"
        (let [state (build-state '([:label
                                    "Remember"
                                    [:input {:type :checkbox
                                             :value "Yes"
                                             :name "remember"}]]))]
          (is (= "remember=Yes" (-> state (check "Remember") submit)))))
      (testing "label without checkbox"
        (let [state (build-state '([:label
                                    "Empty"]))]
          (is (thrown-with-msg?
                Exception
                #"field inside label could not be found with selector \"Empty\""
                (-> state (check "Empty") submit))))))))

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
                                 [:label {:for "file-id"} "File"]
                                 [:input {:id "file-id"
                                          :type "file"
                                          :name "file"}]])}]
    (letfn [(file-value [state]
              (-> (enlive/select (:enlive state) [:input])
                  first :attrs :value))]
        (testing "by label text"
          (is (= :x (file-value (attach-file state "File" :x)))))
        (testing "by field css"
          (is (= :x (file-value (attach-file state [:#file-id] :x))))))
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
