#!/bin/bash


URL="http://localhost:8080/api"
MAX_REQUESTS=10

echo "🚀 Starting $MAX_REQUESTS parallel requests to:"
echo "   $URL"
echo "────────────────────"


do_request() {
  local id=$1
  echo "↗️  Request $id started..."
  local response=$(curl -s "$URL?id=$id")
  echo "📦 Response $id: $response"
  echo "✔️  Request $id completed"
  echo "────────────────────"
}


for i in $(seq 1 $MAX_REQUESTS); do
  do_request $i &
done

wait
echo "✅ All $MAX_REQUESTS requests finished!"