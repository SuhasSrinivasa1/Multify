#!/usr/bin/env python3
"""Fetch the latest AMFI-published large/mid/small-cap workbook and emit an app CSV asset.

The Android app does not make these requests at runtime. This runs only in the
TEST APK build workflow; the exact source URL, period and SHA-256 are recorded.
"""
from __future__ import annotations

import argparse
import csv
from datetime import datetime
from html.parser import HTMLParser
from io import BytesIO
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen

AMFI_PAGE = "https://www.amfiindia.com/otherdata/categorisation-of-stocks"
USER_AGENT = "Mozilla/5.0 (compatible; PhayraBuild/1.0; AMFI classification asset)"
DATE_PATTERN = re.compile(
    r"AverageMarketCapitalization(\d{1,2})([A-Za-z]{3,9})(\d{4})",
    re.IGNORECASE,
)
MONTHS = {m.lower(): i for i, m in enumerate(
    ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"), 1
)}
SYMBOL_PATTERN = re.compile(r"^[A-Z0-9&._-]{1,32}$")


class LinkParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.hrefs: list[str] = []

    def handle_starttag(self, tag: str, attrs) -> None:
        if tag.lower() != "a":
            return
        for key, value in attrs:
            if key.lower() == "href" and value:
                self.hrefs.append(value)


def fetch(url: str, accept: str) -> bytes:
    request = Request(url, headers={"User-Agent": USER_AGENT, "Accept": accept})
    with urlopen(request, timeout=45) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP_{response.status} for {url}")
        return response.read()


def dated_workbook_links(page: str) -> list[tuple[datetime, str]]:
    parser = LinkParser()
    parser.feed(page)
    found: list[tuple[datetime, str]] = []
    for href in parser.hrefs:
        absolute = urljoin(AMFI_PAGE, href)
        name = Path(urlparse(absolute).path).name
        match = DATE_PATTERN.search(name)
        if not match or not name.lower().endswith(".xlsx"):
            continue
        day, month_text, year = match.groups()
        month = MONTHS.get(month_text[:3].lower())
        if month is None:
            continue
        date = datetime(int(year), month, int(day))
        found.append((date, absolute))
    return sorted(set(found), key=lambda item: item[0], reverse=True)


def norm(value) -> str:
    return re.sub(r"[^a-z0-9]", "", str(value or "").lower())


def parse_workbook(data: bytes) -> tuple[list[tuple[str, str, int]], str]:
    try:
        import openpyxl
    except ImportError as exc:
        raise RuntimeError("openpyxl is required; install openpyxl==3.1.5") from exc

    workbook = openpyxl.load_workbook(BytesIO(data), read_only=True, data_only=True)
    try:
        found = None
        for sheet in workbook.worksheets:
            for row_number, row in enumerate(sheet.iter_rows(values_only=True), start=1):
                cells = [norm(value) for value in row]
                rank_idx = next((i for i, value in enumerate(cells)
                                 if value in {"srno", "serialno", "sno", "rank"}), None)
                symbol_idx = next((i for i, value in enumerate(cells)
                                   if value in {"nsesymbol", "symbolnse"}), None)
                category_idx = next((i for i, value in enumerate(cells)
                                      if "categorization" in value), None)
                if rank_idx is not None and symbol_idx is not None and category_idx is not None:
                    found = (sheet, row_number, rank_idx, symbol_idx, category_idx)
                    break
            if found:
                break
        if not found:
            raise RuntimeError("AMFI workbook schema changed: rank/NSE Symbol/Categorization headers not found")

        sheet, header_row, rank_idx, symbol_idx, category_idx = found
        records: list[tuple[str, str, int]] = []
        seen: set[str] = set()
        for row in sheet.iter_rows(min_row=header_row + 1, values_only=True):
            if len(row) <= max(rank_idx, symbol_idx, category_idx):
                continue
            rank_value = row[rank_idx]
            symbol_value = row[symbol_idx]
            category_value = row[category_idx]
            try:
                rank = int(float(rank_value))
            except (TypeError, ValueError):
                continue
            symbol = str(symbol_value or "").strip().upper()
            category_text = str(category_value or "").strip().upper()
            if not SYMBOL_PATTERN.fullmatch(symbol) or symbol in {"-", "NA", "N/A"}:
                continue
            if "LARGE" in category_text:
                category = "LARGE_CAP"
            elif "MID" in category_text:
                category = "MID_CAP"
            elif "SMALL" in category_text:
                category = "SMALL_CAP"
            else:
                continue
            expected = "LARGE_CAP" if rank <= 100 else "MID_CAP" if rank <= 250 else "SMALL_CAP"
            if rank <= 0 or category != expected:
                raise RuntimeError(
                    f"AMFI category/rank mismatch for {symbol}: rank={rank}, category={category}"
                )
            if symbol in seen:
                raise RuntimeError(f"Duplicate NSE symbol in AMFI workbook: {symbol}")
            seen.add(symbol)
            records.append((symbol, category, rank))

        if len(records) < 250:
            raise RuntimeError(f"AMFI workbook yielded only {len(records)} NSE symbols")
        counts = {name: sum(1 for _, category, _ in records if category == name)
                  for name in ("LARGE_CAP", "MID_CAP", "SMALL_CAP")}
        if not all(counts.values()):
            raise RuntimeError(f"AMFI category buckets incomplete: {counts}")
        return sorted(records, key=lambda item: item[2]), json.dumps(counts, sort_keys=True)
    finally:
        workbook.close()


def period_label(date: datetime) -> str:
    if date.month == 6:
        return f"Jan-Jun {date.year}"
    if date.month == 12:
        return f"Jul-Dec {date.year}"
    return f"Average market cap as of {date:%d %b %Y}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--metadata", required=True)
    args = parser.parse_args()

    page_bytes = fetch(AMFI_PAGE, "text/html,application/xhtml+xml")
    page = page_bytes.decode("utf-8", errors="replace")
    links = dated_workbook_links(page)
    if not links:
        raise RuntimeError("No dated AMFI AverageMarketCapitalization .xlsx link found on the official page")

    as_of, source_url = links[0]
    workbook_bytes = fetch(source_url, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    records, counts_json = parse_workbook(workbook_bytes)
    counts = json.loads(counts_json)
    digest = hashlib.sha256(workbook_bytes).hexdigest()
    period = period_label(as_of)

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        handle.write("#AMFI_MARKET_CAP_CLASSIFICATION_V1\n")
        handle.write(f"#period={period}\n")
        handle.write(f"#source={AMFI_PAGE}\n")
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(("symbol", "category", "rank"))
        writer.writerows(records)

    metadata = {
        "period": period,
        "source_page": AMFI_PAGE,
        "workbook_url": source_url,
        "workbook_sha256": digest,
        "nse_symbols": len(records),
        "category_counts": counts,
    }
    metadata_path = Path(args.metadata)
    metadata_path.parent.mkdir(parents=True, exist_ok=True)
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(metadata, sort_keys=True))


if __name__ == "__main__":
    main()
