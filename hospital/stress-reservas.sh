#!/bin/bash
# stress-reservas.sh
# Dispara 20 intentos de reserva contra localhost:8080

BASE=http://localhost:8080
N=20
aciertos=0; bloqueos=0; conflictos=0; errores=0

echo "=== Stress reservas: $N intentos contra $BASE ==="
echo ""

for i in $(seq 1 $N); do
  DON=$(( (i % 5) + 1 ))
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "$BASE/api/reservas?idDonante=$DON&paciente=StressPaciente$i")
  sleep 0.05
  case "$HTTP" in
    200) aciertos=$((aciertos+1)); echo "[$i] OK   donante $DON";;
    423) bloqueos=$((bloqueos+1)); echo "[$i] LOCK donante $DON (sin token)";;
    409) conflictos=$((conflictos+1)); echo "[$i] CONFLICT donante $DON (ya reservado)";;
    *)   errores=$((errores+1)); echo "[$i] $HTTP donante $DON";;
  esac
done

echo ""
echo "=== Resultados ==="
echo "  OK:       $aciertos"
echo "  LOCK:     $bloqueos"
echo "  CONFLICT: $conflictos"
echo "  ERROR:    $errores"
echo "  Total:    $N"
