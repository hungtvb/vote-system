#!/usr/bin/env python3
"""Generate a deterministic, dependency-free code knowledge graph for Vote System.

The first version intentionally favors high-confidence structural relations over a
complete call graph. Every edge records its extraction source and confidence so
agents do not confuse inferred relationships with direct syntax evidence.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "build" / "code-map"
SOURCE_SUFFIXES = {".java", ".ts", ".tsx", ".js", ".mjs", ".sql"}
SKIP_PARTS = {"node_modules", ".next", "target", "build", ".git", ".test-dist", "out"}

JAVA_TYPE = re.compile(r"\b(class|interface|record|enum)\s+([A-Za-z_$][\w$]*)")
JAVA_PACKAGE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
JAVA_IMPORT = re.compile(r"^\s*import\s+(?:static\s+)?([\w.$*]+)\s*;", re.MULTILINE)
JAVA_METHOD = re.compile(
    r"(?m)^\s*(?:public|protected|private|static|final|synchronized|abstract|default|native|\s)+"
    r"[\w<>, ?\[\].@]+\s+([A-Za-z_$][\w$]*)\s*\([^;{}]*\)\s*(?:throws\s+[^{]+)?\{"
)
JAVA_ANNOTATION = re.compile(r"@([A-Za-z_$][\w$]*)(?:\((.*?)\))?", re.DOTALL)
JAVA_CALL = re.compile(r"\b([a-zA-Z_$][\w$]*)\.([a-zA-Z_$][\w$]*)\s*\(")

TS_IMPORT = re.compile(r"(?:import|export)\s+(?:type\s+)?(?:[^'\"]+?\s+from\s+)?['\"]([^'\"]+)['\"]")
TS_SYMBOL = re.compile(
    r"(?m)^\s*export\s+(?:default\s+)?(?:async\s+)?(?:function|class|interface|type|const|enum)\s+([A-Za-z_$][\w$]*)"
)
TS_FUNCTION = re.compile(r"(?m)^\s*(?:export\s+)?(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(")
TS_CALL = re.compile(r"\b([A-Za-z_$][\w$]*)\.([A-Za-z_$][\w$]*)\s*\(")
HTTP_LITERAL = re.compile(r"['\"]((?:https?://[^'\"]+)?/api/v\d+/[^'\"]*)['\"]")
SQL_TABLE = re.compile(r"(?i)\b(?:create\s+table|alter\s+table|references|insert\s+into|update)\s+(?:if\s+not\s+exists\s+)?([\w.\"]+)")

MAPPING_ANNOTATIONS = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "PatchMapping": "PATCH",
    "DeleteMapping": "DELETE",
}
SPRING_STEREOTYPES = {"RestController", "Controller", "Service", "Repository", "Component", "Configuration"}
EVENT_ANNOTATIONS = {"EventListener", "TransactionalEventListener"}


@dataclass(frozen=True)
class Node:
    id: str
    kind: str
    name: str
    file: str
    line: int
    metadata: dict


@dataclass(frozen=True)
class Edge:
    source: str
    target: str
    kind: str
    evidence: str
    confidence: str


def stable_id(kind: str, value: str) -> str:
    digest = hashlib.sha1(f"{kind}:{value}".encode("utf-8")).hexdigest()[:12]
    return f"{kind}:{digest}"


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def source_files() -> Iterable[Path]:
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.suffix not in SOURCE_SUFFIXES:
            continue
        if any(part in SKIP_PARTS for part in path.parts):
            continue
        yield path


def add_node(nodes: dict[str, Node], kind: str, name: str, file: str, line: int = 1, **metadata) -> str:
    key = f"{kind}:{name}:{file}:{line}"
    node_id = stable_id(kind, key)
    nodes[node_id] = Node(node_id, kind, name, file, line, metadata)
    return node_id


def resolve_ts_import(source: Path, specifier: str) -> str:
    if specifier.startswith("@/"):
        candidate = ROOT / "frontend" / "src" / specifier[2:]
    elif specifier.startswith("."):
        candidate = source.parent / specifier
    else:
        return f"package:{specifier}"
    return "file:" + candidate.relative_to(ROOT).as_posix()


def first_string_argument(raw: str | None) -> str:
    if not raw:
        return ""
    match = re.search(r"['\"]([^'\"]*)['\"]", raw)
    return match.group(1) if match else ""


def join_paths(base: str, child: str) -> str:
    combined = "/".join(part.strip("/") for part in (base, child) if part.strip("/"))
    return "/" + combined if combined else "/"


def scan_java(path: Path, text: str, nodes: dict[str, Node], edges: set[Edge], file_id: str) -> None:
    package_match = JAVA_PACKAGE.search(text)
    package = package_match.group(1) if package_match else ""
    type_matches = list(JAVA_TYPE.finditer(text))
    imports = list(JAVA_IMPORT.finditer(text))
    annotations = list(JAVA_ANNOTATION.finditer(text))

    for imported in imports:
        target = add_node(nodes, "java-symbol", imported.group(1), "", 0, external=True)
        edges.add(Edge(file_id, target, "imports", "java-import", "extracted"))

    type_ids: list[str] = []
    for match in type_matches:
        qualified = f"{package}.{match.group(2)}" if package else match.group(2)
        type_id = add_node(nodes, "java-type", qualified, rel(path), line_of(text, match.start()), declaration=match.group(1))
        type_ids.append(type_id)
        edges.add(Edge(file_id, type_id, "defines", "java-ast-pattern", "extracted"))

    owner = type_ids[0] if type_ids else file_id
    annotation_names = {a.group(1) for a in annotations}
    for stereotype in sorted(annotation_names & SPRING_STEREOTYPES):
        target = add_node(nodes, "spring-role", stereotype, "", 0)
        edges.add(Edge(owner, target, "annotated_as", "spring-annotation", "extracted"))

    class_request_path = ""
    for annotation in annotations:
        if annotation.group(1) == "RequestMapping":
            class_request_path = first_string_argument(annotation.group(2))
            break

    method_ids: dict[str, str] = {}
    for method in JAVA_METHOD.finditer(text):
        name = method.group(1)
        method_id = add_node(nodes, "java-method", name, rel(path), line_of(text, method.start()), owner=nodes[owner].name if owner in nodes else "")
        method_ids[name] = method_id
        edges.add(Edge(owner, method_id, "defines", "java-method-pattern", "extracted"))

        prefix = text[max(0, method.start() - 700):method.start()]
        for annotation in JAVA_ANNOTATION.finditer(prefix):
            annotation_name = annotation.group(1)
            if annotation_name in MAPPING_ANNOTATIONS:
                http_method = MAPPING_ANNOTATIONS[annotation_name]
                endpoint_path = join_paths(class_request_path, first_string_argument(annotation.group(2)))
                endpoint_name = f"{http_method} {endpoint_path}"
                endpoint_id = add_node(nodes, "http-endpoint", endpoint_name, rel(path), line_of(text, method.start()), method=http_method, path=endpoint_path)
                edges.add(Edge(endpoint_id, method_id, "handled_by", "spring-mapping", "extracted"))
            elif annotation_name in EVENT_ANNOTATIONS:
                event_role = add_node(nodes, "spring-role", annotation_name, "", 0)
                edges.add(Edge(method_id, event_role, "annotated_as", "spring-event-annotation", "extracted"))
            elif annotation_name == "Transactional":
                transaction = add_node(nodes, "spring-role", "Transactional", "", 0)
                edges.add(Edge(method_id, transaction, "transaction_boundary", "spring-annotation", "extracted"))

    for call in JAVA_CALL.finditer(text):
        caller = min(method_ids.values(), key=lambda mid: abs(nodes[mid].line - line_of(text, call.start()))) if method_ids else owner
        target_name = f"{call.group(1)}.{call.group(2)}"
        target = add_node(nodes, "call-target", target_name, "", 0, language="java")
        edges.add(Edge(caller, target, "calls", "qualified-call-pattern", "inferred"))

    for event_match in re.finditer(r"publishEvent\s*\(\s*new\s+([A-Za-z_$][\w$]*)", text):
        event_id = add_node(nodes, "spring-event", event_match.group(1), rel(path), line_of(text, event_match.start()))
        edges.add(Edge(owner, event_id, "publishes", "application-event-publisher", "extracted"))


def scan_ts(path: Path, text: str, nodes: dict[str, Node], edges: set[Edge], file_id: str) -> None:
    for imported in TS_IMPORT.finditer(text):
        target_name = resolve_ts_import(path, imported.group(1))
        target = add_node(nodes, "ts-import", target_name, "", 0, specifier=imported.group(1))
        edges.add(Edge(file_id, target, "imports", "typescript-import", "extracted"))

    symbols: dict[str, str] = {}
    for symbol in TS_SYMBOL.finditer(text):
        name = symbol.group(1)
        kind = "react-component" if path.suffix == ".tsx" and name[:1].isupper() else "ts-symbol"
        symbol_id = add_node(nodes, kind, name, rel(path), line_of(text, symbol.start()))
        symbols[name] = symbol_id
        edges.add(Edge(file_id, symbol_id, "defines", "typescript-declaration", "extracted"))

    for function in TS_FUNCTION.finditer(text):
        name = function.group(1)
        if name in symbols:
            continue
        function_id = add_node(nodes, "ts-function", name, rel(path), line_of(text, function.start()))
        symbols[name] = function_id
        edges.add(Edge(file_id, function_id, "defines", "typescript-function", "extracted"))

    owner = next(iter(symbols.values()), file_id)
    for call in TS_CALL.finditer(text):
        target_name = f"{call.group(1)}.{call.group(2)}"
        target = add_node(nodes, "call-target", target_name, "", 0, language="typescript")
        edges.add(Edge(owner, target, "calls", "qualified-call-pattern", "inferred"))

    for literal in HTTP_LITERAL.finditer(text):
        endpoint = add_node(nodes, "http-reference", literal.group(1), rel(path), line_of(text, literal.start()))
        edges.add(Edge(owner, endpoint, "references_endpoint", "http-path-literal", "extracted"))


def scan_sql(path: Path, text: str, nodes: dict[str, Node], edges: set[Edge], file_id: str) -> None:
    for table in SQL_TABLE.finditer(text):
        name = table.group(1).strip('"')
        table_id = add_node(nodes, "database-table", name, rel(path), line_of(text, table.start()))
        edges.add(Edge(file_id, table_id, "defines_or_mutates", "sql-statement", "extracted"))


def generate(output: Path) -> dict:
    nodes: dict[str, Node] = {}
    edges: set[Edge] = set()
    files = sorted(source_files(), key=rel)

    for path in files:
        relative = rel(path)
        file_id = add_node(nodes, "file", relative, relative, 1, language=path.suffix.lstrip("."))
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if path.suffix == ".java":
            scan_java(path, text, nodes, edges, file_id)
        elif path.suffix in {".ts", ".tsx", ".js", ".mjs"}:
            scan_ts(path, text, nodes, edges, file_id)
        elif path.suffix == ".sql":
            scan_sql(path, text, nodes, edges, file_id)

    ordered_nodes = sorted((asdict(node) for node in nodes.values()), key=lambda n: (n["kind"], n["name"], n["file"], n["line"]))
    ordered_edges = sorted((asdict(edge) for edge in edges), key=lambda e: (e["source"], e["kind"], e["target"], e["evidence"]))
    graph = {
        "schemaVersion": 1,
        "repository": "hungtvb/vote-system",
        "revision": os.environ.get("GITHUB_SHA", "working-tree"),
        "generator": "scripts/code_map.py",
        "nodes": ordered_nodes,
        "edges": ordered_edges,
    }

    output.mkdir(parents=True, exist_ok=True)
    (output / "graph.json").write_text(json.dumps(graph, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    write_report(output / "GRAPH_REPORT.md", graph)
    write_mermaid(output / "graph.mmd", graph)
    return graph


def write_report(path: Path, graph: dict) -> None:
    node_counts = Counter(node["kind"] for node in graph["nodes"])
    edge_counts = Counter(edge["kind"] for edge in graph["edges"])
    degree = Counter()
    names = {node["id"]: node["name"] for node in graph["nodes"]}
    for edge in graph["edges"]:
        degree[edge["source"]] += 1
        degree[edge["target"]] += 1

    lines = [
        "# Vote System code graph report",
        "",
        f"- Nodes: **{len(graph['nodes'])}**",
        f"- Edges: **{len(graph['edges'])}**",
        f"- Revision: `{graph['revision']}`",
        "",
        "## Node kinds",
        "",
        "| Kind | Count |",
        "|---|---:|",
    ]
    lines.extend(f"| `{kind}` | {count} |" for kind, count in sorted(node_counts.items()))
    lines.extend(["", "## Edge kinds", "", "| Kind | Count |", "|---|---:|"])
    lines.extend(f"| `{kind}` | {count} |" for kind, count in sorted(edge_counts.items()))
    lines.extend(["", "## Highest-degree nodes", "", "| Node | Degree |", "|---|---:|"])
    for node_id, count in degree.most_common(20):
        lines.append(f"| `{names.get(node_id, node_id)}` | {count} |")
    lines.extend([
        "",
        "## Interpretation boundary",
        "",
        "Edges marked `extracted` are backed by direct syntax or annotations. Edges marked `inferred` are navigation hints and must be verified before changing production behavior.",
        "",
    ])
    path.write_text("\n".join(lines), encoding="utf-8")


def write_mermaid(path: Path, graph: dict) -> None:
    allowed_kinds = {"http-endpoint", "java-method", "spring-event", "react-component", "database-table"}
    nodes = [node for node in graph["nodes"] if node["kind"] in allowed_kinds]
    selected = {node["id"] for node in nodes[:250]}
    lines = ["flowchart LR"]
    for node in nodes[:250]:
        safe = node["name"].replace('"', "'")
        lines.append(f"  {node['id'].replace(':', '_')}[\"{safe}\"]")
    for edge in graph["edges"]:
        if edge["source"] in selected and edge["target"] in selected:
            source = edge["source"].replace(":", "_")
            target = edge["target"].replace(":", "_")
            lines.append(f"  {source} -->|{edge['kind']}| {target}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check-minimum", action="store_true", help="Fail when the graph is suspiciously empty")
    args = parser.parse_args()
    graph = generate(args.output)
    if args.check_minimum:
        node_kinds = Counter(node["kind"] for node in graph["nodes"])
        required = {"file": 10, "java-type": 5, "http-endpoint": 3, "ts-symbol": 3}
        failures = [f"{kind}={node_kinds[kind]} < {minimum}" for kind, minimum in required.items() if node_kinds[kind] < minimum]
        if failures:
            raise SystemExit("Code graph minimum checks failed: " + ", ".join(failures))
    print(f"Generated {len(graph['nodes'])} nodes and {len(graph['edges'])} edges in {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
