package main

import (
	"os"

	"gopkg.in/yaml.v3"
)

type Config struct {
	Server ServerConfig  `yaml:"server"`
	Engine EngineConfig  `yaml:"engine"`
	Models []ModelConfig `yaml:"models"`
}

type ServerConfig struct {
	Host string `yaml:"host"`
	Port int    `yaml:"port"`
}

type EngineConfig struct {
	LlamaServerPath string `yaml:"llama_server_path"`
	DefaultModel    string `yaml:"default_model"`
}

type ModelConfig struct {
	Name    string `yaml:"name"`
	Path    string `yaml:"path"`
	Ctx     int    `yaml:"ctx"`
	Threads int    `yaml:"threads"`
	Type    string `yaml:"type"`
}

func LoadConfig(path string) (*Config, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var c Config
	if err := yaml.Unmarshal(b, &c); err != nil {
		return nil, err
	}
	if c.Server.Host == "" {
		c.Server.Host = "127.0.0.1"
	}
	if c.Server.Port == 0 {
		c.Server.Port = 8080
	}
	return &c, nil
}
