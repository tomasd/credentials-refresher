(ns credentials-refresher.saml
  (:require
    [buddy.core.codecs]
    [buddy.core.codecs.base64]
    [clojure.string :as string])
  (:import
    (com.amazonaws.services.securitytoken.model AssumeRoleWithSAMLRequest)
    (org.jsoup.nodes FormElement)
    (org.jsoup Jsoup)
    (com.amazonaws.services.securitytoken AWSSecurityTokenServiceClientBuilder AWSSecurityTokenService)
    (java.io ByteArrayInputStream)
    (javax.xml.parsers DocumentBuilderFactory)
    (javax.xml.xpath XPathFactory XPathConstants)
    (org.w3c.dom NodeList Text)
    (java.util Base64)))

(defn create-saml-assertion [email password]
  (let [resp                    (-> (Jsoup/connect "https://adfs.ccta.dk/adfs/ls/IdpInitiatedSignOn.aspx?loginToRp=urn:amazon:webservices")
                                    (.followRedirects true)
                                    (.execute))

        ^FormElement login-form (-> (.parse resp)
                                    (.select "#loginForm")
                                    (.first))
        _                       (-> (.select login-form "#userNameInput")
                                    (.val email))
        _                       (-> (.select login-form "#passwordInput")
                                    (.val password))
        result                  (-> (.submit login-form)
                                    (.cookies (.cookies resp))
                                    (.followRedirects true)
                                    (.execute))
        roles-result            (-> (.parse result)
                                    (.select "form")
                                    ^FormElement (.first)
                                    (.submit)
                                    (.followRedirects true)
                                    (.cookies (.cookies result))
                                    (.post))]
    (-> roles-result
        (.select "form")
        (.first)
        (.select "form [name=SAMLResponse]")
        (.first)
        (.val))))

(defn saml-assertion->principal [saml-assertion role]
  (let [builder  (-> (DocumentBuilderFactory/newInstance)
                     (.newDocumentBuilder))

        document (.parse builder (-> (.decode (Base64/getDecoder) ^String saml-assertion)
                                     (ByteArrayInputStream.)))
        xpath    (-> (XPathFactory/newInstance)
                     (.newXPath)
                     (.compile "//Attribute[@Name='https://aws.amazon.com/SAML/Attributes/Role']/AttributeValue/text()"))
        nodes    ^NodeList (.evaluate xpath document XPathConstants/NODESET)]
    (->> (range (.getLength nodes))
         (map #(-> ^Text (.item nodes %)
                   (.getWholeText)
                   (string/split #",")
                   (->> (zipmap [:provider :role]))))
         (filter #(= (:role %) role))
         (map :provider)
         (first))))

(defn assume-role-credentials [sts {:keys [role email password]}]
  (let [saml-assertion (create-saml-assertion email password)
        principal      (saml-assertion->principal saml-assertion role)
        credentials    (-> sts
                           (.assumeRoleWithSAML (-> (AssumeRoleWithSAMLRequest.)
                                                    (.withRoleArn role)
                                                    (.withPrincipalArn principal)
                                                    (.withSAMLAssertion saml-assertion)))
                           (.getCredentials))]
    credentials))
