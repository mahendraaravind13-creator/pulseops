#!/usr/bin/env python3
"""
PulseOps Monitoring Agent
=========================
Run this on any server to start sending metrics to PulseOps.

Usage:
    python pulseops_agent.py \
        --api-key pk_your_key_here \
        --service-name my-app \
        --ingestor http://localhost:8080

Install dependency:
    pip install psutil requests
"""

import argparse
import time
import requests
import psutil
import socket
from datetime import datetime

def get_metrics(service_name):
    """Read real system metrics from the operating system."""
    cpu    = psutil.cpu_percent(interval=1)
    memory = psutil.virtual_memory().percent
    disk   = psutil.disk_usage('/').percent

    # Determine status based on thresholds
    if cpu > 85 or memory > 85:
        status = "CRITICAL"
    elif cpu > 70 or memory > 70:
        status = "WARNING"
    else:
        status = "HEALTHY"

    return {
        "serviceName":  service_name,
        "cpuUsage":     round(cpu, 1),
        "memoryUsage":  round(memory, 1),
        "diskUsage":    round(disk, 1),
        "status":       status,
        "hostname":     socket.gethostname(),
        "timestamp":    datetime.utcnow().isoformat()
    }

def send_metrics(payload, api_key, ingestor_url):
    """Send metrics to PulseOps ingestor with API key authentication."""
    try:
        response = requests.post(
            f"{ingestor_url}/api/v1/telemetry",
            json=payload,
            headers={
                "Content-Type": "application/json",
                "X-API-Key":    api_key
            },
            timeout=5
        )
        if response.status_code == 200:
            print(f"✅ [{payload['status']}] CPU: {payload['cpuUsage']}% "
                  f"| MEM: {payload['memoryUsage']}% "
                  f"| {payload['serviceName']}")
        elif response.status_code == 401:
            print(f"❌ Invalid API key. Check your key and try again.")
            exit(1)
        else:
            print(f"⚠️  Server returned {response.status_code}: {response.text}")
    except requests.exceptions.ConnectionError:
        print(f"⚠️  Cannot reach PulseOps at {ingestor_url} — retrying in 10s")
    except Exception as e:
        print(f"⚠️  Error: {e}")

def main():
    parser = argparse.ArgumentParser(description='PulseOps Monitoring Agent')
    parser.add_argument('--api-key',      required=True,  help='Your PulseOps API key')
    parser.add_argument('--service-name', required=True,  help='Name of this service')
    parser.add_argument('--ingestor',     required=True,  help='PulseOps ingestor URL')
    parser.add_argument('--interval',     type=int, default=10, help='Seconds between reports')
    args = parser.parse_args()

    print(f"🚀 PulseOps Agent started")
    print(f"   Service  : {args.service_name}")
    print(f"   Ingestor : {args.ingestor}")
    print(f"   Interval : every {args.interval} seconds")
    print(f"   API Key  : {args.api_key[:8]}...{args.api_key[-4:]}")
    print()

    # Test connection first
    try:
        r = requests.get(
            f"{args.ingestor}/api/v1/telemetry/health",
            headers={"X-API-Key": args.api_key},
            timeout=5
        )
        if r.status_code == 200:
            print(f"Connection verified: {r.text}")
        elif r.status_code == 401:
            print("Invalid API key. Get your key from pulseops.io")
            exit(1)
    except Exception:
        print(f"Could not reach {args.ingestor} — will keep retrying")

    print()

    # Main monitoring loop
    while True:
        payload = get_metrics(args.service_name)
        send_metrics(payload, args.api_key, args.ingestor)
        time.sleep(args.interval)

if __name__ == '__main__':
    main()