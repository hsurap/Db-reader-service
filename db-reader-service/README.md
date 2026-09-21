# db-reader-service

A small Spring Boot service that sits between you and the production MongoDB replica
set. You deploy this *once* somewhere that already has network/SSH access to the DB.
After that, you talk to this service over plain HTTP and never need direct DB access
yourself. It ships with a browser UI at `/` — a sidebar of databases/collections plus
a query console — so you don't need `curl` day to day. The REST API underneath is
still there if you want to script against it.

It is read-only by construction, using whatever credentials you give it:
- There is no insert/update/delete/drop code anywhere in the app — only `find`,
  `aggregate`, and `countDocuments` are ever called against Mongo. Nothing else is
  reachable, regardless of what permissions the connecting user actually has.
- Aggregation pipelines are scanned and rejected if they contain a writing or
  code-execution stage (`$out`, `$merge`, `$function`, `$accumulator`, `$where`,
  `$currentOp`, `$indexStats`).
- `admin`, `local`, and `config` databases are always blocked.
- Every query has a server-side timeout and a hard max row limit, so nobody can
  accidentally pull an entire collection or hang the replica set.

You don't need to create a separate Mongo user for this — just point it at whatever
connection string or username/password you already use.

## 1. Configure the connection

Set these as environment variables (or however you inject config into your
deployment — Docker env, Kubernetes secret, systemd EnvironmentFile, etc). Use
**one** of the two options:

**Option A — you already have a full connection string:**
```bash
export MONGO_URI="mongodb://myuser:mypassword@host1,host2,host3/?replicaSet=devices&authSource=admin"
```

**Option B — you have the pieces separately:**
```bash
export MONGO_HOST="host1,host2,host3"
export MONGO_USERNAME="myuser"
export MONGO_PASSWORD="mypassword"
export MONGO_REPLICA_SET="devices"     # optional
export MONGO_AUTH_SOURCE="admin"       # optional, defaults to admin
```

Either way, also set an API key the UI/clients will use to talk to this service
(this is separate from your Mongo credentials — it protects this service itself):
```bash
export SERVICE_API_KEY="generate-a-long-random-string-here"
```

## 2. Build and run

```bash
mvn clean package
java -jar target/db-reader-service-1.0.0.jar
```

Or with Docker:

```bash
mvn clean package
docker build -t db-reader-service .
docker run -p 8080:8080 \
  -e MONGO_URI=... \
  -e SERVICE_API_KEY=... \
  db-reader-service
```

Deploy this on the host/VPC that already has network access to the replica set (the
same place your SSH tunnel currently lands). You then expose port 8080 to
yourself — over a VPN, an internal load balancer, or another SSH tunnel to *this*
service instead of to Mongo directly.

## 3. Use the UI

Open `http://<host>:8080/` in a browser (tunnel or VPN into it the same way you'd
reach any internal tool). Paste the `SERVICE_API_KEY` value into the "API key" field
top-right and click **Connect** — the key is kept only in that browser's
`localStorage`, and is sent as the `X-API-KEY` header on every request.

Once connected:
- The left sidebar lists databases; click one to lazy-load its collections.
- Click a collection to open the query console for it.
- **Find** tab: JSON filter/projection/sort, plus limit/skip.
- **Aggregate** tab: a JSON pipeline array.
- **Count** tab: JSON filter, returns just the count.
- Results render as pretty JSON or a flattened table (toggle top-right of the
  results pane), with a copy button.

The UI is just a thin client over the `/api/**` endpoints below — it can't do
anything the API itself doesn't allow, so all the same read-only guardrails apply.

## 4. Query it from the command line (optional)

All `/api/**` endpoints require `X-API-KEY: <your key>`.

**List databases**
```bash
curl -H "X-API-KEY: $KEY" http://localhost:8080/api/metadata/databases
```

**List collections in a database**
```bash
curl -H "X-API-KEY: $KEY" \
  http://localhost:8080/api/metadata/databases/b2b-account-service/collections
```

**Find documents**
```bash
curl -X POST -H "X-API-KEY: $KEY" -H "Content-Type: application/json" \
  http://localhost:8080/api/query/b2b-account-service/account/find \
  -d '{
    "filter": { "status": "ACTIVE" },
    "projection": { "accountId": 1, "status": 1, "_id": 0 },
    "sort": { "createdAt": -1 },
    "limit": 50
  }'
```

**Count documents**
```bash
curl -X POST -H "X-API-KEY: $KEY" -H "Content-Type: application/json" \
  http://localhost:8080/api/query/b2b-account-service/account/count \
  -d '{ "filter": { "status": "ACTIVE" } }'
```

**Aggregate**
```bash
curl -X POST -H "X-API-KEY: $KEY" -H "Content-Type: application/json" \
  http://localhost:8080/api/query/b2b-account-service/account/aggregate \
  -d '{
    "pipeline": [
      { "$match": { "status": "ACTIVE" } },
      { "$group": { "_id": "$region", "count": { "$sum": 1 } } }
    ],
    "limit": 100
  }'
```

## Defaults / limits

| Setting | Default | Env var override |
|---|---|---|
| Default row limit (if you don't pass one) | 200 | `DEFAULT_MAX_LIMIT` |
| Hard max row limit (even if you ask for more) | 1000 | `HARD_MAX_LIMIT` |
| Query timeout | 15s | `QUERY_TIMEOUT_MS` |

## What this deliberately does NOT do

- No write, update, delete, or index-management endpoints.
- No raw JavaScript execution (`$where`, `mapReduce`, `eval` are all unreachable —
  there's no code path that calls them).
- No credentials or connection strings ever leave the server — clients (including
  the browser UI) only ever send filters/pipelines, never a Mongo URI.

## A note on real security

The app-level blocks above are a safety net, not the actual boundary. If the
credentials you give this service have write access on the underlying database,
this app won't grant a client the ability to use it — but it's still better
practice, whenever you're able to, to use a Mongo user that only has the `read`
role on the databases you need. That way the guarantee holds even if this app has a
bug, not just when it's working as intended.
