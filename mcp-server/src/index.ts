import express from "express";
import { randomUUID } from "node:crypto";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { z } from "zod";

const baseUrl = process.env.SPENDIFI_BASE_URL?.replace(/\/$/, "");
const apiKey = process.env.SPENDIFI_API_KEY;
const port = Number(process.env.PORT ?? 3001);

if (!baseUrl || !apiKey) {
  throw new Error("SPENDIFI_BASE_URL and SPENDIFI_API_KEY are required");
}

async function spendifi(path: string, init?: RequestInit) {
  const response = await fetch(`${baseUrl}/api/webhook${path}`, {
    ...init,
    headers: {
      "X-API-Key": apiKey,
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });

  const body = await response.text();
  if (!response.ok) {
    throw new Error(`Spendifi returned ${response.status}: ${body}`);
  }

  return body ? JSON.parse(body) : null;
}

function textResult(data: unknown) {
  return { content: [{ type: "text" as const, text: JSON.stringify(data) }] };
}

function createServer() {
  const server = new McpServer({ name: "spendifi", version: "0.1.0" });

  server.tool(
    "get_expenses",
    "Get Spendifi expenses. Use date filters whenever the user specifies a period.",
    {
      startDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
      endDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
      category: z.string().optional(),
      limit: z.number().int().positive().max(1000).optional(),
    },
    async ({ startDate, endDate, category, limit }) => {
      const params = new URLSearchParams();
      if (startDate) params.set("startDate", startDate);
      if (endDate) params.set("endDate", endDate);
      if (category) params.set("category", category);
      if (limit) params.set("limit", String(limit));
      const suffix = params.size ? `?${params}` : "";
      return textResult(await spendifi(`/expenses${suffix}`));
    }
  );

  server.tool(
    "get_dashboard",
    "Get a monthly Spendifi dashboard including spending, categories, merchants, budgets and account balances.",
    { month: z.string().regex(/^\d{4}-\d{2}$/).optional() },
    async ({ month }) => {
      const suffix = month ? `?date=${encodeURIComponent(month)}` : "";
      return textResult(await spendifi(`/dashboard${suffix}`));
    }
  );

  server.tool(
    "get_budgets",
    "Get Spendifi budget status for a month.",
    { month: z.string().regex(/^\d{4}-\d{2}$/).optional() },
    async ({ month }) => {
      const suffix = month ? `?date=${encodeURIComponent(month)}` : "";
      return textResult(await spendifi(`/budgets${suffix}`));
    }
  );

  server.tool(
    "create_expense",
    "Create a Spendifi expense. Only call after the user clearly asks to add or record an expense.",
    {
      amount: z.number().positive(),
      merchant: z.string().min(1),
      name: z.string().optional(),
      category: z.string().min(1),
      notes: z.string().optional(),
      timestamp: z.string().optional(),
      accountId: z.string().uuid().optional(),
    },
    async (expense) => textResult(await spendifi("/expense", {
      method: "POST",
      body: JSON.stringify(expense),
    }))
  );

  return server;
}

const app = express();
app.use(express.json());

const transports = new Map<string, StreamableHTTPServerTransport>();

app.post("/mcp", async (req, res) => {
  const sessionId = req.header("mcp-session-id");
  let transport = sessionId ? transports.get(sessionId) : undefined;

  if (!transport) {
    const server = createServer();
    transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: () => randomUUID(),
      onsessioninitialized: (id) => transports.set(id, transport!),
    });
    transport.onclose = () => {
      if (transport?.sessionId) transports.delete(transport.sessionId);
    };
    await server.connect(transport);
  }

  await transport.handleRequest(req, res, req.body);
});

app.get("/mcp", async (req, res) => {
  const transport = transports.get(req.header("mcp-session-id") ?? "");
  if (!transport) return res.status(400).send("Invalid or missing MCP session");
  await transport.handleRequest(req, res);
});

app.delete("/mcp", async (req, res) => {
  const transport = transports.get(req.header("mcp-session-id") ?? "");
  if (!transport) return res.status(400).send("Invalid or missing MCP session");
  await transport.handleRequest(req, res);
});

app.get("/health", (_req, res) => res.json({ status: "ok" }));

app.listen(port, "0.0.0.0", () => console.log(`Spendifi MCP listening on ${port}`));
