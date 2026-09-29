package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"strconv"
	"sync"
	"time"
)

type Engine struct {
	cfg  *Config
	mu   sync.Mutex
	proc *exec.Cmd
	name string
	port int
}

func NewEngine(cfg *Config) *Engine {
	return &Engine{cfg: cfg}
}

func (e *Engine) Current() string {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.name
}

// Ensure starts llama-server for the given model if not already running.
func (e *Engine) Ensure(ctx context.Context, m ModelConfig) (string, error) {
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.proc != nil && e.name == m.Name {
		return e.baseURL(), nil
	}
	if e.proc != nil {
		_ = e.proc.Process.Kill()
		_, _ = e.proc.Process.Wait()
		e.proc = nil
		e.name = ""
	}
	if _, err := os.Stat(m.Path); err != nil {
		return "", fmt.Errorf("model file missing: %s", m.Path)
	}
	port := 8091
	args := []string{
		"-m", m.Path,
		"--host", "127.0.0.1",
		"--port", strconv.Itoa(port),
	}
	if m.Ctx > 0 {
		args = append(args, "-c", strconv.Itoa(m.Ctx))
	}
	if m.Threads > 0 {
		args = append(args, "-t", strconv.Itoa(m.Threads))
	}
	cmd := exec.CommandContext(ctx, e.cfg.Engine.LlamaServerPath, args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Start(); err != nil {
		return "", err
	}
	e.proc = cmd
	e.name = m.Name
	e.port = port
	if err := waitReady(ctx, e.baseURL(), 30*time.Second); err != nil {
		_ = cmd.Process.Kill()
		e.proc = nil
		e.name = ""
		return "", err
	}
	return e.baseURL(), nil
}

func (e *Engine) baseURL() string {
	return fmt.Sprintf("http://127.0.0.1:%d", e.port)
}

func waitReady(ctx context.Context, base string, timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}
		req, _ := http.NewRequestWithContext(ctx, "GET", base+"/health", nil)
		resp, err := http.DefaultClient.Do(req)
		if err == nil && resp.StatusCode == 200 {
		resp.Body.Close()
			return nil
		}
		if resp != nil {
			resp.Body.Close()
		}
		time.Sleep(300 * time.Millisecond)
	}
	return fmt.Errorf("llama-server not ready in %s", timeout)
}
