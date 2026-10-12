# Vote System code knowledge graph

This tooling gives coding agents and reviewers a deterministic navigation layer over the repository. It follows the useful ideas from Graphify—typed nodes, typed edges, explicit evidence, and machine-readable output—without adding a graph database to the application runtime.

## Generate

From the repository root:

```bash
python3 scripts/code_map.py --check-minimum
```

Artifacts are written to `build/code-map/`:

- `graph.json` — primary machine-readable graph for agents and future query tools;
- `GRAPH_REPORT.md` — counts, high-degree nodes, and interpretation boundaries;
- `graph.mmd` — a bounded Mermaid projection for quick inspection.

The generator uses only the Python standard library. It does not execute project code, connect to production services, read secrets, or send source code outside the repository.

## Initial graph model

Node kinds currently include:

- source files;
- Java types and methods;
- Spring roles and transaction boundaries;
- HTTP endpoints;
- Spring events;
- TypeScript symbols, functions, and React components;
- frontend HTTP path references;
- database tables discovered from Flyway SQL;
- qualified call targets.

Edge kinds currently include:

- `defines`;
- `imports`;
- `calls`;
- `handled_by`;
- `annotated_as`;
- `transaction_boundary`;
- `publishes`;
- `references_endpoint`;
- `defines_or_mutates`.

Every edge has:

- `evidence`: which extractor produced the relation;
- `confidence`: `extracted` or `inferred`.

Agents must verify `inferred` edges before making production changes. Qualified-call extraction is deliberately treated as a navigation hint because static patterns cannot fully resolve Java polymorphism, Spring proxies, dynamic dispatch, or runtime configuration.

## Scope boundary

This first phase is intentionally not a complete semantic compiler. It does not yet guarantee:

- resolved method-to-method call targets;
- Spring bean wiring and conditional configuration;
- JPA entity relationships, query impact, indexes, or cascades;
- publisher-to-listener event matching;
- frontend API caller to backend endpoint normalization;
- Git history, PR, Linear issue, or test coverage relationships;
- runtime traces.

Those are planned as additive extractors. The stable `graph.json` schema allows the project to adopt tree-sitter, JavaParser, ArchUnit, jQAssistant, or a graph database later without discarding the first-phase artifacts.

## Design rules

1. Generated output must be deterministic for the same source revision.
2. Runtime dependencies must not be added merely for code-map generation.
3. Extracted and inferred relations must remain distinguishable.
4. Dynamic identifiers, secrets, credentials, and production data must never appear in the graph.
5. CI minimum checks detect a silently broken extractor.
6. The graph supports navigation and impact analysis; it does not replace compilation, tests, runtime evidence, or code review.

## Next extractors

The next useful increments for Vote System are:

1. resolve Spring constructor injection into bean dependency edges;
2. match `ApplicationEventPublisher` events to transactional listeners;
3. extract JPA entities, repositories, queries, tables, indexes, and foreign keys;
4. normalize frontend API templates against backend mappings;
5. connect production symbols to focused tests;
6. add a small CLI for neighborhood and impact queries;
7. add changed-symbol graph reports to pull requests.
