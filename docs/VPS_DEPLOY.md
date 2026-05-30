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

La lista completa y actualizada está en [`.env.example`](../.env.example) en la raíz del proyecto. Copiarlo y completar los valores:

```bash
cp .env.example .env
# editar .env con los valores reales
```

Variables mínimas obligatorias:

```env
# Cosmos DB
COSMOS_DB_ENDPOINT=https://tu-cuenta.documents.azure.com:443/
COSMOS_DB_KEY=tu_clave
COSMOS_DB_DATABASE=apalabrazos

# JWT
JWT_SECRET=secreto_largo_y_aleatorio
JWT_ISSUER=apalabrazos
JWT_AUDIENCE=apalabrazos-client

# Generador de preguntas IA (scheduler periódico)
AI_GENERATOR_ENABLED=false
AI_API_URL=http://localhost:11434/api/chat
AI_API_KEY=
AI_MODEL=gemma3:4b
AI_GENERATOR_HOUR=8
AI_GENERATOR_MINUTE=0
AI_GENERATOR_TIMEZONE=Europe/Madrid

# Docker Compose
GHCR_OWNER=trutoman
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
