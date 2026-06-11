#!/bin/bash
BASE="http://localhost:8080/api"
sep() { printf "\n%60s\n" "" | tr ' ' '-'; }

echo "========== DONANTES =========="

sep
echo "1. POST /api/donantes — registrar Luis Perez"
curl -s -X POST "$BASE/donantes" -d "nombre=Luis Perez&tipoSangre=O%2B&organo=Ri%C3%B1%C3%B3n"

sep
echo "2. POST /api/donantes — registrar Ana Garcia"
curl -s -X POST "$BASE/donantes" -d "nombre=Ana Garcia&tipoSangre=A%2D&organo=Coraz%C3%B3n"

sep
echo "3. POST /api/donantes — registrar Carlos Ruiz"
curl -s -X POST "$BASE/donantes" -d "nombre=Carlos Ruiz&tipoSangre=O%2B&organo=H%C3%ADgado"

sep
echo "4. GET /api/donantes — listar todos"
curl -s "$BASE/donantes"

sep
echo "5. GET /api/donantes/1 — buscar id 1"
curl -s "$BASE/donantes/1"

sep
echo "6. GET /api/donantes/disponibles"
curl -s "$BASE/donantes/disponibles"

sep
echo "7. PUT /api/donantes/1/reservar — marcar como reservado"
curl -s -X PUT "$BASE/donantes/1/reservar"

sep
echo "8. GET /api/donantes/disponibles — Luis ya no aparece"
curl -s "$BASE/donantes/disponibles"

sep
echo "9. GET /api/donantes/reservados — Luis aparece acá"
curl -s "$BASE/donantes/reservados"

sep
echo "10. PUT /api/donantes/1/liberar — liberar"
curl -s -X PUT "$BASE/donantes/1/liberar"

sep
echo "========== RESERVAS =========="

sep
echo "11. POST /api/reservas — Pepe reserva donante 1"
curl -s -X POST "$BASE/reservas" -d "idDonante=1&paciente=Pepe"

sep
echo "12. POST /api/reservas — Maria reserva donante 1 (mismo!)"
curl -s -X POST "$BASE/reservas" -d "idDonante=1&paciente=Maria"

sep
echo "13. POST /api/reservas — Juan reserva donante 1 (mismo!)"
curl -s -X POST "$BASE/reservas" -d "idDonante=1&paciente=Juan"

sep
echo "14. POST /api/reservas — Laura reserva donante 2"
curl -s -X POST "$BASE/reservas" -d "idDonante=2&paciente=Laura"

sep
echo "15. GET /api/reservas — listar todas"
curl -s "$BASE/reservas"

sep
echo "16. GET /api/reservas/donante/1 — reservas del donante 1"
curl -s "$BASE/reservas/donante/1"

sep
echo "17. GET /api/reservas/donante/1/count — cuantas tiene donante 1"
curl -s "$BASE/reservas/donante/1/count"

sep
echo "18. GET /api/reservas/donante/2/count — cuantas tiene donante 2"
curl -s "$BASE/reservas/donante/2/count"

sep
echo ""
echo "========== FIN =========="
