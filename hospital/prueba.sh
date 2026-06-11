#!/bin/bash

# Registrar donantes
curl -s -X POST "http://localhost:8080/api/donantes" -d "nombre=Juan Perez&tipoSangre=O%2B&organo=Coraz%C3%B3n"
echo
curl -s -X POST "http://localhost:8080/api/donantes" -d "nombre=Maria Garcia&tipoSangre=A%2D&organo=Ri%C3%B1%C3%B3n"
echo
curl -s -X POST "http://localhost:8080/api/donantes" -d "nombre=Carlos Lopez&tipoSangre=O%2D&organo=Coraz%C3%B3n"
echo
curl -s -X POST "http://localhost:8080/api/donantes" -d "nombre=Ana Martinez&tipoSangre=B%2B&organo=Pulm%C3%B3n"
echo

# Listar todos
curl -s "http://localhost:8080/api/donantes"
echo

# Buscar por ID
curl -s "http://localhost:8080/api/donantes/1"
echo

# Listar disponibles
curl -s "http://localhost:8080/api/donantes/disponibles"
echo

# Listar reservados
curl -s "http://localhost:8080/api/donantes/reservados"
echo

# Listar por tipo de órgano
curl -s "http://localhost:8080/api/donantes/tipo/Coraz%C3%B3n"
echo

# Reservar donante (marcar como no disponible)
curl -s -X PUT "http://localhost:8080/api/donantes/1/reservar"
echo

# Ver que ya no aparece como disponible
curl -s "http://localhost:8080/api/donantes/disponibles"
echo

# Liberar donante
curl -s -X PUT "http://localhost:8080/api/donantes/1/liberar"
echo

# Buscar compatibles (órgano + sangre)
curl -s "http://localhost:8080/api/donantes/compatibles?organo=Coraz%C3%B3n&sangre=O%2B"
echo

# Estadísticas
curl -s "http://localhost:8080/api/donantes/estadisticas"
echo
