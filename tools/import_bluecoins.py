#!/usr/bin/env python3
"""
Import a Bluecoins .fydb export into Sumry via the REST API.

    python3 import_bluecoins.py Bluecoins_*.fydb \
        --api http://localhost:48090 --email you@example.com --password ...

The importer is chronological on purpose: Sumry derives account balances by applying every
transaction in turn, so replaying history in order from a zero balance reproduces the
correct current balance. Bluecoins' own "new account" rows become the opening balances.

Bluecoins facts this relies on (verified against the export):
  * amounts are integers in millionths of the currency unit
  * transactionTypeID: 2 = opening balance, 3 = expense, 4 = income, 5 = transfer
  * deletedTransaction: 6 = live, 5 = deleted
  * reminderTransaction is non-null for scheduled//future rows, which are not real history
  * a transfer is two rows sharing transferGroupID, with amounts +x and -x
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
import urllib.error
import urllib.request
from collections import defaultdict
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

MICROS = 1_000_000

# Bluecoins records wall-clock time in the user's local zone.
LOCAL_TZ = ZoneInfo("Europe/Zurich")

TYPE_OPENING, TYPE_EXPENSE, TYPE_INCOME, TYPE_TRANSFER = 2, 3, 4, 5
GROUP_INCOME, GROUP_EXPENSE = 2, 3

# Bluecoins' internal categories that have no Sumry equivalent: they are represented by
# the opening-balance category and by real transfers instead.
SKIP_CATEGORY_IDS = {2, 3}

OPENING_CATEGORY = "Anfangssaldo"

# Bluecoins drawable name -> Ionicons name used by the app.
ICON_MAP = {
    "xxx_more_horiz_black_24dp": "ellipsis-horizontal",
    "xxx_account_balance_black_24dp": "business",
    "xxx_swap_horiz_black_24dp": "swap-horizontal",
    "xxx_format_list_bulleted_black_24dp": "list",
    "xxx_local_gas_station_black_24dp": "car",
    "xxx_local_movies_black_24dp": "film",
    "xxx_local_dining_black_24dp": "restaurant",
    "xxx_shopping_basket_black_24dp": "cart",
    "xxx_wc_black_24dp": "shirt",
    "xxx_work_black_24dp": "briefcase",
    "xxx_flight_black_24dp": "airplane",
    "xxx_baseline_grass_24": "fast-food",
    "xxx_build_black_24dp": "construct",
}
DEFAULT_ICON = "pricetag"

# Muted, low-saturation palette — one hue per parent group.
GROUP_COLORS = {
    "Food & Snacks": "#7C9A80",
    "Love / Freizeit": "#A98BA0",
    "Shopping & Gear": "#8892B0",
    "Business Expenses": "#B08968",
    "Business Revenue": "#6E9887",
    "Arbeitgeber": "#6E9887",
    "Unterhaltung": "#A98BA0",
    "Haushalt": "#8A8F98",
    "Nebenkosten": "#8A8F98",
    "Sonstige": "#8A8F98",
}
DEFAULT_COLOR = "#8A8F98"


# --------------------------------------------------------------------------- HTTP


class Api:
    def __init__(self, base: str):
        self.base = base.rstrip("/")
        self.token: str | None = None

    def _call(self, method: str, path: str, body=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        # Cloudflare's browser-integrity check rejects urllib's default agent with
        # error 1010 when the API sits behind an orange-clouded record.
        req.add_header("User-Agent", "sumry-importer/1.0")
        if self.token:
            req.add_header("Authorization", f"Bearer {self.token}")
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                raw = resp.read()
                return resp.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as exc:
            raw = exc.read()
            try:
                return exc.code, json.loads(raw)
            except Exception:
                return exc.code, {"error": raw.decode(errors="replace")}

    def login(self, email: str, password: str):
        status, body = self._call("POST", "/api/users/login", {"email": email, "password": password})
        if status != 200:
            sys.exit(f"login failed ({status}): {body}")
        self.token = body["token"]

    def get(self, path):
        return self._call("GET", path)

    def post(self, path, body):
        return self._call("POST", path, body)


def iso(dt_text: str) -> str:
    """Bluecoins local timestamp -> UTC ISO-8601 the backend's Jackson mapper accepts."""
    text = dt_text.strip()
    for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            naive = datetime.strptime(text, fmt)
            break
        except ValueError:
            continue
    else:
        raise ValueError(f"unparsable Bluecoins date: {dt_text!r}")
    return (
        naive.replace(tzinfo=LOCAL_TZ)
        .astimezone(timezone.utc)
        .strftime("%Y-%m-%dT%H:%M:%S.000+0000")
    )


# ---------------------------------------------------------------------- extraction


def read_export(path: str):
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row

    accounts = {
        r["accountsTableID"]: r["accountName"]
        for r in conn.execute(
            "SELECT accountsTableID, accountName FROM ACCOUNTSTABLE WHERE accountsTableID > 0"
        )
    }

    parents = {
        r["parentCategoryTableID"]: r
        for r in conn.execute("SELECT * FROM PARENTCATEGORYTABLE")
    }

    # Build category definitions, disambiguating labels that repeat (Sumry requires a
    # unique label per user, Bluecoins only requires uniqueness within a parent).
    raw = []
    for r in conn.execute("SELECT * FROM CHILDCATEGORYTABLE"):
        cid = r["categoryTableID"]
        if cid in SKIP_CATEGORY_IDS:
            continue
        parent = parents.get(r["parentCategoryID"])
        group = parent["categoryGroupID"] if parent else GROUP_EXPENSE
        raw.append(
            {
                "id": cid,
                "name": r["childCategoryName"],
                "parent": (parent["parentCategoryName"] if parent else None),
                "type": "INCOME" if group == GROUP_INCOME else "EXPENSE",
                "icon": ICON_MAP.get(r["childCategoryIcon"], DEFAULT_ICON),
            }
        )

    by_name = defaultdict(list)
    for c in raw:
        by_name[c["name"]].append(c)

    categories = {}
    for name, group in by_name.items():
        for c in group:
            if len(group) == 1:
                label = name
            elif len({g["parent"] for g in group}) == len(group):
                label = f"{name} ({c['parent']})"
            else:
                label = f"{name} ({'Einnahme' if c['type'] == 'INCOME' else 'Ausgabe'})"
            categories[c["id"]] = {
                "label": label,
                "parentGroup": c["parent"],
                "categoryType": c["type"],
                "icon": c["icon"],
                "color": GROUP_COLORS.get(c["parent"], DEFAULT_COLOR),
            }

    rows = list(
        conn.execute(
            """
            SELECT t.transactionsTableID AS id, t.amount, t.date, t.transactionTypeID AS type,
                   t.categoryID, t.accountID, t.notes, t.transferGroupID, i.itemName
            FROM TRANSACTIONSTABLE t
            LEFT JOIN ITEMTABLE i ON i.itemTableID = t.itemID
            WHERE t.deletedTransaction = 6
              AND t.reminderTransaction IS NULL
            ORDER BY t.date ASC, t.transactionsTableID ASC
            """
        )
    )
    conn.close()
    return accounts, categories, rows


def describe(row) -> str | None:
    for candidate in (row["itemName"], row["notes"]):
        if candidate and candidate.strip():
            return candidate.strip()[:255]
    return None


# ------------------------------------------------------------------------- import


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("fydb")
    ap.add_argument("--api", default="http://localhost:48090")
    ap.add_argument("--email", required=True)
    ap.add_argument("--password", required=True)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    accounts, categories, rows = read_export(args.fydb)
    print(f"export: {len(accounts)} accounts, {len(categories)} categories, {len(rows)} live rows")

    if args.dry_run:
        for label in sorted(c["label"] for c in categories.values()):
            print("  category:", label)
        for name in accounts.values():
            print("  account:", name)
        return

    api = Api(args.api)
    api.login(args.email, args.password)

    # --- accounts (created at zero; history builds the balance) ---
    _, existing = api.get("/api/users/accounts/")
    account_ids = {a["accountName"]: a["id"] for a in (existing or [])}
    for name in accounts.values():
        if name in account_ids:
            continue
        status, body = api.post("/api/users/accounts", {"accountName": name, "currentBalance": 0.0})
        if status != 201:
            sys.exit(f"account {name!r} failed ({status}): {body}")
        account_ids[name] = body["id"]
    bluecoins_account = {bid: account_ids[name] for bid, name in accounts.items()}
    print(f"accounts ready: {len(bluecoins_account)}")

    # --- categories ---
    _, existing_cats = api.get("/api/category/user")
    have = {c["label"] for c in (existing_cats or [])}
    wanted = list(categories.values()) + [
        {
            "label": OPENING_CATEGORY,
            "parentGroup": None,
            "categoryType": "INCOME",
            "icon": "flag",
            "color": DEFAULT_COLOR,
        }
    ]
    for cat in wanted:
        if cat["label"] in have:
            continue
        status, body = api.post("/api/category/", cat)
        if status not in (201, 409):
            sys.exit(f"category {cat['label']!r} failed ({status}): {body}")
    print(f"categories ready: {len(wanted)}")

    # --- transactions, chronologically ---
    transfers = defaultdict(list)
    for row in rows:
        if row["type"] == TYPE_TRANSFER:
            transfers[row["transferGroupID"]].append(row)

    done_transfers = set()
    counts = defaultdict(int)
    failures = []

    for row in rows:
        ttype = row["type"]
        amount = abs(row["amount"]) / MICROS
        account_id = bluecoins_account.get(row["accountID"])
        if account_id is None:
            counts["skipped_no_account"] += 1
            continue

        if ttype == TYPE_TRANSFER:
            group = row["transferGroupID"]
            if group in done_transfers:
                continue
            legs = transfers[group]
            if len(legs) != 2:
                failures.append((row["id"], f"transfer group {group} has {len(legs)} legs"))
                done_transfers.add(group)
                continue
            source = next(l for l in legs if l["amount"] < 0)
            dest = next(l for l in legs if l["amount"] > 0)
            from_id = bluecoins_account.get(source["accountID"])
            to_id = bluecoins_account.get(dest["accountID"])
            if from_id is None or to_id is None or from_id == to_id:
                failures.append((row["id"], "transfer references an unknown or identical account"))
                done_transfers.add(group)
                continue
            status, body = api.post(
                "/api/transactions/transfer",
                {
                    "amount": abs(source["amount"]) / MICROS,
                    "description": describe(source),
                    "fromAccountId": from_id,
                    "toAccountId": to_id,
                    "date": iso(source["date"]),
                },
            )
            done_transfers.add(group)
            if status == 201:
                counts["transfer"] += 1
            else:
                failures.append((row["id"], f"transfer {status}: {body}"))
            continue

        if ttype == TYPE_OPENING:
            if amount == 0:
                counts["skipped_zero_opening"] += 1
                continue
            label = OPENING_CATEGORY
        else:
            cat = categories.get(row["categoryID"])
            if cat is None:
                failures.append((row["id"], f"unknown category {row['categoryID']}"))
                continue
            label = cat["label"]

        status, body = api.post(
            "/api/transactions/",
            {
                "amount": amount,
                "description": describe(row),
                "categoryLabel": label,
                "date": iso(row["date"]),
                "userAccountId": account_id,
            },
        )
        if status == 200:
            counts["expense" if ttype == TYPE_EXPENSE else "income" if ttype == TYPE_INCOME else "opening"] += 1
        else:
            failures.append((row["id"], f"{status}: {body}"))

    print("\nimported:", dict(counts))
    if failures:
        print(f"\n{len(failures)} failures:")
        for tid, msg in failures[:20]:
            print(f"  {tid}: {msg}")

    _, final = api.get("/api/users/accounts/")
    print("\nresulting balances:")
    for a in sorted(final or [], key=lambda x: x["accountName"]):
        print(f"  {a['accountName']:<24} {a['currentBalance']:>12,.2f}")


if __name__ == "__main__":
    main()
