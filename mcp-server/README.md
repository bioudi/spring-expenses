# Spendifi MCP server

A narrow MCP adapter for the existing Spendifi `/api/webhook` API. The Spendifi API key stays on the MCP server and is never passed to ChatGPT.

## Tools

- `get_expenses` — read expenses with date/category/limit filters
- `get_dashboard` — monthly spending, categories, merchants, budgets and balances
- `get_budgets` — monthly budget status
- `create_expense` — create an expense

## Configuration

Set:

```env
SPENDIFI_BASE_URL=https://spendifi.app
SPENDIFI_API_KEY=<your existing Spendifi API key>
PORT=3001
```

Do not commit `.env` or the API key.

## Run locally

```bash
cd mcp-server
npm install
npm run build
npm start
```

Health check: `GET /health`
MCP endpoint: `POST/GET/DELETE /mcp`

## Docker

```bash
docker build -t spendifi-mcp ./mcp-server
docker run --rm -p 3001:3001 \
  -e SPENDIFI_BASE_URL=https://spendifi.app \
  -e SPENDIFI_API_KEY="$SPENDIFI_API_KEY" \
  spendifi-mcp
```

For ChatGPT, deploy the container behind HTTPS at a publicly reachable URL, for example `https://mcp.spendifi.app/mcp`, then add that remote MCP URL as a custom app/connector in ChatGPT developer mode.

## Security

The connector intentionally exposes only the existing webhook capabilities rather than arbitrary database or HTTP access. `create_expense` is described as a write operation and should only be invoked after an explicit user request. For a multi-user/public deployment, add OAuth authentication in front of the MCP endpoint; this initial version is intended for a private, single-user deployment.
