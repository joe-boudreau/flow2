#!/bin/bash

CERT_PEM_URL=$1
CERT_PASS=$2

CERT_PEM_FILE=global-bundle.pem
curl "$CERT_PEM_URL" -o $CERT_PEM_FILE

pemToJks()
{
        # number of certs in the PEM file
        pemCerts=$1
        certPass=$2
        newCert=$(basename "$pemCerts")
        newCert="${newCert%%.*}"
        newCert="${newCert}"".JKS"
        ##echo $newCert $pemCerts $certPass
        CERTS=$(grep 'END CERTIFICATE' $pemCerts| wc -l)
        echo $CERTS
        # For every cert in the PEM file, extract it and import into the JKS keystore
        # awk command: step 1, if line is in the desired cert, print the line
        #              step 2, increment counter when last line of cert is found
        for N in $(seq 0 $(($CERTS - 1))); do
          ALIAS="${pemCerts%.*}-$N"
          cat $pemCerts |
                awk "n==$N { print }; /END CERTIFICATE/ { n++ }" |
                keytool -noprompt -import -trustcacerts \
                                -alias "$ALIAS" -keystore cacerts -storepass "$certPass"
        done
}
pemToJks "$CERT_PEM_FILE" "$CERT_PASS"