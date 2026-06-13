#!/bin/bash
sudo -v  # cachea contraseña sudo
echo "Iniciando PostgreSQL para el cluster..."
sudo docker run -d --name hospital-db -p 5432:5432 \
  -e POSTGRES_DB=hospital_db \
  -e POSTGRES_USER=hospital_user \
  -e POSTGRES_PASSWORD=hospital_pass \
  postgres:17-alpine
echo "BD lista en localhost:5432"
