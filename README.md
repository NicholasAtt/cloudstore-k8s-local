# CloudStore

CloudStore is a full-stack demo e-commerce system composed of a Java backend, a Python Streamlit frontend, and MySQL + Redis infrastructure.

## Quick Start With Docker

### Create your `.env`

From repository root:

```bash
cp .env.example .env
```

Then edit `.env` and set values, especially:

- `JWT_SECRET` must be Base64 and at least 32 bytes after decoding.

Generate a secure secret:

```bash
openssl rand -base64 32
```

Paste it into `JWT_SECRET` in `.env`.

### Build and start all services

```bash
docker compose up -d --build
```

## Redis Inspection (Keys, Types, TTL)

List keys with type and TTL:

```bash
docker compose exec redis sh -lc 'redis-cli --scan | while read k; do t=$(redis-cli TYPE "$k"); ttl=$(redis-cli TTL "$k"); printf "%-60s | %-8s | TTL=%s\n" "$k" "$t" "$ttl"; done | sort'
```