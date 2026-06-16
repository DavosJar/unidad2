#!/bin/bash
# demo-cluster.sh
# FASE 1 (solo en la PC con PostgreSQL): ./demo-cluster.sh seed
# FASE 2 (en cada PC):                   ./demo-cluster.sh run <NODE_ID>

set -e

seed() {
    echo "=== Insertando 5 donantes de prueba ==="
    PGPASSWORD=hospital_pass psql -h localhost -U hospital_user -d hospital_db -c "
        INSERT INTO registros_donantes (nombre, tipo_sangre, organo, disponible)
        SELECT * FROM (VALUES
            ('Ana Lopez',    'O+', 'Corazon', true),
            ('Carlos Ruiz',  'A+', 'Riñon',   true),
            ('Maria Gomez',  'B+', 'Higado',  true),
            ('Pedro Diaz',   'AB+','Pulmon',  true),
            ('Laura Martin', 'O-', 'Corazon', true)
        ) AS v(nombre, tipo_sangre, organo, disponible)
        WHERE NOT EXISTS (
            SELECT 1 FROM registros_donantes WHERE nombre = v.nombre
        );
    "
    echo "Done."
    PGPASSWORD=hospital_pass psql -h localhost -U hospital_user -d hospital_db -c "
        SELECT id, nombre, organo FROM registros_donantes ORDER BY id;
    "
}

run() {
    local NODE_ID=$1
    [ -z "$NODE_ID" ] && { echo "Uso: ./demo-cluster.sh run <NODE_ID>"; exit 1; }
    sudo -n date +%s > /dev/null 2>&1 || {
        echo "Ejecuta: echo \"\$USER ALL=(ALL) NOPASSWD: /usr/bin/date\" | sudo tee /etc/sudoers.d/hospital-date"
        exit 1
    }
    echo "=== Iniciando nodo $NODE_ID ==="
    NODE_ID=$NODE_ID java -jar target/hospital-0.0.1-SNAPSHOT.jar
}

case "$1" in
    seed) seed ;;
    run)  run "$2" ;;
    *)    echo "Uso: ./demo-cluster.sh seed | run <NODE_ID>" ;;
esac
