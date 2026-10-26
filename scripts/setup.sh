#!/bin/bash
# =============================================================================
# setup.sh — Levanta TODO el sistema desde cero
# Tienda de Ropa — Kubernetes + Prometheus + Grafana
# =============================================================================
set -e

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
err()  { echo -e "${RED}[✗]${NC} $1"; exit 1; }
step() { echo -e "\n${BLUE}══════════════════════════════════════${NC}"; echo -e "${BLUE}  $1${NC}"; echo -e "${BLUE}══════════════════════════════════════${NC}"; }

# =============================================================================
# PASO 0: Verificar prerequisitos
# =============================================================================
step "PASO 0 — Verificando prerequisitos"

command -v docker  >/dev/null 2>&1 || err "Docker no está instalado. Instala Docker Desktop primero."
command -v minikube >/dev/null 2>&1 || err "Minikube no está instalado."
command -v kubectl  >/dev/null 2>&1 || err "kubectl no está instalado."
command -v helm     >/dev/null 2>&1 || err "Helm no está instalado."

log "Docker:   $(docker --version)"
log "Minikube: $(minikube version --short)"
log "kubectl:  $(kubectl version --client --short 2>/dev/null || kubectl version --client)"
log "Helm:     $(helm version --short)"

# =============================================================================
# PASO 1: Iniciar Minikube
# =============================================================================
step "PASO 1 — Iniciando Minikube"

if minikube status | grep -q "Running"; then
    warn "Minikube ya está corriendo. Continuando..."
else
    log "Iniciando Minikube con recursos suficientes..."
    minikube start \
        --driver=docker \
        --cpus=4 \
        --memory=6144 \
        --disk-size=20g \
        --kubernetes-version=stable
fi

log "Habilitando metrics-server..."
minikube addons enable metrics-server

log "Minikube IP: $(minikube ip)"

# =============================================================================
# PASO 2: Instalar stack de monitoreo con Helm
# =============================================================================
step "PASO 2 — Instalando kube-prometheus-stack"

# Agregar repositorio de Helm si no existe
helm repo list | grep -q "prometheus-community" || \
    helm repo add prometheus-community https://prometheus-community.github.io/helm-charts

helm repo update

# Instalar o actualizar el stack
if helm list -n monitoring | grep -q "kube-prometheus-stack"; then
    warn "kube-prometheus-stack ya instalado. Actualizando..."
    helm upgrade kube-prometheus-stack prometheus-community/kube-prometheus-stack \
        --namespace monitoring \
        --create-namespace \
        --values "$(dirname "$0")/../k8s/values-prometheus.yaml" \
        --wait --timeout 10m
else
    log "Instalando kube-prometheus-stack..."
    helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
        --namespace monitoring \
        --create-namespace \
        --values "$(dirname "$0")/../k8s/values-prometheus.yaml" \
        --wait --timeout 10m
fi

log "Stack de monitoreo instalado."

# =============================================================================
# PASO 3: Construir imágenes Docker apuntando al daemon de Minikube
# =============================================================================
step "PASO 3 — Construyendo imágenes Docker"

log "Apuntando Docker al daemon interno de Minikube..."
eval $(minikube docker-env)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

log "Construyendo imagen de catalogo-service..."
docker build -t catalogo-service:1.0.0 \
    "$PROJECT_ROOT/microservices/catalogo-service"

log "Construyendo imagen de compras-service..."
docker build -t compras-service:1.0.0 \
    "$PROJECT_ROOT/microservices/compras-service"

log "Imágenes construidas correctamente."

# =============================================================================
# PASO 4: Aplicar manifiestos de Kubernetes
# =============================================================================
step "PASO 4 — Aplicando manifiestos de Kubernetes"

K8S_DIR="$PROJECT_ROOT/k8s"

kubectl apply -f "$K8S_DIR/00-namespace.yaml"
log "Namespace creado."

kubectl apply -f "$K8S_DIR/01-catalogo-deployment.yaml"
kubectl apply -f "$K8S_DIR/02-catalogo-service.yaml"
log "catalogo-service desplegado."

kubectl apply -f "$K8S_DIR/03-compras-deployment.yaml"
kubectl apply -f "$K8S_DIR/04-compras-service.yaml"
log "compras-service desplegado."

kubectl apply -f "$K8S_DIR/05-service-monitors.yaml"
log "ServiceMonitors creados."

kubectl apply -f "$K8S_DIR/06-prometheus-rules.yaml"
log "PrometheusRules creadas."

# =============================================================================
# PASO 5: Esperar a que los pods estén listos
# =============================================================================
step "PASO 5 — Esperando que los pods estén listos"

log "Esperando catalogo-service..."
kubectl rollout status deployment/catalogo-service -n tienda-ropa --timeout=120s

log "Esperando compras-service..."
kubectl rollout status deployment/compras-service -n tienda-ropa --timeout=120s

log "Estado de pods:"
kubectl get pods -n tienda-ropa

# =============================================================================
# PASO 6: Mostrar URLs de acceso
# =============================================================================
step "PASO 6 — Sistema listo"

MINIKUBE_IP=$(minikube ip)

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║              SISTEMA TIENDA DE ROPA LISTO               ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  ${BLUE}Grafana:${NC}          http://$MINIKUBE_IP:30300"
echo -e "  ${BLUE}  Usuario:${NC}        admin"
echo -e "  ${BLUE}  Contraseña:${NC}     TiendaRopa2024!"
echo ""
echo -e "  ${BLUE}Prometheus:${NC}       http://$MINIKUBE_IP:30090"
echo -e "  ${BLUE}AlertManager:${NC}     http://$MINIKUBE_IP:30093"
echo ""
echo -e "  ${BLUE}catalogo-service:${NC} http://$MINIKUBE_IP:30080/api/catalogo/productos"
echo -e "  ${BLUE}compras-service:${NC}  http://$MINIKUBE_IP:30081/api/compras"
echo ""
echo -e "  ${BLUE}Métricas catálogo:${NC} http://$MINIKUBE_IP:30080/metrics"
echo -e "  ${BLUE}Métricas compras:${NC}  http://$MINIKUBE_IP:30081/metrics"
echo ""
