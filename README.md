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

## Container images

The local Kubernetes version of CloudStore provides two container images published on GitHub Container Registry:

- `ghcr.io/nicholasatt/cloudstore-k8s-local-client:main`
- `ghcr.io/nicholasatt/cloudstore-k8s-local-server:main`

Both images are built manually from the `main` branch after the project-specific codebase alignment.

Each image includes OCI labels that identify:

- source repository;
- branch name;
- component name;
- commit SHA.

This makes each package traceable to the exact version of the codebase used to build it.