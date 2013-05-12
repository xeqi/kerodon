(ns kerodon.functional.example
  (:use [kerodon.core]
        [clojure.test]
        [kerodon.test])
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
               (response/response
                (hiccup/html [:a {:href "/login"} "login"]))))}
     ["login"]
     {:get (constantly
            (response/response
             (hiccup/html [:form {:action "/login" :method "post"}
                           [:label {:for "user"} "User"]
                           [:input {:type "text" :name "user"}]
                           [:label {:for "password"} "Password"]
                           [:input {:type "password" :name "password"}]
                           [:label {:for "type"} "Type"]
                           [:select {:name "type"}
                            [:option "Administrator"]
                            [:option "Standard"]]
                           [:input {:type "submit" :value "Login"}]])))
      :post (fn [{:keys [params]}]
              (if (and (= (params "user") "someone")
                       (= (params "password") "password"))
                (assoc (response/redirect "/") :session {:user "someone"})
                (response/response "Bad login")))}))))

(deftest user-can-login-and-see-name
  (-> (session app)
      (visit "/")
      (follow "login")
      (fill-in "User" "someone")
      (fill-in "Password" "password")
      (choose "Type" "Standard")
      (press "Login")
      (follow-redirect)
      (has (text? "hi someone"))))