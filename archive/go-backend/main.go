package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"io"
	"log"
	"net/http"
	"net/url"
	"strconv"
)

type App struct {
	cfg    *Config
	models *ModelRegistry
	engine *Engine
}

func main() {
	cfgPath := flag.String("config", "config.yaml", "path to config.yaml")
	flag.Parse()

	cfg, err := LoadConfig(*cfgPath)
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	app := &App{
		cfg:    cfg,
		models: NewModelRegistry(cfg.Models),
		engine: NewEngine(cfg),
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/health", app.handleHealth)
	mux.HandleFunc("/v1/models", app.handleModels)
	mux.HandleFunc("/v1/chat/completions", app.handleChat)
	mux.HandleFunc("/api/run", app.handleRun)

	addr := cfg.Server.Host + ":" + strconv.Itoa(cfg.Server.Port)
	log.Printf("SamuServe listening on %s", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatal(err)
	}
}

func (a *App) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, 200, map[string]any{
		"status": "ok",
		"model":  a.engine.Current(),
	})
}

func (a *App) handleModels(w http.ResponseWriter, r *http.Request) {
	list := a.models.List()
	data := make([]map[string]any, 0, len(list))
	for _, m := range list {
		data = append(data, map[string]any{
			"id":       m.Name,
			"object":   "model",
			"owned_by": "samu-lab",
		})
	}
	writeJSON(w, 200, map[string]any{"object": "list", "data": data})
}

func (a *App) handleRun(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "POST only", 405)
		return
	}
	var body struct {
		Model string `json:"model"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	m, err := a.models.Get(body.Model)
	if err != nil {
		http.Error(w, err.Error(), 404)
		return
	}
	base, err := a.engine.Ensure(r.Context(), m)
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	writeJSON(w, 200, map[string]any{"model": m.Name, "endpoint": base})
}

func (a *App) handleChat(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "POST only", 405)
		return
	}
	raw, _ := io.ReadAll(r.Body)
	var probe struct {
		Model string `json:"model"`
	}
	_ = json.Unmarshal(raw, &probe)
	m, err := a.models.Get(probe.Model)
	if err != nil {
		http.Error(w, err.Error(), 404)
		return
	}
	base, err := a.engine.Ensure(r.Context(), m)
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	a.proxy(w, r, base+"/v1/chat/completions", raw)
}

func (a *App) proxy(w http.ResponseWriter, r *http.Request, target string, body []byte) {
	u, _ := url.Parse(target)
	req := &http.Request{
		Method: http.MethodPost,
		URL:    u,
		Header: http.Header{"Content-Type": []string{"application/json"}},
		Body:   io.NopCloser(bytes.NewReader(body)),
		Host:   u.Host,
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		http.Error(w, err.Error(), 502)
		return
	}
	defer resp.Body.Close()
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(resp.StatusCode)
	_, _ = io.Copy(w, resp.Body)
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

