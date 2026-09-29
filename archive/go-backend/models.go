package main

import (
	"errors"
	"sync"
)

type ModelRegistry struct {
	mu     sync.RWMutex
	byName map[string]ModelConfig
}

func NewModelRegistry(cfgs []ModelConfig) *ModelRegistry {
	m := &ModelRegistry{byName: make(map[string]ModelConfig)}
	for _, c := range cfgs {
		m.byName[c.Name] = c
	}
	return m
}

func (m *ModelRegistry) Get(name string) (ModelConfig, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	c, ok := m.byName[name]
	if !ok {
		return ModelConfig{}, errors.New("model not found: " + name)
	}
	return c, nil
}

func (m *ModelRegistry) List() []ModelConfig {
	m.mu.RLock()
	defer m.mu.RUnlock()
	out := make([]ModelConfig, 0, len(m.byName))
	for _, c := range m.byName {
		out = append(out, c)
	}
	return out
}
