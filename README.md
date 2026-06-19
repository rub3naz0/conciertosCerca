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

## Funcionalidades principales

**App móvil (conciertosFront)**
- Descubrimiento de conciertos cercanos en **mapa** (MapLibre, marcadores por sala) y en **listado** con detalle deslizable.
- Navegación cruzada entre concierto, **artista** y **sala** desde el detalle.
- **Filtro por rango de fechas** (chip `HOY` / rango) aplicado a las cuatro pestañas.
- **Modo offline / cache-first**: la UI lee de la base local (Room/SQLite) y refresca en segundo plano.
- **Sincronización delta** (`?since=`) contra el backend, con recuperación automática de la base local.

**Backend (conciertosBack)**
- **Scraping** automático de fuentes (conciertos.club, alcalaesmusica.org) con parseo de HTML.
- **Enriquecimiento de datos**: geocoding de salas (Foursquare + LocationIQ) y relleno de texto vía LLM (Tavily + OpenAI).
- **Sync programado** dentro de la app (`@Scheduled`), semanal por defecto y configurable.
- **API de solo lectura** para la app (delta sync) y **API de administración** protegido con Basic auth.
- **Panel de administración web** para revisión humana de calidad de datos y CRUD manual de salas/artistas/conciertos.

---

## Quick start

Pasos mínimos para clonar y arrancar. Elige qué necesitas ejecutar.

### 1. Clonar
```bash
git clone <repo-url> conciertosCerca
cd conciertosCerca
```

### 2. App móvil (Android) — apunta a producción por defecto
La app resuelve el backend contra `https://api.conciertoscerca.es` automáticamente (`AppConfig.PRODUCTION_BASE_URL`), así que **no necesitas backend local** para probarla.

Único requisito: crear `conciertosFront/local.properties` (no versionado) con la ruta de tu SDK de Android:
```bash
echo "sdk.dir=/ruta/a/tu/Android/sdk" > conciertosFront/local.properties
```
Luego, con un emulador o dispositivo conectado:
```bash
cd conciertosFront
./gradlew :androidApp:installDebug
```
> iOS requiere **Mac + Xcode**: abre `iosApp/iosApp.xcodeproj` y ejecuta.

### 3. Backend — opcional, solo si quieres servir/scrapear en local
El backend **arranca sin configuración** (`spring.config.import` usa `optional:`), creando la base SQLite al vuelo. Las integraciones externas sin API key caen a un adaptador `NoOp*`, así que no rompen el arranque:
```bash
cd conciertosBack
./gradlew bootRun            # api en :8080
```
Para una instancia **funcional** (scraping real, credenciales de admin, geocoding/LLM), copia la plantilla y rellena tus valores:
```bash
cp config/application.properties.example config/application.properties
# edita config/application.properties: cambia app.admin.password (viene como CHANGE_ME)
```

### Resumen de requisitos

| Quiero ejecutar… | Requisito mínimo |
|---|---|
| App Android (contra prod) | `local.properties` con `sdk.dir` |
| App iOS | Mac + Xcode + `local.properties` |
| Backend que arranca | Nada (config es opcional) |
| Backend funcional (scraping/admin) | Copiar y rellenar `application.properties` |

---

## conciertosFront — App móvil (ConciertosCerca)

**ConciertosCerca** es una app de **Kotlin Multiplatform** con UI compartida en **Compose Multiplatform**, corriendo en Android e iOS desde el mismo código.

### Navegación (4 tabs)
La pantalla principal usa una `NavigationBar` con cuatro pestañas:

| Tab | Contenido |
|-----|-----------|
| **MAPA** | Mapa de conciertos con **MapLibre** (marcadores numerados por sala) |
| **CONCIERTOS** | Listado de conciertos, con detalle deslizable |
| **ARTISTAS** | Conciertos agrupados por artista |
| **SALAS** | Conciertos agrupados por sala (con salto al mapa) |

Una **top bar** compartida muestra el logo + nombre, un indicador de sincronización en segundo plano y un **filtro de rango de fechas** (chip `HOY` / rango) que afecta a las cuatro pestañas. El detalle de concierto se abre como hoja deslizable y permite navegar de forma cruzada al artista o a la sala.

### Sincronización
Sincronización delta contra el backend (`?since=`) en modo *cache-first*, con recuperación automática de la base local.

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
