# Deploying PulseOps

PulseOps runs at **https://pulseops.atlas-theproject.duckdns.org** on the same EC2 instance as Project Atlas
(t3.micro in ap-south-1: 2 vCPU, 1 GB RAM, 4 GB swap, Docker and Compose already installed).

Atlas's Caddy container owns ports 80 and 443 and terminates TLS for both projects. PulseOps is a second
site in that Caddyfile, proxied to PulseOps's own nginx over the `atlas_default` Docker network.
[docker-compose.aws.yml](docker-compose.aws.yml) explains every difference from the local stack; all of them
come from the 1 GB of RAM.

## Layout on the box

```
/opt/pulseops/docker-compose.aws.yml   copy of deploy/docker-compose.aws.yml
/opt/pulseops/.env.aws                 secrets, mode 600, never in git (see below)
/opt/atlas/deploy/Caddyfile            Atlas's Caddyfile, with the PulseOps site block appended
```

`.env.aws` holds:

```
PULSEOPS_TAG=<git short sha of the images that were shipped>
PULSEOPS_PUBLIC_URL=https://pulseops.atlas-theproject.duckdns.org
DB_PASSWORD=<random>
JWT_SECRET=<random, 64 hex chars>
GEMINI_API_KEY=<optional>
```

## Releasing a new version

Images are built on a developer machine and shipped as a tarball. Building with Maven on a 1 GB instance
is not viable.

```bash
# 1. Build locally and tag with the commit being shipped
docker compose build
TAG=$(git rev-parse --short HEAD)
docker tag pulseops-stack-app:latest pulseops-app:$TAG
docker tag pulseops-stack-web:latest pulseops-web:$TAG

# 2. Ship both images (about 200 MB compressed) and load them on the box
docker save pulseops-app:$TAG pulseops-web:$TAG | gzip -1 \
  | ssh -i atlas.pem ec2-user@<box> 'gunzip | sudo docker load'

# 3. Point the stack at the new tag and roll it out
ssh -i atlas.pem ec2-user@<box> "cd /opt/pulseops \
  && sed -i 's/^PULSEOPS_TAG=.*/PULSEOPS_TAG=$TAG/' .env.aws \
  && sudo docker compose -f docker-compose.aws.yml --env-file .env.aws up -d \
  && sudo docker image prune -f"
```

If `deploy/docker-compose.aws.yml` changed, `scp` it to `/opt/pulseops/` before step 3.

## The Caddy site block

This is what routes the hostname to PulseOps. It lives in Atlas's Caddyfile because Caddy is Atlas's
container:

```
pulseops.atlas-theproject.duckdns.org {
	encode zstd gzip
	log {
		output stdout
	}
	reverse_proxy pulseops-web:80
}
```

**It must also be committed to the Atlas repository's `deploy/Caddyfile`.** Atlas's deploy workflow runs
`git reset --hard origin/main` on the box, which would otherwise drop the block on the next Atlas release
and take PulseOps offline (Atlas itself would be unaffected).

After editing the Caddyfile on the box, apply it without downtime:

```bash
sudo docker exec atlas-caddy-1 caddy validate --config /etc/caddy/Caddyfile
sudo docker exec atlas-caddy-1 caddy reload   --config /etc/caddy/Caddyfile
```

Caddy obtains the Let's Encrypt certificate itself. The hostname resolves because DuckDNS answers for any
subdomain of `atlas-theproject.duckdns.org`.

## Checking on it

```bash
sudo docker compose -f /opt/pulseops/docker-compose.aws.yml --env-file /opt/pulseops/.env.aws ps
sudo docker compose -f /opt/pulseops/docker-compose.aws.yml --env-file /opt/pulseops/.env.aws logs --tail=100 app
curl -s https://pulseops.atlas-theproject.duckdns.org/actuator/health
free -m    # both projects together sit slightly above physical RAM; a few hundred MB in swap is expected
```

## What is different from the local stack, and why

| Local (`docker-compose.yml`) | Box (`deploy/docker-compose.aws.yml`) | Reason |
|---|---|---|
| `apache/kafka` (JVM), about 1 GB resident | `apache/kafka-native`, about 175 MB | RAM |
| 2 app replicas | 1 replica | RAM. The consumer group and nginx upstream are unchanged, so `--scale app=2` works on a larger instance |
| JVM sizes itself from the host | 512 MB limit, 256 MB heap, serial GC, C1 JIT | RAM |
| Postgres, Redis, Kafka ports published | no host ports | only Caddy is reachable from the internet |
| nginx on `:8088` | nginx on the shared network only, behind Caddy with TLS | one public entry point |
| Built on the machine | built elsewhere, shipped as images | no Maven on 1 GB |
