# ConfirmAI

A Quarkus application for analyzing code implementation against requirements using AI models.

## Features

- Store requirements with embeddings in Redis
- Analyze code implementations against requirements
- Support for multiple programming languages
- AST parsing for code analysis
- AI-powered feedback using Ollama models

## Prerequisites

- Java 24
- Maven
- Redis with RedisSearch module
- Ollama with nomic-embed-text, Qwen2.5-Coder, and Gemma3 models

## Setup

1. Install and start Redis with RedisSearch
2. Install Ollama and pull required models:
   ```bash
   ollama pull nomic-embed-text
   ollama pull Qwen2.5-Coder:14B
   ollama pull Gemma3:27B-IT-QAT