# InsertaBot Android — Product

## Purpose
Native Android companion app for the InsertaBot Cloudflare Worker (`insertabot-cfworker`). Provides a mobile chat interface to an AI agent running on Cloudflare's edge, with on-device configuration and MCP server management.

## Status
Initial scaffold. UI, local preferences, Worker capability probe, and isolated WebSocket adapter are in place. Cloudflare Agents wire protocol must be verified before chat transport is enabled.

## Key Features
- **Chat** — Conversational UI for the InsertaBot AI agent (transport pending wire-protocol verification)
- **MCP Server Management** — Browse and manage remote MCP servers from the phone
- **Model Lane Switching** — Switch between research, coding, and automatic model lanes
- **On-device Configuration** — Worker endpoint URL and optional bearer token stored locally via DataStore
- **Connection Health Check** — Probe `/health` and `/info` endpoints to verify Worker reachability
- **WebSocket Transport** — Isolated `AgentWebSocket` adapter for Cloudflare Agents `ChatAgent` protocol

## Target Users
Android users with a deployed InsertaBot Cloudflare Worker who want a native mobile chat experience.

## Distribution
Designed for F-Droid-style self-hosted reproducible builds. Release keys, keystores, APKs, and credentials must never be committed.
