(ns isjustok.core
  (:require [clojure.data.json :as json]
            [clj-http.client :as client]
            [clj-http.conn-mgr :as conn]
            [clj-http.core :as core]
            [clojure.core.async :as async :refer [<! >! <!! >!! chan go]])

  (:gen-class))



(def base-url "https://api.open-meteo.com/v1/forecast")

(def DB (reduce (fn [acc entry]
                  (assoc acc (get entry "CapitalName") entry))  
                {} (json/read-str (slurp "resources/capitals.json"))))

(def capitals ["Lisbon" "London" "Madrid" "Paris"])


(defn get-coords
  "extract lat & lon from a city record"
  [city]
  (let [city (DB city)]
    {:latitude (city "CapitalLatitude")
     :longitude (city "CapitalLongitude")}))

(defn get-temp
  "extracts temp from body payload"
  [body]
  (-> body
      json/read-str
      (get-in ["current_weather" "temperature"])))


(defn get-city-temp
  "calls API, if 200 extracts temperature,
   retries on 429"
  [city {:keys [cm hclient] :as hparams}]
  (let [city (get-coords city)
        {:keys [status body]} (client/get base-url {:query-params {:latitude (:latitude city)
                                                                   :longitude (:longitude city)
                                                                   :current_weather true}
                                                    :accept :json
                                                    :throw-exceptions false
                                                    :connection-manager cm
                                                    :http-client hclient})]
    
    (case status
      200 (get-temp body)
      400 "💣"
      429 (do
            (Thread/sleep (* 10 1000))
            (recur city hparams)))))

(defn format-city [[city temp]]
  (format "%s: %s°" city temp))

(defn by-channels []
  (let [c (chan)
        ;;all-cities (keys DB)
        all-cities ["Madrid" "Paris" "London"]
        cm (conn/make-reusable-conn-manager {:threads 10
                                             :default-per-route 5})
        hclient (core/build-http-client {} false cm)]
    (println "Quering for temperatures... ")
    (doseq [city all-cities]
      (go
        (>! c [city (get-city-temp city {:cm cm :hclient hclient})])))

    (println "All requests sent")
    (doseq [_ all-cities]
      
      (println (format-city (<!! c))))))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (by-channels)
  )


(comment
  (get DB "London")
  (take 2 (keys DB))
  (get-city-temp "Madrid")
  (Float/parseFloat "0")
  )
