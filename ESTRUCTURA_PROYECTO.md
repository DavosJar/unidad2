# Estructura del Proyecto

## Resumen

- **Tecnología principal:** Spring Boot 4.1.0 con Java 21
- **Gestor de dependencias:** Maven
- **Base de datos:** PostgreSQL
- **Arquitectura:** MVC (Model-View-Controller) con servicios REST

---

## Árbol de directorios

```
unidad2/
├── .gitignore
├── ESTRUCTURA_PROYECTO.md
├── README.md
└── hospital/
    ├── .dockerignore
    ├── .gitattributes
    ├── .gitignore
    ├── .mvn/
    │   └── wrapper/
    │       └── maven-wrapper.properties
    ├── docker-compose.yml
    ├── Dockerfile
    ├── Makefile
    ├── mvnw
    ├── mvnw.cmd
    ├── pom.xml
    ├── prueba.sh
    ├── test_endpoints.sh
    ├── src/
    │   ├── main/
    │   │   ├── java/com/gruposiete/hospital/
    │   │   │   ├── HospitalApplication.java          # Punto de entrada
    │   │   │   ├── controller/
    │   │   │   │   ├── ControladorDonantes.java       # Endpoints REST de donantes
    │   │   │   │   └── ControladorReservas.java        # Endpoints REST de reservas
    │   │   │   ├── model/
    │   │   │   │   ├── RegistroDonante.java            # Entidad JPA: donante
    │   │   │   │   ├── RepositorioDonantes.java        # Repositorio JPA de donantes
    │   │   │   │   ├── RepositorioReservas.java        # Repositorio JPA de reservas
    │   │   │   │   └── Reserva.java                    # Entidad JPA: reserva
    │   │   │   └── service/
    │   │   │       ├── GestionDonantes.java            # Lógica de negocio de donantes
    │   │   │       └── GestionReservas.java            # Lógica de negocio de reservas
    │   │   └── resources/
    │   │       └── application.properties              # Configuración de la app
    │   └── test/java/com/gruposiete/hospital/
    │       └── HospitalApplicationTests.java           # Tests de integración
    └── target/                                         # Compilados (generado por Maven)
        ├── classes/
        └── test-classes/
```

---

## Descripción por capa

### 1. Configuración del proyecto (`hospital/`)

| Archivo | Propósito |
|---|---|
| `pom.xml` | Definición del proyecto Maven con dependencias: Spring Boot (Web MVC, JPA, WebSocket, Actuator), PostgreSQL y sus contrapartes de testing |
| `Dockerfile` | Imagen Docker para el despliegue del servicio |
| `docker-compose.yml` | Orquestación de contenedores (app + base de datos) |
| `Makefile` | Comandos automatizados para build, test, Docker, etc. |
| `mvnw` / `mvnw.cmd` | Maven Wrapper (no requiere Maven instalado globalmente) |
| `prueba.sh` / `test_endpoints.sh` | Scripts de prueba para los endpoints REST |
| `application.properties` | Configuración de la aplicación (puerto, base de datos, JPA, etc.) |

### 2. Capa de presentación — Controladores REST (`controller/`)

- **`ControladorDonantes`** — Expone endpoints para operaciones CRUD sobre donantes (registro, listado, consulta por ID, actualización, eliminación).
- **`ControladorReservas`** — Expone endpoints para gestionar reservas de donantes (crear, listar, consultar, cancelar).

### 3. Capa de modelo — Entidades y repositorios (`model/`)

- **`RegistroDonante`** — Entidad JPA que modela un donante (nombre, tipo de sangre, contacto, etc.).
- **`Reserva`** — Entidad JPA que modela una reserva (donante asociado, fecha, estado, etc.).
- **`RepositorioDonantes`** — Interfaz `JpaRepository` para operaciones de base de datos sobre donantes.
- **`RepositorioReservas`** — Interfaz `JpaRepository` para operaciones de base de datos sobre reservas.

### 4. Capa de servicio — Lógica de negocio (`service/`)

- **`GestionDonantes`** — Contiene la lógica de negocio para donantes (validaciones, transformaciones, reglas de negocio).
- **`GestionReservas`** — Contiene la lógica de negocio para reservas (disponibilidad, reglas de asignación).

### 5. Capa de pruebas (`test/`)

- **`HospitalApplicationTests`** — Test de integración básico que verifica que el contexto de Spring Boot se carga correctamente.

---

## Dependencias principales (Maven)

| Dependencia | Propósito |
|---|---|
| `spring-boot-starter-webmvc` | API REST (controllers, validación, serialización JSON) |
| `spring-boot-starter-data-jpa` | Persistencia con JPA/Hibernate |
| `spring-boot-starter-websocket` | Soporte para WebSockets |
| `spring-boot-starter-actuator` | Monitoreo y métricas de la aplicación |
| `postgresql` | Driver para conexión a PostgreSQL |

---

## Tecnologías

| Tecnología | Versión |
|---|---|
| Spring Boot | 4.1.0 |
| Java | 21 |
| Maven | (Wrapper incluido) |
| PostgreSQL | (configurado en Docker) |
| Docker | (Dockerfile + compose incluidos) |
