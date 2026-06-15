# ConciertosCerca

Plataforma para descubrir conciertos cercanos: una app móvil multiplataforma (Android + iOS) que consume un backend que scrapea, enriquece y sirve la información de conciertos, salas y artistas.

El repositorio es un **monorepo** con dos proyectos independientes:

| Carpeta | Proyecto | Stack |
|---------|----------|-------|
| [`conciertosFront/`](conciertosFront) | App móvil | Kotlin Multiplatform + Compose Multiplatform (Android + iOS) |
| [`conciertosBack/`](conciertosBack) | Backend | Spring Boot (Java), arquitectura hexagonal, SQLite |

---

## Arquitectura general

```
┌─────────────────────────┐         HTTP (sync)          ┌──────────────────────────┐
│      conciertosFront      │  ◄───────────────────────►   │       conciertosBack       │
│  Compose Multiplatform    │      GET /api/v1/**          │  api (Spring Boot, 8080)   │
│  Android · iOS            │                              │  ├─ scraper (java-library) │
│  Room (SQLite local)      │                              │  ├─ admin-web (8081)       │
│  cache-first + sync delta │                              │  └─ SQLite (WAL)           │
└─────────────────────────┘                              └──────────────────────────┘
```

- El **front** mantiene una copia local (Room/SQLite) y sincroniza por delta (`?since=`), funcionando en modo *cache-first* (la UI lee de local y refresca en segundo plano).
- El **back** ejecuta un pipeline de scraping + enriquecimiento (geocoding, LLM) y expone un API de solo lectura para la app, más un API de administración protegido.

---

## conciertosFront — App móvil

App de **Kotlin Multiplatform** con UI compartida en **Compose Multiplatform**, corriendo en Android e iOS desde el mismo código.

### Funcionalidades
- Mapa de conciertos con **MapLibre** (marcadores numerados por sala).
- Listado de conciertos con **filtro por fecha** y pantalla de detalle.
- Pantallas de artistas y salas.
- Sincronización delta contra el backend con recuperación automática de la base local.

### Stack
- **Kotlin** 2.3.21 · **Compose Multiplatform** 1.11.0
- **Room** 2.7.2 + `androidx.sqlite` bundled (persistencia local multiplataforma)
- **Ktor Client** 3.1.2 (HTTP + JSON)
- **Koin** 4.2.1 (inyección de dependencias)
- **MapLibre** 0.13.0 (mapas) · **Coil** 3.2.0 (imágenes)
- Android `minSdk 24` / `compileSdk 36` · AGP 9.1.1

### Módulos Gradle
- `:shared` — lógica común multiplataforma (datos, dominio, UI Compose, DI).
- `:androidApp` — entry point Android.
- `iosApp/` — proyecto Xcode (entry point iOS).

### Ejecutar
```bash
cd conciertosFront

# Android (debug, dispositivo/emulador conectado)
./gradlew :androidApp:installDebug

# Build del APK release
./gradlew :androidApp:assembleRelease

# iOS: abrir iosApp/iosApp.xcodeproj en Xcode y ejecutar
```

`local.properties` (no versionado) debe contener la ruta del SDK de Android:
```
sdk.dir=/ruta/a/tu/Android/sdk
```

---

## conciertosBack — Backend

Backend en **Spring Boot** (Java) siguiendo **arquitectura hexagonal** (Ports & Adapters). Proyecto Gradle multi-módulo.

### Módulos

| Módulo | Tipo | Puerto | Propósito |
|--------|------|--------|-----------|
| `api` | Spring Boot app | 8080 | API pública de sync (`/api/v1/**`), persistencia (SQLite), orquestación de sync y todos los endpoints de admin (`/api/admin/**`) |
| `scraper` | java-library | — | Scraping web, parseo HTML y enriquecimiento de artistas. Sin controllers ni escritura en BD |
| `admin-web` | Spring Boot app | 8081 | UI web para relleno manual de calidad de datos y CRUD manual de salas/artistas/conciertos. Sobre todo un proxy HTTP a `api` |
| `shared` | java-library | — | Records de dominio compartidos entre módulos |

### Capas (hexagonal)
- `domain/` — Records Java puros, sin anotaciones de framework.
- `application/ports/{in,out}/` — Interfaces de puertos de entrada y salida.
- `application/` — Casos de uso (`*UseCase`): sync, calidad de datos, geocoding, CRUD manual.
- `adapters/in/` — Controllers REST (`*Api`) y scheduler.
- `adapters/out/` — Adaptadores SQLite + servicios externos (cada integración externa tiene una variante `NoOp*` que se activa si falta su API key, de modo que la app siempre arranca).

### Pipeline de datos
Scraping de **conciertos.club** y **alcalaesmusica.org** → persistencia → registro de huecos en `data_quality` → autorelleno (Tavily + LLM para texto, geocoding para coordenadas) → revisión humana en `admin-web` para lo que no se puede resolver.

- **Geocoding**: Foursquare Places (búsqueda por nombre) como primario, LocationIQ (validado por dirección) como fallback.
- **Sync programado**: corre **dentro de la app** (`@Scheduled`, `ScheduledSyncRunner`), semanal por defecto en producción. Configurable vía `app.sync.cron`.

### Stack
- **Spring Boot** (Java records, sin Lombok)
- **SQLite** con WAL (`journal_mode=WAL`) — esquema en `api/src/main/resources/schema.sql`, auto-ejecutado al arrancar.
- Integraciones opcionales: Tavily, OpenAI, LocationIQ, Foursquare.

### Ejecutar
```bash
cd conciertosBack

./gradlew build                 # compilar + tests de todos los módulos
./gradlew test                  # solo tests
./gradlew bootRun               # arranca api en :8080
./gradlew :admin-web:bootRun    # arranca admin-web en :8081 (requiere api corriendo)
```

### Configuración
La config de runtime vive en `config/application.properties` (**no versionado**). Cópiala desde la plantilla y rellena tus valores:
```bash
cp conciertosBack/config/application.properties.example conciertosBack/config/application.properties
```
Las API keys externas son **opcionales**: si faltan, se activa el adaptador `NoOp*` correspondiente y esa integración queda deshabilitada sin romper el arranque.

> ⚠️ **Seguridad**: cambia `app.admin.password` (en la plantilla está como `CHANGE_ME`). Los endpoints `/api/admin/**` requieren HTTP Basic auth.

---

## Contrato de API (sync móvil)

Endpoints de solo lectura que consume la app:

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `HEAD/GET` | `/api/v1/salas-concierto?since={ISO8601}` | Salas |
| `HEAD/GET` | `/api/v1/artists?since={ISO8601}` | Artistas |
| `HEAD/GET` | `/api/v1/concerts?since={ISO8601}` | Conciertos (incluye `deleted_ids`) |

- `HEAD` devuelve `200` (hay cambios) o `204` (sin cambios).
- `GET` devuelve `{ timestamp, data }`.
- Los conciertos se filtran por `date >= hoy` (los pasados nunca se devuelven).

Endpoints de administración (`/api/admin/**`, protegidos con Basic auth): gestión de sync, calidad de datos, geocoding y CRUD manual. Detalle completo en `conciertosBack/CLAUDE.md`.

---

## Estructura del repositorio

```
conciertosCerca/
├── conciertosFront/        # App KMP (Android + iOS)
│   ├── shared/             # Código común (data, dominio, UI, DI)
│   ├── androidApp/         # Entry point Android
│   └── iosApp/             # Proyecto Xcode (iOS)
├── conciertosBack/         # Backend Spring Boot multi-módulo
│   ├── api/                # App principal (:8080)
│   ├── scraper/            # Scraping + parseo
│   ├── admin-web/          # UI de administración (:8081)
│   ├── shared/             # Dominio compartido
│   └── config/             # application.properties.example
└── README.md
```
