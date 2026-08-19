# ═══════════════════════════════════════════════════════════════════
# SecureChat - Multi-Stage Docker Build
# ═══════════════════════════════════════════════════════════════════
# 
# This Dockerfile uses a multi-stage build:
#   1. Build stage: Compiles all Java sources
#   2. Runtime stage: Runs the server with minimal image
#
# Usage:
#   docker build -t securechat .
#   docker run -p 5000:5000 -p 5001:5001 -p 5002:5002 securechat
#
# Ports:
#   5000 - TCP Chat (for terminal clients)
#   5001 - WebSocket (for web clients)
#   5002 - HTTP (web UI)
# ═══════════════════════════════════════════════════════════════════

# ── Stage 1: Build ───────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copy source files
COPY *.java ./
COPY sqlite-jdbc.jar ./
COPY slf4j-api.jar ./
COPY slf4j-nop.jar ./

# Compile all Java sources
RUN javac -cp ".:sqlite-jdbc.jar:slf4j-api.jar:slf4j-nop.jar" \
    CryptoUtils.java \
    PasswordUtils.java \
    DatabaseManager.java \
    WebSocketHandler.java \
    Server.java \
    ClientHandler.java \
    Client.java

# ── Stage 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy compiled classes and dependencies
COPY --from=builder /app/*.class ./
COPY --from=builder /app/sqlite-jdbc.jar ./
COPY --from=builder /app/slf4j-api.jar ./
COPY --from=builder /app/slf4j-nop.jar ./

# Copy web UI files
COPY web/ ./web/

# Expose all three ports
EXPOSE 5000 5001 5002

# Health check (optional — checks if TCP port is listening)
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD nc -z localhost 5000 || exit 1

# Run the server
ENTRYPOINT ["java", "-cp", ".:sqlite-jdbc.jar:slf4j-api.jar:slf4j-nop.jar", "Server"]

# Default port (can be overridden with: docker run ... securechat 8080)
CMD ["5000"]
