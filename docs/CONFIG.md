# Config Schema

SamuServe reads `config.yaml` from working dir or `--config` flag.

```yaml
server:
  host: 127.0.0.1
  port: 8080

engine:
  llama_server_path: ./bin/llama-server
  default_model: my-llm

models:
  - name: my-llm
    path: ./models/my-llm/model.gguf
    ctx: 2048
    threads: 4
    type: llm   # llm | stt | tts | embed (future)
```

## Fields

- `server.host` — bind address (default 127.0.0.1, LAN ke liye 0.0.0.0)
- `server.port` — HTTP port
- `engine.llama_server_path` — llama.cpp binary
- `models[].name` — custom naam (API me yahi use hoga)
- `models[].path` — GGUF file ka path
- `models[].ctx` — context window
- `models[].threads` — CPU threads
- `models[].type` — model category (future plugin ke liye)
