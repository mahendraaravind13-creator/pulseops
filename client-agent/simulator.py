#!/usr/bin/env python3
"""
PulseOps simulator: several fake services with scripted problems, for demos and manual testing.

    python simulator.py --api-key pk_...                          # 4 healthy services, forever
    python simulator.py --api-key pk_... --scenario cpu-spike --target checkout-api --after 60 --duration 180
    python simulator.py --api-key pk_... --scenario memory-leak --target orders-api --services orders-api
    python simulator.py --api-key pk_... --scenario flood          # exceed the rate limit, see 429s

Scenarios:
  cpu-spike    target's CPU jumps to ~95% for --duration seconds, then recovers (opens, then auto-resolves an incident)
  memory-leak  target's memory climbs steadily past 90% and stays there
  disk-fill    target's disk climbs past 90%
  flood        sends as fast as possible from one service to demonstrate rate limiting
"""

import argparse
import random
import threading
import time
from datetime import datetime, timezone

import requests

SERVICES = ["checkout-api", "orders-api", "payments-worker", "search-api"]


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


class FakeService:
    def __init__(self, name: str):
        self.name = name
        self.cpu = random.uniform(15, 35)
        self.memory = random.uniform(35, 55)
        self.disk = random.uniform(40, 60)
        self.latency = random.uniform(40, 90)

    def healthy_step(self) -> None:
        self.cpu = clamp(self.cpu + random.uniform(-4, 4), 8, 45)
        self.memory = clamp(self.memory + random.uniform(-1.5, 1.5), 30, 60)
        self.disk = clamp(self.disk + random.uniform(-0.2, 0.2), 30, 70)
        self.latency = clamp(self.latency + random.uniform(-8, 8), 30, 120)

    def sample(self, error_rate: float) -> dict:
        return {
            "service": self.name,
            "hostname": f"{self.name}-1",
            "cpu": round(self.cpu, 1),
            "memory": round(self.memory, 1),
            "disk": round(self.disk, 1),
            "latencyMs": round(self.latency, 1),
            "errorRate": round(error_rate, 2),
            "recordedAt": now_iso(),
        }


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def apply_scenario(svc: FakeService, scenario: str, elapsed: float, after: int, duration: int) -> float:
    """Mutates the service for the active scenario and returns its error rate."""
    active = after <= elapsed < after + duration
    if scenario == "cpu-spike" and active:
        svc.cpu = random.uniform(91, 98)
        svc.latency = random.uniform(400, 900)
        return random.uniform(2, 6)
    if scenario == "memory-leak" and elapsed >= after:
        svc.memory = clamp(svc.memory + random.uniform(1.5, 3.0), 0, 97)
        return 0.3
    if scenario == "disk-fill" and elapsed >= after:
        svc.disk = clamp(svc.disk + random.uniform(1.0, 2.0), 0, 98)
        return 0.1
    svc.healthy_step()
    return random.uniform(0, 0.5)


def post(session: requests.Session, url: str, api_key: str, sample: dict) -> int:
    try:
        return session.post(f"{url}/api/v1/ingest", json=sample, headers={"X-API-Key": api_key}, timeout=5).status_code
    except requests.RequestException:
        return 0


def flood(url: str, api_key: str, seconds: int = 20, workers: int = 16) -> None:
    """Many concurrent senders, well above the default 600 samples/minute, so the server starts answering 429."""
    counts: dict[int, int] = {}
    lock = threading.Lock()
    deadline = time.monotonic() + seconds

    def worker(n: int) -> None:
        session = requests.Session()
        svc = FakeService(f"flood-test-{n}")
        while time.monotonic() < deadline:
            status = post(session, url, api_key, svc.sample(0))
            with lock:
                counts[status] = counts.get(status, 0) + 1

    threads = [threading.Thread(target=worker, args=(n,)) for n in range(workers)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    print(f"status code counts over {seconds}s with {workers} senders:", dict(sorted(counts.items())),
          "(202 = accepted, 429 = rate limited, 0 = network error)")


def main() -> None:
    parser = argparse.ArgumentParser(description="PulseOps multi-service simulator")
    parser.add_argument("--api-key", required=True)
    parser.add_argument("--url", default="http://localhost:8088")
    parser.add_argument("--interval", type=float, default=5, help="seconds between rounds")
    parser.add_argument("--scenario", choices=["none", "cpu-spike", "memory-leak", "disk-fill", "flood"], default="none")
    parser.add_argument("--target", default="checkout-api", help="service the scenario applies to")
    parser.add_argument("--after", type=int, default=30, help="seconds before the scenario starts")
    parser.add_argument("--duration", type=int, default=150, help="seconds the cpu-spike lasts")
    parser.add_argument("--start-memory", type=float, default=None,
                        help="initial memory %% of the target, e.g. to continue a memory leak that is already in progress")
    parser.add_argument("--services", default="",
                        help="comma-separated services to simulate (default: the four built-in ones); "
                             "lets two simulator runs drive different services at the same time")
    args = parser.parse_args()
    url = args.url.rstrip("/")
    session = requests.Session()

    if args.scenario == "flood":
        flood(url, args.api_key)
        return

    names = [n.strip() for n in args.services.split(",") if n.strip()] if args.services else list(SERVICES)
    if args.target not in names:
        names.append(args.target)
    services = [FakeService(name) for name in names]
    if args.start_memory is not None:
        for svc in services:
            if svc.name == args.target:
                svc.memory = args.start_memory
    print(f"Simulating {len(services)} services -> {url}, scenario={args.scenario} on {args.target} (Ctrl+C to stop)")
    started = time.monotonic()
    while True:
        elapsed = time.monotonic() - started
        line = []
        for svc in services:
            scenario = args.scenario if svc.name == args.target else "none"
            error_rate = apply_scenario(svc, scenario, elapsed, args.after, args.duration)
            status = post(session, url, args.api_key, svc.sample(error_rate))
            line.append(f"{svc.name}:{svc.cpu:4.0f}%/{svc.memory:4.0f}%{'' if status == 202 else ' [' + str(status) + ']'}")
        print(f"[{int(elapsed):4d}s] " + "  ".join(line))
        time.sleep(args.interval)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nstopped")
