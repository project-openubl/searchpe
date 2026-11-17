// Copies the OpenAPI specs the Maven builds write into client/openapi/, then regenerates the
// clients. Build the backends first (see docs/scripts.md):
//   (cd .. && ./mvnw -q -pl searchpe package -DskipTests -Dquarkus.smallrye-openapi.store-schema-directory=target/openapi)
//   (cd ../xhandler && ./mvnw -q -pl app package -DskipTests)
import { execFileSync } from "node:child_process";
import { copyFileSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const uiRoot = path.resolve(fileURLToPath(new URL(".", import.meta.url)), "..");
const repoRoot = path.resolve(uiRoot, "..");

const SPECS = [
  {
    name: "xhandler",
    from: path.join(repoRoot, "xhandler/app/target/openapi/openapi.json"),
    build:
      "(cd xhandler && ./mvnw -q -pl app package -DskipTests)",
  },
  {
    name: "searchpe",
    from: path.join(repoRoot, "searchpe/target/openapi/openapi.json"),
    build:
      "./mvnw -q -pl searchpe package -DskipTests -Dquarkus.smallrye-openapi.store-schema-directory=target/openapi",
  },
];

let missing = false;
for (const spec of SPECS) {
  if (!existsSync(spec.from)) {
    console.error(
      `openapi:refresh: ${path.relative(repoRoot, spec.from)} is missing; from the repository root run:\n  ${spec.build}`,
    );
    missing = true;
    continue;
  }
  const to = path.join(uiRoot, "client/openapi", `${spec.name}.json`);
  copyFileSync(spec.from, to);
  console.log(
    `openapi:refresh: ${path.relative(repoRoot, spec.from)} → ${path.relative(repoRoot, to)}`,
  );
}

if (missing) process.exit(1);

execFileSync("npm", ["run", "generate", "-w", "client"], {
  cwd: uiRoot,
  stdio: "inherit",
});
