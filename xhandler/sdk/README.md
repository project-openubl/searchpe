# SDKs

Generated from the OpenAPI document (DESIGN.md §12, §26.2): TypeScript with
[`@hey-api/openapi-ts`](https://heyapi.dev) (the same generator xhandler-ui uses) and Java with
[OpenAPI Generator](https://openapi-generator.tech). The CI workflow `sdk.yml` builds them on every
push to `main` and keeps them as artifacts; publishing to npm and Maven Central belongs to the
release process.

```bash
./mvnw -B -q -pl app package -DskipTests   # writes app/target/openapi/openapi.json
./sdk/generate.sh                          # writes sdk/typescript and sdk/java
```
