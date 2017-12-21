(ns ^{:doc "Bandwidth.com telecom API driver"}
  arctype.service.io.bandwidth
  (:import
    [org.apache.commons.codec.binary Base64])
  (:require
    [clojure.core.async :as async]
    [cheshire.core :as json]
    [clojure.tools.logging :as log]
    [schema.core :as S]
    [sundbry.resource :as resource :refer [with-resources]]
    [arctype.service.util :refer [map-vals xform-validator]]
    [arctype.service.io.http.client :as http-client]))

(def Config
  {:api-token S/Str
   :api-secret S/Str
   :user-id S/Str
   :default-from S/Str ; Default from phone number
   (S/optional-key :endpoint) S/Str
   (S/optional-key :http) http-client/Config})

(def default-config
  {:endpoint "https://api.catapult.inetwork.com"
   :http {:throttle
          {:rate 1
           :period :second
           :burst 1}}})

(defn- api-post-request
  [{{api-token :api-token
     api-secret :api-secret
     user-id :user-id
     endpoint :endpoint} :config}
   method 
   params]
  (let [auth (Base64/encodeBase64String (.getBytes (str api-token ":" api-secret)))]
    {:url (str endpoint "/v1/users/" user-id method)
     :method :post
     :headers {"Content-Type" "application/json; charset=utf-8"
               "Authorization" (str "Basic " auth)}
     :body (json/encode params)}))

(def xform-response
  (http-client/xform-response
    {:200 (fn [{:keys [body]}] (json/decode body true))
     :201 (fn [{:keys [body]}] (json/decode body true))}))

(defn- response-chan
  []
  (async/chan 1 xform-response))

(def SendSMSParams
  {(S/optional-key :from) S/Str
   :to S/Str
   :text S/Str})

(S/defn send-sms!
  "Send an SMS message."
  [{:keys [config] :as this}
   params :- SendSMSParams]
  (with-resources this [:http]
    (let [params (cond-> params
                   (nil? (:from params)) (assoc :from (:default-from config)))
          req (api-post-request this "/messages" params)]
      (http-client/request! http req (response-chan)))))

(defrecord BandwidthClient [config])

(S/defn create
  [resource-name
   config :- Config]
  (let [config (merge default-config config)]
    (resource/make-resource
      (map->BandwidthClient
        {:config config})
      resource-name nil
      [(http-client/create :http (:http config))])))
