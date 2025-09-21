Symptom:
```
SyncthingNativeCode: [xxxxx] INFO: listenerSupervisor@dynamic+https://relays.syncthing.net/endpoint: service dynamic+https://relays.syncthing.net/endpoint failed: Get "https://relays.syncthing.net/endpoint": tls: failed to verify certificate: x509: certificate signed by unknown authority
```

Workaround:
- Download the certificates from:
  - https://letsencrypt.org/certs/isrgrootx1.pem
  - https://letsencrypt.org/certs/isrg-root-x2.pem

- Copy the certificates to: /storage/emulated/0/certs
- Go to Settings > Troubleshooting > Environment variables
- Enter the following content:
> SSL_CERT_DIR=/storage/emulated/0/certs
- Exit and restart the app
