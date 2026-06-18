.PHONY: init_dev run_dev

init_dev:
	@echo "=> 🧹 Borrando entorno viejo y forzando reseteo puro (Maven Clean)..."
	sudo docker compose down
	sudo rm -rf target/
	@echo "=> 📦 Descargando dependencias iniciales de Maven (Opcional)..."
	sudo docker compose run --rm app mvn dependency:go-offline

run_dev:
	@echo "=> 🧹 Despejando puertos previos..."
	-sudo docker compose down 2>/dev/null || true
	-sudo fuser -k 8080/tcp 2>/dev/null || true
	-sudo fuser -k 5433/tcp 2>/dev/null || true
	@echo "=> 🚀 Iniciando Base de datos y Aplicación ya buildeadas..."
	sudo docker compose up

republish:
	@echo "=> 📦 Building imagen..."
	docker build -t 192.168.101.3:5000/hospital:latest .
	@echo "=> 🔼 Pushing a registry local..."
	docker push 192.168.101.3:5000/hospital:latest
	@echo "=> ✅ Imagen actualizada en registry"
	
build-extract:
	@echo "=> 🐳 Build de app-node1 con compose..."
	@echo "=> 📦 Extrayendo jar del contenedor builder..."
	sudo docker compose build app-node1
	sudo docker rm temp 2>/dev/null || true
	sudo docker create --name temp hospital-cluster:latest
	sudo docker cp temp:/app/hospital.jar target/hospital-0.0.1-SNAPSHOT.jar
	sudo docker rm temp

deploy:
	@echo "=> 🗑️  Removiendo stack anterior..."
	sudo docker stack rm hospital || true
	@echo "⏳ Esperando que se elimine..."
	sleep 5
	@echo "=> 🚀 Deployando stack..."
	sudo docker stack deploy -c stack.yml hospital
	@echo "=> ⏳ Esperando que los nodos arranquen (60s)..."
	sleep 60
	@echo "=> ✅ Deploy completado"