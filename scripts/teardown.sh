#!/bin/bash
# =============================================================================
# teardown.sh — Elimina todos los recursos del sistema
# Uso: ./teardown.sh           → elimina recursos K8s pero mantiene el cluster
#      ./teardown.sh --full    → también elimina el cluster de Minikube
# =============================================================================
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
step() { echo -e "\n${BLUE}══════════════════════════════════════${NC}"; echo -e "${BLUE}  $1${NC}"; echo -e "${BLUE}══════════════════════════════════════${NC}"; }

FULL_TEARDOWN=false
if [ "$1" = "--full" ]; then
    FULL_TEARDOWN=true
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
K8S_DIR="$PROJECT_ROOT/k8s"

# =============================================================================
# PASO 1: Eliminar recursos del namespace tienda-ropa
# =============================================================================
step "PASO 1 — Eliminando recursos de tienda-ropa"

kubectl delete -f "$K8S_DIR/06-prometheus-rules.yaml"   --ignore-not-found=true
kubectl delete -f "$K8S_DIR/05-service-monitors.yaml"   --ignore-not-found=true
kubectl delete -f "$K8S_DIR/04-compras-service.yaml"    --ignore-not-found=true
kubectl delete -f "$K8S_DIR/03-compras-deployment.yaml" --ignore-not-found=true
kubectl delete -f "$K8S_DIR/02-catalogo-service.yaml"   --ignore-not-found=true
kubectl delete -f "$K8S_DIR/01-catalogo-deployment.yaml" --ignore-not-found=true
kubectl delete -f "$K8S_DIR/00-namespace.yaml"           --ignore-not-found=true

log "Recursos de tienda-ropa eliminados."

# =============================================================================
# PASO 2: Eliminar el stack de monitoreo (Helm)
# =============================================================================
step "PASO 2 — Eliminando stack de monitoreo"

if helm list -n monitoring | grep -q "kube-prometheus-stack"; then
    helm uninstall kube-prometheus-stack -n monitoring
    kubectl delete namespace monitoring --ignore-not-found=true
    log "Stack de monitoreo eliminado."
else
    warn "kube-prometheus-stack no encontrado, saltando."
fi

# =============================================================================
# PASO 3 (opcional): Eliminar el cluster
# =============================================================================
if [ "$FULL_TEARDOWN" = true ]; then
    step "PASO 3 — Eliminando cluster de Minikube"
    warn "Esto eliminará el cluster completo. Presiona Ctrl+C en 5 segundos para cancelar..."
    sleep 5
    minikube delete
    log "Cluster eliminado."
else
    warn "Cluster de Minikube mantenido. Usa --full para eliminarlo también."
fi

echo ""
echo -e "${GREEN}Teardown completado.${NC}"
