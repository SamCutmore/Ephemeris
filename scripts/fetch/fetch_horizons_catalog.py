#!/usr/bin/env python3
"""Fetch J2000.0 ICRF osculating elements from JPL Horizons and emit SQL + JSON seeds."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

try:
    import yaml
except ImportError:
    print("PyYAML required: pip install pyyaml", file=sys.stderr)
    sys.exit(1)

ROOT = Path(__file__).resolve().parents[2]
CATALOG_DIR = ROOT / "backend" / "catalog"
MANIFEST_PATH = CATALOG_DIR / "bodies.yaml"
JSON_OUT = CATALOG_DIR / "j2000_icrf.json"
SQL_SEED = ROOT / "backend" / "src" / "main" / "resources" / "catalog" / "catalog_seed.sql"

HORIZONS_URL = "https://ssd.jpl.nasa.gov/api/horizons.api"
PCK_URL = "https://naif.jpl.nasa.gov/pub/naif/generic_kernels/pck/pck00011.tpc"
PCK_PATH = CATALOG_DIR / "pck00011.tpc"
J2000_JD = 2451545.0
DEG2RAD = math.pi / 180.0
KM2M = 1000.0
REQUEST_PAUSE_S = 0.35
MAX_RETRIES = 4
ROTATION_COLUMNS = (
    "pole_ra_0",
    "pole_ra_1",
    "pole_ra_2",
    "pole_dec_0",
    "pole_dec_1",
    "pole_dec_2",
    "prime_meridian_0",
    "prime_meridian_1",
    "prime_meridian_2",
)

ELEMENT_RE = {
    "EC": re.compile(r"\bEC\s*=\s*([+\-]?(?:\d+\.?\d*|\.\d+)(?:[Ee][+\-]?\d+)?)"),
    "IN": re.compile(r"\bIN\s*=\s*([+\-]?(?:\d+\.?\d*|\.\d+)(?:[Ee][+\-]?\d+)?)"),
    "OM": re.compile(r"\bOM\s*=\s*([+\-]?(?:\d+\.?\d*|\.\d+)(?:[Ee][+\-]?\d+)?)"),
    "W": re.compile(r"\bW\s*=\s*([+\-]?(?:\d+\.?\d*|\.\d+)(?:[Ee][+\-]?\d+)?)"),
    "MA": re.compile(r"\bMA\s*=\s*([+\-]?(?:\d+\.?\d*|\.\d+)(?:[Ee][+\-]?\d+)?)"),
    "A": re.compile(r"\bA\s*=\s*([+\-]?(?:\d+\.?\d*|\.\d+)(?:[Ee][+\-]?\d+)?)"),
    "QR": re.compile(r"\bQR\s*=\s*([+\-]?(?:\d+\.?\d*|\.\d+)(?:[Ee][+\-]?\d+)?)"),
}


def sql_str(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def sql_num(value: float | None) -> str:
    if value is None:
        return "NULL"
    if not math.isfinite(value):
        raise ValueError(f"non-finite number: {value}")
    return repr(float(value))


def sql_int(value: int | None) -> str:
    if value is None:
        return "NULL"
    return str(int(value))


def horizons_get(params: dict[str, str]) -> str:
    query = urllib.parse.urlencode(params)
    url = f"{HORIZONS_URL}?{query}"
    last_err: Exception | None = None
    for attempt in range(MAX_RETRIES):
        try:
            with urllib.request.urlopen(url, timeout=60) as resp:
                raw = resp.read().decode("utf-8", errors="replace")
            # JSON wrapper or plain text
            if raw.lstrip().startswith("{"):
                payload = json.loads(raw)
                if "result" in payload:
                    return payload["result"]
                if payload.get("error"):
                    raise RuntimeError(str(payload["error"]))
                return raw
            return raw
        except (urllib.error.URLError, TimeoutError, RuntimeError, json.JSONDecodeError) as e:
            last_err = e
            time.sleep(REQUEST_PAUSE_S * (attempt + 1))
    raise RuntimeError(f"Horizons request failed: {params.get('COMMAND')}: {last_err}")


def parse_elements(text: str) -> dict[str, float]:
    # Prefer the $$SOE block when present
    soe = text
    if "$$SOE" in text and "$$EOE" in text:
        soe = text.split("$$SOE", 1)[1].split("$$EOE", 1)[0]

    vals: dict[str, float] = {}
    for key, rx in ELEMENT_RE.items():
        m = rx.search(soe)
        if m:
            vals[key] = float(m.group(1))
    if "EC" not in vals or "IN" not in vals or "OM" not in vals or "W" not in vals:
        raise ValueError("missing classical elements in Horizons response")
    if "MA" not in vals:
        raise ValueError("missing MA in Horizons response")
    if "A" not in vals:
        if "QR" in vals and vals["EC"] != 1.0:
            # a = q / (1 - e)
            vals["A"] = vals["QR"] / (1.0 - vals["EC"])
        else:
            raise ValueError("missing A/QR in Horizons response")
    return vals


def horizons_command(body: dict) -> str:
    """Disambiguate numbered small bodies from major-body IDs."""
    raw = str(body["horizons"]).strip()
    body_type = body.get("type")
    if body_type == "asteroid" or (
        body_type == "dwarf_planet" and raw.isdigit() and int(raw) < 1000000 and raw not in {"999"}
    ):
        if not raw.endswith(";"):
            return f"{raw};"
    return raw


def fetch_elements(command: str, center: str, jd: float) -> dict[str, float]:
    params = {
        "format": "json",
        "COMMAND": f"'{command}'",
        "OBJ_DATA": "NO",
        "MAKE_EPHEM": "YES",
        "EPHEM_TYPE": "ELEMENTS",
        "CENTER": f"'{center}'",
        "REF_SYSTEM": "ICRF",
        "REF_PLANE": "FRAME",
        "OUT_UNITS": "KM-S",
        "TIME_TYPE": "TDB",
        "TLIST": str(jd),
        "TLIST_TYPE": "JD",
    }
    text = horizons_get(params)
    if "$$SOE" not in text:
        raise ValueError(text[:500].replace("\n", " "))
    return parse_elements(text)


def expand_manifest(manifest: dict) -> list[dict]:
    bodies = list(manifest["bodies"])
    by_key = {b["key"]: b for b in bodies}

    ast = manifest.get("asteroids") or {}
    numbers = ast.get("numbers") or []
    id_start = int(ast.get("id_start", 1000))
    parent = ast.get("parent", "sun")
    center = ast.get("center", "@10")
    default_mu = float(ast.get("default_mu", 1e8))
    default_radius = float(ast.get("default_radius", 5e4))

    for i, num in enumerate(numbers):
        key = f"asteroid_{num}"
        if key in by_key:
            continue
        entry = {
            "key": key,
            "id": id_start + i,
            "name": f"Asteroid {num}",
            "type": "asteroid",
            "horizons": str(num),
            "parent": parent,
            "center": center,
            "mu": default_mu,
            "radius": default_radius,
            "horizons_name": True,
        }
        bodies.append(entry)
        by_key[key] = entry
    return bodies


def resolve_parent_id(body: dict, by_key: dict[str, dict]) -> int | None:
    parent_key = body.get("parent")
    if parent_key is None:
        return None
    parent = by_key.get(parent_key)
    if parent is None:
        raise KeyError(f"unknown parent {parent_key} for {body['key']}")
    return int(parent["id"])


def fetch_body_orbit(body: dict, ref_jd: float) -> tuple[dict[str, float], float, float]:
    """Returns (elements_km_deg, epoch_jd_used, epoch_sim_seconds)."""
    if body.get("parent") is None:
        return {}, ref_jd, 0.0

    center = body.get("center") or "@10"
    command = horizons_command(body)
    fallback = body.get("fallback_jd")
    # Spacecraft often have no J2000 coverage. Try fallback epoch.
    jd_candidates: list[float] = []
    if body.get("type") == "spacecraft" and fallback is not None:
        jd_candidates.append(float(fallback))
    jd_candidates.append(ref_jd)
    if fallback is not None and float(fallback) not in jd_candidates:
        jd_candidates.append(float(fallback))

    last_err: Exception | None = None
    for jd in jd_candidates:
        try:
            els = fetch_elements(command, center, jd)
            epoch_s = (jd - ref_jd) * 86400.0
            return els, jd, epoch_s
        except Exception as e:
            last_err = e
            continue
    raise RuntimeError(f"{body['key']}: {last_err}") from last_err


def maybe_update_name(body: dict, text_hint: str | None = None) -> None:
    # Names for numbered asteroids stay as "Asteroid N".
    return


def elements_to_si(els: dict[str, float], epoch_s: float) -> dict[str, float]:
    a_km = els["A"]
    return {
        "semi_major_axis": a_km * KM2M,  # signed; negative for hyperbola
        "eccentricity": els["EC"],
        "inclination": els["IN"] * DEG2RAD,
        "long_asc_node": els["OM"] * DEG2RAD,
        "arg_periapsis": els["W"] * DEG2RAD,
        "mean_anomaly_at_epoch": els["MA"] * DEG2RAD,
        "epoch": epoch_s,
    }


def parse_pck(text: str) -> dict[int, dict[str, list[float]]]:
    """Assignments inside \\begindata blocks. Later blocks override earlier blocks."""
    number = r"[+\-]?(?:\d+\.?\d*|\.\d+)(?:[DdEe][+\-]?\d+)?"
    assign = re.compile(
        rf"BODY(\d+)_([A-Z0-9_]+)\s*=\s*\((.*?)\)",
        re.DOTALL,
    )
    models: dict[int, dict[str, list[float]]] = {}
    for block in re.findall(r"\\begindata(.*?)\\begintext", text, flags=re.DOTALL | re.IGNORECASE):
        for match in assign.finditer(block):
            naif_id = int(match.group(1))
            coeffs = [
                float(token.replace("D", "E").replace("d", "e"))
                for token in re.findall(number, match.group(3))
            ]
            models.setdefault(naif_id, {})[match.group(2)] = coeffs
    return models


def _pad3(coeffs: list[float]) -> list[float]:
    vals = list(coeffs[:3])
    while len(vals) < 3:
        vals.append(0.0)
    return vals


def rotation_for_horizons(
    horizons: str, models: dict[int, dict[str, list[float]]]
) -> dict | None:
    """Match a Horizons command to a PCK body. Numbered asteroids use the 2000000+n id."""
    raw = str(horizons).strip().rstrip(";")
    if not re.fullmatch(r"-?\d+", raw):
        return None
    number = int(raw)
    candidates = [number]
    if number > 0:
        candidates.append(2_000_000 + number)
    for naif_id in candidates:
        model = models.get(naif_id)
        if not model:
            continue
        if not all(key in model for key in ("POLE_RA", "POLE_DEC", "PM")):
            continue
        return {
            "source": "IAU WGCCRE 2015 via NAIF pck00011.tpc",
            "naif_id": naif_id,
            "pole_ra": _pad3(model["POLE_RA"]),
            "pole_dec": _pad3(model["POLE_DEC"]),
            "prime_meridian": _pad3(model["PM"]),
        }
    return None


def rotation_sql(rotation: dict | None) -> str:
    if rotation is None:
        return ", ".join(["NULL"] * 9)
    pole_ra = [v * DEG2RAD for v in rotation["pole_ra"]]
    pole_dec = [v * DEG2RAD for v in rotation["pole_dec"]]
    prime = [v * DEG2RAD for v in rotation["prime_meridian"]]
    return ", ".join(sql_num(v) for v in (*pole_ra, *pole_dec, *prime))


def emit_sql(reference: dict, rows: list[dict]) -> str:
    lines: list[str] = []
    lines.append("-- Generated by scripts/fetch/fetch_horizons_catalog.py.")
    lines.append("")
    lines.append(
        "INSERT INTO ephemeris_reference (id, epoch_jd, frame, plane, time_scale, source)"
    )
    lines.append(
        f"SELECT 1, {sql_num(reference['epoch_jd'])}, {sql_str(reference['frame'])}, "
        f"{sql_str(reference['plane'])}, {sql_str(reference['time_scale'])}, "
        f"{sql_str(reference['source'])}"
    )
    lines.append("WHERE NOT EXISTS (SELECT 1 FROM ephemeris_reference WHERE id = 1);")
    lines.append("")

    # Parents before children.
    for row in rows:
        b = row["body"]
        parent_id = row["parent_id"]
        lines.append("INSERT INTO celestial_bodies (")
        lines.append(
            "    id, name, type, parent_id, mu, radius,"
        )
        lines.append("    " + ", ".join(ROTATION_COLUMNS))
        lines.append(")")
        lines.append(
            f"SELECT {int(b['id'])}, {sql_str(b['name'])}, {sql_str(b['type'])}, "
            f"{sql_int(parent_id)}, "
            f"{sql_num(float(b['mu']))}, {sql_num(float(b.get('radius') or 1.0))}, "
            f"{rotation_sql(row.get('rotation'))}"
        )
        lines.append(f"WHERE NOT EXISTS (SELECT 1 FROM celestial_bodies WHERE id = {int(b['id'])});")
        lines.append("")

        orbit = row.get("orbit")
        if orbit is not None:
            lines.append("INSERT INTO orbits (")
            lines.append(
                "    celestial_body_id, semi_major_axis, eccentricity, inclination,"
            )
            lines.append(
                "    long_asc_node, arg_periapsis, mean_anomaly_at_epoch, epoch"
            )
            lines.append(")")
            lines.append(
                f"SELECT {int(b['id'])}, {sql_num(orbit['semi_major_axis'])}, "
                f"{sql_num(orbit['eccentricity'])}, {sql_num(orbit['inclination'])}, "
                f"{sql_num(orbit['long_asc_node'])}, {sql_num(orbit['arg_periapsis'])}, "
                f"{sql_num(orbit['mean_anomaly_at_epoch'])}, {sql_num(orbit['epoch'])}"
            )
            lines.append(
                f"WHERE NOT EXISTS (SELECT 1 FROM orbits WHERE celestial_body_id = {int(b['id'])});"
            )
            lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=MANIFEST_PATH)
    parser.add_argument("--json-out", type=Path, default=JSON_OUT)
    parser.add_argument("--sql-out", type=Path, default=SQL_SEED)
    parser.add_argument("--limit", type=int, default=0, help="Fetch only first N bodies (debug)")
    parser.add_argument("--skip-asteroids", action="store_true")
    parser.add_argument(
        "--apply-rotations",
        action="store_true",
        help="Merge NAIF pck00011 orientation into the existing JSON catalog and regenerate SQL",
    )
    args = parser.parse_args()

    if args.apply_rotations:
        return apply_rotations(args.json_out, args.sql_out, PCK_PATH)

    with args.manifest.open() as f:
        manifest = yaml.safe_load(f)

    reference = dict(manifest["reference"])
    ref_jd = float(reference.get("epoch_jd", J2000_JD))

    if args.skip_asteroids:
        manifest = dict(manifest)
        manifest["asteroids"] = {"numbers": [], "id_start": 1000}

    bodies = expand_manifest(manifest)
    if args.limit > 0:
        bodies = bodies[: args.limit]

    by_key = {b["key"]: b for b in bodies}
    rows: list[dict] = []
    errors: list[str] = []
    pck_models = (
        parse_pck(PCK_PATH.read_text(encoding="utf-8", errors="replace"))
        if PCK_PATH.exists()
        else {}
    )

    for idx, body in enumerate(bodies):
        print(
            f"[{idx + 1}/{len(bodies)}] {body['name']} ({body.get('horizons', 'synthetic')})…",
            flush=True,
        )
        parent_id = resolve_parent_id(body, by_key)
        record: dict = {
            "body": body,
            "parent_id": parent_id,
            "orbit": None,
            "horizons_jd": None,
            "rotation": body.get("rotation")
            or rotation_for_horizons(str(body.get("horizons") or ""), pck_models),
        }
        if body.get("orbit"):
            record["orbit"] = body["orbit"]
            record["horizons_jd"] = ref_jd
        elif parent_id is not None:
            try:
                els, jd_used, epoch_s = fetch_body_orbit(body, ref_jd)
                record["orbit"] = elements_to_si(els, epoch_s)
                record["horizons_jd"] = jd_used
                # Improve asteroid names from an OBJ_DATA call.
            except Exception as e:
                msg = f"FAIL {body['key']}: {e}"
                print("  " + msg, flush=True)
                errors.append(msg)
                time.sleep(REQUEST_PAUSE_S)
                continue
        rows.append(record)
        time.sleep(REQUEST_PAUSE_S)

    payload = {
        "reference": reference,
        "bodies": [
            {
                "id": r["body"]["id"],
                "key": r["body"]["key"],
                "name": r["body"]["name"],
                "type": r["body"]["type"],
                "horizons": r["body"].get("horizons"),
                "parent_id": r["parent_id"],
                "mu": r["body"]["mu"],
                "radius": r["body"].get("radius"),
                "horizons_jd": r["horizons_jd"],
                "orbit": r["orbit"],
                "rotation": r.get("rotation"),
            }
            for r in rows
        ],
        "errors": errors,
    }

    args.json_out.parent.mkdir(parents=True, exist_ok=True)
    args.json_out.write_text(json.dumps(payload, indent=2) + "\n")
    seed_sql = emit_sql(reference, rows)
    args.sql_out.parent.mkdir(parents=True, exist_ok=True)
    args.sql_out.write_text(seed_sql)

    print(f"Wrote {args.json_out} ({len(rows)} bodies, {len(errors)} errors)")
    print(f"Wrote {args.sql_out}")
    if errors:
        print("Errors:", file=sys.stderr)
        for e in errors:
            print(" ", e, file=sys.stderr)
        return 1 if len(rows) < 10 else 0
    return 0


def apply_rotations(json_path: Path, sql_path: Path, pck_path: Path) -> int:
    if not pck_path.exists():
        print(f"Downloading {PCK_URL}", flush=True)
        with urllib.request.urlopen(PCK_URL, timeout=60) as resp:
            pck_path.write_bytes(resp.read())
    models = parse_pck(pck_path.read_text(encoding="utf-8", errors="replace"))
    payload = json.loads(json_path.read_text())
    matched = 0
    missing_named: list[str] = []
    rows: list[dict] = []
    for body in payload["bodies"]:
        existing = body.get("rotation") or {}
        if existing and "pck00011" not in str(existing.get("source") or ""):
            rotation = existing
        else:
            rotation = rotation_for_horizons(str(body.get("horizons") or ""), models)
        body["rotation"] = rotation
        if rotation is not None:
            matched += 1
        elif body.get("type") in {"star", "planet", "dwarf_planet", "moon"}:
            missing_named.append(f"{body['name']} ({body.get('horizons')})")
        rows.append(
            {
                "body": body,
                "parent_id": body.get("parent_id"),
                "orbit": body.get("orbit"),
                "rotation": rotation,
            }
        )
    payload.setdefault("reference", {})["rotation_source"] = (
        "IAU WGCCRE 2015 via NAIF pck00011.tpc"
    )
    json_path.write_text(json.dumps(payload, indent=2) + "\n")
    seed_sql = emit_sql(payload["reference"], rows)
    sql_path.write_text(seed_sql)
    print(f"Rotation models: {matched}/{len(rows)} bodies")
    if missing_named:
        print("No PCK orientation for:")
        for name in missing_named:
            print(f"  {name}")
    print(f"Wrote {json_path}")
    print(f"Wrote {sql_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
