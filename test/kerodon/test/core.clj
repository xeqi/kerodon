(ns kerodon.test.core
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
            [clojure.java.io :as io]))

(def app
  (params/wrap-params
   (session/wrap-session
    (moustache/app
     [""]
     {:get (fn [{:keys [session]}]
             (if-let [user (:user session)]
               (response/response (str "hi " user))
               (response/response (hiccup/html [:a {:id "go-login" :href "/login"} "login"]))))}
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
     ["login-with-css"]
     {:get (constantly
            (response/response
             (hiccup/html [:form {:action "/login" :method "post"}
                           [:input {:id "user" :type "text" :name "user"}]
                           [:input {:class "password"
                                    :type "password"
                                    :name "password"}]
                           [:input {:id "some-button"
                                    :type "submit"
                                    :value "Login"}]])))}
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
              (assoc (response/redirect "/") :session nil))}
     ["login-x2"]
     {:get (constantly
            (response/response
             (hiccup/html [:div
                           [:form {:id "1"}
                            [:label {:for "user"} "User"]
                            [:input {:type "text" :name "user"}]
                            [:label {:for "password"} "Password"]
                            [:input {:type "password" :name "password"}]
                            [:input {:type "submit" :value "Login"}]]
                           [:form {:id "2"}
                            [:label {:for "user"} "User"]
                            [:input {:type "text" :name "user"}]
                            [:label {:for "password"} "Password"]
                            [:input {:type "password" :name "password"}]
                            [:input {:type "submit" :value "Login"}]]])))
      :post (constantly (response/response "hi"))}
     ["title"]
     {:get (constantly
            (response/response
             (hiccup/html [:html
                           [:head [:title "some title"]]
                           [:body]])))}))))

(def upload-app
  (multipart-params/wrap-multipart-params
   (params/wrap-params
    (moustache/app
     ["upload"]
     {:get (constantly
            (response/response
             (hiccup/html
              [:form {:enctype "multipart/form-data"}
               [:label {:for "file"} "File"]
               [:input {:id "file" :name "file" :type "file"}]
               [:input {:type "submit" :value "upload"}]])))
      :post (fn [{:keys [params body]}]
              (response/response
               (hiccup/html [:html
                             [:body
                              (when-let [file (:tempfile (params "file"))]
                                (slurp file))]])))}))))

(def upload-app-with-params
  (multipart-params/wrap-multipart-params
   (params/wrap-params
    (moustache/app
     ["upload"]
     {:get (constantly
            (response/response
             (hiccup/html
              [:form {:enctype "multipart/form-data"}
               [:label {:for "u"} "Name"]
               [:input {:type "text" :name "u"}]
               [:input {:id "file" :name "file" :type "file"}]
               [:input {:type "submit" :value "upload"}]])))
      :post (fn [{:keys [params body]}]
              (response/response
               (hiccup/html [:html
                             [:body
                              (str (params "u")
                                   " "
                                   (when-let [file (:tempfile (params "file"))]
                                     (slurp file)))]])))}))))

(deftest good-form
  (-> (session app)
      (visit "/")
      (follow "login")
      (fill-in "User" "someone")
      (fill-in "Password" "password")
      (press "Login")
      (follow-redirect)
      (has (text? "hi someone")
           "press sends form fields to action url")))

(deftest selector-not-found
  (let [state (-> (session app)
                  (visit "/"))]
    (is (thrown-with-msg? Exception
          #"field could not be found with selector \"NonExistant\""
          (fill-in state "NonExistant" "")))
    (is (thrown-with-msg? Exception
          #"button could not be found with selector \"NonExistant\""
          (press state "NonExistant")))
    (is (thrown-with-msg? Exception
          #"link could not be found with selector \"NonExistant\""
          (follow state "NonExistant")))))

(deftest follow-by-css
  (-> (session app)
      (visit "/")
      (follow [:#go-login])
      (has (text? "UserPassword"))))

(deftest form-by-css
  (-> (session app)
      (visit "/login-with-css")
      (fill-in [:#user] "someone")
      (fill-in [:.password] "password")
      (press [:#some-button])
      (follow-redirect)
      (has (text? "hi someone")
           "fill-in and press can find by css")))

(deftest form-without-action
  (-> (session app)
      (visit "/login-without-action")
      (fill-in "User" "someone")
      (fill-in "Password" "password")
      (press "Login")
      (follow-redirect)
      (has (text? "hi someone")
           "press sends form fields to same url when action is blank")))

(deftest form-without-method
  (-> (session app)
      (visit "/login-without-method")
      (fill-in "User" "someone")
      (fill-in "Password" "password")
      (press "Login")
      (follow-redirect)
      (has (text? "hi someone")
           "press sends form fields to action url with post when method blank")))

(deftest text-area-can-be-filled
  (-> (session app)
      (visit "/login-with-text-area")
      (fill-in "User" "someone")
      (fill-in "Password" "password")
      (press "Login")
      (follow-redirect)
      (has (text? "hi someone")
           "fill-in and press work for textareas")))

(deftest fill-in-can-be-checked-with-value?
  (-> (session app)
      (visit "/login")
      (fill-in "User" "someone")
      (has (value? "User" "someone"))))

(deftest within-generic
  (-> (session app)
      (visit "/login-x2")
      (within [:#2]
              (fill-in "User" "someone"))
      (within [:#2]
              (has (value? "User" "someone")))
      (within [:#1]
              (has (value? "User" "")))
      (within [:#2]
              (press "Login"))
      (has (text? "hi"))))

(deftest status-generic
  (-> (session app)
      (visit "/login")
      (has (status? 200))
      (visit "/non-existant")
      (has (status? 404))))

(deftest title-generic
  (-> (session app)
      (visit "/title")
      (within [:title]
              (has (text? "some title")))))

(deftest test-attrs
  (-> (session app)
      (visit "/login-x2")
      (has (attr? [:form#1 :label] :for "user"))
      (within [:div]
              (has (attr? [:form] :id "1")))))

(deftest attach-file-alone
  (-> (session upload-app)
      (visit "/upload")
      (attach-file [:#file] (io/file (io/resource "file.txt")))
      (press "upload")
      (doto
          (#(is (re-find #"multipart/form-data;"
                         (:content-type (:request %))))))
      (has (text? "hi from uploaded file\n"))))

(deftest attach-file-by-label
  (-> (session upload-app)
      (visit "/upload")
      (attach-file "File" (io/file (io/resource "file.txt")))
      (press "upload")
      (doto
          (#(is (re-find #"multipart/form-data;"
                         (:content-type (:request %))))))
      (has (text? "hi from uploaded file\n"))))

(deftest attach-file-with-params
  (-> (session upload-app-with-params)
      (visit "/upload")
      (fill-in "Name" "name")
      (attach-file [:#file] (io/file (io/resource "file.txt")))
      (press "upload")
      (has (text? "name hi from uploaded file\n"))))
