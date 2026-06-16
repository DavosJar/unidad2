#!/bin/bash
# demo-cluster.sh seed | run
# seed: inserta 5 donantes
# run:  lanza 10 reservas contra el mismo donante en localhost

seed() {
    echo "=== Insertando 5 donantes ==="
    PGPASSWORD=hospital_pass psql -h localhost -U hospital_user -d hospital_db -c "
        INSERT INTO registros_donantes (nombre, tipo_sangre, organo, disponible)
        SELECT * FROM (VALUES
            ('Ana Lopez','O+','Corazon',true),
            ('Carlos Ruiz','A+','Riñon',true),
            ('Maria Gomez','B+','Higado',true),
            ('Pedro Diaz','AB+','Pulmon',true),
            ('Laura Martin','O-','Corazon',true)
        ) AS v(nombre, tipo_sangre, organo, disponible)
        WHERE NOT EXISTS (SELECT 1 FROM registros_donantes WHERE nombre=v.nombre);
    "
    echo "Done."
    PGPASSWORD=hospital_pass psql -h localhost -U hospital_user -d hospital_db -c \
        "SELECT id, nombre, organo FROM registros_donantes ORDER BY id;"
}

run() {
    echo "=== 10 reservas contra donante 5 (Laura Martin - Corazon) ==="
    for i in $(seq 1 10); do
        HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
            "http://localhost:8080/api/reservas?idDonante=5&paciente=Paciente$i")
        case $HTTP in
            200) echo "[$i] 200 OK - Reservado" ;;
            409) echo "[$i] 409 CONFLICT - Donante ya reservado" ;;
            423) echo "[$i] 423 LOCKED - Sin token" ;;
            *)   echo "[$i] $HTTP" ;;
        esac
    done
    echo "=== Resultado ==="
    curl -s http://localhost:8080/api/reservas
}

case "$1" in
    seed) seed ;;
    run)  run ;;
    *)    echo "Uso: ./demo-cluster.sh seed | run" ;;
esac
