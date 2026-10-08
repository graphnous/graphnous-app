# angular-shop

A small Angular application to test the Angular enhancer with.
`../angular-shop.json` is what the
[TypeScript scanner](https://github.com/graphnous/Graphnous-typescript-scanner)
makes of it. After changing the sources, scan them again from a checkout of
the scanner:

```sh
npx tsx src/main.ts --path <this directory> --target . --output <this directory>/../angular-shop.json
```

The dependencies are not installed, as they are not when Graphnous scans a
repository: what comes from Angular is left unresolved.
