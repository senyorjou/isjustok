(ns isjustok.core
  (:require [clojure.data.json :as json]
            [clj-http.client :as client]
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
  "calls API, if 200 extracts temperature
   - TODO: handle 429 🤔"
  [city]
  (let [city (get-coords city)
        {:keys [status body]} (client/get base-url {:query-params {:latitude (:latitude city)
                                                                   :longitude (:longitude city)
                                                                   :current_weather true}
                                                    :accept :json
                                                    :throw-exceptions false})]
    
    (case status
      200 (get-temp body)
      400 "💣")))

(defn format-city [[city temp]]
  (format "%s: %s°" city temp))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]

  (let [c (chan)
        all-cities (keys DB)]
    (println "Quering for temperatures... ")
    (doseq [city all-cities]
      (go
        (>! c [city (get-city-temp city)])))

    (doseq [_ all-cities]
      (println (format-city (<!! c))))))


(comment
  (get DB "London")
  (take 2 (keys DB))
  (get-city-temp "Madrid")
  (Float/parseFloat "0")
  )
