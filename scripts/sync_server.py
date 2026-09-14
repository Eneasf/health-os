#!/usr/bin/env python3
"""
Lightweight Local Wi-Fi Sync Server for Health Dashboard.
Receives direct HTTP POST sync payloads from the Samsung Health Android Companion App.

Features:
- Auto-detects local LAN IP address and prints ready-to-use endpoint URL for the Android app.
- Validates SHA-256 integrity and ingests records directly into data/health_dashboard.db.
- Auto-refreshes dashboard/dashboard_data.json on receipt.
- 100% Python standard library.
"""

import os
import re
import sys
import glob
import json
import time
import base64
import hashlib
import threading
import subprocess
import socket
import uuid
import datetime
import sqlite3
import urllib.request
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INBOX_DIR = os.path.join(BASE_DIR, "data", "inbox")
SCRIPTS_DIR = os.path.join(BASE_DIR, "scripts")
DB_PATH = os.path.join(BASE_DIR, "data", "health_dashboard.db")
INTERVENTIONS_DIR = os.path.join(BASE_DIR, "data", "records", "interventions")
SCANS_DIR = os.path.join(BASE_DIR, "data", "records", "scans")
PROTOCOLS_DIR = os.path.join(BASE_DIR, "data", "records", "protocols")
EVENTS_DIR = os.path.join(BASE_DIR, "data", "records", "events")

# Import the ingestion and sync engines
sys.path.insert(0, SCRIPTS_DIR)
import ingest_sdk_payload
import export_dashboard_data
import sync_withings
import reconcile_scans
import health_intake_agent
import migrate_v13
import migrate_v14
import version

SERVER_VERSION = version.SYSTEM_VERSION_NAME

# Maximum accepted request body size, enforced from Content-Length before the
# body is ever read into memory.
MAX_PAYLOAD_BYTES = 50 * 1024 * 1024  # 50 MB

# Root directories for static assets. Resolved once so the
# path-traversal guards in do_GET don't re-resolve them on every request.
JS_STATIC_ROOT = os.path.realpath(os.path.join(BASE_DIR, "dashboard", "js"))
SCANS_STATIC_ROOT = os.path.realpath(SCANS_DIR)


def get_local_ip():
    """Finds the primary local LAN IP address of this Mac."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # Does not actually send data; connects to a non-routable address to discover local interface
        s.connect(('10.255.255.255', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip


def build_status_payload():
    deployed_commit = None
    for candidate in [
        os.path.join(BASE_DIR, ".deployed_commit"),
        os.path.join(BASE_DIR, "data", ".deployed_commit"),
        os.path.join(BASE_DIR, "config", ".deployed_commit"),
    ]:
        if os.path.exists(candidate):
            try:
                with open(candidate, "r", encoding="utf-8") as cf:
                    val = cf.read().strip()
                    if val:
                        deployed_commit = val
                        break
            except Exception:
                pass

    withings_status = sync_withings.get_sync_status()

    # Gather Dual Telemetry Freshness (ADR-029)
    sensor_freshness = None
    last_companion_sync = None
    companion_device = None
    try:
        if os.path.exists(DB_PATH):
            conn = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
            try:
                cur = conn.cursor()
                sensor_freshness = export_dashboard_data.latest_data_timestamp(cur)
                cur.execute("SELECT MAX(last_synced_at), device_id FROM sdk_sync_state")
                row = cur.fetchone()
                if row and row[0]:
                    last_companion_sync = row[0]
                    companion_device = row[1]
            finally:
                conn.close()
    except Exception:
        pass

    now_utc = datetime.datetime.now(datetime.timezone.utc)
    last_withings_sync = withings_status.get("last_success")

    # Evaluate sync contact pipeline health
    sync_contact_times = []
    if last_companion_sync:
        try:
            dt_comp = datetime.datetime.fromisoformat(last_companion_sync.replace('Z', '+00:00'))
            sync_contact_times.append(dt_comp)
        except Exception:
            pass
    if last_withings_sync:
        try:
            dt_with = datetime.datetime.fromisoformat(last_withings_sync.replace('Z', '+00:00'))
            sync_contact_times.append(dt_with)
        except Exception:
            pass

    newest_contact_dt = max(sync_contact_times) if sync_contact_times else None
    pipeline_status = "idle"
    contact_age_hours = None
    if newest_contact_dt:
        contact_age_hours = round((now_utc - newest_contact_dt).total_seconds() / 3600, 1)
        if contact_age_hours <= 12:
            pipeline_status = "active"
        elif contact_age_hours <= 24:
            pipeline_status = "delayed"
        else:
            pipeline_status = "stalled"
    else:
        pipeline_status = "unconnected"

    telemetry_freshness = {
        "sensor_current_through": sensor_freshness,
        "last_sdk_sync": last_companion_sync,
        "sdk_device": companion_device,
        "last_scale_sync": last_withings_sync,
        "last_server_contact": newest_contact_dt.isoformat() if newest_contact_dt else None,
        "contact_age_hours": contact_age_hours,
        "pipeline_status": pipeline_status,
        "sensor_freshness": {
            "data_current_through": sensor_freshness,
            "unit": "utc_timestamp"
        },
        "sync_freshness": {
            "last_companion_sync": last_companion_sync,
            "companion_device": companion_device,
            "last_withings_sync": last_withings_sync,
            "last_server_contact": newest_contact_dt.isoformat() if newest_contact_dt else None,
            "contact_age_hours": contact_age_hours,
            "pipeline_status": pipeline_status
        }
    }

    return {
        "status": "online",
        "server_version": SERVER_VERSION,
        "service": "Samsung Health Companion Local Sync Hub",
        "deployed_commit": deployed_commit,
        "timestamp": now_utc.isoformat(),
        "authority": sync_withings.get_token_authority(),
        "withings": withings_status,
        "telemetry_freshness": telemetry_freshness,
        "endpoints": {
            "dashboard": "/",
            "data": "/dashboard_data.json",
            "sync": "/api/sync",
            "log_dose": "/api/log_dose",
            "upload_scan": "/api/upload_scan",
            "scans_pending": "/api/scans/pending",
            "scans_reconcile": "/api/scans/reconcile",
            "scans_trigger_extract": "/api/scans/trigger_extract",
            "dev_sync": "/api/dev/sync_from_nas",
            "withings_sync": "/api/sync/withings",
            "protocols": "/api/protocols",
            "active_protocol": "/api/protocol/active",
            "save_protocol": "/api/protocol",
            "events": "/api/events",
            "save_event": "/api/events"
        }
    }


class HealthSyncHandler(BaseHTTPRequestHandler):
    timeout = 15  # Sockets inactive for >15s are closed to prevent thread starvation

    def handle(self):
        try:
            super().handle()
        except (socket.timeout, TimeoutError):
            pass
        except (ConnectionResetError, BrokenPipeError):
            pass

    def handle_one_request(self):
        try:
            super().handle_one_request()
        except (socket.timeout, TimeoutError):
            pass
        except (ConnectionResetError, BrokenPipeError):
            pass

    def _cors_allowed_origin(self):
        """Return the Origin header value to reflect back, or None to omit CORS headers.

        Only reflects http://localhost:<port> or http://127.0.0.1:<port>, where
        <port> is the port this server is actually bound to (derived, not
        hardcoded). Requests with no Origin header (e.g. the Android app,
        curl) simply get no CORS headers — they don't need them.
        """
        origin = self.headers.get('Origin')
        if not origin:
            return None
        port = self.server.server_port
        allowed = (f"http://localhost:{port}", f"http://127.0.0.1:{port}")
        return origin if origin in allowed else None

    def _send_cors_headers(self):
        allowed_origin = self._cors_allowed_origin()
        if allowed_origin:
            self.send_header('Access-Control-Allow-Origin', allowed_origin)
            self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
            self.send_header('Access-Control-Allow-Headers', 'Content-Type')

    def _set_json_headers(self, status_code=200):
        self.send_response(status_code)
        self.send_header('Content-Type', 'application/json')
        self._send_cors_headers()
        self.end_headers()

    def do_OPTIONS(self):
        self._set_json_headers(200)

    def do_HEAD(self):
        self.do_GET(head_only=True)

    def do_GET(self, head_only=False):
        clean_path = self.path.split('?')[0].rstrip('/')
        
        if clean_path in ('/api/status',):
            response = build_status_payload()
            body = json.dumps(response, indent=2).encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self._send_cors_headers()
            self.end_headers()
            if not head_only:
                self.wfile.write(body)
            return

        if clean_path in ('/api/scans/pending',):
            staged = reconcile_scans.get_staged_scans()
            pending_scans = [s for s in staged if s.get("status") in ("staged", "ready_for_review", "staged_pending_review", "uploaded")]
            enriched = []
            for s in pending_scans:
                sid = s.get("id")
                prop_file = os.path.join(SCANS_DIR, f"{sid}_reconciliation.json")
                prop = None
                if os.path.exists(prop_file):
                    try:
                        with open(prop_file, "r", encoding="utf-8") as pf:
                            prop = json.load(pf)
                    except Exception:
                        pass
                item = dict(s)
                item["proposal"] = prop
                item["image_url"] = f"/api/scans/image/{s.get('filename')}"
                enriched.append(item)
            body = json.dumps({
                "staged_scans": {"pending_count": len(enriched), "scans": enriched},
                "scans": enriched,
                "count": len(enriched),
                "pending_count": len(enriched)
            }, indent=2).encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self._send_cors_headers()
            self.end_headers()
            if not head_only:
                self.wfile.write(body)
            return

        if clean_path in ('/api/protocols', '/api/protocol/active'):
            protocol_files = sorted(glob.glob(os.path.join(PROTOCOLS_DIR, "*.json")))
            all_protocols = []
            for ppath in protocol_files:
                try:
                    with open(ppath, "r", encoding="utf-8") as pf:
                        all_protocols.append(json.load(pf))
                except Exception:
                    pass
            active = next((p for p in all_protocols if p.get("status") == "active"), None)
            if not active and all_protocols:
                active = all_protocols[-1]

            if clean_path == '/api/protocol/active':
                res_obj = {"active_protocol": active}
            else:
                res_obj = {"protocols": all_protocols, "active_protocol": active, "count": len(all_protocols)}

            body = json.dumps(res_obj, indent=2).encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self._send_cors_headers()
            self.end_headers()
            if not head_only:
                self.wfile.write(body)
            return

        if clean_path == '/api/events':
            event_files = sorted(glob.glob(os.path.join(EVENTS_DIR, "*.json")))
            all_events = []
            for epath in event_files:
                try:
                    with open(epath, "r", encoding="utf-8") as ef:
                        all_events.append(json.load(ef))
                except Exception:
                    pass
            all_events.sort(key=lambda x: (x.get("start_date", ""), x.get("id", "")))
            res_obj = {"events": all_events, "count": len(all_events)}
            body = json.dumps(res_obj, indent=2).encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self._send_cors_headers()
            self.end_headers()
            if not head_only:
                self.wfile.write(body)
            return

        if clean_path.startswith('/api/scans/image/'):
            img_name = clean_path.replace('/api/scans/image/', '').lstrip('/')
            img_path = os.path.join(SCANS_DIR, img_name)
            real_img_path = os.path.realpath(img_path)
            if not real_img_path.startswith(SCANS_STATIC_ROOT + os.sep):
                body = json.dumps({"error": "Forbidden"}).encode('utf-8')
                self.send_response(403)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self._send_cors_headers()
                self.end_headers()
                if not head_only:
                    self.wfile.write(body)
                return
            if os.path.exists(real_img_path) and os.path.isfile(real_img_path):
                file_size = os.path.getsize(real_img_path)
                mime = "image/jpeg"
                if real_img_path.endswith('.png'):
                    mime = "image/png"
                elif real_img_path.endswith('.webp'):
                    mime = "image/webp"
                self.send_response(200)
                self.send_header('Content-Type', mime)
                self.send_header('Content-Length', str(file_size))
                self._send_cors_headers()
                self.end_headers()
                if not head_only:
                    with open(real_img_path, 'rb') as f:
                        self.wfile.write(f.read())
                return

        if clean_path in ('', '/', '/dashboard', '/index.html'):
            index_file = os.path.join(BASE_DIR, "dashboard", "index.html")
            if os.path.exists(index_file):
                file_size = os.path.getsize(index_file)
                self.send_response(200)
                self.send_header('Content-Type', 'text/html; charset=utf-8')
                self.send_header('Content-Length', str(file_size))
                self._send_cors_headers()
                self.end_headers()
                if not head_only:
                    with open(index_file, 'rb') as f:
                        self.wfile.write(f.read())
                return

        if clean_path in ('/dashboard_data.json', '/dashboard/dashboard_data.json'):
            json_file = os.path.join(BASE_DIR, "dashboard", "dashboard_data.json")
            if os.path.exists(json_file):
                file_size = os.path.getsize(json_file)
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(file_size))
                self._send_cors_headers()
                self.end_headers()
                if not head_only:
                    with open(json_file, 'rb') as f:
                        self.wfile.write(f.read())
                return

        # Serve static JS assets in dashboard/js/
        if clean_path.startswith('/js/') or clean_path.startswith('/dashboard/js/'):
            js_rel = clean_path.replace('/dashboard/js/', 'js/').lstrip('/')
            js_file = os.path.join(BASE_DIR, "dashboard", js_rel)
            real_js_file = os.path.realpath(js_file)
            if not real_js_file.startswith(JS_STATIC_ROOT + os.sep):
                body = json.dumps({"error": "Forbidden"}).encode('utf-8')
                self.send_response(403)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self._send_cors_headers()
                self.end_headers()
                if not head_only:
                    self.wfile.write(body)
                return
            if os.path.exists(real_js_file) and os.path.isfile(real_js_file):
                file_size = os.path.getsize(real_js_file)
                self.send_response(200)
                self.send_header('Content-Type', 'application/javascript; charset=utf-8')
                self.send_header('Content-Length', str(file_size))
                self._send_cors_headers()
                self.end_headers()
                if not head_only:
                    with open(real_js_file, 'rb') as f:
                        self.wfile.write(f.read())
                return

        body = json.dumps({"error": "Not Found"}).encode('utf-8')
        self.send_response(404)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self._send_cors_headers()
        self.end_headers()
        if not head_only:
            self.wfile.write(body)

    def _read_json_body(self, max_bytes=MAX_PAYLOAD_BYTES):
        content_length_header = self.headers.get('Content-Length')
        if content_length_header is None:
            self._set_json_headers(400)
            self.wfile.write(json.dumps({"error": "Missing Content-Length header"}).encode('utf-8'))
            return None
        try:
            content_length = int(content_length_header)
        except ValueError:
            self._set_json_headers(400)
            self.wfile.write(json.dumps({"error": "Invalid Content-Length header"}).encode('utf-8'))
            return None

        if content_length > max_bytes:
            self._set_json_headers(413)
            self.wfile.write(json.dumps({"error": f"Payload exceeds maximum allowed size of {max_bytes} bytes"}).encode('utf-8'))
            return None

        content_type = (self.headers.get('Content-Type') or '').strip().lower()
        if not content_type.startswith('application/json'):
            self._set_json_headers(415)
            self.wfile.write(json.dumps({"error": "Content-Type must be application/json"}).encode('utf-8'))
            return None

        if content_length == 0:
            return {}

        body_bytes = self.rfile.read(content_length)
        try:
            return json.loads(body_bytes.decode('utf-8'))
        except Exception as e:
            self._set_json_headers(400)
            self.wfile.write(json.dumps({"error": f"Malformed JSON: {str(e)}"}).encode('utf-8'))
            return None

    def do_POST(self):
        clean_path = self.path.split('?')[0].rstrip('/')

        if clean_path in ('/api/sync',):
            content_length_header = self.headers.get('Content-Length')
            if content_length_header is None:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Missing Content-Length header"}).encode('utf-8'))
                return
            try:
                content_length = int(content_length_header)
            except ValueError:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Invalid Content-Length header"}).encode('utf-8'))
                return

            # Enforce the size cap from the declared Content-Length, before
            # ever touching the body, so an oversized payload is rejected
            # without being read into memory.
            if content_length > MAX_PAYLOAD_BYTES:
                self._set_json_headers(413)
                self.wfile.write(json.dumps({"error": f"Payload exceeds maximum allowed size of {MAX_PAYLOAD_BYTES} bytes"}).encode('utf-8'))
                return

            content_type = (self.headers.get('Content-Type') or '').strip().lower()
            if not content_type.startswith('application/json'):
                self._set_json_headers(415)
                self.wfile.write(json.dumps({"error": "Content-Type must be application/json"}).encode('utf-8'))
                return

            if content_length == 0:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Empty payload body"}).encode('utf-8'))
                return

            body_bytes = self.rfile.read(content_length)
            raw_text = body_bytes.decode('utf-8')

            try:
                envelope = json.loads(raw_text)
            except Exception as e:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": f"Malformed JSON: {str(e)}"}).encode('utf-8'))
                return

            # Validate envelope
            is_valid, error_msg = ingest_sdk_payload.validate_envelope(envelope, raw_text)
            if not is_valid:
                self._set_json_headers(422)
                self.wfile.write(json.dumps({"error": f"Validation failed: {error_msg}"}).encode('utf-8'))
                return

            # Save into inbox. Filenames use microsecond precision plus a
            # uniqueness suffix (batch_id + chunk index when present, else a
            # short uuid4) so same-second arrivals — realistic for backfill
            # chunks — never overwrite one another.
            os.makedirs(INBOX_DIR, exist_ok=True)
            ts_str = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S.%f")
            chunk_idx = envelope.get('chunk_index')
            total_chunks = envelope.get('total_chunks')
            batch_id = envelope.get('batch_id')
            if batch_id:
                suffix = str(batch_id) if chunk_idx is None else f"{batch_id}_{chunk_idx}"
                # batch_id comes from the client envelope: strip anything that
                # could alter the filesystem path before it reaches the filename.
                suffix = re.sub(r'[^A-Za-z0-9_.-]', '', suffix)[:64] or uuid.uuid4().hex[:8]
            else:
                suffix = uuid.uuid4().hex[:8]
            payload_filename = f"samsung_health_sync_{ts_str}_{suffix}.json"
            target_path = os.path.join(INBOX_DIR, payload_filename)

            with open(target_path, "w", encoding="utf-8") as f:
                f.write(raw_text)

            chunk_info = f" [Chunk {chunk_idx}/{total_chunks}]" if chunk_idx and total_chunks else ""

            print(f"\n[HTTP INCOMING] Received payload from {envelope.get('device_id')}{chunk_info} with {len(envelope.get('records', []))} records.")
            print(f"Saved to: {target_path}")

            # Process inbox immediately
            try:
                ingest_result = ingest_sdk_payload.process_inbox(target_path)
                
                # Refresh dashboard data JSON
                try:
                    export_dashboard_data.export_data()
                    print("  [DASHBOARD] dashboard_data.json refreshed.")
                except Exception as ex:
                    print(f"  [DASHBOARD NOTICE] {ex}")

                self._set_json_headers(200)
                response_data = {
                    "status": "success",
                    "server_version": SERVER_VERSION,
                    "message": "Payload ingested and dashboard updated successfully",
                    "record_count": len(envelope.get("records", [])),
                    "device_id": envelope.get("device_id"),
                    "batch_id": batch_id,
                    "chunk_index": chunk_idx,
                    "total_chunks": total_chunks,
                    "processed_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    "ingest_summary": ingest_result if isinstance(ingest_result, dict) else {}
                }
                self.wfile.write(json.dumps(response_data, indent=2).encode('utf-8'))
            except Exception as e:
                print(f"  [ERROR DURING INGESTION] {e}")
                self._set_json_headers(500)
                self.wfile.write(json.dumps({"error": f"Ingestion error: {str(e)}"}).encode('utf-8'))

        elif clean_path in ('/api/log_dose',):
            content_length_header = self.headers.get('Content-Length')
            if content_length_header is None:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Missing Content-Length header"}).encode('utf-8'))
                return
            try:
                content_length = int(content_length_header)
            except ValueError:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Invalid Content-Length header"}).encode('utf-8'))
                return

            if content_length > MAX_PAYLOAD_BYTES:
                self._set_json_headers(413)
                self.wfile.write(json.dumps({"error": f"Payload exceeds maximum allowed size of {MAX_PAYLOAD_BYTES} bytes"}).encode('utf-8'))
                return

            content_type = (self.headers.get('Content-Type') or '').strip().lower()
            if not content_type.startswith('application/json'):
                self._set_json_headers(415)
                self.wfile.write(json.dumps({"error": "Content-Type must be application/json"}).encode('utf-8'))
                return

            if content_length == 0:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Empty payload body"}).encode('utf-8'))
                return

            body_bytes = self.rfile.read(content_length)
            raw_text = body_bytes.decode('utf-8')

            try:
                payload = json.loads(raw_text)
            except Exception as e:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": f"Malformed JSON: {str(e)}"}).encode('utf-8'))
                return

            compound_id = payload.get("compound_id")
            if not compound_id:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Missing compound_id"}).encode('utf-8'))
                return

            dt_val = payload.get("datetime")
            if not dt_val:
                dt_val = datetime.datetime.now(datetime.timezone.utc).isoformat()

            timestamp = str(dt_val)
            date = timestamp[:10]
            divergence = payload.get("divergence")
            divergence_code = str(divergence) if divergence is not None else "adherent"
            precision = str(payload.get("precision")) if payload.get("precision") is not None else "exact"
            confidence = str(payload.get("confidence")) if payload.get("confidence") is not None else "confirmed"
            notes = payload.get("notes")
            if notes is not None:
                notes = str(notes)

            event_id = uuid.uuid4().hex

            # Construct canonical raw event record (Event Sourcing ADR-028)
            raw_event = {
                "id": event_id,
                "intervention_id": compound_id,
                "timestamp": timestamp,
                "date": date,
                "event_type": "dose_taken",
                "divergence_code": divergence_code,
                "precision": precision,
                "confidence": confidence,
                "notes": notes,
                "source": str(payload.get("source", "manual_attestation")),
                "created_at": datetime.datetime.now(datetime.timezone.utc).isoformat()
            }

            try:
                # 1. Persist immutable raw event record on disk first (System of Record)
                os.makedirs(INTERVENTIONS_DIR, exist_ok=True)
                filename = f"{date}_{compound_id}_{event_id[:8]}.json"
                event_path = os.path.join(INTERVENTIONS_DIR, filename)
                temp_path = event_path + ".tmp"
                with open(temp_path, "w", encoding="utf-8") as f:
                    json.dump(raw_event, f, indent=2, sort_keys=True)
                    f.write("\n")
                os.replace(temp_path, event_path)

                # 2. Materialize into disposable SQLite read projection
                conn = sqlite3.connect(DB_PATH)
                try:
                    cur = conn.cursor()
                    cur.execute("""
                        INSERT INTO intervention_events (
                            id, intervention_id, timestamp, date, event_type,
                            divergence_code, precision, confidence, notes
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, (
                        event_id,
                        compound_id,
                        timestamp,
                        date,
                        'dose_taken',
                        divergence_code,
                        precision,
                        confidence,
                        notes
                    ))
                    conn.commit()
                finally:
                    conn.close()

                # Refresh dashboard data JSON and inject into index.html
                try:
                    export_dashboard_data.export_data()
                    print(f"  [DOSE LOGGED] {compound_id} at {timestamp} -> saved to {filename}. Dashboard refreshed.")
                except Exception as ex:
                    print(f"  [DASHBOARD EXPORT NOTICE] {ex}")

                self._set_json_headers(200)
                self.wfile.write(json.dumps({"status": "ok", "event_id": event_id, "file": filename}).encode('utf-8'))
            except Exception as e:
                print(f"  [ERROR LOGGING DOSE] {e}")
                self._set_json_headers(500)
                self.wfile.write(json.dumps({"error": f"Database error: {str(e)}"}).encode('utf-8'))
        elif clean_path in ('/api/protocol',):
            content_length_header = self.headers.get('Content-Length')
            if content_length_header is None:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Missing Content-Length header"}).encode('utf-8'))
                return
            try:
                content_length = int(content_length_header)
            except ValueError:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Invalid Content-Length header"}).encode('utf-8'))
                return

            if content_length > MAX_PAYLOAD_BYTES:
                self._set_json_headers(413)
                self.wfile.write(json.dumps({"error": "Payload exceeds maximum allowed size"}).encode('utf-8'))
                return

            content_type = (self.headers.get('Content-Type') or '').strip().lower()
            if not content_type.startswith('application/json'):
                self._set_json_headers(415)
                self.wfile.write(json.dumps({"error": "Content-Type must be application/json"}).encode('utf-8'))
                return

            body_bytes = self.rfile.read(content_length)
            try:
                payload = json.loads(body_bytes.decode('utf-8'))
            except Exception as e:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": f"Malformed JSON: {str(e)}"}).encode('utf-8'))
                return

            proto_id = payload.get("id")
            if not proto_id:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Missing protocol id"}).encode('utf-8'))
                return

            clean_id = re.sub(r'[^a-zA-Z0-9_-]', '_', proto_id)
            target_json = os.path.join(PROTOCOLS_DIR, f"{clean_id}.json")
            temp_json = target_json + ".tmp"

            now_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()
            if "created_at" not in payload:
                payload["created_at"] = now_iso
            payload["updated_at"] = now_iso

            # 1. Event Sourced JSON persistence (ADR-028)
            with open(temp_json, "w", encoding="utf-8") as f:
                json.dump(payload, f, indent=2, ensure_ascii=False)
                f.write("\n")
            os.replace(temp_json, target_json)

            # 2. Materialize into disposable SQLite fact store
            try:
                migrate_v13.migrate(DB_PATH, PROTOCOLS_DIR)
            except Exception as ex:
                print(f"  [PROTOCOL MIGRATION NOTICE] {ex}")

            # 3. Refresh dashboard export
            try:
                export_dashboard_data.export_data()
            except Exception as ex:
                print(f"  [DASHBOARD EXPORT NOTICE] {ex}")

            self._set_json_headers(200)
            self.wfile.write(json.dumps({
                "status": "success",
                "protocol": payload,
                "file": f"{clean_id}.json",
                "message": f"Protocol {proto_id} saved and projected cleanly"
            }).encode('utf-8'))
            return

        elif clean_path in ('/api/events', '/api/event'):
            content_length_header = self.headers.get('Content-Length')
            if content_length_header is None:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Missing Content-Length header"}).encode('utf-8'))
                return
            try:
                content_length = int(content_length_header)
            except ValueError:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Invalid Content-Length header"}).encode('utf-8'))
                return

            if content_length > MAX_PAYLOAD_BYTES:
                self._set_json_headers(413)
                self.wfile.write(json.dumps({"error": "Payload exceeds maximum allowed size"}).encode('utf-8'))
                return

            content_type = (self.headers.get('Content-Type') or '').strip().lower()
            if not content_type.startswith('application/json'):
                self._set_json_headers(415)
                self.wfile.write(json.dumps({"error": "Content-Type must be application/json"}).encode('utf-8'))
                return

            body_bytes = self.rfile.read(content_length)
            try:
                payload = json.loads(body_bytes.decode('utf-8'))
            except Exception as e:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": f"Malformed JSON: {str(e)}"}).encode('utf-8'))
                return

            event_id = payload.get("id")
            if not event_id:
                title = payload.get("title", "event")
                clean_title = re.sub(r'[^a-zA-Z0-9]', '_', title.lower())
                start_d = payload.get("start_date", datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d"))
                event_id = f"event_{start_d[:4]}_{clean_title[:20]}"

            clean_id = re.sub(r'[^a-zA-Z0-9_-]', '_', event_id)
            payload["id"] = clean_id

            if not payload.get("category"):
                payload["category"] = "travel"
            if not payload.get("title"):
                payload["title"] = clean_id
            if not payload.get("start_date"):
                payload["start_date"] = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")

            os.makedirs(EVENTS_DIR, exist_ok=True)
            target_json = os.path.join(EVENTS_DIR, f"{clean_id}.json")
            temp_json = target_json + ".tmp"

            now_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()
            if "created_at" not in payload:
                payload["created_at"] = now_iso
            payload["updated_at"] = now_iso

            # 1. Event Sourced JSON persistence (ADR-028)
            with open(temp_json, "w", encoding="utf-8") as f:
                json.dump(payload, f, indent=2, ensure_ascii=False)
                f.write("\n")
            os.replace(temp_json, target_json)

            # 2. Materialize into disposable SQLite fact store
            try:
                migrate_v14.migrate(DB_PATH, EVENTS_DIR)
            except Exception as ex:
                print(f"  [LIFE EVENTS MIGRATION NOTICE] {ex}")

            # 3. Refresh dashboard export
            try:
                export_dashboard_data.export_data()
            except Exception as ex:
                print(f"  [DASHBOARD EXPORT NOTICE] {ex}")

            self._set_json_headers(200)
            self.wfile.write(json.dumps({
                "status": "success",
                "event": payload,
                "file": f"{clean_id}.json",
                "message": f"Life event {clean_id} saved and projected cleanly"
            }).encode('utf-8'))
            return

        elif clean_path in ('/api/sync/withings', '/api/withings/sync'):
            print("\n[HTTP INCOMING] Ad-hoc Withings sync requested.")
            try:
                authority = sync_withings.get_token_authority()
                # If running in local dev with NAS authority, attempt forwarding to NAS container over Tailscale
                if authority == "nas" and not os.path.exists("/app/docker"):
                    nas_url = os.environ.get("NAS_SYNC_URL", "http://health-dashboard.local:8088/api/sync/withings")
                    try:
                        print(f"  [DEV PROXY] Forwarding Withings sync to authoritative NAS hub: {nas_url}")
                        req = urllib.request.Request(
                            nas_url,
                            data=b"{}",
                            headers={"Content-Type": "application/json", "User-Agent": "HealthDashboard-DevProxy/1.0"}
                        )
                        with urllib.request.urlopen(req, timeout=8) as n_resp:
                            nas_data = json.loads(n_resp.read().decode("utf-8"))
                            nas_data["proxied_to_nas"] = True
                            self._set_json_headers(200)
                            self.wfile.write(json.dumps(nas_data, indent=2).encode('utf-8'))
                            return
                    except Exception as proxy_ex:
                        print(f"  [DEV PROXY NOTICE] NAS forward not reachable ({proxy_ex}). Falling back to local handler.")

                sync_res = sync_withings.run_sync()
                http_code = 200
                if sync_res.get("status") == "failed":
                    err_type = sync_res.get("error_type")
                    if err_type == "transient_service_unavailable":
                        http_code = 503
                    elif err_type == "auth_revoked":
                        http_code = 401
                    elif err_type == "nas_authoritative":
                        http_code = 200
                    else:
                        http_code = 500

                self._set_json_headers(http_code)
                self.wfile.write(json.dumps(sync_res, indent=2).encode('utf-8'))
            except Exception as ex:
                print(f"  [ERROR DURING WITHINGS SYNC] {ex}")
                self._set_json_headers(500)
                self.wfile.write(json.dumps({"error": f"Withings sync error: {str(ex)}", "status": "failed"}).encode('utf-8'))
        elif clean_path in ('/api/dev/sync_from_nas',):
            sync_script = os.path.join(SCRIPTS_DIR, "sync_from_nas.sh")
            if not os.path.exists(sync_script):
                self._set_json_headers(404)
                self.wfile.write(json.dumps({"error": "sync_from_nas.sh script not found on this host"}).encode('utf-8'))
                return

            def run_dev_sync():
                try:
                    print("\n[REMOTE TRIGGER] Initiating scripts/sync_from_nas.sh...")
                    res = subprocess.run(["/bin/bash", sync_script], cwd=BASE_DIR, capture_output=True, text=True)
                    print(f"  [REMOTE TRIGGER COMPLETE] Exit code: {res.returncode}")
                    if res.returncode != 0:
                        print(f"  [REMOTE TRIGGER ERROR] {res.stderr.strip()}")
                except Exception as e:
                    print(f"  [REMOTE TRIGGER EXCEPTION] {e}")

            t = threading.Thread(target=run_dev_sync, daemon=True, name="RemoteDevSyncThread")
            t.start()

            self._set_json_headers(200)
            self.wfile.write(json.dumps({
                "status": "triggered",
                "message": "Mac dev pull from NAS has been initiated in background",
                "started_at": datetime.datetime.now(datetime.timezone.utc).isoformat()
            }, indent=2).encode('utf-8'))

        elif clean_path in ('/api/upload_scan',):
            content_length_header = self.headers.get('Content-Length')
            if content_length_header is None:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Missing Content-Length header"}).encode('utf-8'))
                return
            try:
                content_length = int(content_length_header)
            except ValueError:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Invalid Content-Length header"}).encode('utf-8'))
                return

            if content_length > MAX_PAYLOAD_BYTES:
                self._set_json_headers(413)
                self.wfile.write(json.dumps({"error": f"Payload exceeds maximum allowed size of {MAX_PAYLOAD_BYTES} bytes"}).encode('utf-8'))
                return

            content_type = (self.headers.get('Content-Type') or '').strip().lower()
            if not content_type.startswith('application/json'):
                self._set_json_headers(415)
                self.wfile.write(json.dumps({"error": "Content-Type must be application/json"}).encode('utf-8'))
                return

            if content_length == 0:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Empty payload body"}).encode('utf-8'))
                return

            body_bytes = self.rfile.read(content_length)
            try:
                payload = json.loads(body_bytes.decode('utf-8'))
            except Exception as e:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": f"Malformed JSON: {str(e)}"}).encode('utf-8'))
                return

            image_base64 = payload.get("image_base64")
            if not image_base64:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Missing image_base64 in payload"}).encode('utf-8'))
                return

            try:
                image_data = base64.b64decode(image_base64)
            except Exception as e:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": f"Invalid base64 encoding: {str(e)}"}).encode('utf-8'))
                return

            if len(image_data) == 0:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Decoded image data is empty"}).encode('utf-8'))
                return

            img_hash = hashlib.sha256(image_data).hexdigest()
            raw_filename = payload.get("filename") or f"scan_{img_hash[:8]}.jpg"
            clean_filename = re.sub(r'[^A-Za-z0-9_.-]', '_', os.path.basename(raw_filename))
            if not clean_filename:
                clean_filename = f"scan_{img_hash[:8]}.jpg"

            timestamp = payload.get("timestamp") or datetime.datetime.now(datetime.timezone.utc).isoformat()
            date_prefix = timestamp[:10]
            saved_filename = f"{date_prefix}_{img_hash[:8]}_{clean_filename}"

            os.makedirs(SCANS_DIR, exist_ok=True)
            image_target_path = os.path.join(SCANS_DIR, saved_filename)
            temp_target_path = image_target_path + ".tmp"

            with open(temp_target_path, "wb") as f_img:
                f_img.write(image_data)
            os.replace(temp_target_path, image_target_path)

            notes = payload.get("notes")
            if notes is not None:
                notes = str(notes).strip()

            staged_entry = {
                "id": f"{date_prefix}_{img_hash[:8]}",
                "filename": saved_filename,
                "original_filename": raw_filename,
                "sha256": img_hash,
                "size_bytes": len(image_data),
                "timestamp": timestamp,
                "notes": notes,
                "status": "staged",
                "uploaded_at": datetime.datetime.now(datetime.timezone.utc).isoformat()
            }

            # Persist individual scan metadata JSON (Event Sourcing ADR-028)
            meta_filename = f"{date_prefix}_{img_hash[:8]}_meta.json"
            meta_path = os.path.join(SCANS_DIR, meta_filename)
            with open(meta_path + ".tmp", "w", encoding="utf-8") as f_meta:
                json.dump(staged_entry, f_meta, indent=2, sort_keys=True)
                f_meta.write("\n")
            os.replace(meta_path + ".tmp", meta_path)

            # Update consolidated staged_scans.json
            staged_index_file = os.path.join(SCANS_DIR, "staged_scans.json")
            existing_staged = []
            if os.path.exists(staged_index_file):
                try:
                    with open(staged_index_file, "r", encoding="utf-8") as sf:
                        existing_staged = json.load(sf)
                        if not isinstance(existing_staged, list):
                            existing_staged = []
                except Exception:
                    existing_staged = []

            # Deduplicate by id if already staged
            existing_staged = [item for item in existing_staged if item.get("id") != staged_entry["id"]]
            existing_staged.append(staged_entry)

            with open(staged_index_file + ".tmp", "w", encoding="utf-8") as sf:
                json.dump(existing_staged, sf, indent=2, sort_keys=True)
                sf.write("\n")
            os.replace(staged_index_file + ".tmp", staged_index_file)

            # Spawn asynchronous autonomous health intake (Milestone 20 Door 1)
            def _async_intake(sid):
                try:
                    health_intake_agent.process_scan(sid, db_path=DB_PATH)
                except Exception as err:
                    print(f"⚠️ Asynchronous health intake error for {sid}: {err}")

            threading.Thread(target=_async_intake, args=(staged_entry["id"],), daemon=True).start()

            print(f"\n[HTTP INCOMING] Staged visual scan: {saved_filename} ({len(image_data)} bytes) [SHA: {img_hash[:8]}]")
            if notes:
                print(f"  Note: {notes}")

            self._set_json_headers(200)
            self.wfile.write(json.dumps({
                "status": "success",
                "message": "Scan uploaded and queued for autonomous intake",
                "staged_id": staged_entry["id"],
                "filename": saved_filename,
                "image_path": f"data/records/scans/{saved_filename}",
                "sha256": img_hash,
                "bytes": len(image_data),
                "notes": notes,
                "staged_at": staged_entry["uploaded_at"]
            }, indent=2).encode('utf-8'))

        elif clean_path in ('/api/scans/trigger_extract',):
            content_length_header = self.headers.get('Content-Length')
            body_json = {}
            if content_length_header:
                try:
                    cl = int(content_length_header)
                    if cl > 0:
                        body_bytes = self.rfile.read(cl)
                        body_json = json.loads(body_bytes.decode('utf-8'))
                except Exception:
                    pass

            scan_id = body_json.get("scan_id")
            if scan_id:
                res = health_intake_agent.process_scan(scan_id, db_path=DB_PATH)
                if not res or res.get("status") == "error":
                    self._set_json_headers(404)
                    self.wfile.write(json.dumps({"error": f"Scan '{scan_id}' not found or extraction failed"}).encode('utf-8'))
                    return
                self._set_json_headers(200)
                self.wfile.write(json.dumps({"status": "success", "scan_id": scan_id, "result": res}, indent=2).encode('utf-8'))
            else:
                staged = reconcile_scans.get_staged_scans()
                processed = []
                for s in staged:
                    if s.get("status") in ("staged", "uploaded"):
                        r = health_intake_agent.process_scan(s["id"], db_path=DB_PATH)
                        if r and r.get("status") == "success":
                            processed.append(s["id"])
                self._set_json_headers(200)
                self.wfile.write(json.dumps({"status": "success", "processed_scans": processed, "count": len(processed)}, indent=2).encode('utf-8'))

        elif clean_path in ('/api/scans/reconcile',):
            content_length_header = self.headers.get('Content-Length')
            if content_length_header is None:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Missing Content-Length header"}).encode('utf-8'))
                return
            try:
                cl = int(content_length_header)
            except ValueError:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Invalid Content-Length header"}).encode('utf-8'))
                return

            body_bytes = self.rfile.read(cl)
            try:
                payload = json.loads(body_bytes.decode('utf-8'))
            except Exception as e:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": f"Malformed JSON: {str(e)}"}).encode('utf-8'))
                return

            scan_id = payload.get("scan_id")
            if not scan_id:
                self._set_json_headers(400)
                self.wfile.write(json.dumps({"error": "Missing 'scan_id' in payload"}).encode('utf-8'))
                return

            # Check if proposal exists or payload supplies full reconciliation
            reconciliation = payload.get("reconciliation")
            if not reconciliation:
                rec_path = os.path.join(SCANS_DIR, f"{scan_id}_reconciliation.json")
                if os.path.exists(rec_path):
                    try:
                        with open(rec_path, "r", encoding="utf-8") as rf:
                            reconciliation = json.load(rf)
                    except Exception as e:
                        self._set_json_headers(500)
                        self.wfile.write(json.dumps({"error": f"Error reading proposal: {e}"}).encode('utf-8'))
                        return
                else:
                    # Run extraction on the fly
                    reconciliation = reconcile_scans.process_scan(scan_id, auto_commit=False, db_path=DB_PATH)

            if not reconciliation:
                self._set_json_headers(404)
                self.wfile.write(json.dumps({"error": f"Could not generate reconciliation for scan '{scan_id}'"}).encode('utf-8'))
                return

            # Apply any overrides supplied in payload
            overrides = payload.get("overrides")
            if overrides and isinstance(overrides, dict):
                for k, v in overrides.items():
                    if k == "extracted_payload":
                        reconciliation["extracted_payload"].update(v)
                    else:
                        reconciliation[k] = v

            try:
                reconcile_scans.commit_reconciliation(reconciliation, db_path=DB_PATH)
                try:
                    export_dashboard_data.export_data()
                except Exception as ex:
                    print(f"  [DASHBOARD REFRESH NOTICE] {ex}")

                self._set_json_headers(200)
                self.wfile.write(json.dumps({
                    "status": "success",
                    "message": "Scan reconciled and committed to event store and fact database",
                    "scan_id": scan_id,
                    "domain": reconciliation.get("domain")
                }, indent=2).encode('utf-8'))
            except Exception as e:
                self._set_json_headers(500)
                self.wfile.write(json.dumps({"error": f"Commit failed: {str(e)}"}).encode('utf-8'))
        else:
            self._set_json_headers(404)
            self.wfile.write(json.dumps({"error": "Endpoint not found"}).encode('utf-8'))

    def log_message(self, format, *args):
        # Clean custom log format
        sys.stderr.write(f"[{datetime.datetime.now().strftime('%H:%M:%S')}] {self.address_string()} - {format % args}\n")


DashboardSyncHandler = HealthSyncHandler


class ThreadingHealthServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def start_withings_scheduler(interval_hours=6):
    """Background daemon thread to periodically poll Withings API if tokens are configured."""
    def scheduler_loop():
        time.sleep(60)
        while True:
            try:
                if os.path.exists(sync_withings.TOKENS_FILE):
                    print(f"[{datetime.datetime.now().strftime('%H:%M:%S')}] [WITHINGS SCHEDULER] Running scheduled Withings sync...")
                    res = sync_withings.run_sync()
                    print(f"[{datetime.datetime.now().strftime('%H:%M:%S')}] [WITHINGS SCHEDULER] Result: {res.get('status')} (records: {res.get('records_synced', 0)})")
            except Exception as ex:
                print(f"[{datetime.datetime.now().strftime('%H:%M:%S')}] [WITHINGS SCHEDULER ERROR] {ex}")
            time.sleep(interval_hours * 3600)

    t = threading.Thread(target=scheduler_loop, daemon=True, name="WithingsSchedulerThread")
    t.start()


def start_server(port=8765):
    local_ip = get_local_ip()
    server_address = ('0.0.0.0', port)
    httpd = ThreadingHealthServer(server_address, HealthSyncHandler)

    start_withings_scheduler(interval_hours=6)

    print("=" * 70)
    print("🚀 Health Dashboard Local Wi-Fi Sync Hub")
    print("=" * 70)
    print(f"📡 Server Listening on all interfaces at port {port}")
    print(f"\n👉 Set your Android Companion App Endpoint to:")
    print(f"   http://{local_ip}:{port}/api/sync")
    print(f"\n   (Or http://127.0.0.1:{port}/api/sync if testing locally)")
    print("=" * 70)
    print("Waiting for companion app sync pushes... (Press Ctrl+C to stop)\n")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping sync server...")
        httpd.server_close()


if __name__ == '__main__':
    port = int(os.environ.get("PORT", sys.argv[1] if len(sys.argv) > 1 else 8765))
    start_server(port)
