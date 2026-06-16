#!/bin/bash
# demo-cluster.sh
# seed:  inserta 5 donantes (una vez)
# test:  bateria de 10 operaciones mezcladas

seed() {
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

test() {
    BASE=${1:-http://localhost:8080}
    echo "=== 10 operaciones contra $BASE ==="
    echo ""

    # 1-3: consultas (GET donantes)
    for i in 1 2 3; do
        HTTP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/donantes")
        echo "[$i] GET /api/donantes -> $HTTP"
    done

    # 4-6: intentos de reserva sobre distintos donantes
    for i in 4 5 6; do
        DON=$((i - 3))
        HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
            "$BASE/api/reservas?idDonante=$DON&paciente=Paciente$i")
        echo "[$i] POST reserva donante $DON -> $HTTP"
    done

    # 7-8: consultar reservas y donantes disponibles
    HTTP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/reservas")
    echo "[7] GET /api/reservas -> $HTTP"
    HTTP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/donantes/disponibles")
    echo "[8] GET /api/donantes/disponibles -> $HTTP"

    # 9-10: dos reservas mas contra el mismo donante para ver exclusion
    for i in 9 10; do
        HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
            "$BASE/api/reservas?idDonante=5&paciente=Paciente$i")
        echo "[$i] POST reserva donante 5 -> $HTTP"
    done

    echo ""
    echo "=== Resultado final ==="
    curl -s "$BASE/api/reservas" | python3 -m json.tool 2>/dev/null || curl -s "$BASE/api/reservas"
}

case "$1" in
    seed) seed ;;
    test) test "$2" ;;
    *)    echo "Uso: ./demo-cluster.sh seed | test [URL]" ;;
esac
