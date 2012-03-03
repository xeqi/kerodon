(ns kerodon.test.core
  (:use [kerodon.core]
        [clojure.test])
  (:require [net.cgrand.moustache :as moustache]
            [ring.util.response :as response]
            [ring.middleware.params :as params]
            [ring.middleware.session :as session]
            [hiccup.core :as hiccup]))

(def app
  (params/wrap-params
   (session/wrap-session
    (moustache/app
     [""]
     {:get (fn [{:keys [session]}]
             (if-let [user (:user session)]
               (response/response (str "hi " user))
               (response/response (hiccup/html [:a {:href "/login"} "login"]))))}
     ["login"]
     {:get (constantly
            (response/response
             (hiccup/html [:form {:action "/login" :method "post"}
                           [:label {:for "user"} "User"]
                           [:input {:type "text" :name "user"}]
                           [:label {:for "password"} "Password"]
                           [:input {:type "password" :name "password"}]
                           [:input {:type "submit" :value "Login"}]])))
      :post (fn [{:keys [params]}]
              (if (and (= (params "user")
                          "someone")
                       (= (params "password")
                          "password"))
                (assoc (response/redirect "/") :session {:user "someone"})
                (response/response "Bad login")))}
     ["login-without-labels"]
     {:get (constantly
            (response/response
             (hiccup/html [:form {:action "/login" :method "post"}
                           [:input {:id "user" :type "text" :name "user"}]
                           [:input {:class "password"
                                    :type "password"
                                    :name "password"}]
                           [:input {:type "submit" :value "Login"}]])))}
     ["login-without-method"]
     {:get (constantly
            (response/response
             (hiccup/html [:form {:action "/login"}
                           [:label {:for "user"} "User"]
                           [:input {:type "text" :name "user"}]
                           [:label {:for "password"} "Password"]
                           [:input {:type "password" :name "password"}]
                           [:input {:type "submit" :value "Login"}]])))}
     ["login-without-action"]
     {:get (constantly
            (response/response
             (hiccup/html [:form {:method "post"}
                           [:label {:for "user"} "User"]
                           [:input {:type "text" :name "user"}]
                           [:label {:for "password"} "Password"]
                           [:input {:type "password" :name "password"}]
                           [:input {:type "submit" :value "Login"}]])))
      :post (fn [{:keys [params]}]
              (if (and (= (params "user")
                          "someone")
                       (= (params "password")
                          "password"))
                (assoc (response/redirect "/") :session {:user "someone"})
                (response/response "Bad login")))}
     ["login-with-text-area"]
     {:get (constantly
            (response/response
             (hiccup/html [:form {:action "/login" :method "post"}
                           [:label {:for "user"} "User"]
                           [:textarea {:name "user"}]
                           [:label {:for "password"} "Password"]
                           [:input {:type "password" :name "password"}]
                           [:input {:type "submit" :value "Login"}]])))}
     ["logout"]
     {:post (fn [{:keys [params]}]
              (assoc (response/redirect "/") :session nil))}))))

(deftest good-form
  (-> (session app)
      (visit "/")
      (follow "login")
      (fill-in "User" "someone")
      (fill-in "Password" "password")
      (press "Login")
      (follow-redirect)
      (validate
       #(is (= "hi someone"
               (:body (:response %)))
            "press sends form fields to action url"))))

(deftest selector-not-found
  (-> (session app)
      (visit "/")
      (validate
       #(is (thrown-with-msg? Exception
              #"field could not be found with selector \"NonExistant\""
              (fill-in % "NonExistant" "")))
       #(is (thrown-with-msg? Exception
              #"button could not be found with selector \"NonExistant\""
              (press % "NonExistant"))))))

(deftest form-without-labels
  (-> (session app)
      (visit "/login-without-labels")
      (fill-in "#user" "someone")
      (fill-in ".password" "password")
      (press "Login")
      (follow-redirect)
      (validate
       #(is (= "hi someone"
               (:body (:response %)))
            "fill-in can find input by css"))))

(deftest form-without-action
  (-> (session app)
      (visit "/login-without-action")
      (fill-in "User" "someone")
      (fill-in "Password" "password")
      (press "Login")
      (follow-redirect)
      (validate
       #(is (= "hi someone"
               (:body (:response %)))
            "press sends form fields to same url when action is blank"))))

(deftest form-without-method
  (-> (session app)
      (visit "/login-without-method")
      (fill-in "User" "someone")
      (fill-in "Password" "password")
      (press "Login")
      (follow-redirect)
      (validate
       #(is (= "hi someone"
               (:body (:response %)))
            "press sends form fields to action url with post when method blank"))))

(deftest text-area
  (-> (session app)
      (visit "/login-with-text-area")
      (fill-in "User" "someone")
      (fill-in "Password" "password")
      (press "Login")
      (follow-redirect)
      (validate
       #(is (= "hi someone"
               (:body (:response %)))
            "fill-in and press work for textareas"))))