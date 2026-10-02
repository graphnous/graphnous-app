# Enhancers

Every `.json` file in this folder is an enhancer manifest the server installs
on startup, in file name order. A manifest follows
`graphnous-schemas/enhancer/enhancer-manifest.schema.json`; add
`"$schema": "https://graphnous.dev/schema/enhancer/enhancer-manifest.schema.json"`
for editor support.

The server does not start when a manifest does not parse, uses an unknown
field, or when the enhancers cannot be ordered (a missing dependency, a
dependency cycle, or a target-scoped enhancer depending on a scan-scoped one).

Set `graphnous.enhancers.location` to load them from somewhere else, such as
`file:/enhancers/*.json`.

## What an enhancer produces

After a scan's results are stored, the target-scoped enhancers run on each
target, then the scan-scoped ones on the whole scan, each after the
enhancers it depends on. Every rule becomes one Cypher query over the scan's
nodes in Neo4j:

- Labels, properties and relationship types get the enhancer's namespace:
  `addLabels: ["Endpoint"]` in namespace `Spring` adds the label
  `Spring_Endpoint`. Enhancers only add; core data is never changed.
- Every node and relationship a rule changes records it in
  `<namespace>_enhancedBy`, e.g. `io.graphnous.spring-web/endpoints`.
- Applying a rule again changes nothing: relationships are merged.
- Deleting a scan deletes everything its enhancers added.

To connect a node to a related one, use `{"source": "<property>"}` in the
relationship's target. It can sit inside a `related` condition, for example
to reach the class that declares a method:

```json
{
  "type": "SERVED_BY",
  "cardinality": "exactlyOne",
  "target": {
    "kind": "Class",
    "where": {
      "related": {
        "type": "HAS_METHOD",
        "to": { "kind": "Method", "where": { "property": "id", "equals": { "source": "id" } } }
      }
    }
  }
}
```

The scan log reports what each enhancer did; a rule that left nodes without
their 'exactlyOne' target is a warning.
