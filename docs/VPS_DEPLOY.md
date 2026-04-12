# Despliegue a VPS (Producción)

Servidor objetivo:
- IP: 138.68.174.4
- Usuario: root

## 1) Preparación en VPS (una sola vez)

1. Instalar Docker
2. Crear carpeta de despliegue (por ejemplo `/opt/apalabrazos`)
3. Copiar a la VPS estos archivos:
   - `docker-compose.prod.yml`
   - `.env` (no subir a git)
4. Login a GHCR desde la VPS con un token de GitHub con permiso `read:packages`.

## 2) Variables requeridas en `.env`

```env
GHCR_OWNER=TU_ORG_O_USUARIO_GITHUB

COSMOS_DB_ENDPOINT=https://cosmos-apalabrazos.documents.azure.com:443/
COSMOS_DB_KEY=REEMPLAZAR_POR_KEY_VIGENTE
COSMOS_DB_DATABASE=apalabrazosDB
COSMOS_DB_QUESTIONS_CONTAINER=Questions

JWT_SECRET=REEMPLAZAR_POR_SECRETO_LARGO
JWT_ISSUER=apalabrazos-api
JWT_AUDIENCE=apalabrazos-client

SEED_QUESTIONS_ON_START=false
QUESTIONS_SEED_FILE=classpath:/Apalabrazos/data/questions2.json
JAVA_OPTS=-Xms256m -Xmx512m

# AI Question Generator (opcional - genera preguntas con IA periódicamente)
AI_GENERATOR_ENABLED=false
AI_API_KEY=TU_OPENROUTER_API_KEY
AI_API_URL=https://openrouter.ai/api/v1/chat/completions
AI_MODEL=openai/gpt-4o-mini
AI_QUESTIONS_PER_LETTER=3
AI_GENERATOR_HOUR=8
AI_GENERATOR_MINUTE=0
AI_GENERATOR_TIMEZONE=Europe/Madrid
AI_GENERATOR_OUTPUT_DIR=src/main/resources/Apalabrazos/data
AI_GENERATOR_FILENAME=questions2.json
AI_GENERATOR_RUN_ON_START=false
```

## 3) Arranque

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

## 4) Actualización automática

- El workflow publica imágenes a GHCR.
- `watchtower` detecta nuevos tags y reinicia `apalabrazos-backend` automáticamente.

## 5) Verificación rápida

```bash
docker ps
curl -I http://localhost:8080/
docker logs --tail 100 apalabrazos-backend
# Verificar que el generador de preguntas usa OpenRouter
docker logs apalabrazos-backend | grep -i "openrouter"
```

## Notas de seguridad

- No hardcodear secretos en `Dockerfile`.
- Rotar cualquier `COSMOS_DB_KEY` expuesta durante pruebas.
- Limitar acceso al puerto 8080 con firewall o reverse proxy (Caddy/Nginx) según dominio.

## Siguiente paso (próxima sesión): reverse proxy + dominio

Objetivo: pasar de acceso por IP:8080 a dominio con HTTPS.

Plan:
1. Elegir proxy (recomendado Caddy por TLS automático).
2. Configurar DNS del dominio para que apunte a `138.68.174.4`.
3. Publicar solo puertos `80` y `443` en el proxy.
4. Hacer reverse proxy al backend interno (`apalabrazos-backend:8080`).
5. Forzar redirección HTTP→HTTPS.
6. Cerrar acceso público directo a `8080` (firewall/security group).

Checklist previo:
- [ ] Dominio registrado y control de DNS disponible.
- [ ] Registro `A` (y opcional `AAAA`) apuntando a la VPS.
- [ ] Decidir si Caddy/Nginx vive en el mismo `docker-compose` o en uno separado.
