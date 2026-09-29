# Model Meta (meta.json)

Har model folder me `meta.json` rakhega. Ye info API response me jayegi.

```json
{
  "name": "my-llm",
  "type": "llm",
  "file": "model.gguf",
  "ctx": 2048,
  "threads": 4,
  "quant": "Q4_K_M",
  "size_mb": 1200,
  "params": {
    "temperature": 0.7,
    "top_p": 0.9
  },
  "notes": "optional description"
}
```

## Fields

- `name` — public API model name (config.yaml se match karna chahiye)
- `type` — `llm` | `stt` | `tts` | `embed`
- `file` — GGUF filename inside the folder
- `ctx` — context window
- `threads` — CPU threads
- `quant` — quantization label (info only)
- `size_mb` — file size info
- `params` — default sampling params
- `notes` — optional
