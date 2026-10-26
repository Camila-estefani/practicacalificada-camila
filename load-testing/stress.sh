#!/bin/bash
# =============================================================================
# stress.sh — Genera tráfico hacia los microservicios de la tienda
# Uso: ./stress.sh [opciones]
#   -h HOST        IP de Minikube (default: minikube ip)
#   -r REQUESTS    Número de requests (default: 100)
#   -c CONCURRENT  Concurrencia (default: 5)
#   -t TARGET      catalogo | compras | ambos (default: ambos)
#   -s SCENARIO    normal | compras-rapidas | agota-stock (default: normal)
# =============================================================================

# Parámetros por defecto
HOST=$(minikube ip 2>/dev/null || echo "192.168.49.2")
REQUESTS=100
CONCURRENT=5
TARGET="ambos"
SCENARIO="normal"

# Colores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Parsear argumentos
while getopts "h:r:c:t:s:" opt; do
    case $opt in
        h) HOST="$OPTARG" ;;
        r) REQUESTS="$OPTARG" ;;
        c) CONCURRENT="$OPTARG" ;;
        t) TARGET="$OPTARG" ;;
        s) SCENARIO="$OPTARG" ;;
        *) echo "Uso: $0 [-h host] [-r requests] [-c concurrent] [-t target] [-s scenario]"; exit 1 ;;
    esac
done

CATALOGO_URL="http://$HOST:30080"
COMPRAS_URL="http://$HOST:30081"

echo -e "${BLUE}════════════════════════════════════════${NC}"
echo -e "${BLUE}  STRESS TEST — Tienda de Ropa${NC}"
echo -e "${BLUE}════════════════════════════════════════${NC}"
echo -e "  Host:       $HOST"
echo -e "  Requests:   $REQUESTS"
echo -e "  Concurrent: $CONCURRENT"
echo -e "  Target:     $TARGET"
echo -e "  Scenario:   $SCENARIO"
echo ""

# =============================================================================
# Función: generar tráfico al catálogo
# =============================================================================
stress_catalogo() {
    echo -e "${GREEN}[→]${NC} Generando tráfico en catalogo-service..."
    local productos=("P001" "P002" "P003" "P004" "P005" "P006" "P007")

    for i in $(seq 1 $REQUESTS); do
        PRODUCTO=${productos[$((RANDOM % ${#productos[@]}))]}
        (
            curl -s -o /dev/null -w "%{http_code}" \
                "$CATALOGO_URL/api/catalogo/productos/$PRODUCTO" &
        )
        # Control de concurrencia simple
        if (( i % CONCURRENT == 0 )); then
            wait
            echo -ne "\r  Requests enviados: $i/$REQUESTS"
        fi
    done
    wait
    echo -e "\n  catalogo-service: $REQUESTS requests completados."
}

# =============================================================================
# Función: generar compras
# =============================================================================
stress_compras() {
    echo -e "${GREEN}[→]${NC} Generando tráfico en compras-service..."
    local productos=("P001" "P002" "P003" "P004" "P005")
    local clientes=("Ana García" "Luis Torres" "María López" "Carlos Ruiz" "Elena Vega")

    for i in $(seq 1 $REQUESTS); do
        PRODUCTO=${productos[$((RANDOM % ${#productos[@]}))]}
        CLIENTE=${clientes[$((RANDOM % ${#clientes[@]}))]}
        CANTIDAD=$((RANDOM % 3 + 1))
        (
            curl -s -o /dev/null -X POST \
                -H "Content-Type: application/json" \
                -d "{\"productoId\":\"$PRODUCTO\",\"clienteNombre\":\"$CLIENTE\",\"cantidad\":$CANTIDAD}" \
                "$COMPRAS_URL/api/compras" &
        )
        if (( i % CONCURRENT == 0 )); then
            wait
            echo -ne "\r  Requests enviados: $i/$REQUESTS"
        fi
    done
    wait
    echo -e "\n  compras-service: $REQUESTS requests completados."
}

# =============================================================================
# Escenarios
# =============================================================================
case $SCENARIO in
    "normal")
        echo -e "${YELLOW}Escenario: Tráfico normal mixto${NC}"
        if [ "$TARGET" = "catalogo" ] || [ "$TARGET" = "ambos" ]; then
            stress_catalogo
        fi
        if [ "$TARGET" = "compras" ] || [ "$TARGET" = "ambos" ]; then
            stress_compras
        fi
        ;;

    "compras-rapidas")
        echo -e "${YELLOW}Escenario: Ráfaga de compras (pico de ventas)${NC}"
        CONCURRENT=20
        for round in 1 2 3; do
            echo "  Ronda $round de 3..."
            stress_compras
            sleep 2
        done
        ;;

    "agota-stock")
        echo -e "${YELLOW}Escenario: Intentar agotar stock de P006 (solo 5 unidades)${NC}"
        for i in $(seq 1 20); do
            curl -s -o /dev/null -X POST \
                -H "Content-Type: application/json" \
                -d '{"productoId":"P006","clienteNombre":"Bot Comprador","cantidad":1}' \
                "$COMPRAS_URL/api/compras"
            echo -ne "\r  Intento $i/20"
        done
        echo ""
        echo "  Verificando stock restante..."
        curl -s "$CATALOGO_URL/api/catalogo/productos/P006" | \
            python3 -c "import sys,json; p=json.load(sys.stdin); print(f'  Stock final: {p[\"stock\"]}')" \
            2>/dev/null || echo "  (instala python3 para ver el stock)"
        ;;

    *)
        echo "Escenario no reconocido: $SCENARIO"
        echo "Opciones: normal | compras-rapidas | agota-stock"
        exit 1
        ;;
esac

echo ""
echo -e "${GREEN}Stress test completado.${NC}"
echo -e "Revisa Grafana en: http://$HOST:30300"
