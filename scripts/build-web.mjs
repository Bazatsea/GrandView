// Builds www/ for the app: the GrandView page (web/grandview.html, the same file as the
// claude.ai prototype) wrapped in a full document, plus the bundled data/alarm bridge.
import { build } from "esbuild";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";

mkdirSync("www", { recursive: true });
await build({ entryPoints: ["src/shim.js"], bundle: true, minify: true, format: "iife", target: "es2020", outfile: "www/shim.js" });

const page = readFileSync("web/grandview.html", "utf8");
const reset = ":root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}body{margin:0;font:14px system-ui,sans-serif}img{max-width:100%}[hidden]{display:none!important}";
writeFileSync("www/index.html",
  `<!doctype html><html lang="en-GB"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><style>${reset}</style>` +
  `<script src="shim.js"></script></head><body>${page}</body></html>`);
console.log("www/ built");
