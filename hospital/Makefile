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