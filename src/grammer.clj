(ns grammer
  (:refer-clojure :exclude [replace])
  (:use [useful :only [index-by]]
        [clojure.walk :only [keywordize-keys]]
        [clojure.java.shell :only [sh]]
        [clojure.java.io :only [input-stream output-stream copy]]
        [clojure.string :only [split replace trim-newline]])
  (:require [clj-http.client :as http]
            [clj-json.core :as json]
            [oauth.client :as oauth])
  (:import [java.io File])
  (:gen-class))

(def consumer
  (oauth/make-consumer "WkITiqyL4yzZgEfh1Ez6w"
                       "P6BuoIaxeqtyI7pKZfhi2ldbwvcc7RuMcAjXEbQbyYo"
                       "https://www.yammer.com/oauth/request_token"
                       "https://www.yammer.com/oauth/access_token"
                       "https://www.yammer.com/oauth/authorize"
                       :plaintext))

(defmacro mkdir [& args]
  `(doto (File. ~@args)
     (.mkdirs)))

(defmacro touch [& args]
  `(doto (File. ~@args)
     (.createNewFile)))

(defn conf-dir    [] (mkdir (System/getProperty "user.home") ".grammer"))
(defn access-file [] (touch (conf-dir) "access-token"))
(defn cursor-file [] (touch (conf-dir) "cursor"))

(def access-token  (atom (read-string (str (slurp (access-file)) " nil"))))

(defn signed-params [access-token method url params]
  (merge params
         (oauth/credentials consumer
           (:oauth_token access-token)
           (:oauth_token_secret access-token)
           method url params)))

(def http-method
  {:GET    http/get
   :POST   http/post
   :PUT    http/put
   :DELETE http/delete})

(defn http [method resource params access-token]
  (let [url      (str "https://www.yammer.com/api/v1/" resource ".json")
        params   (signed-params access-token method url params)]
    (when-let [response (try ((http-method method) url {:query-params params})
                             (catch java.net.UnknownHostException e))]
      (keywordize-keys (json/parse-string (:body response))))))

(defn authorize []
  (let [request-token (oauth/request-token consumer)
        approval-uri  (oauth/user-approval-uri consumer request-token)]
    (println "authorize your yammer account at" approval-uri)
    (loop []
      (print "please enter your authorization code: ")
      (flush)
      (if-let [token (try (oauth/access-token consumer request-token (trim-newline (read-line)))
                          (catch java.lang.Exception e))]
        (do (reset! access-token token)
            (spit (access-file) (prn-str token)))
        (recur)))))

(defn notify [title message & [icon]]
  (sh "growlnotify" "-s"
      "-n" "grammer"
      "--image" (str icon)
      "-t" (str title)
      :in message))

(defn get-notifications []
  (let [max-id  (slurp (cursor-file))
        opts    (into {:mark_seen true} (if (empty? max-id) {:count 5} {:newer_than max-id}))
        results (http :GET "notifications" opts @access-token)]
    (when-let [max-id (-> results :notifications first :id)]
      (spit (cursor-file) max-id))
    (let [by-id (partial index-by :id)]
      (-> results
          (update-in [:objects :message] by-id)
          (update-in [:objects :user]    by-id)
          (update-in [:references]       by-id)))))

(defn objects [notification results]
  (for [{:keys [id type]} (reverse (:objects notification))]
    (get-in results [:objects (keyword type) id])))

(defn reference [id results]
  (get-in results [:references id]))

(defn interpolate [string results]
  (replace string #"\[\[\w+:(\d+)\]\]"
           (fn [[_ id]] (:name (reference (Integer. id) results)))))

(defn download [src dest]
  (with-open [dest (output-stream dest)
              src  (input-stream  src)]
    (copy src dest)))

(defn mugshot [user]
  (when-let [url (:mugshot_url user)]
    (let [ext  (last (split url #"\."))
          dest (File. (mkdir (conf-dir) "mugshots") (str (:name user) "." ext))]
      (when-not (.exists dest)
        (download url dest))
      dest)))

(defn mention-or-reply [notification results]
  (doseq [message (objects notification results)]
    (let [sender (-> message :sender_id (reference results))]
      (notify (:name sender)
              (-> message :body :plain)
              (mugshot sender)))))

(defn likes-message [notification results]
  (doseq [message (objects notification results)
          name    (-> message :liked_by :names)]
    (let [sender (-> message :sender_id (reference results))]
      (notify (format "%s likes your message" (:permalink name))
              (-> message :body :plain)
              (mugshot sender)))))

(defn new-follower [notification results]
  (doseq [user (objects notification results)]
    (notify (:name user)
            (format "%s started following you" (:full_name user))
            (mugshot user))))

(defn growl-notifications []
  (let [results (get-notifications)]
    (doseq [notification (reverse (:notifications results))]
      (if-let [handler (ns-resolve 'grammer (symbol (:category notification)))]
        (handler notification results)
        (println "unknown notification category" (:category notification))))
    (:meta results)))

(defn -main []
  (when-not @access-token
    (authorize))
  (loop []
    (let [interval (or (:requested_poll_interval (growl-notifications)) 15)]
      (Thread/sleep (* interval 1000)))
    (recur)))
