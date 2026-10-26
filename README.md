# Tienda de Ropa — Microservicios con Monitoreo en Kubernetes

Proyecto individual del curso **Kubernetes con Prometheus & Grafana — Valle Grande**.

Arquitectura de dos microservicios Java que se comunican entre sí, desplegados en Minikube con observabilidad completa usando Prometheus y Grafana.

---

## Dominio de negocio

| Servicio           | Rol       | Descripción                                                  |
|--------------------|-----------|--------------------------------------------------------------|
| `catalogo-service` | Servicio A | Gestiona el catálogo de productos de la tienda (ropa, tallas, stock) |
| `compras-service`  | Servicio B | Procesa compras de clientes. **Consulta al catálogo** para verificar disponibilidad antes de confirmar cada compra |

**Regla de negocio:** `compras-service` no puede confirmar una compra sin verificar el stock en `catalogo-service`. Si el catálogo no responde, la compra se rechaza de forma controlada.

---

## Estructura del repositorio

```
microservicios-ropa/
├── microservices/
│   ├── catalogo-service/          # Servicio A — Spring Boot 3.x
│   │   ├── src/main/java/...
│   │   ├── src/main/resources/application.properties
│   │   ├── pom.xml
│   │   └── Dockerfile
│   └── compras-service/           # Servicio B — Spring Boot 3.x
│       ├── src/main/java/...
│       ├── src/main/resources/application.properties
│       ├── pom.xml
│       └── Dockerfile
├── k8s/
│   ├── 00-namespace.yaml
│   ├── 01-catalogo-deployment.yaml
│   ├── 02-catalogo-service.yaml
│   ├── 03-compras-deployment.yaml
│   ├── 04-compras-service.yaml
│   ├── 05-service-monitors.yaml
│   ├── 06-prometheus-rules.yaml
│   ├── values-prometheus.yaml     # Configuración del chart de Helm
│   └── grafana-dashboard.json     # Dashboard exportado
├── load-testing/
│   └── stress.sh
├── scripts/
│   ├── setup.sh                   # Levanta todo desde cero
│   └── teardown.sh                # Elimina todos los recursos
├── docs/
│   └── informe.md                 # Informe técnico completo
└── README.md
```

---

## Instalación desde cero

### Prerequisitos

Instalar las siguientes herramientas en orden:

**1. Docker Desktop**  
https://www.docker.com/products/docker-desktop/  
```bash
# Verificar instalación
docker --version
```

**2. Minikube**  
https://minikube.sigs.k8s.io/docs/start/
```bash
# macOS con Homebrew
brew install minikube

# Windows con Chocolatey
choco install minikube

# Verificar
minikube version
```

**3. kubectl**
```bash
# macOS
brew install kubectl

# Windows
choco install kubernetes-cli

# Verificar
kubectl version --client
```

**4. Helm**
```bash
# macOS
brew install helm

# Windows
choco install kubernetes-helm

# Verificar
helm version
```

---

### Iniciar el cluster de Minikube

```bash
# Iniciar con recursos suficientes para dos microservicios + stack de monitoreo
minikube start \
  --driver=docker \
  --cpus=4 \
  --memory=6144 \
  --disk-size=20g

# Habilitar metrics-server
minikube addons enable metrics-server

# Verificar que está corriendo
minikube status
```

---

### Instalar el stack de monitoreo (Prometheus + Grafana)

```bash
# Agregar repositorio de Helm
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Instalar kube-prometheus-stack con la configuración personalizada
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --values k8s/values-prometheus.yaml \
  --wait --timeout 10m
```

**Puntos clave del `values-prometheus.yaml`:**
- Contraseña de Grafana: `TiendaRopa2024!`
- `serviceMonitorSelector: {}` → Prometheus busca ServiceMonitors en **todos los namespaces**
- Grafana expuesto en NodePort `30300`
- Prometheus expuesto en NodePort `30090`

Verificar acceso antes de continuar:
```bash
MINIKUBE_IP=$(minikube ip)
echo "Grafana:    http://$MINIKUBE_IP:30300  (admin / TiendaRopa2024!)"
echo "Prometheus: http://$MINIKUBE_IP:30090"
```

---

### Construir imágenes Docker (apuntando al daemon de Minikube)

> ⚠️ Este paso es crítico. Si no se hace, Kubernetes no encontrará las imágenes.

```bash
# Reapuntar Docker al daemon INTERNO de Minikube
eval $(minikube docker-env)

# Construir las imágenes
docker build -t catalogo-service:1.0.0 ./microservices/catalogo-service
docker build -t compras-service:1.0.0  ./microservices/compras-service

# Verificar que las imágenes están disponibles en Minikube
docker images | grep -E "catalogo-service|compras-service"
```

---

### Desplegar los microservicios

```bash
# Crear namespace
kubectl apply -f k8s/00-namespace.yaml

# Desplegar catalogo-service
kubectl apply -f k8s/01-catalogo-deployment.yaml
kubectl apply -f k8s/02-catalogo-service.yaml

# Desplegar compras-service
kubectl apply -f k8s/03-compras-deployment.yaml
kubectl apply -f k8s/04-compras-service.yaml

# Crear ServiceMonitors (integración con Prometheus)
kubectl apply -f k8s/05-service-monitors.yaml

# Crear reglas de alerta
kubectl apply -f k8s/06-prometheus-rules.yaml

# Esperar que los pods estén listos
kubectl rollout status deployment/catalogo-service -n tienda-ropa
kubectl rollout status deployment/compras-service  -n tienda-ropa

# Verificar estado
kubectl get pods -n tienda-ropa
```

---

### O todo en un solo comando

```bash
bash scripts/setup.sh
```

---

## URLs de acceso

| Servicio              | URL                                            |
|-----------------------|------------------------------------------------|
| Grafana               | http://\<minikube-ip\>:30300 (admin / TiendaRopa2024!) |
| Prometheus            | http://\<minikube-ip\>:30090                   |
| AlertManager          | http://\<minikube-ip\>:30093                   |
| catalogo-service API  | http://\<minikube-ip\>:30080/api/catalogo/productos |
| compras-service API   | http://\<minikube-ip\>:30081/api/compras        |
| Métricas catálogo     | http://\<minikube-ip\>:30080/metrics            |
| Métricas compras      | http://\<minikube-ip\>:30081/metrics            |

```bash
# Obtener la IP de Minikube
minikube ip
```

---

## Endpoints de la API

### catalogo-service (puerto 30080)

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/catalogo/health` | Health check |
| GET | `/metrics` | Métricas para Prometheus |
| GET | `/api/catalogo/productos` | Listar todos los productos |
| GET | `/api/catalogo/productos/{id}` | Obtener producto por ID |
| GET | `/api/catalogo/productos/categoria/{cat}` | Filtrar por categoría |
| POST | `/api/catalogo/productos` | Agregar producto |
| PUT | `/api/catalogo/productos/{id}` | Actualizar producto |
| POST | `/api/catalogo/productos/{id}/reducir-stock` | Reducir stock |

### compras-service (puerto 30081)

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/compras/health` | Health check |
| GET | `/metrics` | Métricas para Prometheus |
| POST | `/api/compras` | Realizar una compra |
| GET | `/api/compras` | Listar todas las compras |
| GET | `/api/compras/{id}` | Obtener compra por ID |
| GET | `/api/compras/estado/{estado}` | Filtrar por estado |
| GET | `/api/compras/resumen` | Resumen de negocio |

---

## Métricas instrumentadas

### catalogo-service

| Métrica | Tipo | Descripción |
|---------|------|-------------|
| `catalogo_consultas_total` | Counter | Total de consultas al catálogo |
| `catalogo_stock_reducido_total` | Counter | Veces que se redujo stock |
| `catalogo_consultas_categoria_total` | Counter | Consultas filtradas por categoría |
| `catalogo_errores_total` | Counter | Errores en el catálogo |
| `catalogo_productos_disponibles` | Gauge | Productos con stock > 0 (tiempo real) |
| `catalogo_delay_artificial_ms` | Gauge | Delay configurado (Casuística 2) |
| `catalogo_consulta_producto_duration` | Timer | Latencia de consulta por ID |

### compras-service

| Métrica | Tipo | Descripción |
|---------|------|-------------|
| `compras_realizadas_total` | Counter | Compras confirmadas exitosamente |
| `compras_rechazadas_total` | Counter | Compras rechazadas |
| `compras_timeouts_catalogo_total` | Counter | Timeouts al consultar el catálogo |
| `compras_errores_comunicacion_total` | Counter | Errores de comunicación |
| `compras_confirmadas_activas` | Gauge | Compras confirmadas acumuladas |
| `compras_ingresos_total` | Gauge | Ingresos totales por ventas confirmadas |
| `compras_procesamiento_duration` | Timer | Tiempo de procesamiento de una compra |

---

## Casuísticas

### Casuística 1 — El Servicio Caído
```bash
# Activar
kubectl set env deployment/catalogo-service -n tienda-ropa SERVER_PORT=abc
# Resolver
kubectl set env deployment/catalogo-service -n tienda-ropa SERVER_PORT=8080
```

### Casuística 2 — El Servicio Lento
```bash
# Activar (delay 4s > timeout 3s)
kubectl set env deployment/catalogo-service -n tienda-ropa CATALOGO_DELAY_MS=4000
bash load-testing/stress.sh -r 30 -t compras
# Resolver
kubectl set env deployment/catalogo-service -n tienda-ropa CATALOGO_DELAY_MS=0
```

### Casuística 3 — Agotamiento de Stock
```bash
# Activar
bash load-testing/stress.sh -s agota-stock
# Resolver
curl -X PUT -H "Content-Type: application/json" \
  -d '{"id":"P005","nombre":"Vestido Verano Azul","categoria":"vestidos","precio":59.99,"stock":20,"talla":"S"}' \
  "http://$(minikube ip):30080/api/catalogo/productos/P005"
```

---

## Eliminar el sistema

```bash
# Eliminar recursos pero mantener el cluster
bash scripts/teardown.sh

# Eliminar todo, incluyendo el cluster
bash scripts/teardown.sh --full
```

---

## Solución a errores comunes

| Error | Causa | Solución |
|-------|-------|---------|
| Pod en `ImagePullBackOff` | Docker no apuntaba al daemon de Minikube al buildear | `eval $(minikube docker-env)` y rebuild |
| `/metrics` devuelve 404 | Falta configuración en `application.properties` | Verificar `management.endpoints.web.path-mapping.prometheus=metrics` |
| Target DOWN en Prometheus | Labels del ServiceMonitor no coinciden con el Service | Verificar que `app` y `monitored` coincidan exactamente |
| Alertas no aparecen | `ruleSelector` no configurado en values.yaml | Verificar `ruleSelector: matchLabels: {}` en values-prometheus.yaml |
| compras-service timeout | catalogo-service responde lento o está caído | Revisar logs: `kubectl logs -n tienda-ropa deploy/catalogo-service` |
