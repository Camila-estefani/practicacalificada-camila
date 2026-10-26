# Informe Técnico — Tienda de Ropa
## Kubernetes con Prometheus & Grafana — Valle Grande

**Alumno:** [Tu nombre]  
**Dominio de negocio:** Tienda de Ropa  
**Servicios:** `catalogo-service` (Servicio A) · `compras-service` (Servicio B)

---

## Parte A — Conceptos

### 1. ¿Qué es Micrometer y por qué se usa en lugar de la librería directa de Prometheus?

Micrometer es una **fachada de métricas** (facade) para aplicaciones JVM, similar a lo que SLF4J es para los logs. Su propósito es desacoplar el código de la aplicación del sistema de monitoreo específico que se use.

Si se usara la librería cliente de Prometheus directamente (`prometheus_java_client`), el código quedaría atado a Prometheus: si mañana el equipo decide usar Datadog, InfluxDB o cualquier otro sistema, habría que reescribir toda la instrumentación. Con Micrometer, solo se cambia el **registry** (la dependencia `micrometer-registry-prometheus`) sin tocar el código que registra las métricas.

En este proyecto se usa así en `CatalogoMetrics.java`:

```java
// Se inyecta MeterRegistry, no una clase específica de Prometheus
@Autowired
public CatalogoMetrics(MeterRegistry registry, ...) {
    this.registry = registry;  // Podría ser Prometheus, Datadog, etc.
}

// Se registra el counter con la API genérica de Micrometer
Counter.builder("catalogo_consultas_total")
       .description("Total de consultas al catálogo")
       .register(registry);  // ← registry decide el formato de exportación
```

---

### 2. ¿Cuál es la diferencia entre Counter, Gauge y Timer? Ejemplos del código.

| Tipo    | Qué mide                          | Solo sube | Puede bajar |
|---------|-----------------------------------|:---------:|:-----------:|
| Counter | Ocurrencias acumuladas            | ✓         | ✗           |
| Gauge   | Valor actual en un momento        | ✓         | ✓           |
| Timer   | Duración + cantidad de llamadas   | ✓         | ✗           |

**Counter — `catalogo_consultas_total`** (`CatalogoMetrics.java`)  
Cuenta cuántas veces se consultó el catálogo. Solo sube, nunca baja. Se incrementa cada vez que llega un request.

```java
Counter.builder("catalogo_consultas_total")
       .description("Número total de consultas al catálogo de productos")
       .tag("servicio", "catalogo-service")
       .register(registry);

// En el controller, al recibir un request:
catalogoMetrics.getConsultasTotal().increment();
```

**Gauge — `catalogo_productos_disponibles`** (`CatalogoMetrics.java`)  
Refleja cuántos productos tienen stock > 0 en tiempo real. Sube y baja conforme se confirman o cancelan compras.

```java
Gauge.builder("catalogo_productos_disponibles",
              productoRepository,
              ProductoRepository::contarDisponibles)  // ← se recalcula en cada scrape
     .description("Productos con stock > 0")
     .register(registry);
```

**Timer — `catalogo_consulta_producto_duration`** (`CatalogoController.java`)  
Mide cuánto tarda cada consulta de producto por ID. Registra cantidad de llamadas, suma de duración, y percentiles.

```java
// En el controller, wrapping la lógica con el timer:
return catalogoMetrics.getConsultaProductoTimer().record(() -> {
    aplicarDelayArtificial();  // ← si hay delay, el timer lo captura
    // ... lógica del endpoint
});
```

---

### 3. ¿Qué es un ServiceMonitor y cómo sabe el Prometheus Operator qué monitorear?

Un **ServiceMonitor** es un Custom Resource (CRD) que instala el Prometheus Operator. En lugar de editar el archivo `prometheus.yml` manualmente para agregar un target, se crea un ServiceMonitor y el Operator se encarga de actualizar la configuración de Prometheus automáticamente.

El Prometheus Operator hace lo siguiente:
1. Vigila todos los ServiceMonitors en los namespaces configurados.
2. Para cada ServiceMonitor, busca Services de Kubernetes que **coincidan con el selector** definido.
3. Genera la configuración de scrape en Prometheus apuntando a esos Services.

En el proyecto, `05-service-monitors.yaml`:

```yaml
spec:
  selector:
    matchLabels:
      app: catalogo-service    # ← busca Services con este label
      monitored: "true"
  endpoints:
    - port: http               # ← usa el puerto llamado "http" del Service
      path: /metrics           # ← raspa esta ruta
      interval: 15s
```

Y el Service en `02-catalogo-service.yaml` tiene esos labels exactos:

```yaml
metadata:
  labels:
    app: catalogo-service
    monitored: "true"
```

Si los labels no coinciden exactamente, Prometheus no descubrirá el target.

---

### 4. ¿Cuál es la diferencia entre liveness probe y readiness probe?

Ambas son chequeos de salud que Kubernetes ejecuta periódicamente, pero tienen propósitos distintos:

| Probe       | Pregunta que responde                     | Consecuencia si falla              |
|-------------|-------------------------------------------|------------------------------------|
| **Liveness**  | ¿Sigue vivo? ¿Necesita reiniciarse?     | Kubernetes **mata y reinicia** el pod |
| **Readiness** | ¿Está listo para recibir tráfico?       | Kubernetes **quita el pod del Service** (no lo mata) |

Un ejemplo real: durante el arranque de Spring Boot (que puede tardar 20-30 segundos), el pod está vivo pero aún no terminó de inicializar el contexto. Si existiera solo liveness, Kubernetes lo reiniciaría porque no responde. La readiness probe resuelve esto: el pod existe, pero no recibe tráfico hasta que responda con éxito.

En los deployments del proyecto:

```yaml
# Liveness: si falla 3 veces seguidas → reinicia el pod
livenessProbe:
  httpGet:
    path: /api/catalogo/health
    port: 8080
  initialDelaySeconds: 30   # espera antes del primer chequeo
  periodSeconds: 10

# Readiness: si falla → sale del balanceador, pero el pod sigue vivo
readinessProbe:
  httpGet:
    path: /api/catalogo/health
    port: 8080
  initialDelaySeconds: 20   # empieza a chequear antes que liveness
  periodSeconds: 5
```

---

### 5. ¿Por qué es necesario apuntar Docker al daemon de Minikube antes de construir las imágenes?

Por defecto, cuando se ejecuta `docker build`, la imagen se almacena en el **Docker daemon local** (el de la PC). Minikube corre dentro de una máquina virtual o contenedor con su **propio daemon de Docker interno**.

Cuando Kubernetes intenta ejecutar un pod, busca la imagen en el daemon que tiene acceso — el de Minikube — no en el de la PC. Si la imagen no está ahí, el pod queda en estado `ImagePullBackOff` o `ErrImageNeverPull`.

El comando `eval $(minikube docker-env)` reconfigura las variables de entorno del shell (`DOCKER_HOST`, `DOCKER_TLS_VERIFY`, etc.) para que `docker build` use el daemon **dentro de Minikube**. Así la imagen queda donde Kubernetes la espera.

En el setup.sh:
```bash
eval $(minikube docker-env)   # ← reapunta Docker al daemon de Minikube
docker build -t catalogo-service:1.0.0 ./microservices/catalogo-service
# Ahora la imagen está dentro de Minikube
```

Además, en los Deployments se usa `imagePullPolicy: Never` para evitar que Kubernetes intente bajar la imagen de un registry externo.

---

### 6. ¿Qué ocurre si no configuras el selector de ServiceMonitors en el values.yaml del chart?

El Prometheus Operator tiene un selector que determina qué ServiceMonitors "escucha". Por defecto, **solo escucha ServiceMonitors que tengan el label `release: kube-prometheus-stack`** y que estén en el **mismo namespace que Prometheus** (generalmente `monitoring`).

Si no se configura `serviceMonitorSelector: {}` y `serviceMonitorNamespaceSelector: {}`, cualquier ServiceMonitor que se cree en el namespace `tienda-ropa` será ignorado completamente. Los targets no aparecerán en Prometheus, y las métricas nunca serán recolectadas aunque el endpoint `/metrics` funcione perfectamente.

En el `values-prometheus.yaml` del proyecto:

```yaml
prometheusSpec:
  serviceMonitorSelector:
    matchLabels: {}   # ← acepta CUALQUIER ServiceMonitor (vacío = sin filtro)
  serviceMonitorNamespaceSelector:
    matchLabels: {}   # ← busca en TODOS los namespaces
```

Sin esto, los targets de los dos microservicios nunca aparecerían en Prometheus → no hay métricas → no hay alertas → no hay dashboard útil.

---

## Parte B — PromQL

### 1. Tasa de requests por minuto del compras-service en los últimos 5 minutos

```promql
rate(compras_realizadas_total[5m]) * 60
```

Esta query calcula cuántas compras exitosas se están procesando por minuto. `rate()` devuelve el promedio de incrementos por segundo en el rango indicado; multiplicar por 60 convierte a por minuto. Se incluyen también las rechazadas para ver el total de intentos:

```promql
(rate(compras_realizadas_total[5m]) + rate(compras_rechazadas_total[5m])) * 60
```

---

### 2. Latencia p95 del endpoint más crítico del sistema

El endpoint más crítico es `GET /api/catalogo/productos/{id}` porque todas las compras dependen de él.

```promql
histogram_quantile(
  0.95,
  rate(catalogo_consulta_producto_duration_seconds_bucket[5m])
)
```

Esta query calcula el percentil 95: el 95% de las consultas responden en menos de X segundos. Es el indicador más útil porque los picos extremos (p99) pueden ser outliers, pero el p95 refleja la experiencia real de la mayoría de los clientes.

---

### 3. Estado UP/DOWN de ambos servicios al mismo tiempo

```promql
up{job=~"catalogo-service|compras-service"}
```

Retorna una serie por cada servicio: `1` si Prometheus puede raspar sus métricas (UP), `0` si no puede (DOWN). El operador `=~` es un match por regex, lo que permite consultar ambos en una sola query.

---

### 4. Query de negocio propia: ¿Cuánto del potencial de ventas se está perdiendo por rechazos?

```promql
rate(compras_rechazadas_total[10m]) /
(rate(compras_realizadas_total[10m]) + rate(compras_rechazadas_total[10m]))
* 100
```

Esta query calcula el **porcentaje de compras fallidas** sobre el total de intentos en los últimos 10 minutos. Si el resultado supera el 20%, significa que 1 de cada 5 clientes no pudo completar su compra — una pérdida de negocio real que justifica investigar si es un problema de stock o de disponibilidad del catálogo.

---

## Parte C — Casuísticas

---

### Casuística 1 — El Servicio Caído

#### Contexto narrativo
Son las 11:00 AM del lunes, hora pico de la tienda online. Un desarrollador junior hace un deploy del catalogo-service con un error en las variables de entorno: configura mal el `SERVER_PORT` con el valor `"abc"`. Spring Boot no puede parsear el puerto, lanza una excepción al arrancar, y el pod entra en `CrashLoopBackOff`. Los clientes que intentan comprar reciben errores 503.

#### Síntoma
Los clientes ven "El servicio de catálogo no está disponible" en todas las compras. Las compras nuevas se registran como RECHAZADAS con el motivo "catalogo-service no disponible".

#### Activación

```bash
# Romper el catalogo-service con un puerto inválido
kubectl set env deployment/catalogo-service \
  -n tienda-ropa \
  SERVER_PORT=abc

# Observar cómo el pod entra en CrashLoopBackOff
kubectl get pods -n tienda-ropa -w
```

#### Diagnóstico en Grafana/Prometheus
- Panel "Estado de los Servicios": `catalogo-service` cambia de `UP` a `DOWN` (valor 0, fondo rojo)
- Prometheus `/targets`: `catalogo-service` aparece en estado `DOWN`
- Alerta `CatalogoServiceCaido` pasa a estado `FIRING` tras 1 minuto
- Panel de compras: la tasa de compras confirmadas cae a 0; la de rechazadas sube
- En kubectl: `kubectl describe pod -n tienda-ropa` muestra `Back-off restarting failed container`

#### Resolución

```bash
# Corregir el puerto a su valor correcto
kubectl set env deployment/catalogo-service \
  -n tienda-ropa \
  SERVER_PORT=8080

# Verificar que el pod vuelve a Running
kubectl rollout status deployment/catalogo-service -n tienda-ropa
```

#### Lección técnica
**Los probes de Kubernetes tienen responsabilidades distintas:** La liveness probe detecta que el proceso ya no puede continuar y ordena un reinicio. La readiness probe detecta que el servicio aún no está listo y lo retira del balanceador de carga sin reiniciarlo. Sin probes, Kubernetes seguiría enviando tráfico a un pod que no puede responder. El monitoring con Prometheus añade visibilidad externa: no solo sé que el pod crasheó, sino que puedo correlacionarlo con el impacto en el negocio (compras rechazadas subiendo en el mismo instante).

---

### Casuística 2 — El Servicio Lento

#### Contexto narrativo
Es Black Friday. El tráfico se triplicó y el catálogo-service, que normalmente responde en 50ms, empieza a tardar 4 segundos porque la JVM está bajo presión de memoria. compras-service tiene un timeout de 3 segundos. El resultado: cada intento de compra falla con un timeout silencioso. Los clientes intentan comprar, no pasa nada, vuelven a intentarlo. La tasa de timeouts sube, los ingresos caen.

#### Síntoma
Los clientes hacen click en "Comprar", esperan y reciben error. El equipo de soporte recibe quejas pero nadie sabe qué servicio está fallando.

#### Activación

```bash
# Introducir un delay de 4000ms en catalogo-service
# (supera el timeout de 3s configurado en compras-service)
kubectl set env deployment/catalogo-service \
  -n tienda-ropa \
  CATALOGO_DELAY_MS=4000

# Generar tráfico de compras para ver los timeouts
bash load-testing/stress.sh -r 30 -t compras

# Ver el delay en tiempo real como métrica
# En Prometheus: catalogo_delay_artificial_ms
```

#### Diagnóstico en Grafana/Prometheus
- Panel "Delay Artificial en Catálogo": salta de 0ms a 4000ms
- Panel "Timeouts del Catálogo": el contador `compras_timeouts_catalogo_total` sube
- Panel de latencia p95: supera los 3 segundos → cruce con la línea del timeout
- Alerta `TimeoutsCatalogoFrecuentes` pasa a `FIRING`
- Alerta `LatenciaAltaCatalogo` pasa a `FIRING`
- Panel de compras: compras confirmadas = 0, rechazadas sube

#### Resolución

```bash
# Quitar el delay artificial
kubectl set env deployment/catalogo-service \
  -n tienda-ropa \
  CATALOGO_DELAY_MS=0

# Verificar que los timeouts dejan de ocurrir
# En Prometheus: rate(compras_timeouts_catalogo_total[1m]) debe volver a 0
```

#### Lección técnica
**La latencia se propaga entre microservicios como un efecto dominó.** catalogo-service no está "caído" — sigue respondiendo. Pero sus 4 segundos de latencia superan el timeout de compras-service, que falla silenciosamente. Sin observabilidad, el equipo de compras culparía a su propio servicio. Con el Gauge `catalogo_delay_artificial_ms` visible en Grafana, el diagnóstico es inmediato: el problema está en el servicio upstream. Esta casuística demuestra por qué cada microservicio debe instrumentar su propia latencia y por qué los timeouts explícitos son obligatorios en comunicación entre servicios.

---

### Casuística 3 — El Agotamiento de Stock (negocio)

#### Contexto narrativo
Una colección limitada de vestidos (P005 y P006, 10 y 5 unidades respectivamente) se lanza a las 9:00 AM. En 10 minutos, bots de compra automatizados agotan el stock completo. El panel de "Productos Disponibles" cae a 0 para esas categorías. Los clientes reales que llegan a las 9:15 reciben rechazos constantes por "stock insuficiente", sin saber que ya se agotó.

#### Síntoma
Alta tasa de compras rechazadas con motivo "Stock insuficiente". El `catalogo_productos_disponibles` cae drásticamente.

#### Activación

```bash
# Simular agotamiento rápido de stock
bash load-testing/stress.sh -s agota-stock

# O manualmente, agotar P005 y P006:
MINIKUBE_IP=$(minikube ip)
for i in $(seq 1 15); do
  curl -s -X POST \
    -H "Content-Type: application/json" \
    -d '{"productoId":"P005","clienteNombre":"BotCompra","cantidad":1}' \
    "http://$MINIKUBE_IP:30081/api/compras"
  curl -s -X POST \
    -H "Content-Type: application/json" \
    -d '{"productoId":"P006","clienteNombre":"BotCompra","cantidad":1}' \
    "http://$MINIKUBE_IP:30081/api/compras"
done

# Verificar el estado de stock
curl -s "http://$MINIKUBE_IP:30080/api/catalogo/productos/P005"
curl -s "http://$MINIKUBE_IP:30080/api/catalogo/productos/P006"
```

#### Diagnóstico en Grafana/Prometheus
- Panel "Productos Disponibles": baja de 7 a 5 (P005 y P006 ya sin stock)
- Panel de compras rechazadas: sube abruptamente
- Query de negocio: porcentaje de rechazos supera el 50% (la mitad de los intentos fallan)
- Alerta `AltaTasaComprasRechazadas` pasa a `FIRING`

#### Resolución

```bash
# Reponer stock editando el deployment con una variable de entorno
# (en un sistema real se haría vía API de administración)
MINIKUBE_IP=$(minikube ip)

# Actualizar stock vía la API PUT del catálogo
curl -X PUT \
  -H "Content-Type: application/json" \
  -d '{"id":"P005","nombre":"Vestido Verano Azul","categoria":"vestidos","precio":59.99,"stock":20,"talla":"S"}' \
  "http://$MINIKUBE_IP:30080/api/catalogo/productos/P005"

curl -X PUT \
  -H "Content-Type: application/json" \
  -d '{"id":"P006","nombre":"Vestido Casual Rojo","categoria":"vestidos","precio":44.99,"stock":10,"talla":"M"}' \
  "http://$MINIKUBE_IP:30080/api/catalogo/productos/P006"

# Verificar que productos_disponibles sube de nuevo en Grafana
```

#### Lección técnica
**El monitoreo de negocio es tan importante como el de infraestructura.** Los servicios estaban técnicamente UP y con latencia normal, pero el negocio estaba perdiendo ventas. El Gauge `catalogo_productos_disponibles` y el Counter `compras_rechazadas_total` son métricas de negocio que dan contexto real a los números técnicos. Un dashboard que solo muestre CPU y memoria no habría detectado este problema. Esta casuística demuestra la diferencia entre "el sistema funciona" y "el negocio funciona".

---

## Parte D — Evidencias del sistema

> **Nota:** Las siguientes capturas deben tomarse con el sistema corriendo en Minikube. Los pasos para reproducirlas están en el README y en el `setup.sh`.

### D1. Targets en Prometheus en estado UP

Acceder a: `http://<minikube-ip>:30090/targets`

Se deben ver dos targets con estado `UP`:
- `tienda-ropa/catalogo-service-monitor` → endpoint: `<pod-ip>:8080/metrics`
- `tienda-ropa/compras-service-monitor` → endpoint: `<pod-ip>:8081/metrics`

**[Insertar captura de pantalla aquí]**

---

### D2. Dashboard de Grafana con los seis paneles

Acceder a: `http://<minikube-ip>:30300`  
Dashboard: "Tienda de Ropa — Dashboard de Monitoreo"

Los 8 paneles del dashboard exportado en `k8s/grafana-dashboard.json`:
1. Estado de los Servicios (stat)
2. Tasa de Consultas al Catálogo
3. Tasa de Compras Realizadas vs Rechazadas
4. Latencia p95 — Consulta Producto
5. Timeouts del Catálogo
6. Productos Disponibles en Catálogo
7. Ingresos Totales Acumulados
8. Delay Artificial en Catálogo

**[Insertar captura de pantalla aquí]**

---

### D3. Pods en estado Running

```bash
kubectl get pods -n tienda-ropa
```

Salida esperada:
```
NAME                               READY   STATUS    RESTARTS   AGE
catalogo-service-xxxxxxxxx-xxxxx   1/1     Running   0          5m
compras-service-xxxxxxxxx-xxxxx    1/1     Running   0          5m
```

**[Insertar captura de pantalla aquí]**

---

### D4. Alerta en estado FIRING

Durante la Casuística 1 (servicio caído) o Casuística 2 (servicio lento), acceder a:  
`http://<minikube-ip>:30090/alerts`

La alerta `CatalogoServiceCaido` o `TimeoutsCatalogoFrecuentes` aparece en estado `FIRING`.

**[Insertar captura de pantalla aquí]**
