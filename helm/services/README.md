# Service Helm charts

Each service chart owns its ConfigMap, Deployment, and Service. Install the
infrastructure-facing services before the business services:

```powershell
helm upgrade --install configserver .\helm\services\configserver --wait --timeout 3m
helm upgrade --install eurekaserver .\helm\services\eurekaserver --wait --timeout 3m
helm upgrade --install account .\helm\services\account --wait --timeout 3m
helm upgrade --install card .\helm\services\card --wait --timeout 3m
helm upgrade --install loan .\helm\services\loan --wait --timeout 3m
helm upgrade --install messageservice .\helm\services\messageservice --wait --timeout 3m
helm upgrade --install gatewayserver .\helm\services\gatewayserver --wait --timeout 3m
```

The Config Server chart deliberately does not contain an encryption key. Either
keep an existing `configserver-secrets` Secret, or create it from a local values
file that is not committed to Git:

```yaml
# helm/configserver-secret.local.yaml
secret:
  create: true
  encryptKey: replace-with-a-real-secret
```

```powershell
helm upgrade --install configserver .\helm\services\configserver -f .\helm\configserver-secret.local.yaml
```

Do not use `kubectl apply -f kubernetes\<service>.yml` for a service after its
Helm chart owns the same resources. Use `kubectl` for inspection and debugging,
for example `kubectl get pods` and `kubectl logs`.
