# perfect-click-webapp

Internal config supply webapp built with Java 21 + Gradle + Javalin.

## Quickstart

Windows PowerShell:

```powershell
.\scripts\bootstrap.ps1
```

Windows cmd:

```bat
scripts\bootstrap.bat
```

macOS/Linux:

```sh
chmod +x scripts/*.sh
./scripts/bootstrap.sh
```

Then run:

```powershell
.\gradlew.bat run
```

```sh
./gradlew run
```

## Common Commands

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck test
.\scripts\dev-check.ps1
```

```bat
scripts\dev-check.bat
```

```sh
./scripts/dev-check.sh
```

## API

- `GET /health`
- `GET /api/v1/config` list config files and field counts
- `POST /api/v1/config` with JSON body: `{"name":"application-a"}`
- `GET /api/v1/config/{name}/json` full config as JSON for the admin UI
- `GET /api/v1/config/{name}` properties text for consumer applications
  - Optional query param: `?key=field.name` to return only a single field value
- `PUT /api/v1/config/{name}/fields/{key}` with JSON body:
  `{"type":"STRING|INTEGER|LONG|BOOLEAN","value":"raw-value"}`
- `DELETE /api/v1/config/{name}/fields/{key}`
- `DELETE /api/v1/config/{name}`

All responses include `X-Request-Id`. You can pass your own request ID using that header.

## Frontend

The web UI is served from `/` and lets you:

- Create/delete `.properties` files
- Add/edit/delete typed fields
- Copy the consumer endpoint for each config

## Environment

- `PORT` (default `7000`)
- `APP_ENV` (default `dev`)
- `APP_CONFIG_DIR` (default `data/configs`)

## Podman

```powershell
.\scripts\container-up.ps1
```

```bat
scripts\container-up.bat
```

```sh
./scripts/container-up.sh
```

## Using This Template

See [TEMPLATE.md](TEMPLATE.md) for steps to rename package/project values after creating a new repo.
