# Convenience wrappers. Set PROFILE=supabase to target Supabase instead of the local database.
PROFILE ?= local
SERVICE := $(if $(filter supabase,$(PROFILE)),app-supabase,app)
COMPOSE := docker compose --profile $(PROFILE)

.PHONY: build test openapi image up down logs restart ps migrate migrate-info backup

build:            ## Compile and package the JAR (requires Java 21)
	./mvnw -B package -DskipTests

test:             ## Run unit and integration tests (requires Docker for Testcontainers)
	./mvnw -B verify

openapi:          ## Regenerate docs/openapi.yaml from the code
	OPENAPI_WRITE=true ./mvnw -B test -Dtest=OpenApiSpecTest

image:            ## Build the Docker image
	$(COMPOSE) build

up:               ## Start the stack
	$(COMPOSE) up -d --build

down:             ## Stop the stack (data volume is kept)
	$(COMPOSE) down

logs:             ## Follow application logs
	$(COMPOSE) logs -f $(SERVICE)

restart:          ## Restart the application container
	$(COMPOSE) restart $(SERVICE)

ps:               ## Show container status and health
	$(COMPOSE) ps

migrate:          ## Apply Flyway migrations without starting the app
	docker compose --profile migrate run --rm flyway migrate

migrate-info:     ## Show migration status
	docker compose --profile migrate run --rm flyway info

backup:           ## Dump the local PostgreSQL database to ./backups
	mkdir -p backups
	docker compose --profile local exec -T postgres sh -c 'pg_dump -U "$$POSTGRES_USER" -d "$$POSTGRES_DB" -Fc' > backups/eduplay-$$(date -u +%Y%m%dT%H%M%SZ).dump
