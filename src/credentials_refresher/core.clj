(ns credentials-refresher.core
  (:require [clojure.core.async :as async]
            [credentials-refresher.saml :as saml]
            [credentials-refresher.credentials :as cred]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import (java.time Duration Instant)
           (com.amazonaws.services.securitytoken AWSSecurityTokenService AWSSecurityTokenServiceClientBuilder)))

(def sts ^AWSSecurityTokenService (AWSSecurityTokenServiceClientBuilder/defaultClient))
(defonce state (atom nil))

(defn start!
  ([]
   (start! {:email    (System/getenv "CR_EMAIL")
            :password (System/getenv "CR_PASSWORD")
            :role     (System/getenv "CR_ROLE")}))
  ([{:keys [email password role] :as user-creds}]
   (if (or (str/blank? email)
           (str/blank? password)
           (str/blank? role))
     (do
       (log/error "Missing credentials, not starting..." (str {:email           email
                                                               :role            role
                                                               :empty-password? (str/blank? password)}))
       false)
     (locking state
       (if (nil? @state)
         (let [stop-signal-chan  (async/chan)
               force-signal-chan (async/chan)
               change-role-chan  (async/chan)]
           (do
             (reset! state {:stop-signal-chan  stop-signal-chan
                            :force-signal-chan force-signal-chan
                            :change-role-chan  change-role-chan})
             (async/thread
               (loop [role role]
                 (log/info "Starting SAML login for" email "/" role)
                 (let [credentials  (saml/assume-role-credentials sts (assoc user-creds :role role))
                       next-refresh (cred/get-expiration credentials)]
                   (log/info "Setting new credentials for" email "/" role "valid to" (str (cred/get-expiration credentials)))
                   (cred/reset-aws-credentials! credentials)

                   (async/alt!!
                     stop-signal-chan
                     :stop

                     force-signal-chan
                     (recur role)

                     change-role-chan
                     ([role]
                      (recur role))

                     (async/timeout (-> (Duration/between (Instant/now) next-refresh)
                                        (.toMillis)))
                     (recur role)))))
             true))
         false)))))

(defn stop! []
  (locking state
    (let [{:keys [stop-signal-chan]} @state]
      (when (some? stop-signal-chan)
        (async/put! stop-signal-chan true)
        (reset! state nil)))
    nil))

(defn refresh! []
  (let [{:keys [force-signal-chan]} @state]
    (when (some? force-signal-chan)
      (async/put! force-signal-chan true))))

(defn change-role [role]
  (let [{:keys [change-role-chan]} @state]
    (when (some? change-role-chan)
      (async/put! change-role-chan role))))