# SaMu Lab

Local LLM server on Android. Bring your own GGUF model, get your own API endpoint.

- **App**: SaMu Lab (Android) — package `com.neurasamu.build`
- **Backend**: SamuServe (Go, single binary)
- **Engine**: llama.cpp `llama-server` (aarch64)
- **Owner**: NeuraSamu

## Layout

```
server/   Go backend (SamuServe)
app/      Android WebView shell
models/   GGUF models + meta.json per model
docs/     schemas, API docs
```

## Status

Skeleton. See `docs/ROADMAP.md`.
