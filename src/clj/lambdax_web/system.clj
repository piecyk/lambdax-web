(ns lambdax-web.system
  (:require [lambdax-web.app :as app]
            [lambdax-web.server :as webserver]
            [lambdax-web.scheduler :as scheduler]
            [lambdax-web.events :as events]
            [lambdax-web.config :as config]
            [lambdax-web.repl :as repl]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import java.util.Properties))

(defn version!
  "Return the current version from the version.properties file."
  []
  (let [props (Properties.)]
    (try
      ;; side effect
      (some->> "version.properties"
               io/resource
               io/reader
               (.load props))
      (catch java.io.FileNotFoundException ex
        (println "Handled exception -" (.getMessage ex)))) 
    (.getProperty props "VERSION")))

(defn make-config
  "Creates a default configuration map"
  []
  (-> config/defaults
      (assoc :version
             (if-let [version (version!)]
               version
               "no version found"))
      (merge (into {} [(some->> (:lambdax-web-version env) (hash-map :version))
                       (some->> (:lambdax-web-port env) (Integer/parseInt) (hash-map :port))
                       (some->> (:lambdax-web-nrepl-port env) (Integer/parseInt) (hash-map :nrepl-port))
                       (some->> (:lambdax-web-fetch-every env) (Integer/parseInt) (hash-map :fetch-interval))]))))

(defn new-system
  [config-map]
  (component/system-map
   :app (app/new-app (:name config-map)
                     (:version config-map))
   :scheduler (component/using
                (scheduler/new-scheduler scheduler/fetch-events!
                                         (:fetch-interval config-map))
                [:app])
   :webserver (component/using
                (webserver/new-server (:port config-map)
                                      (:build config-map)
                                      (or (env :access-domain)
                                          (:access-domain config-map))
                                      (:pre-middleware config-map)
                                      (:post-middleware config-map))
                [:app])
   :repl (repl/new-repl-server (:nrepl-port config-map)
                               (:build config-map))))
