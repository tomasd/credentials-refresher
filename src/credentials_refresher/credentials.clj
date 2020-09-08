(ns credentials-refresher.credentials
  (:require [clojure.tools.logging :as log])
  (:import (com.amazonaws.auth DefaultAWSCredentialsProviderChain)
           (com.amazonaws.services.securitytoken.model Credentials)
           (java.time ZoneId)))


(defn get-expiration [^Credentials credentials]
  (-> (.getExpiration credentials)
      (.toInstant)
      (.atZone (ZoneId/systemDefault)))
  )
(defn refresh-aws-credentials! []
  (doto (DefaultAWSCredentialsProviderChain/getInstance)
    (.setReuseLastProvider false)
    (.refresh)))

(defn set-aws-credentials! [^Credentials credentials]
  (System/setProperty "aws.accessKeyId" (.getAccessKeyId credentials))
  (System/setProperty "aws.secretKey" (.getSecretAccessKey credentials))
  (System/setProperty "aws.sessionToken" (.getSessionToken credentials)))

(defn reset-aws-credentials! [^Credentials credentials]
  (set-aws-credentials! credentials)
  (refresh-aws-credentials!))

