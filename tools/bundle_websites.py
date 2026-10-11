#!/usr/bin/env python3
"""Refresh reviewable website assets packaged in every APK. Normal builds need no network."""

import hashlib
import json
import re
import shutil
import tempfile
from collections import deque
from pathlib import Path
from urllib.parse import urldefrag, urljoin, urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/websites"
SITES = {
    "alvin": "https://ajschedule.pages.dev/",
    "sue": "https://sue.ajschedule.pages.dev/",
}
USER_AGENT = "Class-Schedule-Offline/1.0"
MAX_RESOURCE_BYTES = 2_000_000
MAX_TOTAL_BYTES = 8_000_000


def css_references(text):
    return re.findall(r"url\(\s*[\"']?([^\s\"')]+)[\"']?\s*\)", text)


def dependencies(url, mime, data):
    references = []
    if mime in ("text/html", "text/css", "application/manifest+json"):
        text = data.decode("utf-8")
        if mime == "text/html":
            for tag in re.findall(
                r"<(?:link|script|img|source)\b[^>]*>", text, re.IGNORECASE
            ):
                if not re.search(
                    r"\brel\s*=\s*[\"'](?:preconnect|dns-prefetch)[\"']",
                    tag,
                    re.IGNORECASE,
                ):
                    references.extend(
                        re.findall(r"\b(?:src|href)\s*=\s*[\"']([^\"']+)[\"']", tag)
                    )
            references.extend(css_references(text))
        elif mime == "text/css":
            references.extend(css_references(text))
        else:
            references.extend(icon["src"] for icon in json.loads(text).get("icons", []))
    return [
        urldefrag(urljoin(url, ref.replace("&amp;", "&")))[0]
        for ref in references
        if ref and not ref.startswith(("#", "data:"))
    ]


def download_site(profile, base, directory):
    manifest = {}
    pending = deque([base, urljoin(base, "sw.js")])
    if profile == "alvin":
        pending.append(urljoin(base, "schedule.json"))
    total = 0
    while pending:
        url = pending.popleft()
        if url in manifest:
            continue
        parsed = urlsplit(url)
        if (
            parsed.scheme != "https"
            or parsed.hostname
            not in (
                urlsplit(base).hostname,
                "fonts.googleapis.com",
                "fonts.gstatic.com",
            )
            or parsed.username
            or parsed.port not in (None, 443)
        ):
            raise ValueError(f"Unsupported website asset: {url}")
        if len(manifest) >= 128:
            raise ValueError("Too many website assets")
        with urlopen(
            Request(url, headers={"User-Agent": USER_AGENT}), timeout=30
        ) as response:
            if response.url != url:
                raise ValueError(f"Unexpected redirect: {url}")
            data = response.read(MAX_RESOURCE_BYTES + 1)
            mime = response.headers.get_content_type()
        total += len(data)
        if not data or len(data) > MAX_RESOURCE_BYTES or total > MAX_TOTAL_BYTES:
            raise ValueError("Website exceeds offline bundle limits")
        if parsed.hostname == urlsplit(base).hostname:
            path = parsed.path.lstrip("/") or "index.html"
        else:
            extension = ".css" if mime == "text/css" else Path(parsed.path).suffix
            path = (
                "external/" + hashlib.sha256(url.encode()).hexdigest()[:24] + extension
            )
        destination = directory / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        manifest[url] = {"path": path, "mimeType": mime}
        pending.extend(dependencies(url, mime, data))
    (directory / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"{profile}: {len(manifest)} assets, {total:,} bytes")


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=ROOT.parent) as temporary:
        staging = Path(temporary)
        for profile, base in SITES.items():
            directory = staging / profile
            directory.mkdir()
            download_site(profile, base, directory)
        # A failed download never touches the checked-in copies.
        for profile in SITES:
            destination = ROOT / profile
            if destination.exists():
                shutil.rmtree(destination)
            shutil.move(str(staging / profile), destination)


if __name__ == "__main__":
    main()
