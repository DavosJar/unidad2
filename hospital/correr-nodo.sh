#!/bin/bash
if [ $# -ne 2 ]; then
    echo "Uso: ./correr-nodo.sh NODE_ID IP_BD"
    echo "Ej:  ./correr-nodo.sh 2 192.168.0.100"
    exit 1
fi
sudo -n date +%s > /dev/null 2>&1 || {
    echo "ERROR: sudo requiere contraseña. Ejecuta primero:"
    echo "  echo \"\$USER ALL=(ALL) NOPASSWD: /usr/bin/date\" | sudo tee /etc/sudoers.d/hospital-date"
    exit 1
}
NODE_ID=$1 DB_URL=jdbc:postgresql://$2:5432/hospital_db java -jar target/hospital-0.0.1-SNAPSHOT.jar
