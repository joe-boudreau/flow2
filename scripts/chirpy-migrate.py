#!/usr/bin/env python3
"""Export public Chirpy comments without account data; dry-run or import into MongoDB.

Export uses only Python's standard library. Import/dry-run also need pymongo.
Credentials are read from COMMENT_MONGO_URI (or DB_CONNECTION_STRING), never CLI args.
"""
import argparse
import datetime as dt
import html.parser
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.parse
import urllib.request

ALLOWED = {"chirpyId", "sourceUrl", "name", "body", "createdAt", "updatedAt", "parentId", "deleted"}


class PageParser(html.parser.HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.links, self.data, self.in_data = [], [], False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "a" and attrs.get("href"):
            self.links.append(attrs["href"])
        if tag == "script" and attrs.get("id") == "__NEXT_DATA__":
            self.in_data = True

    def handle_endtag(self, tag):
        if tag == "script":
            self.in_data = False

    def handle_data(self, data):
        if self.in_data:
            self.data.append(data)


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "flowtwo-comment-migration/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        # Responses are parsed in memory; never save raw responses containing user records.
        return response.read().decode("utf-8")


def plain_text(node):
    """Keep paragraph boundaries, quotes, code, list structure, and link destinations."""
    if node is None:
        return ""
    kind = node.get("type")
    if kind == "text":
        text = node.get("text", "")
        for mark in node.get("marks", []):
            if mark.get("type") == "link":
                href = mark.get("attrs", {}).get("href", "")
                if href and href != text:
                    text += f" ({href})"
        return text
    if kind == "hardBreak":
        return "\n"
    parts = [plain_text(child) for child in node.get("content", [])]
    if kind in ("doc", "bulletList", "orderedList"):
        if kind == "bulletList":
            return "\n".join("- " + p for p in parts)
        if kind == "orderedList":
            start = node.get("attrs", {}).get("start", 1)
            return "\n".join(f"{start + i}. {p}" for i, p in enumerate(parts))
        return "\n\n".join(parts)
    if kind == "blockquote":
        return "\n".join("> " + line for line in "\n\n".join(parts).splitlines())
    if kind in ("paragraph", "heading", "codeBlock"):
        return "".join(parts)
    if kind == "listItem":
        return "\n".join(parts)
    if kind == "horizontalRule":
        return "---"
    if kind == "image":
        attrs = node.get("attrs", {})
        return f"[Image: {attrs.get('alt', '')}] {attrs.get('src', '')}".strip()
    raise ValueError(f"Unsupported content node: {kind!r}")


def extract(html, url):
    parser = PageParser()
    parser.feed(html)
    if not parser.data:
        raise ValueError("Widget has no structured data")
    data = json.loads("".join(parser.data))
    queries = data["props"]["pageProps"]["trpcState"]["json"]["queries"]
    result, found_forest = {}, False
    for query in queries:
        key = query.get("queryKey", [])
        if len(key) != 2 or key[1].get("input", {}).get("url") != url:
            continue
        if key[0] not in (["comment", "forest"], ["comment", "pinnedComments"]):
            continue
        if query["state"].get("status") != "success":
            raise ValueError("Comment query was not successful")
        if key[0] == ["comment", "forest"]:
            found_forest = True
        rows = query["state"]["data"]
        if not isinstance(rows, list):
            raise ValueError("Unexpected comment list / pagination format")

        def walk(items, parent=None):
            for item in items:
                deleted = item.get("deletedAt") is not None
                record = {
                    "chirpyId": item["id"], "sourceUrl": url,
                    "name": "" if deleted else (item.get("user") or {}).get("name") or "Anonymous",
                    "body": "" if deleted else plain_text(item.get("content")),
                    "createdAt": item["createdAt"], "updatedAt": item.get("updatedAt", item["createdAt"]),
                    "parentId": item.get("parentId") or parent, "deleted": deleted,
                }
                if item["id"] in result and result[item["id"]] != record:
                    raise ValueError("Conflicting duplicate comment")
                result[item["id"]] = record
                walk(item.get("replies", []), item["id"])
        walk(rows)
    if not found_forest:
        raise ValueError("No successful comment forest for the exact requested URL")
    return list(result.values())


def export(args):
    site = args.site.rstrip("/")
    if args.urls:
        urls = [line.strip() for line in Path(args.urls).read_text().splitlines() if line.strip()]
    else:
        parser = PageParser()
        parser.feed(fetch(site + "/archive"))
        urls = sorted({urllib.parse.urljoin(site, link).split("#")[0].split("?")[0]
                       for link in parser.links if urllib.parse.urlparse(urllib.parse.urljoin(site, link)).path.startswith("/post/")})
    if not urls:
        raise ValueError("No post URLs found")
    records, report, failures = {}, [], []
    for url in urls:
        if urllib.parse.urlparse(url).netloc != urllib.parse.urlparse(site).netloc:
            raise ValueError("Post URL belongs to another site")
        try:
            widget = "https://chirpy.dev/widget/comment/" + urllib.parse.quote(url, safe="") + "?" + urllib.parse.urlencode({"referrer": site})
            comments = extract(fetch(widget), url)
            for comment in comments:
                old = records.get(comment["chirpyId"])
                if old and old != comment:
                    raise ValueError("Duplicate ID across post URLs; review mapping")
                records[comment["chirpyId"]] = comment
            report.append({"url": url, "comments": len(comments), "roots": sum(c["parentId"] is None for c in comments)})
            print(f"{len(comments):4} {url}")
        except Exception as error:
            # Exception messages can contain raw responses; only report the type.
            failures.append({"url": url, "error": type(error).__name__})
            print(f"FAIL {url}: {type(error).__name__}", file=sys.stderr)
        time.sleep(args.delay)
    output = {"version": 1, "exportedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
              "site": site, "posts": report, "failures": failures, "comments": list(records.values())}
    path = Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    # Restrictive permissions even though email/account data is excluded.
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as file:
        json.dump(output, file, ensure_ascii=False, indent=2)
    print(f"Saved {len(records)} comments; {len(failures)} failed posts to {path}")
    return 1 if failures else 0


def milliseconds(value):
    return int(dt.datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp() * 1000)


def prepare(snapshot, posts, mappings=None):
    if snapshot.get("version") != 1 or snapshot.get("failures"):
        raise ValueError("Unsupported or incomplete export; resolve failed posts first")
    mappings = mappings or {}
    post_by_slug = {post["slug"]: post["_id"] for post in posts}
    post_ids = set(post_by_slug.values())
    source = snapshot["comments"]
    if any(set(row) != ALLOWED for row in source):
        raise ValueError("Export has unexpected fields; only the sanitized schema is accepted")
    by_id = {row["chirpyId"]: row for row in source}
    if len(by_id) != len(source):
        raise ValueError("Duplicate Chirpy IDs in export")
    prepared, warnings = [], []
    for row in source:
        url = row["sourceUrl"]
        slug = urllib.parse.unquote(urllib.parse.urlparse(url).path.rstrip("/").split("/")[-1])
        post_id = mappings.get(url, post_by_slug.get(slug))
        if post_id not in post_ids:
            raise ValueError(f"Unmapped post URL: {url}")
        root = row
        visited = {root["chirpyId"]}
        while root["parentId"]:
            parent = by_id.get(root["parentId"])
            if parent is None or parent["sourceUrl"] != url or parent["chirpyId"] in visited:
                raise ValueError(f"Missing, cross-post, or cyclic parent for {row['chirpyId']}")
            visited.add(parent["chirpyId"])
            root = parent
        if len(row["body"]) > 5000 or len(row["name"]) > 80:
            warnings.append(f"Preserving long legacy content: {row['chirpyId']}")
        prepared.append({"_id": "chirpy:" + row["chirpyId"], "chirpyId": row["chirpyId"], "sourceUrl": url,
                         "postId": post_id, "name": row["name"], "body": row["body"],
                         "createdAt": milliseconds(row["createdAt"]), "updatedAt": milliseconds(row["updatedAt"]),
                         "threadId": "chirpy:" + root["chirpyId"],
                         "replyToId": "chirpy:" + row["parentId"] if row["parentId"] else None,
                         "deleted": row["deleted"], "owner": False})
    return prepared, warnings


def migrate(args):
    from pymongo import MongoClient
    uri = os.environ.get("COMMENT_MONGO_URI") or os.environ.get("DB_CONNECTION_STRING")
    if not uri:
        raise ValueError("Set COMMENT_MONGO_URI (or DB_CONNECTION_STRING)")
    client = MongoClient(uri, serverSelectionTimeoutMS=10000)
    db = client[args.database]
    snapshot = json.loads(Path(args.input).read_text())
    mapping = json.loads(Path(args.mapping).read_text()) if args.mapping else None
    rows, warnings = prepare(snapshot, list(db.posts.find({}, {"_id": 1, "slug": 1})), mapping)
    for warning in warnings:
        print(warning)
    ids = [row["chirpyId"] for row in rows]
    existing = {row["chirpyId"] for row in db.comments.find({"chirpyId": {"$in": ids}}, {"chirpyId": 1})}
    counts = {}
    for row in rows:
        counts[row["sourceUrl"]] = counts.get(row["sourceUrl"], 0) + 1
    for url, count in sorted(counts.items()):
        print(f"{count:4} {url}")
    print(f"{len(rows)} comments; {len(existing)} already present; {len(rows) - len(existing)} new")
    if args.preview:
        Path(args.preview).write_text(json.dumps(rows, ensure_ascii=False, indent=2))
    if args.command == "import":
        db.comments.create_index("chirpyId", unique=True, partialFilterExpression={"chirpyId": {"$exists": True}})
        for row in rows:
            db.comments.update_one({"chirpyId": row["chirpyId"]}, {"$setOnInsert": row}, upsert=True)
        print("Import complete. No emails, notification jobs, or quota writes.")
    else:
        print("Dry run only; database unchanged.")
    client.close()
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    exp = commands.add_parser("export")
    exp.add_argument("--site", default="https://flowtwo.io")
    exp.add_argument("--urls", help="Optional file of exact post URLs, one per line")
    exp.add_argument("--output", required=True)
    exp.add_argument("--delay", type=float, default=0.2)
    for command in ("dry-run", "import"):
        p = commands.add_parser(command)
        p.add_argument("--input", required=True)
        p.add_argument("--database", default=os.environ.get("DB_NAME", "flow2"))
        p.add_argument("--mapping", help="JSON mapping of source URLs to stable post IDs for renamed posts")
        p.add_argument("--preview", help="Write normalized comments for review (no emails)")
    args = parser.parse_args()
    try:
        return export(args) if args.command == "export" else migrate(args)
    except Exception as error:
        # Don't leak DB connection strings through driver exception messages.
        if isinstance(error, (ValueError, ImportError)):
            print(str(error), file=sys.stderr)
        else:
            print(f"Migration failed: {type(error).__name__}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
