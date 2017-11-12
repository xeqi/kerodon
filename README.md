# kerodon [![Build Status](https://travis-ci.org/xeqi/kerodon.svg?branch=master)](http://travis-ci.org/xeqi/kerodon) [![Dependencies Status](https://versions.deps.co/xeqi/kerodon/status.svg)](https://versions.deps.co/xeqi/kerodon)

kerodon is an interaction and testing library for [ring](https://github.com/ring-clojure/ring) html based apps. It is intended to look like the interaction a user would have.  It is inspired by [capybara](https://github.com/jnicklas/capybara).

## Dependency Information

[![Clojars Project](http://clojars.org/kerodon/latest-version.svg)](http://clojars.org/kerodon)

kerodon's latest version and information on how to install it is available from [clojars](http://clojars.org/kerodon).

## Usage

### Example

```clojure
(ns myapp.test.integration
  (:use [kerodon.core]
        [kerodon.test]
        [clojure.test])
  (:require [clojure.java.io :as io]))

(deftest user-can-login-and-upload-picture
  ;imagine ring-app is a login required picture upload
  (-> (session ring-app)
      (visit "/")
      (follow "login")
      (fill-in "User:" "username")
      (fill-in "Password:" "password")
      (press "Login")
      (follow-redirect)
      (has (missing? [:#no-such-element])
           "User shouldn't see the no-such-element")

      (follow "update profile")
      (has (attr? [:form] :id "profile"))
      (has (value? "Email:" "example@example.org")
           "Email field defaults to user's email")
      (attach-file "Picture:" (io/file "/tmp/foo.png"))
      (press "update")
      (follow-redirect)

      (within [:#picture]
        (has (text? "foo.png")
             "Picture name is near picture."))
      (within [:#content]
        (has (missing? [:#navigation])))))
```

### Interaction

The api namespace for interaction is ```kerodon.core```.  If you are using kerodon in tests you may want to have ```(:use [kerodon.core])``` in your ```ns``` declaration.  All examples below assume so.

kerodon is based on [peridot](https://github.com/xeqi/peridot), and designed to be used with ->.

#### Initialization

You can create an initial state with ```session```.

```clojure
(session ring-app) ;Use your ring app
```

#### Navigation

##### You can use ```visit``` to send a request to your ring app.

```clojure
(-> (session ring-app) ;Use your ring app
    (visit "/"))
```

You can pass extra arguments like you can to ```peridot.core/request```, but this is not recommended.

##### You can use ``follow-redirect`` to follow a redirect response.

kerodon will not follow redirects automatically. This will throw an ```IllegalArgumentException``` when the last response was not a redirect.

```clojure
(-> (session ring-app) ;Use your ring app
    (visit "/redirect")
    (follow-redirect))
```

##### You can use ```follow``` to follow a link.

```clojure
(-> (session ring-app) ;Use your ring app
    (visit "/")
    (follow "login"))
```

The selector can be the text of the link, or a vector of css elements.

#### Form interaction

##### You can use ```fill-in``` to fill in a form field.

```clojure
(-> (session ring-app) ;Use your ring app
    (visit "/")
    (follow "login")
    (fill-in "User:" "username")
    (fill-in "Password:" "password")
```

The selector can be the text or css of a label with a for attribute, or the css of the field itself.

##### You can use ```choose``` to fill in a combo box.

```clojure
(-> (session ring-app) ;Use your ring app
    (visit "/")
    (follow "login")
    (choose "Issue Type:" "Bug")
    (choose "Parent:" "123")
```

The selector can be the text or css of a label with a for attribute, or the css of the field itself.
The option argument can be the text of the option, or its value.

##### You can use ```check``` to tick a checkbox.

```clojure
(-> (session ring-app) ;Use your ring app
    (visit "/")
    (follow "login")
    (check "Remember Me"))
```

The selector can be the text or css of a label with a for attribute, or the css of the field itself.

##### You can use ```attach-file``` to fill in a file field.

```clojure
(-> (session ring-app) ;Use your ring app
    (visit "/")
    (follow "upload")
    (attach-file "Picture:" (clojure.java.io/file "/tmp/foo")))
```

The selector can be the text or css of a label with a for attribute, or the css of the field itself.

##### You can use ```press``` to submit a form.

```clojure
(-> (session ring-app) ;Use your ring app
    (visit "/")
    (follow "login")
    (fill-in "User:" "username")
    (fill-in "Password:" "password")
    (press "Login"))
```

The selector can be the text or css of a submit button.


### Within

Sometimes you might have multiple html elements that can match.  You can restrict the search space using ```within```.

```clojure
(-> (session ring-app) ;Use your ring app
    (visit "/")
    (within [:#signin2]
      (press "Login")))
```

### Testing

The api namespace for testing is ```kerodon.test```.  This uses the same machinery as ```clojure.test```. If you are using kerodon in tests you may want to have ```(:use [kerodon.test])``` in your ```ns``` declaration.  All examples below assume so.

The main function is ```has```.  It allows the verifications to compose using ->.  It requires one of the verification functions, and an optional error message.

You can use ```status?``` to validate the status code of the last response.
You can use ```text?``` or ```regex?``` to validate the text in the page/element.
You can use ```some-text?``` or ```some-regex?``` to look for text anywhere in the page/element.
Note that for all of ```text?```, ```regex?```, ```some-text?```, and
```some-regex?``` tests replace all one or more consecutive whitespace
characters (i.e., ' ', '\t', '\n', etc.) in the text of the page/element with a
single space.  This feature allows the tests to ignore line breaks and spacing
issues in the source text.
You can use ```value?``` to validate the value of a field.  The
selector can be the text or css of a label with a for element, or the
css of the field itself.
You can use ```attr?``` to validate an attribute's value.
You can use ```link?``` to look for an anchor tag with matching text, href or both.
You can use ```heading?``` to look for a heading (h1 - h6) with matching text.

```clojure
(-> (session ring-app)
    (visit "/hello")
    (has (status? 200)
         "page is found")
    (has (text? "hello world")
         "page says hello world")
    (has (some-text? "world")
         "page includes 'world'"))

(-> (session ring-app)
    (visit "/comment/new")
    (has (value? "name" "anonymous")
         "comments default to anonymous")
    (has (value? "comment" "")
         "comments default empty"))

(-> (session ring-app)
    (visit "/comment/new")
    (has (attr? [:form] :class "comments")))

(-> (session ring-app)
	(visit "/welcome")
	(has (link? "Login")
		 "page has link to login")
	(has (link? :href "/session/new")
		"login link text is 'Login'"))
```

These should all work with ```within```.

## Transactions and database setup

kerodon runs without an http server and, depending on your setup, transactions can be used to rollback and isolate tests.  Some fixtures may be helpful:

```clojure
(use-fixtures :once
              (fn [f]
                (clojure.java.jdbc/with-connection db (f))))
(use-fixtures :each
              (fn [f]
                (clojure.java.jdbc/transaction
                 (clojure.java.jdbc/set-rollback-only)
                 (f))))
```

## Building

[leiningen](https://github.com/technomancy/leiningen) is used as the build tool.  ```lein all test``` will run the test suite against clojure 1.5, 1.6 and 1.7.

## License

Copyright (C) 2013 Nelson Morris and [contributors](https://github.com/xeqi/kerodon/graphs/contributors)

Distributed under the Eclipse Public License, the same as Clojure.
