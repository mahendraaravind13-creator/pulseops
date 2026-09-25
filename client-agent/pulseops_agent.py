#!/usr/bin/env python3
"""
PulseOps agent: reports this machine's CPU, memory and disk usage to PulseOps.

    pip install -r requirements.txt
    python pulseops_agent.py --api-key pk_... --service checkout-api --url http://localhost:8088

The agent only reports numbers. It never decides whether something is wrong; alert rules on the
server do that. It sends `recordedAt` with every sample, so a retried request is recognised as a
duplicate by the server instead of being counted twice.
"""

import argparse
import random
import socket
import sys
import time
from datetime import datetime, timezone

import psutil
import requests


def read_sample(service: str) -> dict:
    return {
        "service": service,
        "hostname": socket.gethostname(),
        "cpu": round(psutil.cpu_percent(interval=1), 1),
        "memory": round(psutil.virtual_memory().percent, 1),
        "disk": round(psutil.disk_usage("C:\\" if sys.platform == "win32" else "/").percent, 1),
        "recordedAt": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
    }


def send(session: requests.Session, url: str, api_key: str, sample: dict, max_attempts: int = 4) -> None:
    """POST one sample. Retries network errors, 5xx and 429 with backoff; gives up on other 4xx."""
    for attempt in range(1, max_attempts + 1):
        try:
            response = session.post(f"{url}/api/v1/ingest", json=sample,
                                    headers={"X-API-Key": api_key}, timeout=5)
        except requests.RequestException as error:
            wait = backoff(attempt)
            print(f"  network error ({error.__class__.__name__}), retrying in {wait:.1f}s")
            time.sleep(wait)
            continue

        if response.status_code == 202:
            print(f"  sent  cpu={sample['cpu']:5.1f}%  mem={sample['memory']:5.1f}%  disk={sample['disk']:5.1f}%")
            return
        if response.status_code == 401:
            sys.exit("Invalid API key. Copy it from Settings in the PulseOps dashboard.")
        if response.status_code == 429:
            wait = float(response.headers.get("Retry-After", backoff(attempt)))
            print(f"  rate limited, waiting {wait:.0f}s")
            time.sleep(wait)
            continue
        if response.status_code >= 500:
            wait = float(response.headers.get("Retry-After", backoff(attempt)))
            print(f"  server unavailable ({response.status_code}), retrying in {wait:.1f}s")
            time.sleep(wait)
            continue
        print(f"  rejected ({response.status_code}): {response.text[:200]}")
        return
    print("  giving up on this sample after retries")


def backoff(attempt: int) -> float:
    """Exponential backoff with jitter: about 1s, 2s, 4s, 8s."""
    return min(2 ** (attempt - 1), 30) + random.uniform(0, 0.5)


def main() -> None:
    parser = argparse.ArgumentParser(description="PulseOps monitoring agent")
    parser.add_argument("--api-key", required=True, help="tenant API key (pk_...)")
    parser.add_argument("--service", required=True, help="name of the service running on this host")
    parser.add_argument("--url", default="http://localhost:8088", help="PulseOps base URL")
    parser.add_argument("--interval", type=int, default=10, help="seconds between samples")
    args = parser.parse_args()

    print(f"PulseOps agent: service={args.service} url={args.url} every {args.interval}s (Ctrl+C to stop)")
    session = requests.Session()
    while True:
        started = time.monotonic()
        send(session, args.url.rstrip("/"), args.api_key, read_sample(args.service))
        time.sleep(max(0.0, args.interval - (time.monotonic() - started)))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nstopped")
