# Observability

The project includes a local observability stack for development and demos.

## Components

- Spring Boot Actuator exposes health and metrics endpoints.
- Micrometer Prometheus registry exposes `/actuator/prometheus`.
- Prometheus scrapes the backend service on the internal Docker network.
- Grafana is provisioned with a Prometheus datasource and a JVM/Spring Boot dashboard.

## Running

Set Grafana credentials in `.env`:

```dotenv
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=change-me
```

Start the stack:

```powershell
docker compose up -d --build
```

Open Grafana:

```text
http://127.0.0.1:3000
```

## Files

- `observability/prometheus.yml`: scrape configuration.
- `observability/grafana/provisioning/datasources/prometheus.yml`: Grafana datasource.
- `observability/grafana/provisioning/dashboards/dashboard.yml`: dashboard provisioning.
- `observability/grafana/provisioning/dashboards/jvm-spring-boot.json`: JVM/Spring Boot dashboard.

## Notes

Grafana is bound to `127.0.0.1:3000` in Compose so it is not exposed on all network interfaces by default. For any shared environment, change the default credentials and review network exposure before deploying.
